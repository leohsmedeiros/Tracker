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

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresPermission;

import br.com.phonetracker.lib.commons.KalmanSettings;
import br.com.phonetracker.lib.commons.Logger;
import br.com.phonetracker.lib.commons.TrackerSharedPreferences;
import br.com.phonetracker.lib.interfaces.ISender;
import br.com.phonetracker.lib.interfaces.TrackerGeoHashListener;
import br.com.phonetracker.lib.interfaces.TrackerLocationListener;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.KILL_BACKGROUND_PROCESSES;

public class Tracker {

    private Context context;
    private KalmanSettings kalmanSettings;
    private ISender sender;
    private TrackerSettings senderSettings;


    private Tracker(Context context, KalmanSettings kalmanSettings, ISender sender, TrackerSettings senderSettings) {
        this.context = context;
        this.kalmanSettings = kalmanSettings;
        this.sender = sender;
        this.senderSettings = senderSettings;
    }

    public void addLocationListener(TrackerLocationListener listener) {
        TrackerBackgroundService.listenerToLocation = listener;
    }

    public void removeLocationListener() {
        TrackerBackgroundService.listenerToLocation = null;
    }

    public void addGeohashListener(TrackerGeoHashListener listener) {
        TrackerBackgroundService.listenerToGeohash = listener;
    }

    public void removeGeohashListener() {
        TrackerBackgroundService.listenerToGeohash = null;
    }

    public void setTrackedStatus(TrackerSettings.Status status) {
        senderSettings.setStatus(status);

        Intent mServiceIntent = new Intent(context, TrackerBackgroundService.class);
        mServiceIntent.putExtra(TrackerBackgroundService.Config.TRACKER_STATUS.toString(), senderSettings.getStatus().toString());
        context.startService(mServiceIntent);
    }

    public void setTrackedOnTravelStatus(boolean onTravel) {
        senderSettings.setOnTravel(onTravel);

        Intent mServiceIntent = new Intent(context, TrackerBackgroundService.class);
        mServiceIntent.putExtra(TrackerBackgroundService.Config.TRACKER_ON_TRAVEL.toString(), onTravel);
        context.startService(mServiceIntent);
    }

    public boolean isActive() {
        return senderSettings.getStatus().equals(TrackerSettings.Status.ONLINE);
    }

    @RequiresPermission(allOf = {ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, KILL_BACKGROUND_PROCESSES})
    public void startTracking() {
        Logger.d("startTracking");
        sender.logOnFile("startTracking");

        TrackerSharedPreferences.save(context, senderSettings);
        TrackerSharedPreferences.save(context, kalmanSettings);

        Intent mServiceIntent = new Intent(context, TrackerBackgroundService.class);

        TrackerBackgroundService.setTrackerSender(sender);
        mServiceIntent.putExtra(TrackerBackgroundService.Config.TRACKER_SETTINGS.toString(), senderSettings);
        mServiceIntent.putExtra(TrackerBackgroundService.Config.KALMAN_SETTINGS.toString(), kalmanSettings);

        context.startService(mServiceIntent);
    }

    public void stopTracking() {
        TrackerSharedPreferences.remove(context, TrackerSettings.class);
        TrackerSharedPreferences.remove(context, KalmanSettings.class);

        Intent mServiceIntent = new Intent(context, TrackerBackgroundService.class);
        mServiceIntent.putExtra(TrackerBackgroundService.Config.FORCE_STOP_TRACKER.toString(), "stop");
        context.startService(mServiceIntent);

        Logger.d("stopTracking");
        sender.logOnFile("stopTracking");
    }

    public static TrackerSettings getTrackerSettingsOnCache (Context context) {
        return TrackerSharedPreferences.load(context, TrackerSettings.class);
    }


    public static class Builder {
        private Context context;
        private KalmanSettings kalmanSettings;
        private ISender sender = null;
        private TrackerSettings settings;

        public Builder(Context context) {
            this.context = context;
            kalmanSettings = new KalmanSettings();
            settings = new TrackerSettings.Builder().build();
        }

        /**
         * Set a TrackerSettings to send the user location filtered.
         * Default is null.
         */
        public Builder sender(@NonNull ISender sender) {
            this.sender = sender;
            return this;
        }

        /**
         * Set a TrackerSettings to send the user location filtered.
         * Default is null.
         */
        public Builder settings(@NonNull TrackerSettings settings) {
            this.settings = settings;
            return this;
        }


        public Tracker build() {
            return new Tracker(context, kalmanSettings, sender, settings);
        }
    }
}
