package com.leomedeiros.trackerconsumer.dto

import android.content.Context
import br.com.phonetracker.lib.interfaces.ISender
import com.irvem.iot.AwsIot
import com.irvem.iot.AwsIotSettings
import org.json.JSONObject

class IoTSender (val awsIotSettings: AwsIotSettings): ISender {
    private var awsIot: AwsIot = AwsIot(awsIotSettings)


    override fun connect(context: Context?) {
        awsIot.connect(context)
    }

    override fun isConnected(): Boolean {
        return awsIot.isConnected
    }

    override fun disconnect() {
        awsIot.disconnect()
    }

    override fun send(`object`: JSONObject?) {
        awsIot.send(`object`)
    }
}