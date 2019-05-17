package br.com.phonetracker.lib.services;

import android.annotation.SuppressLint;
import android.location.Location;
import android.location.LocationManager;
import android.support.v4.app.ActivityCompat;
import br.com.phonetracker.lib.commons.Coordinates;
import br.com.phonetracker.lib.commons.GeoPoint;
import br.com.phonetracker.lib.commons.SensorGpsDataItem;
import br.com.phonetracker.lib.TrackerSettings;
import br.com.phonetracker.lib.commons.Battery;
import br.com.phonetracker.lib.commons.Logger;

import java.util.*;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

class TrackerTask extends Observable {

    private static final int MIN_BATTERY_PERCENTAGE = 10;


    //used to send position to Aws IoT in background
    private Timer timerToSendToAwsIot;
    //used to send position to callback on UI Thread, to show positions on UI
    private Timer timerToNotifyObservers;

    private AwsIot awsIot;
    private TrackerService owner;
    private int intervalToSendToAws;
    private int intervalToCallbackOnUiThread;

//    private final String TAG = "SensorDataEventLoopTask";

    private Location m_lastLocation;


    TrackerTask (TrackerService owner, TrackerSettings trackerSettings) {
        this.owner = owner;
        awsIot = new AwsIot(owner.getContext(), trackerSettings.getAwsIotSettings());
        intervalToSendToAws = trackerSettings.getIntervalInSeconds();
        intervalToCallbackOnUiThread = trackerSettings.getKalmanSettings().getGpsMinTime();
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

//        Logger.d("handleUpdate");
//
//        Logger.d(String.format("%d%d KalmanUpdate : pos lon=%f, lat=%f, xVel=%f, yVel=%f, posErr=%f, velErr=%f",
//                Utils.LogMessageType.KALMAN_UPDATE.ordinal(),
//                (long)sdi.getTimestamp(),
//                sdi.getGpsLon(),
//                sdi.getGpsLat(),
//                xVel,
//                yVel,
//                sdi.getPosErr(),
//                sdi.getVelErr()
//        ));

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
        Location loc = new Location(LocationManager.GPS_PROVIDER);
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
            location.getLongitude() == 0 || !location.getProvider().equals(LocationManager.GPS_PROVIDER)) {

            return;
        }

        owner.m_serviceStatus = TrackerService.ServiceStatus.HAS_LOCATION;
        m_lastLocation = location;

        if (ActivityCompat.checkSelfPermission(owner, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
            owner.m_gpsStatus = owner.m_locationManager.getGpsStatus(owner.m_gpsStatus);
        }
    }


    /** Callback on UI thread
     *
     * Tracker Service in observing this Tracker Task
     * then, when passes the time this task will notify the service
     * and the service will callback to Activity
     *
     */
    private void callbackOnUIThreadByTime (int time) {
        timerToNotifyObservers.schedule(new TimerTask() {
            @Override
            public void run() {
                if (Battery.getBatteryPercentage(owner.getContext()) > MIN_BATTERY_PERCENTAGE) {

//                    Logger.d("onTaskUpdate");
//                    Logger.d("pool size: " + owner.m_sensorDataQueue.size());

                    SensorGpsDataItem sdi;

                    while ((sdi = owner.m_sensorDataQueue.poll()) != null) {
                        if (sdi.getGpsLat() == SensorGpsDataItem.NOT_INITIALIZED) {
                            handlePredict(sdi);
                        } else {
                            handleUpdate(sdi);
                            Location loc = locationAfterUpdateStep(sdi);
                            onLocationChangedImp(loc);
                        }
                    }

                    setChanged();
                    notifyObservers(m_lastLocation);

                }else {
                    Logger.d("battery is too low (lower than " + MIN_BATTERY_PERCENTAGE + "%");
                }
            }
        }, 0, time);
    }

    /** Send to Aws IoT in another thread
     *
     * This Tracker Task will evaluate if the battery is lower than minimun,
     * if not, will try to get information Gps from sensors data list collected
     * on Tracker Service and use the position collected with kalman filter. If Gps
     * sensor was not initialized, will user the kalman filter to predict the position.
     * Then, after passes the time this task will send a message to Aws IoT with the
     * position filtered.
     *
     */
    private void sendToAwsIotInBackgroundByTime (int time) {
        timerToSendToAwsIot.schedule(new TimerTask() {
            @Override
            public void run() {
//                Logger.d("SEND TO AWS IoT: " + m_lastLocation);
                awsIot.sendPosition(m_lastLocation);
            }
        }, 0 , time);
    }

    void startTask () {
//        Logger.d("startTask");

        timerToNotifyObservers = new Timer();
        timerToSendToAwsIot = new Timer();

        callbackOnUIThreadByTime (intervalToCallbackOnUiThread);
        sendToAwsIotInBackgroundByTime (intervalToSendToAws);

    }

    void stopTask () {
        deleteObservers();

        if (timerToSendToAwsIot != null) {
            timerToSendToAwsIot.cancel();
        }

        if (timerToNotifyObservers != null) {
            timerToNotifyObservers.cancel();
        }

        awsIot.disconnectAWSIot();
    }


}
