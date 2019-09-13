package br.com.phonetracker.lib.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import br.com.phonetracker.lib.TrackerBackgroundService;
import br.com.phonetracker.lib.commons.Logger;
import br.com.phonetracker.lib.commons.TrackerSharedPreferences;

public class ShutdownBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.d("*************   ShutdownBroadcastReceiver   *************");

        Intent mServiceIntent = new Intent(context, TrackerBackgroundService.class);
        mServiceIntent.putExtra(TrackerBackgroundService.Config.FORCE_STOP_TRACKER.toString(), "");
        context.startService(new Intent(context, TrackerBackgroundService.class));


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
