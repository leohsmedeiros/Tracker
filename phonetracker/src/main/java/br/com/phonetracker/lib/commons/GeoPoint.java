package br.com.phonetracker.lib.commons;

/**
 * Created by lezh1k on 2/13/18.
 */

public class GeoPoint {
    public double Latitude;
    public double Longitude;

    private GeoPoint() { this.Latitude = 0; this.Longitude = 0; }

    public GeoPoint(double latitude, double longitude) {
        Latitude = latitude;
        Longitude = longitude;
    }
}
