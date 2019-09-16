package com.leomedeiros.trackerconsumer.dto

import br.com.phonetracker.lib.commons.GeoPoint
import com.google.android.gms.maps.model.LatLng

class History private constructor() {
    var route: MutableList<GeoPoint> = mutableListOf()
    var routeGeoHashFilter: MutableList<GeoPoint> = mutableListOf()

    constructor(route: MutableList<LatLng>, routeGeoHashFilter: MutableList<LatLng>) : this () {
        route.forEach {
            position ->
            this.route.add(GeoPoint(position.latitude, position.longitude))
        }

        routeGeoHashFilter.forEach {
            position ->
            this.routeGeoHashFilter.add(GeoPoint(position.latitude, position.longitude))
        }
    }
}