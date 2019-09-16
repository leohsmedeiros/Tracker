package com.irvem.iot;


/**
 * AWS IOT
 *
 * Will user an object of AwsIotSettings to initialize AwsMobileClient and to
 * send device position to AwsIot.
 *
 *
 * @author Leonardo Medeiros
 */


import android.content.Context;
import android.os.Environment;
import android.support.annotation.NonNull;

import com.amazonaws.mobile.auth.core.internal.util.ThreadUtils;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttLastWillAndTestament;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttMessageDeliveryCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Region;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

public class AwsIot implements Serializable {
    private static final String LOG_FILE_POSITION = "aws_iot_positions.txt";
    private static final String LOG_FILE_ERROR = "aws_iot_error.txt";
    private static final String LOG_FILE_GENERAL = "aws_iot_general.txt";

    private AwsIotSettings settings;
    private AWSIotMqttManager mqttManager;

    private String clientId;
    private boolean connected = false;
    private boolean highQuality;
    private boolean cleanSession;

    /**
     * @param settings settings like topic, password, region, etc
     * @param highQuality true will set QoS1 (Messages will be delivered at least once), false will set QoS0 (don't check if message was delivered)
     * @param cleanSession the cleansession true tells the broker to do not persist the data, so it clears the db for the client who disconnects
     */

    public AwsIot(AwsIotSettings settings, boolean highQuality, boolean cleanSession) {
        this.settings = settings;
        this.highQuality = highQuality;
        this.cleanSession = cleanSession;
    }


    private void initIoTClient(Context context, @NonNull Runnable onConnect) {
        clientId = UUID.randomUUID().toString();
        KeyStore clientKeyStore = null;

//        Logger.d("initIoTClient whit ID: " + clientId);

        // MQTT Client
        mqttManager = new AWSIotMqttManager(clientId, settings.getCustomerSpecificEndpoint());

        // Set keepalive to 10 seconds.  Will recognize disconnects more quickly but will also send
        // MQTT pings every 10 seconds.
        mqttManager.setKeepAlive(10);

        mqttManager.setOfflinePublishQueueEnabled(true);

        Logger.d("isOfflinePublishQueueEnabled: " + mqttManager.isOfflinePublishQueueEnabled());

        // Set Last Will and Testament for MQTT.  On an unclean disconnect (loss of connection)
        // AWS IoT will publish this message to alert other clients.

        AWSIotMqttQos qos = highQuality ? AWSIotMqttQos.QOS1 : AWSIotMqttQos.QOS0;

        AWSIotMqttLastWillAndTestament lwt = new AWSIotMqttLastWillAndTestament("my/lwt/topic",
                "Android client lost connection", qos);

        mqttManager.setMqttLastWillAndTestament(lwt);
        mqttManager.setCleanSession(cleanSession);

        Region region = Region.getRegion(settings.getMyRegion());
        // IoT Client (for creation of certificate if needed)
        AWSIotClient mIotAndroidClient = new AWSIotClient(AWSMobileClient.getInstance());
        mIotAndroidClient.setRegion(region);

        String keystorePath = context.getFilesDir().getPath();

        // To load cert/key from keystore on filesystem
        try {
            if (AWSIotKeystoreHelper.isKeystorePresent(keystorePath, settings.getKeystoreName())) {

                if (AWSIotKeystoreHelper.keystoreContainsAlias(settings.getCertificateId(),
                                                               keystorePath,
                                                               settings.getKeystoreName(),
                                                               settings.getKeystorePassword()))

                {
                    Logger.d("Certificate " + settings.getCertificateId() + " found in keystore - using for MQTT.");

                    // load keystore from file into memory to pass on connection
                    clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(settings.getCertificateId(),
                                                                         keystorePath,
                                                                         settings.getKeystoreName(),
                                                                         settings.getKeystorePassword());
                    connectMqttManager(clientKeyStore, onConnect);

                } else {
                    Logger.d("Key/cert " + settings.getCertificateId() + " not found in keystore.");
                }
            } else {
                Logger.d("Keystore " + keystorePath + "/" + settings.getKeystoreName() + " not found.");
            }
        } catch (Exception e) {
            Logger.logOnFile(LOG_FILE_ERROR, e.getMessage());
            Logger.e("An error occurred retrieving cert/key from keystore.", e);
        }

        Logger.d("clientKeyStore is null? " + (clientKeyStore == null));

        if (clientKeyStore == null) {
            Logger.d("Cert/key was not found in keystore - creating new key and certificate.");

//            new Thread(() -> {
                try {
                    // Create a new private key and certificate. This call
                    // creates both on the server and returns them to the
                    // device.
                    CreateKeysAndCertificateRequest createKeysAndCertificateRequest = new CreateKeysAndCertificateRequest();
                    createKeysAndCertificateRequest.setSetAsActive(true);

                    final CreateKeysAndCertificateResult createKeysAndCertificateResult;
                    createKeysAndCertificateResult = mIotAndroidClient.createKeysAndCertificate(createKeysAndCertificateRequest);

                    Logger.d("Cert ID: " + createKeysAndCertificateResult.getCertificateId() + " created.");

                    // store in keystore for use in MQTT client
                    // saved as alias "default" so a new certificate isn't
                    // generated each run of this application
                    AWSIotKeystoreHelper.saveCertificateAndPrivateKey(settings.getCertificateId(),
                            createKeysAndCertificateResult.getCertificatePem(),
                            createKeysAndCertificateResult.getKeyPair().getPrivateKey(),
                            keystorePath, settings.getKeystoreName(), settings.getKeystorePassword());

                    // load keystore from file into memory to pass on
                    // connection
                    clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(settings.getCertificateId(),
                                                                         keystorePath,
                                                                         settings.getKeystoreName(),
                                                                         settings.getKeystorePassword());

                    // Attach a policy to the newly created certificate.
                    // This flow assumes the policy was already created in
                    // AWS IoT and we are now just attaching it to the
                    // certificate.

                    AttachPrincipalPolicyRequest policyAttachRequest =
                            new AttachPrincipalPolicyRequest();
                    policyAttachRequest.setPolicyName(settings.getAwsIotPolicyName());
                    policyAttachRequest.setPrincipal(createKeysAndCertificateResult
                            .getCertificateArn());
                    mIotAndroidClient.attachPrincipalPolicy(policyAttachRequest);

/*
                    AttachPolicyRequest policyRequest = new AttachPolicyRequest();
                    policyRequest.setPolicyName(settings.getAwsIotPolicyName());
                    mIotAndroidClient.attachPolicy(policyRequest);
*/

                    connectMqttManager(clientKeyStore, onConnect);

                } catch (Exception e) {
                    Logger.logOnFile(LOG_FILE_ERROR, e.getMessage());
                    Logger.e("Exception occurred when generating new private key and certificate.", e);
                }
//            }).start();
        }
    }

    private void connectMqttManager(@NonNull KeyStore clientKeyStore, @NonNull Runnable onConnect) {
        Logger.d("clientId = " + clientId);

        try {
            mqttManager.connect(clientKeyStore, (status, throwable) -> {
                Logger.d("Status = " + status);

                if (throwable != null)
                    logOnFile("error on connect to iot: " + throwable.getMessage());
                else
                    logOnFile("connection status iot changed: " + status);


                ThreadUtils.runOnUiThread(() -> {
                    if (status.equals(AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected)) {
                        connected = true;
                        onConnect.run();

                    }else {
                        connected = false;
                    }

                    if (throwable != null)
                        Logger.e("Connection error.", throwable);
                });

            });
        } catch (final Exception e) {
            Logger.logOnFile(LOG_FILE_ERROR, e.getMessage());
            Logger.e("Connection error.", e);
        }
    }

    public void connect (Context context, @NonNull Runnable onConnect) {
        AWSMobileClient.getInstance().initialize(context, new Callback<UserStateDetails>() {
            @Override
            public void onResult(UserStateDetails result) { initIoTClient(context, onConnect); }

            @Override
            public void onError(Exception e) { Logger.e("onError: ", e); }
        });
    }

    public boolean isConnected () {
        return connected;
    }

    public void disconnect () {
        Logger.d("disconnect");
        try {
            logOnFile("close connection with iot");
            mqttManager.disconnect();
            connected = false;
        } catch (Exception e) {
            Logger.logOnFile(LOG_FILE_ERROR, e.getMessage());
            Logger.e("Disconnect error.", e);
        }
    }

    public void send (JSONObject object) {
        AWSIotMqttQos qos = highQuality ? AWSIotMqttQos.QOS1 : AWSIotMqttQos.QOS0;
        Logger.d("getOfflinePublishQueueBound: " + mqttManager.getOfflinePublishQueueBound());

        String msg = object.toString();
        Logger.logOnFile(LOG_FILE_POSITION, msg);

        if (connected) {
            mqttManager.publishString(msg, settings.getTopic(), qos, (status, userData) -> {
                if (status.equals(AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus.Success)) {

                    Logger.d("enviado com sucesso: " + msg);
                    logOnFile("enviado com sucesso: " + msg);

                }else {

                    Logger.e("falha ao enviar: " +  userData);
                    logOnFile("ERRO AO ENVIAR = { iotConnectionStatus : " + status + " , userData : " + userData + " } ");

                }
            }, null);
        }
    }


    public void logOnFile (String message) {
        Logger.logOnFile(LOG_FILE_GENERAL, "[" + new Date().toString() + "] : " + message);
    }

}
