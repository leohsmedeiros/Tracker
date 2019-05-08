package br.com.phonetracker.lib.Services;

import android.app.Service;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.PowerManager;
import br.com.phonetracker.lib.Commons.SensorGpsDataItem;
import br.com.phonetracker.lib.Commons.Utils;
import br.com.phonetracker.lib.Filters.GPSAccKalmanFilter;
import br.com.phonetracker.lib.Interfaces.ILogger;
import br.com.phonetracker.lib.Interfaces.LocationServiceInterface;
import br.com.phonetracker.lib.Interfaces.LocationServiceStatusInterface;
import br.com.phonetracker.lib.TrackerSettings;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

public abstract class LocationService extends Service {
    protected List<LocationServiceInterface> m_locationServiceInterfaces;
    protected List<LocationServiceStatusInterface> m_locationServiceStatusInterfaces;

    protected Location m_lastLocation;

    protected ServiceStatus m_serviceStatus = ServiceStatus.SERVICE_STOPPED;

    public enum ServiceStatus {
        PERMISSION_DENIED(0),
        SERVICE_STOPPED(1),
        SERVICE_STARTED(2),
        HAS_LOCATION(3),
        SERVICE_PAUSED(4);

        int value;

        ServiceStatus(int value) { this.value = value;}

        public int getValue() { return value; }
    }

    //region - Settings
    public static class Settings {
        final double accelerationDeviation;
        final int gpsMinDistance;
        final int gpsMinTime;
        final int positionMinTime;
        final int geoHashPrecision;
        final int geoHashMinPointCount;
        final double sensorFrequencyHz;
        public final ILogger logger;
        final boolean filterMockGpsCoordinates;

        final double mVelFactor;
        final double mPosFactor;


        public Settings(double accelerationDeviation,
                        int gpsMinDistance,
                        int gpsMinTime,
                        int positionMinTime,
                        int geoHashPrecision,
                        int geoHashMinPointCount,
                        double sensorFrequencyHz,
                        ILogger logger,
                        boolean filterMockGpsCoordinates,
                        double velFactor,
                        double posFactor) {
            this.accelerationDeviation = accelerationDeviation;
            this.gpsMinDistance = gpsMinDistance;
            this.gpsMinTime = gpsMinTime;
            this.positionMinTime = positionMinTime;
            this.geoHashPrecision = geoHashPrecision;
            this.geoHashMinPointCount = geoHashMinPointCount;
            this.sensorFrequencyHz = sensorFrequencyHz;
            this.logger = logger;
            this.filterMockGpsCoordinates = filterMockGpsCoordinates;
            this.mVelFactor = velFactor;
            this.mPosFactor = posFactor;
        }
    }
    public static Settings defaultSettings =
            new Settings(Utils.ACCELEROMETER_DEFAULT_DEVIATION,
                    Utils.GPS_MIN_DISTANCE,
                    Utils.GPS_MIN_TIME,
                    Utils.SENSOR_POSITION_MIN_TIME,
                    Utils.GEOHASH_DEFAULT_PREC,
                    Utils.GEOHASH_DEFAULT_MIN_POINT_COUNT,
                    Utils.SENSOR_DEFAULT_FREQ_HZ,
                    null,
                    true,
                    Utils.DEFAULT_VEL_FACTOR,
                    Utils.DEFAULT_POS_FACTOR);

    protected Settings m_settings;
    //endregion - Settings


    protected boolean m_sensorsEnabled = false;
//    protected PowerManager.WakeLock m_wakeLock;
    protected int m_activeSatellites = 0;
    protected float m_lastLocationAccuracy = 0;
    protected boolean m_gpsEnabled = false;
    protected GpsStatus m_gpsStatus;

    protected GPSAccKalmanFilter m_kalmanFilter;
    protected double m_magneticDeclination = 0.0;

    protected List<Sensor> m_lstSensors;
    protected SensorManager m_sensorManager;

    protected LocationManager m_locationManager;
    protected PowerManager m_powerManager;




    public boolean isSensorsEnabled() {
        return m_sensorsEnabled;
    }

    public boolean IsRunning() {
        return m_serviceStatus != ServiceStatus.SERVICE_STOPPED && m_serviceStatus != ServiceStatus.SERVICE_PAUSED && m_sensorsEnabled;
    }

    public void addInterface(LocationServiceInterface locationServiceInterface) {
        if (m_locationServiceInterfaces.add(locationServiceInterface) && m_lastLocation != null) {
            locationServiceInterface.locationChanged(m_lastLocation);
        }
    }

    public void addInterfaces(List<LocationServiceInterface> locationServiceInterfaces) {
        if (m_locationServiceInterfaces.addAll(locationServiceInterfaces) && m_lastLocation != null) {
            for (LocationServiceInterface locationServiceInterface : locationServiceInterfaces) {
                locationServiceInterface.locationChanged(m_lastLocation);
            }
        }
    }

    public void removeInterface(LocationServiceInterface locationServiceInterface) {
        m_locationServiceInterfaces.remove(locationServiceInterface);
    }

    public void removeStatusInterface(LocationServiceStatusInterface locationServiceStatusInterface) {
        m_locationServiceStatusInterfaces.remove(locationServiceStatusInterface);
    }

    public void addStatusInterface(LocationServiceStatusInterface locationServiceStatusInterface) {
        if (m_locationServiceStatusInterfaces.add(locationServiceStatusInterface)) {
            locationServiceStatusInterface.serviceStatusChanged(m_serviceStatus);
            locationServiceStatusInterface.GPSStatusChanged(m_activeSatellites);
            locationServiceStatusInterface.GPSEnabledChanged(m_gpsEnabled);
            locationServiceStatusInterface.lastLocationAccuracyChanged(m_lastLocationAccuracy);
        }
    }

    public void addStatusInterfaces(List<LocationServiceStatusInterface> locationServiceStatusInterfaces) {
        if (m_locationServiceStatusInterfaces.addAll(locationServiceStatusInterfaces)) {
            for (LocationServiceStatusInterface locationServiceStatusInterface : locationServiceStatusInterfaces) {
                locationServiceStatusInterface.serviceStatusChanged(m_serviceStatus);
                locationServiceStatusInterface.GPSStatusChanged(m_activeSatellites);
                locationServiceStatusInterface.GPSEnabledChanged(m_gpsEnabled);
                locationServiceStatusInterface.lastLocationAccuracyChanged(m_lastLocationAccuracy);
            }
        }
    }

    public Location getLastLocation() {
        return m_lastLocation;
    }

    abstract protected void start(TrackerSettings trackerSettings);

    abstract protected void stop();

    abstract protected void reset(Settings settings);

}
