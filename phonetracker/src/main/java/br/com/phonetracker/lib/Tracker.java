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
import android.location.Location;
import android.support.annotation.RequiresPermission;

import br.com.phonetracker.lib.commons.GeohashRTFilter;
import br.com.phonetracker.lib.interfaces.LocationServiceInterface;
import br.com.phonetracker.lib.services.AwsIotSettings;
import br.com.phonetracker.lib.services.TrackerService;
import br.com.phonetracker.lib.commons.Logger;
import br.com.phonetracker.lib.commons.TrackerSharedPreferences;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.*;

public class Tracker implements LocationServiceInterface {
    public interface GeoHashFilterLocationListener {
        void onGeoHashFilterUpdate (List<Location> locationsFiltered);
    }


    private List<GeoHashFilterLocationListener> listenersToGeohash;
    private Context context;
    private TrackerSettings trackerSettings;
    private GeohashRTFilter m_geoHashRTFilter;


    private Tracker(Context context, TrackerSettings trackerSettings) {
        this.context = context;
        this.listenersToGeohash = new ArrayList<>();
        this.trackerSettings = trackerSettings;
        TrackerSharedPreferences.save(context, trackerSettings);

        KalmanSettings kalmanSettings = trackerSettings.getKalmanSettings();

        m_geoHashRTFilter = new GeohashRTFilter(kalmanSettings.getGeoHashPrecision(),
                                                kalmanSettings.getGeoHashMinPointCount());

    }

    public void addLocationServiceInterface(LocationServiceInterface locationServiceInterface) {
        TrackerService.addInterface(locationServiceInterface);
    }

    public void removeLocationServiceInterface(LocationServiceInterface locationServiceInterface) {
        TrackerService.removeInterface(locationServiceInterface);
    }

    public void addListenerToGeohash(GeoHashFilterLocationListener listener) {
        if(!listenersToGeohash.contains(listener))
            listenersToGeohash.add(listener);
    }

    public void removeListenerToGeohash(GeoHashFilterLocationListener listener) {
        listenersToGeohash.remove(listener);
    }

    public void changeTrackerId (String trackerID) {
        TrackerSettings trackerSettings = TrackerSharedPreferences.load(context, TrackerSettings.class);

        if (trackerSettings != null) {
            trackerSettings.setTrackedId(trackerID);
            TrackerSharedPreferences.save(context, trackerSettings);
        }
    }

    @RequiresPermission(allOf = {ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION})
    public void startTracking () {
        if (trackerSettings != null) {
            TrackerSharedPreferences.save(context, trackerSettings);
        }

        Intent mServiceIntent = new Intent(context, TrackerService.class);

        Logger.d("isServiceRunning? " + (isServiceRunning()));

        //To not running the same service twice
        if (!isServiceRunning()) {
            Logger.d("startService");
            context.startService(mServiceIntent);
        }

        m_geoHashRTFilter.reset();
        TrackerService.addInterface(this);
    }

    public void stopTracking () {
        Logger.e("stopTracking");

        if (TrackerService.runningInstance != null) {

            Logger.e("runningInstance != null");

            TrackerSettings trackerSettings = TrackerSharedPreferences.load(context, TrackerSettings.class);

            if (trackerSettings != null) {
                trackerSettings.setShouldRestartIfKilled(false);
                TrackerSharedPreferences.save(context, trackerSettings);
            }

            TrackerService.runningInstance.stopSelf();

            TrackerSharedPreferences.remove(context, TrackerSettings.class);

//            context.stopService(mServiceIntent);
        }else {

            Logger.e("runningInstance == null");

        }

        m_geoHashRTFilter.stop();
        TrackerService.removeInterface(this);
    }

    public boolean isServiceRunning() {
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

    @Override
    public void locationChanged(Location location) {
        m_geoHashRTFilter.filter(location);

        for (GeoHashFilterLocationListener listener : listenersToGeohash) {
            listener.onGeoHashFilterUpdate(m_geoHashRTFilter.getGeoFilteredTrack());
        }
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

        public Builder intervalInSecondsToSendLocation(int intervalInSeconds) throws IllegalArgumentException {
            if (intervalInSeconds < 0)
                throw new IllegalArgumentException("Min interval to send to IoT is 0");

            trackerSettings.setIntervalInSeconds(intervalInSeconds * 1000);
            return this;
        }

        public Builder enableRestartIfKilled () {
            trackerSettings.setShouldRestartIfKilled(true);
            return this;
        }

        public Builder enableToSendSpeed () {
            trackerSettings.setShouldSendSpeed(true);
            return this;
        }

        public Builder enableToSendDirection () {
            trackerSettings.setShouldSendDirection(true);
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
