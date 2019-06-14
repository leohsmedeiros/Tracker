package br.com.phonetracker.lib;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ShutdownBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (TrackerBackgroundService.runningInstance != null &&
            TrackerBackgroundService.trackerSender != null ) {

            TrackerBackgroundService.runningInstance.sendLastMessage();
            TrackerBackgroundService.trackerSender.sender.disconnect();
        }


//        TrackerSettings trackerSettings = TrackerSharedPreferences.load(context, TrackerSettings.class);
//
//        if (trackerSettings != null) {
//            trackerSettings.setShouldAutoRestart(false);
//            TrackerSharedPreferences.save(context, trackerSettings);
//        }
//
//        TrackerBackgroundService.runningInstance.stopSelf();
//        TrackerSharedPreferences.remove(context, TrackerSettings.class);

    }
}
