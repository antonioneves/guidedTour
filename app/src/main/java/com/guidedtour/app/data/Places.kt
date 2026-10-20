package com.guidedtour.app.data

import java.time.Instant
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sqrt

/** Scans taken within [PLACE_RADIUS_M] of each other share one map pin. */
const val PLACE_RADIUS_M = 50.0

/** A day's route is drawn only when the walk between its places adds up to at least this. */
const val MIN_ROUTE_M = 150.0

/** Places further apart than this in a row were reached by car, train or plane: the route breaks there. */
const val MAX_LEG_M = 5_000.0

/** Scans taken close together, shown as one pin at their average position. [scans] is oldest first. */
data class Place(val latitude: Double, val longitude: Double, val scans: List<Scan>)

/**
 * Groups scans with a location into places: each scan joins the first place whose centre is within
 * [radius] metres, else starts a new one. Scans are taken oldest first, so places keep their order.
 */
fun groupPlaces(scans: List<Scan>, radius: Double = PLACE_RADIUS_M): List<Place> {
    val places = mutableListOf<Place>()
    for (scan in scans.filter { it.latitude != null && it.longitude != null }.sortedBy { it.timestamp }) {
        val lat = scan.latitude!!
        val lon = scan.longitude!!
        val index = places.indexOfFirst { distanceMeters(it.latitude, it.longitude, lat, lon) <= radius }
        if (index < 0) {
            places += Place(lat, lon, listOf(scan))
        } else {
            val place = places[index]
            val n = place.scans.size
            places[index] = Place(
                (place.latitude * n + lat) / (n + 1),
                (place.longitude * n + lon) / (n + 1),
                place.scans + scan,
            )
        }
    }
    return places
}

/**
 * Walking routes per day, through the places in the order they were visited, skipping repeats of
 * the same place in a row. A route breaks where two places in a row are more than [maxLeg] metres
 * apart, and pieces shorter than [minLength] metres are left out.
 */
fun dailyRoutes(
    places: List<Place>,
    minLength: Double = MIN_ROUTE_M,
    maxLeg: Double = MAX_LEG_M,
    zone: ZoneId = ZoneId.systemDefault(),
): List<List<Place>> {
    val placeOf = places.flatMap { place -> place.scans.map { it.id to place } }.toMap()
    return places.flatMap { it.scans }
        .sortedBy { it.timestamp }
        .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        .values
        .map { day -> day.mapNotNull { placeOf[it.id] }.fold(mutableListOf<Place>()) { route, p -> route.apply { if (lastOrNull() !== p) add(p) } } }
        .flatMap { route -> splitLongLegs(route, maxLeg) }
        .filter { route -> route.zipWithNext(::distance).sum() >= minLength }
}

private fun splitLongLegs(route: List<Place>, maxLeg: Double): List<List<Place>> {
    val pieces = mutableListOf(mutableListOf<Place>())
    for (place in route) {
        val last = pieces.last().lastOrNull()
        if (last != null && distance(last, place) > maxLeg) pieces += mutableListOf<Place>()
        pieces.last() += place
    }
    return pieces
}

private fun distance(a: Place, b: Place) = distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

/** Great-circle distance in metres. */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * kotlin.math.sin(dLon / 2).let { it * it }
    return 2 * 6_371_000.0 * asin(sqrt(a))
}
