package com.guidedtour.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guidedtour.app.TourViewModel

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Scan("Scan", Icons.Outlined.CameraAlt, Icons.Filled.CameraAlt),
    Places("Places", Icons.Outlined.Map, Icons.Filled.Map),
    Texts("Texts", Icons.AutoMirrored.Outlined.Notes, Icons.AutoMirrored.Filled.Notes),
}

@Composable
fun TourApp(viewModel: TourViewModel) {
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        permissions.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        )
    }

    var selected by rememberSaveable { mutableStateOf(Tab.Scan) }
    var showQueue by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val player by viewModel.player.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(selected) { viewModel.scanTabVisible.value = selected == Tab.Scan }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                item(
                    selected = selected == tab,
                    onClick = { selected = tab },
                    icon = { Icon(if (selected == tab) tab.selectedIcon else tab.icon, contentDescription = null) },
                    label = { Text(tab.label) },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                // The camera stays bound while you look at other tabs, so coming back is instant.
                // It sits underneath; the other tabs are opaque and cover it.
                val onScan = selected == Tab.Scan
                Box(Modifier.fillMaxSize().then(if (onScan) Modifier else Modifier.clearAndSetSemantics {})) {
                    CameraScreen(viewModel, visible = onScan, onOpenSettings = { showSettings = true })
                }
                when (selected) {
                    Tab.Scan -> Unit
                    Tab.Places -> Surface(Modifier.fillMaxSize()) { MapScreen(viewModel) }
                    Tab.Texts -> Surface(Modifier.fillMaxSize()) { TextsScreen(viewModel, onOpenSettings = { showSettings = true }) }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
            AnimatedVisibility(player.active) {
                NowPlayingBar(player, viewModel, onOpenQueue = { showQueue = true })
            }
        }
    }

    if (showQueue) QueueSheet(viewModel, onDismiss = { showQueue = false })
    if (showSettings) SettingsSheet(viewModel, onDismiss = { showSettings = false })
}
