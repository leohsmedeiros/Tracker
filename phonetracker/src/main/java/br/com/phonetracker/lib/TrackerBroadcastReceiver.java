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
import android.os.Build;

import br.com.phonetracker.lib.Services.ServicesHelper;
import br.com.phonetracker.lib.Services.TrackerService;
import br.com.phonetracker.lib.utils.Logger;
import br.com.phonetracker.lib.utils.TrackerSharedPreferences;


public class TrackerBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.d("Service Stops! Oooooooooooooppppssssss!!!!");

        //Not allowed to start service in Background if api version is higher than 26
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {

            TrackerSettings trackerSettings = TrackerSharedPreferences.load(context, TrackerSettings.class);

            if (trackerSettings != null && trackerSettings.isSettedToRestart()) {

                context.startService(new Intent(context, TrackerService.class));

//                ServicesHelper.getLocationService(context, value -> {
//                    Logger.d("getLocationService");
//                    if (!value.IsRunning()) {
//                        Logger.d("not running");
//                        value.stop();
//                        value.reset(trackerSettings.getKalmanSettings()); //warning!! here you can adjust your filter behavior
//                        value.start();
//                    }else {
//                        Logger.d("is running");
//                    }
//                });


            }

        }
    }

}
