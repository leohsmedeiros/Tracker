package br.com.phonetracker.lib.commons;

import java.io.Serializable;

public class KalmanSettings implements Serializable {

    private double accelerationDeviation = 0.1;
    private int gpsMinDistance = 0;
    private int gpsMinTime = 2000;
    private int geoHashPrecision = 6;
    private int geoHashMinPointCount = 2;
    private double sensorFrequencyHz = 10.0;
    private double mVelFactor = 1.0;
    private double mPosFactor = 1.0;


    public double getAccelerationDeviation() {
        return accelerationDeviation;
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
        return geoHashMinPointCount;
    }
    public double getSensorFrequencyHz() {
        return sensorFrequencyHz;
    }
    public double getmVelFactor() {
        return mVelFactor;
    }
    public double getmPosFactor() {
        return mPosFactor;
    }
}

