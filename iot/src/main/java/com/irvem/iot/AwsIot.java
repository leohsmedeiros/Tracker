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

import com.amazonaws.mobile.auth.core.internal.util.ThreadUtils;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttLastWillAndTestament;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Region;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;

import org.json.JSONObject;

import java.security.KeyStore;
import java.util.UUID;

public class AwsIot {
    private AwsIotSettings settings;

    private String clientId;
    private KeyStore clientKeyStore = null;
    private AWSIotClient mIotAndroidClient;
    private AWSIotMqttManager mqttManager;
    private boolean connected = false;


    public AwsIot(AwsIotSettings settings) {
        this.settings = settings;
    }


    private void initIoTClient(Context context) {

        clientId = UUID.randomUUID().toString();
//        Logger.d("initIoTClient whit ID: " + clientId);

        // MQTT Client
        mqttManager = new AWSIotMqttManager(clientId, settings.getCustomerSpecificEndpoint());

        // Set keepalive to 10 seconds.  Will recognize disconnects more quickly but will also send
        // MQTT pings every 10 seconds.
        mqttManager.setKeepAlive(10);

        // Set Last Will and Testament for MQTT.  On an unclean disconnect (loss of connection)
        // AWS IoT will publish this message to alert other clients.
        AWSIotMqttLastWillAndTestament lwt = new AWSIotMqttLastWillAndTestament("my/lwt/topic",
                "Android client lost connection", AWSIotMqttQos.QOS0);
        mqttManager.setMqttLastWillAndTestament(lwt);

        Region region = Region.getRegion(settings.getMyRegion());
        // IoT Client (for creation of certificate if needed)
        mIotAndroidClient = new AWSIotClient(AWSMobileClient.getInstance());
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
                    connectMqttManager();

                } else {
                    Logger.d("Key/cert " + settings.getCertificateId() + " not found in keystore.");
                }
            } else {
                Logger.d("Keystore " + keystorePath + "/" + settings.getKeystoreName() + " not found.");
            }
        } catch (Exception e) {
            Logger.e("An error occurred retrieving cert/key from keystore.", e);
        }

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

                    connectMqttManager();

                } catch (Exception e) {
                    Logger.e("Exception occurred when generating new private key and certificate.", e);
                }
//            }).start();
        }
    }

    private void connectMqttManager() {
        Logger.d("clientId = " + clientId);
        try {
            mqttManager.connect(clientKeyStore, (status, throwable) -> {
                Logger.d("Status = " + status);
                ThreadUtils.runOnUiThread(() -> {
                    if (throwable != null) {
                        connected = false;
                        Logger.e("Connection error.", throwable);
                    }else {
                        connected = true;
                    }
                });
            });
        } catch (final Exception e) {
            Logger.e("Connection error.", e);
        }
    }


    public void connect (Context context) {
        AWSMobileClient.getInstance().initialize(context, new Callback<UserStateDetails>() {
            @Override
            public void onResult(UserStateDetails result) { initIoTClient(context); }

            @Override
            public void onError(Exception e) { Logger.e("onError: ", e); }
        });
    }

    public boolean isConnected () {
        return connected;
    }

    public void disconnect () {
        try {
            mqttManager.disconnect();
            connected = false;
        } catch (Exception e) {
            Logger.e("Disconnect error.", e);
        }
    }

    public void send (JSONObject object) {
        String msg = object.toString();
        try {
            mqttManager.publishString(msg, settings.getTopic(), AWSIotMqttQos.QOS0);
            Logger.d("enviado com sucesso: " + msg);
        } catch (Exception e) {
            Logger.e("Publish error.", e);
        }
    }

}
