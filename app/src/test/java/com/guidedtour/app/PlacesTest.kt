package com.guidedtour.app

import com.guidedtour.app.data.Scan
import com.guidedtour.app.data.dailyRoutes
import com.guidedtour.app.data.distanceMeters
import com.guidedtour.app.data.groupPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class PlacesTest {
    private val day = 1_699_963_200_000L // 14 Nov 2023, 12:00 UTC
    private fun scan(id: Long, lat: Double, lon: Double, minutes: Long, days: Long = 0) =
        Scan(id = id, text = "t$id", timestamp = day + days * 86_400_000 + minutes * 60_000, latitude = lat, longitude = lon, placeName = null, languageTag = null)

    // About 11 m per 0.0001 degree of latitude.
    private val museum = listOf(scan(1, 37.96850, 23.7285, 0), scan(2, 37.96870, 23.7285, 10), scan(3, 37.96860, 23.7286, 20))

    @Test fun distanceIsAboutRight() {
        val d = distanceMeters(37.9685, 23.7285, 37.9715, 23.7267)
        assertTrue(d in 360.0..380.0)
    }

    @Test fun scansInOneMuseumShareAPinAndHaveNoRoute() {
        val places = groupPlaces(museum)
        assertEquals(1, places.size)
        assertEquals(3, places[0].scans.size)
        assertEquals(emptyList<Any>(), dailyRoutes(places, zone = ZoneOffset.UTC))
    }

    @Test fun walkBetweenSitesGetsARoute() {
        val parthenon = scan(4, 37.9715, 23.7267, 60)
        val agora = scan(5, 37.9746, 23.7223, 120)
        val places = groupPlaces(museum + parthenon + agora)
        assertEquals(3, places.size)
        val routes = dailyRoutes(places, zone = ZoneOffset.UTC)
        assertEquals(1, routes.size)
        assertEquals(3, routes[0].size)
    }

    @Test fun shortHopGetsNoRoute() {
        val nextRoom = scan(6, 37.96950, 23.7285, 30) // about 100 m from the museum pin
        val places = groupPlaces(museum + nextRoom)
        assertEquals(2, places.size)
        assertEquals(emptyList<Any>(), dailyRoutes(places, zone = ZoneOffset.UTC))
    }

    @Test fun routesAreSplitByDay() {
        val places = groupPlaces(listOf(scan(1, 37.9685, 23.7285, 0), scan(2, 37.9746, 23.7223, 60), scan(3, 37.9685, 23.7285, 0, days = 1)))
        val routes = dailyRoutes(places, zone = ZoneOffset.UTC)
        assertEquals(1, routes.size)
    }

    @Test fun routeBreaksAtAFlight() {
        val athens = listOf(scan(1, 37.9685, 23.7285, 0), scan(2, 37.9746, 23.7223, 60))
        val california = listOf(scan(3, 37.4220, -122.0841, 300), scan(4, 37.4300, -122.0841, 360))
        val routes = dailyRoutes(groupPlaces(athens + california), zone = ZoneOffset.UTC)
        assertEquals(2, routes.size)
        assertTrue(routes.all { it.size == 2 })
    }
}
