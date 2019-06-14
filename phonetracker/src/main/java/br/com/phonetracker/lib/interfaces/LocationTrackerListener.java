package br.com.phonetracker.lib.interfaces;

import android.location.Location;

/**
 * Created by lezh1k on 2/13/18.
 */

public interface LocationTrackerListener {
    void locationChanged(Location location);
}
