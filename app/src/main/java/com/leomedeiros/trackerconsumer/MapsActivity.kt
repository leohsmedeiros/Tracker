package com.leomedeiros.trackerconsumer

import android.annotation.SuppressLint
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import br.com.phonetracker.lib.interfaces.LocationServiceInterface
import br.com.phonetracker.lib.Tracker
import br.com.phonetracker.lib.commons.Logger

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.maps.model.Polyline



class MapsActivity : AppCompatActivity(), OnMapReadyCallback, LocationServiceInterface {

    private lateinit var mMap: GoogleMap
    private lateinit var tracker: Tracker
    private lateinit var marker: Marker
    private var route: MutableList<LatLng> = mutableListOf()
    private var isStartTracking: Boolean = false

    var polylines: MutableList<Polyline> = ArrayList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        tracker = Tracker.Builder(this, resources.getXml(R.xml.aws_iot_settings))
            .trackedId("new Tracker")
            .intervalInSeconds(5)
            .restartIfKilled(true)
            .gpsMinTimeInSeconds(1)
            .gpsMinDistanceInMeters(0)
            .geoHashPrecision(6)
            .build()

        tracker.startTracking()

        marker = mMap.addMarker(MarkerOptions().position(LatLng(0.0, 0.0)))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom( LatLng(0.0, 0.0), 16f ))

        tracker.addLocationServiceInterface(this)

        mMap.uiSettings.isMyLocationButtonEnabled = true
    }


    override fun locationChanged(location: Location?) {
        Logger.d("MainActivity locationChanged: $location")

        if (location is Location) {
            val position = LatLng(location.latitude, location.longitude)

            if (marker.position == LatLng(0.0, 0.0))
                mMap.moveCamera(CameraUpdateFactory.newLatLng( position ))

            marker.position = position
            marker.title = resources.getString(R.string.position_lbl)
            marker.snippet = position.toString()
            marker.showInfoWindow()

            if(isStartTracking) {
                mMap.moveCamera(CameraUpdateFactory.newLatLng( position ))

                if (route.size > 1) {
                    val polylineOptions = PolylineOptions()
                        .width(5f)
                        .add(route.last())
                        .add(position)

                    polylines.add(mMap.addPolyline(polylineOptions))
                }

                route.add(position)
            }
        }
    }

    fun onClickToRepositionCamera (view: View) {
        mMap.moveCamera(CameraUpdateFactory.newLatLng( marker.position ))
    }

    fun onClickToDrawRoute (view: View) {
        if (view is Button) {
            route.clear()

            isStartTracking = !isStartTracking

            view.text = if (isStartTracking) resources.getString(R.string.stop_tracking) else resources.getString(R.string.start_tracking)
        }
    }

    fun onClickToClearRoute (view: View) {
        polylines.forEach {
                polyline: Polyline -> polyline.remove()
        }

        polylines.clear()
    }
}
