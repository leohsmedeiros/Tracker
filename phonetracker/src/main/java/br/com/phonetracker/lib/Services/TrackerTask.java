package br.com.phonetracker.lib.Services;

import android.location.GpsSatellite;
import android.location.Location;
import android.support.v4.app.ActivityCompat;
import br.com.phonetracker.lib.Commons.Coordinates;
import br.com.phonetracker.lib.Commons.GeoPoint;
import br.com.phonetracker.lib.Commons.SensorGpsDataItem;
import br.com.phonetracker.lib.Commons.Utils;
import br.com.phonetracker.lib.utils.Logger;

import java.util.Timer;
import java.util.TimerTask;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

class TrackerTask {
    //used on tracker by time
    private Timer timer;
    private int interval;
    private AwsIot awsIot;
    private TrackerService owner;
    private final String TAG = "SensorDataEventLoopTask";


    TrackerTask (TrackerService owner, int timeInterval, AwsIot awsIot) {
        this.owner = owner;
//        int SEC_TIME_BASE = 1000;
//        interval = timeInterval * SEC_TIME_BASE;
        interval = timeInterval;
        this.awsIot = awsIot;
//        this.deltaTMs = deltaTMs;
    }

    private void handlePredict(SensorGpsDataItem sdi) {
        Logger.d(String.format("%d%d KalmanPredict : accX=%f, accY=%f",
                Utils.LogMessageType.KALMAN_PREDICT.ordinal(),
                (long)sdi.getTimestamp(),
                sdi.getAbsEastAcc(),
                sdi.getAbsNorthAcc()));

        Logger.d("handlePredict");

        owner.m_kalmanFilter.predict(sdi.getTimestamp(), sdi.getAbsEastAcc(), sdi.getAbsNorthAcc());
    }
    private void handleUpdate(SensorGpsDataItem sdi) {
        double xVel = sdi.getSpeed() * Math.cos(sdi.getCourse());
        double yVel = sdi.getSpeed() * Math.sin(sdi.getCourse());

        Logger.d("handleUpdate");

        Logger.d(String.format("%d%d KalmanUpdate : pos lon=%f, lat=%f, xVel=%f, yVel=%f, posErr=%f, velErr=%f",
                Utils.LogMessageType.KALMAN_UPDATE.ordinal(),
                (long)sdi.getTimestamp(),
                sdi.getGpsLon(),
                sdi.getGpsLat(),
                xVel,
                yVel,
                sdi.getPosErr(),
                sdi.getVelErr()
        ));

        owner.m_kalmanFilter.update(
                sdi.getTimestamp(),
                Coordinates.longitudeToMeters(sdi.getGpsLon()),
                Coordinates.latitudeToMeters(sdi.getGpsLat()),
                xVel,
                yVel,
                sdi.getPosErr(),
                sdi.getVelErr()
        );
    }
    private Location locationAfterUpdateStep(SensorGpsDataItem sdi) {
        double xVel, yVel;
        Location loc = new Location(TAG);
        GeoPoint pp = Coordinates.metersToGeoPoint(owner.m_kalmanFilter.getCurrentX(), owner.m_kalmanFilter.getCurrentY());
        loc.setLatitude(pp.Latitude);
        loc.setLongitude(pp.Longitude);
        loc.setAltitude(sdi.getGpsAlt());
        xVel = owner.m_kalmanFilter.getCurrentXVel();
        yVel = owner.m_kalmanFilter.getCurrentYVel();
        double speed = Math.sqrt(xVel*xVel + yVel*yVel); //scalar speed without bearing
        loc.setBearing((float)sdi.getCourse());
        loc.setSpeed((float) speed);
        loc.setTime(System.currentTimeMillis());
        loc.setElapsedRealtimeNanos(System.nanoTime());
        loc.setAccuracy((float) sdi.getPosErr());

        if (owner.getGeoHashRTFilter() != null) {
            owner.getGeoHashRTFilter().filter(loc);
        }

        return loc;
    }
    private void onLocationChangedImp(Location location) {
        if (location == null || location.getLatitude() == 0 ||
            location.getLongitude() == 0 || !location.getProvider().equals(TAG)) {

            return;
        }

        owner.m_serviceStatus = LocationService.ServiceStatus.HAS_LOCATION;
        owner.m_lastLocation = location;
        owner.m_lastLocationAccuracy = location.getAccuracy();

        if (ActivityCompat.checkSelfPermission(owner, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
            owner.m_gpsStatus = owner.m_locationManager.getGpsStatus(owner.m_gpsStatus);
        }

        int activeSatellites = 0;
        if (owner.m_gpsStatus != null) {
            for (GpsSatellite satellite : owner.m_gpsStatus.getSatellites()) {
                activeSatellites += satellite.usedInFix() ? 1 : 0;
            }
            owner.m_activeSatellites = activeSatellites;
        }

        Logger.d("onLocationChanged");
        owner.getAwsIot().sendPosition(location);


//        for (LocationServiceInterface locationServiceInterface : owner.m_locationServiceInterfaces) {
//            Logger.d("onLocationChanged.locationServiceInterface");
//            locationServiceInterface.locationChanged(location);
//        }
//
//        for (LocationServiceStatusInterface locationServiceStatusInterface : owner.m_locationServiceStatusInterfaces) {
//            locationServiceStatusInterface.serviceStatusChanged(owner.m_serviceStatus);
//            locationServiceStatusInterface.lastLocationAccuracyChanged(owner.m_lastLocationAccuracy);
//            locationServiceStatusInterface.GPSStatusChanged(owner.m_activeSatellites);
//        }

    }


    void startTask () {
        Logger.d("startTask");
        timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                SensorGpsDataItem sdi;

                Logger.d("onTaskUpdate");
                Logger.d("pool size: " + owner.m_sensorDataQueue.size());

                while ((sdi = owner.m_sensorDataQueue.poll()) != null) {

                    if (sdi.getGpsLat() == SensorGpsDataItem.NOT_INITIALIZED) {
                        handlePredict(sdi);
                    } else {
                        handleUpdate(sdi);
                    }

                    Location loc = locationAfterUpdateStep(sdi);
                    onLocationChangedImp(loc);
                }
            }
        }, interval, interval);
    }

    void stopTask () {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        awsIot.disconnectAWSIot();
    }


}
