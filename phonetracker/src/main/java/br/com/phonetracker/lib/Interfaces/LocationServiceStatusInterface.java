package br.com.phonetracker.lib.Interfaces;


import br.com.phonetracker.lib.Services.LocationService;

/**
 * Created by lezh1k on 2/13/18.
 */

public interface LocationServiceStatusInterface {
    void serviceStatusChanged(LocationService.ServiceStatus status);
    void GPSStatusChanged(int activeSatellites);
    void GPSEnabledChanged(boolean enabled);
    void lastLocationAccuracyChanged(float accuracy);
}
