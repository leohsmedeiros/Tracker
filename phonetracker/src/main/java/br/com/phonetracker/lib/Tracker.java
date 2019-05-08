package br.com.phonetracker.lib;


/**
 * TRACKER
 *
 * Init a background service to send the device position using an Aws Iot API.
 *
 * Can be configured to restart automatically if user kills the app.
 *
 * Can also restart if Android Api level is lower than 26, because on higher Api levels there's a security
 * component that kill the service anyway.
 *
 *
 * @author Leonardo Medeiros
 */


import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.support.annotation.RequiresPermission;

import br.com.phonetracker.lib.interfaces.LocationServiceInterface;
import br.com.phonetracker.lib.services.AwsIotSettings;
import br.com.phonetracker.lib.services.TrackerService;
import br.com.phonetracker.lib.loggers.Logger;
import br.com.phonetracker.lib.commons.TrackerSharedPreferences;

import java.io.IOException;

import static android.Manifest.permission.*;

public class Tracker {
    private Intent mServiceIntent;
    private Context context;
    private TrackerSettings trackerSettings;

    private Tracker(Context context, TrackerSettings trackerSettings) {
//        this.context = context.getApplicationContext();
        this.context = context;
        this.trackerSettings = trackerSettings;
    }

    public void addLocationServiceInterface(LocationServiceInterface locationServiceInterface) {
        TrackerService.addInterface(locationServiceInterface);
    }


//    public void restartTracking () {
//        this.awsIot.connectAWSIot();
//    }
//
//    public void stopTracking () {
//        this.awsIot.disconnectAWSIot();
//    }

    @RequiresPermission(allOf = {ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION})
    public void startTracking () {
        TrackerSharedPreferences.save(context, trackerSettings);

        /*
        ServicesHelper.getLocationService(context, value -> {

            Logger.d("getLocationService");
            if (!value.IsRunning()) {
                Logger.d("not running");
                value.stop();
                value.reset(trackerSettings.getKalmanSettings()); //warning!! here you can adjust your filter behavior
                value.start();
            }else {
                Logger.d("is running");
            }
        });
        */


        mServiceIntent = new Intent(context, TrackerService.class);

        Logger.d("isMyServiceRunning? " + (isMyServiceRunning()));

        //To not running the same service twice
        if (!isMyServiceRunning()) {
            Logger.d("startService");
            context.startService(mServiceIntent);
        }
    }

    public void stopTracking () {
        TrackerSharedPreferences.remove(context, trackerSettings.getClass());

        /*
        ServicesHelper.getLocationService().stop();
        */

        if(mServiceIntent!=null) {
            context.stopService(mServiceIntent);
            Logger.d("onDestroy Tracker!");
        }

    }

    private boolean isMyServiceRunning() {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (TrackerService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static class Builder {
        private Context context;
        private TrackerSettings trackerSettings;

        public Builder(Context context, XmlResourceParser xmlIotClientSettings) throws IOException {
            this.context = context;
            trackerSettings = new TrackerSettings(new AwsIotSettings(xmlIotClientSettings));
        }

        public Builder trackedId (String trackedId) {
            trackerSettings.setTrackedId(trackedId);
            return this;
        }

        public Builder intervalInSeconds (int intervalInSeconds) {
            trackerSettings.setIntervalInSeconds(intervalInSeconds);
            return this;
        }

        public Builder restartIfKilled(boolean value) {
            trackerSettings.setRestartIfKilled(value);
            return this;
        }


        //region Kalman Settings
        public Builder gpsMinTimeInSeconds (int value) {
            KalmanSettings kalmanSettings = trackerSettings.getKalmanSettings();
            kalmanSettings.gpsMinTime = value * 1000;
            trackerSettings.setKalmanSettings(kalmanSettings);
            return this;
        }
        public Builder gpsMinDistanceInMeters (int value) {
            KalmanSettings kalmanSettings = trackerSettings.getKalmanSettings();
            kalmanSettings.gpsMinDistance = value;
            trackerSettings.setKalmanSettings(kalmanSettings);
            return this;
        }
        public Builder geoHashPrecision (int value) {
            KalmanSettings kalmanSettings = trackerSettings.getKalmanSettings();
            kalmanSettings.geoHashPrecision = value;
            trackerSettings.setKalmanSettings(kalmanSettings);
            return this;
        }
        //endregion Kalman Settings


        public Tracker build () {
            return new Tracker(context, trackerSettings);
        }
    }


}
