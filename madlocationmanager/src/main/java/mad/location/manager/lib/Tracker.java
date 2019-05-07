package mad.location.manager.lib;


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

import mad.location.manager.lib.Services.AwsIotSettings;
import mad.location.manager.lib.Services.KalmanLocationService;
import mad.location.manager.lib.Services.ServicesHelper;
import mad.location.manager.lib.Services.TrackerService;
import mad.location.manager.lib.utils.Logger;
import mad.location.manager.lib.utils.TrackerSharedPreferences;

import java.io.IOException;

import static android.Manifest.permission.*;

public class Tracker {
    private Intent mServiceIntent;
    private Context context;
    private TrackerSettings trackerSettings;

    private KalmanLocationService kalmanLocationService;

    private Tracker(Context context, TrackerSettings trackerSettings) {
//        this.context = context.getApplicationContext();
        this.context = context;
        this.trackerSettings = trackerSettings;
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


//        TrackerService trackerService = new TrackerService();
        mServiceIntent = new Intent(context, TrackerService.class);

        Logger.d("isMyServiceRunning? " + (isMyServiceRunning(TrackerService.class)));

        //To not running the same service twice
        if (!isMyServiceRunning(TrackerService.class)) {
            Logger.d("startService");
            context.startService(mServiceIntent);
        }
    }

    public void stopTracking () {
        TrackerSharedPreferences.remove(context, trackerSettings.getClass());

        /*
        ServicesHelper.getLocationService().stop();
        */

//        if(mServiceIntent!=null) {
//            context.stopService(mServiceIntent);
//            Logger.d("onDestroy Tracker!");
//        }

    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
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

        public Builder kalmanSettings (XmlResourceParser xmlKalmanSettings) throws IOException  {
            trackerSettings.setKalmanSettings(new KalmanSettingsFactory().buildSettings(xmlKalmanSettings));
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

        public Tracker build () {
            return new Tracker(context, trackerSettings);
        }
    }


}
