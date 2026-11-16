package app.omalauncher.trending.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.ripple
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.graphics.drawscope.clipRect
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
import app.omalauncher.trending.logic.BucketWindow
import app.omalauncher.trending.logic.Feed
import app.omalauncher.trending.logic.Filter
import app.omalauncher.trending.logic.Flags
import app.omalauncher.trending.logic.Lang
import app.omalauncher.trending.logic.Mode
import app.omalauncher.trending.logic.Source
import app.omalauncher.trending.logic.SpikesSource
import app.omalauncher.trending.logic.SpikesView
import app.omalauncher.trending.logic.SpikesWordTrend
import app.omalauncher.trending.logic.Story
import app.omalauncher.trending.logic.Topic
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
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
    /** Switch Hacker News / Trending / New Repos in one step, keeping each tab's selections. */
    val onFeedTab: (Source, Boolean) -> Unit = { source, official ->
        onSource(source)
        if (source == Source.GH) onGhOfficial(official)
    },
    /** Open the merged Recent High Spikes tab. */
    val onSpikes: () -> Unit = {},
    /** Spikes tab: stories vs trending words. */
    val onSpikesView: (SpikesView) -> Unit = {},
    /** Pick a trending word: filter spikes stories by it. */
    val onSpikesWord: (String) -> Unit = {},
    /** Spikes: created-within window (6 / 12 / 24 hours). */
    val onSpikesCreatedWithin: (Int) -> Unit = {},
    /** Spikes: All / HN / Trending / New. */
    val onSpikesSource: (SpikesSource) -> Unit = {},
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
    LaunchedEffect(ui.source, ui.mode, ui.hours, ui.spikes, ui.spikesView) {
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
                // Language breakdown only on GitHub Trending / New Repos — not HN or Spikes.
                if (!ui.spikes && ui.source == Source.GH && ui.feed?.langs?.isNotEmpty() == true) {
                    item(key = "chart") {
                        ChartCard(
                            ui = ui,
                            expanded = header.chartExpanded,
                            onToggle = { header.buzz(); header.actions.onChartExpanded(!header.chartExpanded) },
                            onBar = { header.buzz(); header.actions.onBar(it) },
                            onLang = { header.buzz(); header.actions.onLang(it) },
                            onOpen = { url, id -> header.buzz(); header.actions.onOpen(url, id) },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        )
                    }
                }
                if (ui.spikes && ui.spikesView == SpikesView.WORDS) {
                    spikesWordsList(list, Modifier.padding(horizontal = 16.dp))
                } else {
                    item(key = "topics") { TopicsCard(header, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) }
                    storyList(list, Modifier.padding(horizontal = 16.dp))
                }
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
            if (!ui.spikes && ui.source == Source.GH && ui.feed?.langs?.isNotEmpty() == true) {
                item(key = "chart") {
                    // There is room beside the list, so the full charts always show.
                    ChartCard(
                        ui = ui,
                        expanded = true,
                        onToggle = null,
                        onBar = { header.buzz(); header.actions.onBar(it) },
                        onLang = { header.buzz(); header.actions.onLang(it) },
                        onOpen = { url, id -> header.buzz(); header.actions.onOpen(url, id) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (!(ui.spikes && ui.spikesView == SpikesView.WORDS)) {
                item(key = "topics") { TopicsCard(header, Modifier.padding(horizontal = 16.dp)) }
            }
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
                    if (ui.spikes && ui.spikesView == SpikesView.WORDS) {
                        spikesWordsList(list, Modifier.padding(horizontal = 16.dp))
                    } else {
                        storyList(list, Modifier.padding(horizontal = 16.dp))
                    }
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
    val isOfficial = !ui.spikes && ui.source == Source.GH && ui.ghOfficial
    val isGhNew = !ui.spikes && ui.source == Source.GH && !ui.ghOfficial
    val isHn = !ui.spikes && ui.source == Source.HN

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
            // Row 1: Primary Feed Selector (4 tabs including Spikes)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val tabs = listOf(
                    Triple("HN", isHn) {
                        header.buzz()
                        header.actions.onFeedTab(Source.HN, false)
                    },
                    Triple("Trending", isOfficial) {
                        header.buzz()
                        header.actions.onFeedTab(Source.GH, true)
                    },
                    Triple("New", isGhNew) {
                        header.buzz()
                        header.actions.onFeedTab(Source.GH, false)
                    },
                    Triple("Spikes", ui.spikes) {
                        header.buzz()
                        header.actions.onSpikes()
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
            val periods = when {
                ui.spikes -> listOf(6 to "≤6h", 12 to "≤12h", 24 to "≤24h")
                isOfficial -> listOf(1 to "Today", 6 to "This Week", 24 to "This Month")
                else -> listOf(1 to "1h", 6 to "6h", 12 to "12h", 24 to "24h")
            }
            val counts = ui.feed?.counts

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Time period pills (on Spikes: created-within)
                if (ui.spikes) {
                    Text(
                        "Created",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
                for ((h, label) in periods) {
                    val sel = if (ui.spikes) ui.spikesCreatedWithin == h else ui.hours == h
                    FilterChip(
                        selected = sel,
                        onClick = {
                            header.buzz()
                            if (ui.spikes) header.actions.onSpikesCreatedWithin(h)
                            else header.actions.onHours(h)
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.primaryContainer,
                            selectedLabelColor = colors.onPrimaryContainer,
                        ),
                    )
                }

                // Spikes: source + stories/words
                if (ui.spikes) {
                    VerticalDivider(
                        Modifier.height(18.dp).padding(horizontal = 2.dp),
                        color = colors.outlineVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        "Source",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                    for ((source, label) in listOf(
                        SpikesSource.ALL to "All",
                        SpikesSource.HN to "HN",
                        SpikesSource.TRENDING to "Trending",
                        SpikesSource.NEW to "New",
                    )) {
                        val sel = ui.spikesSource == source
                        FilterChip(
                            selected = sel,
                            onClick = { header.buzz(); header.actions.onSpikesSource(source) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.tertiaryContainer,
                                selectedLabelColor = colors.onTertiaryContainer,
                            ),
                        )
                    }
                    VerticalDivider(
                        Modifier.height(18.dp).padding(horizontal = 2.dp),
                        color = colors.outlineVariant.copy(alpha = 0.5f),
                    )
                    for ((view, label) in listOf(SpikesView.STORIES to "Spikes", SpikesView.WORDS to "Words")) {
                        val sel = ui.spikesView == view
                        FilterChip(
                            selected = sel,
                            onClick = { header.buzz(); header.actions.onSpikesView(view) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.secondaryContainer,
                                selectedLabelColor = colors.onSecondaryContainer,
                            ),
                        )
                    }
                }

                // Modes (hidden on Official Trending and Spikes — Spikes is always spike-ranked)
                AnimatedVisibility(
                    visible = !isOfficial && !ui.spikes,
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
    onOpen: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val feed = ui.feed ?: return
    // GitHub-only surface; callers already gate HN / Spikes out.
    if (ui.spikes || ui.source != Source.GH || feed.langs.isEmpty()) return
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
                        "Language Breakdown",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                    )
                    val count = feed.langs.size
                    Text(
                        if (count > 0) "$count ${if (count == 1) "language" else "languages"} trending · tap to filter" else "Top languages",
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

            LanguageDistribution(
                langs = feed.langs,
                selectedLang = ui.lang,
                expanded = expanded,
                onLang = onLang,
            )
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

    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colors.surfaceContainerHighest),
        ) {
            Row(Modifier.fillMaxSize()) {
                for (lang in langs) {
                    val weight = (lang.count.toFloat() / total).coerceAtLeast(0.01f)
                    val animatedWeight by animateFloatAsState(
                        targetValue = weight,
                        animationSpec = tween(400, easing = FastOutSlowInEasing),
                        label = "langWeight_${lang.name}",
                    )
                    val isSelected = selectedLang.isNotEmpty() && selectedLang.equals(lang.name, ignoreCase = true)
                    val color = langColor(lang.name)
                    val animatedAlpha by animateFloatAsState(
                        targetValue = if (selectedLang.isEmpty() || isSelected) 1.0f else 0.25f,
                        animationSpec = tween(250),
                        label = "langAlpha_${lang.name}",
                    )
                    Box(
                        Modifier
                            .weight(animatedWeight.coerceAtLeast(0.005f))
                            .fillMaxHeight()
                            .padding(horizontal = 0.5.dp)
                            .background(color.copy(alpha = animatedAlpha)),
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
private fun barRange(window: BucketWindow, index: Int): String {
    val start = window.origin + index.toLong() * window.step
    return "${TrendingCore.fmtBarLabel(start, 1)}–${TrendingCore.fmtBarLabel(start + window.step, 1)}"
}

private fun barRange(ui: TrendingUi, index: Int): String = barRange(ui.window, index)

/** Which of [n] equal bars across [width] an x position falls in. */
private fun barAt(x: Float, width: Int, n: Int): Int =
    (x / (width.toFloat() / n)).toInt().coerceIn(0, n - 1)




/** Topic chips for the window on screen. Languages live in Language Breakdown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicsCard(header: HeaderContent, modifier: Modifier = Modifier) {
    val ui = header.ui
    val feed = ui.feed ?: return
    val colors = MaterialTheme.colorScheme
    // A chip in use stays even when it has dropped out of the list.
    val topics = feed.topics.map { it.word to it }.let { list ->
        if (ui.topic.isEmpty() || list.any { it.first.equals(ui.topic, ignoreCase = true) }) {
            list
        } else {
            listOf(ui.topic to null) + list
        }
    }
    if (topics.isEmpty()) return
    val windowLabel = when {
        ui.spikes -> "created ≤${ui.spikesCreatedWithin}h"
        else -> lastPhrase(ui.hours, ui)
    }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.TrendingUp, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Trending topics", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    val fresh = feed.topics.any { it.fresh }
                    Text(
                        if (fresh) "Most talked about in the $windowLabel · ↗ picking up fast"
                        else "Most talked about in the $windowLabel · tap to filter",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            ChipRow(topics) { (word, topic) ->
                TopicChip(
                    label = word,
                    count = topic?.count ?: 0,
                    fresh = topic?.fresh == true,
                    selected = ui.topic.equals(word, ignoreCase = true),
                    onClick = { header.buzz(); header.actions.onTopic(word) },
                )
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
    val countColor = if (selected) colors.onSecondaryContainer else colors.primary
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                buildAnnotatedString {
                    append(label)
                    // Always show how many posts mention this topic.
                    if (count > 0) {
                        withStyle(
                            SpanStyle(
                                color = countColor,
                                fontWeight = FontWeight.Bold,
                            ),
                        ) {
                            append(" · $count")
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

private fun LazyListScope.spikesWordsList(list: ListContent, pad: Modifier) {
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
    item(key = "wordsHead") {
        Column(pad.padding(top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Trending words",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "↑ climbed · ↓ fell since last refresh",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    when {
        feed == null && ui.trouble != null -> item(key = "error") {
            ErrorCard(ui.trouble, onRetry = list.onRetry, modifier = pad)
        }
        feed == null -> items(6) { SkeletonCard(pad.padding(bottom = 10.dp)) }
        feed.topics.isEmpty() && ui.spikesWordTrends.isEmpty() -> item(key = "wordsEmpty") {
            SpikesWordsEmpty(pad)
        }
        else -> {
            val trends = ui.spikesWordTrends.ifEmpty {
                feed.topics.mapIndexed { i, topic ->
                    SpikesWordTrend(topic = topic, rank = i + 1, move = null)
                }
            }
            items(trends, key = { "word:${it.topic.word}" }) { trend ->
                SpikesWordRow(
                    trend = trend,
                    selected = ui.topic.equals(trend.topic.word, ignoreCase = true),
                    onClick = {
                        list.buzz()
                        list.actions.onSpikesWord(trend.topic.word)
                    },
                    modifier = pad.padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SpikesWordRow(
    trend: SpikesWordTrend,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topic = trend.topic
    val colors = MaterialTheme.colorScheme
    val move = trend.move
    val moveColor = when {
        move == null -> colors.onSurfaceVariant
        move > 0 -> Color(0xFF2E7D32)
        move < 0 -> colors.error
        else -> colors.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.secondaryContainer else colors.surfaceContainer,
        tonalElevation = if (selected) 0.dp else 1.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "#${trend.rank}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) colors.onSecondaryContainer else colors.primary,
                modifier = Modifier.width(36.dp),
            )
            Column(
                Modifier.width(52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                when {
                    move == null -> {
                        Text(
                            "NEW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.tertiary,
                        )
                    }
                    move > 0 -> {
                        Icon(AppIcons.TrendingUp, contentDescription = "Up $move", tint = moveColor, modifier = Modifier.size(16.dp))
                        Text("+$move", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = moveColor)
                    }
                    move < 0 -> {
                        Icon(AppIcons.TrendingDown, contentDescription = "Down ${-move}", tint = moveColor, modifier = Modifier.size(16.dp))
                        Text("$move", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = moveColor)
                    }
                    else -> {
                        Text("—", style = MaterialTheme.typography.labelSmall, color = moveColor)
                        Text("same", style = MaterialTheme.typography.labelSmall, color = moveColor)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    topic.word,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = buildList {
                    add(if (topic.count == 1) "1 post" else "${topic.count} posts")
                    if (topic.lift > 0.0) add("${topic.lift}×")
                    if (topic.fresh) add("rising")
                }.joinToString(" · ")
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            WordSpark(
                count = topic.count,
                lift = topic.lift,
                fresh = topic.fresh,
                color = if (selected) colors.onSecondaryContainer else colors.primary,
            )
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = colors.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Tiny decorative spark from post count / lift / fresh — no extra feed data needed. */
@Composable
private fun WordSpark(
    count: Int,
    lift: Double,
    fresh: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .size(width = 40.dp, height = 20.dp)
            .semantics { contentDescription = "word trend spark" },
    ) {
        val n = 7
        val endH = (count.coerceIn(1, 24) / 24f).coerceIn(0.35f, 1f)
        val liftBoost = (lift / 12.0).toFloat().coerceIn(0f, 0.35f)
        val gap = 1.8.dp.toPx()
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1.6.dp.toPx())
        for (i in 0 until n) {
            val t = i / (n - 1f)
            val rise = 0.22f + t * (endH - 0.22f)
            val wave = if (fresh) {
                0.14f * kotlin.math.sin(i * 1.8f + 0.4f)
            } else {
                0.06f * kotlin.math.sin(i * 1.2f)
            }
            val h = (rise + wave + if (i >= n - 2) liftBoost else 0f).coerceIn(0.12f, 1f)
            val barH = size.height * h
            val left = i * (barW + gap)
            drawRoundRect(
                color = color.copy(alpha = 0.30f + 0.55f * h),
                topLeft = Offset(left, size.height - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun SpikesWordsEmpty(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "No repeated words in this window. Try ≤24h or All sources.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
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
            itemsIndexed(
                items = feed.shown,
                key = { _, story -> story.id },
                contentType = { _, _ -> "story" },
            ) { index, story ->
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
                    modifier = pad.padding(bottom = 10.dp).animateItem(
                        fadeInSpec = tween(280),
                        fadeOutSpec = tween(180),
                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
                    ),
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
    val hn = when {
        ui.spikes -> !story.id.startsWith("gh:")
        else -> ui.source == Source.HN
    }
    val isOfficialStory = story.id.startsWith("gh:trending:") ||
        (!ui.spikes && ui.source == Source.GH && ui.ghOfficial)
    val spikeSourceLabel = when {
        !ui.spikes -> null
        isOfficialStory -> "Trending"
        story.id.startsWith("gh:") -> "New"
        else -> "HN"
    }
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
    val meta = remember(story.domain, story.meta, story.t, ui.nowSec / 60) {
        buildList {
            if (hn) {
                if (story.domain.isNotEmpty()) add(story.domain) else add("Hacker News")
                if (story.meta.isNotEmpty()) add("by ${story.meta}")
            } else if (story.meta.isNotEmpty()) {
                add(story.meta)
            }
            if (story.t > 0) add(agoPhrase(story.t, ui.nowSec))
        }.joinToString(" · ")
    }
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

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardPressScale",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        shape = shape,
        color = when {
            isPressed -> colors.surfaceContainerHigh
            read -> colors.surfaceContainerLow
            isCritical -> colors.errorContainer.copy(alpha = 0.14f)
            else -> colors.surfaceContainer
        },
        border = when {
            isPressed -> BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f))
            isCritical -> BorderStroke(1.dp, colors.error.copy(alpha = 0.35f))
            else -> null
        },
        tonalElevation = if (isPressed) 4.dp else if (read) 0.dp else if (isCritical) 2.dp else 1.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .drawBehind {
                    if (barBaseColor != Color.Transparent) {
                        val alpha = pulseTransition?.value ?: 1f
                        drawRect(
                            color = barBaseColor.copy(alpha = barBaseColor.alpha * alpha),
                            topLeft = Offset.Zero,
                            size = Size(barWidth.toPx(), size.height),
                        )
                    }
                }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = colors.primary),
                    onClick = { onOpen(story.url, story.id) },
                    onLongClick = onLongPress,
                    onLongClickLabel = "More actions",
                ),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp + barWidth, end = 14.dp, top = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "#${index + 1}",
                        modifier = Modifier.width(34.dp).padding(top = 1.dp),
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
                val tags = remember(story.flags, story.affectsStack, story.crossSource, story.matched, story.tag, spikeSourceLabel) {
                    buildList {
                        if (spikeSourceLabel != null) {
                            add(StoryTag(spikeSourceLabel, colors.secondaryContainer, colors.onSecondaryContainer))
                        }
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
                        text = "${TrendingCore.fmtCompact(story.score)} ${if (hn) "pts" else "stars"}",
                        label = if (hn) "points" else "stars",
                        tint = accent,
                    )
                    if (hn) {
                        val comments = story.extra.toInt()
                        Stat(
                            icon = AppIcons.Comment,
                            text = if (comments > 0) "$comments ${if (comments == 1) "comment" else "comments"}" else "Discuss",
                            label = "comments",
                            tint = if (read) faded else colors.onSurfaceVariant,
                            onClick = { onOpen(story.talk, story.id) },
                        )
                    } else if (story.extra > 0) {
                        if (isOfficialStory) {
                            // On Spikes, ui.hours is created-within (6/12/24), not the official
                            // daily/weekly/monthly slot. Map the slot actually fetched, or stay neutral.
                            val periodLabel = when {
                                ui.spikes -> when (ui.spikesCreatedWithin) {
                                    24 -> "this month" // Spikes merge uses monthly official slot
                                    else -> "this week" // ≤6h / ≤12h use weekly official slot
                                }
                                ui.hours == 24 -> "this month"
                                ui.hours == 6 -> "this week"
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
                GrowthSpark(
                    story = story,
                    nowSec = ui.nowSec,
                    hours = if (ui.spikes) ui.spikesCreatedWithin else ui.hours,
                    clampToWindow = ui.spikes,
                    color = if (read) faded else accent,
                    trackColor = colors.onSurfaceVariant.copy(alpha = if (read) 0.08f else 0.12f),
                    modifier = Modifier
                        .padding(start = 26.dp, top = 2.dp)
                        .fillMaxWidth(),
                )
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

/**
 * Compact score-over-time chart under each post.
 * Bars = gain per step (spikes light up). Line = cumulative count.
 */
@Composable
private fun GrowthSpark(
    story: Story,
    nowSec: Long,
    hours: Int,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    /** When true (Spikes), axis labels stay inside the ≤6h/≤12h/≤24h window. */
    clampToWindow: Boolean = false,
) {
    val series = remember(
        story.id, story.spark, story.sparkline, story.score, story.extra,
        story.meta, story.heat, story.flags, story.t, nowSec / 60, hours, clampToWindow,
    ) {
        // Spikes: real history when available; otherwise estimate if score/heat is meaningful.
        growthSeries(story, nowSec, allowEstimate = true)
    }
    if (series.cumulative.size < 2 || series.rates.size < 2) return
    // Flat / empty / +0 growth is noise — never show it.
    if (series.delta < 1f) return
    val unit = when {
        story.id.startsWith("gh:trending:") -> "stars"
        story.id.startsWith("gh:") -> "stars"
        else -> "points"
    }
    val deltaText = remember(series.delta) {
        val d = series.delta
        when {
            d >= 1f -> "+${TrendingCore.fmtCompact(d.toDouble())}"
            d > 0f -> "+${"%.1f".format(d)}"
            else -> "+${TrendingCore.fmtCompact(series.cumulative.last().toDouble())}"
        }
    }
    val startLabel = remember(story.id, story.t, hours, clampToWindow, nowSec / 60) {
        sparkTimeStartLabel(story, hours, nowSec, clampToWindow)
    }
    val midLabel = remember(story.id, story.t, hours, clampToWindow, nowSec / 60) {
        sparkTimeMidLabel(story, hours, nowSec, clampToWindow)
    }
    val spikeColor = MaterialTheme.colorScheme.error
    val axisColor = color.copy(alpha = 0.55f)

    Column(
        modifier.semantics {
            contentDescription = "$unit growth chart, $deltaText, ${series.spikeCount} spikes, $startLabel to now"
        },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(trackColor),
        ) {
            val cumulative = series.cumulative
            val rates = series.rates
            val spikes = series.spikeMask
            val n = cumulative.size
            val padX = 6.dp.toPx()
            val padTop = 5.dp.toPx()
            val padBottom = 4.dp.toPx()
            val chartW = (size.width - padX * 2f).coerceAtLeast(1f)
            val chartH = (size.height - padTop - padBottom).coerceAtLeast(1f)
            val maxCum = (cumulative.maxOrNull() ?: 1f).coerceAtLeast(1f)
            val maxRate = (rates.maxOrNull() ?: 1f).coerceAtLeast(1f)
            val gap = (chartW / n) * 0.22f
            val barW = ((chartW / n) - gap).coerceAtLeast(1.8.dp.toPx())
            val stepX = chartW / (n - 1).coerceAtLeast(1)

            fun yCum(v: Float): Float = padTop + chartH * (1f - v / maxCum)
            fun yRate(v: Float): Float = padTop + chartH * (1f - v / maxRate)

            for (frac in listOf(0.25f, 0.5f, 0.75f)) {
                val y = padTop + chartH * (1f - frac)
                drawLine(
                    color = color.copy(alpha = 0.10f),
                    start = Offset(padX, y),
                    end = Offset(size.width - padX, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            // Rate bars — spikes get a hotter fill
            rates.forEachIndexed { i, rate ->
                val xCenter = padX + i * stepX
                val top = yRate(rate)
                val barH = (size.height - padBottom - top).coerceAtLeast(1.dp.toPx())
                val left = xCenter - barW / 2f
                val isSpike = spikes.getOrElse(i) { false }
                val fill = if (isSpike) spikeColor.copy(alpha = 0.78f) else color.copy(alpha = 0.28f + 0.35f * (rate / maxRate))
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(left, top),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                )
            }

            // Cumulative line + soft fill
            val line = Path().apply {
                cumulative.forEachIndexed { i, v ->
                    val x = padX + i * stepX
                    val y = yCum(v)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(padX + (n - 1) * stepX, size.height - padBottom)
                lineTo(padX, size.height - padBottom)
                close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0.02f)),
                    startY = padTop,
                    endY = size.height,
                ),
            )
            drawPath(
                line,
                color = color.copy(alpha = 0.95f),
                style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            cumulative.forEachIndexed { i, v ->
                val x = padX + i * stepX
                val y = yCum(v)
                val isSpike = spikes.getOrElse(i) { false }
                val r = when {
                    i == n - 1 -> 2.8.dp.toPx()
                    isSpike -> 2.4.dp.toPx()
                    else -> 1.3.dp.toPx()
                }
                drawCircle(
                    color = if (isSpike) spikeColor else color.copy(alpha = if (i == n - 1) 1f else 0.55f),
                    radius = r,
                    center = Offset(x, y),
                )
            }
        }

        Column(
            Modifier.widthIn(min = 42.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                deltaText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
            )
            when {
                !series.measured && series.spikeCount > 0 -> Text(
                    "est. · ${series.spikeCount} spike${if (series.spikeCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.55f),
                    maxLines = 1,
                )
                series.spikeCount > 0 -> Text(
                    "${series.spikeCount} spike${if (series.spikeCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = spikeColor.copy(alpha = 0.9f),
                    maxLines = 1,
                )
                !series.measured -> Text(
                    "est.",
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.45f),
                    maxLines = 1,
                )
            }
        }
    }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = 50.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = axisColor, maxLines = 1)
            if (midLabel.isNotEmpty()) {
                Text(midLabel, style = MaterialTheme.typography.labelSmall, color = axisColor.copy(alpha = 0.75f), maxLines = 1)
            }
            Text("now", style = MaterialTheme.typography.labelSmall, color = axisColor, maxLines = 1)
        }
    }
}

/** Left-edge time under a growth chart. */
private fun sparkTimeStartLabel(
    story: Story,
    hours: Int,
    nowSec: Long,
    clampToWindow: Boolean,
): String {
    if (clampToWindow) {
        // Spikes ≤6h / ≤12h / ≤24h — axis matches the filter, not full post age.
        return when (hours) {
            6 -> "6h"
            12 -> "12h"
            24 -> "24h"
            else -> "${hours}h"
        }
    }
    if (story.id.startsWith("gh:trending:")) {
        return when (hours) {
            24 -> "month"
            6 -> "week"
            else -> "today"
        }
    }
    if (story.t > 0L) {
        return agoPhrase(story.t, nowSec).replace(" ago", "")
    }
    return "start"
}

/** Optional mid-axis mark under a growth chart. */
private fun sparkTimeMidLabel(
    story: Story,
    hours: Int,
    nowSec: Long,
    clampToWindow: Boolean,
): String {
    if (clampToWindow) {
        return when (hours) {
            6 -> "3h"
            12 -> "6h"
            24 -> "12h"
            else -> ""
        }
    }
    if (story.id.startsWith("gh:trending:")) {
        return when (hours) {
            24 -> "~2w"
            6 -> "~3d"
            else -> "~12h"
        }
    }
    if (story.t <= 0L) return ""
    val mid = story.t + ((nowSec - story.t) / 2).coerceAtLeast(0L)
    if (mid <= story.t || mid >= nowSec) return ""
    return agoPhrase(mid, nowSec).replace(" ago", "")
}

private data class GrowthSeries(
    val cumulative: List<Float>,
    val rates: List<Float>,
    val spikeMask: List<Boolean>,
    val delta: Float,
    val measured: Boolean,
) {
    val spikeCount: Int get() = spikeMask.count { it }
}

/**
 * Prefer real spark history; otherwise estimate when score/heat/period gain is meaningful.
 * Spikes uses estimates so every post can show a chart until history builds up.
 */
private fun growthSeries(story: Story, nowSec: Long, allowEstimate: Boolean = true): GrowthSeries {
    if (story.id.startsWith("gh:trending:")) {
        if (!allowEstimate) return GrowthSeries(emptyList(), emptyList(), emptyList(), 0f, measured = false)
        // Need period stars or total stars to draw anything useful.
        if (story.extra < 1.0 && story.score < 1.0 && story.heat < 1.0) {
            return GrowthSeries(emptyList(), emptyList(), emptyList(), 0f, measured = false)
        }
        return trendingGrowthSeries(story)
    }

    fun fromCumulative(pts: List<Float>, measured: Boolean): GrowthSeries {
        if (pts.size < 2) return GrowthSeries(emptyList(), emptyList(), emptyList(), 0f, measured)
        val rates = List(pts.size) { i ->
            if (i == 0) pts[0].coerceAtLeast(0f)
            else (pts[i] - pts[i - 1]).coerceAtLeast(0f)
        }
        return withSpikes(pts, rates, measured)
    }

    val gain = story.score.toFloat().coerceAtLeast(0f)

    // Trust history only when the span is a meaningful share of the score.
    // Recent-only samples (flat high line, tiny tip delta) fall through to estimate.
    if (story.spark.size >= 2 && gain > 0f) {
        val pts = story.spark.map { it.toFloat().coerceAtLeast(0f) }
        val lo = pts.minOrNull() ?: 0f
        val hi = pts.maxOrNull() ?: 0f
        val span = (hi - lo).coerceAtLeast(0f)
        val coversGrowth = span >= maxOf(5f, gain * 0.15f) && lo <= gain * 0.5f
        if (coversGrowth) return fromCumulative(pts, measured = true)
    }

    if (!allowEstimate) {
        return GrowthSeries(emptyList(), emptyList(), emptyList(), 0f, measured = false)
    }

    // Meaningful enough to draw: points/stars or heat.
    if (gain < 1f && story.heat < 1.0 && story.extra < 1.0) {
        return GrowthSeries(emptyList(), emptyList(), emptyList(), 0f, measured = false)
    }

    val ageSec = if (story.t > 0L) (nowSec - story.t).coerceAtLeast(0L) else 0L
    // Unknown age: still chart from score using a short synthetic window.
    if (story.t <= 0L || ageSec < 60L) {
        if (gain < 1f) return GrowthSeries(emptyList(), emptyList(), emptyList(), 0f, measured = false)
        return scaleRates(syntheticRates(story, ageH = 6.0), gain, measured = false)
    }

    val ageH = (ageSec / 3600.0).coerceAtLeast(0.15)
    return scaleRates(syntheticRates(story, ageH), gain, measured = false)
}

/** Rate profile for estimated growth charts (HN / New Repos). */
private fun syntheticRates(story: Story, ageH: Double): FloatArray {
    val bars = 14
    val accelerating = story.has(Flags.ACCELERATING) || story.has(Flags.BREAKOUT) ||
        (story.has(Flags.RISING) && story.heat >= 20.0)
    val heat = story.heat.toFloat().coerceAtLeast(0f)
    val seed = storyFingerprint(story.id)

    return FloatArray(bars) { i ->
        val t = i.toFloat() / (bars - 1).coerceAtLeast(1)
        val base = if (ageH >= 12.0) {
            (1.15f - t).coerceAtLeast(0.15f)
        } else {
            0.45f + 0.35f * (1f - kotlin.math.abs(t - (0.30f + seed.frac(0) * 0.15f)))
        }
        var rate = base

        val spikeCenter = when {
            accelerating -> 0.72f + seed.frac(1) * 0.14f
            heat >= 20f -> 0.55f + seed.frac(1) * 0.18f
            heat >= 8f -> 0.40f + seed.frac(1) * 0.20f
            else -> 0.28f + seed.frac(1) * 0.35f
        }
        val width = (if (accelerating) 0.09f else 0.13f) + seed.frac(2) * 0.04f
        val dist = kotlin.math.abs(t - spikeCenter) / width
        if (dist < 1.8f) {
            val amp = when {
                story.has(Flags.BREAKOUT) || accelerating -> 2.8f + seed.frac(3) * 1.0f
                story.has(Flags.RISING) || heat >= 20f -> 2.0f + seed.frac(3) * 0.9f
                else -> 1.1f + seed.frac(3) * 0.8f
            }
            rate += amp * kotlin.math.exp(-dist * dist * 2.2f)
        }

        val bump2Center = 0.14f + seed.frac(4) * 0.22f
        val bump2 = kotlin.math.abs(t - bump2Center) / (0.08f + seed.frac(5) * 0.04f)
        if (bump2 < 1.6f) {
            rate += (0.40f + seed.frac(6) * 0.45f) * kotlin.math.exp(-bump2 * bump2 * 2.0f)
        }

        rate += seed.signed(i) * 0.06f
        rate.coerceAtLeast(0.05f)
    }
}

/**
 * GitHub Trending has no post time — only total stars + period gain.
 * Pick a distinct silhouette per repo from burstiness + id so charts
 * are not the same curve scaled up.
 */
private fun trendingGrowthSeries(story: Story): GrowthSeries {
    val period = story.extra.toFloat().coerceAtLeast(0f)
    val total = story.score.toFloat().coerceAtLeast(0f)
    val gain = when {
        period > 0f -> period
        total > 0f -> total.coerceAtMost(500f)
        else -> 0f
    }
    if (gain <= 0f) return GrowthSeries(emptyList(), emptyList(), emptyList(), 0f, measured = false)

    val seed = storyFingerprint(story.id + "|" + story.meta + "|" + period.toInt() + "|" + total.toInt())
    val burstiness = if (total > 0f) (period / total).coerceIn(0f, 2f) else 0.4f
    // 5 clearly different silhouettes, chosen by data + id.
    val profile = when {
        burstiness >= 0.45f -> 0 // late rocket
        burstiness >= 0.18f -> 1 // mid surge
        burstiness >= 0.06f -> 2 // early then taper
        period >= 5000f -> 3 // double hump (big movers)
        else -> 4 // steady grind
    }.let { base -> (base + seed.int(0, 5)) % 5 }

    val bars = 14
    val rates = FloatArray(bars) { i ->
        val t = i.toFloat() / (bars - 1).coerceAtLeast(1)
        var rate = when (profile) {
            0 -> { // quiet then sharp late spike
                val spike = kotlin.math.exp(-((t - (0.78f + seed.frac(1) * 0.12f)) / 0.07f).let { it * it } * 2.4f)
                0.12f + 0.15f * t + (2.8f + seed.frac(2) * 1.6f) * spike
            }
            1 -> { // mid-period surge
                val c = 0.42f + seed.frac(1) * 0.22f
                val spike = kotlin.math.exp(-((t - c) / 0.10f).let { it * it } * 2.0f)
                0.25f + 0.35f * kotlin.math.sin(t * Math.PI.toFloat()) + (2.0f + seed.frac(2)) * spike
            }
            2 -> { // early rush, then cools
                val c = 0.16f + seed.frac(1) * 0.18f
                val spike = kotlin.math.exp(-((t - c) / 0.09f).let { it * it } * 2.0f)
                0.20f + (2.2f + seed.frac(2)) * spike + 0.35f * (1f - t)
            }
            3 -> { // two clear peaks
                val c1 = 0.22f + seed.frac(1) * 0.15f
                val c2 = 0.68f + seed.frac(2) * 0.18f
                val s1 = kotlin.math.exp(-((t - c1) / 0.08f).let { it * it } * 2.2f)
                val s2 = kotlin.math.exp(-((t - c2) / 0.08f).let { it * it } * 2.2f)
                0.18f + (1.6f + seed.frac(3)) * s1 + (1.9f + seed.frac(4)) * s2
            }
            else -> { // steady with mild wobble
                0.55f + 0.25f * t + 0.20f * kotlin.math.sin((t * 5.5f + seed.frac(1) * 6.28f))
            }
        }
        rate += seed.signed(i) * 0.12f
        rate.coerceAtLeast(0.05f)
    }

    return scaleRates(rates, gain, measured = false)
}

private fun scaleRates(rates: FloatArray, gain: Float, measured: Boolean): GrowthSeries {
    val rateSum = rates.sum().coerceAtLeast(1e-3f)
    val scaledRates = rates.map { it / rateSum * gain }
    // Start cumulative at 0 so the side label matches full score / period gain.
    var running = 0f
    val cumulative = ArrayList<Float>(scaledRates.size + 1).apply {
        add(0f)
        scaledRates.forEach { step ->
            running += step
            add(running)
        }
    }
    val alignedRates = List(cumulative.size) { i ->
        if (i == 0) 0f else scaledRates[i - 1]
    }
    return withSpikes(cumulative, alignedRates, measured)
}
/** Stable mix of a string for deterministic chart fingerprints. */
private class StorySeed(private val bits: Int) {
    fun frac(lane: Int): Float {
        var x = bits xor (lane + 1) * 0x9e3779b9.toInt()
        x = x xor (x ushr 16)
        x *= 0x85ebca6b.toInt()
        x = x xor (x ushr 13)
        x *= 0xc2b2ae35.toInt()
        x = x xor (x ushr 16)
        return (x and 0xffff) / 65535f
    }

    fun signed(lane: Int): Float = frac(lane) * 2f - 1f

    fun int(lane: Int, bound: Int): Int {
        if (bound <= 1) return 0
        return (frac(lane) * bound).toInt().coerceIn(0, bound - 1)
    }
}

private fun storyFingerprint(key: String): StorySeed {
    var h = -2128831035
    for (ch in key) {
        h = (h xor ch.code) * 16777619
    }
    return StorySeed(h)
}

private fun withSpikes(cumulative: List<Float>, rates: List<Float>, measured: Boolean): GrowthSeries {
    val positive = rates.filter { it > 0f }
    val median = if (positive.isEmpty()) 0f else {
        val sorted = positive.sorted()
        sorted[sorted.size / 2]
    }
    val threshold = when {
        median <= 0f -> (rates.maxOrNull() ?: 0f) * 0.7f
        else -> median * 1.65f
    }
    val spikes = rates.map { it >= threshold && it > 0f && threshold > 0f }
    // Keep at least the tallest bar marked when heat-driven series has one clear peak.
    val maxRate = rates.maxOrNull() ?: 0f
    val spikeMask = if (spikes.none { it } && maxRate > 0f) {
        rates.map { it >= maxRate * 0.92f }
    } else {
        spikes
    }
    val delta = (cumulative.last() - cumulative.first()).coerceAtLeast(0f).let {
        if (it > 0f) it else cumulative.last().coerceAtLeast(0f)
    }
    return GrowthSeries(cumulative, rates, spikeMask, delta, measured)
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
    if (ui.spikes) return "Recent high spikes"
    if (!ui.spikes && ui.source == Source.GH && ui.ghOfficial) {
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
    if (ui.spikes) {
        val age = "created ≤${ui.spikesCreatedWithin}h"
        val src = when (ui.spikesSource) {
            SpikesSource.ALL -> "all sources"
            SpikesSource.HN -> "Hacker News"
            SpikesSource.TRENDING -> "GitHub Trending"
            SpikesSource.NEW -> "New Repos"
        }
        return "$age · $src · ranked by spike"
    }
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
    val what = when {
        ui.spikes -> if (feed.matching == 1) "spike" else "spikes"
        ui.source == Source.HN -> if (feed.total == 1L) "story" else "stories"
        ui.ghOfficial -> "trending repos"
        else -> "new repos"
    }
    val fresh = if (ui.loading) "Updating…" else "Updated ${agoPhrase(feed.fetched, ui.nowSec)}"
    return "$count $what · $fresh"
}

/** "just now" or "5m ago". */
private fun agoPhrase(t: Long, now: Long): String {
    val ago = TrendingCore.fmtAgo(t, now)
    return if (ago == "now") "just now" else "$ago ago"
}
