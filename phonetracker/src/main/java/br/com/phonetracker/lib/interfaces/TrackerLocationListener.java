package br.com.phonetracker.lib.interfaces;

import android.location.Location;

/**
 * Created by lezh1k on 2/13/18.
 */

public interface TrackerLocationListener {
    void locationChanged(Location location);
}
