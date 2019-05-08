package br.com.phonetracker.lib.services;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.*;
import android.location.*;
import android.os.*;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import br.com.phonetracker.lib.commons.Coordinates;
import br.com.phonetracker.lib.commons.SensorGpsDataItem;
import br.com.phonetracker.lib.commons.Utils;
import br.com.phonetracker.lib.filters.GPSAccKalmanFilter;
import br.com.phonetracker.lib.interfaces.LocationServiceInterface;
import br.com.phonetracker.lib.interfaces.LocationServiceStatusInterface;
import br.com.phonetracker.lib.loggers.GeohashRTFilter;
import br.com.phonetracker.lib.TrackerSettings;
import br.com.phonetracker.lib.loggers.Logger;
import br.com.phonetracker.lib.commons.TrackerSharedPreferences;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class TrackerService extends Service
        implements SensorEventListener, LocationListener, GpsStatus.Listener, Observer {


    //region VARIABLES

    //region - for Sensors
    private float[] rotationMatrix = new float[16];
    private float[] rotationMatrixInv = new float[16];
    private float[] absAcceleration = new float[4];
    private float[] linearAcceleration = new float[4];

    private List<Sensor> m_lstSensors;
    private SensorManager m_sensorManager;

    private static int[] sensorTypes = { Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ROTATION_VECTOR, };
    private static int[] substituteSensorTypes = { Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GAME_ROTATION_VECTOR, };

    boolean m_sensorsEnabled = false;
    private double m_magneticDeclination = 0.0;
    private boolean m_gpsEnabled = false;

    Queue<SensorGpsDataItem> m_sensorDataQueue = new PriorityBlockingQueue<>();
    //endregion - for Sensors

    public enum ServiceStatus {
        PERMISSION_DENIED(0),
        SERVICE_STOPPED(1),
        SERVICE_STARTED(2),
        HAS_LOCATION(3),
        SERVICE_PAUSED(4);

        int value;

        ServiceStatus(int value) { this.value = value;}
    }

    ServiceStatus m_serviceStatus = ServiceStatus.SERVICE_STOPPED;

    GpsStatus m_gpsStatus;
    GPSAccKalmanFilter m_kalmanFilter;

    GeohashRTFilter m_geoHashRTFilter = null;
    LocationManager m_locationManager;

    private TrackerTask trackerTask;

    static List<LocationServiceInterface> m_locationServiceInterfaces = new ArrayList<>();
    static List<LocationServiceStatusInterface> m_locationServiceStatusInterfaces = new ArrayList<>();

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

        m_locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        m_sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        m_lstSensors = new ArrayList<>();
        trackerTask = null;

        if (m_sensorManager == null) {
            m_sensorsEnabled = false;
        }else {
            fillSensorsList();
        }

        TrackerSettings trackerSettings = TrackerSharedPreferences.load(this, TrackerSettings.class);

        if (trackerSettings != null) {
            m_sensorDataQueue.clear();
            if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
                m_serviceStatus = ServiceStatus.PERMISSION_DENIED;
            } else {

                m_serviceStatus = ServiceStatus.SERVICE_STARTED;
                m_locationManager.removeGpsStatusListener(this);
                m_locationManager.addGpsStatusListener(this);
                m_locationManager.removeUpdates(this);
                m_locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        trackerSettings.getKalmanSettings().getGpsMinTime(),
                        trackerSettings.getKalmanSettings().getGpsMinDistance(),
                        this);
            }

            m_sensorsEnabled = true;
            for (Sensor sensor : m_lstSensors) {
                m_sensorManager.unregisterListener(this, sensor);

                m_sensorsEnabled &= !m_sensorManager.registerListener(this, sensor,
                        Utils.hertz2periodUs(trackerSettings.getKalmanSettings().getSensorFrequencyHz()));
            }
            m_gpsEnabled = m_locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

            trackerTask = new TrackerTask(this, trackerSettings);
            trackerTask.addObserver(this);
            trackerTask.startTask();

            for (LocationServiceStatusInterface ilss : m_locationServiceStatusInterfaces) {
                ilss.serviceStatusChanged(m_serviceStatus);
                ilss.GPSEnabledChanged(m_gpsEnabled);
            }


            // START_STICKY:  Will restart after process was killed
            // START_NOT_STICKY:  Will not restart after process was killed
            return (trackerSettings.getRestartIfKilled()) ? START_STICKY : START_NOT_STICKY;
        }
        else {
            stopSelf();
            return START_NOT_STICKY;
        }

    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Logger.d("onTaskRemoved");
        Logger.d("stop");

        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
            m_serviceStatus = ServiceStatus.SERVICE_STOPPED;
        }
        else {
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

        m_sensorDataQueue.clear();


        trackerTask.stopTask();
        stopSelf();

        TrackerSettings trackerSettings = TrackerSharedPreferences.load(this, TrackerSettings.class);

        m_kalmanFilter = null;

        if (trackerSettings != null) {
            if (trackerSettings.getKalmanSettings().getGeoHashPrecision() != 0 &&
                    trackerSettings.getKalmanSettings().getGeoHashMinPointCount() != 0) {

                m_geoHashRTFilter = new GeohashRTFilter(
                        trackerSettings.getKalmanSettings().getGeoHashPrecision(),
                        trackerSettings.getKalmanSettings().getGeoHashMinPointCount());
            }

            if (trackerSettings.getRestartIfKilled()) {
                Intent broadcastIntent = new Intent("uk.ac.shef.oak.ActivityRecognition.RestartSensor");
                sendBroadcast(broadcastIntent);
            }
        }


        super.onTaskRemoved(rootIntent);
    }
    //endregion Service Methods



    //try to get the sensor from sensorType, if not found try to get from substituteSensorType
    @SuppressLint("DefaultLocale")
    private void fillSensorsList () {
        for (int i=0; i<sensorTypes.length; i++) {
            Logger.d("sensor: " + sensorTypes[i]);
            Sensor sensor = m_sensorManager.getDefaultSensor(sensorTypes[i]);
            if (sensor == null) {
                Logger.d(String.format("TrackerService - Couldn't get sensor %d", sensorTypes[i]));

                sensor = m_sensorManager.getDefaultSensor(substituteSensorTypes[i]);
                Logger.d("sensor: " + substituteSensorTypes[i]);
                if (sensor == null) {
                    Logger.d(String.format("TrackerService - Couldn't get substitute sensor %d", substituteSensorTypes[i]));
                    continue;
                }
            }

            Logger.d(String.format("TrackerService - Get sensor %d", sensor.getType()));

            m_lstSensors.add(sensor);
        }
    }

    public static void addInterface(LocationServiceInterface locationServiceInterface) {
        m_locationServiceInterfaces.add(locationServiceInterface);
    }

    public Context getContext () { return this; }



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
            TrackerSettings trackerSettings = TrackerSharedPreferences.load(this, TrackerSettings.class);

            if (trackerSettings != null) {

                Logger.d(String.format("%d%d KalmanAlloc : lon=%f, lat=%f, speed=%f, course=%f, m_accDev=%f, posDev=%f",
                        Utils.LogMessageType.KALMAN_ALLOC.ordinal(),
                        timeStamp, x, y, speed, course, trackerSettings.getKalmanSettings().getAccelerationDeviation(), posDev));

                m_kalmanFilter = new GPSAccKalmanFilter(
                        false, //todo move to settings
                        Coordinates.longitudeToMeters(x),
                        Coordinates.latitudeToMeters(y),
                        xVel,
                        yVel,
                        trackerSettings.getKalmanSettings().getAccelerationDeviation(),
                        posDev,
                        timeStamp,
                        trackerSettings.getKalmanSettings().getmVelFactor(),
                        trackerSettings.getKalmanSettings().getmPosFactor());
            }
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
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
            m_gpsStatus = m_locationManager.getGpsStatus(m_gpsStatus);
        }

        int activeSatellites = 0;
        if (m_gpsStatus != null) {
            for (GpsSatellite satellite : m_gpsStatus.getSatellites()) {
                activeSatellites += satellite.usedInFix() ? 1 : 0;
            }

            if (activeSatellites != 0) {
//                this.m_activeSatellites = activeSatellites;
                for (LocationServiceStatusInterface locationServiceStatusInterface : m_locationServiceStatusInterfaces) {
                    locationServiceStatusInterface.GPSStatusChanged(activeSatellites);
                }
            }
        }
    }
    //endregion GpsStatus.Listener

    //region Observer
    @Override
    public void update(Observable observable, Object o) {
        if (o instanceof Location) {
            for (LocationServiceInterface locationServiceInterface : m_locationServiceInterfaces) {
                locationServiceInterface.locationChanged((Location)o);
            }
        }
    }
    //endregion Observer
}
