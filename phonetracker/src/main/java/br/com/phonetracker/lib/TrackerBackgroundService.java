package br.com.phonetracker.lib;

import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import br.com.phonetracker.lib.commons.KalmanSettings;
import br.com.phonetracker.lib.commons.Logger;
import br.com.phonetracker.lib.interfaces.ISender;
import br.com.phonetracker.lib.interfaces.TrackerGeoHashListener;
import br.com.phonetracker.lib.interfaces.TrackerLocationListener;

/**
 * The responsibility of this service is to extend the PositionCollectService and use the iot
 * to send the position collected
 */

public class TrackerBackgroundService extends PositionCollectorService {

    public enum Config { TRACKER_SETTINGS, TRACKER_STATUS, TRACKER_ON_TRAVEL, KALMAN_SETTINGS, FORCE_STOP_TRACKER }

    static TrackerLocationListener listenerToLocation;
    static TrackerGeoHashListener listenerToGeohash;

    private static TrackerSettings trackerSettings;

    //used to send position to Aws IoT in background
    private Handler loopToSendInformation = new Handler();

    private static ISender sender;




    public static void setTrackerSender(@NonNull ISender isender) {
        if (sender != null && sender.isConnected())
            sender.disconnect();

        sender = isender;
    }

    public void sendMessage() {

        sender.logOnFile("------ sendMessage ------");
        sender.logOnFile("current location: " + m_lastLocation);

        if (trackerSettings == null) {

            sender.logOnFile("error: trackerSettings is null");
            stopSelf();

        } else if (m_lastLocation != null) {

            JSONObject telemetric = new JSONObject();
            JSONArray coordinate = new JSONArray();

            try {
                coordinate.put(m_lastLocation.getLongitude());
                coordinate.put(m_lastLocation.getLatitude());

                if (trackerSettings.getTrackedId() != null)
                    telemetric.put("trackedId", trackerSettings.getTrackedId());

                telemetric.put("status", trackerSettings.getStatus().toString());

                telemetric.put("onTravel", trackerSettings.isOnTravel());

                telemetric.put("coordinates", coordinate);

                if (trackerSettings.sendSpeed)
                    telemetric.put("speed", m_lastLocation.getSpeed());

                if (trackerSettings.sendDirection)
                    telemetric.put("direction", m_lastLocation.getBearing());

            } catch (JSONException e) {
                e.printStackTrace();
            }

//            if (sender.isConnected())

//            if(!BuildConfig.DEBUG)
                sender.send(telemetric);

        }

        sender.logOnFile("------------------------------------------");
    }


    void sendToListeners () {
        Logger.d("m_lastLocation: " + m_lastLocation);
        if (m_lastLocation != null) {
            if (listenerToLocation != null)
                listenerToLocation.locationChanged(m_lastLocation);

            List<Location> geoHashFilteredPositions = super.getGeoHashFiltered();

            if(geoHashFilteredPositions != null && listenerToGeohash != null)
                listenerToGeohash.onGeoHashFilterUpdate(geoHashFilteredPositions);
        }
    }


    @Override
    protected void calculatePositionToSend() {
        super.calculatePositionToSend();

        Logger.d("m_lastLocation: " + m_lastLocation);
        if (trackerSettings != null)
            Logger.d("frequency: " + trackerSettings.getFrequency());

        sendMessage();
        sendToListeners();

        if (serviceStatus != Status.DESTROYED && trackerSettings != null)
            loopToSendInformation.postDelayed(this::calculatePositionToSend, trackerSettings.getFrequency());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.d("onStartCommand");

        if (intent == null || intent.hasExtra(Config.FORCE_STOP_TRACKER.toString())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent.hasExtra(Config.TRACKER_SETTINGS.toString())) {
            if (sender != null) {
                sender.logOnFile("started background service");
                trackerSettings = (TrackerSettings) intent.getSerializableExtra(Config.TRACKER_SETTINGS.toString());

                super.startPositionCollector(kalmanSettings);

                Logger.d("trackerSettings: " + trackerSettings);

                if (sender.isConnected())
                    sender.disconnect();

                sender.connect(this, () -> {
                    if (loopToSendInformation != null)
                        loopToSendInformation.removeCallbacksAndMessages(null);

                    loopToSendInformation = new Handler(Looper.getMainLooper());
                    loopToSendInformation.post(this::calculatePositionToSend);
                });
            }
        }

        if (intent.hasExtra(Config.KALMAN_SETTINGS.toString())) {
            kalmanSettings = (KalmanSettings) intent.getSerializableExtra(Config.KALMAN_SETTINGS.toString());
        }

        if (intent.hasExtra(Config.TRACKER_STATUS.toString()) && trackerSettings != null) {
            trackerSettings.setStatus(intent.getStringExtra(Config.TRACKER_STATUS.toString()));
            sender.logOnFile("changed status to " + trackerSettings.getStatus());

            if (loopToSendInformation != null) {
                loopToSendInformation.removeCallbacksAndMessages(null);
                loopToSendInformation.post(this::calculatePositionToSend);
            }

        }

        if (intent.hasExtra(Config.TRACKER_ON_TRAVEL.toString()) && trackerSettings != null) {
            trackerSettings.setOnTravel(intent.getBooleanExtra(Config.TRACKER_ON_TRAVEL.toString(), false));
            sender.logOnFile("changed on travel to " + trackerSettings.isOnTravel());


            if (loopToSendInformation != null) {
                loopToSendInformation.removeCallbacksAndMessages(null);
                loopToSendInformation.post(this::calculatePositionToSend);
            }
        }

        serviceStatus = Status.STARTED;

        // START_NOT_STICKY:  Will not restart after process was killed
        // START_STICKY:  Will restart after process was killed
        return (trackerSettings == null || sender == null) ? START_NOT_STICKY : START_STICKY;

    }

    private void stop () {
        super.clear();

        if (sender != null)
            sender.logOnFile("**** send broadcast ****");

        if (loopToSendInformation != null)
            loopToSendInformation.removeCallbacksAndMessages(null);


        if (trackerSettings != null) {
            if (sender != null && sender.isConnected()) {
                trackerSettings.setStatus(TrackerSettings.Status.OFFLINE);
                sendMessage();
                sender.disconnect();
            }
        }

        if (sender != null)
            sender.logOnFile("finished background service");

        serviceStatus = Status.DESTROYED;
    }

    @Override
    public void onDestroy() {
        Logger.d("onDestroy");
        stop();

        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Logger.d("onTaskRemoved");
        stop();

        super.onTaskRemoved(rootIntent);
    }


}
