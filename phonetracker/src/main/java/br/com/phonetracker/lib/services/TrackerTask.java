package br.com.phonetracker.lib.services;

import android.annotation.SuppressLint;
import android.location.Location;
import android.support.v4.app.ActivityCompat;
import br.com.phonetracker.lib.commons.Coordinates;
import br.com.phonetracker.lib.commons.GeoPoint;
import br.com.phonetracker.lib.commons.SensorGpsDataItem;
import br.com.phonetracker.lib.commons.Utils;
import br.com.phonetracker.lib.TrackerSettings;
import br.com.phonetracker.lib.commons.Battery;
import br.com.phonetracker.lib.commons.Logger;

import java.util.*;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

class TrackerTask extends Observable {
    //used on tracker by time
    private Timer timer;
    private int interval;
    private AwsIot awsIot;
    private TrackerService owner;
    private final String TAG = "SensorDataEventLoopTask";

    private Location m_lastLocation;


    TrackerTask (TrackerService owner, TrackerSettings trackerSettings) {
        this.owner = owner;
        awsIot = new AwsIot(owner.getContext(), trackerSettings.getTrackedId(), trackerSettings.getAwsIotSettings());
        interval = trackerSettings.getIntervalInSeconds() * 1000;
    }

    @SuppressLint("DefaultLocale")
    private void handlePredict(SensorGpsDataItem sdi) {

//        Logger.d(String.format("%d%d KalmanPredict : accX=%f, accY=%f",
//                Utils.LogMessageType.KALMAN_PREDICT.ordinal(),
//                (long)sdi.getTimestamp(),
//                sdi.getAbsEastAcc(),
//                sdi.getAbsNorthAcc()));
//
//        Logger.d("handlePredict");

        owner.m_kalmanFilter.predict(sdi.getTimestamp(), sdi.getAbsEastAcc(), sdi.getAbsNorthAcc());
    }
    @SuppressLint("DefaultLocale")
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

        if (owner.m_geoHashRTFilter != null) {
            owner.m_geoHashRTFilter.filter(loc);
        }

        return loc;
    }
    private void onLocationChangedImp(Location location) {
        if (location == null || location.getLatitude() == 0 ||
            location.getLongitude() == 0 || !location.getProvider().equals(TAG)) {

            return;
        }

        owner.m_serviceStatus = TrackerService.ServiceStatus.HAS_LOCATION;
        m_lastLocation = location;

        if (ActivityCompat.checkSelfPermission(owner, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
            owner.m_gpsStatus = owner.m_locationManager.getGpsStatus(owner.m_gpsStatus);
        }
    }


    void startTask () {
        Logger.d("startTask");
        timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                int minimunBatteryPercentage = 10;
                int batteryPercentage = Battery.getBatteryPercentage(owner.getContext());

                if (batteryPercentage > minimunBatteryPercentage) {
                    SensorGpsDataItem sdi;

                    Logger.d("onTaskUpdate");
                    Logger.d("pool size: " + owner.m_sensorDataQueue.size());

                    while ((sdi = owner.m_sensorDataQueue.poll()) != null) {

                        if (sdi.getGpsLat() == SensorGpsDataItem.NOT_INITIALIZED) {
                            handlePredict(sdi);
                        } else {
                            handleUpdate(sdi);
                            Location loc = locationAfterUpdateStep(sdi);
                            onLocationChangedImp(loc);
                        }
                    }

                    Logger.d("onLocationChanged");
                    awsIot.sendPosition(m_lastLocation);

                    setChanged();
                    notifyObservers(m_lastLocation);

                }else {
                    StringBuilder sb = new StringBuilder();
                    Logger.d(sb.append("battery is too low (lower than ").append(minimunBatteryPercentage).append("%").toString());
                }

                StringBuilder sb = new StringBuilder();
                Logger.d(sb.append("battery: ").append(batteryPercentage).append("%").toString());

            }
        }, interval, interval);
    }

    void stopTask () {
        deleteObservers();

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        awsIot.disconnectAWSIot();
    }


}
