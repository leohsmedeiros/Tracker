package com.leomedeiros.trackerconsumer.dto

import android.content.Context
import br.com.phonetracker.lib.interfaces.ISender
import com.irvem.iot.AwsIot
import com.irvem.iot.AwsIotSettings
import org.json.JSONObject

class IotSender (awsIotSettings: AwsIotSettings, highQuality: Boolean, cleanSession: Boolean): ISender {

    private val awsIot: AwsIot = AwsIot(awsIotSettings, highQuality, cleanSession)

    override fun connect(context: Context, onConnect: Runnable) = awsIot.connect(context, onConnect)

    override fun isConnected(): Boolean = awsIot.isConnected

    override fun disconnect() = awsIot.disconnect()

    override fun send(obj: JSONObject?) = awsIot.send(obj)

    override fun logOnFile(message: String?) = awsIot.logOnFile(message)

}