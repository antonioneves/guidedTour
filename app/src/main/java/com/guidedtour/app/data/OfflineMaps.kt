package com.guidedtour.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.util.UUID
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** A map area saved for offline use, in both map styles (light and dark): its regions, name and size. */
data class SavedArea(val key: String, val name: String, val regions: List<OfflineRegion>, val bytes: Long, val complete: Boolean)

/**
 * Map areas saved for use without a connection, so the Places map works abroad without data: the
 * area on screen is downloaded from zoom [MIN_ZOOM] to [MAX_ZOOM], for the light and the dark map,
 * with MapLibre's offline regions (which share tiles with each other and with the map's cache).
 * Its calls and callbacks run on the main thread, as MapLibre's do.
 */
class OfflineMaps private constructor(context: Context) {
    companion object {
        @Volatile private var instance: OfflineMaps? = null
        fun get(context: Context): OfflineMaps =
            instance ?: synchronized(this) { instance ?: OfflineMaps(context.applicationContext).also { instance = it } }

        private const val MIN_ZOOM = 0.0
        /** The map's tiles have detail up to zoom 14; closer, MapLibre enlarges them. */
        private const val MAX_ZOOM = 14.0
        /** About the tiles of a large city: more is likely a mistake (and a long download). */
        const val MAX_TILES = 6_000
    }

    private val manager = OfflineManager.getInstance(context)
    private val pixelRatio = context.resources.displayMetrics.density

    private val _areas = MutableStateFlow<List<SavedArea>>(emptyList())
    val areas: StateFlow<List<SavedArea>> = _areas

    /** How far the area being saved has come, 0 to 1; null when nothing is being saved. */
    private val _saving = MutableStateFlow<Float?>(null)
    val saving: StateFlow<Float?> = _saving

    /** Reads the saved areas again. */
    fun refresh() {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions.orEmpty().toList()
                val statuses = HashMap<Long, OfflineRegionStatus>()
                if (regions.isEmpty()) { _areas.value = emptyList(); return }
                var answered = 0
                regions.forEach { region ->
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            status?.let { statuses[region.id] = it }
                            if (++answered == regions.size) publish(regions, statuses)
                        }
                        override fun onError(error: String?) { if (++answered == regions.size) publish(regions, statuses) }
                    })
                }
            }
            override fun onError(error: String) { android.util.Log.w("GuidedTour", "offline maps: can't list: $error") }
        })
    }

    private fun publish(regions: List<OfflineRegion>, statuses: Map<Long, OfflineRegionStatus>) {
        _areas.value = regions.groupBy { meta(it).optString("area", it.id.toString()) }.map { (key, parts) ->
            SavedArea(
                key,
                meta(parts.first()).optString("name", "Map area"),
                parts,
                parts.sumOf { statuses[it.id]?.completedResourceSize ?: 0L },
                parts.all { statuses[it.id]?.isComplete == true },
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** The regions of the area being saved, to stop it. */
    private val downloading = mutableListOf<OfflineRegion>()
    private var stop: (() -> Unit)? = null

    /** Stops saving the area being saved, and removes what came down of it. */
    fun cancel() { stop?.invoke() }

    private fun meta(region: OfflineRegion) = runCatching { JSONObject(String(region.metadata)) }.getOrDefault(JSONObject())

    /**
     * Saves [bounds] as an area called [name], for each map style in [styles]; [onDone] says whether
     * it all came down. Areas over [MAX_TILES] tiles aren't saved (see [tiles]).
     */
    fun save(bounds: LatLngBounds, name: String, styles: List<String>, onDone: (Boolean) -> Unit) {
        if (_saving.value != null) return
        _saving.value = 0f
        val key = UUID.randomUUID().toString()
        val metadata = JSONObject().put("name", name).put("area", key).toString().toByteArray()
        val progress = DoubleArray(styles.size)
        var left = styles.size
        var failed = false
        var stopped = false
        fun finish(ok: Boolean) {
            failed = failed || !ok
            if (--left > 0 || stopped) return
            _saving.value = null
            downloading.clear()
            stop = null
            refresh()
            onDone(!failed)
        }
        stop = {
            stopped = true
            downloading.forEach { it.setObserver(null) }
            val parts = downloading.toList()
            downloading.clear()
            stop = null
            _saving.value = null
            delete(SavedArea(key, name, parts, 0, false))
            onDone(false)
        }
        styles.forEachIndexed { i, style ->
            val definition = OfflineTilePyramidRegionDefinition(style, bounds, MIN_ZOOM, MAX_ZOOM, pixelRatio)
            manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    if (stopped) { delete(SavedArea(key, name, listOf(offlineRegion), 0, false)); return }
                    downloading += offlineRegion
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            if (status.requiredResourceCount > 0) progress[i] = status.completedResourceCount.toDouble() / status.requiredResourceCount
                            _saving.value = progress.average().toFloat()
                            if (status.isComplete) {
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                offlineRegion.setObserver(null)
                                finish(true)
                            }
                        }
                        override fun onError(error: OfflineRegionError) {
                            // Mostly a dropped connection, which MapLibre retries itself; a missing tile is no reason to stop.
                            android.util.Log.w("GuidedTour", "offline maps: ${error.reason}: ${error.message}")
                        }
                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            offlineRegion.setObserver(null)
                            finish(false)
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }
                override fun onError(error: String) {
                    android.util.Log.w("GuidedTour", "offline maps: can't create: $error")
                    finish(false)
                }
            })
        }
    }

    /** Removes [area]'s map from the phone. */
    fun delete(area: SavedArea) {
        if (area.regions.isEmpty()) { refresh(); return }
        var left = area.regions.size
        area.regions.forEach { region ->
            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() { if (--left == 0) refresh() }
                override fun onError(error: String) { android.util.Log.w("GuidedTour", "offline maps: can't delete: $error"); if (--left == 0) refresh() }
            })
        }
    }
}

/** How many map tiles cover the area from [south] to [north] and [west] to [east], at zooms 0 to [maxZoom]. */
fun tiles(south: Double, west: Double, north: Double, east: Double, maxZoom: Int = 14): Long {
    fun x(lng: Double, z: Int) = floor((lng + 180) / 360 * (1 shl z)).toLong()
    fun y(lat: Double, z: Int): Long {
        val r = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return floor((1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2 * (1 shl z)).toLong()
    }
    return (0..maxZoom).sumOf { z -> (x(east, z) - x(west, z) + 1) * (y(south, z) - y(north, z) + 1) }
}
