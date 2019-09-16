package br.com.phonetracker.lib.broadcast;

/**
 * TRACKER BROADCAST RECEIVER
 *
 * It is another background service, that will receive a signal when the task was killed
 * and will restart automatically (if it was configured on TrackerSettings)
 *
 *
 * @author Leonardo Medeiros
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import br.com.phonetracker.lib.TrackerBackgroundService;
import br.com.phonetracker.lib.TrackerSettings;
import br.com.phonetracker.lib.commons.Logger;
import br.com.phonetracker.lib.commons.TrackerSharedPreferences;

public class RestartBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.d("*************   RestartBroadcastReceiver   *************");

        TrackerSettings trackerSettings = TrackerSharedPreferences.load(context, TrackerSettings.class);

        if (trackerSettings != null) {

            Intent mServiceIntent = new Intent(context, TrackerBackgroundService.class);
            mServiceIntent.putExtra(TrackerBackgroundService.Config.TRACKER_SETTINGS.toString(), trackerSettings);
            context.startService(mServiceIntent);
            Logger.d("restarting service");

        }else {
            Logger.d("trackerSettings is null");
        }
    }
}
