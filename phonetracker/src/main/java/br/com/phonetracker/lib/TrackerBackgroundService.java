package br.com.phonetracker.lib;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.*;
import android.location.*;
import android.os.*;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import br.com.phonetracker.lib.commons.Battery;
import br.com.phonetracker.lib.commons.Coordinates;
import br.com.phonetracker.lib.commons.GeoPoint;
import br.com.phonetracker.lib.commons.SensorGpsDataItem;
import br.com.phonetracker.lib.commons.Utils;
import br.com.phonetracker.lib.filters.GPSAccKalmanFilter;
import br.com.phonetracker.lib.interfaces.GeoHashFilterLocationListener;
import br.com.phonetracker.lib.interfaces.LocationTrackerListener;
import br.com.phonetracker.lib.commons.GeohashRTFilter;
import br.com.phonetracker.lib.commons.Logger;
import br.com.phonetracker.lib.commons.TrackerSharedPreferences;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;


public class TrackerBackgroundService extends Service
        implements SensorEventListener, LocationListener, GpsStatus.Listener {

    //region VARIABLES
    private static final int[] sensorTypes = { Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ROTATION_VECTOR, };
    private static final int[] substituteSensorTypes = { Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GAME_ROTATION_VECTOR, };
    private static final int MIN_BATTERY_PERCENTAGE = 10;

    static List<LocationTrackerListener> listenersToLocationTracker = new ArrayList<>();
    static List<GeoHashFilterLocationListener> listenersToGeohash = new ArrayList<>();
    static TrackerSender trackerSender;
    static TrackerBackgroundService runningInstance;

    //region - for Sensors
    private float[] rotationMatrix = new float[16];
    private float[] rotationMatrixInv = new float[16];
    private float[] absAcceleration = new float[4];
    private float[] linearAcceleration = new float[4];

    private List<Sensor> m_lstSensors;
    private SensorManager m_sensorManager;

    boolean m_sensorsEnabled = false;
    private double m_magneticDeclination = 0.0;

    private Queue<SensorGpsDataItem> m_sensorDataQueue = new PriorityBlockingQueue<>();
    //endregion - for Sensors

    private GpsStatus m_gpsStatus;
    private GPSAccKalmanFilter m_kalmanFilter;

    private GeohashRTFilter m_geoHashRTFilter = null;
    private LocationManager m_locationManager;

    private Location m_lastLocation;
    private boolean isServiceActive;

    //used to send position to callback on UI Thread, to show positions on UI
    private Handler loopToCalculatePosition = new Handler();
    //used to send position to Aws IoT in background
    private Handler loopToSendInformation = new Handler();

    private Runnable runnableCalculatePosition;
    private Runnable runnableSendInformation;


    public void calculatePosition () {
        Logger.d("--- calculate location ---");

        if (Battery.getBatteryPercentage(TrackerBackgroundService.this) > MIN_BATTERY_PERCENTAGE) {
            SensorGpsDataItem sdi;

            while ((sdi = m_sensorDataQueue.poll()) != null) {
                if (sdi.getGpsLat() == SensorGpsDataItem.NOT_INITIALIZED) {
                    handlePredict(sdi);
                } else {
                    handleUpdate(sdi);
                    Location loc = locationAfterUpdateStep(sdi);
                    onLocationChangedImp(loc);
                }
            }

            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                if (m_lastLocation != null) {
                    Logger.d("last location: " + m_lastLocation);

                    for (LocationTrackerListener locationTrackerListener : listenersToLocationTracker) {
                        locationTrackerListener.locationChanged(m_lastLocation);
                    }

                    if (m_geoHashRTFilter != null) {
                        m_geoHashRTFilter.filter(m_lastLocation);

                        for (GeoHashFilterLocationListener locationGeohashListener : listenersToGeohash) {
                            locationGeohashListener.onGeoHashFilterUpdate(m_geoHashRTFilter.getGeoFilteredTrack());
                        }
                    }
                }else {
                    Logger.e("last location: null");
                }
            });

        } else {
            Logger.d("battery is too low (lower than " + MIN_BATTERY_PERCENTAGE + "%");
        }
    }
    public void sendInformation () {
        if (trackerSender != null && m_lastLocation != null && trackerSender.sender.isConnected()) {
            JSONObject telemetric = new JSONObject();
            JSONArray cordenadas = new JSONArray();
            try {
                cordenadas.put(m_lastLocation.getLongitude());
                cordenadas.put(m_lastLocation.getLatitude());

                if (trackerSender.getTrackedId() != null)
                    telemetric.put("trackedId", trackerSender.getTrackedId());

                telemetric.put("status", trackerSender.getStatus());

                telemetric.put("coordinates", cordenadas);

                if (trackerSender.sendSpeed)
                    telemetric.put("speed", m_lastLocation.getSpeed());

                if (trackerSender.sendDirection)
                    telemetric.put("direction", m_lastLocation.getBearing());

            } catch (JSONException e) {
                e.printStackTrace();
            }

            trackerSender.sender.send(telemetric);
        }
    }
    public void sendLastMessage () {
        if (trackerSender != null && trackerSender.sender.isConnected() && trackerSender.getTrackedId() != null) {
            JSONObject telemetric = new JSONObject();
            try {
                telemetric.put("trackedId", trackerSender.getTrackedId());
                telemetric.put("status", "offline");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            trackerSender.sender.send(telemetric);
        }
    }

    //endregion VARIABLES

    //region Service Methods

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.d("onStartCommand");
        runningInstance = this;
        isServiceActive = true;

        m_locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        m_sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        m_lstSensors = new ArrayList<>();

        if (m_sensorManager == null) {
            m_sensorsEnabled = false;
        }else {
            fillSensorsList();
        }

        TrackerSettings trackerSettings = TrackerSharedPreferences.load(this, TrackerSettings.class);

        if (trackerSettings != null) {

            m_sensorDataQueue.clear();

            try {
                if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
                    m_locationManager.removeGpsStatusListener(this);
                    m_locationManager.addGpsStatusListener(this);
                    m_locationManager.removeUpdates(this);
                    m_locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                            trackerSettings.getKalmanSettings().getGpsMinTime(),
                            trackerSettings.getKalmanSettings().getGpsMinDistance(),
                            this);
                }
            }catch (SecurityException e) {
                e.printStackTrace();
            }

            m_sensorsEnabled = true;
            for (Sensor sensor : m_lstSensors) {
                m_sensorManager.unregisterListener(this, sensor);

                m_sensorsEnabled &= !m_sensorManager.registerListener(this, sensor,
                        Utils.hertz2periodUs(trackerSettings.getKalmanSettings().getSensorFrequencyHz()));
            }

            m_geoHashRTFilter = new GeohashRTFilter(trackerSettings.getKalmanSettings().getGeoHashPrecision(),
                                                    trackerSettings.getKalmanSettings().getGeoHashMinPointCount());

            m_geoHashRTFilter.reset();


            runnableCalculatePosition = () -> {
                calculatePosition();
                loopToCalculatePosition.postDelayed(runnableCalculatePosition,
                        trackerSettings.getKalmanSettings().gpsMinTime);
            };

            loopToCalculatePosition.post(runnableCalculatePosition);


            if (trackerSender != null) {
                trackerSender.sender.connect(this);

                runnableSendInformation = () -> {
                    sendInformation();
                    loopToSendInformation.postDelayed(runnableSendInformation,
                                                      trackerSender.getFrequency());
                };

                loopToSendInformation.post(runnableSendInformation);
            }


            // START_STICKY:  Will restart after process was killed
            // START_NOT_STICKY:  Will not restart after process was killed
            return (trackerSettings.getShouldAutoRestart()) ? START_STICKY : START_NOT_STICKY;
        }
        else {
            stopSelf();
            return START_NOT_STICKY;
        }

    }

    private void stop () {
        runningInstance = null;

        m_locationManager.removeGpsStatusListener(this);
        m_locationManager.removeUpdates(this);


        if (m_geoHashRTFilter != null) {
            m_geoHashRTFilter.stop();
        }

        m_sensorsEnabled = false;

        for (Sensor sensor : m_lstSensors)
            m_sensorManager.unregisterListener(this, sensor);

        m_sensorDataQueue.clear();

        if (loopToCalculatePosition != null)
            loopToCalculatePosition.removeCallbacks(runnableCalculatePosition);

        if (loopToSendInformation != null)
            loopToSendInformation.removeCallbacks(runnableSendInformation);

        if (trackerSender != null) {
            sendLastMessage ();
            trackerSender.sender.disconnect();
        }

        TrackerSettings trackerSettings = TrackerSharedPreferences.load(this, TrackerSettings.class);

        m_kalmanFilter = null;

        if (trackerSettings != null) {
            if (trackerSettings.getKalmanSettings().getGeoHashPrecision() != 0 &&
                    trackerSettings.getKalmanSettings().getGeoHashMinPointCount() != 0) {

                m_geoHashRTFilter = new GeohashRTFilter(trackerSettings.getKalmanSettings().getGeoHashPrecision(),
                                                        trackerSettings.getKalmanSettings().getGeoHashMinPointCount());
            }

            if (trackerSettings.getShouldAutoRestart()) {
                Intent broadcastIntent = new Intent("uk.ac.shef.oak.ActivityRecognition.RestartSensor");
                sendBroadcast(broadcastIntent);
            }
        }
    }

    @Override
    public void onDestroy() {
        Logger.d("onDestroy");
        isServiceActive = false;
        stop();

        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Logger.d("onTaskRemoved");
        isServiceActive = false;
        stop();

        super.onTaskRemoved(rootIntent);
    }
    //endregion Service Methods


    @SuppressLint("DefaultLocale")
    private void handlePredict(SensorGpsDataItem sdi) {
        m_kalmanFilter.predict(sdi.getTimestamp(), sdi.getAbsEastAcc(), sdi.getAbsNorthAcc());
    }
    @SuppressLint("DefaultLocale")
    private void handleUpdate(SensorGpsDataItem sdi) {
        double xVel = sdi.getSpeed() * Math.cos(sdi.getCourse());
        double yVel = sdi.getSpeed() * Math.sin(sdi.getCourse());

        m_kalmanFilter.update(sdi.getTimestamp(),
                              Coordinates.longitudeToMeters(sdi.getGpsLon()),
                              Coordinates.latitudeToMeters(sdi.getGpsLat()),
                              xVel,
                              yVel,
                              sdi.getPosErr(),
                              sdi.getVelErr());
    }
    private Location locationAfterUpdateStep(SensorGpsDataItem sdi) {
        double xVel, yVel;
        Location loc = new Location(LocationManager.GPS_PROVIDER);
        GeoPoint pp = Coordinates.metersToGeoPoint(m_kalmanFilter.getCurrentX(), m_kalmanFilter.getCurrentY());
        loc.setLatitude(pp.Latitude);
        loc.setLongitude(pp.Longitude);
        loc.setAltitude(sdi.getGpsAlt());
        xVel = m_kalmanFilter.getCurrentXVel();
        yVel = m_kalmanFilter.getCurrentYVel();
        double speed = Math.sqrt(xVel*xVel + yVel*yVel); //scalar speed without bearing
        loc.setBearing((float)sdi.getCourse());
        loc.setSpeed((float) speed);
        loc.setTime(System.currentTimeMillis());
        loc.setElapsedRealtimeNanos(System.nanoTime());
        loc.setAccuracy((float) sdi.getPosErr());

        if (m_geoHashRTFilter != null) {
            m_geoHashRTFilter.filter(loc);
        }

        return loc;
    }
    private void onLocationChangedImp(Location location) {
        if (location == null || location.getLatitude() == 0 ||
                location.getLongitude() == 0 || !location.getProvider().equals(LocationManager.GPS_PROVIDER)) {

            return;
        }

        m_lastLocation = location;

        try {
            if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED)
                m_gpsStatus = m_locationManager.getGpsStatus(m_gpsStatus);
        }catch (SecurityException e) {
            e.printStackTrace();
        }
    }



    //try to get the sensor from sensorType, if not found try to get from substituteSensorType
    @SuppressLint("DefaultLocale")
    private void fillSensorsList () {
        for (int i=0; i<sensorTypes.length; i++) {
            Sensor sensor = m_sensorManager.getDefaultSensor(sensorTypes[i]);
            if (sensor == null) {
                sensor = m_sensorManager.getDefaultSensor(substituteSensorTypes[i]);
                if (sensor == null) {
                    continue;
                }
            }

            m_lstSensors.add(sensor);
        }
    }


    public Context getContext () {
        return this;
    }



    //region SensorEventListener
    @SuppressLint("MissingPermission")
    @Override
    public void onSensorChanged(SensorEvent event) {

        final int east = 0;
        final int north = 1;
        final int up = 2;

        long now = android.os.SystemClock.elapsedRealtimeNanos();
        long nowMs = Utils.nano2milli(now);

        switch (event.sensor.getType()) {

            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_LINEAR_ACCELERATION:
                System.arraycopy(event.values, 0, linearAcceleration, 0, event.values.length);
                android.opengl.Matrix.multiplyMV(absAcceleration, 0, rotationMatrixInv, 0, linearAcceleration, 0);

//                @SuppressLint("DefaultLocale") String logStr = String.format("%d%d abs acc: %f %f %f",
//                        Utils.LogMessageType.ABS_ACC_DATA.ordinal(),
//                        nowMs, absAcceleration[east], absAcceleration[north], absAcceleration[up]);
//
//                Logger.d(logStr);

                try {
                    if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
                        if (m_kalmanFilter == null && m_locationManager != null && isServiceActive) {
                            onLocationChanged(m_locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
                            break;
                        }
                    }
                }catch (SecurityException e) {
                    e.printStackTrace();
                }


                SensorGpsDataItem sdi = new SensorGpsDataItem(nowMs,
                                                              SensorGpsDataItem.NOT_INITIALIZED,
                                                              SensorGpsDataItem.NOT_INITIALIZED,
                                                              SensorGpsDataItem.NOT_INITIALIZED,
                                                              absAcceleration[north],
                                                              absAcceleration[east],
                                                              absAcceleration[up],
                                                              SensorGpsDataItem.NOT_INITIALIZED,
                                                              SensorGpsDataItem.NOT_INITIALIZED,
                                                              SensorGpsDataItem.NOT_INITIALIZED,
                                                              SensorGpsDataItem.NOT_INITIALIZED,
                                                              m_magneticDeclination);

                m_sensorDataQueue.add(sdi);
                break;

            case Sensor.TYPE_GAME_ROTATION_VECTOR:
            case Sensor.TYPE_ROTATION_VECTOR:
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                android.opengl.Matrix.invertM(rotationMatrixInv, 0, rotationMatrix, 0);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //do nothing
    }
    //endregion SensorEventListener

    //region LocationListener
    @SuppressLint("DefaultLocale")
    @Override
    public void onLocationChanged(Location loc) {
        TrackerSettings trackerSettings = TrackerSharedPreferences.load(this, TrackerSettings.class);

        if (trackerSettings == null) {
            this.stopSelf();
            return;
        }

        if (loc == null) {
            return;
        }

        double x, y, xVel, yVel, posDev, course, speed;
        long timeStamp;
        speed = loc.getSpeed();
        course = loc.getBearing();
        x = loc.getLongitude();
        y = loc.getLatitude();
        xVel = speed * Math.cos(course);
        yVel = speed * Math.sin(course);
        posDev = loc.getAccuracy();
        timeStamp = Utils.nano2milli(loc.getElapsedRealtimeNanos());
        //WARNING!!! here should be speed accuracy, but loc.hasSpeedAccuracy()
        // and loc.getSpeedAccuracyMetersPerSecond() requares API 26
        double velErr = loc.getAccuracy() * 0.1;

//        String logStr = String.format("%d%d GPS : pos lat=%f, lon=%f, alt=%f, hdop=%f, speed=%f, bearing=%f, sa=%f",
//                Utils.LogMessageType.GPS_DATA.ordinal(),
//                timeStamp, loc.getLatitude(),
//                loc.getLongitude(), loc.getAltitude(), loc.getAccuracy(),
//                loc.getSpeed(), loc.getBearing(), velErr);
//        Logger.d(logStr);

        GeomagneticField f = new GeomagneticField((float)loc.getLatitude(),
                                                  (float)loc.getLongitude(),
                                                  (float)loc.getAltitude(),
                                                  timeStamp);

        m_magneticDeclination = f.getDeclination();

        if (m_kalmanFilter == null) {
//                Logger.d(String.format("%d%d KalmanAlloc : lon=%f, lat=%f, speed=%f, course=%f, m_accDev=%f, posDev=%f",
//                        Utils.LogMessageType.KALMAN_ALLOC.ordinal(),
//                        timeStamp, x, y, speed, course, trackerSettings.getKalmanSettings().getAccelerationDeviation(), posDev));

            m_kalmanFilter = new GPSAccKalmanFilter(false,
                                                    Coordinates.longitudeToMeters(x),
                                                    Coordinates.latitudeToMeters(y),
                                                    xVel,
                                                    yVel,
                                                    trackerSettings.getKalmanSettings().getAccelerationDeviation(),
                                                    posDev,
                                                    timeStamp,
                                                    trackerSettings.getKalmanSettings().getmVelFactor(),
                                                    trackerSettings.getKalmanSettings().getmPosFactor());
            return;
        }

        SensorGpsDataItem sdi = new SensorGpsDataItem(timeStamp,
                                                      loc.getLatitude(),
                                                      loc.getLongitude(),
                                                      loc.getAltitude(),
                                                      SensorGpsDataItem.NOT_INITIALIZED,
                                                      SensorGpsDataItem.NOT_INITIALIZED,
                                                      SensorGpsDataItem.NOT_INITIALIZED,
                                                      loc.getSpeed(),
                                                      loc.getBearing(),
                                                      loc.getAccuracy(),
                                                      velErr,
                                                      m_magneticDeclination);

        m_sensorDataQueue.add(sdi);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}
    //endregion LocationListener

    //region GpsStatus.Listener
    @Override
    public void onGpsStatusChanged(int event) {
        try {
            if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
                m_gpsStatus = m_locationManager.getGpsStatus(m_gpsStatus);
            }
        }catch (NoSuchElementException | SecurityException e) {
            e.printStackTrace();
        }
    }
    //endregion GpsStatus.Listener

}
