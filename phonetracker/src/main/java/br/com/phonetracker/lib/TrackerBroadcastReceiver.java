package br.com.phonetracker.lib;

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

import br.com.phonetracker.lib.commons.Logger;
import br.com.phonetracker.lib.commons.TrackerSharedPreferences;


public class TrackerBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.d("*************   TrackerBroadcastReceiver   *************");
        context.startService(new Intent(context, TrackerBackgroundService.class));
        Logger.d("restarting service");
    }

}
