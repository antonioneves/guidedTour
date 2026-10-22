package com.guidedtour.app

import com.guidedtour.app.data.OfflineMaps
import com.guidedtour.app.data.tiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapsTest {
    @Test fun theWholeWorldAtZoomOneIsFourTiles() {
        assertEquals(1L + 4L, tiles(-85.0, -180.0, 85.0, 179.999, maxZoom = 1))
    }

    @Test fun aCityFitsAndACountryDoesNot() {
        // Central Athens, about 6 × 5 km.
        assertTrue(tiles(37.95, 23.70, 38.00, 23.77) <= OfflineMaps.MAX_TILES)
        // All of Greece.
        assertTrue(tiles(34.8, 19.3, 41.8, 28.3) > OfflineMaps.MAX_TILES)
    }
}
