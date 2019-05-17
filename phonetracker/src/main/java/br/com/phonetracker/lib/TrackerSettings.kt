package br.com.phonetracker.lib

import br.com.phonetracker.lib.services.AwsIotSettings
import java.io.Serializable

class TrackerSettings (val awsIotSettings: AwsIotSettings): Serializable {
    var trackedId: String? = null
    var kalmanSettings: KalmanSettings = KalmanSettings()
    var intervalInSeconds: Int = 10
    var shouldRestartIfKilled: Boolean = false
    var shouldSendSpeed: Boolean = false
    var shouldSendDirection: Boolean = false
}