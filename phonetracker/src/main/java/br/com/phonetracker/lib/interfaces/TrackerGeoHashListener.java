package br.com.phonetracker.lib.interfaces;

import android.location.Location;

import java.util.List;

public interface TrackerGeoHashListener {
    void onGeoHashFilterUpdate(List<Location> locationsFiltered);
}
