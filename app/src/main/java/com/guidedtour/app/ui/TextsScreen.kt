package com.guidedtour.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.ui.res.painterResource
import com.guidedtour.app.R
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guidedtour.app.ScanState
import com.guidedtour.app.TourViewModel
import com.guidedtour.app.data.PlayerState
import com.guidedtour.app.data.Scan
import com.guidedtour.app.data.Translator
import com.guidedtour.app.data.Visit
import com.guidedtour.app.data.groupVisits
import com.guidedtour.app.data.paragraphsOf
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextsScreen(viewModel: TourViewModel, bottomInset: Dp = 0.dp, onOpenSettings: () -> Unit) {
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val translating by viewModel.translating.collectAsStateWithLifecycle()
    val translateTo by viewModel.settings.translateTo.collectAsStateWithLifecycle()
    val target = Translator.target(translateTo)
    val scanState by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var highlightId by remember { mutableStateOf<Long?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var onlyFavorites by remember { mutableStateOf(false) }

    val visible = remember(scans, query, onlyFavorites) {
        scans.filter { scan ->
            (!onlyFavorites || scan.favorite) &&
                (query.isBlank() || listOf(scan.text, scan.translation, scan.placeName).any { it?.contains(query, ignoreCase = true) == true })
        }
    }
    val visits = remember(visible) { groupVisits(visible) }
    val processing = scanState is ScanState.Processing

    fun delete(scan: Scan) {
        viewModel.delete(scan)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Text deleted", actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(scan)
        }
    }

    LaunchedEffect(processing) { if (processing) listState.animateScrollToItem(0) }
    // A new scan was just read: show it at the top of the list, opened and highlighted.
    LaunchedEffect(scanState, visits) {
        val done = scanState as? ScanState.Done ?: return@LaunchedEffect
        query = ""
        onlyFavorites = false
        val index = listIndexOf(visits, done.scan.id) ?: return@LaunchedEffect
        highlightId = done.scan.id
        viewModel.dismissResult()
        listState.animateScrollToItem(index)
    }
    LaunchedEffect(highlightId) {
        if (highlightId != null) {
            delay(HIGHLIGHT_MS)
            highlightId = null
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text("Guides", style = MaterialTheme.typography.displaySmall) },
                actions = {
                    IconButton(onClick = { onlyFavorites = !onlyFavorites }) {
                        Icon(
                            if (onlyFavorites) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            if (onlyFavorites) "Show all texts" else "Show favourites only",
                            tint = if (onlyFavorites) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomInset)) },
    ) { padding ->
        if (scans.isEmpty() && !processing) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    // Room for the floating player, so the last card can scroll clear of it.
                    start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp + bottomInset,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "search") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search texts and places") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Clear search") }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                        )
                    }
                }
                if (processing) {
                    item(key = "processing") { ReadingCard(Modifier.animateItem()) }
                }
                if (visible.isEmpty() && !processing) {
                    item(key = "none") {
                        Text("No texts match.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                visits.forEach { visit ->
                    item(key = "visit-${visit.id}") { VisitHeader(visit, onPlay = { viewModel.playAll(visit.chronological) }) }
                    items(visit.scans, key = { it.id }) { scan ->
                        ScanCard(
                            scan, player, scan.id in translating, target, viewModel,
                            highlighted = scan.id == highlightId,
                            onDelete = { delete(scan) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitHeader(visit: Visit, onPlay: () -> Unit) {
    val day = visit.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val title = visit.place?.let { "$it · $day" } ?: day
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(
                if (visit.scans.size == 1) "1 text" else "${visit.scans.size} texts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (visit.scans.size > 1) {
            TextButton(onClick = onPlay) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Play visit")
            }
        }
    }
}

@Composable
private fun ScanCard(
    scan: Scan,
    player: PlayerState,
    translating: Boolean,
    target: String,
    viewModel: TourViewModel,
    highlighted: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(highlighted) { if (highlighted) expanded = true }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var confirmCloud by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf(false) }
    if (viewing && scan.thumbnail != null) PhotoViewer(scan.thumbnail, onDismiss = { viewing = false })
    val current = player.current?.takeIf { it.id == scan.id }
    val active = current != null
    val playing = active && player.playing
    val queued = player.queue.withIndex().any { (i, it) -> it.id == scan.id && i > player.index }

    val container by animateColorAsState(
        when {
            highlighted -> MaterialTheme.colorScheme.tertiaryContainer
            active -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "card color",
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = modifier.fillMaxWidth().animateContentSize().clickable(onClickLabel = if (expanded) "Collapse" else "Expand") { expanded = !expanded },
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(6.dp))
                Text(
                    scan.placeLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(scan.timeLabel(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FavoriteButton(scan) { viewModel.toggleFavorite(scan) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Play next") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) },
                            onClick = { menu = false; viewModel.playNext(scan) },
                        )
                        DropdownMenuItem(
                            text = { Text("Add to queue") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                            onClick = { menu = false; viewModel.addToQueue(scan) },
                        )
                        DropdownMenuItem(
                            text = { Text("Fix text") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menu = false; editing = true },
                        )
                        if (paragraphsOf(scan.text).size > 1) {
                            DropdownMenuItem(
                                text = { Text("Choose paragraphs") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, null) },
                                onClick = { menu = false; choosing = true },
                            )
                        }
                        if (viewModel.cloudAvailable && scan.thumbnail != null) {
                            DropdownMenuItem(
                                text = { Text("Improve with cloud") },
                                leadingIcon = { Icon(Icons.Filled.Translate, null) },
                                onClick = { menu = false; confirmCloud = true },
                            )
                        }
                        if (Translator.needsTranslation(scan.languageTag, target)) {
                            DropdownMenuItem(
                                text = { Text(if (scan.translationTag == target) "Translate again" else "Translate into ${Translator.nameOf(target)}") },
                                leadingIcon = { Icon(Icons.Filled.Translate, null) },
                                enabled = !translating,
                                onClick = { menu = false; viewModel.translate(scan) },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(end = 8.dp)) {
                Thumbnail(
                    scan.thumbnail,
                    Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "View photo") { viewing = true },
                    maxSide = 256,
                )
                HighlightedText(
                    scan.text,
                    if (current?.translated == false) player.highlight else null,
                    MaterialTheme.typography.bodyLarge,
                    Modifier.weight(1f),
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                )
            }
            if (scan.translation != null) {
                Column(Modifier.padding(end = 8.dp)) {
                    Text(
                        "Translation" + (scan.translationTag?.let { " · ${Translator.nameOf(it)}" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    HighlightedText(
                        scan.translation,
                        if (current?.translated == true) player.highlight else null,
                        MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    listOfNotNull(scan.languageLabel(), if (queued) "In queue" else null, if (translating) "Translating…" else null).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                ListenButton(scan, player, translating, target, viewModel)
            }
        }
    }
    if (editing) EditTextDialog(scan.text, onDismiss = { editing = false }) { viewModel.editText(scan, it) }
    if (choosing) ChooseParagraphsDialog(scan.text, onDismiss = { choosing = false }) { viewModel.editText(scan, it) }
    if (confirmCloud) CloudConfirmDialog(onDismiss = { confirmCloud = false }) { viewModel.improveWithCloud(scan) }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_tab_guides), null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.size(16.dp))
        Text("Nothing scanned yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Point the camera at a plaque or label and it will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val HIGHLIGHT_MS = 3000L

/** Position of [scanId] in the list: the search field first, then each visit's header and scans. */
private fun listIndexOf(visits: List<Visit>, scanId: Long): Int? {
    var index = 1
    for (visit in visits) {
        index++
        for (scan in visit.scans) {
            if (scan.id == scanId) return index
            index++
        }
    }
    return null
}

/** Stands in for the new scan while its photo is being read. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReadingCard(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LoadingIndicator(Modifier.size(48.dp))
            Column {
                Text("Reading the text…", style = MaterialTheme.typography.titleMedium)
                Text(
                    "It will appear here in a moment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/**
 * Plays or pauses the scan. When it has (or can have) a translation, it is a split button: the
 * arrow picks the original, the translation, or translating into [target]; picking one plays it.
 * With a translation it plays the translation by default.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListenButton(scan: Scan, player: PlayerState, translating: Boolean, target: String, viewModel: TourViewModel) {
    val current = player.current?.takeIf { it.id == scan.id }
    val playing = current != null && player.playing
    val translatable = Translator.needsTranslation(scan.languageTag, target)
    val hasTranslation = scan.translation != null
    var useTranslation by rememberSaveable(scan.id) { mutableStateOf(hasTranslation) }
    LaunchedEffect(scan.translation) { if (scan.translation != null) useTranslation = true }
    var menu by remember { mutableStateOf(false) }

    fun play(translated: Boolean) {
        useTranslation = translated
        if (current != null && current.translated == translated) viewModel.togglePlayback() else viewModel.playNow(scan, translated)
    }
    val label = when {
        playing -> "Pause"
        current != null -> "Resume"
        !hasTranslation && !translatable -> "Listen"
        useTranslation && hasTranslation -> "Listen · ${scan.translationTag?.let(Translator::nameOf) ?: "translation"}"
        else -> "Listen · ${scan.languageLabel() ?: "original"}"
    }
    val content: @Composable RowScope.() -> Unit = {
        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(label)
        if (playing) {
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Icon(Icons.Filled.GraphicEq, null, Modifier.size(ButtonDefaults.IconSize))
        }
    }
    if (!hasTranslation && !translatable) {
        FilledTonalButton(onClick = { play(false) }, content = content)
        return
    }
    Box {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(onClick = {
                    if (current != null) viewModel.togglePlayback() else play(useTranslation && hasTranslation)
                }, content = content)
            },
            trailingButton = {
                SplitButtonDefaults.TonalTrailingButton(
                    checked = menu,
                    onCheckedChange = { menu = it },
                    modifier = Modifier.semantics { contentDescription = "Choose original or translation" },
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(SplitButtonDefaults.TrailingIconSize))
                }
            },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Original" + (scan.languageLabel()?.let { " · $it" } ?: "")) },
                leadingIcon = { if (!useTranslation || !hasTranslation) Icon(Icons.Filled.Check, null) },
                onClick = { menu = false; play(false) },
            )
            if (hasTranslation) {
                DropdownMenuItem(
                    text = { Text("Translation" + (scan.translationTag?.let { " · ${Translator.nameOf(it)}" } ?: "")) },
                    leadingIcon = { if (useTranslation) Icon(Icons.Filled.Check, null) },
                    onClick = { menu = false; play(true) },
                )
            }
            if (translatable && scan.translationTag != target) {
                DropdownMenuItem(
                    text = { Text(if (translating) "Translating…" else "Translate into ${Translator.nameOf(target)}") },
                    leadingIcon = { Icon(Icons.Filled.Translate, null) },
                    enabled = !translating,
                    onClick = { menu = false; useTranslation = true; viewModel.translate(scan) },
                )
            }
        }
    }
}

