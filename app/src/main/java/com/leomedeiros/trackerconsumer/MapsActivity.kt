package com.leomedeiros.trackerconsumer

import android.annotation.SuppressLint
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import br.com.phonetracker.lib.interfaces.LocationServiceInterface
import br.com.phonetracker.lib.Tracker
import br.com.phonetracker.lib.commons.Logger

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.maps.model.Polyline
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.leomedeiros.trackerconsumer.dto.History


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, LocationServiceInterface, Tracker.GeoHashFilterLocationListener {

    private lateinit var mMap: GoogleMap
    private lateinit var tracker: Tracker
    private lateinit var marker: Marker
    private var markersToGeohash: MutableList<Marker> = mutableListOf()

    private var route: MutableList<LatLng> = mutableListOf()
    private var routeWithGeohashFilter: MutableList<LatLng> = mutableListOf()

    private var history: MutableList<History> = mutableListOf()

    private var isListeningPosition: Boolean = false

    var polylines: MutableList<Polyline> = ArrayList()
    var polylinesGeoHash: MutableList<Polyline> = ArrayList()


    fun signIn (email: String, password: String) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                authResult ->

                // Obtain the SupportMapFragment and get notified when the map is ready to be used.
                val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
                mapFragment.getMapAsync(this)
            }
            .addOnFailureListener {
                exception ->
                Toast.makeText(this@MapsActivity, exception.message, Toast.LENGTH_SHORT).show()
                signIn(email, password)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        signIn("leohsmedeiros@gmail.com", "12345678")
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        tracker = Tracker.Builder(this, resources.getXml(R.xml.aws_iot_settings))
                            .trackedId("new Tracker")
                            .enableRestartIfKilled()
                            .intervalInSecondsToSendLocation(5)
                            .enableToSendSpeed()
                            .enableToSendDirection()
                            .gpsMinTimeInSeconds(1)
                            .gpsMinDistanceInMeters(0)
                            .geoHashPrecision(6)
                            .build()

        marker = mMap.addMarker(MarkerOptions().position(LatLng(0.0, 0.0)))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom( LatLng(0.0, 0.0), 16f ))
        mMap.uiSettings.isMyLocationButtonEnabled = true

        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null) {
            FirebaseDatabase.getInstance()
                .getReference(firebaseUser.uid)
                .child("history")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        history.clear()
                        for (postSnapshot in dataSnapshot.children) {
                            val historyOnFirebase = postSnapshot.getValue(History::class.java)
                            if (historyOnFirebase is History) {
                                history.add(historyOnFirebase)
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(this@MapsActivity, error.message, Toast.LENGTH_SHORT).show()
                    }
                })

        }

        findViewById<Button>(R.id.tracker_button).performClick()
        findViewById<Button>(R.id.listener_button).performClick()
    }

    override fun locationChanged(location: Location?) {
        if (location is Location) {
            val position = LatLng(location.latitude, location.longitude)

            if (marker.position == LatLng(0.0, 0.0))
                mMap.moveCamera(CameraUpdateFactory.newLatLng( position ))

            marker.position = position
            marker.title = resources.getString(R.string.info_window_title)
            marker.snippet = position.toString()
            marker.showInfoWindow()

            if(isListeningPosition) {
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

    override fun onGeoHashFilterUpdate(locationsFiltered: MutableList<Location>) {
        Logger.e("[onGeoHashFilterUpdate] locations size: ${locationsFiltered.size}")

        if (isListeningPosition && locationsFiltered.size > 1) {

            val filteredPos: MutableList<LatLng> = mutableListOf()

            markersToGeohash.forEach { marker -> marker.remove() }
            markersToGeohash.clear()

            val icon = BitmapDescriptorFactory.fromResource(R.drawable.bulletred)
            locationsFiltered.forEach { location:Location ->
                val position = LatLng(location.latitude, location.longitude)

                filteredPos.add(position)

                val marker = mMap.addMarker(
                    MarkerOptions()
                        .icon(icon)
                        .position(position)
                )

                marker.title = resources.getString(R.string.info_window_title)
                marker.snippet = position.toString()

                markersToGeohash.add(marker)
            }

            routeWithGeohashFilter.clear()
            routeWithGeohashFilter.addAll(filteredPos)

            polylinesGeoHash.forEach { polyline: Polyline -> polyline.remove() }
            polylinesGeoHash.clear()

            val polylineOptions = PolylineOptions()
                                    .width(5f)
                                    .color(R.color.green)
                                    .addAll(filteredPos)

            polylinesGeoHash.add(mMap.addPolyline(polylineOptions))
        }
    }

    fun onClickToRepositionCamera (view: View) {
        mMap.moveCamera(CameraUpdateFactory.newLatLng( marker.position ))
    }

    fun onClickToDrawRoute (view: View) {
        if (view is Button) {
            route.clear()

            isListeningPosition = !isListeningPosition

            if (isListeningPosition) {
                tracker.addLocationServiceInterface(this)
                tracker.addListenerToGeohash(this)
                view.text = resources.getString(R.string.stop_listening_tracking)
            }else {
                tracker.removeLocationServiceInterface(this)
                tracker.removeListenerToGeohash(this)
                view.text = resources.getString(R.string.start_listening_tracking)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun onClickToStartStopSendingLocation (view: View) {
        if (view is Button) {
            if (tracker.isServiceRunning) {
                tracker.stopTracking()
                view.text = resources.getString(R.string.start_sending_location)
            } else {
                tracker.startTracking()
                view.text = resources.getString(R.string.stop_sending_location)
            }
        }
    }

    fun onClickToClearRoute (view: View) {
        polylines.forEach { polyline: Polyline -> polyline.remove() }
        polylines.clear()

        polylinesGeoHash.forEach { polyline: Polyline -> polyline.remove() }
        polylinesGeoHash.clear()

        markersToGeohash.forEach { marker -> marker.remove() }
        markersToGeohash.clear()
    }

    fun onClickToSaveRoute (view: View) {
        history.add(History(route, routeWithGeohashFilter))

        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null) {

            FirebaseDatabase.getInstance()
                .getReference(firebaseUser.uid)
                .child("history")
                .setValue(history)
        }

    }
}
