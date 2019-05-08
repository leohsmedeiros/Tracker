package br.com.phonetracker.lib

import br.com.phonetracker.lib.Services.AwsIotSettings
import java.io.Serializable

class TrackerSettings (val awsIotSettings: AwsIotSettings): Serializable {
    var kalmanSettings: KalmanSettings = KalmanSettings()
    var restartIfKilled: Boolean = true
    var intervalInSeconds = 10
    var trackedId: String? = null
}