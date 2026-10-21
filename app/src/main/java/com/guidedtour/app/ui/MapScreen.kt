package com.guidedtour.app.ui

import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ListItem
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.PathParser
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.animateContentSize
import com.guidedtour.app.TourViewModel
import com.guidedtour.app.data.Place
import com.guidedtour.app.data.PlayerState
import com.guidedtour.app.data.Scan
import com.guidedtour.app.data.dailyRoutes
import com.guidedtour.app.data.groupPlaces
import com.guidedtour.app.data.photoSpot
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
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
private const val ICON = "icon"
private const val PLACE_INDEX = "place"

private fun pinImage(count: Int) = if (count == 1) "pin" else "pin-$count"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: TourViewModel) {
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
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
    // The place whose pin was tapped (by its first scan's id): its row is scrolled to and highlighted.
    var selected by remember { mutableStateOf<Long?>(null) }
    var showCredits by remember { mutableStateOf(false) }
    // A tapped pin holding several texts (by its first scan's id): they're listed in a sheet.
    var stack by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val dark = isSystemInDarkTheme()
    val routeColor = MaterialTheme.colorScheme.primary.toArgb()
    val bottomPadding = with(LocalDensity.current) { PeekHeight.toPx().toDouble() }
    // Scans taken close together share one pin; a day's route shows only for a real walk.
    val places = remember(located) { groupPlaces(located) }
    val routes = remember(places) { dailyRoutes(places).map { route -> route.map { Point.fromLngLat(it.longitude, it.latitude) } } }
    val listed = remember(places) { places.sortedByDescending { it.scans.last().timestamp } }
    val currentPlaces by rememberUpdatedState(places)
    val currentListed by rememberUpdatedState(listed)

    // Load the light or dark style, then add the route and pin layers on top of it.
    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.setStyle(Style.Builder().fromUri(if (dark) DARK_STYLE else LIGHT_STYLE)) { loaded ->
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
                    PropertyFactory.iconImage(Expression.get(ICON)),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                ),
            )
            style = loaded
        }
    }

    // Keep the map data in step with the saved scans.
    LaunchedEffect(style, routes, places) {
        val s = style ?: return@LaunchedEffect
        // One pin image per group size: the plain pin, or one showing how many texts it holds.
        places.map { it.scans.size }.distinct().forEach { count ->
            if (s.getImage(pinImage(count)) == null) s.addImage(pinImage(count), pinBitmap(context, routeColor, count))
        }
        s.getSourceAs<GeoJsonSource>(ROUTES)?.setGeoJson(
            FeatureCollection.fromFeatures(routes.map { Feature.fromGeometry(LineString.fromLngLats(it)) }),
        )
        s.getSourceAs<GeoJsonSource>(PINS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                places.mapIndexed { index, place ->
                    Feature.fromGeometry(Point.fromLngLat(place.longitude, place.latitude)).apply {
                        addNumberProperty(PLACE_INDEX, index)
                        addStringProperty(ICON, pinImage(place.scans.size))
                    }
                },
            ),
        )
    }

    // Fit every recorded place on screen whenever the set of places changes.
    LaunchedEffect(style, places.size) {
        val m = map ?: return@LaunchedEffect
        if (style == null || places.isEmpty()) return@LaunchedEffect
        val points = places.map { LatLng(it.latitude, it.longitude) }
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
                // One row per place (like the pins), most recent first. Tapping a row moves the map
                // there; its button plays the place's texts and shows whether they are playing.
                // Space below the last row lets any row scroll to the top of the lowered sheet when its pin is tapped.
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = screenHeight),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(listed, key = { it.scans.first().id }) { place ->
                        PlaceRow(
                            place,
                            player,
                            viewModel,
                            highlighted = place.scans.first().id == selected,
                            onClick = {
                                scope.launch {
                                    sheetState.bottomSheetState.partialExpand()
                                    selected = null
                                    map?.animateCamera(
                                        CameraUpdateFactory.newCameraPosition(cameraAt(LatLng(place.latitude, place.longitude), 18.0, bottomPadding)),
                                    )
                                }
                            },
                            onPlay = {
                                val ids = place.scans.map { it.id }.toSet()
                                when {
                                    player.current?.id in ids -> viewModel.togglePlayback()
                                    place.scans.size == 1 -> viewModel.playNow(place.scans[0])
                                    else -> viewModel.playAll(place.scans)
                                }
                            },
                        )
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            MapLibreView(
                onMapReady = { m ->
                    m.uiSettings.isCompassEnabled = true
                    m.addOnMapClickListener { point ->
                        val screen = m.projection.toScreenLocation(point)
                        val index = m.queryRenderedFeatures(screen, PINS).firstOrNull()?.getNumberProperty(PLACE_INDEX)?.toInt()
                        val place = index?.let { currentPlaces.getOrNull(it) }
                        val key = place?.scans?.first()?.id
                        selected = key
                        if (place != null && place.scans.size > 1) stack = key
                        // Bring the tapped place's row into view in the list below.
                        val row = currentListed.indexOfFirst { it.scans.first().id == key }
                        if (row >= 0) scope.launch { listState.animateScrollToItem(row) }
                        key != null
                    }
                    map = m
                },
            )
            // OpenStreetMap's licence asks for visible credit; tapping it lists every source.
            Surface(
                onClick = { showCredits = true },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = PeekHeight + 20.dp),
            ) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("© OpenStreetMap", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (showCredits) MapCredits(onDismiss = { showCredits = false })
            places.firstOrNull { it.scans.first().id == stack }?.let { place ->
                PlaceSheet(place, player, viewModel, onDismiss = { stack = null })
            }
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
            }
        }
    }
}

/**
 * A place visited, as a card with the place's photo behind it (darkened under white text), or a plain
 * card without one. The place being read, or whose pin was tapped, is outlined.
 */
@Composable
private fun PlaceRow(place: Place, player: PlayerState, viewModel: TourViewModel, highlighted: Boolean, onClick: () -> Unit, onPlay: () -> Unit) {
    val first = place.scans.first()
    val active = player.current?.id in place.scans.map { it.id }
    val playing = active && player.playing
    val photo = rememberPlacePhoto(viewModel, place.photoSpot())
    val colors = MaterialTheme.colorScheme
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = if (active && photo == null) colors.primaryContainer else colors.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
            .then(if (active || highlighted) Modifier.border(3.dp, colors.primary, CardDefaults.elevatedShape) else Modifier)
            .semantics { onClick(label = "Show on the map") { onClick(); true } },
    ) {
        Box(Modifier.height(IntrinsicSize.Min)) {
            if (photo != null) {
                Image(photo.image, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 1f to Color.Black.copy(alpha = 0.7f)),
                    ),
                )
            }
            CompositionLocalProvider(LocalContentColor provides if (photo != null) Color.White else LocalContentColor.current) {
                PlaceRowContent(place, first, playing, active, onPlay, onPhoto = photo != null)
            }
        }
    }
}

@Composable
private fun PlaceRowContent(place: Place, first: Scan, playing: Boolean, active: Boolean, onPlay: () -> Unit, onPhoto: Boolean) {
    // Pin (or count) with the time below it, then the place and its text, then play centred.
    val secondary = if (onPhoto) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (onPhoto) 112.dp else 0.dp).padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (place.scans.size > 1) {
                        Text("${place.scans.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else {
                        Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            Text(
                place.scans.last().shortTimeLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = secondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(first.placeLabel(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (place.scans.size == 1) first.text else "${place.scans.size} texts",
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val description = when {
            playing -> "Pause"
            active -> "Resume"
            place.scans.size == 1 -> "Read aloud"
            else -> "Read all aloud"
        }
        FilledTonalIconButton(onClick = onPlay) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, description)
        }
    }
}

/** MapLibre's MapView, following the screen's lifecycle. */
@Composable
private fun MapLibreView(onMapReady: (MapLibreMap) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                // The credits are shown by MapCredits instead of MapLibre's own (i) dialog.
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.isAttributionEnabled = false
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

/**
 * The Material "place" pin in [color] with a white outline, sized for the map. With a [count] above
 * one, the pin's head shows that number instead of a hole.
 */
private fun pinBitmap(context: Context, color: Int, count: Int = 1): Bitmap {
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
        if (count > 1) {
            drawCircle(12f, 9f, 4.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE })
            val label = if (count > 99) "99+" else count.toString()
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textSize = if (label.length == 1) 6.2f else if (label.length == 2) 5f else 3.8f
            }
            drawText(label, 12f, 9f - (text.descent() + text.ascent()) / 2, text)
        }
    }
    return bitmap
}

private fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** Who made the map, each linking to its site: the credit OpenStreetMap and OpenMapTiles ask for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapCredits(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text("Map data", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            (MAP_CREDITS + IMPROVE_MAP).forEach { CreditRow(it) }
        }
    }
}

private val IMPROVE_MAP = Credit("Improve this map", "Report a mistake to OpenStreetMap", "https://www.openstreetmap.org/fix-the-map")

/** Every text of a pin that holds several, as cards under the place's photo; tap one to hear it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceSheet(place: Place, player: PlayerState, viewModel: TourViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val photo = rememberPlacePhoto(viewModel, place.photoSpot())
        val name = place.scans.first().placeLabel()
        val count = "${place.scans.size} texts"
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                val playAll = @Composable {
                    FilledTonalButton(onClick = { viewModel.playAll(place.scans) }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Play all")
                    }
                }
                if (photo != null) {
                    PlacePhotoBanner(photo, Modifier.padding(bottom = 4.dp)) {
                        Row(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.semantics { heading() })
                                Text(count, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                            }
                            playAll()
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                            Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        playAll()
                    }
                }
            }
            // Oldest first, the order they were walked and "Play all" reads them.
            items(place.scans, key = { it.id }) { scan ->
                val current = player.current?.id == scan.id
                val playing = current && player.playing
                val play = { if (current) viewModel.togglePlayback() else viewModel.playNow(scan) }
                TextCard(
                    status = when {
                        playing -> "Now playing"
                        current -> "Paused"
                        else -> scan.timeLabel()
                    },
                    title = null,
                    text = scan.text,
                    current = current,
                    image = {
                        if (scan.thumbnail != null) Thumbnail(scan.thumbnail, Modifier.fillMaxSize())
                        else Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    },
                    onClick = play,
                    onClickLabel = if (playing) "Pause" else "Read aloud",
                ) {
                    FilledTonalIconButton(onClick = play) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Read aloud")
                    }
                }
            }
        }
    }
}
