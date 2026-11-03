package app.omalauncher.trending.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omalauncher.trending.TrendingUi
import app.omalauncher.trending.core.TrendingCore
import app.omalauncher.trending.logic.Feed
import app.omalauncher.trending.logic.Filter
import app.omalauncher.trending.logic.Flags
import app.omalauncher.trending.logic.Lang
import app.omalauncher.trending.logic.Mode
import app.omalauncher.trending.logic.Source
import app.omalauncher.trending.logic.Story
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Everything the feed screen can ask for. */
class TrendingActions(
    val onSource: (Source) -> Unit,
    val onMode: (Mode) -> Unit,
    val onHours: (Int) -> Unit,
    val onRefresh: () -> Unit,
    val onQuery: (String) -> Unit,
    val onShowMore: () -> Unit,
    val onOpen: (url: String, id: String) -> Unit,
    val onShare: (Story) -> Unit,
    val onCopy: (Story) -> Unit,
    val onMarkSeen: (String) -> Unit,
    val onMarkUnseen: (String) -> Unit,
    val onClearSeen: () -> List<String>,
    val onRestoreSeen: (List<String>) -> Unit,
    val onChartExpanded: (Boolean) -> Unit,
    val onSettings: () -> Unit,
    val onNotificationSettings: () -> Unit,
    val onFilter: (Filter) -> Unit,
    val onTopic: (String) -> Unit,
    val onLang: (String) -> Unit,
    val onBar: (Int) -> Unit,
    val onClearNarrowing: () -> Unit,
    val onRadar: () -> Unit,
    val onGhOfficial: (Boolean) -> Unit = {},
    /** Returns true when the story is now saved. */
    val onToggleSaved: (Story) -> Boolean,
    val onSaved: () -> Unit,
    val onDiscussions: suspend (Story) -> List<Story>,
)

private val Hours = listOf(1, 6, 12, 24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingScreen(
    ui: TrendingUi,
    chartExpanded: Boolean,
    notifying: Boolean,
    saved: Set<String>,
    hasInterests: Boolean,
    actions: TrendingActions,
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    val buzz: () -> Unit = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var searching by rememberSaveable { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<Story?>(null) }
    val seen = remember(ui.seen) { ui.seen.toHashSet() }

    val closeSearch = {
        searching = false
        actions.onQuery("")
    }
    BackHandler(enabled = searching, onBack = closeSearch)

    val clearMarks = {
        buzz()
        val before = actions.onClearSeen()
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Cleared ${before.size} read marks", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) actions.onRestoreSeen(before)
        }
        Unit
    }
    val toggleSaved = { story: Story ->
        buzz()
        val now = actions.onToggleSaved(story)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                if (now) "Saved for later" else "Removed from saved",
                actionLabel = if (now) "View" else "Undo",
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (now) actions.onSaved() else actions.onToggleSaved(story)
            }
        }
        Unit
    }
    val longPress = { story: Story ->
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        sheetFor = story
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val wide = maxWidth >= 700.dp
        val list = ListContent(
            ui = ui,
            seen = seen,
            onOpen = { url, id -> buzz(); actions.onOpen(url, id) },
            onLongPress = longPress,
            onClearMarks = clearMarks,
            onShowMore = { buzz(); actions.onShowMore() },
            onClearSearch = closeSearch,
            onRetry = { buzz(); actions.onRefresh() },
            saved = saved,
            hasInterests = hasInterests,
            onToggleSaved = toggleSaved,
            buzz = buzz,
            actions = actions,
        )
        val header = HeaderContent(
            ui = ui,
            searching = searching,
            notifying = notifying,
            chartExpanded = chartExpanded,
            hasInterests = hasInterests,
            buzz = buzz,
            actions = actions,
            onSearch = { searching = true },
            onCloseSearch = closeSearch,
        )
        if (wide) {
            WideLayout(header, list, listState, ui)
        } else {
            NarrowLayout(header, list, listState, ui)
        }
        ScrollToTop(listState, Modifier.align(Alignment.BottomEnd), wide)
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars),
        )
    }

    // A new source, list or window starts at the top of its list.
    LaunchedEffect(ui.source, ui.mode, ui.hours) {
        if (listState.firstVisibleItemIndex > 3) listState.scrollToItem(0)
    }

    sheetFor?.let { story ->
        StorySheet(
            story = story,
            read = story.id in seen,
            saved = story.id in saved,
            nowSec = ui.nowSec,
            loadThreads = actions.onDiscussions,
            onToggleSaved = { sheetFor = null; toggleSaved(story) },
            onDismiss = { sheetFor = null },
            onOpen = { url -> sheetFor = null; actions.onOpen(url, story.id) },
            onShare = { sheetFor = null; actions.onShare(story) },
            onCopy = { sheetFor = null; actions.onCopy(story) },
            onToggleRead = {
                sheetFor = null
                if (story.id in seen) actions.onMarkUnseen(story.id) else actions.onMarkSeen(story.id)
            },
        )
    }
}

/** The top bar, controls and chart, shared by both layouts. */
private class HeaderContent(
    val ui: TrendingUi,
    val searching: Boolean,
    val notifying: Boolean,
    val chartExpanded: Boolean,
    val hasInterests: Boolean,
    val buzz: () -> Unit,
    val actions: TrendingActions,
    val onSearch: () -> Unit,
    val onCloseSearch: () -> Unit,
)

/** The story list, shared by both layouts. */
private class ListContent(
    val ui: TrendingUi,
    val seen: Set<String>,
    val onOpen: (String, String) -> Unit,
    val onLongPress: (Story) -> Unit,
    val onClearMarks: () -> Unit,
    val onShowMore: () -> Unit,
    val onClearSearch: () -> Unit,
    val onRetry: () -> Unit,
    val saved: Set<String>,
    val hasInterests: Boolean,
    val onToggleSaved: (Story) -> Unit,
    val buzz: () -> Unit,
    val actions: TrendingActions,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NarrowLayout(header: HeaderContent, list: ListContent, listState: LazyListState, ui: TrendingUi) {
    val colors = MaterialTheme.colorScheme
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var isPulling by remember { mutableStateOf(false) }
    LaunchedEffect(ui.loading) {
        if (!ui.loading) isPulling = false
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        PullToRefreshBox(
            isRefreshing = isPulling && ui.loading,
            onRefresh = { isPulling = true; header.buzz(); header.actions.onRefresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottom + 88.dp),
            ) {
                item(key = "top") { TopBar(header) }
                // Source, list and window stay on screen while scrolling with translucent backdrop.
                stickyHeader(key = "controls") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.background.copy(alpha = 0.94f))
                    ) {
                        Controls(header, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        LoadingLine(ui.loading && !isPulling)
                        HorizontalDivider(
                            color = colors.outlineVariant.copy(alpha = 0.35f),
                            thickness = 0.5.dp,
                        )
                    }
                }
                item(key = "chart") {
                    ChartCard(
                        ui = ui,
                        expanded = header.chartExpanded,
                        onToggle = { header.buzz(); header.actions.onChartExpanded(!header.chartExpanded) },
                        onBar = { header.buzz(); header.actions.onBar(it) },
                        onLang = { header.buzz(); header.actions.onLang(it) },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    )
                }
                item(key = "topics") { TopicsCard(header, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) }
                storyList(list, Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WideLayout(header: HeaderContent, list: ListContent, listState: LazyListState, ui: TrendingUi) {
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var isPulling by remember { mutableStateOf(false) }
    LaunchedEffect(ui.loading) {
        if (!ui.loading) isPulling = false
    }

    Row(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
        LazyColumn(
            modifier = Modifier.width(380.dp).fillMaxHeight(),
            contentPadding = PaddingValues(bottom = bottom + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "top") { TopBar(header) }
            item(key = "controls") { Controls(header, Modifier.padding(horizontal = 16.dp)) }
            item(key = "chart") {
                // There is room beside the list, so the full charts always show.
                ChartCard(
                    ui = ui,
                    expanded = true,
                    onToggle = null,
                    onBar = { header.buzz(); header.actions.onBar(it) },
                    onLang = { header.buzz(); header.actions.onLang(it) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item(key = "topics") { TopicsCard(header, Modifier.padding(horizontal = 16.dp)) }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.weight(1f)) {
            LoadingLine(ui.loading && !isPulling)
            PullToRefreshBox(
                isRefreshing = isPulling && ui.loading,
                onRefresh = { isPulling = true; header.buzz(); header.actions.onRefresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottom + 88.dp),
                ) {
                    storyList(list, Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun LoadingLine(loading: Boolean) {
    Box(Modifier.fillMaxWidth().height(2.dp)) {
        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
        ) {
            LinearProgressIndicator(Modifier.fillMaxSize(), trackColor = Color.Transparent)
        }
    }
}

@Composable
private fun TopBar(header: HeaderContent) {
    val ui = header.ui
    val colors = MaterialTheme.colorScheme
    if (header.searching) {
        SearchBar(query = ui.query, onQuery = header.actions.onQuery, onClose = header.onCloseSearch)
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Trending",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
            )
            Text(
                statusLine(ui),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 1.dp,
            color = colors.surfaceContainer,
        ) {
            Row(
                Modifier.padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { header.buzz(); header.actions.onRadar() }, modifier = Modifier.size(36.dp)) {
                    Icon(AppIcons.Radar, contentDescription = "Daily Radar", tint = colors.primary, modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = { header.buzz(); header.onSearch() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = colors.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                // Pull down to refresh; the bar under the controls shows a download.
                IconButton(onClick = { header.buzz(); header.actions.onSaved() }, modifier = Modifier.size(36.dp)) {
                    Icon(AppIcons.BookmarkBorder, contentDescription = "Saved stories", tint = colors.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = { header.buzz(); header.actions.onNotificationSettings() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = if (header.notifying) "Alerts are on" else "Alerts are off",
                        tint = if (header.notifying) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(19.dp),
                    )
                }
                IconButton(onClick = { header.buzz(); header.actions.onSettings() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = colors.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
            TextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text("Search titles, sites, languages") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Controls(header: HeaderContent, modifier: Modifier = Modifier) {
    val ui = header.ui
    val colors = MaterialTheme.colorScheme
    val isOfficial = ui.source == Source.GH && ui.ghOfficial
    val isGhNew = ui.source == Source.GH && !ui.ghOfficial

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = colors.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Row 1: Primary Feed Selector (3 streamlined tabs)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val tabs = listOf(
                    Triple("Hacker News", ui.source == Source.HN) {
                        header.buzz()
                        header.actions.onSource(Source.HN)
                    },
                    Triple("Trending", isOfficial) {
                        header.buzz()
                        if (ui.source != Source.GH) header.actions.onSource(Source.GH)
                        header.actions.onGhOfficial(true)
                    },
                    Triple("New Repos", isGhNew) {
                        header.buzz()
                        if (ui.source != Source.GH) header.actions.onSource(Source.GH)
                        header.actions.onGhOfficial(false)
                    },
                )
                tabs.forEachIndexed { i, (label, selected, onClick) ->
                    SegmentedButton(
                        selected = selected,
                        onClick = onClick,
                        shape = SegmentedButtonDefaults.itemShape(i, tabs.size),
                        icon = {},
                    ) {
                        Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Row 2: Minimalist horizontal strip for Time, Mode, and Filters
            val periods = if (isOfficial) {
                listOf(1 to "Today", 6 to "This Week", 24 to "This Month")
            } else {
                listOf(1 to "1h", 6 to "6h", 12 to "12h", 24 to "24h")
            }
            val counts = ui.feed?.counts

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Time period pills
                for ((h, label) in periods) {
                    val sel = ui.hours == h
                    FilterChip(
                        selected = sel,
                        onClick = { header.buzz(); header.actions.onHours(h) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.primaryContainer,
                            selectedLabelColor = colors.onPrimaryContainer,
                        ),
                    )
                }

                // Modes (only when not in Official Trending)
                AnimatedVisibility(
                    visible = !isOfficial,
                    enter = fadeIn(tween(200)) + expandHorizontally(),
                    exit = fadeOut(tween(150)) + shrinkHorizontally(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        VerticalDivider(
                            Modifier.height(18.dp).padding(horizontal = 2.dp),
                            color = colors.outlineVariant.copy(alpha = 0.5f),
                        )
                        val modes = listOf(Mode.HOT to "Top", Mode.RISING to "Rising", Mode.NEW to "New")
                        for ((m, label) in modes) {
                            val sel = ui.mode == m
                            FilterChip(
                                selected = sel,
                                onClick = { header.buzz(); header.actions.onMode(m) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.secondaryContainer,
                                    selectedLabelColor = colors.onSecondaryContainer,
                                ),
                            )
                        }
                    }
                }

                // Unread & Interests filter chips
                if (ui.seen.isNotEmpty() || header.hasInterests) {
                    VerticalDivider(
                        Modifier.height(18.dp).padding(horizontal = 2.dp),
                        color = colors.outlineVariant.copy(alpha = 0.5f),
                    )
                    if (header.hasInterests || ui.filter == Filter.INTERESTS) {
                        val sel = ui.filter == Filter.INTERESTS
                        FilterChip(
                            selected = sel,
                            onClick = { header.buzz(); header.actions.onFilter(if (sel) Filter.ALL else Filter.INTERESTS) },
                            label = { Text(if (counts?.interest != null) "For you ${counts.interest}" else "For you", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                    if (ui.seen.isNotEmpty() || ui.filter == Filter.UNREAD) {
                        val sel = ui.filter == Filter.UNREAD
                        FilterChip(
                            selected = sel,
                            onClick = { header.buzz(); header.actions.onFilter(if (sel) Filter.ALL else Filter.UNREAD) },
                            label = { Text(if (counts?.unread != null) "Unread ${counts.unread}" else "Unread", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }
    }
}

private fun langColor(name: String): Color = when (name.lowercase()) {
    "rust" -> Color(0xFFDEA584)
    "python" -> Color(0xFF3572A5)
    "typescript" -> Color(0xFF3178C6)
    "javascript" -> Color(0xFFF1E05A)
    "go" -> Color(0xFF00ADD8)
    "kotlin" -> Color(0xFFA97BFF)
    "c++", "cpp" -> Color(0xFFF34B7D)
    "c" -> Color(0xFF555555)
    "java" -> Color(0xFFB07219)
    "swift" -> Color(0xFFF05138)
    "c#", "csharp" -> Color(0xFF178600)
    "ruby" -> Color(0xFF701516)
    "php" -> Color(0xFF4F5D95)
    "shell", "bash" -> Color(0xFF89E051)
    "dart" -> Color(0xFF00B4AB)
    "html" -> Color(0xFFE34C26)
    "css" -> Color(0xFF563D7C)
    "zig" -> Color(0xFFEC915C)
    "lua" -> Color(0xFF000080)
    else -> Color(0xFF8B949E)
}

@Composable
private fun ChartCard(
    ui: TrendingUi,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    onBar: (Int) -> Unit,
    onLang: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val feed = ui.feed ?: return
    val isGh = ui.source == Source.GH
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = colors.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isGh) "Language Breakdown" else "Activity Pulse",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                    )
                    Text(
                        if (isGh) {
                            val count = feed.langs.size
                            if (count > 0) "$count ${if (count == 1) "language" else "languages"} trending · tap to filter" else "Top languages"
                        } else {
                            val points = TrendingCore.fmtCompact(feed.hotBars.sum())
                            val count = TrendingCore.fmtCompact(feed.newBars.sum())
                            "$points points · $count stories in the ${lastPhrase(ui.hours, ui)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (onToggle != null) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Show compact view" else "Show full view",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.rotate(turn),
                    )
                }
            }

            if (isGh) {
                if (feed.langs.isNotEmpty()) {
                    LanguageDistribution(
                        langs = feed.langs,
                        selectedLang = ui.lang,
                        expanded = expanded,
                        onLang = onLang,
                    )
                }
            } else {
                if (!expanded) {
                    MiniAreaCurve(feed.hotBars, colors.primary)
                } else {
                    SmoothAreaCurve(
                        values = feed.hotBars,
                        tint = colors.primary,
                        ui = ui,
                        onBar = onBar,
                    )
                    val peak = feed.newBars.indices.maxByOrNull { feed.hotBars[it] }
                    if (peak != null && feed.hotBars[peak] > 0) {
                        Text(
                            "Peak activity at ${barRange(ui, peak)} (${TrendingCore.fmtCompact(feed.hotBars[peak])} pts)",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDistribution(
    langs: List<Lang>,
    selectedLang: String,
    expanded: Boolean,
    onLang: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val total = langs.sumOf { it.count }.coerceAtLeast(1)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceContainerHighest),
        ) {
            Row(Modifier.fillMaxSize()) {
                for (lang in langs) {
                    val weight = (lang.count.toFloat() / total).coerceAtLeast(0.01f)
                    val isSelected = selectedLang.isNotEmpty() && selectedLang.equals(lang.name, ignoreCase = true)
                    val color = langColor(lang.name)
                    Box(
                        Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .padding(horizontal = 0.5.dp)
                            .background(if (selectedLang.isEmpty() || isSelected) color else color.copy(alpha = 0.25f)),
                    )
                }
            }
        }

        if (expanded) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(top = 2.dp),
            ) {
                items(langs, key = { it.name }) { lang ->
                    val isSelected = selectedLang.isNotEmpty() && selectedLang.equals(lang.name, ignoreCase = true)
                    val color = langColor(lang.name)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onLang(if (isSelected) "" else lang.name) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(color),
                                )
                                Text(lang.name, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "${lang.count}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) colors.onSecondaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.height(28.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.secondaryContainer,
                            selectedLabelColor = colors.onSecondaryContainer,
                        ),
                    )
                }
            }
        }
    }
}

/** "14:00–15:00" for bar [index]. */
private fun barRange(ui: TrendingUi, index: Int): String {
    val start = ui.window.origin + index.toLong() * ui.window.step
    return "${TrendingCore.fmtBarLabel(start, 1)}–${TrendingCore.fmtBarLabel(start + ui.window.step, 1)}"
}

/** Which of [n] equal bars across [width] an x position falls in. */
private fun barAt(x: Float, width: Int, n: Int): Int =
    (x / (width.toFloat() / n)).toInt().coerceIn(0, n - 1)

@Composable
private fun MiniAreaCurve(values: List<Double>, tint: Color) {
    val peakV = values.maxOrNull() ?: 0.0
    val n = values.size.coerceAtLeast(1)
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .drawWithCache {
                if (peakV <= 0.0 || n < 2) {
                    onDrawBehind { }
                } else {
                    val w = size.width
                    val h = size.height
                    val points = values.mapIndexed { i, v ->
                        val x = i * (w / (n - 1).toFloat())
                        val y = h - ((v / peakV) * (h - 4.dp.toPx())).toFloat() - 2.dp.toPx()
                        Offset(x, y)
                    }
                    val linePath = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val cx = (p0.x + p1.x) / 2f
                            cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                        }
                    }
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    val fillBrush = Brush.verticalGradient(
                        listOf(tint.copy(alpha = 0.28f), tint.copy(alpha = 0.06f), Color.Transparent)
                    )
                    val strokeStyle = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    onDrawBehind {
                        drawPath(fillPath, brush = fillBrush)
                        drawPath(linePath, color = tint, style = strokeStyle)
                    }
                }
            }
    )
}

@Composable
private fun SmoothAreaCurve(
    values: List<Double>,
    tint: Color,
    ui: TrendingUi,
    onBar: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val peakV = values.maxOrNull() ?: 0.0
    val peakIdx = if (peakV > 0.0) values.indexOf(peakV) else -1
    val n = values.size.coerceAtLeast(1)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .pointerInput(n) { detectTapGestures { onBar(barAt(it.x, size.width, n)) } }
                .drawWithCache {
                    if (peakV <= 0.0 || n < 2) {
                        onDrawBehind { }
                    } else {
                        val w = size.width
                        val h = size.height
                        val padTop = 10.dp.toPx()
                        val padBot = 6.dp.toPx()
                        val chartH = h - padTop - padBot

                        val points = values.mapIndexed { i, v ->
                            val x = i * (w / (n - 1).toFloat())
                            val y = h - padBot - ((v / peakV) * chartH).toFloat()
                            Offset(x, y)
                        }

                        val linePath = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 0 until points.size - 1) {
                                val p0 = points[i]
                                val p1 = points[i + 1]
                                val cx = (p0.x + p1.x) / 2f
                                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                            }
                        }

                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(w, h)
                            lineTo(0f, h)
                            close()
                        }
                        val fillBrush = Brush.verticalGradient(
                            colors = listOf(tint.copy(alpha = 0.35f), tint.copy(alpha = 0.08f), Color.Transparent),
                            startY = padTop,
                            endY = h,
                        )
                        val strokeStyle = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        val peakOuterRadius = 8.dp.toPx()
                        val peakMidRadius = 4.dp.toPx()
                        val peakInnerRadius = 2.5.dp.toPx()
                        val selLineWidth = 1.dp.toPx()
                        val selDashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                        onDrawBehind {
                            drawPath(fillPath, brush = fillBrush)
                            drawPath(linePath, color = tint, style = strokeStyle)

                            if (peakIdx in points.indices) {
                                val peakPt = points[peakIdx]
                                drawCircle(tint.copy(alpha = 0.25f), radius = peakOuterRadius, center = peakPt)
                                drawCircle(colors.surface, radius = peakMidRadius, center = peakPt)
                                drawCircle(tint, radius = peakInnerRadius, center = peakPt)
                            }

                            if (ui.bar in points.indices) {
                                val selPt = points[ui.bar]
                                drawLine(
                                    color = tint.copy(alpha = 0.6f),
                                    start = Offset(selPt.x, padTop),
                                    end = Offset(selPt.x, h),
                                    strokeWidth = selLineWidth,
                                    pathEffect = selDashEffect,
                                )
                            }
                        }
                    }
                }
        )
    }
}




/** Topic chips, and on GitHub the languages, for the window on screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicsCard(header: HeaderContent, modifier: Modifier = Modifier) {
    val ui = header.ui
    val feed = ui.feed ?: return
    val colors = MaterialTheme.colorScheme
    // A chip in use stays even when it has dropped out of the list.
    val topics = feed.topics.map { it.word to it }.let { list ->
        if (ui.topic.isEmpty() || list.any { it.first == ui.topic }) list else listOf(ui.topic to null) + list
    }
    val langs = feed.langs.map { it.name to it.count }.let { list ->
        if (ui.lang.isEmpty() || list.any { it.first == ui.lang }) list else listOf(ui.lang to 0) + list
    }
    val showLangs = langs.isNotEmpty() && ui.source != Source.GH
    if (topics.isEmpty() && !showLangs) return
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.TrendingUp, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Trending topics", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    val fresh = feed.topics.any { it.fresh }
                    Text(
                        if (fresh) "Most talked about in the ${lastPhrase(ui.hours, ui)} · ↗ picking up fast"
                        else "Most talked about in the ${lastPhrase(ui.hours, ui)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (topics.isNotEmpty()) {
                ChipRow(topics) { (word, topic) ->
                    TopicChip(
                        label = word,
                        count = topic?.count ?: 0,
                        fresh = topic?.fresh == true,
                        selected = ui.topic == word,
                        onClick = { header.buzz(); header.actions.onTopic(word) },
                    )
                }
            }
            if (showLangs) {
                Text(
                    "Languages",
                    modifier = Modifier.padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                ChipRow(langs) { (name, count) ->
                    TopicChip(
                        label = name,
                        count = count,
                        fresh = false,
                        selected = ui.lang == name,
                        onClick = { header.buzz(); header.actions.onLang(name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> ChipRow(items: List<T>, chip: @Composable (T) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items) { chip(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicChip(label: String, count: Int, fresh: Boolean, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                buildAnnotatedString {
                    append(label)
                    if (count > 0) {
                        withStyle(SpanStyle(color = if (selected) colors.onSecondaryContainer.copy(alpha = 0.7f) else colors.onSurfaceVariant)) {
                            append("  $count")
                        }
                    }
                },
                maxLines = 1,
            )
        },
        leadingIcon = when {
            selected -> ({ Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) })
            fresh -> ({ Icon(AppIcons.TrendingUp, contentDescription = "Picking up fast", tint = colors.tertiary, modifier = Modifier.size(FilterChipDefaults.IconSize)) })
            else -> null
        },
    )
}

private fun LazyListScope.storyList(list: ListContent, pad: Modifier) {
    val ui = list.ui
    val feed = ui.feed
    if (ui.trouble != null && feed != null) {
        item(key = "trouble") {
            TroubleBanner(
                "${ui.trouble} Showing results from ${agoPhrase(feed.fetched, ui.nowSec)}.",
                onRetry = list.onRetry,
                modifier = pad.padding(top = 12.dp),
            )
        }
    }
    item(key = "listHead") { ListHeader(list, pad.padding(top = 16.dp, bottom = 8.dp)) }
    when {
        feed == null && ui.trouble != null -> item(key = "error") {
            ErrorCard(ui.trouble, onRetry = list.onRetry, modifier = pad)
        }
        feed == null -> items(6) { SkeletonCard(pad.padding(bottom = 10.dp)) }
        feed.shown.isEmpty() -> item(key = "empty") {
            EmptyState(ui, onClearSearch = list.onClearSearch, onClearNarrowing = list.actions.onClearNarrowing, modifier = pad)
        }
        else -> {
            itemsIndexed(feed.shown, key = { _, story -> story.id }) { index, story ->
                StoryCard(
                    story = story,
                    index = index,
                    ui = ui,
                    feed = feed,
                    read = story.id in list.seen,
                    saved = story.id in list.saved,
                    onOpen = list.onOpen,
                    onLongPress = { list.onLongPress(story) },
                    onToggleSaved = { list.onToggleSaved(story) },
                    modifier = pad.padding(bottom = 10.dp).animateItem(),
                )
            }
            if (feed.matching > feed.shown.size) {
                item(key = "more") {
                    Box(pad.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = list.onShowMore) {
                            Text("Show more · ${feed.matching - feed.shown.size} left")
                        }
                    }
                }
            } else if (feed.shown.size >= 10) {
                item(key = "end") {
                    Text(
                        "You're all caught up",
                        modifier = pad.fillMaxWidth().padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListHeader(list: ListContent, modifier: Modifier) {
    val ui = list.ui
    val feed = ui.feed
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                listTitle(ui),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
            )
            Text(listNoteFor(ui, feed), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        AnimatedVisibility(
            visible = ui.narrowed || ui.seen.isNotEmpty(),
            enter = fadeIn(tween(200)) + scaleIn(),
            exit = fadeOut(tween(150)) + scaleOut(),
        ) {
            when {
                ui.narrowed -> TextButton(onClick = { list.buzz(); list.actions.onClearNarrowing() }) { Text("Show all") }
                ui.seen.isNotEmpty() -> TextButton(onClick = list.onClearMarks) { Text("Clear read") }
            }
        }
    }
}

private fun listNoteFor(ui: TrendingUi, feed: Feed?): String {
    val narrowing = buildList {
        if (ui.topic.isNotEmpty()) add("about “${ui.topic}”")
        if (ui.lang.isNotEmpty()) add("in ${ui.lang}")
        if (ui.bar >= 0) add("posted ${barRange(ui, ui.bar)}")
    }
    return when {
        ui.query.isNotBlank() && feed != null ->
            "${feed.matching} ${if (feed.matching == 1) "match" else "matches"} for “${ui.query.trim()}”"
        feed != null && (narrowing.isNotEmpty() || ui.filter != Filter.ALL) -> {
            val noun = if (ui.source == Source.HN) {
                if (feed.matching == 1) "story" else "stories"
            } else {
                if (feed.matching == 1) "repo" else "repos"
            }
            (listOf("${feed.matching} $noun") + narrowing).joinToString(" ")
        }
        feed != null && feed.derived && ui.source == Source.GH && ui.loading ->
            "Preview from a wider window · loading the exact list"
        ui.olderLoading -> "${listNote(ui)} · loading older stories"
        feed != null && feed.hidden > 0 ->
            "${listNote(ui)} · ${feed.hidden} spam ${if (feed.hidden == 1) "repo" else "repos"} hidden"
        else -> listNote(ui)
    }
}



private data class StoryTag(
    val label: String,
    val bg: Color,
    val fg: Color,
    val isCritical: Boolean = false,
    val isWarning: Boolean = false,
    val isSpecial: Boolean = false,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoryCard(
    story: Story,
    index: Int,
    ui: TrendingUi,
    feed: Feed,
    read: Boolean,
    saved: Boolean,
    onOpen: (String, String) -> Unit,
    onLongPress: () -> Unit,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val hn = ui.source == Source.HN
    val faded = colors.onSurfaceVariant.copy(alpha = 0.7f)

    val isStackAffected = story.affectsStack.isNotEmpty() || story.has(Flags.STACK_AFFECTED)
    val isSecurity = story.has(Flags.SECURITY)
    val isCritical = !read && (isStackAffected || isSecurity)
    val isBreakout = !read && !isCritical && (story.has(Flags.BREAKOUT) || (story.has(Flags.RISING) && story.heat >= 40.0))

    // Animation state read is deferred to graphicsLayer so scrolling never recomposes StoryCard
    val pulseTransition = if (isCritical || isBreakout) {
        val critTransition = rememberInfiniteTransition(label = "critTransition")
        critTransition.animateFloat(
            initialValue = if (isCritical) 0.35f else 0.60f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(if (isCritical) 850 else 1300), RepeatMode.Reverse),
            label = "critAlpha",
        )
    } else null

    val accent = when {
        read -> faded
        isCritical -> colors.error
        isBreakout -> colors.primary
        ui.mode == Mode.NEW -> colors.tertiary
        else -> colors.primary
    }
    val shape = RoundedCornerShape(16.dp)
    val meta = buildList {
        if (hn) {
            if (story.domain.isNotEmpty()) add(story.domain) else add("Hacker News")
            if (story.meta.isNotEmpty()) add("by ${story.meta}")
        } else if (story.meta.isNotEmpty()) {
            add(story.meta)
        }
        if (story.t > 0) add(agoPhrase(story.t, ui.nowSec))
    }.joinToString(" · ")
    val value = if (ui.mode == Mode.RISING) story.heat else story.score
    val peak = if (ui.mode == Mode.RISING) feed.peakHeat else feed.peak
    val frac = (value / peak.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val animatedFrac by animateFloatAsState(
        targetValue = frac,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "scoreFrac",
    )
    val saveScale by animateFloatAsState(
        targetValue = if (saved) 1.22f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "saveScale",
    )

    val barBaseColor = when {
        read -> Color.Transparent
        isCritical -> colors.error
        isBreakout -> colors.primary
        else -> accent.copy(alpha = 0.85f)
    }
    val barWidth = if (isCritical) 4.dp else 3.dp

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = when {
            read -> colors.surfaceContainerLow
            isCritical -> colors.errorContainer.copy(alpha = 0.14f)
            else -> colors.surfaceContainer
        },
        border = if (isCritical) BorderStroke(1.dp, colors.error.copy(alpha = 0.35f)) else null,
        tonalElevation = if (read) 0.dp else if (isCritical) 2.dp else 1.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(shape)
                .combinedClickable(
                    onClick = { onOpen(story.url, story.id) },
                    onLongClick = onLongPress,
                    onLongClickLabel = "More actions",
                ),
        ) {
            Box(
                Modifier
                    .width(barWidth)
                    .fillMaxHeight()
                    .then(
                        if (pulseTransition != null) {
                            Modifier.graphicsLayer { alpha = pulseTransition.value }
                        } else Modifier
                    )
                    .background(barBaseColor),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "${index + 1}",
                        modifier = Modifier.width(26.dp).padding(top = 1.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            story.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (read) FontWeight.Normal else FontWeight.SemiBold,
                            color = if (read) faded else colors.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (meta.isNotEmpty()) {
                            Text(
                                meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = faded,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                val tags = buildList {
                    if (isStackAffected) {
                        add(StoryTag("In your stack: ${story.affectsStack.ifEmpty { "dependency" }}", colors.errorContainer, colors.onErrorContainer, isCritical = true))
                    }
                    if (story.crossSource || story.has(Flags.CROSS_SOURCE)) {
                        add(StoryTag("HN + GitHub", colors.tertiaryContainer, colors.onTertiaryContainer))
                    }
                    if (story.has(Flags.NEW_AND_TRENDING)) {
                        add(StoryTag("New & Trending", colors.secondaryContainer, colors.onSecondaryContainer, isSpecial = true))
                    }
                    if (story.has(Flags.BREAKOUT)) {
                        add(StoryTag("Breakout", colors.primaryContainer, colors.onPrimaryContainer, isWarning = true))
                    }
                    if (isSecurity) {
                        add(StoryTag(story.tag.ifEmpty { "Security" }.replaceFirstChar { it.uppercase() }, colors.errorContainer, colors.onErrorContainer, isCritical = true))
                    }
                    if (story.has(Flags.RISING)) {
                        add(StoryTag("Rising fast", colors.primaryContainer, colors.onPrimaryContainer, isWarning = story.heat >= 40.0))
                    }
                    if (story.has(Flags.INTEREST)) add(StoryTag(story.matched, colors.tertiaryContainer, colors.onTertiaryContainer))
                    if (story.has(Flags.SPAM)) add(StoryTag("Likely spam", colors.surfaceVariant, colors.onSurfaceVariant))
                }
                if (tags.isNotEmpty() && !read) {
                    Row(Modifier.padding(start = 26.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (tag in tags) {
                            Tag(
                                label = tag.label,
                                bg = tag.bg,
                                fg = tag.fg,
                                isPulsing = tag.isCritical || tag.isWarning,
                                pulseState = pulseTransition,
                                isSpecial = tag.isSpecial,
                            )
                        }
                    }
                }
                if (story.blurb.isNotEmpty()) {
                    Text(
                        story.blurb,
                        modifier = Modifier.padding(start = 26.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (read) faded else colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    Modifier.padding(start = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Stat(
                        icon = if (hn) AppIcons.ArrowUp else Icons.Filled.Star,
                        text = TrendingCore.fmtCompact(story.score),
                        label = if (hn) "points" else "stars",
                        tint = accent,
                    )
                    if (hn) {
                        Stat(
                            icon = AppIcons.Comment,
                            text = if (story.extra > 0) TrendingCore.fmtCompact(story.extra) else "Discuss",
                            label = "comments",
                            tint = if (read) faded else colors.onSurfaceVariant,
                            onClick = { onOpen(story.talk, story.id) },
                        )
                    } else if (story.extra > 0) {
                        if (ui.source == Source.GH && ui.ghOfficial) {
                            val periodLabel = when (ui.hours) {
                                24 -> "this month"
                                6 -> "this week"
                                else -> "today"
                            }
                            Stat(
                                icon = AppIcons.TrendingUp,
                                text = "+${TrendingCore.fmtCompact(story.extra)}",
                                label = "stars $periodLabel",
                                tint = if (read) faded else colors.primary,
                            )
                        } else {
                            Text(
                                "${TrendingCore.fmtCompact(story.extra)} ${if (story.extra == 1.0) "fork" else "forks"}",
                                modifier = Modifier.padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = faded,
                            )
                        }
                    }
                    if (story.heat >= 1 && (ui.mode == Mode.RISING || story.has(Flags.RISING))) {
                        Stat(
                            icon = AppIcons.TrendingUp,
                            text = "${TrendingCore.fmtCompact(story.heat)}/h",
                            label = if (hn) "points per hour" else "stars per hour",
                            tint = if (read) faded else colors.primary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (read) {
                        Text("Read", style = MaterialTheme.typography.labelSmall, color = faded)
                    }
                    IconButton(onClick = onToggleSaved, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (saved) AppIcons.Bookmark else AppIcons.BookmarkBorder,
                            contentDescription = if (saved) "Remove from saved" else "Save for later",
                            tint = if (saved) colors.primary else faded,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    scaleX = saveScale
                                    scaleY = saveScale
                                },
                        )
                    }
                }
                Box(
                    Modifier
                        .padding(start = 26.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(colors.onSurfaceVariant.copy(alpha = 0.12f), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(animatedFrac)
                            .fillMaxHeight()
                            .background(accent.copy(alpha = if (read) 0.4f else 0.9f), RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, text: String, label: String, tint: Color, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Open $label", onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
private fun Tag(
    label: String,
    bg: Color,
    fg: Color,
    isPulsing: Boolean = false,
    pulseState: State<Float>? = null,
    isSpecial: Boolean = false,
) {
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .then(
                if (isSpecial) Modifier.border(0.75.dp, fg.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                else Modifier
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isPulsing && pulseState != null) {
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer { alpha = pulseState.value }
                    .background(fg, CircleShape)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
private fun SkeletonCard(modifier: Modifier) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse",
    )
    val block = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp).alpha(pulse), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.9f).height(14.dp).background(block, RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(0.6f).height(14.dp).background(block, RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(0.4f).height(10.dp).background(block, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun TroubleBanner(text: String, onRetry: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = colors.errorContainer) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.onErrorContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.onErrorContainer)
            TextButton(onClick = onRetry) { Text("Retry", color = colors.onErrorContainer, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.error, modifier = Modifier.size(36.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun EmptyState(ui: TrendingUi, onClearSearch: () -> Unit, onClearNarrowing: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ui.query.isNotBlank()) {
            Text("No matches for “${ui.query.trim()}”", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text("Try another word, or a wider time window.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            TextButton(onClick = onClearSearch) { Text("Clear search") }
        } else if (ui.narrowed) {
            Text("Nothing matches these filters", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(
                "Try another topic or bar, or a wider time window.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onClearNarrowing) { Text("Show all") }
        } else {
            Text("All quiet", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(
                "Nothing new in the ${lastPhrase(ui.hours, ui)} yet. Try a wider window.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ScrollToTop(listState: LazyListState, modifier: Modifier, wide: Boolean) {
    val scope = rememberCoroutineScope()
    val show by remember { derivedStateOf { listState.firstVisibleItemIndex > 6 } }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    AnimatedVisibility(
        visible = show,
        modifier = modifier.padding(end = if (wide) 24.dp else 16.dp, bottom = bottom + 16.dp),
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
            Icon(AppIcons.ArrowUp, contentDescription = "Back to top")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorySheet(
    story: Story,
    read: Boolean,
    saved: Boolean,
    nowSec: Long,
    loadThreads: suspend (Story) -> List<Story>,
    onToggleSaved: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onToggleRead: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val hn = story.id.startsWith("hn:")
    // Ask HN and other text posts link to their own thread; there is nothing else to find.
    val searchable = story.url.isNotEmpty() && (!hn || story.url != story.talk)
    var threads by remember(story.id) { mutableStateOf<List<Story>?>(null) }
    var failed by remember(story.id) { mutableStateOf(false) }
    if (searchable) {
        LaunchedEffect(story.id) {
            try {
                threads = loadThreads(story)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
                Text(story.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3)
                val where = if (hn) story.domain.ifEmpty { "news.ycombinator.com" } else "github.com"
                Text(where, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (!hn && story.blurb.isNotEmpty()) {
                    Text(
                        story.blurb,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.outlineVariant)
            val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                SheetTile(AppIcons.OpenInNew, if (hn) "Article" else "Repo", Modifier.weight(1f)) { onOpen(story.url) }
                if (hn) SheetTile(AppIcons.Comment, "Comments", Modifier.weight(1f)) { onOpen(story.talk) }
                SheetTile(
                    if (saved) AppIcons.Bookmark else AppIcons.BookmarkBorder,
                    if (saved) "Saved" else "Save",
                    Modifier.weight(1f),
                    onToggleSaved,
                )
                SheetTile(Icons.Filled.Share, "Share", Modifier.weight(1f), onShare)
                SheetTile(AppIcons.Copy, "Copy link", Modifier.weight(1f), onCopy)
                SheetTile(AppIcons.Visibility, if (read) "Unread" else "Read", Modifier.weight(1f), onToggleRead)
            }
            if (searchable) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.outlineVariant)
                Text(
                    if (hn) "Also discussed on Hacker News" else "Discussed on Hacker News",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primary,
                )
                val list = threads
                when {
                    failed -> SheetNote("Couldn't look up discussions. Check your connection.")
                    list == null -> Row(
                        Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Looking for threads…", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    list.isEmpty() -> SheetNote(if (hn) "No other threads link here." else "No threads link to this repo yet.")
                    else -> for (thread in list) {
                        ListItem(
                            headlineContent = { Text(thread.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    listOf(
                                        "${TrendingCore.fmtCompact(thread.score)} ${if (thread.score == 1.0) "point" else "points"}",
                                        "${TrendingCore.fmtCompact(thread.extra)} ${if (thread.extra == 1.0) "comment" else "comments"}",
                                        agoPhrase(thread.t, nowSec),
                                    ).joinToString(" · "),
                                )
                            },
                            leadingContent = { Icon(AppIcons.Comment, contentDescription = null) },
                            colors = itemColors,
                            modifier = Modifier.clickable { onOpen(thread.talk) }.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp).windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun SheetNote(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One of the round action buttons across the top of the story sheet. */
@Composable
private fun SheetTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(44.dp).background(colors.secondaryContainer, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun sourceName(source: Source) = if (source == Source.HN) "Hacker News" else "GitHub"

private fun modeName(mode: Mode) = when (mode) {
    Mode.HOT -> "Top"
    Mode.RISING -> "Rising"
    Mode.NEW -> "New"
}

private fun listTitle(ui: TrendingUi): String {
    if (ui.source == Source.GH && ui.ghOfficial) {
        return "Official Trending Repos"
    }
    val hn = ui.source == Source.HN
    return when (ui.mode) {
        Mode.HOT -> if (hn) "Top stories" else "Top new repos"
        Mode.RISING -> if (hn) "Rising now" else "Rising repos"
        Mode.NEW -> if (hn) "Newest stories" else "Newest repos"
    }
}

private fun listNote(ui: TrendingUi): String {
    if (ui.source == Source.GH && ui.ghOfficial) {
        val window = lastPhrase(ui.hours, ui)
        return "GitHub official trending repositories for $window"
    }
    val hn = ui.source == Source.HN
    val window = lastPhrase(ui.hours)
    return when (ui.mode) {
        Mode.HOT -> if (hn) "Most points in the $window" else "Most stars among repos created in the $window"
        Mode.RISING -> if (hn) "Most points per hour since posting" else "Most stars per hour since creation"
        Mode.NEW -> if (hn) "Latest first, from the $window" else "Latest of the most-starred new repos"
    }
}

/** "last hour", "last 6 hours" or "today", "this week", "this month". */
private fun lastPhrase(hours: Int, ui: TrendingUi? = null): String {
    if (ui != null && ui.source == Source.GH && ui.ghOfficial) {
        return when (hours) {
            24 -> "this month"
            6 -> "this week"
            else -> "today"
        }
    }
    return if (hours == 1) "last hour" else "last $hours hours"
}

private fun statusLine(ui: TrendingUi): String {
    val feed = ui.feed
    if (feed == null) return if (ui.trouble != null) "Offline" else "Loading…"
    val count = TrendingCore.fmtCompact(feed.total.toDouble())
    val what = if (ui.source == Source.HN) {
        if (feed.total == 1L) "story" else "stories"
    } else if (ui.ghOfficial) {
        "trending repos"
    } else {
        "new repos"
    }
    val fresh = if (ui.loading) "Updating…" else "Updated ${agoPhrase(feed.fetched, ui.nowSec)}"
    return "$count $what · $fresh"
}

/** "just now" or "5m ago". */
private fun agoPhrase(t: Long, now: Long): String {
    val ago = TrendingCore.fmtAgo(t, now)
    return if (ago == "now") "just now" else "$ago ago"
}
