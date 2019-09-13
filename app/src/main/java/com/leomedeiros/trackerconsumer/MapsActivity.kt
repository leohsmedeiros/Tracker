package com.leomedeiros.trackerconsumer

import android.annotation.SuppressLint
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import br.com.phonetracker.lib.Tracker
import br.com.phonetracker.lib.TrackerSettings
import br.com.phonetracker.lib.interfaces.TrackerGeoHashListener
import br.com.phonetracker.lib.interfaces.TrackerLocationListener

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.maps.model.Polyline
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.irvem.iot.AwsIotSettings
import com.leomedeiros.trackerconsumer.dto.History


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, TrackerLocationListener, TrackerGeoHashListener {

    private lateinit var mMap: GoogleMap
    private lateinit var tracker: Tracker
    private lateinit var marker: Marker
    private var markersToGeohash: MutableList<Marker> = mutableListOf()

    private var route: MutableList<LatLng> = mutableListOf()
    private var routeWithGeohashFilter: MutableList<LatLng> = mutableListOf()

    private var history: MutableList<History> = mutableListOf()

    private var isListeningPosition: Boolean = false
    private var isServiceRunning: Boolean = false

    var polylines: MutableList<Polyline> = ArrayList()
    var polylinesGeoHash: MutableList<Polyline> = ArrayList()


    private fun signIn (email: String, password: String) {
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

        marker = mMap.addMarker(MarkerOptions().position(LatLng(0.0, 0.0)))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom( LatLng(0.0, 0.0), 16f ))
        mMap.uiSettings.isMyLocationButtonEnabled = true

        val firebaseUser = FirebaseAuth.getInstance().currentUser

        var trackedId = ""

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

            trackedId = firebaseUser.uid
        }

        try {
            val iotSettings = AwsIotSettings.build(assets.open("aws_iot_settings.xml"))

            val trackerSettings = TrackerSettings.Builder()
                .trackedId(trackedId)
                .frequencyOnlineInSeconds(5)
                .frequencyInactiveInSeconds(5)
                .enableToSendSpeed()
                .enableToSendDirection()
                .build()

            tracker = Tracker.Builder(this)
                .sender(IotSender(iotSettings, highQuality = true, cleanSession = false))
                .settings(trackerSettings)
                .build()

        } catch (e: Exception) {
            e.printStackTrace()
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
//        Logger.e("[onGeoHashFilterUpdate] locations size: ${locationsFiltered.size}")

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

    fun onClickToRepositionCamera (v: View) {
        Log.d(MapsActivity::class.java.name, "onClickToRepositionCamera: $v")
        mMap.moveCamera(CameraUpdateFactory.newLatLng( marker.position ))
    }

    fun onClickToDrawRoute (view: View) {
        if (view is Button) {
            route.clear()

            isListeningPosition = !isListeningPosition

            if (isListeningPosition) {
                tracker.addLocationListener(this)
                tracker.addGeohashListener(this)
                view.text = resources.getString(R.string.stop_listening_tracking)
            }else {
                tracker.removeLocationListener()
                tracker.removeGeohashListener()
                view.text = resources.getString(R.string.start_listening_tracking)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun onClickToStartStopSendingLocation (view: View) {
        if (view is Button) {
            if (isServiceRunning) {
                isServiceRunning = false
                tracker.stopTracking()
                view.text = resources.getString(R.string.start_sending_location)
            } else {
                isServiceRunning = true
                tracker.startTracking()
                view.text = resources.getString(R.string.stop_sending_location)
            }
        }
    }

    fun onClickToClearRoute (v: View) {
        Log.d(MapsActivity::class.java.name, "onClickToClearRoute: $v")

        polylines.forEach { polyline: Polyline -> polyline.remove() }
        polylines.clear()

        polylinesGeoHash.forEach { polyline: Polyline -> polyline.remove() }
        polylinesGeoHash.clear()

        markersToGeohash.forEach { marker -> marker.remove() }
        markersToGeohash.clear()
    }

    fun onClickToSaveRoute (v: View) {
        Log.d(MapsActivity::class.java.name, "onClickToSaveRoute: $v")

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
