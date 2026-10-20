package com.guidedtour.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.PathParser
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guidedtour.app.TourViewModel
import com.guidedtour.app.data.Scan
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private val PeekHeight = 200.dp

// Free OpenStreetMap tiles from OpenFreeMap: no key or account needed.
private const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

private const val ROUTES = "routes"
private const val PINS = "pins"
private const val PIN_IMAGE = "pin"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: TourViewModel) {
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val located = scans.filter { it.latitude != null && it.longitude != null }
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetScaffoldState()
    val context = LocalContext.current
    var online by remember { mutableStateOf(isOnline(context)) }
    LifecycleResumeEffect(Unit) {
        online = isOnline(context)
        onPauseOrDispose {}
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var selected by remember { mutableStateOf<Scan?>(null) }
    val dark = isSystemInDarkTheme()
    val routeColor = MaterialTheme.colorScheme.primary.toArgb()
    val bottomPadding = with(LocalDensity.current) { PeekHeight.toPx().toDouble() }
    val currentLocated by rememberUpdatedState(located)

    // One route line per day, oldest to newest.
    val routes = remember(located) {
        located.groupBy { java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
            .values.map { day -> day.sortedBy { it.timestamp }.map { Point.fromLngLat(it.longitude!!, it.latitude!!) } }
            .filter { it.size > 1 }
    }

    // Load the light or dark style, then add the route and pin layers on top of it.
    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.setStyle(Style.Builder().fromUri(if (dark) DARK_STYLE else LIGHT_STYLE)) { loaded ->
            loaded.addImage(PIN_IMAGE, pinBitmap(context, routeColor))
            loaded.addSource(GeoJsonSource(ROUTES))
            loaded.addSource(GeoJsonSource(PINS))
            loaded.addLayer(
                LineLayer(ROUTES, ROUTES).withProperties(
                    PropertyFactory.lineColor(routeColor),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineOpacity(0.85f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            loaded.addLayer(
                SymbolLayer(PINS, PINS).withProperties(
                    PropertyFactory.iconImage(PIN_IMAGE),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                ),
            )
            style = loaded
        }
    }

    // Keep the map data in step with the saved scans.
    LaunchedEffect(style, routes, located) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(ROUTES)?.setGeoJson(
            FeatureCollection.fromFeatures(routes.map { Feature.fromGeometry(LineString.fromLngLats(it)) }),
        )
        s.getSourceAs<GeoJsonSource>(PINS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                located.map { scan ->
                    Feature.fromGeometry(Point.fromLngLat(scan.longitude!!, scan.latitude!!)).apply { addNumberProperty("id", scan.id) }
                },
            ),
        )
    }

    // Fit every recorded place on screen whenever the set of places changes.
    LaunchedEffect(style, located.size) {
        val m = map ?: return@LaunchedEffect
        if (style == null || located.isEmpty()) return@LaunchedEffect
        val points = located.map { LatLng(it.latitude!!, it.longitude!!) }
        if (points.size == 1) {
            m.easeCamera(CameraUpdateFactory.newCameraPosition(cameraAt(points[0], 17.0, bottomPadding)))
        } else {
            val bounds = LatLngBounds.Builder().includes(points).build()
            m.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120, 160, 120, 120 + bottomPadding.toInt()))
        }
    }

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = PeekHeight,
        sheetContent = {
            Text(
                "Places visited",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
            )
            Text(
                if (located.size == 1) "1 scan with a location" else "${located.size} scans with a location",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            if (located.isEmpty()) {
                Text(
                    "Scan some text with location permission enabled and your route will appear here.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(located, key = { it.id }) { scan ->
                        ListItem(
                            modifier = Modifier.clickable {
                                scope.launch {
                                    sheetState.bottomSheetState.partialExpand()
                                    selected = scan
                                    map?.animateCamera(
                                        CameraUpdateFactory.newCameraPosition(
                                            cameraAt(LatLng(scan.latitude!!, scan.longitude!!), 18.0, bottomPadding),
                                        ),
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            },
                            headlineContent = { Text(scan.placeLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(scan.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            trailingContent = { Text(scan.timeLabel(), style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            MapLibreView(
                bottomPadding = bottomPadding.toInt(),
                onMapReady = { m ->
                    m.uiSettings.isCompassEnabled = true
                    m.addOnMapClickListener { point ->
                        val screen = m.projection.toScreenLocation(point)
                        val id = m.queryRenderedFeatures(screen, PINS).firstOrNull()?.getNumberProperty("id")?.toLong()
                        selected = currentLocated.firstOrNull { it.id == id }
                        id != null
                    }
                    map = m
                },
            )
            // OpenStreetMap's licence asks for visible credit; the (i) button lists the full sources.
            Text(
                "© OpenStreetMap",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = PeekHeight + 20.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), MaterialTheme.shapes.small)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp)) {
                if (!online) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Text("You're offline – only places you've already viewed will show.", Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
                selected?.let { scan -> SelectedPlace(scan, onPlay = { viewModel.playNow(scan) }, onClose = { selected = null }) }
            }
        }
    }
}

/** A card for the tapped pin, in place of Google Maps' info window. */
@Composable
private fun SelectedPlace(scan: Scan, onPlay: () -> Unit, onClose: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(scan.placeLabel(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    scan.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, "Read aloud") }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
        }
    }
}

/** MapLibre's MapView, following the screen's lifecycle. */
@Composable
private fun MapLibreView(bottomPadding: Int, onMapReady: (MapLibreMap) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                // Keep the logo and the OpenStreetMap credit above the bottom sheet.
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.attributionGravity = Gravity.BOTTOM or Gravity.START
                m.uiSettings.setAttributionMargins(24, 0, 0, bottomPadding + 16)
                onMapReady(m)
            }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

private fun cameraAt(target: LatLng, zoom: Double, bottomPadding: Double): CameraPosition =
    CameraPosition.Builder().target(target).zoom(zoom).padding(0.0, 0.0, 0.0, bottomPadding).build()

/** The Material "place" pin in [color] with a white outline, sized for the map. */
private fun pinBitmap(context: Context, color: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (36 * density).toInt()
    val path = PathParser.createPathFromPathData(
        "M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7zM12,11.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -2.5,2.5 -2.5,2.5z",
    )
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        scale(size / 24f, size / 24f)
        drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; this.color = android.graphics.Color.WHITE })
        drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }
    return bitmap
}

private fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
