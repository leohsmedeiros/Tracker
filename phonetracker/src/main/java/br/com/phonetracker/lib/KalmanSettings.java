package br.com.phonetracker.lib;

import br.com.phonetracker.lib.Commons.Utils;

public class KalmanSettings {

    int gpsMinDistance = Utils.GPS_MIN_DISTANCE;
    int gpsMinTime = Utils.GPS_MIN_TIME;
    int geoHashPrecision = Utils.GEOHASH_DEFAULT_PREC;


    public double getAccelerationDeviation() {
        return Utils.ACCELEROMETER_DEFAULT_DEVIATION;
    }
    public int getGpsMinDistance() {
        return gpsMinDistance;
    }
    public int getGpsMinTime() {
        return gpsMinTime;
    }
    public int getGeoHashPrecision() {
        return geoHashPrecision;
    }
    public int getGeoHashMinPointCount() {
        return Utils.GEOHASH_DEFAULT_MIN_POINT_COUNT;
    }
    public double getSensorFrequencyHz() {
        return Utils.SENSOR_DEFAULT_FREQ_HZ;
    }

    public double getmVelFactor() {
        return Utils.DEFAULT_VEL_FACTOR;
    }
    public double getmPosFactor() {
        return Utils.DEFAULT_POS_FACTOR;
    }
}

