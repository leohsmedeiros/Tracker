package mad.location.manager.lib.Services;


/**
 * AWS IOT SETTINGS
 *
 * This class is necessary to configure AwsIot class, externally of the phonetracker module.
 *
 * It will receive a xml file and parse the informations.
 *
 *
 * @author Leonardo Medeiros
 */


import android.content.res.XmlResourceParser;
import com.amazonaws.regions.Regions;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;


public class AwsIotSettings implements Serializable {
    // IoT endpoint
    // AWS Iot CLI describe-endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com
    private String customer_specific_endpoint;
    String getCustomerSpecificEndpoint() { return customer_specific_endpoint; }

    // Name of the AWS IoT policy to attach to a newly created certificate
    private String aws_iot_policy_name;
    String getAwsIotPolicyName() { return aws_iot_policy_name; }

    // Region of AWS IoT
    private Regions my_region;
    Regions getMyRegion() { return my_region; }

    // Filename of KeyStore file on the filesystem
    private String keystore_name;
    String getKeystoreName() { return keystore_name; }

    // Password for the private key in the KeyStore
    private String keystore_password;
    String getKeystorePassword() { return keystore_password; }

    // Certificate and key aliases in the KeyStore
    private String certificate_id;
    String getCertificateId() { return certificate_id; }

    // Topic to subscribe
    private String topic;
    String getTopic() { return topic; }



    private void initVariables (String fieldKey, XmlResourceParser parser) throws XmlPullParserException, IOException {
        if ("customer_specific_endpoint".equals(fieldKey)) {
            customer_specific_endpoint = parser.nextText().trim();
        } else if ("aws_iot_policy_name".equals(fieldKey)) {
            aws_iot_policy_name = parser.nextText().trim();
        } else if ("my_region".equals(fieldKey)) {
            my_region = Regions.fromName(parser.nextText().trim());
        } else if ("keystore_name".equals(fieldKey)) {
            keystore_name = parser.nextText().trim();
        } else if ("keystore_password".equals(fieldKey)) {
            keystore_password = parser.nextText().trim();
        } else if ("certificate_id".equals(fieldKey)) {
            certificate_id = parser.nextText().trim();
        } else if ("topic".equals(fieldKey)) {
            topic = parser.nextText().trim();
        }
    }

    private void checkIfAllVariablesWasInitialized () throws IllegalAccessException {
        String fieldsNotInitialized = "";

        for (Field f : getClass().getDeclaredFields()) {
            if (f.get(this) == null) {
                fieldsNotInitialized += " <" + f.getName() + ">";
            }
        }

        if (!fieldsNotInitialized.isEmpty()) {
            throw new IllegalAccessException("Field(s) was(were) not found in xml file:" + fieldsNotInitialized);
        }
    }

    public AwsIotSettings(XmlResourceParser parser) throws IOException {
        try {
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {

                if (eventType == XmlPullParser.START_TAG) {
                    String fieldKey = parser.getName();
                    initVariables(fieldKey, parser);
                }

                eventType = parser.next();
            }

            checkIfAllVariablesWasInitialized ();

        } catch (XmlPullParserException e) {
            throw new IOException(e.getMessage());
        } catch (IllegalAccessException e) {
            throw new IOException(e.getMessage());
        }
    }

    private AwsIotSettings () {}

    @Override
    public String toString() {
        return  "\ncustomer_specific_endpoint: " + customer_specific_endpoint +
                "\naws_iot_policy_name: " + aws_iot_policy_name +
                "\nmy_region: " + my_region +
                "\nkeystore_name: " + keystore_name +
                "\nkeystore_password: " + keystore_password +
                "\ncertificate_id: " + certificate_id;
    }
}
