package mad.location.manager.lib.Services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.Location;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import mad.location.manager.lib.Commons.Coordinates;
import mad.location.manager.lib.Commons.GeoPoint;
import mad.location.manager.lib.Commons.SensorGpsDataItem;
import mad.location.manager.lib.Commons.Utils;
import mad.location.manager.lib.Interfaces.LocationServiceInterface;
import mad.location.manager.lib.Interfaces.LocationServiceStatusInterface;
import mad.location.manager.lib.utils.Logger;

/*
public class SensorDataEventLoopTask extends AsyncTask {
    boolean needTerminate = false;
    private long deltaTMs;
    private TrackerService owner;
    private final String TAG = "SensorDataEventLoopTask";

    SensorDataEventLoopTask(long deltaTMs, TrackerService owner) {
        this.deltaTMs = deltaTMs;
        this.owner = owner;
    }

    private void handlePredict(SensorGpsDataItem sdi) {

        Logger.d(String.format("%d%d KalmanPredict : accX=%f, accY=%f",
                Utils.LogMessageType.KALMAN_PREDICT.ordinal(),
                (long)sdi.getTimestamp(),
                sdi.getAbsEastAcc(),
                sdi.getAbsNorthAcc()));

        owner.m_kalmanFilter.predict(sdi.getTimestamp(), sdi.getAbsEastAcc(), sdi.getAbsNorthAcc());
    }

    private void handleUpdate(SensorGpsDataItem sdi) {
        double xVel = sdi.getSpeed() * Math.cos(sdi.getCourse());
        double yVel = sdi.getSpeed() * Math.sin(sdi.getCourse());

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

    @SuppressLint("DefaultLocale")
    @Override
    protected Object doInBackground(Object[] objects) {
        while (!needTerminate) {
            try {
                Thread.sleep(deltaTMs);
            } catch (InterruptedException e) {
                e.printStackTrace();
                continue; //bad
            }

            SensorGpsDataItem sdi;
            double lastTimeStamp = 0.0;
            while ((sdi = owner.m_sensorDataQueue.poll()) != null) {
                if (sdi.getTimestamp() < lastTimeStamp) {
                    continue;
                }
                lastTimeStamp = sdi.getTimestamp();

                //warning!!!
                if (sdi.getGpsLat() == SensorGpsDataItem.NOT_INITIALIZED) {
                    handlePredict(sdi);
                } else {
                    handleUpdate(sdi);
                    Location loc = locationAfterUpdateStep(sdi);
                    publishProgress(loc);
                }
            }
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(Object... values) {
        onLocationChangedImp((Location) values[0]);
    }

    void onLocationChangedImp(Location location) {
        if (location == null || location.getLatitude() == 0 ||
                location.getLongitude() == 0 ||
                !location.getProvider().equals(TAG)) {
            return;
        }

        owner.m_serviceStatus = LocationService.ServiceStatus.HAS_LOCATION;
        owner.m_lastLocation = location;
        owner.m_lastLocationAccuracy = location.getAccuracy();

        if (ActivityCompat.checkSelfPermission(owner,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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

        for (LocationServiceInterface locationServiceInterface : owner.m_locationServiceInterfaces) {
            Logger.d("onLocationChanged.locationServiceInterface");
            locationServiceInterface.locationChanged(location);
        }

        for (LocationServiceStatusInterface locationServiceStatusInterface : owner.m_locationServiceStatusInterfaces) {
            locationServiceStatusInterface.serviceStatusChanged(owner.m_serviceStatus);
            locationServiceStatusInterface.lastLocationAccuracyChanged(owner.m_lastLocationAccuracy);
            locationServiceStatusInterface.GPSStatusChanged(owner.m_activeSatellites);
        }

    }

}
*/
