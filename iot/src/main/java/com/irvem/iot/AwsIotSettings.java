package com.irvem.iot;

/**
 * AWS IOT SETTINGS
 * <p>
 * This class is necessary to configure AwsIot class, externally of the phonetracker module.
 * <p>
 * It will receive a xml file and parse the informations.
 *
 * @author Leonardo Medeiros
 */

import com.amazonaws.regions.Regions;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Persister;

import java.io.InputStream;
import java.io.Serializable;

@Root
public class AwsIotSettings implements Serializable {
    // IoT endpoint
    // AWS Iot CLI describe-endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com
    @Element
    private String customerSpecificEndpoint;

    String getCustomerSpecificEndpoint() { return customerSpecificEndpoint; }

    // Name of the AWS IoT policy to attach to a newly created certificate
    @Element
    private String awsIotPolicyName;

    String getAwsIotPolicyName() { return awsIotPolicyName; }

    // Region of AWS IoT
    @Element
    private Regions myRegion;

    Regions getMyRegion() { return myRegion; }

    // Filename of KeyStore file on the filesystem
    @Element
    private String keystoreName;

    String getKeystoreName() { return keystoreName; }

    // Password for the private key in the KeyStore
    @Element
    private String keystorePassword;

    String getKeystorePassword() { return keystorePassword; }

    // Certificate and key aliases in the KeyStore
    @Element
    private String certificateId;

    String getCertificateId() { return certificateId; }

    // Topic to subscribe
    @Element
    private String topic;

    String getTopic() { return topic; }

    @Override
    public String toString() {
        return "\ncustomerSpecificEndpoint: " + customerSpecificEndpoint +
                "\nawsIotPolicyName: " + awsIotPolicyName +
                "\nmyRegion: " + myRegion +
                "\nkeystoreName: " + keystoreName +
                "\nkeystorePassword: " + keystorePassword +
                "\ncertificateId: " + certificateId +
                "\ntopic: " + topic;
    }


    static public AwsIotSettings build (InputStream xmlIotClientSettings) throws Exception {
        return new Persister().read(AwsIotSettings.class, xmlIotClientSettings);
    }
}
