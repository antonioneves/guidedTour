package com.guidedtour.app.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
import com.guidedtour.app.data.OcrScript
import com.guidedtour.app.data.PlayerState
import com.guidedtour.app.data.paragraphsOf
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

/** The saved photo full screen: pinch or double-tap to zoom, drag to move, back or ✕ to close. */
@Composable
fun PhotoViewer(path: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Keep the zoomed photo covering the screen instead of sliding off it.
    fun clamp(o: Offset, s: Float) = Offset(
        o.x.coerceIn(-(s - 1) * size.width / 2, (s - 1) * size.width / 2),
        o.y.coerceIn(-(s - 1) * size.height / 2, (s - 1) * size.height / 2),
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = clamp(offset + pan, scale)
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
                    translationY = offset.y
                },
                maxSide = 2048,
                contentScale = ContentScale.Fit,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
            ) { Icon(Icons.Filled.Close, "Close photo") }
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier.clickable(onClickLabel = "Open reading queue", onClick = onOpenQueue).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val position = if (player.queue.size > 1) "${player.index + 1} of ${player.queue.size} · " else ""
                Text(
                    position + item.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = viewModel::previous) { Icon(Icons.Filled.SkipPrevious, "Previous") }
            FilledIconButton(onClick = viewModel::togglePlayback) {
                Icon(if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (player.playing) "Pause" else "Play")
            }
            IconButton(onClick = viewModel::next) { Icon(Icons.Filled.SkipNext, "Next") }
            IconButton(onClick = viewModel::stopSpeaking) { Icon(Icons.Filled.Close, "Stop and clear queue") }
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
            "New scans join the end of the queue while you keep exploring.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            itemsIndexed(player.queue, key = { _, it -> it.key }) { position, item ->
                val current = position == player.index
                val played = position < player.index
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = if (current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    ),
                    overlineContent = {
                        Text(
                            when {
                                current && player.playing -> "Now playing"
                                current -> "Paused"
                                played -> "Played"
                                else -> "Up next"
                            }
                        )
                    },
                    headlineContent = {
                        Text(
                            (if (item.translated) "Translation · " else "") + item.title,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = { Text(item.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        Row {
                            if (position > player.index) {
                                IconButton(onClick = { viewModel.moveInQueue(position, -1) }, enabled = position > player.index + 1) {
                                    Icon(Icons.Filled.KeyboardArrowUp, "Move earlier")
                                }
                                IconButton(onClick = { viewModel.moveInQueue(position, 1) }, enabled = position < player.queue.lastIndex) {
                                    Icon(Icons.Filled.KeyboardArrowDown, "Move later")
                                }
                            }
                            IconButton(onClick = { viewModel.removeFromQueue(position) }) {
                                Icon(Icons.Filled.Close, "Remove from queue")
                            }
                        }
                    },
                )
            }
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
    val report by viewModel.accuracyReport.collectAsStateWithLifecycle()
    val script by settings.script.collectAsStateWithLifecycle()

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
            SwitchRow(
                "Careful capture",
                "Takes three photos and reads the sharpest. Slightly slower, usually more accurate.",
                careful, settings::setCarefulCapture,
            )

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
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
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
        (listOf(Locale.getDefault().language) + scans.mapNotNull { it.languageTag?.let { tag -> Locale.forLanguageTag(tag).language } })
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
        scans.firstOrNull { it.languageTag?.let { tag -> Locale.forLanguageTag(tag).language } == selected }
            ?.text?.take(120)?.substringBeforeLast(' ', "")?.takeIf { it.isNotBlank() }
            ?: "Hello, this is how I sound in $languageName."
    }
    when {
        !ready -> Text("Loading voices…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        voices.isEmpty() -> Text(
            "No voices installed for $languageName. Add one in Android Settings, Text-to-speech.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Column {
            VoiceRow("Automatic (best installed voice)", null, chosen[selected] == null, onSelect = { viewModel.settings.setVoice(selected, null) },
                onPlay = { viewModel.previewVoice(selected, null, sample) })
            voices.forEachIndexed { index, voice ->
                val region = voice.locale.displayCountry
                val detail = listOfNotNull(
                    if (voice.quality >= 400) "high quality" else null,
                    if (voice.isNetworkConnectionRequired) "needs internet" else null,
                ).joinToString(", ").ifEmpty { null }
                VoiceRow(
                    "Voice ${index + 1}" + if (region.isNotBlank()) " · $region" else "", detail, chosen[selected] == voice.name,
                    onSelect = { viewModel.settings.setVoice(selected, voice.name) },
                    onPlay = { viewModel.previewVoice(selected, voice.name, sample) },
                )
            }
        }
    }
}

@Composable
private fun VoiceRow(title: String, detail: String?, selected: Boolean, onSelect: () -> Unit, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, "Play a sample of $title") }
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
