package br.com.phonetracker.lib.Services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.location.*;
import android.os.*;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import br.com.phonetracker.lib.Commons.Coordinates;
import br.com.phonetracker.lib.Commons.SensorGpsDataItem;
import br.com.phonetracker.lib.Commons.Utils;
import br.com.phonetracker.lib.Filters.GPSAccKalmanFilter;
import br.com.phonetracker.lib.Interfaces.LocationServiceStatusInterface;
import br.com.phonetracker.lib.Loggers.GeohashRTFilter;
import br.com.phonetracker.lib.TrackerSettings;
import br.com.phonetracker.lib.utils.Logger;
import br.com.phonetracker.lib.utils.TrackerSharedPreferences;

import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

public class TrackerService extends LocationService
        implements SensorEventListener, LocationListener, GpsStatus.Listener {


    //region VARIABLES

    //region - to SensorChanged
    private float[] rotationMatrix = new float[16];
    private float[] rotationMatrixInv = new float[16];
    private float[] absAcceleration = new float[4];
    private float[] linearAcceleration = new float[4];
    //endregion - to SensorChanged

    private GeohashRTFilter m_geoHashRTFilter = null;
    public GeohashRTFilter getGeoHashRTFilter() {
        return m_geoHashRTFilter;
    }

    private static int[] sensorTypes = {
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_ROTATION_VECTOR,
    };

    public Queue<SensorGpsDataItem> m_sensorDataQueue = new PriorityBlockingQueue<>();


    private AwsIot awsIot;
    public AwsIot getAwsIot() {
        return awsIot;
    }

    private TrackerTask trackerTask;

    //endregion VARIABLES


    public TrackerService() {
        m_locationServiceInterfaces = new ArrayList<>();
        m_locationServiceStatusInterfaces = new ArrayList<>();
        m_lstSensors = new ArrayList<Sensor>();
//        m_eventLoopTask = null;
        trackerTask = null;
        reset(defaultSettings);
    }

    /*Service implementation*/
    class LocalBinder extends Binder {
        TrackerService getService() {
            return TrackerService.this;
        }
    }

    @SuppressLint("InvalidWakeLockTag")
    @Override
    public void onCreate() {
        m_locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        m_sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        m_powerManager = (PowerManager) getSystemService(POWER_SERVICE);
//        m_wakeLock = m_powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        if (m_sensorManager == null) {
            m_sensorsEnabled = false;
            return; //todo handle somehow
        }

        for (Integer st : sensorTypes) {
            Sensor sensor = m_sensorManager.getDefaultSensor(st);
            if (sensor == null) {
                Logger.d(String.format("Couldn't get sensor %d", st));
                continue;
            }
            m_lstSensors.add(sensor);
        }

        super.onCreate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.d("onStartCommand");
        TrackerSettings trackerSettings = TrackerSharedPreferences.load(this, TrackerSettings.class);

        Logger.d("trackerSettings != null: " + (trackerSettings != null));

        if (trackerSettings != null && trackerSettings.isSettedToRestart()) {
            Logger.d("Setted To Restart");
            start(trackerSettings);

            // Will restart after process was killed
            return START_STICKY;
        } else {
            Logger.d("Not Setted To Restart");

            // Will not restart after process was killed
            return  START_NOT_STICKY;
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Logger.d("onTaskRemoved");

        TrackerSettings trackerSettings = TrackerSharedPreferences.load(this, TrackerSettings.class);

        stop();

        m_locationServiceInterfaces.clear();
        m_locationServiceStatusInterfaces.clear();

        trackerTask.stopTask();

        stopSelf();

        if (trackerSettings != null && trackerSettings.isSettedToRestart()) {
            reset(trackerSettings.getKalmanSettings());
            Intent broadcastIntent = new Intent("uk.ac.shef.oak.ActivityRecognition.RestartSensor");
            sendBroadcast(broadcastIntent);
        }


        super.onTaskRemoved(rootIntent);
    }

    @Override
    protected void start(TrackerSettings trackerSettings) {
//        m_wakeLock.acquire();

        awsIot = new AwsIot(this, trackerSettings.getTrackedId(), trackerSettings.getAwsIotSettings());

        m_sensorDataQueue.clear();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            m_serviceStatus = ServiceStatus.PERMISSION_DENIED;
        } else {
            m_serviceStatus = ServiceStatus.SERVICE_STARTED;
            m_locationManager.removeGpsStatusListener(this);
            m_locationManager.addGpsStatusListener(this);
            m_locationManager.removeUpdates(this);
            m_locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    m_settings.gpsMinTime, m_settings.gpsMinDistance, this );
        }

        m_sensorsEnabled = true;
        for (Sensor sensor : m_lstSensors) {
            m_sensorManager.unregisterListener(this, sensor);

            m_sensorsEnabled &= !m_sensorManager.registerListener(this, sensor,
                    Utils.hertz2periodUs(m_settings.sensorFrequencyHz));
        }
        m_gpsEnabled = m_locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        for (LocationServiceStatusInterface ilss : m_locationServiceStatusInterfaces) {
            ilss.serviceStatusChanged(m_serviceStatus);
            ilss.GPSEnabledChanged(m_gpsEnabled);
        }

        trackerTask = new TrackerTask(this, trackerSettings.getKalmanSettings().gpsMinTime, awsIot);
        trackerTask.startTask();

//        m_eventLoopTask = new SensorDataEventLoopTask(m_settings.positionMinTime, this);
//        m_eventLoopTask.needTerminate = false;
//        m_eventLoopTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    @Override
    protected void stop() {
        Logger.d("stop");

//        if (m_wakeLock.isHeld())
//            m_wakeLock.release();

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            m_serviceStatus = ServiceStatus.SERVICE_STOPPED;
        } else {
            m_serviceStatus = ServiceStatus.SERVICE_PAUSED;
            m_locationManager.removeGpsStatusListener(this);
            m_locationManager.removeUpdates(this);
        }

        if (m_geoHashRTFilter != null) {
            m_geoHashRTFilter.stop();
        }

        m_sensorsEnabled = false;
        m_gpsEnabled = false;
        for (Sensor sensor : m_lstSensors)
            m_sensorManager.unregisterListener(this, sensor);

        for (LocationServiceStatusInterface ilss : m_locationServiceStatusInterfaces) {
            ilss.serviceStatusChanged(m_serviceStatus);
            ilss.GPSEnabledChanged(m_gpsEnabled);
        }

//        if (m_eventLoopTask != null) {
//            m_eventLoopTask .needTerminate = true;
//            m_eventLoopTask.cancel(true);
//        }
        m_sensorDataQueue.clear();
    }
    @Override
    protected void reset(Settings settings) {
        m_settings = settings;
        m_kalmanFilter = null;

        if (m_settings.geoHashPrecision != 0 && m_settings.geoHashMinPointCount != 0) {
            m_geoHashRTFilter = new GeohashRTFilter(m_settings.geoHashPrecision, m_settings.geoHashMinPointCount);
        }
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
            case Sensor.TYPE_LINEAR_ACCELERATION:
                System.arraycopy(event.values, 0, linearAcceleration, 0, event.values.length);
                android.opengl.Matrix.multiplyMV(absAcceleration, 0, rotationMatrixInv, 0, linearAcceleration, 0);

                String logStr = String.format("%d%d abs acc: %f %f %f",
                        Utils.LogMessageType.ABS_ACC_DATA.ordinal(),
                        nowMs, absAcceleration[east], absAcceleration[north], absAcceleration[up]);

//                Logger.d(logStr);

                if (m_kalmanFilter == null) {
                    if (m_locationManager != null)
                        onLocationChanged(m_locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));

                    break;
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
    @Override
    public void onLocationChanged(Location loc) {

        if (loc == null) {
//            Logger.d("onLocationChanged: null");
            return;
        }
//        if (m_settings.filterMockGpsCoordinates && loc.isFromMockProvider()) return;

//        Logger.d("onLocationChanged: " + loc);

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

        String logStr = String.format("%d%d GPS : pos lat=%f, lon=%f, alt=%f, hdop=%f, speed=%f, bearing=%f, sa=%f",
                Utils.LogMessageType.GPS_DATA.ordinal(),
                timeStamp, loc.getLatitude(),
                loc.getLongitude(), loc.getAltitude(), loc.getAccuracy(),
                loc.getSpeed(), loc.getBearing(), velErr);
        Logger.d(logStr);

        GeomagneticField f = new GeomagneticField(
                (float)loc.getLatitude(),
                (float)loc.getLongitude(),
                (float)loc.getAltitude(),
                timeStamp);
        m_magneticDeclination = f.getDeclination();

        if (m_kalmanFilter == null) {
            Logger.d(String.format("%d%d KalmanAlloc : lon=%f, lat=%f, speed=%f, course=%f, m_accDev=%f, posDev=%f",
                    Utils.LogMessageType.KALMAN_ALLOC.ordinal(),
                    timeStamp, x, y, speed, course, m_settings.accelerationDeviation, posDev));

            m_kalmanFilter = new GPSAccKalmanFilter(
                    false, //todo move to settings
                    Coordinates.longitudeToMeters(x),
                    Coordinates.latitudeToMeters(y),
                    xVel,
                    yVel,
                    m_settings.accelerationDeviation,
                    posDev,
                    timeStamp,
                    m_settings.mVelFactor,
                    m_settings.mPosFactor);
            return;
        }

        SensorGpsDataItem sdi = new SensorGpsDataItem(
                timeStamp, loc.getLatitude(), loc.getLongitude(), loc.getAltitude(),
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
    public void onStatusChanged(String s, int i, Bundle bundle) {
        //do nothing
    }

    @Override
    public void onProviderEnabled(String provider) {
        Logger.d("onProviderEnabled: " + provider);
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            m_gpsEnabled = true;
            for (LocationServiceStatusInterface ilss : m_locationServiceStatusInterfaces) {
                ilss.GPSEnabledChanged(m_gpsEnabled);
            }
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        Logger.d("onProviderDisabled: " + provider);
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            m_gpsEnabled = false;
            for (LocationServiceStatusInterface ilss : m_locationServiceStatusInterfaces) {
                ilss.GPSEnabledChanged(m_gpsEnabled);
            }
        }
    }
    //endregion LocationListener

    //region GpsStatus.Listener
    @Override
    public void onGpsStatusChanged(int event) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            m_gpsStatus = m_locationManager.getGpsStatus(m_gpsStatus);
        }

        int activeSatellites = 0;
        if (m_gpsStatus != null) {
            for (GpsSatellite satellite : m_gpsStatus.getSatellites()) {
                activeSatellites += satellite.usedInFix() ? 1 : 0;
            }

            if (activeSatellites != 0) {
                this.m_activeSatellites = activeSatellites;
                for (LocationServiceStatusInterface locationServiceStatusInterface : m_locationServiceStatusInterfaces) {
                    locationServiceStatusInterface.GPSStatusChanged(this.m_activeSatellites);
                }
            }
        }
    }
    //endregion GpsStatus.Listener
}
