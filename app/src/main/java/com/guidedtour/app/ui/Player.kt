package com.guidedtour.app.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
import com.guidedtour.app.data.QueueItem
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.detectDragGestures
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.guidedtour.app.data.saveToGallery
import com.guidedtour.app.data.savingNeedsPermission
import kotlin.math.abs
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import com.guidedtour.app.SEEK_SECONDS
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.foundation.layout.height
import com.guidedtour.app.BuildConfig
import com.guidedtour.app.data.Translator
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guidedtour.app.TourViewModel
import com.guidedtour.app.data.AiProofreader
import com.guidedtour.app.data.OcrScript
import com.guidedtour.app.data.PlayerState
import com.guidedtour.app.data.paragraphsOf
import com.guidedtour.app.data.pieces
import com.guidedtour.app.data.Scan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** [text] with the word being spoken ([range]) highlighted. */
@Composable
fun HighlightedText(
    text: String,
    range: IntRange?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val highlight = MaterialTheme.colorScheme.tertiaryContainer
    val onHighlight = MaterialTheme.colorScheme.onTertiaryContainer
    val annotated: AnnotatedString = remember(text, range, highlight) {
        if (range == null || range.first < 0 || range.last >= text.length) AnnotatedString(text)
        else buildAnnotatedString {
            append(text.substring(0, range.first))
            withStyle(SpanStyle(background = highlight, color = onHighlight)) { append(text.substring(range.first, range.last + 1)) }
            append(text.substring(range.last + 1))
        }
    }
    Text(annotated, style = style, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Plaque photo loaded from private storage; nothing is shown if it is missing. */
@Composable
fun Thumbnail(path: String?, modifier: Modifier = Modifier, maxSide: Int = 480, contentScale: ContentScale = ContentScale.Crop) {
    if (path == null) return
    val key = "$path@$maxSide"
    val bitmap by produceState<ImageBitmap?>(thumbnailCache.get(key), key) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                // Photos are stored large (for re-reading); decode a smaller copy for the list.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(path, it) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }?.also { thumbnailCache.put(key, it) }
    }
    bitmap?.let {
        Image(it, contentDescription = "Photo of the plaque", contentScale = contentScale, modifier = modifier)
    }
}

/**
 * The saved photo full screen: pinch or double-tap to zoom, drag to move, drag down (unzoomed),
 * back or ✕ to close; the save button copies it into the phone's gallery.
 */
@Composable
fun PhotoViewer(path: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // How far the photo has been dragged down to close it.
    var drop by remember { mutableFloatStateOf(0f) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    fun save() {
        saving = true
        scope.launch {
            saved = saveToGallery(context, path)
            saving = false
            Toast.makeText(context, if (saved) "Saved to Pictures/Guided Tour" else "Couldn't save the photo", Toast.LENGTH_SHORT).show()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else Toast.makeText(context, "Saving needs access to the phone's storage", Toast.LENGTH_SHORT).show()
    }
    // Keep the zoomed photo covering the screen instead of sliding off it.
    fun clamp(o: Offset, s: Float) = Offset(
        o.x.coerceIn(-(s - 1) * size.width / 2, (s - 1) * size.width / 2),
        o.y.coerceIn(-(s - 1) * size.height / 2, (s - 1) * size.height / 2),
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val fade = if (size.height > 0) 1f - (drop / size.height * 2f).coerceIn(0f, 0.8f) else 1f
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = fade))
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    })
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val velocity = VelocityTracker()
                        // Unzoomed, one finger moving mostly down drags the photo away instead.
                        var dropping = false
                        var decided = false
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val fingers = event.changes.count { it.pressed }
                            if (!decided && pan != Offset.Zero) {
                                decided = true
                                dropping = scale == 1f && fingers == 1 && pan.y > abs(pan.x)
                            }
                            if (dropping && fingers == 1) {
                                drop = (drop + pan.y).coerceAtLeast(0f)
                                event.changes.firstOrNull { it.pressed }?.let { velocity.addPosition(it.uptimeMillis, it.position) }
                            } else if (!dropping) {
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset = clamp(offset + pan, scale)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (event.changes.any { it.pressed })
                        if (dropping) {
                            val flung = velocity.calculateVelocity().y > 1_500f
                            if (flung || drop > size.height * 0.2f) onDismiss()
                            else scope.launch { animate(drop, 0f) { value, _ -> drop = value } }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Thumbnail(
                path,
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y + drop
                },
                maxSide = 2048,
                contentScale = ContentScale.Fit,
            )
            val buttons = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                disabledContainerColor = Color.Black.copy(alpha = 0.5f),
                disabledContentColor = Color.White.copy(alpha = 0.7f),
            )
            Row(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp).graphicsLayer { alpha = fade }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = {
                        val granted = !savingNeedsPermission ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        if (granted) save() else permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    },
                    enabled = !saving && !saved,
                    colors = buttons,
                ) { Icon(if (saved) Icons.Filled.Check else Icons.Filled.Download, if (saved) "Saved to the gallery" else "Save to the gallery") }
                IconButton(onClick = onDismiss, colors = buttons) { Icon(Icons.Filled.Close, "Close photo") }
            }
        }
    }
}

/** Decoded thumbnails, so scrolling back through the list doesn't decode them again. */
private val thumbnailCache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

/** Compact player shown above the navigation bar whenever something is queued or playing. */
@Composable
fun NowPlayingBar(player: PlayerState, viewModel: TourViewModel, onOpenQueue: () -> Unit, modifier: Modifier = Modifier) {
    val item = player.current ?: return
    // A floating, rounded player in the inverse colours, so it stands apart from the cards; with the
    // place's photo behind it, darkened so the text and buttons stay readable.
    val colors = MaterialTheme.colorScheme
    val photo = rememberPlacePhoto(viewModel, item.spot)
    val content = if (photo != null) Color.White else colors.inverseOnSurface
    Surface(
        modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = colors.inverseSurface,
        contentColor = content,
        shadowElevation = 6.dp,
    ) {
        Box {
            if (photo != null) {
                Image(photo.image, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)))
            }
            Column(Modifier.clickable(onClickLabel = "Open reading queue", onClick = onOpenQueue)) {
                // How far through the text the reading is (from the word being spoken).
                val progress = player.highlight?.let { (it.last + 1).toFloat() / item.text.length.coerceAtLeast(1) } ?: 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 6.dp).height(3.dp),
                    color = colors.inversePrimary,
                    trackColor = content.copy(alpha = 0.25f),
                    drawStopIndicator = {},
                )
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 4.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val position = if (player.queue.size > 1) "${player.index + 1} of ${player.queue.size} · " else ""
                        Text(
                            position + item.text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = viewModel::stopSpeaking) { Icon(Icons.Filled.Close, "Stop and clear queue") }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = viewModel::previous) { Icon(Icons.Filled.SkipPrevious, "Previous") }
                    IconButton(onClick = viewModel::seekBack) { Icon(Icons.Filled.Replay10, "Back $SEEK_SECONDS seconds") }
                    FilledIconButton(
                        onClick = viewModel::togglePlayback,
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.inversePrimary, contentColor = colors.inverseSurface),
                    ) {
                        Icon(
                            if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (player.playing) "Pause" else "Play",
                            Modifier.size(32.dp),
                        )
                    }
                    IconButton(onClick = viewModel::seekForward) { Icon(Icons.Filled.Forward10, "Forward $SEEK_SECONDS seconds") }
                    IconButton(onClick = viewModel::next) { Icon(Icons.Filled.SkipNext, "Next") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(viewModel: TourViewModel, onDismiss: () -> Unit) {
    val player by viewModel.player.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Reading queue",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() },
        )
        if (!player.active) {
            Text("Nothing queued. Scan another plaque or press Listen on a saved text.", Modifier.padding(24.dp))
            return@ModalBottomSheet
        }
        Text(
            "Drag ≡ to reorder what's next, swipe a card away to remove it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        val listState = rememberLazyListState()
        val queue by rememberUpdatedState(player.queue)
        val index by rememberUpdatedState(player.index)
        // The card being dragged, where it was when picked up, and how far the finger has moved since.
        var dragKey by remember { mutableStateOf<Long?>(null) }
        var dragStart by remember { mutableIntStateOf(0) }
        var dragDistance by remember { mutableFloatStateOf(0f) }
        fun dragBy(delta: Float) {
            dragDistance += delta
            val visible = listState.layoutInfo.visibleItemsInfo
            val dragged = visible.firstOrNull { it.key == dragKey } ?: return
            val centre = dragStart + dragDistance + dragged.size / 2
            val over = visible.firstOrNull { it.key != dragKey && centre.toInt() in it.offset until it.offset + it.size } ?: return
            val from = queue.indexOfFirst { it.key == dragKey }
            val to = queue.indexOfFirst { it.key == over.key }
            // Only what's still to come can be reordered; the current text keeps playing.
            if (from > index && to > index) viewModel.moveInQueue(from, to - from)
        }
        LazyColumn(
            Modifier.fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(player.queue, key = { _, it -> it.key }) { position, item ->
                val dragging = item.key == dragKey
                val upcoming = position > player.index
                QueueCard(
                    item, viewModel,
                    status = when {
                        position == player.index && player.playing -> "Now playing"
                        position == player.index -> "Paused"
                        position < player.index -> "Played"
                        else -> "Up next"
                    },
                    current = position == player.index,
                    dragging = dragging,
                    onRemove = { viewModel.removeFromQueue(position) },
                    onMoveEarlier = if (upcoming && position > player.index + 1) ({ viewModel.moveInQueue(position, -1) }) else null,
                    onMoveLater = if (upcoming && position < player.queue.lastIndex) ({ viewModel.moveInQueue(position, 1) }) else null,
                    handleModifier = if (!upcoming) Modifier else Modifier.pointerInput(item.key) {
                        detectDragGestures(
                            onDragStart = {
                                dragKey = item.key
                                dragStart = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.key }?.offset ?: 0
                                dragDistance = 0f
                            },
                            onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                            onDragEnd = { dragKey = null },
                            onDragCancel = { dragKey = null },
                        )
                    },
                    modifier = if (dragging) {
                        // Follows the finger: where it was picked up, plus the drag, less where the list has put it now.
                        Modifier.zIndex(1f).graphicsLayer {
                            val now = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.key }?.offset ?: dragStart
                            translationY = dragStart + dragDistance - now
                        }
                    } else Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * A text in the reading queue, as a card with its place's photo. Swiping it away removes it; an
 * upcoming one has a handle ([handleModifier]) to drag it to another place in the queue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueCard(
    item: QueueItem,
    viewModel: TourViewModel,
    status: String,
    current: Boolean,
    dragging: Boolean,
    onRemove: () -> Unit,
    onMoveEarlier: (() -> Unit)?,
    onMoveLater: (() -> Unit)?,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    val currentRemove by rememberUpdatedState(onRemove)
    val swipe = rememberSwipeToDismissBoxState()
    val colors = MaterialTheme.colorScheme
    SwipeToDismissBox(
        swipe,
        modifier = modifier,
        gesturesEnabled = !dragging,
        onDismiss = { currentRemove() },
        backgroundContent = {
            // A red strip with a bin, on the side the card is leaving from.
            val alignment = if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
            Box(
                Modifier.fillMaxSize().clip(CardDefaults.shape).background(colors.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = alignment,
            ) {
                Icon(Icons.Filled.Delete, null, tint = colors.onErrorContainer)
            }
        },
    ) {
        val photo = rememberPlacePhoto(viewModel, item.spot)
        TextCard(
            status = status,
            title = (if (item.translated) "Translation · " else "") + item.title,
            text = item.text,
            current = current,
            raised = dragging,
            image = {
                if (photo != null) Image(photo.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            },
            modifier = Modifier.semantics {
                // Swiping isn't available to screen readers, so removing is an action too.
                customActions = listOfNotNull(
                    CustomAccessibilityAction("Remove from queue") { currentRemove(); true },
                    onMoveEarlier?.let { CustomAccessibilityAction("Move earlier") { it(); true } },
                    onMoveLater?.let { CustomAccessibilityAction("Move later") { it(); true } },
                )
            },
        ) {
            if (onMoveEarlier != null || onMoveLater != null) {
                Icon(
                    Icons.Filled.DragHandle, "Drag to reorder",
                    handleModifier.size(48.dp).padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A text as a card, as in the reading queue and a map place's list: a square [image], a [status]
 * line, an optional [title], two or three lines of [text], and [trailing] buttons. [current] (the
 * text being read) is highlighted; [raised] lifts it while it's dragged.
 */
@Composable
fun TextCard(
    status: String,
    title: String?,
    text: String,
    current: Boolean,
    image: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = if (current) colors.primaryContainer else colors.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (raised) 8.dp else 1.dp),
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        Row(
            Modifier.then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(colors.secondaryContainer), contentAlignment = Alignment.Center, content = image)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(status, style = MaterialTheme.typography.labelMedium, color = if (current) colors.onPrimaryContainer else colors.primary)
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = if (title == null) 3 else 2, overflow = TextOverflow.Ellipsis,
                )
            }
            trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(viewModel: TourViewModel, onDismiss: () -> Unit) {
    val settings = viewModel.settings
    val rate by settings.rate.collectAsStateWithLifecycle()
    val pitch by settings.pitch.collectAsStateWithLifecycle()
    val queueNew by settings.queueNewScans.collectAsStateWithLifecycle()
    val volumeShutter by settings.volumeShutter.collectAsStateWithLifecycle()
    val duckMusic by settings.duckMusic.collectAsStateWithLifecycle()
    val careful by settings.carefulCapture.collectAsStateWithLifecycle()
    val placePhotos by settings.placePhotos.collectAsStateWithLifecycle()
    val correctWords by settings.correctWords.collectAsStateWithLifecycle()
    val report by viewModel.accuracyReport.collectAsStateWithLifecycle()
    val script by settings.script.collectAsStateWithLifecycle()
    var showCredits by remember { mutableStateOf(false) }
    if (showCredits) CreditsSheet(onDismiss = { showCredits = false })

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })

            Text("Speech speed: ${"%.1f".format(Locale.US, rate)}×", style = MaterialTheme.typography.titleSmall)
            Slider(rate, { settings.setRate((it * 10).toInt() / 10f) }, valueRange = 0.5f..2f, steps = 14)

            Text("Voice pitch: ${"%.1f".format(Locale.US, pitch)}", style = MaterialTheme.typography.titleSmall)
            Slider(pitch, { settings.setPitch((it * 10).toInt() / 10f) }, valueRange = 0.7f..1.3f, steps = 5)

            VoiceSection(viewModel)

            TranslationSection(viewModel)

            SwitchRow(
                "Queue new scans",
                "Keep reading the current text and add new scans to the end of the queue.",
                queueNew, settings::setQueueNewScans,
            )
            SwitchRow(
                "Only lower other audio",
                "Off: music and other apps pause while reading. On: they keep playing more quietly.",
                duckMusic, settings::setDuckMusic,
            )
            SwitchRow(
                "Volume buttons take the photo",
                "On the Scan tab, press a volume button to capture one-handed.",
                volumeShutter, settings::setVolumeShutter,
            )

            SwitchRow(
                "Photos of places",
                "Shows a Wikipedia photo of each place in Guides and on the map. Asks Wikipedia about each place by its name and area, never your exact position.",
                placePhotos, settings::setPlacePhotos,
            )

            Text("Text language to look for", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OcrScript.entries.forEach {
                    FilterChip(selected = script == it, onClick = { settings.setScript(it) }, label = { Text(it.label) })
                }
            }
            Text(
                "Automatic tries Latin letters first, then Chinese, Japanese, Korean and Devanagari.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AiProofreadingRow(viewModel)
            SwitchRow(
                "Correct misread words",
                "Replaces words the camera misread with the likeliest real word in the text's language, using word lists on the phone.",
                correctWords, settings::setCorrectWords,
            )
            SwitchRow(
                "Careful capture",
                "Takes three photos and reads the sharpest. Slightly slower, usually more accurate.",
                careful, settings::setCarefulCapture,
            )

            DataSection(viewModel)

            Row(
                Modifier.fillMaxWidth().clickable { showCredits = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Credits", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Maps, text recognition, translation, voices and the open-source code the app is built on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Developer tools only appear in debug builds.
            if (BuildConfig.DEBUG) {
                Text("Developer", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Measure reading accuracy on your own photos. Put name.jpg and name.txt (the true text) pairs in:\n" +
                        viewModel.accuracyFolder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.OutlinedButton(onClick = viewModel::runAccuracyTest) { Text("Run accuracy test") }
                report?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
                }
            }
        }
    }
}

/**
 * The guides as a file: export a copy, import one (adding the guides not here yet), and keep a
 * backup file up to date by itself. Any place the phone's file picker offers works, Google Drive
 * included, which makes it a cloud backup.
 */
@Composable
private fun DataSection(viewModel: TourViewModel) {
    val settings = viewModel.settings
    val backupFile by settings.backupFile.collectAsStateWithLifecycle()
    val lastBackup by settings.lastBackup.collectAsStateWithLifecycle()
    val busy by viewModel.backingUp.collectAsStateWithLifecycle()
    val name = "Guided Tour backup ${java.time.LocalDate.now()}.zip"
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(viewModel::exportTo) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importFrom) }
    val keep = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(viewModel::setAutoBackup) }

    Text("Your guides", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
    Text(
        "Keep a copy of every guide and its photo, on the phone or in Google Drive or another cloud. Importing adds the guides that aren't here yet and never removes any.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.OutlinedButton(onClick = { export.launch(name) }, enabled = !busy) {
            Icon(Icons.Filled.Upload, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Export")
        }
        androidx.compose.material3.OutlinedButton(onClick = { import.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy) {
            Icon(Icons.Filled.Download, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Import")
        }
    }
    SwitchRow(
        "Back up automatically",
        when {
            backupFile == null -> "Choose a file, for example in Google Drive, and it's kept up to date after each change."
            busy -> "Backing up…"
            lastBackup == 0L -> "On. The first backup is being written."
            System.currentTimeMillis() - lastBackup < 60_000 -> "On. Last backup just now."
            else -> "On. Last backup " + android.text.format.DateUtils.getRelativeTimeSpanString(lastBackup, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS) + "."
        },
        backupFile != null,
    ) { on -> if (on) keep.launch("Guided Tour backup.zip") else viewModel.setAutoBackup(null) }
}

/** Which language "Translate" uses: the phone's (the default) or one chosen from ML Kit's list. */
@Composable
private fun TranslationSection(viewModel: TourViewModel) {
    val settings = viewModel.settings
    val translateTo by settings.translateTo.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf(false) }
    val phone = Translator.target("")
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Choose language") { choosing = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Translate into", style = MaterialTheme.typography.titleSmall)
            Text(
                if (translateTo.isEmpty()) "Phone language (${Translator.nameOf(phone)})" else Translator.nameOf(translateTo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { choosing = true }) { Text("Change") }
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Translate into") },
            text = {
                LazyColumn {
                    item {
                        LanguageRow("Phone language (${Translator.nameOf(phone)})", translateTo.isEmpty()) {
                            settings.setTranslateTo(""); choosing = false
                        }
                    }
                    items(Translator.languages) { code ->
                        LanguageRow(Translator.nameOf(code), translateTo == code) { settings.setTranslateTo(code); choosing = false }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Close") } },
        )
    }
}

/**
 * Corrects a guide's language: detect it again paragraph by paragraph (the default, which handles
 * bilingual plaques), or set one language for the whole text. [onChoose] gets null for detecting.
 */
@Composable
fun LanguageDialog(scan: Scan, onDismiss: () -> Unit, onChoose: (String?) -> Unit) {
    val current = scan.languageTag?.let { Locale.forLanguageTag(it).language }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Language of this text") },
        text = {
            LazyColumn {
                item {
                    LanguageRow(
                        "Detect automatically" + if (!scan.languageFixed) scan.languageLabel()?.let { " ($it)" }.orEmpty() else "",
                        !scan.languageFixed,
                    ) { onChoose(null); onDismiss() }
                }
                items(Translator.languages) { code ->
                    LanguageRow(Translator.nameOf(code), scan.languageFixed && current == code) { onChoose(code); onDismiss() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun LanguageRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, null)
        Text(name, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * On-device AI proofreading, only where the phone has it: on by default once its model is there;
 * turning it on downloads the model if it isn't yet.
 */
@Composable
private fun AiProofreadingRow(viewModel: TourViewModel) {
    val settings = viewModel.settings
    val on by settings.aiProofreading.collectAsStateWithLifecycle()
    var status by remember { mutableStateOf<AiProofreader.Status?>(null) }
    var downloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { status = viewModel.aiProofreader.status() }
    val current = status ?: return
    if (current == AiProofreader.Status.UNAVAILABLE) return
    val needsDownload = current == AiProofreader.Status.DOWNLOADABLE
    SwitchRow(
        "AI proofreading on the phone",
        when {
            downloading -> "Downloading the proofreading model…"
            needsDownload -> "Mends misread words using Gemini Nano on the phone. Needs a one-time download; nothing is sent anywhere."
            else -> "Mends misread words using Gemini Nano on the phone, only where a word isn't one. Nothing is sent anywhere."
        },
        on && !needsDownload,
    ) { value ->
        settings.setAiProofreading(value)
        if (value && needsDownload && !downloading) {
            downloading = true
            scope.launch {
                viewModel.aiProofreader.download()
                status = viewModel.aiProofreader.status()
                downloading = false
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, null)
    }
}

@Composable
fun FavoriteButton(scan: com.guidedtour.app.data.Scan, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (scan.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            if (scan.favorite) "Remove from favourites" else "Add to favourites",
            tint = if (scan.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Lets the user correct OCR mistakes. Saving also refreshes the detected language. */
@Composable
fun EditTextDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fix recognized text") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 10,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text); onDismiss() }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Lets the user choose which installed voice reads each language, with a spoken sample. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceSection(viewModel: TourViewModel) {
    val ready by viewModel.speechReady.collectAsStateWithLifecycle()
    val chosen by viewModel.settings.voices.collectAsStateWithLifecycle()
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val languages = remember(scans) {
        (listOf(Locale.getDefault().language) + scans.flatMap { it.allLanguages() }.map { Locale.forLanguageTag(it).language })
            .filter { it.isNotBlank() }.distinct()
    }
    var selected by remember { mutableStateOf(languages.first()) }
    DisposableEffect(Unit) { onDispose { viewModel.stopPreview() } }

    Text("Voice", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        languages.forEach { code ->
            FilterChip(
                selected = selected == code,
                onClick = { viewModel.stopPreview(); selected = code },
                label = { Text(Locale.forLanguageTag(code).getDisplayLanguage().replaceFirstChar { it.titlecase() }) },
            )
        }
    }
    val voices = remember(ready, selected) { viewModel.voicesFor(selected) }
    val languageName = Locale.forLanguageTag(selected).displayLanguage
    val sample = remember(scans, selected) {
        // A piece of a guide in this language (only that part of a bilingual one).
        scans.firstNotNullOfOrNull { scan ->
            pieces(scan.text, scan.spans()).firstOrNull { Locale.forLanguageTag(it.first).language == selected }?.second?.trim()
        }?.take(120)?.substringBeforeLast(' ', "")?.takeIf { it.isNotBlank() }
            ?: "Hello, this is how I sound in $languageName."
    }
    when {
        !ready -> Text("Loading voices…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        voices.isEmpty() -> Text(
            "No voices installed for $languageName. Add one in Android Settings, Text-to-speech.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> {
            val options = listOf(VoiceOption(null, "Automatic", "Picked for your region and connection")) +
                voices.mapIndexed { index, voice ->
                    val region = voice.locale.displayCountry
                    VoiceOption(
                        voice.name,
                        "Voice ${index + 1}" + if (region.isNotBlank()) " · $region" else "",
                        listOfNotNull(
                            if (voice.quality >= 400) "High quality" else null,
                            if (voice.isNetworkConnectionRequired) "Needs internet" else null,
                        ).joinToString(", ").ifEmpty { null },
                    )
                }
            VoiceDropdown(
                options,
                current = options.firstOrNull { it.name == chosen[selected] } ?: options.first(),
                onSelect = { viewModel.settings.setVoice(selected, it.name) },
                onPlay = { viewModel.previewVoice(selected, it.name, sample) },
            )
        }
    }
}

/** A voice to choose from; [name] is null for the automatic choice. */
private data class VoiceOption(val name: String?, val title: String, val detail: String?)

/** The chosen voice in a drop-down; every option has its own play button for a spoken sample. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceDropdown(options: List<VoiceOption>, current: VoiceOption, onSelect: (VoiceOption) -> Unit, onPlay: (VoiceOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current.title,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            supportingText = current.detail?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.title, style = MaterialTheme.typography.bodyLarge)
                            if (option.detail != null) {
                                Text(option.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    // Every row keeps the check mark's space, so the names line up.
                    leadingIcon = { Icon(Icons.Filled.Check, null, Modifier.alpha(if (option == current) 1f else 0f)) },
                    // Plays a sample without closing the menu, so voices can be compared before choosing.
                    trailingIcon = { IconButton(onClick = { onPlay(option) }) { Icon(Icons.Filled.PlayArrow, "Play a sample of ${option.title}") } },
                    onClick = { onSelect(option); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** Pick which paragraphs to keep, e.g. to drop a neighbouring sign that was read along with the plaque. */
@Composable
fun ChooseParagraphsDialog(text: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val paragraphs = remember(text) { paragraphsOf(text) }
    val keep = remember(text) { androidx.compose.runtime.mutableStateListOf(*Array(paragraphs.size) { true }) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose what to read") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                paragraphs.forEachIndexed { i, paragraph ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { keep[i] = !keep[i] }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = keep[i], onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                        Text(paragraph, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(paragraphs.filterIndexed { i, _ -> keep[i] }.joinToString("\n\n")); onDismiss() },
                enabled = keep.any { it },
            ) { Text("Keep selected") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The cloud option uploads the photo, so it always asks first. */
@Composable
fun CloudConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send this photo to Google?") },
        text = {
            Text(
                "To read it more accurately, the photo of this plaque is uploaded to Google Cloud Vision. " +
                    "Nothing else is sent, and only for this text."
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Send photo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
