package com.guidedtour.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.net.Uri
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import com.guidedtour.app.data.PlayerState
import com.guidedtour.app.data.Scan
import com.guidedtour.app.data.Translator
import java.util.concurrent.Executors

private const val AUTO_CAPTURE_MS = 2500L
private const val IDLE_RELEASE_MS = 20_000L
private val EMPTY_FRAME = LiveFrame("", emptyList(), 255)

/** Wraps MlKitAnalyzer so we can also measure frame brightness before it is consumed. */
private class BrightnessAwareAnalyzer(
    private val delegate: MlKitAnalyzer,
    private val onBrightness: (Int) -> Unit,
) : ImageAnalysis.Analyzer {
    override fun getTargetCoordinateSystem() = delegate.targetCoordinateSystem
    override fun updateTransform(matrix: Matrix?) = delegate.updateTransform(matrix)
    override fun getDefaultTargetResolution() = delegate.defaultTargetResolution

    override fun analyze(image: ImageProxy) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        var sum = 0L
        var count = 0
        for (y in 0 until image.height step 16) {
            for (x in 0 until image.width step 16) {
                sum += buffer.get(y * plane.rowStride + x * plane.pixelStride).toInt() and 0xFF
                count++
            }
        }
        onBrightness(if (count == 0) 255 else (sum / count).toInt())
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
    Tip.Good -> Icons.Filled.CheckCircle
}

private fun hasCameraPermission(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
fun CameraScreen(viewModel: TourViewModel, visible: Boolean, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(hasCameraPermission(context)) }
    // Re-check on resume so granting the permission dialog takes effect immediately.
    LifecycleResumeEffect(Unit) {
        hasCamera = hasCameraPermission(context)
        onPauseOrDispose {}
    }
    if (hasCamera) CameraContent(viewModel, visible, onOpenSettings) else CameraPermissionNeeded()
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
private fun CameraContent(viewModel: TourViewModel, visible: Boolean, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val script by viewModel.settings.script.collectAsStateWithLifecycle()

    var frame by remember { mutableStateOf(EMPTY_FRAME) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val tip = evaluate(frame, viewSize.width, viewSize.height)
    var autoCapture by remember { mutableStateOf(true) }
    var armed by remember { mutableStateOf(true) }
    val countdown = remember { Animatable(0f) }
    val snackbar = remember { SnackbarHostState() }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
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
        val mlKit = MlKitAnalyzer(
            listOf(recognizer),
            ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
            ContextCompat.getMainExecutor(context),
        ) { result ->
            val text = result.getValue(recognizer)
            frame = if (text == null) {
                LiveFrame("", emptyList(), brightness)
            } else {
                val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                    line.boundingBox?.let { LineBox(it.left, it.top, it.right, it.bottom, line.confidence) }
                }
                LiveFrame(cleanOcr(text.text), lines, brightness)
            }
        }
        controller.setImageAnalysisAnalyzer(analysisExecutor, BrightnessAwareAnalyzer(mlKit) { brightness = it })
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
        if (!visible || state == ScanState.Processing) return
        armed = false
        viewModel.dismissResult()
        val liveText = frame.text
        controller.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) = viewModel.onPhotoCaptured(image, liveText)
                override fun onError(exception: ImageCaptureException) = viewModel.onCaptureFailed(liveText)
            },
        )
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
        if (state is ScanState.NoText) {
            snackbar.showSnackbar("No text found in the photo. Try again.")
            viewModel.dismissResult()
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also {
                    it.controller = controller
                    controller.bindToLifecycle(lifecycleOwner)
                }
            },
        )

        // Live outline of every detected line: green when readable, amber otherwise.
        val outline = if (tip.ok) ScanGreen else ScanAmber
        Canvas(Modifier.fillMaxSize()) {
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
            TipPill(
                tip = tip,
                message = if (autoActive) "Hold steady – capturing…" else tip.message,
            )
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

    (state as? ScanState.Done)?.let { done ->
        val scans by viewModel.scans.collectAsStateWithLifecycle()
        ResultSheet(
            scan = scans.firstOrNull { it.id == done.scan.id } ?: done.scan,
            player = player,
            viewModel = viewModel,
            onDismiss = viewModel::dismissResult,
        )
    }
}

@Composable
private fun TipPill(tip: Tip, message: String) {
    Surface(
        shape = CircleShape,
        color = if (tip.ok) GoodGreen else Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(tip.icon(), null, Modifier.size(20.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultSheet(scan: Scan, player: PlayerState, viewModel: TourViewModel, onDismiss: () -> Unit) {
    val translating by viewModel.translating.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val current = player.current?.takeIf { it.id == scan.id && !it.translated }
    val queuedAt = player.queue.indexOfFirst { it.id == scan.id && !it.translated }
    val waiting = queuedAt > player.index
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    scan.whenAndWhere(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                FavoriteButton(scan) { viewModel.toggleFavorite(scan) }
            }
            Thumbnail(scan.thumbnail, Modifier.fillMaxWidth().heightIn(max = 160.dp).clip(RoundedCornerShape(12.dp)))
            HighlightedText(
                scan.text, current?.let { player.highlight }, MaterialTheme.typography.bodyLarge,
                Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
            )
            if (waiting) {
                val ahead = queuedAt - player.index
                Text(
                    if (ahead == 1) "Queued – plays after the current text." else "Queued – plays after $ahead more texts.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (current != null) {
                    Button(onClick = viewModel::togglePlayback) {
                        Icon(if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (player.playing) "Pause" else "Resume")
                    }
                } else {
                    Button(onClick = { viewModel.playNow(scan) }) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (player.active) "Play now" else "Read aloud")
                    }
                    if (player.active && !waiting) {
                        OutlinedButton(onClick = { viewModel.playNext(scan) }) { Text("Play next") }
                    }
                }
                TextButton(onClick = onDismiss) { Text("Keep scanning") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editing = true }) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Fix text")
                }
                if (Translator.needsTranslation(scan.languageTag)) {
                    TextButton(onClick = { viewModel.translate(scan) }, enabled = scan.id !in translating) {
                        Icon(Icons.Filled.Translate, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (scan.id in translating) "Translating…" else "Translate")
                    }
                }
            }
        }
    }
    if (editing) EditTextDialog(scan.text, onDismiss = { editing = false }) { viewModel.editText(scan, it) }
}
