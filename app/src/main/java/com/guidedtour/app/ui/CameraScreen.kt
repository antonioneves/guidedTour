package com.guidedtour.app.ui

import android.Manifest
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.FlashlightOff
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.get
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guidedtour.app.ScanState
import com.guidedtour.app.TourViewModel
import com.guidedtour.app.data.FrameTracker
import com.guidedtour.app.data.OcrBlock
import com.guidedtour.app.data.OcrLine
import com.guidedtour.app.data.Recognizer
import com.guidedtour.app.data.ViewArea
import com.guidedtour.app.data.textFrames
import com.guidedtour.app.data.orderBlocks
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.sqrt
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val AUTO_CAPTURE_MS = 2500L
private const val IDLE_RELEASE_MS = 20_000L
private const val CAREFUL_PHOTOS = 3

/** How many of the preview's last readings vote on a photo's words. */
private const val LIVE_READINGS = 6
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

/** How often the preview is read in night mode (see [CameraContent]): enough to follow the text. */
private const val NIGHT_PREVIEW_MS = 350L

/** Average brightness and share of glaring pixels of a preview snapshot, as the frame analysis measures them. */
private fun previewStats(bitmap: android.graphics.Bitmap): Pair<Int, Float> {
    var sum = 0L
    var count = 0
    var glaring = 0
    for (y in 0 until bitmap.height step 16) for (x in 0 until bitmap.width step 16) {
        val p = bitmap[x, y]
        val luma = (((p shr 16) and 0xff) * 77 + ((p shr 8) and 0xff) * 150 + (p and 0xff) * 29) shr 8
        sum += luma
        if (luma >= 250) glaring++
        count++
    }
    return (if (count == 0) 255 else (sum / count).toInt()) to (if (count == 0) 0f else glaring.toFloat() / count)
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
fun CameraScreen(viewModel: TourViewModel, visible: Boolean, uprightTurns: Int, flight: CaptureFlight, onOpenSettings: () -> Unit, onDropped: () -> Unit) {
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(hasCameraPermission(context)) }
    // Re-check on resume so granting the permission dialog takes effect immediately.
    LifecycleResumeEffect(Unit) {
        hasCamera = hasCameraPermission(context)
        onPauseOrDispose {}
    }
    if (hasCamera) CameraContent(viewModel, visible, uprightTurns, flight, onOpenSettings, onDropped) else CameraPermissionNeeded()
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
private fun CameraContent(viewModel: TourViewModel, visible: Boolean, uprightTurns: Int, flight: CaptureFlight, onOpenSettings: () -> Unit, onDropped: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val script by viewModel.settings.script.collectAsStateWithLifecycle()
    val careful by viewModel.settings.carefulCapture.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var frame by remember { mutableStateOf(EMPTY_FRAME) }
    // The text in view as frames: the ones to read and the ones skipped, kept steady between preview frames.
    val tracker = remember { FrameTracker() }
    var frames by remember { mutableStateOf<List<ShownFrame>>(emptyList()) }
    // The preview's last few readings of the framed text, to vote on the photo's words.
    val liveReadings = remember { ArrayDeque<String>() }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val steady by rememberSteady(active = visible)
    val tip = evaluate(frame, viewSize.width, viewSize.height, steady)
    var autoCapture by remember { mutableStateOf(true) }
    var torch by remember { mutableStateOf(false) }
    val hasFlash = remember { hasFlashUnit(context) }
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
    // Focus on the text in view, not wherever autofocus settles; a tap to focus still wins for a while.
    val textFocus = remember { TextFocus(controller) }
    DisposableEffect(controller, lifecycleOwner) {
        val observer = androidx.lifecycle.Observer<Int> { if (it == CameraController.TAP_TO_FOCUS_STARTED) textFocus.userTapped() }
        controller.tapToFocusState.observe(lifecycleOwner, observer)
        onDispose { controller.tapToFocusState.removeObserver(observer) }
    }
    LaunchedEffect(frame, visible, state) {
        if (visible && state != ScanState.Processing) textFocus.follow(previewView, frame.lines)
    }
    // The lenses the phone has (wide, main, zoom, front) and its night mode, as chips over the shutter.
    val choices by produceState<CameraChoices?>(null) { value = cameraChoices(context) }
    var lensKey by rememberSaveable { mutableStateOf<String?>(null) }
    var night by rememberSaveable { mutableStateOf(false) }
    val lens = choices?.let { c -> c.lenses.firstOrNull { key(it) == lensKey } ?: c.main }
    val nightOn = night && lens?.night == true
    // Night mode where the phone can't analyse frames alongside it: the preview is read instead.
    val liveOff = nightOn && lens?.nightLive != true
    var zoom by remember { mutableFloatStateOf(1f) }
    // The camera is being switched (or its zoom set): a veil shows if it takes a moment.
    var switching by remember { mutableStateOf(false) }
    val switchToken = remember { IntArray(1) }
    DisposableEffect(controller, lifecycleOwner) {
        val observer = androidx.lifecycle.Observer<androidx.camera.core.ZoomState> { zoom = it.zoomRatio }
        controller.zoomState.observe(lifecycleOwner, observer)
        onDispose { controller.zoomState.removeObserver(observer) }
    }
    LaunchedEffect(choices, lens, nightOn) {
        val c = choices ?: return@LaunchedEffect
        val chosen = lens ?: return@LaunchedEffect
        val mine = ++switchToken[0]
        switching = true
        try {
            val nightSelector = if (nightOn) c.nightSelector(chosen) else null
            val selector = nightSelector ?: chosen.selector
            val cases = if (liveOff) CameraController.IMAGE_CAPTURE else CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE
            if (liveOff) frame = EMPTY_FRAME
            // Never ask for a combination the camera can't do, even for a moment: into night mode without
            // frame analysis, analysis off first; out of it, the camera first, then analysis back on. (A
            // failed combination leaves the camera closed.)
            val before = controller.cameraInfo
            val changes = controller.cameraSelector != selector
            // Quick taps on the night chip would open and close the camera faster than it can follow.
            delay(120)
            var switched = runCatching {
                if (nightSelector != null) {
                    controller.setEnabledUseCases(cases)
                    controller.cameraSelector = selector
                } else {
                    if (controller.cameraSelector != selector) controller.cameraSelector = selector
                    controller.setEnabledUseCases(cases)
                }
            }.onFailure { Log.w(TAG, "camera: can't switch to ${chosen.label}${if (nightOn) " in night mode" else ""}", it) }.isSuccess
            // The phone can also refuse after the call has returned (the camera then closes with an error):
            // a new camera that isn't open a few seconds later is given up.
            if (switched && changes && before != null) {
                // The new camera's info replaces the old one's once it's bound.
                repeat(40) { if (controller.cameraInfo == null || controller.cameraInfo === before) delay(50) }
                val info = controller.cameraInfo
                // Still the old camera's info: not known to have failed.
                val opened = info == null || info === before || withTimeoutOrNull(6_000) { info.opens() } == true
                if (!opened) Log.w(TAG, "camera: ${chosen.label}${if (nightOn) " in night mode" else ""} didn't open")
                if (!opened) switched = false
            }
            if (!switched) {
                // Back to a combination that works: this lens without night mode, the camera restarted.
                runCatching {
                    controller.unbind()
                    controller.cameraSelector = chosen.selector
                    controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE)
                    controller.bindToLifecycle(lifecycleOwner)
                }.onFailure { Log.w(TAG, "camera: can't restart", it) }
                if (nightOn) {
                    night = false
                    snackbar.currentSnackbarData?.dismiss()
                    launch { snackbar.showSnackbar("Night mode isn't available with this lens") }
                }
            }
            // The zoom applies to the camera once it's open (switching cameras can take a few seconds), and
            // is checked: a camera that was still opening, or a switch cut short by another tap, drops it.
            val facing = if (chosen.front) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            // Given up after a few seconds: a zoom the camera can't reach (night mode's range differs)
            // mustn't leave the veil up.
            withTimeoutOrNull(8_000) {
                while (true) {
                    val info = controller.cameraInfo
                    if (info != null && info.lensFacing == facing && withTimeoutOrNull(3_000) { info.opens() } == true) {
                        controller.setZoomRatio(chosen.zoom)
                        delay(100)
                        val state = controller.zoomState.value
                        if (state != null && abs(state.zoomRatio - chosen.zoom.coerceIn(state.minZoomRatio, state.maxZoomRatio)) < 0.05f) return@withTimeoutOrNull
                    } else {
                        delay(100)
                    }
                }
            }
        } finally {
            if (switchToken[0] == mine) switching = false
        }
    }

    // A photo from the gallery is read like a capture.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            // Nothing to hold still for: straight to "Reading the text…".
            photosTaken = true
            viewModel.importPhoto(uri)
        }
    }
    // What live detection saw, in the preview's coordinates: from camera frames, or in night mode
    // (which can't analyse frames alongside) from snapshots of the preview.
    fun showText(text: Text?, brightness: Int, glare: Float) {
        frame = if (text == null) {
            frames = tracker.update(emptyList()).map(::ShownFrame)
            liveReadings.clear()
            LiveFrame("", emptyList(), brightness, glare)
        } else {
            // The text as frames (columns, headings, notes aside); only the included ones count for
            // tips, focus and capture, as only they will be read.
            val blocks = text.textBlocks.mapNotNull { block ->
                val lines = block.lines.mapNotNull { line ->
                    line.boundingBox?.let {
                        OcrLine(
                            line.text, it.left, it.top, it.right, it.bottom, line.confidence,
                            words = line.elements.map { e -> e.text to e.confidence },
                            wordBoxes = line.elements.map { e -> (e.boundingBox?.left ?: it.left) to (e.boundingBox?.right ?: it.right) },
                        )
                    }
                }
                if (lines.isEmpty()) null else OcrBlock(lines)
            }
            val tracked = tracker.update(textFrames(blocks, viewSize.width, viewSize.height))
            frames = tracked.map(::ShownFrame)
            val included = tracked.filter { it.shown && it.included }
            val main = orderBlocks(included.flatMap { it.frame.blocks })
            val lines = main.flatMap { it.lines }.map { LineBox(it.left, it.top, it.right, it.bottom, it.confidence) }
            val read = main.joinToString("\n\n") { it.text }
            if (read.isNotBlank() && read != liveReadings.lastOrNull()) {
                liveReadings.addLast(read)
                if (liveReadings.size > LIVE_READINGS) liveReadings.removeFirst()
            }
            LiveFrame(read, lines, brightness, glare)
        }
    }

    // Live analysis only runs while this tab is showing, so it can't capture from behind another tab.
    // Night mode without frame analysis: read snapshots of the preview instead, a few times a second.
    LaunchedEffect(liveOff, visible, script) {
        if (!liveOff || !visible) return@LaunchedEffect
        val recognizer = Recognizer.client(script)
        while (true) {
            delay(NIGHT_PREVIEW_MS)
            if (state == ScanState.Processing) continue
            val snapshot = previewView?.bitmap ?: continue
            val (brightness, glare) = withContext(Dispatchers.Default) { previewStats(snapshot) }
            val text = suspendCancellableCoroutine<Text?> { cont ->
                recognizer.process(InputImage.fromBitmap(snapshot, 0))
                    .addOnSuccessListener { cont.resume(it) {} }
                    .addOnFailureListener { cont.resume(null) {} }
            }
            snapshot.recycle()
            showText(text, brightness, glare)
        }
    }
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
        ) { result -> showText(result.getValue(recognizer), brightness, glare) }
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

    // The torch is a steady light rather than a flash, so it lights every photo of the burst and the live text outline.
    LaunchedEffect(torch, visible) { controller.enableTorch(torch && visible) }
    LaunchedEffect(visible) { if (!visible) torch = false }

    fun toggleTorch() {
        torch = !torch
        snackbar.currentSnackbarData?.dismiss()
        if (torch) scope.launch {
            val result = snackbar.showSnackbar(
                "Torch on. Many museums don't allow it, as it can harm artworks. Check before using it.",
                actionLabel = "Turn off",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) torch = false
        }
    }

    fun toggleAutoCapture() {
        autoCapture = !autoCapture
        snackbar.currentSnackbarData?.dismiss()
        scope.launch { snackbar.showSnackbar(if (autoCapture) "Auto capture on" else "Auto capture off") }
    }

    fun capture() {
        if (!visible || !viewModel.beginCapture()) return
        armed = false
        val liveText = frame.text
        val readings = liveReadings.toList()
        val textLines = frame.lines
        fun area(f: ShownFrame) = ViewArea(f.left, f.top, f.right, f.bottom, viewSize.width, viewSize.height)
        val aimed = frames.filter { it.shown && it.included }.map(::area)
        val skipped = frames.filter { it.shown && !it.included }.map(::area)
        // Night mode already merges several frames into each photo, slowly: one is enough.
        val photos = if (careful && !nightOn) CAREFUL_PHOTOS else 1
        // The frame on screen at the tap shrinks into a card straight away, while the photos are
        // taken in the background; "Hold still…" stays up until the last one is in.
        photosTaken = false
        snapshot = previewView?.bitmap?.asImageBitmap()
        scope.launch { flash.snapTo(0.85f); flash.animateTo(0f, tween(FLASH_MS)) }
        scope.launch { shrink.snapTo(0f); shrink.animateTo(1f, tween(SHRINK_MS, easing = FastOutSlowInEasing)) }
        val tapped = SystemClock.elapsedRealtime()
        Log.i(TAG, "capture: tap, $photos photo(s)")
        scope.launch {
            // Make sure the text is in focus first.
            textFocus.focusNow(previewView, textLines)
            // Take a few photos in a row; the view model keeps the sharpest and reads it.
            repeat(photos) { i ->
                val image = takePhoto(controller, ContextCompat.getMainExecutor(context))
                Log.i(TAG, "capture: photo ${i + 1} ${if (image != null) "taken" else "failed"} at ${SystemClock.elapsedRealtime() - tapped} ms")
                if (image != null) viewModel.addFrame(image)
            }
            viewModel.finishCapture(liveText, aimed, skipped, ViewArea(0f, 0f, viewSize.width.toFloat(), viewSize.height.toFloat(), viewSize.width, viewSize.height), readings)
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
                    // A tap on a frame includes or skips it; anywhere else it focuses, as before.
                    val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
                    val pad = 12 * ctx.resources.displayMetrics.density
                    var downX = 0f
                    var downY = 0f
                    var downAt = 0L
                    it.setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; downAt = event.eventTime; false }
                            android.view.MotionEvent.ACTION_UP -> {
                                val tap = event.eventTime - downAt < 300 && abs(event.x - downX) < slop && abs(event.y - downY) < slop
                                if (tap && tracker.toggleAt(event.x, event.y, pad)) {
                                    frames = tracker.frames.map(::ShownFrame)
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                    view.performClick()
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    }
                }
            },
        )

        // A veil over the preview while a camera is switching, once it takes noticeably long.
        var veil by remember { mutableStateOf(false) }
        LaunchedEffect(switching) {
            if (switching) { delay(250); veil = true } else veil = false
        }
        AnimatedVisibility(veil, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                    Text("Switching camera…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

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

        // The text as frames: the ones that will be read in green (amber while not yet readable), with
        // the rest of the view dimmed; skipped ones (a note aside, a neighbouring label) dashed, to
        // tap if they should be read too. Hidden while processing, as they'd no longer match the photo.
        val outline = if (tip.ok) ScanGreen else ScanAmber
        val shown = frames.filter { it.shown }
        val framesAlpha by animateFloatAsState(if (shown.isNotEmpty() && !processing) 1f else 0f, label = "frames")
        if (framesAlpha > 0f) Canvas(Modifier.fillMaxSize()) {
            val pad = 12.dp.toPx()
            val corner = CornerRadius(16.dp.toPx())
            fun box(f: ShownFrame) = Rect(f.left - pad, f.top - pad, f.right + pad, f.bottom + pad)
            val read = shown.filter { it.included }
            val cutOut = Path().apply { read.forEach { addRoundRect(androidx.compose.ui.geometry.RoundRect(box(it), corner)) } }
            clipPath(cutOut, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.35f * framesAlpha)) }
            read.forEach {
                val b = box(it)
                drawRoundRect(outline.copy(alpha = 0.12f * framesAlpha), b.topLeft, b.size, corner)
                drawRoundRect(outline.copy(alpha = framesAlpha), b.topLeft, b.size, corner, Stroke(width = 3.dp.toPx()))
            }
            val dashes = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
            shown.filter { !it.included }.forEach {
                val b = box(it)
                drawRoundRect(Color.White.copy(alpha = 0.7f * framesAlpha), b.topLeft, b.size, corner, Stroke(width = 1.5.dp.toPx(), pathEffect = dashes))
            }
        }

        // The tips sit at whichever edge is "up" for the person holding the phone, turned to read upright.
        val iconAngle = animatedUprightAngle(uprightTurns)
        val tipsAlignment = when (uprightTurns) {
            // Turned left, the screen's right edge points up; turned right, its left edge does.
            1 -> Alignment.CenterEnd
            -1 -> Alignment.CenterStart
            else -> Alignment.TopCenter
        }
        Column(
            Modifier.align(tipsAlignment).then(if (uprightTurns == 0) Modifier.statusBarsPadding() else Modifier).rotateLayout(uprightTurns).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                processing && !photosTaken -> StatusPill(Icons.Outlined.PanTool, "Hold still…", highlight = true)
                processing -> StatusPill(Icons.Outlined.DocumentScanner, "Reading the text…")
                else -> TipPill(tip = tip, message = if (autoActive) "Hold steady – capturing…" else tip.message)
            }
        }

        // Above the lens bar, when there is one.
        val lensBar = choices?.let { it.lenses.size > 1 || it.lenses.any { l -> l.night } } == true
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (lensBar) 200.dp else 140.dp)) { data ->
            // A pill like the one at the top, with any action ("Turn off") inside it.
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Row(
                    Modifier.padding(start = 20.dp, end = if (data.visuals.actionLabel != null) 6.dp else 20.dp, top = 4.dp, bottom = 4.dp)
                        .heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        data.visuals.message,
                        Modifier.weight(1f, fill = false).padding(vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                    data.visuals.actionLabel?.let { label ->
                        TextButton(onClick = data::performAction, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)) {
                            Text(label)
                        }
                    }
                }
            }
        }
        if (flash.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))

        choices?.takeIf { lensBar }?.let { c ->
            LensBar(
                lenses = c.lenses,
                selected = lens,
                zoom = zoom,
                switching = switching,
                night = nightOn,
                onSelect = { lensKey = key(it) },
                onNight = {
                    night = !night
                    snackbar.currentSnackbarData?.dismiss()
                    scope.launch {
                        snackbar.showSnackbar(
                            when {
                                !night -> "Night mode off"
                                else -> "Night mode on: brighter, slower photos"
                            }
                        )
                    }
                },
                iconAngle = iconAngle,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp + 92.dp + 16.dp),
            )
        }

        // The torch on the left of the shutter, auto capture on the right; the gallery and settings in the corners.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 28.dp)) {
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasFlash) {
                    CameraToggle(
                        checked = torch,
                        onToggle = ::toggleTorch,
                        icon = if (torch) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                        label = "Torch",
                        iconAngle = iconAngle,
                    )
                } else {
                    // Keeps the shutter centred.
                    Spacer(Modifier.size(CAMERA_TOGGLE_SIZE))
                }
                if (state == ScanState.Processing) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(92.dp)) {
                        Box(contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                } else {
                    Shutter(progress = countdown.value, showRing = autoCapture, onClick = ::capture)
                }
                CameraToggle(
                    checked = autoCapture,
                    onToggle = ::toggleAutoCapture,
                    icon = Icons.Filled.AutoMode,
                    label = "Auto capture",
                    iconAngle = iconAngle,
                )
            }
            IconButton(
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = state != ScanState.Processing,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                    disabledContainerColor = Color.Black.copy(alpha = 0.3f),
                    disabledContentColor = Color.White.copy(alpha = 0.5f),
                ),
            ) { Icon(Icons.Filled.PhotoLibrary, "Read a photo from the gallery", Modifier.rotate(iconAngle)) }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
            ) { Icon(Icons.Filled.Settings, "Settings", Modifier.rotate(iconAngle)) }
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
            Text(message, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

private val CAMERA_TOGGLE_SIZE = 52.dp

/** A round on/off button beside the shutter: filled when on, see-through when off. */
@Composable
private fun CameraToggle(checked: Boolean, onToggle: () -> Unit, icon: ImageVector, label: String, iconAngle: Float) {
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = { onToggle() },
        modifier = Modifier.size(CAMERA_TOGGLE_SIZE),
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) { Icon(icon, label, Modifier.rotate(iconAngle)) }
}

/** A tracked frame as drawn: a snapshot, so Compose sees each change. */
private class ShownFrame(t: FrameTracker.Tracked) {
    val left = t.left
    val top = t.top
    val right = t.right
    val bottom = t.bottom
    val included = t.included
    val shown = t.shown
}

/** What identifies a lens across recompositions and restarts. */
private fun key(lens: Lens) = (if (lens.front) "front " else "back ") + lens.label

/**
 * The lenses as chips, like a camera app's: the chosen one in white, showing the actual zoom when
 * pinched to another; the front camera as an icon; and night mode, where the chosen lens has it.
 */
@Composable
private fun LensBar(
    lenses: List<Lens>,
    selected: Lens?,
    zoom: Float,
    switching: Boolean,
    night: Boolean,
    onSelect: (Lens) -> Unit,
    onNight: () -> Unit,
    iconAngle: Float,
    modifier: Modifier = Modifier,
) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f), contentColor = Color.White, modifier = modifier) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected?.night == true) {
                LensChip(selected = night, onClick = onNight, description = if (night) "Night mode on" else "Night mode off") {
                    Icon(Icons.Filled.Bedtime, null, Modifier.size(18.dp).rotate(iconAngle))
                }
            }
            lenses.forEach { lens ->
                val chosen = lens == selected
                LensChip(
                    selected = chosen,
                    onClick = { onSelect(lens) },
                    description = if (lens.front) "Front camera" else "Lens ${lens.label}",
                ) {
                    if (lens.front) {
                        Icon(Icons.Filled.Cameraswitch, null, Modifier.size(18.dp).rotate(iconAngle))
                    } else {
                        // Pinched away from the chip's zoom: show where it is now (not while switching: the new camera starts at 1×).
                        val shown = if (chosen && !switching && abs(zoom - lens.zoom) > 0.05f) String.format(Locale.US, "%.1f×", zoom) else lens.label
                        Text(shown, style = MaterialTheme.typography.labelMedium, modifier = Modifier.rotate(iconAngle))
                    }
                }
            }
        }
    }
}

@Composable
private fun LensChip(selected: Boolean, onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Color.White else Color.Transparent,
        contentColor = if (selected) Color.Black else Color.White,
        modifier = Modifier.size(44.dp).semantics {
            contentDescription = description
            this.selected = selected
        },
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

/** Whether the back camera has a flash that can stay on as a torch. */
private fun hasFlashUnit(context: android.content.Context): Boolean = runCatching {
    val manager = context.getSystemService(CameraManager::class.java)
    manager.cameraIdList.any { id ->
        val info = manager.getCameraCharacteristics(id)
        info.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
            info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }
}.getOrDefault(false)

/**
 * Shutter with a ring that fills while the automatic capture countdown runs. Without auto capture
 * there's no countdown, so the ring fades away; the shutter keeps its size so nothing around it moves.
 */
@Composable
private fun Shutter(progress: Float, showRing: Boolean, onClick: () -> Unit) {
    val ringAlpha by animateFloatAsState(if (showRing) 1f else 0f, label = "ring")
    Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = ringAlpha },
            color = ScanGreen,
            trackColor = Color.White.copy(alpha = 0.55f),
            strokeWidth = 5.dp,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
        // A plain white button, like a camera app's shutter.
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.White,
            modifier = Modifier.size(70.dp).semantics { contentDescription = "Take photo and read text" },
        ) {}
    }
}

/** Waits until this camera is open (true) or has closed with an error (false): its state only updates while observed. */
private suspend fun androidx.camera.core.CameraInfo.opens(): Boolean = suspendCancellableCoroutine { cont ->
    val observer = object : androidx.lifecycle.Observer<androidx.camera.core.CameraState> {
        override fun onChanged(value: androidx.camera.core.CameraState) {
            val done = when {
                value.type == androidx.camera.core.CameraState.Type.OPEN -> true
                value.error != null -> false
                else -> return
            }
            cameraState.removeObserver(this)
            if (cont.isActive) cont.resume(done)
        }
    }
    cameraState.observeForever(observer)
    cont.invokeOnCancellation { cameraState.removeObserver(observer) }
}
