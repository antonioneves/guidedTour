package com.guidedtour.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.guidedtour.app.R
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.approachLayout
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.runtime.movableContentOf
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.semantics.stateDescription
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
import com.guidedtour.app.data.Place
import com.guidedtour.app.data.Visit
import com.guidedtour.app.data.groupPlaces
import com.guidedtour.app.data.photoSpot
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
    val loaded by viewModel.scansLoaded.collectAsStateWithLifecycle()
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
                (query.isBlank() || listOf(scan.headline, scan.text, scan.translation, scan.placeName).any { it?.contains(query, ignoreCase = true) == true })
        }
    }
    val visits = remember(visible) { groupVisits(visible) }
    // Each scan's place, as on the map, so a visit shows the same photo as its pin.
    val placeOf = remember(scans) { groupPlaces(scans).flatMap { place -> place.scans.map { it.id to place } }.toMap() }
    val processing = scanState is ScanState.Processing
    // Visits shown open (by id): on opening the app only the newest; the others show only their header.
    var opened by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(loaded, visits) {
        if (opened == null && loaded && query.isBlank() && !onlyFavorites) opened = listOfNotNull(visits.firstOrNull()?.id)
    }
    // While searching, every visit with a match is open, so no result is hidden.
    val filtering = query.isNotBlank() || onlyFavorites
    fun isOpen(visit: Visit) = filtering || visit.id in opened.orEmpty()

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
        // Its visit opens, to show it.
        visits.firstOrNull { visit -> visit.scans.any { it.id == done.scan.id } }?.let { visit ->
            if (visit.id !in opened.orEmpty()) opened = opened.orEmpty() + visit.id
        }
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
        // "Nothing scanned yet" only once the guides have been read, never while they load.
        if (scans.isEmpty() && !processing && loaded) {
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
                    val open = isOpen(visit)
                    item(key = "visit-${visit.id}") {
                        VisitHeader(
                            visit, visit.scans.firstNotNullOfOrNull { placeOf[it.id] }, viewModel,
                            open = open,
                            onToggle = if (filtering) null else { { opened = if (open) opened.orEmpty() - visit.id else opened.orEmpty() + visit.id } },
                            onPlay = { viewModel.playAll(visit.chronological) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (open) items(visit.scans, key = { it.id }) { scan ->
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

/**
 * A visit's header: its place (with the place's photo behind, where there is one), day and number
 * of texts. Tapping it opens or closes the visit's cards ([onToggle]; null while searching, when
 * every visit with a match stays [open]).
 */
@Composable
private fun VisitHeader(
    visit: Visit,
    place: Place?,
    viewModel: TourViewModel,
    open: Boolean,
    onToggle: (() -> Unit)?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val day = visit.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val title = visit.place?.let { "$it · $day" } ?: day
    val count = if (visit.scans.size == 1) "1 text" else "${visit.scans.size} texts"
    val photo = rememberPlacePhoto(viewModel, place?.photoSpot())
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "visit arrow")
    val toggle = if (onToggle == null) Modifier else Modifier.clickable(onClickLabel = if (open) "Hide its texts" else "Show its texts", onClick = onToggle)
    val state = Modifier.semantics { stateDescription = if (open) "Open" else "Closed" }
    @Composable
    fun Arrow(tint: Color, modifier: Modifier = Modifier) {
        if (onToggle != null) Icon(Icons.Filled.KeyboardArrowDown, null, modifier.rotate(turn), tint = tint)
    }
    if (photo != null) {
        // The place's photo behind its name, white on the photo's darkened bottom.
        PlacePhotoBanner(photo, modifier.padding(top = 8.dp).clip(MaterialTheme.shapes.large).then(toggle).then(state)) {
            // In the top left corner, on a dark disc like the photo's credit opposite.
            if (onToggle != null) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp).size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { Arrow(Color.White) }
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.semantics { heading() })
                    Text(count, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
                FilledTonalButton(onClick = onPlay) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Play visit")
                }
            }
        }
        return
    }
    // No photo: the name on the list's background, Play visit as text, the arrow on a pill.
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp).clip(MaterialTheme.shapes.medium).then(toggle).then(state).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(count, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        TextButton(onClick = onPlay) {
            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Play visit")
        }
        if (onToggle != null) {
            FilledTonalButton(
                onClick = onToggle,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.semantics { contentDescription = if (open) "Hide its texts" else "Show its texts" },
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.rotate(turn))
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
    var choosingLanguage by remember { mutableStateOf(false) }
    var confirmCloud by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf(false) }
    // Whether Listen reads the translation: by default once there is one.
    var useTranslation by rememberSaveable(scan.id) { mutableStateOf(scan.translation != null) }
    LaunchedEffect(scan.translation) { if (scan.translation != null) useTranslation = true }
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
        // No animateContentSize: the opened text grows in by itself (see AnimatedVisibility below), and both at once judder.
        modifier = modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Collapse" else "Expand") { expanded = !expanded },
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                TranslateButton(scan, translating, target, useTranslation, { useTranslation = it }, viewModel)
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
                        DropdownMenuItem(
                            text = { Text("Language") },
                            leadingIcon = { Icon(Icons.Filled.Language, null) },
                            onClick = { menu = false; choosingLanguage = true },
                        )
                        if (viewModel.cloudAvailable && scan.thumbnail != null) {
                            DropdownMenuItem(
                                text = { Text("Improve with cloud") },
                                leadingIcon = { Icon(Icons.Filled.Translate, null) },
                                onClick = { menu = false; confirmCloud = true },
                            )
                        }
                        if (Translator.needsTranslation(scan.allLanguages(), target)) {
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
            // Closed: the photo, the title (or the text's start) with the version Listen reads, and Listen.
            // Opened: that version first, under the same label (which slides down to head it), then the other.
            val translation = scan.translation
            val translated = useTranslation && translation != null
            val title = if (translated) scan.translatedHeadline ?: scan.headline else scan.headline
            val originalLabel = "Original" + (scan.languageLabel()?.let { " · $it" } ?: "")
            val translationLabel = "Translation" + (scan.translationTag?.let { " · ${Translator.nameOf(it)}" } ?: "")
            // Without a translation there's one version: its language, which stays put.
            val label = when {
                translation == null -> scan.languageLabel()
                translated -> translationLabel
                else -> originalLabel
            }
            val extras = listOfNotNull(if (queued) "In queue" else null, if (translating) "Translating…" else null).joinToString(" · ")
            LookaheadScope {
                val lookahead = this
                // Small and grey under the title; full size, in colour, heading its text: growing as it moves.
                val movingLabel = remember(scan.id) {
                    movableContentOf { text: String, heading: Boolean ->
                        // Laid out at its new size at once and scaled from the old one, and only its position
                        // animated: no text measured every frame, and never squeezed (which cut its last word off).
                        val size = if (heading) HEADING_SP else SMALL_SP
                        val scale = remember { Animatable(1f) }
                        var shown by remember { mutableStateOf(heading) }
                        LaunchedEffect(heading) {
                            if (shown == heading) return@LaunchedEffect
                            shown = heading
                            scale.snapTo(if (heading) SMALL_SP / HEADING_SP else HEADING_SP / SMALL_SP)
                            scale.animateTo(1f, soft())
                        }
                        val color by animateColorAsState(
                            if (heading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            soft(),
                            label = "label colour",
                        )
                        Text(
                            text,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = size.sp, lineHeight = (size + 4).sp),
                            color = color,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .animatePlacement(lookahead)
                                .graphicsLayer {
                                    scaleX = scale.value
                                    scaleY = scale.value
                                    transformOrigin = TransformOrigin(0f, 0f)
                                },
                        )
                    }
                }
                val slides = expanded && translation != null
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Top aligned, so the photo stays where it is while the text beside it changes.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Thumbnail(
                            scan.thumbnail,
                            Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "View photo") { viewing = true },
                            maxSide = 256,
                        )
                        Column(
                            Modifier.weight(1f).heightIn(min = 56.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        ) {
                            if (title != null) {
                                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            } else if (!expanded) {
                                Text(scan.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (!slides && label != null) movingLabel(label, false)
                            if (extras.isNotEmpty()) {
                                Text(extras, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                            ListenButton(scan, player, viewModel, useTranslation)
                        }
                    }
                    // The first version's label, outside the fading text so it stays in sight all the way down.
                    if (slides && label != null) Box(Modifier.padding(top = 4.dp)) { movingLabel(label, true) }
                    AnimatedVisibility(
                        visible = expanded,
                        enter = fadeIn(soft()) + expandVertically(soft(), expandFrom = Alignment.Top),
                        exit = fadeOut(soft()) + shrinkVertically(soft(), shrinkTowards = Alignment.Top),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            @Composable
                            fun Version(heading: String?, otherTitle: String?, text: String, reading: Boolean, primary: Boolean, style: TextStyle) {
                                // The second version a little apart from the first.
                                Column(Modifier.padding(top = if (primary) 0.dp else 16.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    // The first version's heading is the moving label above.
                                    if (heading != null && !primary) {
                                        Text(
                                            heading,
                                            style = MaterialTheme.typography.labelMedium.copy(fontSize = HEADING_SP.sp, lineHeight = (HEADING_SP + 4).sp),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    // The other version's own title, unless it's printed as the text's first line.
                                    if (otherTitle != null && otherTitle != title && !text.startsWith(otherTitle, ignoreCase = true)) {
                                        Text(otherTitle, style = MaterialTheme.typography.titleSmall)
                                    }
                                    HighlightedText(text, if (reading) player.highlight else null, style)
                                }
                            }
                            val originalReading = current?.translated == false
                            val translationReading = current?.translated == true
                            when {
                                translation == null -> Version(null, null, scan.text, originalReading, true, MaterialTheme.typography.bodyLarge)
                                translated -> {
                                    Version(translationLabel, null, translation, translationReading, true, MaterialTheme.typography.bodyLarge)
                                    Version(originalLabel, scan.headline, scan.text, originalReading, false, MaterialTheme.typography.bodyMedium)
                                }
                                else -> {
                                    Version(originalLabel, null, scan.text, originalReading, true, MaterialTheme.typography.bodyLarge)
                                    Version(translationLabel, scan.translatedHeadline, translation, translationReading, false, MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (editing) EditTextDialog(scan.text, onDismiss = { editing = false }) { viewModel.editText(scan, it) }
    if (choosing) ChooseParagraphsDialog(scan.text, onDismiss = { choosing = false }) { viewModel.editText(scan, it) }
    if (confirmCloud) CloudConfirmDialog(onDismiss = { confirmCloud = false }) { viewModel.improveWithCloud(scan) }
    if (choosingLanguage) LanguageDialog(scan, onDismiss = { choosingLanguage = false }) { viewModel.setLanguage(scan, it) }
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
            textAlign = TextAlign.Center,
        )
    }
}

private const val HIGHLIGHT_MS = 3000L

/** How long a card takes to open or close, and its language label to move: easing in and out. */
private const val LABEL_MOVE_MS = 400

private fun <T> soft() = tween<T>(LABEL_MOVE_MS, easing = EaseInOutCubic)

/** The language label's size under the title, and heading its text. */
private const val SMALL_SP = 11f
private const val HEADING_SP = 14f

/** Every label Listen can show, to size it for the widest. */
private val LISTEN_LABELS = listOf("Listen", "Pause", "Resume")

/** Position of [scanId] in the list: the search field first, then each visit's header and scans (all open). */
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

/** Plays or pauses the scan, in the version the card shows: the translation when [useTranslation] and there is one. */
@Composable
private fun ListenButton(scan: Scan, player: PlayerState, viewModel: TourViewModel, useTranslation: Boolean) {
    val current = player.current?.takeIf { it.id == scan.id }
    val playing = current != null && player.playing
    val translated = useTranslation && scan.translation != null
    val label = when {
        playing -> "Pause"
        current != null -> "Resume"
        else -> "Listen"
    }
    FilledTonalButton(onClick = {
        if (current != null && current.translated == translated) viewModel.togglePlayback() else viewModel.playNow(scan, translated)
    }) {
        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        // As wide as its widest label, so the button keeps its size between Listen, Pause and Resume.
        Box(contentAlignment = Alignment.CenterStart) {
            LISTEN_LABELS.forEach { Text(it, maxLines = 1, modifier = Modifier.alpha(0f).clearAndSetSemantics {}) }
            Text(label, maxLines = 1)
        }
    }
}

/**
 * Translation for the card's top row: with a translation, switches the card (its title, language
 * and what Listen reads) between it and the original, highlighted while it shows the translation;
 * without one, translates into [target]. Not shown for a text already in [target].
 */
@Composable
private fun TranslateButton(scan: Scan, translating: Boolean, target: String, useTranslation: Boolean, onUseTranslation: (Boolean) -> Unit, viewModel: TourViewModel) {
    val hasTranslation = scan.translation != null
    if (!hasTranslation && !Translator.needsTranslation(scan.allLanguages(), target)) return
    val showing = useTranslation && hasTranslation
    IconButton(
        onClick = {
            when {
                hasTranslation -> onUseTranslation(!showing)
                !translating -> { onUseTranslation(true); viewModel.translate(scan) }
            }
        },
        enabled = hasTranslation || !translating,
    ) {
        if (translating && !hasTranslation) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Filled.Translate,
                when {
                    !hasTranslation -> "Translate into ${Translator.nameOf(target)}"
                    showing -> "Show the original"
                    else -> "Show the translation"
                },
                tint = if (showing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Moves this to where it's placed in [scope] smoothly (see [soft]) instead of jumping there, as
 * Compose's LookaheadScope sample does: only its position animates, it's measured as it is.
 */
private fun Modifier.animatePlacement(scope: LookaheadScope): Modifier = composed {
    val coroutines = rememberCoroutineScope()
    var animation by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this.approachLayout(
        isMeasurementApproachInProgress = { false },
        isPlacementApproachInProgress = { animation?.isRunning == true },
    ) { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            val coordinates = coordinates
            if (coordinates == null) {
                placeable.place(0, 0)
                return@layout
            }
            with(scope) {
                val target = lookaheadScopeCoordinates.localLookaheadPositionOf(coordinates).round()
                val current = animation ?: Animatable(target, IntOffset.VectorConverter).also { animation = it }
                if (current.targetValue != target) coroutines.launch { current.animateTo(target, soft()) }
                val placed = lookaheadScopeCoordinates.localPositionOf(coordinates, Offset.Zero).round()
                placeable.place(current.value - placed)
            }
        }
    }
}
