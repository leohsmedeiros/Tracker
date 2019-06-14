package br.com.phonetracker.lib.interfaces;

import android.location.Location;

import java.util.List;

public interface GeoHashFilterLocationListener {
    void onGeoHashFilterUpdate(List<Location> locationsFiltered);
}
