package mad.location.manager.lib.Services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

import mad.location.manager.lib.Commons.Coordinates;
import mad.location.manager.lib.Commons.GeoPoint;
import mad.location.manager.lib.Commons.SensorGpsDataItem;
import mad.location.manager.lib.Commons.Utils;
import mad.location.manager.lib.Filters.GPSAccKalmanFilter;
import mad.location.manager.lib.Interfaces.ILogger;
import mad.location.manager.lib.Interfaces.LocationServiceInterface;
import mad.location.manager.lib.Interfaces.LocationServiceStatusInterface;
import mad.location.manager.lib.Loggers.GeohashRTFilter;
import mad.location.manager.lib.TrackerSettings;
import mad.location.manager.lib.utils.Logger;
import mad.location.manager.lib.utils.TrackerSharedPreferences;

/*
public class KalmanLocationService extends Service
        implements SensorEventListener, LocationListener, GpsStatus.Listener {

//    public static final String TAG = "KalmanLocationService";
    public static final String TAG = "phonetracker";



//!
//    class SensorDataEventLoopTask extends AsyncTask {
//        boolean needTerminate = false;
//        long deltaTMs;
//        KalmanLocationService owner;
//        SensorDataEventLoopTask(long deltaTMs, KalmanLocationService owner) {
//            this.deltaTMs = deltaTMs;
//            this.owner = owner;
//        }
//
//        private void handlePredict(SensorGpsDataItem sdi) {
//            log2File("%d%d KalmanPredict : accX=%f, accY=%f",
//                    Utils.LogMessageType.KALMAN_PREDICT.ordinal(),
//                    (long)sdi.getTimestamp(),
//                    sdi.getAbsEastAcc(),
//                    sdi.getAbsNorthAcc());
//            m_kalmanFilter.predict(sdi.getTimestamp(), sdi.getAbsEastAcc(), sdi.getAbsNorthAcc());
//        }
//
//        private void handleUpdate(SensorGpsDataItem sdi) {
//            double xVel = sdi.getSpeed() * Math.cos(sdi.getCourse());
//            double yVel = sdi.getSpeed() * Math.sin(sdi.getCourse());
//            log2File("%d%d KalmanUpdate : pos lon=%f, lat=%f, xVel=%f, yVel=%f, posErr=%f, velErr=%f",
//                    Utils.LogMessageType.KALMAN_UPDATE.ordinal(),
//                    (long)sdi.getTimestamp(),
//                    sdi.getGpsLon(),
//                    sdi.getGpsLat(),
//                    xVel,
//                    yVel,
//                    sdi.getPosErr(),
//                    sdi.getVelErr()
//            );
//
//            m_kalmanFilter.update(
//                    sdi.getTimestamp(),
//                    Coordinates.longitudeToMeters(sdi.getGpsLon()),
//                    Coordinates.latitudeToMeters(sdi.getGpsLat()),
//                    xVel,
//                    yVel,
//                    sdi.getPosErr(),
//                    sdi.getVelErr()
//            );
//        }
//
//        private Location locationAfterUpdateStep(SensorGpsDataItem sdi) {
//            double xVel, yVel;
//            Location loc = new Location(TAG);
//            GeoPoint pp = Coordinates.metersToGeoPoint(m_kalmanFilter.getCurrentX(),
//                    m_kalmanFilter.getCurrentY());
//            loc.setLatitude(pp.Latitude);
//            loc.setLongitude(pp.Longitude);
//            loc.setAltitude(sdi.getGpsAlt());
//            xVel = m_kalmanFilter.getCurrentXVel();
//            yVel = m_kalmanFilter.getCurrentYVel();
//            double speed = Math.sqrt(xVel*xVel + yVel*yVel); //scalar speed without bearing
//            loc.setBearing((float)sdi.getCourse());
//            loc.setSpeed((float) speed);
//            loc.setTime(System.currentTimeMillis());
//            loc.setElapsedRealtimeNanos(System.nanoTime());
//            loc.setAccuracy((float) sdi.getPosErr());
//
//            if (m_geoHashRTFilter != null) {
//                m_geoHashRTFilter.filter(loc);
//            }
//
//            return loc;
//        }
//
//        @SuppressLint("DefaultLocale")
//        @Override
//        protected Object doInBackground(Object[] objects) {
//            while (!needTerminate) {
//                try {
//                    Thread.sleep(deltaTMs);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                    continue; //bad
//                }
//
//                SensorGpsDataItem sdi;
//                double lastTimeStamp = 0.0;
//                while ((sdi = m_sensorDataQueue.poll()) != null) {
//                    if (sdi.getTimestamp() < lastTimeStamp) {
//                        continue;
//                    }
//                    lastTimeStamp = sdi.getTimestamp();
//
//                    //warning!!!
//                    if (sdi.getGpsLat() == SensorGpsDataItem.NOT_INITIALIZED) {
//                        handlePredict(sdi);
//                    } else {
//                        handleUpdate(sdi);
//                        Location loc = locationAfterUpdateStep(sdi);
//                        publishProgress(loc);
//                    }
//                }
//            }
//            return null;
//        }
//
//        @Override
//        protected void onProgressUpdate(Object... values) {
//            onLocationChangedImp((Location) values[0]);
//        }
//
//        void onLocationChangedImp(Location location) {
//            if (location == null || location.getLatitude() == 0 ||
//                    location.getLongitude() == 0 ||
//                    !location.getProvider().equals(TAG)) {
//                return;
//            }
//
//            m_serviceStatus = ServiceStatus.HAS_LOCATION;
//            m_lastLocation = location;
//            m_lastLocationAccuracy = location.getAccuracy();
//
//            if (ActivityCompat.checkSelfPermission(owner,
//                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//                m_gpsStatus = m_locationManager.getGpsStatus(m_gpsStatus);
//            }
//
//            int activeSatellites = 0;
//            if (m_gpsStatus != null) {
//                for (GpsSatellite satellite : m_gpsStatus.getSatellites()) {
//                    activeSatellites += satellite.usedInFix() ? 1 : 0;
//                }
//                m_activeSatellites = activeSatellites;
//            }
//
//            Logger.d("phonetracker", "onLocationChanged");
//
//            awsIot.sendPosition(location);
//
//
//            //for (LocationServiceInterface locationServiceInterface : m_locationServiceInterfaces) {
////                Logger.d("phonetracker", "onLocationChanged");
//            //    locationServiceInterface.locationChanged(location);
//            //}
//
//
//
//            for (LocationServiceStatusInterface locationServiceStatusInterface : m_locationServiceStatusInterfaces) {
//                locationServiceStatusInterface.serviceStatusChanged(m_serviceStatus);
//                locationServiceStatusInterface.lastLocationAccuracyChanged(m_lastLocationAccuracy);
//                locationServiceStatusInterface.GPSStatusChanged(m_activeSatellites);
//            }
//
//        }
//    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}
*/
