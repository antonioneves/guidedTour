package com.guidedtour.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.provider.Settings
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guidedtour.app.ScanState
import com.guidedtour.app.TourViewModel
import com.guidedtour.app.data.Recognizer
import com.guidedtour.app.data.cleanOcr
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val AUTO_CAPTURE_MS = 2500L
private const val IDLE_RELEASE_MS = 20_000L
private const val CAREFUL_PHOTOS = 3
private const val FLASH_MS = 250
private const val SHRINK_MS = 450
private const val MIN_HOLD_STILL_MS = 1000L
private const val TAG = "GuidedTour"
private const val CARD_SCALE = 0.6f
private const val CARD_LIFT = 0.04f

/** Takes one photo; null if the camera failed. The caller owns (and must close) the image. */
/** Takes one photo. [onStarted] runs at the moment of capture, before the photo is processed. */
private suspend fun takePhoto(
    controller: LifecycleCameraController,
    executor: java.util.concurrent.Executor,
    onStarted: () -> Unit = {},
): ImageProxy? =
    suspendCancellableCoroutine { cont ->
        controller.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureStarted() = onStarted()
            override fun onCaptureSuccess(image: ImageProxy) = cont.resume(image) { image.close() }
            override fun onError(exception: ImageCaptureException) = cont.resume(null) {}
        })
    }

/** True while the phone is held still (low gyroscope rotation); always true without a gyroscope. */
@Composable
private fun rememberSteady(active: Boolean): State<Boolean> {
    val context = LocalContext.current
    val steady = remember { mutableStateOf(true) }
    DisposableEffect(active) {
        val manager = context.getSystemService(SensorManager::class.java)
        val gyroscope = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (!active || gyroscope == null) {
            steady.value = true
            return@DisposableEffect onDispose {}
        }
        var smoothed = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rotation = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2])
                smoothed = 0.8f * smoothed + 0.2f * rotation
                // Hysteresis so the tip doesn't flicker: calm below 0.2 rad/s, moving above 0.3.
                steady.value = if (steady.value) smoothed < 0.3f else smoothed < 0.2f
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return steady
}
private val EMPTY_FRAME = LiveFrame("", emptyList(), 255)

/**
 * Wraps MlKitAnalyzer to skip frames (see [analysisIntervalMs]) and to measure frame brightness
 * before a frame is consumed.
 */
private class ThrottledAnalyzer(
    private val delegate: MlKitAnalyzer,
    private val intervalMs: AtomicLong,
    private val onStats: (brightness: Int, glare: Float) -> Unit,
) : ImageAnalysis.Analyzer {
    private var lastRun = 0L

    override fun getTargetCoordinateSystem() = delegate.targetCoordinateSystem
    override fun updateTransform(matrix: Matrix?) = delegate.updateTransform(matrix)
    override fun getDefaultTargetResolution() = delegate.defaultTargetResolution

    override fun analyze(image: ImageProxy) {
        val interval = intervalMs.get()
        val now = SystemClock.elapsedRealtime()
        if (interval == ANALYSIS_PAUSED || now - lastRun < interval) {
            image.close()
            return
        }
        lastRun = now
        val plane = image.planes[0]
        val buffer = plane.buffer
        var sum = 0L
        var count = 0
        var glaring = 0
        for (y in 0 until image.height step 16) {
            for (x in 0 until image.width step 16) {
                val luma = buffer.get(y * plane.rowStride + x * plane.pixelStride).toInt() and 0xFF
                sum += luma
                if (luma >= 250) glaring++
                count++
            }
        }
        onStats(if (count == 0) 255 else (sum / count).toInt(), if (count == 0) 0f else glaring.toFloat() / count)
        delegate.analyze(image)
    }
}

private val GoodGreen = Color(0xFF2E7D32)
private val ScanGreen = Color(0xFF69F0AE)
private val ScanAmber = Color(0xFFFFCA28)

private fun Tip.icon(): ImageVector = when (this) {
    Tip.NoText -> Icons.Outlined.DocumentScanner
    Tip.Dark -> Icons.Outlined.LightMode
    Tip.TooSmall -> Icons.Outlined.ZoomIn
    Tip.CutOff -> Icons.Outlined.CropFree
    Tip.Unclear -> Icons.Outlined.BlurOn
    Tip.Shaky -> Icons.Outlined.BlurOn
    Tip.Glare -> Icons.Outlined.LightMode
    Tip.Good -> Icons.Filled.CheckCircle
}

private fun hasCameraPermission(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
fun CameraScreen(viewModel: TourViewModel, visible: Boolean, flight: CaptureFlight, onOpenSettings: () -> Unit, onDropped: () -> Unit) {
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(hasCameraPermission(context)) }
    // Re-check on resume so granting the permission dialog takes effect immediately.
    LifecycleResumeEffect(Unit) {
        hasCamera = hasCameraPermission(context)
        onPauseOrDispose {}
    }
    if (hasCamera) CameraContent(viewModel, visible, flight, onOpenSettings, onDropped) else CameraPermissionNeeded()
}

@Composable
private fun CameraPermissionNeeded() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(16.dp))
        Text("Camera access needed", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Guided Tour reads the plaques and labels you point at.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Button(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        }) { Text("Open settings") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CameraContent(viewModel: TourViewModel, visible: Boolean, flight: CaptureFlight, onOpenSettings: () -> Unit, onDropped: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val script by viewModel.settings.script.collectAsStateWithLifecycle()
    val careful by viewModel.settings.carefulCapture.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var frame by remember { mutableStateOf(EMPTY_FRAME) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val steady by rememberSteady(active = visible)
    val tip = evaluate(frame, viewSize.width, viewSize.height, steady)
    var autoCapture by remember { mutableStateOf(true) }
    var armed by remember { mutableStateOf(true) }
    val countdown = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    // While the photo is read, the frame that was photographed shrinks to the middle of the screen.
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var photosTaken by remember { mutableStateOf(false) }
    val shrink = remember { Animatable(0f) }
    val drop = remember { Animatable(0f) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val snackbar = remember { SnackbarHostState() }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysisInterval = remember { AtomicLong(0L) }
    SideEffect {
        analysisInterval.set(
            analysisIntervalMs(
                processing = state is ScanState.Processing,
                showingResult = state is ScanState.Done,
                textInView = frame.lines.isNotEmpty(),
            )
        )
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            isTapToFocusEnabled = true
            isPinchToZoomEnabled = true
        }
    }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }
    // Live analysis only runs while this tab is showing, so it can't capture from behind another tab.
    DisposableEffect(controller, visible, script) {
        if (!visible) {
            frame = EMPTY_FRAME
            return@DisposableEffect onDispose {}
        }
        val recognizer = Recognizer.client(script)
        var brightness = 255
        var glare = 0f
        val mlKit = MlKitAnalyzer(
            listOf(recognizer),
            ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
            ContextCompat.getMainExecutor(context),
        ) { result ->
            val text = result.getValue(recognizer)
            frame = if (text == null) {
                LiveFrame("", emptyList(), brightness, glare)
            } else {
                val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                    line.boundingBox?.let { LineBox(it.left, it.top, it.right, it.bottom, line.confidence) }
                }
                LiveFrame(cleanOcr(text.text), lines, brightness, glare)
            }
        }
        controller.setImageAnalysisAnalyzer(analysisExecutor, ThrottledAnalyzer(mlKit, analysisInterval) { b, g -> brightness = b; glare = g })
        onDispose { controller.clearImageAnalysisAnalyzer() }
    }
    // Stay bound for a quick return, but release the camera if the tab is left for a while.
    LaunchedEffect(visible) {
        if (visible) {
            controller.bindToLifecycle(lifecycleOwner)
        } else {
            delay(IDLE_RELEASE_MS)
            controller.unbind()
        }
    }

    fun capture() {
        if (!visible || !viewModel.beginCapture()) return
        armed = false
        val liveText = frame.text
        val photos = if (careful) CAREFUL_PHOTOS else 1
        // The frame on screen at the tap shrinks into a card straight away, while the photos are
        // taken in the background; "Hold still…" stays up until the last one is in.
        photosTaken = false
        snapshot = previewView?.bitmap?.asImageBitmap()
        scope.launch { flash.snapTo(0.85f); flash.animateTo(0f, tween(FLASH_MS)) }
        scope.launch { shrink.snapTo(0f); shrink.animateTo(1f, tween(SHRINK_MS, easing = FastOutSlowInEasing)) }
        val tapped = SystemClock.elapsedRealtime()
        Log.i(TAG, "capture: tap, $photos photo(s)")
        scope.launch {
            // Take a few photos in a row; the view model keeps the sharpest and reads it.
            repeat(photos) { i ->
                val image = takePhoto(controller, ContextCompat.getMainExecutor(context))
                Log.i(TAG, "capture: photo ${i + 1} ${if (image != null) "taken" else "failed"} at ${SystemClock.elapsedRealtime() - tapped} ms")
                if (image != null) viewModel.addFrame(image)
            }
            viewModel.finishCapture(liveText)
            // Keep "Hold still…" up for a moment even when the photos were quick, so it can be read.
            delay((MIN_HOLD_STILL_MS - (SystemClock.elapsedRealtime() - tapped)).coerceAtLeast(0))
            photosTaken = true
        }
    }

    // Re-arm once the text has left the frame, so a finished scan isn't repeated.
    LaunchedEffect(tip.ok) { if (!tip.ok) armed = true }
    // Auto-capture once the text has looked readable for a few seconds; the shutter ring shows progress.
    val autoActive = visible && tip.ok && autoCapture && armed && (state is ScanState.Idle || state is ScanState.NoText)
    LaunchedEffect(autoActive) {
        countdown.snapTo(0f)
        if (autoActive) {
            countdown.animateTo(1f, tween(AUTO_CAPTURE_MS.toInt(), easing = LinearEasing))
            capture()
        }
    }
    LaunchedEffect(Unit) { viewModel.shutter.collect { capture() } }
    LaunchedEffect(state) {
        // Text found: the photo card drops into the Guides tab, which then opens.
        val photo = snapshot
        if (state is ScanState.Done && visible && photo != null) {
            if (shrink.value < 1f) shrink.animateTo(1f, tween(SHRINK_MS / 2))
            // Hand the card over to the app-wide overlay, which flies it into the Guides tab.
            val centre = Offset(bounds.center.x, bounds.center.y - bounds.height * CARD_LIFT)
            val half = Offset(bounds.width, bounds.height) * (CARD_SCALE / 2)
            val card = Rect(centre - half, centre + half)
            drop.snapTo(1f)
            flight.fly(photo, card)
            onDropped()
        } else if (state is ScanState.Done) {
            onDropped()
        }
        if (state !is ScanState.Processing) {
            snapshot = null
            photosTaken = false
            shrink.snapTo(0f)
            drop.snapTo(0f)
        }
        if (state is ScanState.NoText) {
            snackbar.showSnackbar("No text found in the photo. Try again.")
            viewModel.dismissResult()
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { viewSize = it }.onGloballyPositioned { bounds = it.boundsInRoot() }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also {
                    previewView = it
                    it.controller = controller
                    controller.bindToLifecycle(lifecycleOwner)
                }
            },
        )

        // While a capture is processed, the photographed frame shrinks into a card over the dimmed
        // camera; the indications below are drawn on top of it.
        val processing = state == ScanState.Processing
        if (processing || (state is ScanState.Done && snapshot != null)) {
            CaptureOverlay(
                snapshot = snapshot,
                progress = shrink.value,
                handedOver = drop.value > 0f,
                scrim = 1f - flight.progress.value,
            )
        }

        // Live outline of every detected line: green when readable, amber otherwise. Hidden while
        // processing, as it would no longer match the shrinking photo.
        val outline = if (tip.ok) ScanGreen else ScanAmber
        if (!processing) Canvas(Modifier.fillMaxSize()) {
            val pad = 4.dp.toPx()
            frame.lines.forEach {
                val topLeft = Offset(it.left - pad, it.top - pad)
                val size = Size(it.width + 2 * pad, it.height + 2 * pad)
                drawRoundRect(outline.copy(alpha = 0.18f), topLeft, size, CornerRadius(8.dp.toPx()))
                drawRoundRect(outline, topLeft, size, CornerRadius(8.dp.toPx()), Stroke(width = 2.dp.toPx()))
            }
        }

        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                processing && !photosTaken -> StatusPill(Icons.Outlined.PanTool, "Hold still…", highlight = true)
                processing -> StatusPill(Icons.Outlined.DocumentScanner, "Reading the text…")
                else -> TipPill(tip = tip, message = if (autoActive) "Hold steady – capturing…" else tip.message)
            }
            FilterChip(
                selected = autoCapture,
                onClick = { autoCapture = !autoCapture },
                label = { Text("Auto capture") },
                modifier = Modifier.semantics { stateDescription = if (autoCapture) "On" else "Off" },
                leadingIcon = { Icon(Icons.Filled.AutoMode, null, Modifier.size(FilterChipDefaults.IconSize)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    labelColor = Color.White,
                    iconColor = Color.White,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                border = null,
            )
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
        ) { Icon(Icons.Filled.Settings, "Settings") }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))
        if (flash.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)) {
            if (state == ScanState.Processing) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(92.dp)) {
                    Box(contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
            } else {
                Shutter(progress = countdown.value, onClick = ::capture)
            }
        }
    }
}

/**
 * Shown while a capture is processed: the photographed frame shrinking into a card in the middle of
 * a dimmed screen ([progress] 0 to 1). Once [handedOver], the card is flying into the Guides tab
 * (see [CaptureFlight]) and only the dimmed background remains, fading out with [scrim].
 */
@Composable
private fun CaptureOverlay(snapshot: ImageBitmap?, progress: Float, handedOver: Boolean, scrim: Float) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f * progress * scrim))) {
        if (snapshot != null && !handedOver) {
            val corner = (28 * progress).dp
            Image(
                snapshot,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1f - (1f - CARD_SCALE) * progress
                        scaleX = scale
                        scaleY = scale
                        translationY = -size.height * CARD_LIFT * progress
                        shape = RoundedCornerShape(corner)
                        clip = true
                        shadowElevation = 24.dp.toPx() * progress
                    },
            )
        }
    }
}

@Composable
private fun TipPill(tip: Tip, message: String) = StatusPill(tip.icon(), message, highlight = tip.ok)

/** The pill at the top of the camera: a photo tip, or what a capture is doing. */
@Composable
private fun StatusPill(icon: ImageVector, message: String, highlight: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (highlight) GoodGreen else Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Shutter with a ring that fills while the automatic capture countdown runs. */
@Composable
private fun Shutter(progress: Float, onClick: () -> Unit) {
    Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = ScanGreen,
            trackColor = Color.White.copy(alpha = 0.55f),
            strokeWidth = 5.dp,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
        Surface(onClick = onClick, shape = CircleShape, color = Color.White, modifier = Modifier.size(70.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CameraAlt, "Take photo and read text", tint = Color(0xFF202020), modifier = Modifier.size(30.dp))
            }
        }
    }
}
