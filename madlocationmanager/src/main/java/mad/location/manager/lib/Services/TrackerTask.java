package mad.location.manager.lib.Services;

import mad.location.manager.lib.utils.Logger;

import java.util.Observer;
import java.util.Timer;
import java.util.TimerTask;

public class TrackerTask {
    //the time is in millisec
    private static final int SEC_TIME_BASE = 1000;

//    private enum TaskMethod { BY_TIME, BY_OBSERVER }
//
//    private TaskMethod taskMethod;

    private AwsIot awsIot;

    //used on tracker by time
    private Timer timer;

    //used on tracker by observerDevicePosition
//    private Observer observerDevicePosition;

    private int interval;

    TrackerTask (int timeInterval, AwsIot awsIot) {
        interval = timeInterval * SEC_TIME_BASE;

//        this.taskMethod = timeInterval == 0 ? TaskMethod.BY_OBSERVER : TaskMethod.BY_TIME;
        this.awsIot = awsIot;
    }

    void startTask () {
        Logger.d("startTask");

        timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {

                Logger.d("onTaskUpdate");

//                awsIot.sendPosition(kalmanLatLong);

            }
        }, interval, interval);

        /*
        switch (taskMethod) {
            case BY_TIME:
                //set a new Timer
                timer = new Timer();

                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
//                        sendPositionIfBatteryIsNotLow (deviceInfo.getPosition());
                    }
                }, interval, interval);
                break;

            case BY_OBSERVER:
//                observerDevicePosition = (observable, o) -> {
//                    if (o instanceof IKalmanLatLong) {
//                        IKalmanLatLong kalmanLatLong = (IKalmanLatLong) o;
//                        sendPositionIfBatteryIsNotLow(kalmanLatLong);
//                    }
//                };
//
//                deviceInfo.addObserverOnPosition(observerDevicePosition);
                break;
        }
        */
    }

    void stopTask () {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }


        /*
        switch (taskMethod) {
            case BY_TIME:
                //stop the timer, if it's not already null
                break;

            case BY_OBSERVER:
//                if (observerDevicePosition != null) {
//                    Logger.d("removeObserverOnPosition");
//                    deviceInfo.removeObserverOnPosition(observerDevicePosition);
//                    observerDevicePosition = null;
//                }
                break;
        }
        */

        awsIot.disconnectAWSIot();
    }

//    private void sendPositionIfBatteryIsNotLow (IKalmanLatLong kalmanLatLong) {
//        int battery = deviceInfo.getBatteryPercentage();
//        Logger.d("battery: " + battery);
//
//        //only send position if battery is above 10%
//        if (battery > 10) {
//            awsIot.sendPosition(kalmanLatLong);
//        }
//    }
}
