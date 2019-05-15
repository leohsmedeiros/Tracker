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
import br.com.phonetracker.lib.commons.Logger;
import br.com.phonetracker.lib.commons.TrackerSharedPreferences;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static android.Manifest.permission.*;

public class Tracker {
    private Intent mServiceIntent;
    private Context context;
    private TrackerSettings trackerSettings;

    private Tracker(Context context, TrackerSettings trackerSettings) {
        this.context = context;
        this.trackerSettings = trackerSettings;
    }

    public void addLocationServiceInterface(LocationServiceInterface locationServiceInterface) {
        TrackerService.addInterface(locationServiceInterface);
    }

    public void removeLocationServiceInterface(LocationServiceInterface locationServiceInterface) {
        TrackerService.removeInterface(locationServiceInterface);
    }

    @RequiresPermission(allOf = {ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION})
    public void startTracking () {
        TrackerSharedPreferences.save(context, trackerSettings);

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

        public Builder(@NotNull Context context, @NotNull XmlResourceParser xmlIotClientSettings) throws IOException {
            this.context = context;
            trackerSettings = new TrackerSettings(new AwsIotSettings(xmlIotClientSettings));
        }

        public Builder trackedId (String trackedId) {
            trackerSettings.setTrackedId(trackedId);
            return this;
        }

        public Builder restartIfKilled(boolean value) {
            trackerSettings.setRestartIfKilled(value);
            return this;
        }

        public Builder intervalInSeconds (int intervalInSeconds) throws IllegalArgumentException {
            if (intervalInSeconds < 0)
                throw new IllegalArgumentException("Min interval to send to IoT is 0");

            trackerSettings.setIntervalInSeconds(intervalInSeconds * 1000);
            return this;
        }


        //region Kalman Settings
        public Builder gpsMinTimeInSeconds (int value) throws IllegalArgumentException {
            if (value < 1)
                throw new IllegalArgumentException("Min time to Gps position is 1");

            KalmanSettings kalmanSettings = trackerSettings.getKalmanSettings();
            kalmanSettings.gpsMinTime = value * 1000;
            trackerSettings.setKalmanSettings(kalmanSettings);
            return this;
        }
        public Builder gpsMinDistanceInMeters (int value) throws IllegalArgumentException {
            if (value < 0)
                throw new IllegalArgumentException("Min distance to Gps distance is 0");

            KalmanSettings kalmanSettings = trackerSettings.getKalmanSettings();
            kalmanSettings.gpsMinDistance = value;
            trackerSettings.setKalmanSettings(kalmanSettings);
            return this;
        }
        public Builder geoHashPrecision (int value) throws IllegalArgumentException {
            if (value < 1)
                throw new IllegalArgumentException("Min Geo Hash precision is 1");

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
