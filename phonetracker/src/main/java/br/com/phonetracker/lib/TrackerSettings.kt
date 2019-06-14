package br.com.phonetracker.lib

import br.com.phonetracker.lib.commons.KalmanSettings
import java.io.Serializable

class TrackerSettings : Serializable {
    var kalmanSettings: KalmanSettings = KalmanSettings()
    var shouldAutoRestart: Boolean = false
}