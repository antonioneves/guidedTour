package com.guidedtour.app.ui

import android.Manifest
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guidedtour.app.R
import com.guidedtour.app.ScanState
import com.guidedtour.app.TourViewModel
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector?, val selectedIcon: ImageVector?, @DrawableRes val iconRes: Int? = null) {
    Scan("Scan", Icons.Outlined.CameraAlt, Icons.Filled.CameraAlt),
    Places("Places", Icons.Outlined.Map, Icons.Filled.Map),
    // The five columns from the app icon; one silhouette serves both states.
    Guides("Guides", null, null, R.drawable.ic_tab_guides),
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
    // Like a camera app, the camera screen keeps its layout when the phone is turned; its icons and
    // tips turn instead to stay upright. The other tabs follow the phone's auto-rotate setting.
    val activity = LocalActivity.current
    DisposableEffect(selected, activity) {
        activity?.requestedOrientation =
            if (selected == Tab.Scan) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val uprightTurns by rememberUprightTurns(active = selected == Tab.Scan)
    val iconAngle = animatedUprightAngle(uprightTurns)
    // Show each new scan in Guides as soon as it's read (from the camera, after its photo has dropped
    // into the tab); go back to the camera if no text was found.
    val scanState by viewModel.state.collectAsStateWithLifecycle()
    val guidesBounce = remember { Animatable(1f) }
    val flight = remember { CaptureFlight() }
    val scope = rememberCoroutineScope()
    fun openGuides(bounce: Boolean) {
        selected = Tab.Guides
        if (bounce) scope.launch {
            guidesBounce.snapTo(1.35f)
            guidesBounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    LaunchedEffect(scanState) {
        when (scanState) {
            is ScanState.Done -> if (selected != Tab.Scan) openGuides(bounce = false)
            ScanState.NoText -> selected = Tab.Scan
            else -> Unit
        }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                item(
                    selected = selected == tab,
                    onClick = { selected = tab },
                    icon = {
                        val vector = if (selected == tab) tab.selectedIcon else tab.icon
                        val bounce = (if (tab == Tab.Guides) {
                            Modifier.scale(guidesBounce.value).onGloballyPositioned { flight.target = it.boundsInRoot().center }
                        } else Modifier).rotate(iconAngle)
                        if (vector != null) Icon(vector, contentDescription = null, modifier = bounce)
                        else Icon(painterResource(tab.iconRes!!), contentDescription = null, modifier = bounce)
                    },
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
                    CameraScreen(viewModel, visible = onScan, uprightTurns = uprightTurns, flight = flight, onOpenSettings = { showSettings = true }, onDropped = { openGuides(bounce = true) })
                }
                when (selected) {
                    Tab.Scan -> Unit
                    Tab.Places -> Surface(Modifier.fillMaxSize()) { MapScreen(viewModel) }
                    Tab.Guides -> Surface(Modifier.fillMaxSize()) { TextsScreen(viewModel, onOpenSettings = { showSettings = true }) }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
            AnimatedVisibility(player.active) {
                NowPlayingBar(player, viewModel, onOpenQueue = { showQueue = true })
            }
        }
    }
    CaptureFlightOverlay(flight)
    }

    if (showQueue) QueueSheet(viewModel, onDismiss = { showQueue = false })
    if (showSettings) SettingsSheet(viewModel, onDismiss = { showSettings = false })
}
