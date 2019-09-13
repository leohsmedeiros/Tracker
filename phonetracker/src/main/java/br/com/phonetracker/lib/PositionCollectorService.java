package br.com.phonetracker.lib;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

import br.com.phonetracker.lib.commons.Coordinates;
import br.com.phonetracker.lib.commons.GeoPoint;
import br.com.phonetracker.lib.commons.GeohashRTFilter;
import br.com.phonetracker.lib.commons.KalmanSettings;
import br.com.phonetracker.lib.commons.Logger;
import br.com.phonetracker.lib.commons.SensorGpsDataItem;
import br.com.phonetracker.lib.commons.Utils;
import br.com.phonetracker.lib.filters.GPSAccKalmanFilter;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;


/**
 * The responsibility of this service is to collect and fuse data from GPS, accelerometer
 * and gyroscope and then apply kalman filter to reduce noise.
 */
public abstract class PositionCollectorService extends Service implements LocationListener, SensorEventListener {
    public enum Status { STARTED, DESTROYED }

    protected static KalmanSettings kalmanSettings;

    private float[] rotationMatrix = new float[16];
    private float[] rotationMatrixInv = new float[16];
    private float[] absAcceleration = new float[4];
    private float[] linearAcceleration = new float[4];
    private double m_magneticDeclination = 0.0;
    private GpsStatus m_gpsStatus;

    private Queue<SensorGpsDataItem> m_sensorDataQueue = new PriorityBlockingQueue<>();

    private GeohashRTFilter m_geoHashRTFilter = null;
    private GPSAccKalmanFilter m_kalmanFilter;

    private LocationManager m_locationManager;
    protected Location m_lastLocation;

    protected static Status serviceStatus = Status.DESTROYED;



    private String getBestProvider () {
        Criteria proviterCriteria = new Criteria();
        proviterCriteria.setAccuracy(Criteria.ACCURACY_FINE);
        return m_locationManager.getBestProvider(proviterCriteria, true);
    }

    private Location findLastKnownLocation() {
        Location foundLocation = null;

        if (m_locationManager != null) {
            if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED) {

                foundLocation = m_locationManager.getLastKnownLocation(getBestProvider ());
            }
        }

        return foundLocation;
    }




    protected List<Location> getGeoHashFiltered() {
        if (m_geoHashRTFilter != null) {
            m_geoHashRTFilter.filter(m_lastLocation);

            return m_geoHashRTFilter.getGeoFilteredTrack();
        }

        return null;
    }


    @SuppressLint("MissingPermission")
    protected void startPositionCollector(KalmanSettings kalmanSettings) {
        m_locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        m_locationManager.requestLocationUpdates(getBestProvider(), 0, 0, this);

        if (kalmanSettings != null) {
            m_geoHashRTFilter = new GeohashRTFilter(kalmanSettings.getGeoHashPrecision(),
                    kalmanSettings.getGeoHashMinPointCount());

            m_geoHashRTFilter.reset();
        }
    }

    protected void clear () {
        if (m_locationManager != null)
            m_locationManager.removeUpdates(this);

        if (m_geoHashRTFilter != null)
            m_geoHashRTFilter.stop();

        m_sensorDataQueue.clear();

        if (kalmanSettings != null) {
            if (kalmanSettings.getGeoHashPrecision() != 0 &&
                kalmanSettings.getGeoHashMinPointCount() != 0) {

                m_geoHashRTFilter = new GeohashRTFilter(kalmanSettings.getGeoHashPrecision(),
                                                        kalmanSettings.getGeoHashMinPointCount());
            }

            Intent broadcastIntent = new Intent("uk.ac.shef.oak.ActivityRecognition.RestartSensor");
            sendBroadcast(broadcastIntent);

            Logger.d("send broadcast");
        }
    }

    protected void calculatePositionToSend() {
        if (m_lastLocation == null)
            m_lastLocation = findLastKnownLocation();

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
    }


    @SuppressLint("DefaultLocale")
    private void handleUpdate(SensorGpsDataItem sdi) {
        if (m_kalmanFilter != null) {
            double xVel = sdi.getSpeed() * Math.cos(sdi.getCourse());
            double yVel = sdi.getSpeed() * Math.sin(sdi.getCourse());

            m_kalmanFilter.update(  sdi.getTimestamp(),
                                    Coordinates.longitudeToMeters(sdi.getGpsLon()),
                                    Coordinates.latitudeToMeters(sdi.getGpsLat()),
                                    xVel, yVel, sdi.getPosErr(), sdi.getVelErr() );
        }
    }

    @SuppressLint("DefaultLocale")
    private void handlePredict(SensorGpsDataItem sdi) {
        m_kalmanFilter.predict(sdi.getTimestamp(), sdi.getAbsEastAcc(), sdi.getAbsNorthAcc());
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
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }



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

        GeomagneticField f = new GeomagneticField((float)loc.getLatitude(),
                (float)loc.getLongitude(),
                (float)loc.getAltitude(),
                timeStamp);

        m_magneticDeclination = f.getDeclination();

        if (m_kalmanFilter == null) {
            m_kalmanFilter = new GPSAccKalmanFilter(false,
                    Coordinates.longitudeToMeters(x),
                    Coordinates.latitudeToMeters(y),
                    xVel,
                    yVel,
                    kalmanSettings.getAccelerationDeviation(),
                    posDev,
                    timeStamp,
                    kalmanSettings.getmVelFactor(),
                    kalmanSettings.getmPosFactor());
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
    public void onProviderEnabled(String provider) {
        if (m_lastLocation != null)
            m_lastLocation.setProvider(getBestProvider());
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (m_lastLocation != null)
            m_lastLocation.setProvider(getBestProvider());
    }



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

                try {
                    if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
                        if (m_kalmanFilter == null && m_locationManager != null && Status.DESTROYED.equals(serviceStatus)) {
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
    public void onAccuracyChanged(Sensor sensor, int i) { }

}
