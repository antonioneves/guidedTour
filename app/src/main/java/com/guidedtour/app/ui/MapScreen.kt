package com.guidedtour.app.ui

import androidx.compose.foundation.clickable
import com.google.maps.android.compose.Polyline
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import android.net.NetworkCapabilities
import android.net.ConnectivityManager
import android.content.pm.PackageManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.guidedtour.app.TourViewModel
import kotlinx.coroutines.launch

private val PeekHeight = 200.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: TourViewModel) {
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val located = scans.filter { it.latitude != null && it.longitude != null }
    val camera = rememberCameraPositionState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetScaffoldState()
    var mapLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val hasKey = remember { mapsKeyPresent(context) }
    var online by remember { mutableStateOf(isOnline(context)) }
    LifecycleResumeEffect(Unit) {
        online = isOnline(context)
        onPauseOrDispose {}
    }
    // One route line per day, oldest to newest.
    val routes = remember(located) {
        located.groupBy { java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
            .values.map { day -> day.sortedBy { it.timestamp }.map { LatLng(it.latitude!!, it.longitude!!) } }
            .filter { it.size > 1 }
    }
    val routeColor = MaterialTheme.colorScheme.primary

    // Fit every recorded place on screen whenever the set of places changes.
    LaunchedEffect(mapLoaded, located.size) {
        if (!hasKey || !mapLoaded || located.isEmpty()) return@LaunchedEffect
        val points = located.map { LatLng(it.latitude!!, it.longitude!!) }
        if (points.size == 1) {
            camera.animate(CameraUpdateFactory.newLatLngZoom(points[0], 17f))
        } else {
            val bounds = LatLngBounds.builder().apply { points.forEach(::include) }.build()
            camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
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
                                    camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(scan.latitude!!, scan.longitude!!), 18f))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
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
            if (!hasKey) {
                MapUnavailable(
                    "The map needs a Google Maps API key",
                    "Add MAPS_API_KEY=your_key to local.properties and rebuild. Your scans are still listed below.",
                )
            } else {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = camera,
                    contentPadding = PaddingValues(bottom = PeekHeight),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
                    mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
                    onMapLoaded = { mapLoaded = true },
                ) {
                    routes.forEach { Polyline(points = it, color = routeColor, width = 8f) }
                    located.forEach { scan ->
                        Marker(
                            state = rememberMarkerState(key = scan.id.toString(), position = LatLng(scan.latitude!!, scan.longitude!!)),
                            title = scan.placeLabel(),
                            snippet = scan.text.take(80),
                        )
                    }
                }
                if (!online) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Text("You're offline – map tiles may not load.", Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MapUnavailable(title: String, detail: String) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(32.dp).padding(bottom = PeekHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Place, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private fun mapsKeyPresent(context: Context): Boolean = runCatching {
    val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    !info.metaData?.getString("com.google.android.geo.API_KEY").isNullOrBlank()
}.getOrDefault(false)

private fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
