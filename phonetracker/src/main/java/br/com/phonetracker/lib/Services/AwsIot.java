package br.com.phonetracker.lib.Services;


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
import android.location.Location;

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
import br.com.phonetracker.lib.utils.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.KeyStore;
import java.util.UUID;

class AwsIot {
    private AwsIotSettings settings;

    private String clientId;
    private KeyStore clientKeyStore = null;
    private AWSIotClient mIotAndroidClient;
    private AWSIotMqttManager mqttManager;
    private Context context;

    private String trackedId;

    AwsIot(Context context, String trackedId, AwsIotSettings settings) {
        this.context = context;
        this.settings = settings;
        this.trackedId = trackedId;


        AWSMobileClient.getInstance().initialize(this.context, new Callback<UserStateDetails>() {
            @Override
            public void onResult(UserStateDetails result) { initIoTClient(); }

            @Override
            public void onError(Exception e) { Logger.e("onError: ", e); }
        });
    }

    private void initIoTClient() {

        clientId = UUID.randomUUID().toString();
        Logger.d("initIoTClient whit ID: " + clientId);

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
                    connectAWSIot();

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

            new Thread(() -> {
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

                    connectAWSIot();

                } catch (Exception e) {
                    Logger.e("Exception occurred when generating new private key and certificate.", e);
                }
            }).start();
        }
    }

    void connectAWSIot() {
        Logger.d("clientId = " + clientId);

        try {
            mqttManager.connect(clientKeyStore, (status, throwable) -> {
                Logger.d("Status = " + String.valueOf(status));
                ThreadUtils.runOnUiThread(() -> {
                    if (throwable != null) {
                        Logger.e("Connection error.", throwable);
                    }
                });
            });
        } catch (final Exception e) {
            Logger.e("Connection error.", e);
        }
    }

    void disconnectAWSIot() {
        try {
            mqttManager.disconnect();
        } catch (Exception e) {
            Logger.e("Disconnect error.", e);
        }
    }

    void sendPosition (Location location) {
        if(location == null){
            Logger.e("location is null");
            return;
        }

        JSONObject telemetric = new JSONObject();
        JSONArray cordenadas = new JSONArray();
        try {

            cordenadas.put(location.getLongitude());
            cordenadas.put(location.getLatitude());
            telemetric.put("coordinates", cordenadas);
            telemetric.put("speed", location.getSpeed());
            telemetric.put("direction", location.getBearing());
            if (trackedId != null)
                telemetric.put("trackedId", trackedId);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        String msg = telemetric.toString();

        try {
            mqttManager.publishString(msg, settings.getTopic(), AWSIotMqttQos.QOS0);
        } catch (Exception e) {
            Logger.e("Publish error.", e);
        }

//        String logMsg = "\nposition: " + (kalmanLatLong.get_lat() + ", " + kalmanLatLong.get_lng()) +
//                        "\nspeed: " + kalmanLatLong.getSpeed() +
//                        "\ndirection: " + kalmanLatLong.getBearing();

        Logger.d(msg);
    }

}
