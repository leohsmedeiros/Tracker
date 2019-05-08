package br.com.phonetracker.lib;

import android.content.res.XmlResourceParser;
import br.com.phonetracker.lib.Services.TrackerService;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import br.com.phonetracker.lib.Commons.Utils;

class KalmanSettingsFactory {
    private double accelerationDeviation = Utils.ACCELEROMETER_DEFAULT_DEVIATION;
    private int gpsMinTime = Utils.GPS_MIN_TIME;
    private int gpsMinDistance = Utils.GPS_MIN_DISTANCE;
    private int geoHashPrecision = 0; //Utils.GEOHASH_DEFAULT_PREC;
    private int geoHashMinPointCount = 0; // Utils.GEOHASH_DEFAULT_MIN_POINT_COUNT;

    private double sensorFfequencyHz = Utils.SENSOR_DEFAULT_FREQ_HZ;
//    private int sensorFfequencyHz = Utils.SENSOR_DEFAULT_FREQ_HZ;

    private boolean filterMockGpsCoordinates = false;
    private double velFactor = Utils.DEFAULT_VEL_FACTOR;
    private double posFactor = Utils.DEFAULT_POS_FACTOR;


    private void initVariables (String fieldKey, XmlResourceParser parser) throws XmlPullParserException, IOException {
        if ("accelerationDeviation".equals(fieldKey)) {
            accelerationDeviation = Double.parseDouble(parser.nextText().trim());
        } else if ("gpsMinTime".equals(fieldKey)) {
            gpsMinTime = Integer.parseInt(parser.nextText().trim());
        } else if ("gpsMinDistance".equals(fieldKey)) {
            gpsMinDistance = Integer.parseInt(parser.nextText().trim());
        } else if ("geoHashPrecision".equals(fieldKey)) {
            geoHashPrecision = Integer.parseInt(parser.nextText().trim());
        } else if ("geoHashMinPointCount".equals(fieldKey)) {
            geoHashMinPointCount = Integer.parseInt(parser.nextText().trim());
        } else if ("sensorFfequencyHz".equals(fieldKey)) {
            sensorFfequencyHz = Integer.parseInt(parser.nextText().trim());
        } else if ("filterMockGpsCoordinates".equals(fieldKey)) {
            filterMockGpsCoordinates = Boolean.parseBoolean(parser.nextText().trim());
        }else if ("velFactor".equals(fieldKey)) {
            velFactor = Double.parseDouble(parser.nextText().trim());
        }else if ("posFactor".equals(fieldKey)) {
            posFactor = Double.parseDouble(parser.nextText().trim());
        }
    }

    TrackerService.Settings buildSettings (XmlResourceParser parser) throws IOException {
        if (parser != null) {
            try {
                int eventType = parser.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {

                    if (eventType == XmlPullParser.START_TAG) {
                        String fieldKey = parser.getName();
                        initVariables(fieldKey, parser);
                    }

                    eventType = parser.next();
                }

            } catch (XmlPullParserException e) {
                throw new IOException(e.getMessage());
            }
        }

        return new TrackerService.Settings(
                accelerationDeviation,
                gpsMinDistance,
                gpsMinTime,
                Utils.SENSOR_POSITION_MIN_TIME,
                geoHashPrecision,
                geoHashMinPointCount,
                sensorFfequencyHz,
                null,
                filterMockGpsCoordinates,
                velFactor,
                posFactor);
  }

    TrackerService.Settings buildSettings () throws IOException {
        return buildSettings (null);
    }

}
