package br.com.phonetracker.lib;

/**
 * TRACKER
 * <p>
 * Init a background service to send the device location.
 * <p>
 * Can be configured to restart automatically if user kills the app.
 * <p>
 * Can also restart if user reboot the device, but only if Android Api level is lower than 26,
 * because on higher Api levels there's a security component that kill the service anyway.
 *
 * @author Leonardo Medeiros
 */

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresPermission;

import br.com.phonetracker.lib.commons.KalmanSettings;
import br.com.phonetracker.lib.commons.Logger;
import br.com.phonetracker.lib.commons.TrackerSharedPreferences;
import br.com.phonetracker.lib.interfaces.GeoHashFilterLocationListener;
import br.com.phonetracker.lib.interfaces.LocationTrackerListener;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.KILL_BACKGROUND_PROCESSES;

public class Tracker {

    private Context context;
    private TrackerSettings trackerSettings;

    private Tracker(Context context, TrackerSettings trackerSettings, TrackerSender sender) {
        this.context = context;
        this.trackerSettings = trackerSettings;

        TrackerSharedPreferences.save(context, trackerSettings);
        TrackerBackgroundService.trackerSender = sender;
    }

    public void addLocationServiceInterface(LocationTrackerListener locationTrackerListener) {
        if (!TrackerBackgroundService.listenersToLocationTracker.contains(locationTrackerListener))
            TrackerBackgroundService.listenersToLocationTracker.add(locationTrackerListener);
    }

    public void removeLocationServiceInterface(LocationTrackerListener locationTrackerListener) {
        TrackerBackgroundService.listenersToLocationTracker.remove(locationTrackerListener);
    }

    public void addListenerToGeohash(GeoHashFilterLocationListener listener) {
        if (!TrackerBackgroundService.listenersToGeohash.contains(listener))
            TrackerBackgroundService.listenersToGeohash.add(listener);
    }

    public void removeListenerToGeohash(GeoHashFilterLocationListener listener) {
        TrackerBackgroundService.listenersToGeohash.remove(listener);
    }

    public void changeTrackedId (String trackedId) {
        if (TrackerBackgroundService.trackerSender != null) {
            TrackerBackgroundService.trackerSender.setTrackedId(trackedId);
        }
    }
    public void changeTrackedStatus (boolean active) {
        if (TrackerBackgroundService.trackerSender != null) {
            TrackerBackgroundService.trackerSender.isActive = active;
        }

        if(TrackerBackgroundService.runningInstance != null) {
            TrackerBackgroundService.runningInstance.sendInformation();
        }
    }


    @RequiresPermission(allOf = {ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, KILL_BACKGROUND_PROCESSES})
    public void startTracking() {
        Intent mServiceIntent = new Intent(context, TrackerBackgroundService.class);

        if (this.trackerSettings.getShouldAutoRestart()) {
            mServiceIntent.setAction("uk.ac.shef.oak.ActivityRecognition.RestartSensor");
            mServiceIntent.setAction("android.intent.action.BOOT_COMPLETED");
        }

        String pid = findServiceRunning();

        if (pid != null) {
            try {
                ActivityManager actvityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (actvityManager != null)
                    actvityManager.killBackgroundProcesses(pid);// pkgn is a process id /////
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        context.startService(mServiceIntent);
    }

    public void stopTracking() {
        Logger.d("stopTracking");

        if(TrackerBackgroundService.runningInstance != null) {
            TrackerSettings trackerSettings = TrackerSharedPreferences.load(context, TrackerSettings.class);

            if(trackerSettings != null) {
                trackerSettings.setShouldAutoRestart(false);
                TrackerSharedPreferences.save(context, trackerSettings);
            }

            TrackerBackgroundService.runningInstance.stopSelf();
            TrackerSharedPreferences.remove(context, TrackerSettings.class);
        }
    }


    private String findServiceRunning() {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if(manager != null) {
            for(ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if(TrackerBackgroundService.class.getName().equals(service.service.getClassName())) {
                    return service.service.getPackageName();
                }
            }
        }
        return null;
    }


    public static class Builder {
        private Context context;
        private TrackerSettings trackerSettings;
        private TrackerSender sender = null;

        public Builder(Context context) throws Exception {
            this.context = context;
            trackerSettings = new TrackerSettings();
        }

        /**
         * Set a TrackerSender to send the user location filtered.
         * Default is null.
         */
        public Builder sender(@NonNull TrackerSender sender) {
            this.sender = sender;
            return this;
        }

        /**
         * Enable tracker auto restart if user kill the application or restart the device.
         * Default is false.
         */
        public Builder enableAutoRestart() {
            trackerSettings.setShouldAutoRestart(true);
            return this;
        }

        /**
         * Frequency in seconds that callback will be activated.
         * Must be higher or equal to 1
         * Default is 2 secs.
         */
        public Builder intervalToCallbackInSeconds(int time) throws IllegalArgumentException {
            if(time < 1)
                throw new IllegalArgumentException("Min time to Gps position is 1");

            KalmanSettings kalmanSettings = trackerSettings.getKalmanSettings();
            kalmanSettings.gpsMinTime = time * 1000;
            trackerSettings.setKalmanSettings(kalmanSettings);
            return this;
        }


        /**
         * Min distance in meters to be detected by gps.
         * Must be higher or equal to 0
         * Default is 0.
         */
        public Builder gpsMinDistanceInMeters(int value) throws IllegalArgumentException {
            if(value < 0)
                throw new IllegalArgumentException("Min distance to Gps distance is 0");

            KalmanSettings kalmanSettings = trackerSettings.getKalmanSettings();
            kalmanSettings.gpsMinDistance = value;
            trackerSettings.setKalmanSettings(kalmanSettings);
            return this;
        }

        /**
         * Precision of geo hash (count of decimal numbers).
         * Must be higher or equal to 1
         * Default is 6.
         */
        public Builder geoHashPrecision(int precision) throws IllegalArgumentException {
            if(precision < 1)
                throw new IllegalArgumentException("Min Geo Hash precision is 1");

            KalmanSettings kalmanSettings = trackerSettings.getKalmanSettings();
            kalmanSettings.geoHashPrecision = precision;
            trackerSettings.setKalmanSettings(kalmanSettings);
            return this;
        }

        public Tracker build() {
            return new Tracker(context, trackerSettings, sender);
        }
    }
}
