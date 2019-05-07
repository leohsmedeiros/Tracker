package mad.location.manager.lib;

/**
 * PHONE TRACKER SETTINGS
 *
 * This class is necessary to configure Tracker class.
 *
 *
 * @author Leonardo Medeiros
 */

import java.io.IOException;
import java.io.Serializable;

import mad.location.manager.lib.Services.AwsIotSettings;
import mad.location.manager.lib.Services.TrackerService;

public class TrackerSettings implements Serializable {

    private AwsIotSettings awsIotSettings;
    private TrackerService.Settings kalmanSettings;
    private Boolean restartIfKilled = true;
    private int intervalInSeconds = 10;
    private String trackedId = null;

    private TrackerSettings() { }

    TrackerSettings(AwsIotSettings awsIotSettings) throws IOException {
        this.awsIotSettings = awsIotSettings;
        this.kalmanSettings = new KalmanSettingsFactory().buildSettings();
    }

    public AwsIotSettings getAwsIotSettings () {
        return awsIotSettings;
    }

    void setRestartIfKilled(boolean autoStartIfKilled){
        this.restartIfKilled = autoStartIfKilled;
    }

    public boolean isSettedToRestart() {
        return restartIfKilled;
    }

    void setIntervalInSeconds (int interval) {
        this.intervalInSeconds = interval;
    }

    public int getIntervalInSeconds () {
        return intervalInSeconds;
    }

    void setTrackedId (String trackedId) {
        this.trackedId = trackedId;
    }

    public String getTrackedId () {
        return trackedId;
    }

    void setKalmanSettings (TrackerService.Settings kalmanSettings) {
        this.kalmanSettings = kalmanSettings;
    }

    public TrackerService.Settings getKalmanSettings () {
        return kalmanSettings;
    }

}
