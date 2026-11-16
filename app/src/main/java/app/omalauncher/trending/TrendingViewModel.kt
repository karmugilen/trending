package app.omalauncher.trending

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.omalauncher.trending.core.FeedOptions
import app.omalauncher.trending.core.GhQuery
import app.omalauncher.trending.core.TrendingCore
import app.omalauncher.trending.data.FeedCache
import app.omalauncher.trending.data.FeedClient
import app.omalauncher.trending.data.HttpStatusException
import app.omalauncher.trending.data.SettingsStore
import app.omalauncher.trending.logic.BucketWindow
import app.omalauncher.trending.logic.Counts
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
import app.omalauncher.trending.logic.normalizeHours
import app.omalauncher.trending.notify.AlertCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class TrendingUi(
    val source: Source,
    val mode: Mode,
    val hours: Int,
    val seen: List<String>,
    /** A download for what is on screen is running. */
    val loading: Boolean,
    val trouble: String?,
    val olderLoading: Boolean,
    /** Last good feed for this source and window, kept on screen through refreshes and errors. */
    val feed: Feed?,
    val window: BucketWindow,
    val nowSec: Long,
    val query: String,
    val limit: Int,
    val filter: Filter = Filter.ALL,
    /** Topic chip in use, or empty. */
    val topic: String = "",
    /** GitHub language chip in use, or empty. */
    val lang: String = "",
    /** Chart bar the list is narrowed to, or -1. */
    val bar: Int = -1,
    val ghOfficial: Boolean = false,
    /** Merged HN + Trending + New Repos, ranked by recent spike strength. */
    val spikes: Boolean = false,
    /** On Spikes: story list vs trending/repeated words. */
    val spikesView: SpikesView = SpikesView.STORIES,
    /** Ranked words with up/down moves for the Spikes Words board. */
    val spikesWordTrends: List<SpikesWordTrend> = emptyList(),
    /** Spikes only: keep posts created within this many hours (6 / 12 / 24). */
    val spikesCreatedWithin: Int = 24,
    /** Spikes only: All / HN / Trending / New. */
    val spikesSource: SpikesSource = SpikesSource.ALL,
) {
    /** Anything beyond the plain list is narrowing it. */
    val narrowed: Boolean get() = filter != Filter.ALL || topic.isNotEmpty() || lang.isNotEmpty() || bar >= 0
}

/**
 * Switching sources and windows never waits on the network:
 * - Hacker News is one 24h download. Every shorter window is cut from it in Rust.
 * - GitHub's list is a top 40 per window, so each window has its own download.
 *   They are fetched in the background, and until one lands, a wider window
 *   stands in as a preview.
 * - Every slot is also on disk, so even a cold start switches instantly.
 */
class TrendingViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsStore.get(app)
    private val client = FeedClient.shared
    private val cache = FeedCache(app)
    private val alerts = AlertCenter.get(app)
    private var generation = 0
    private var loadJob: Job? = null
    private var loadingSlot: String? = null
    private var tickJob: Job? = null
    private var prefetchJob: Job? = null
    private val threads = HashMap<String, Pair<Long, List<Story>>>()

    private val _ui = MutableStateFlow(initial())
    val ui: StateFlow<TrendingUi> = _ui.asStateFlow()

    private val _briefing = MutableStateFlow<app.omalauncher.trending.logic.Briefing?>(null)
    val briefing: StateFlow<app.omalauncher.trending.logic.Briefing?> = _briefing.asStateFlow()

    /** Per-tab hours/mode/filters so switching Hacker News / Trending / New Repos restores them. */
    private val tabMemory = HashMap<String, TabMemory>()

    /** Last Spikes Words order (lowercase), used to show rank up/down between refreshes. */
    private var lastSpikesWordOrder: List<String> = emptyList()

    /** Merged+ranked Spikes list (before limit); reused when slot fetches/filters are unchanged. */
    private var spikesMergeCache: SpikesMergeCache? = null

    fun loadBriefing(overrideSinceSec: Long? = null) {
        val s = settings.value
        val nowSec = System.currentTimeMillis() / 1000L
        val hnSlot = slotFor(Source.HN, 24)
        val ghSlot = slotFor(Source.GH, 24, s.ghOfficial)
        val since = overrideSinceSec ?: s.lastBriefingSeenSec
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = TrendingCore.briefing(
                    sinceSec = since,
                    nowSec = nowSec,
                    hnSlot = hnSlot,
                    ghSlot = ghSlot,
                    stackDependencies = s.stackDependencies,
                    interests = s.interests,
                    sensitivity = s.sensitivity,
                )
                _briefing.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("TrendingViewModel", "could not build the briefing", e)
            }
        }
    }

    fun markBriefingCaughtUp() {
        val nowSec = System.currentTimeMillis() / 1000L
        settings.update { it.copy(lastBriefingSeenSec = nowSec) }
        val briefingIds = _briefing.value?.let { b ->
            (b.affectsYou + b.brokeOut + b.crossSource + b.forYou).map { it.id }
        } ?: emptyList()
        if (briefingIds.isNotEmpty()) {
            val current = _ui.value.seen
            val next = (briefingIds + current).distinct().take(300)
            setSeen(next)
        }
        loadBriefing(overrideSinceSec = nowSec)
    }

    init {
        viewModelScope.launch {
            // Spam hiding, interests and sensitivity change the rows, not the data.
            settings.flow.map { Triple(it.hideSpam, it.interests, it.sensitivity) }
                .distinctUntilChanged().drop(1).collect { render() }
        }
        viewModelScope.launch {
            // Current view first, so it draws as soon as possible.
            withContext(Dispatchers.IO) {
                cache.prune(ALL_SLOTS)
                cache.restoreHistory()
                cache.restore(slotFor(_ui.value.source, _ui.value.hours, _ui.value.ghOfficial))
            }
            render(loading = needsNetwork(FRESH_SEC))
            withContext(Dispatchers.IO) { ALL_SLOTS.forEach { cache.restore(it) } }
            render()
            if (needsNetwork(FRESH_SEC)) refresh() else prefetch()
        }
    }

    /** Called while the screen is visible. Keeps ages fresh and the feed current. */
    fun onStart() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
        // Coming back to the app after a while refreshes old data.
        if (needsNetwork(AUTO_REFRESH_SEC)) refresh()
    }

    /** Called when the screen is hidden. No polling in the background; the worker does that. */
    fun onStop() {
        tickJob?.cancel()
        tickJob = null
        prefetchJob?.cancel()
    }

    fun selectSource(source: Source) {
        selectFeedTab(source, source == Source.GH && _ui.value.ghOfficial)
    }

    /** Open the Spikes tab: all three feeds merged, ranked by recent spike. */
    fun selectSpikes() {
        selectFeedTab(Source.HN, ghOfficial = false, spikes = true)
    }

    /**
     * Switch the top feed tab (Hacker News / Trending / New Repos / Spikes).
     * Remembers hours, mode, filters, topic, language and chart-bar for each tab
     * and restores them when you come back.
     */
    fun selectFeedTab(source: Source, ghOfficial: Boolean, spikes: Boolean = false) {
        val official = !spikes && source == Source.GH && ghOfficial
        val cur = _ui.value
        val fromKey = tabKey(cur.source, cur.ghOfficial, cur.spikes)
        val toKey = tabKey(source, official, spikes)
        if (fromKey == toKey) return

        tabMemory[fromKey] = TabMemory(
            hours = cur.hours,
            mode = cur.mode,
            filter = cur.filter,
            topic = cur.topic,
            lang = cur.lang,
            bar = cur.bar,
            spikesView = cur.spikesView,
            spikesCreatedWithin = cur.spikesCreatedWithin,
            spikesSource = cur.spikesSource,
        )

        val saved = tabMemory[toKey]
        val spikesCreatedWithin = if (spikes) {
            normalizeSpikesCreatedWithin(saved?.spikesCreatedWithin ?: cur.spikesCreatedWithin)
        } else {
            cur.spikesCreatedWithin
        }
        val hours = when {
            spikes -> spikesCreatedWithin
            else -> normalizeTabHours(official, saved?.hours ?: cur.hours)
        }
        val mode = if (spikes) Mode.RISING else (saved?.mode ?: cur.mode)
        val filter = if (spikes) {
            saved?.filter ?: Filter.ALL
        } else {
            normalizeTabFilter(official, saved?.filter ?: cur.filter)
        }
        val topic = saved?.topic.orEmpty()
        val lang = if (!spikes && source == Source.GH) saved?.lang.orEmpty() else ""
        val bar = if (spikes) -1 else (saved?.bar ?: -1)
        val spikesView = if (spikes) saved?.spikesView ?: SpikesView.STORIES else SpikesView.STORIES
        val spikesSource = if (spikes) saved?.spikesSource ?: SpikesSource.ALL else SpikesSource.ALL

        if (!spikes) {
            settings.update {
                it.copy(source = source, ghOfficial = official, hours = hours, mode = mode)
            }
        } else {
            settings.update { it.copy(hours = hours, mode = Mode.RISING) }
        }
        _ui.value = cur.copy(
            source = if (spikes) cur.source else source,
            ghOfficial = if (spikes) cur.ghOfficial else official,
            spikes = spikes,
            hours = hours,
            mode = mode,
            filter = filter,
            topic = topic,
            lang = lang,
            bar = bar,
            spikesView = spikesView,
            spikesCreatedWithin = spikesCreatedWithin,
            spikesSource = spikesSource,
            trouble = null,
            olderLoading = false,
            limit = PAGE,
        )
        switched()
    }

    fun selectSpikesView(view: SpikesView) {
        if (!_ui.value.spikes || view == _ui.value.spikesView) return
        _ui.value = _ui.value.copy(spikesView = view)
        rememberCurrentTab()
    }

    /** Spikes: only posts created within the last 6 / 12 / 24 hours. */
    fun selectSpikesCreatedWithin(hours: Int) {
        if (!_ui.value.spikes) return
        val next = normalizeSpikesCreatedWithin(hours)
        if (next == _ui.value.spikesCreatedWithin) return
        lastSpikesWordOrder = emptyList()
        settings.update { it.copy(hours = next) }
        _ui.value = _ui.value.copy(
            spikesCreatedWithin = next,
            hours = next,
            trouble = null,
            olderLoading = false,
            limit = PAGE,
            bar = -1,
        )
        rememberCurrentTab()
        switched()
    }

    /** Spikes: All / HN / Trending / New source filter. */
    fun selectSpikesSource(source: SpikesSource) {
        if (!_ui.value.spikes || source == _ui.value.spikesSource) return
        _ui.value = _ui.value.copy(spikesSource = source, limit = PAGE)
        rememberCurrentTab()
        render()
    }

    /** Filter spikes stories by [word] and show the story list. */
    fun pickSpikesWord(word: String) {
        if (!_ui.value.spikes || word.isEmpty()) return
        _ui.value = _ui.value.copy(topic = word, spikesView = SpikesView.STORIES, limit = PAGE)
        rememberCurrentTab()
        render()
    }

    fun selectMode(mode: Mode) {
        if (mode == _ui.value.mode) return
        settings.update { it.copy(mode = mode) }
        _ui.value = _ui.value.copy(mode = mode, limit = PAGE)
        rememberCurrentTab()
        render()
    }

    fun selectHours(hours: Int) {
        if (hours != 1 && hours != 6 && hours != 12 && hours != 24) return
        val next = normalizeHours(hours)
        if (next == _ui.value.hours) return
        settings.update { it.copy(hours = next) }
        // Bars mean different times in another window, so that choice goes.
        // Word ranks are not comparable across windows.
        if (_ui.value.spikes) lastSpikesWordOrder = emptyList()
        _ui.value = _ui.value.copy(hours = next, trouble = null, olderLoading = false, limit = PAGE, bar = -1)
        rememberCurrentTab()
        switched()
    }

    fun selectGhOfficial(official: Boolean) {
        selectFeedTab(Source.GH, official)
    }

    fun setQuery(query: String) {
        if (query == _ui.value.query) return
        _ui.value = _ui.value.copy(query = query, limit = PAGE)
        render()
    }

    fun selectFilter(filter: Filter) {
        if (filter == _ui.value.filter) return
        _ui.value = _ui.value.copy(filter = filter, limit = PAGE)
        rememberCurrentTab()
        render()
    }

    /** Tapping the chip in use clears it. */
    fun toggleTopic(word: String) {
        _ui.value = _ui.value.copy(topic = if (_ui.value.topic == word) "" else word, limit = PAGE)
        rememberCurrentTab()
        render()
    }

    fun toggleLang(name: String) {
        _ui.value = _ui.value.copy(lang = if (_ui.value.lang == name) "" else name, limit = PAGE)
        rememberCurrentTab()
        render()
    }

    /** Narrow the list to one chart bar; the same bar again shows the whole window. */
    fun toggleBar(index: Int) {
        _ui.value = _ui.value.copy(bar = if (_ui.value.bar == index) -1 else index, limit = PAGE)
        rememberCurrentTab()
        render()
    }

    fun clearNarrowing() {
        _ui.value = _ui.value.copy(filter = Filter.ALL, topic = "", lang = "", bar = -1, limit = PAGE)
        rememberCurrentTab()
        render()
    }

    /**
     * Hacker News threads about [story]'s link, best first. For a GitHub repo
     * that is every thread linking into it. Answers are kept for a while.
     */
    suspend fun discussions(story: Story): List<Story> {
        val now = nowSec()
        threads[story.id]?.let { (at, list) -> if (now - at < THREADS_TTL_SEC) return list }
        val list = withContext(Dispatchers.IO) {
            val body = client.fetch(TrendingCore.discussUrl(story.url))
            TrendingCore.discussions(body, story.id)
        }
        threads[story.id] = now to list
        return list
    }

    fun showMore() {
        _ui.value = _ui.value.copy(limit = _ui.value.limit + PAGE)
        render()
    }

    fun markSeen(id: String) {
        val current = _ui.value.seen
        if (id in current) return
        setSeen((listOf(id) + current).take(200))
    }

    fun markUnseen(id: String) {
        setSeen(_ui.value.seen - id)
    }

    /** Clears every mark and returns them, so the screen can offer undo. */
    fun clearSeen(): List<String> {
        val before = _ui.value.seen
        setSeen(emptyList())
        return before
    }

    fun restoreSeen(marks: List<String>) = setSeen(marks)

    private fun setSeen(next: List<String>) {
        settings.update { it.copy(seen = next) }
        _ui.value = _ui.value.copy(seen = next)
        // Unread counts and the unread filter follow the marks.
        render()
    }

    /** Download what is on screen again, keeping the current rows until the new ones land. */
    fun refresh() = refresh(force = true)

    private fun refresh(force: Boolean) {
        val cur = _ui.value
        if (cur.spikes) {
            refreshSpikes(force)
            return
        }
        val source = cur.source
        val hours = cur.hours
        val slot = slotFor(source, hours, cur.ghOfficial)
        // Hacker News windows share one download; let a running one finish.
        if (!force && loadJob?.isActive == true && loadingSlot == slot) return
        loadJob?.cancel()
        val gen = ++generation
        loadingSlot = slot
        loadJob = viewModelScope.launch {
            render(loading = true, trouble = null, older = false)
            try {
                download(source, hours) { gen == generation }
                if (gen != generation) return@launch
                render(loading = false, trouble = null, older = false)
                afterDownload(slot)
                prefetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen != generation) return@launch
                render(loading = false, trouble = troubleFor(source, e), older = false)
            }
        }
    }

    /** Pull HN + Official Trending + New Repos so the Spikes merge is complete. */
    private fun refreshSpikes(force: Boolean) {
        val cur = _ui.value
        val created = normalizeSpikesCreatedWithin(cur.spikesCreatedWithin)
        val offHours = when (created) {
            24 -> 24
            else -> 6
        }
        if (!force && loadJob?.isActive == true && loadingSlot == "spikes") return
        loadJob?.cancel()
        val gen = ++generation
        loadingSlot = "spikes"
        loadJob = viewModelScope.launch {
            render(loading = true, trouble = null, older = false)
            var lastError: Exception? = null
            suspend fun one(source: Source, hours: Int, official: Boolean) {
                if (gen != generation) return
                val slot = slotFor(source, hours, official)
                if (!force && !slotNeedsNetwork(source, hours, FRESH_SEC, official = official)) return
                try {
                    download(source, hours, official = official, onScreen = { gen == generation })
                    afterDownload(slot)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                }
            }
            try {
                one(Source.HN, 24, false)
                one(Source.GH, offHours, true)
                one(Source.GH, created, false)
                if (gen != generation) return@launch
                render(loading = false, trouble = lastError?.let { troubleFor(Source.HN, it) }, older = false)
                prefetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen != generation) return@launch
                render(loading = false, trouble = troubleFor(Source.HN, e), older = false)
            }
        }
    }

    /** Show the new choice at once from memory, then top it up from the network if needed. */
    private fun switched() {
        val slot = slotFor(_ui.value.source, _ui.value.hours, _ui.value.ghOfficial)
        val busy = loadJob?.isActive == true && loadingSlot == slot
        render(loading = busy || needsNetwork(FRESH_SEC), trouble = null, older = false)
        if (needsNetwork(FRESH_SEC)) refresh(force = false)
    }

    /**
     * Fill every other slot quietly, so the next switch is instant too. GitHub
     * search allows 10 requests a minute, so these are spaced out.
     */
    private fun prefetch() {
        if (prefetchJob?.isActive == true) return
        prefetchJob = viewModelScope.launch {
            val wanted = buildList {
                add(Triple(Source.HN, 24, false))
                for (h in listOf(1, 6, 12, 24)) add(Triple(Source.GH, h, false))
                for (h in listOf(1, 6, 24)) add(Triple(Source.GH, h, true))
            }
            for ((source, hours, official) in wanted) {
                val slot = slotFor(source, hours, official)
                if (slot == loadingSlot && loadJob?.isActive == true) continue
                if (!slotNeedsNetwork(source, hours, PREFETCH_MAX_AGE_SEC, official)) continue
                try {
                    download(source, hours, official = official, onScreen = { false })
                    afterDownload(slot)
                    val ui = _ui.value
                    if (ui.spikes || slotFor(ui.source, ui.hours, ui.ghOfficial) == slot) render()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A background fill failing is fine; the user's own refresh will say why.
                }
                delay(PREFETCH_GAP_MS)
            }
        }
    }

    private suspend fun download(
        source: Source,
        hours: Int,
        official: Boolean = settings.value.ghOfficial,
        onScreen: () -> Boolean,
    ) {
        val slot = slotFor(source, hours, official)
        val origin = TrendingCore.window(nowSec(), fetchHours(source, hours)).origin
        if (source == Source.GH) {
            val url = if (official) {
                val since = when (hours) {
                    24 -> "monthly"
                    6 -> "weekly"
                    else -> "daily"
                }
                "https://github.com/trending?since=$since"
            } else {
                TrendingCore.ghUrl(GhQuery.NEW_REPOS, origin)
            }
            val page = client.fetch(url)
            withContext(Dispatchers.Default) {
                TrendingCore.ingest(slot, Source.GH, origin, nowSec(), page, append = false)
            }
            return
        }
        val page0 = client.fetch(TrendingCore.hnUrl(origin))
        var chunk = withContext(Dispatchers.Default) {
            TrendingCore.ingest(slot, Source.HN, origin, nowSec(), page0, append = false)
        }
        // Algolia answers at most 1000 hits per query. A full page means older
        // stories are missing, so ask again for the ones before the oldest seen.
        var more = chunk.count >= HN_PAGE && chunk.oldest > origin
        if (onScreen()) render(loading = false, trouble = null, older = more)
        var rounds = 0
        while (more && rounds < HN_MAX_EXTRA_PAGES) {
            val before = chunk.oldest
            val page = try {
                client.fetch(TrendingCore.hnUrl(origin, before))
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                break // The newest rows are enough to draw.
            }
            chunk = withContext(Dispatchers.Default) {
                TrendingCore.ingest(slot, Source.HN, origin, nowSec(), page, append = true)
            }
            rounds++
            more = chunk.count >= HN_PAGE && chunk.oldest in (origin + 1) until before
            if (onScreen()) render(loading = false, trouble = null, older = more)
        }
    }

    /** Disk cache and alert check run outside the load job so a new refresh cannot cut them off. */
    private fun afterDownload(slot: String) {
        viewModelScope.launch(Dispatchers.IO) {
            cache.save(slot)
            cache.saveHistory()
            alerts.check(listOf(slot))
            try {
                app.omalauncher.trending.widget.BreakoutWidgetProvider.refreshAll(getApplication())
            } catch (_: Exception) {
                // Home-screen widget update is best-effort.
            }
        }
    }

    private fun needsNetwork(maxAgeSec: Long): Boolean {
        val cur = _ui.value
        if (cur.spikes) {
            val offHours = when (cur.hours) {
                24 -> 24
                12, 6 -> 6
                else -> 1
            }
            return slotNeedsNetwork(Source.HN, 24, maxAgeSec, official = false) ||
                slotNeedsNetwork(Source.GH, offHours, maxAgeSec, official = true) ||
                slotNeedsNetwork(Source.GH, cur.hours, maxAgeSec, official = false)
        }
        return slotNeedsNetwork(cur.source, cur.hours, maxAgeSec, official = cur.ghOfficial)
    }

    /** True when the slot is missing, from an older window, or older than [maxAgeSec]. */
    private fun slotNeedsNetwork(
        source: Source,
        hours: Int,
        maxAgeSec: Long,
        official: Boolean = settings.value.ghOfficial,
    ): Boolean {
        val info = TrendingCore.slotInfo(slotFor(source, hours, official)) ?: return true
        val now = nowSec()
        return info.origin != TrendingCore.window(now, fetchHours(source, hours)).origin ||
            now - info.fetched > maxAgeSec
    }

    private fun tick() {
        if (loadJob?.isActive == true) {
            _ui.value = _ui.value.copy(nowSec = nowSec())
            return
        }
        if (needsNetwork(AUTO_REFRESH_SEC)) refresh(force = false) else render()
    }

    private fun troubleFor(source: Source, e: Exception): String {
        val base = TrendingCore.troubleMessage(source, (e as? HttpStatusException)?.code)
        val wait = (e as? HttpStatusException)?.retryAfterSec
        return if (wait != null && wait in 1..3600 && "Try again" !in base) "$base Try again in ${wait}s." else base
    }

    private fun render(
        loading: Boolean = _ui.value.loading,
        trouble: String? = _ui.value.trouble,
        older: Boolean = _ui.value.olderLoading,
    ) {
        val cur = _ui.value
        val now = nowSec()
        val s = settings.value
        val options = FeedOptions(
            hideSpam = s.hideSpam,
            interests = s.interests,
            sensitivity = s.sensitivity,
            query = cur.query,
            limit = if (cur.spikes) maxOf(cur.limit, PAGE * 3) else cur.limit,
            topic = cur.topic,
            lang = if (cur.spikes) "" else cur.lang,
            bar = if (cur.spikes) -1 else cur.bar,
            only = cur.filter.flags,
            seen = cur.seen,
            unreadOnly = cur.filter == Filter.UNREAD,
        )
        val feedRaw = if (cur.spikes) {
            spikesFeed(cur, now, options)
        } else {
            val raw = TrendingCore.feed(slotFor(cur.source, cur.hours, cur.ghOfficial), cur.mode, cur.hours, now, options)
                ?: previewFor(cur, now, options)
            // New Repos only: collapse forks/mirrors that share a repo name.
            if (raw != null && cur.source == Source.GH && !cur.ghOfficial) dedupeNewReposFeed(raw) else raw
        }
        // If Rust/HTML left langs empty, rebuild from story.meta so Language Breakdown still works.
        val feed = feedRaw?.let { f ->
            if (f.langs.isNotEmpty()) f
            else {
                val rebuilt = langsFromStories(f.shown)
                if (rebuilt.isEmpty()) f else f.copy(langs = rebuilt)
            }
        }
        val wordTrends = if (cur.spikes) {
            rankSpikesWords(feed?.topics.orEmpty())
        } else {
            lastSpikesWordOrder = emptyList()
            emptyList()
        }
        _ui.value = cur.copy(
            nowSec = now,
            window = TrendingCore.window(now, cur.hours),
            loading = loading,
            trouble = trouble,
            olderLoading = older,
            feed = feed,
            spikesWordTrends = wordTrends,
        )
        loadBriefing()
    }

    /** Compare current topic order to the last snapshot and attach up/down moves. */
    private fun rankSpikesWords(topics: List<Topic>): List<SpikesWordTrend> {
        val previous = lastSpikesWordOrder
            .withIndex()
            .associate { (i, word) -> word to (i + 1) }
        val trends = topics.mapIndexed { i, topic ->
            val rank = i + 1
            val key = topic.word.lowercase()
            val prev = previous[key]
            val move = when {
                prev == null -> null
                else -> prev - rank // climbed if previous place was worse (higher number)
            }
            SpikesWordTrend(topic = topic, rank = rank, move = move)
        }
        lastSpikesWordOrder = topics.map { it.word.lowercase() }
        return trends
    }

    /**
     * Merge Hacker News + Official Trending + New Repos and rank by recent spike
     * strength (heat, breakout/accelerating flags, period star bursts, spark jumps).
     * Reuses [spikesMergeCache] when slot fetch times and Spikes filters are unchanged.
     */
    private fun spikesFeed(cur: TrendingUi, now: Long, options: FeedOptions): Feed? {
        val created = normalizeSpikesCreatedWithin(cur.spikesCreatedWithin)
        val offHours = when (created) {
            24 -> 24
            else -> 6 // weekly official slice for shorter created-within windows
        }
        val hnSlot = slotFor(Source.HN, 24)
        val offSlot = slotFor(Source.GH, offHours, true)
        val neuSlot = slotFor(Source.GH, created, false)
        val hnFetched = TrendingCore.slotInfo(hnSlot)?.fetched ?: -1L
        val offFetched = TrendingCore.slotInfo(offSlot)?.fetched
            ?: TrendingCore.slotInfo(slotFor(Source.GH, 24, true))?.fetched
            ?: -1L
        val neuFetched = TrendingCore.slotInfo(neuSlot)?.fetched ?: -1L
        val cacheKey = SpikesMergeKey(
            hnFetched = hnFetched,
            offFetched = offFetched,
            neuFetched = neuFetched,
            createdWithin = created,
            spikesSource = cur.spikesSource,
            topic = cur.topic,
            lang = cur.lang,
            filter = cur.filter,
            query = options.query,
            hideSpam = options.hideSpam,
            interests = options.interests,
            sensitivity = options.sensitivity,
            unreadOnly = options.unreadOnly,
            seen = if (options.unreadOnly) options.seen else emptyList(),
        )
        spikesMergeCache?.takeIf { it.key == cacheKey }?.let { cached ->
            val shown = cached.ranked.take(cur.limit)
            return Feed(
                hotBars = cached.hotBars,
                newBars = cached.newBars,
                shown = shown,
                peak = shown.maxOfOrNull { it.score } ?: cached.peakFallback,
                peakHeat = shown.maxOfOrNull { it.heat } ?: cached.peakHeatFallback,
                total = cached.total,
                fetched = cached.fetched,
                stale = cached.stale,
                hidden = cached.hidden,
                matching = cached.ranked.size,
                derived = cached.derived,
                topics = cached.topics,
                langs = cached.langs,
                counts = cached.counts,
            )
        }

        // Fetch unfiltered by lang so Language Breakdown stays complete; apply lang below.
        val wideOpts = options.copy(limit = 80, bar = -1, lang = "", topic = cur.topic)
        val hn = TrendingCore.feed(hnSlot, Mode.RISING, created, now, wideOpts)
        val off = TrendingCore.feed(offSlot, Mode.HOT, offHours, now, wideOpts)
            ?: TrendingCore.feed(slotFor(Source.GH, 24, true), Mode.HOT, offHours, now, wideOpts)
        val neuRaw = TrendingCore.feed(neuSlot, Mode.RISING, created, now, wideOpts)
            ?: previewFor(
                cur.copy(source = Source.GH, ghOfficial = false, spikes = false, hours = created),
                now,
                wideOpts,
            )
        // Same New Repos name-dedupe so Spikes does not reintroduce those forks.
        val neu = neuRaw?.let { dedupeNewReposFeed(it) }

        val parts = listOfNotNull(hn, off, neu)
        if (parts.isEmpty()) {
            spikesMergeCache = null
            return null
        }

        val maxAgeSec = cur.spikesCreatedWithin.coerceIn(6, 24) * 3600L
        val pool = parts.flatMap { it.shown }
            .distinctBy { it.id }
            .filter { matchesSpikesSource(it, cur.spikesSource) }
            .filter { matchesSpikesCreatedWithin(it, now, maxAgeSec) }
            .sortedByDescending { spikeScore(it, now) }
            .let { dedupeSpikesCrossSource(it) }
        // Languages from the full pool (before lang chip) so the breakdown stays useful.
        val langs = langsFromStories(pool)
        val ranked = if (cur.lang.isEmpty()) {
            pool
        } else {
            pool.filter { it.meta.equals(cur.lang, ignoreCase = true) }
        }
        val topics = mergeSpikesTopics(
            hn?.topics.orEmpty() + off?.topics.orEmpty() + neu?.topics.orEmpty(),
            ranked,
        )
        val counts = parts.map { it.counts }.reduce { a, b ->
            Counts(
                security = a.security + b.security,
                interest = a.interest + b.interest,
                rising = a.rising + b.rising,
                unread = a.unread + b.unread,
            )
        }
        val hotBars = hn?.hotBars ?: List(24) { 0.0 }
        val newBars = hn?.newBars ?: List(24) { 0.0 }
        val total = parts.sumOf { it.total }
        val fetched = parts.maxOf { it.fetched }
        val stale = parts.any { it.stale }
        val hidden = parts.sumOf { it.hidden }
        val derived = parts.any { it.derived }
        val peakFallback = parts.maxOf { it.peak }
        val peakHeatFallback = parts.maxOf { it.peakHeat }
        spikesMergeCache = SpikesMergeCache(
            key = cacheKey,
            ranked = ranked,
            topics = topics,
            langs = langs,
            hotBars = hotBars,
            newBars = newBars,
            total = total,
            fetched = fetched,
            stale = stale,
            hidden = hidden,
            derived = derived,
            counts = counts,
            peakFallback = peakFallback,
            peakHeatFallback = peakHeatFallback,
        )
        val shown = ranked.take(cur.limit)
        return Feed(
            hotBars = hotBars,
            newBars = newBars,
            shown = shown,
            peak = shown.maxOfOrNull { it.score } ?: peakFallback,
            peakHeat = shown.maxOfOrNull { it.heat } ?: peakHeatFallback,
            total = total,
            fetched = fetched,
            stale = stale,
            hidden = hidden,
            matching = ranked.size,
            derived = derived,
            topics = topics,
            langs = langs,
            counts = counts,
        )
    }

    /**
     * After merge+rank: collapse the same GitHub repo or same normalized URL to the
     * highest-[spikeScore] row. Unrelated HN posts (no shared repo/URL) stay untouched.
     * When HN and GitHub both mention a repo, mark the keeper as cross-source.
     */
    private fun dedupeSpikesCrossSource(rankedBestFirst: List<Story>): List<Story> {
        if (rankedBestFirst.size <= 1) return rankedBestFirst
        val seen = HashMap<String, Int>()
        val out = ArrayList<Story>(rankedBestFirst.size)
        for (story in rankedBestFirst) {
            val key = spikesDedupeKey(story)
            if (key == null) {
                out.add(story)
                continue
            }
            val idx = seen[key]
            if (idx == null) {
                seen[key] = out.size
                out.add(story)
                continue
            }
            val winner = out[idx]
            val crossFamily = sourceFamily(winner) != sourceFamily(story)
            val loserCross = story.crossSource || story.has(Flags.CROSS_SOURCE)
            if ((crossFamily || loserCross) &&
                !(winner.crossSource || winner.has(Flags.CROSS_SOURCE))
            ) {
                out[idx] = winner.copy(
                    crossSource = true,
                    flags = winner.flags or Flags.CROSS_SOURCE,
                )
            }
        }
        return out
    }

    /** HN / official trending / new repos — used only to detect cross-source pairs. */
    private fun sourceFamily(story: Story): Int = when {
        story.id.startsWith("gh:trending:") -> 1
        story.id.startsWith("gh:") -> 2
        else -> 0
    }

    /**
     * Shared identity for Spikes dedupe: `repo:owner/repo` or `url:…`.
     * Null means the story has no shareable key (typical plain HN link) — never merge those.
     */
    private fun spikesDedupeKey(story: Story): String? {
        canonicalRepoKey(story.url, story.title)?.let { return "repo:$it" }
        if (story.id.startsWith("gh:")) {
            bareOwnerRepoKey(story.title)?.let { return "repo:$it" }
        }
        normalizedUrlKey(story.url)?.let { return "url:$it" }
        return null
    }

    /** `owner/repo` from a github.com URL or a title that embeds one (mirrors Rust canonical_repo). */
    private fun canonicalRepoKey(url: String, title: String): String? {
        val target = if (url.contains("github.com/", ignoreCase = true)) url else title
        val marker = "github.com/"
        val pos = target.indexOf(marker, ignoreCase = true)
        if (pos < 0) return null
        val path = target.substring(pos + marker.length)
        val parts = path.split('/')
        if (parts.size < 2) return null
        val owner = parts[0].trim()
        var repo = parts[1].substringBefore('#').substringBefore('?').trim()
        if (repo.endsWith(".git")) repo = repo.dropLast(4)
        if (owner.isEmpty() || repo.isEmpty() || ' ' in owner || ' ' in repo) return null
        return "${owner.lowercase()}/${repo.lowercase()}"
    }

    /** Whole-title `owner/repo` used by GitHub cards. */
    private fun bareOwnerRepoKey(title: String): String? {
        val head = title.trim().substringBefore(' ').substringBefore(':')
        val slash = head.indexOf('/')
        if (slash <= 0 || slash >= head.length - 1) return null
        val owner = head.substring(0, slash)
        var repo = head.substring(slash + 1).substringBefore('#').substringBefore('?')
        if (repo.endsWith(".git")) repo = repo.dropLast(4)
        if (owner.isEmpty() || repo.isEmpty() || '/' in repo) return null
        fun ok(s: String) = s.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
        if (!ok(owner) || !ok(repo)) return null
        return "${owner.lowercase()}/${repo.lowercase()}"
    }

    private fun normalizedUrlKey(url: String): String? {
        var s = url.trim().lowercase()
        if (s.isEmpty()) return null
        s = s.removePrefix("https://").removePrefix("http://")
        if (s.startsWith("www.")) s = s.drop(4)
        val cut = s.indexOfAny(charArrayOf('#', '?'))
        if (cut >= 0) s = s.substring(0, cut)
        s = s.trimEnd('/')
        if (s.isEmpty() || s == "github.com" || s == "news.ycombinator.com") return null
        return s
    }

    private fun matchesSpikesSource(story: Story, source: SpikesSource): Boolean = when (source) {
        SpikesSource.ALL -> true
        SpikesSource.HN -> !story.id.startsWith("gh:")
        SpikesSource.TRENDING -> story.id.startsWith("gh:trending:")
        SpikesSource.NEW -> story.id.startsWith("gh:") && !story.id.startsWith("gh:trending:")
    }

    /** Language breakdown from story.meta (GitHub language / empty for HN). */
    private fun langsFromStories(stories: List<Story>): List<Lang> =
        stories
            .map { it.meta.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .map { (name, count) -> Lang(name, count) }
            .sortedWith(compareByDescending<Lang> { it.count }.thenBy { it.name })
            .take(12)

    /**
     * Keep posts created inside the window. Official Trending with unknown age (t=0)
     * stays; when creation time is known it must fit ≤6h / ≤12h / ≤24h.
     */
    private fun matchesSpikesCreatedWithin(story: Story, now: Long, maxAgeSec: Long): Boolean {
        if (story.t <= 0L) {
            // No creation time — only official trending can pass.
            return story.id.startsWith("gh:trending:")
        }
        return now - story.t <= maxAgeSec
    }

    /** Prefer feed topics; always enrich from story titles so Words is not almost empty. */
    private fun mergeSpikesTopics(fromFeeds: List<Topic>, stories: List<Story>): List<Topic> {
        val merged = fromFeeds
            .groupBy { it.word.lowercase() }
            .map { (_, list) ->
                val best = list.maxBy { it.count + if (it.fresh) 2 else 0 }
                best.copy(count = list.sumOf { it.count }.coerceAtLeast(best.count))
            }
        val known = merged.map { it.word.lowercase() }.toHashSet()
        val enriched = countRepeatedWords(stories, nowSec())
            .filter { it.word.lowercase() !in known }
        return (merged + enriched).sortedWith(
            compareByDescending<Topic> { it.fresh }
                .thenByDescending { it.count }
                .thenByDescending { it.lift },
        ).take(20)
    }

    /**
     * Rank words by how many spike stories mention them and how hot those stories are.
     * Repo titles like owner/agent-desktop become tokens agent, desktop.
     */
    private fun countRepeatedWords(stories: List<Story>, now: Long): List<Topic> {
        if (stories.isEmpty()) return emptyList()
        val perStory = HashMap<String, HashSet<String>>()
        val heatByKey = HashMap<String, Double>()
        val forms = HashMap<String, HashMap<String, Int>>()
        for (story in stories) {
            val text = buildString {
                append(story.title)
                append(' ')
                append(story.blurb)
                append(' ')
                append(story.meta)
            }
            val words = significantWords(text)
            val seen = HashSet<String>()
            val heat = spikeScore(story, now).coerceAtLeast(0.0)
            for (word in words) {
                val key = stemWord(word)
                if (!seen.add(key)) continue
                perStory.getOrPut(key) { HashSet() }.add(story.id)
                heatByKey[key] = (heatByKey[key] ?: 0.0) + heat
                val formCounts = forms.getOrPut(key) { HashMap() }
                formCounts[word] = (formCounts[word] ?: 0) + 1
            }
            for (i in 0 until words.size - 1) {
                val a = words[i]
                val b = words[i + 1]
                if (a == b) continue
                val phrase = "$a $b"
                val key = "${stemWord(a)} ${stemWord(b)}"
                if (!seen.add(key)) continue
                perStory.getOrPut(key) { HashSet() }.add(story.id)
                heatByKey[key] = (heatByKey[key] ?: 0.0) + heat
                val formCounts = forms.getOrPut(key) { HashMap() }
                formCounts[phrase] = (formCounts[phrase] ?: 0) + 1
            }
        }
        return perStory
            .mapNotNull { (key, ids) ->
                val count = ids.size
                val heat = heatByKey[key] ?: 0.0
                // Allow single-post hot terms; prefer repeats.
                if (count < 1) return@mapNotNull null
                if (count == 1 && heat < 8.0) return@mapNotNull null
                val label = forms[key]?.maxByOrNull { it.value }?.key ?: key
                val lift = if (count >= 2) (count.toDouble() / stories.size.coerceAtLeast(1)) * 20.0 else 0.0
                Triple(
                    count * 100.0 + heat,
                    Topic(
                        word = label,
                        count = count,
                        lift = (lift * 10.0).roundTo(1),
                        fresh = count >= 2 && heat >= 25.0,
                    ),
                    heat,
                )
            }
            .sortedWith(
                compareByDescending<Triple<Double, Topic, Double>> { it.first }
                    .thenByDescending { it.second.fresh },
            )
            .map { it.second }
            .take(20)
    }

    private fun Double.roundTo(digits: Int): Double {
        var p = 1.0
        repeat(digits) { p *= 10.0 }
        return kotlin.math.round(this * p) / p
    }

    private fun significantWords(text: String): List<String> {
        val out = ArrayList<String>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isEmpty()) return
            val w = buf.toString()
            buf.clear()
            if (isTopicWord(w)) out.add(w)
        }
        for (ch in text.lowercase()) {
            // Split owner/repo and kebab-case so agent-desktop → agent, desktop.
            if (ch.isLetterOrDigit() || ch == '+' || ch == '#') buf.append(ch) else flush()
        }
        flush()
        return out
    }

    private fun stemWord(word: String): String =
        if (word.length > 3 && word.endsWith('s') && !word.endsWith("ss")) word.dropLast(1) else word

    private fun isTopicWord(word: String): Boolean {
        val n = word.length
        if (n > 24 || word.all { it.isDigit() }) return false
        if (n < 3 && word !in SHORT_TOPIC_WORDS) return false
        return word !in TOPIC_STOP_WORDS
    }

    /** A wider GitHub window cut down to this one, shown while the exact list loads. */
    private fun previewFor(cur: TrendingUi, now: Long, options: FeedOptions): Feed? {
        if (cur.source != Source.GH) return null
        return listOf(24, 12, 6).filter { it > cur.hours }
            .firstNotNullOfOrNull { TrendingCore.feed(slotFor(Source.GH, it, cur.ghOfficial), cur.mode, cur.hours, now, options) }
    }

    /**
     * New Repos: keep one story per repository name (the part after `/` in `owner/repo`).
     * Prefer highest stars, then heat, then newer `t`. Adjusts [Feed.matching] by drops.
     */
    private fun dedupeNewReposFeed(feed: Feed): Feed {
        val before = feed.shown.size
        val shown = dedupeStoriesByRepoName(feed.shown)
        val dropped = before - shown.size
        if (dropped == 0) return feed
        return feed.copy(
            shown = shown,
            matching = maxOf(shown.size, feed.matching - dropped),
        )
    }

    private fun dedupeStoriesByRepoName(stories: List<Story>): List<Story> {
        if (stories.size <= 1) return stories
        val winnerIds = stories
            .groupBy { repoNameKey(it.title) }
            .values
            .mapTo(HashSet(stories.size)) { group ->
                group.maxWith(
                    compareBy<Story> { it.score }
                        .thenBy { it.heat }
                        .thenBy { it.t },
                ).id
            }
        return stories.filter { it.id in winnerIds }
    }

    /** `agent-desktop` from `vercel/agent-desktop`; whole trimmed title when there is no `/`. */
    private fun repoNameKey(title: String): String {
        val trimmed = title.trim()
        val slash = trimmed.indexOf('/')
        return if (slash >= 0) trimmed.substring(slash + 1).lowercase() else trimmed.lowercase()
    }

    private fun initial(): TrendingUi {
        val now = nowSec()
        val s = settings.value
        return TrendingUi(
            source = s.source,
            mode = s.mode,
            hours = s.hours,
            seen = s.seen,
            loading = true,
            trouble = null,
            olderLoading = false,
            feed = null,
            window = TrendingCore.window(now, s.hours),
            nowSec = now,
            query = "",
            limit = PAGE,
            ghOfficial = s.ghOfficial,
        )
    }

    private fun nowSec(): Long = System.currentTimeMillis() / 1000

    private fun rememberCurrentTab() {
        val cur = _ui.value
        tabMemory[tabKey(cur.source, cur.ghOfficial, cur.spikes)] = TabMemory(
            hours = cur.hours,
            mode = cur.mode,
            filter = cur.filter,
            topic = cur.topic,
            lang = cur.lang,
            bar = cur.bar,
            spikesView = cur.spikesView,
            spikesCreatedWithin = cur.spikesCreatedWithin,
            spikesSource = cur.spikesSource,
        )
    }

    private companion object {
        const val TICK_MS = 30_000L
        /** Switching to a slot within this many seconds of its download reuses it as is. */
        const val FRESH_SEC = 3 * 60L
        /** While visible, refresh rows older than this. */
        const val AUTO_REFRESH_SEC = 5 * 60L
        /** Background fills skip slots younger than this. */
        const val PREFETCH_MAX_AGE_SEC = 10 * 60L
        const val PREFETCH_GAP_MS = 1_500L
        const val HN_PAGE = 1000
        const val HN_MAX_EXTRA_PAGES = 3
        const val PAGE = 30
        const val THREADS_TTL_SEC = 10 * 60L

        private val SHORT_TOPIC_WORDS = setOf(
            "ai", "ml", "go", "os", "js", "ui", "ux", "vr", "ar", "3d", "5g", "eu", "uk", "qt", "db",
        )
        private val TOPIC_STOP_WORDS = (
            "the and for are but not you all any can had her was one our out day get has him his how man new now old see two way who " +
                "boy did its let put say she too use with that this from have they will your what when where which while about after " +
                "again also been before being below between both could does doing down during each even every few first further here " +
                "into just like made make many more most much must never next only other over same should some such than then there " +
                "these those through under until very want well were whom would yet why yes off own per via vs versus etc " +
                "show ask tell launch hn hacker news story post posts thread comment comments discussion " +
                "using used uses based build built building simple easy fast faster better best good great free open source tool tools " +
                "app apps application applications project projects repo repository library framework official awesome list lists " +
                "my me we it is an to of in on at by or as be do if so no up " +
                "year years month months week weeks today yesterday time times thing things people way ways part " +
                "http https www com org net github io html pdf video videos blog article paper " +
                "version release released update updated newest latest last " +
                "one two three four five six seven eight nine ten hundred thousand million billion " +
                "day days hour hours minute minutes second seconds " +
                // News / place / filler noise that was polluting Spikes Words
                "italy fines fine says said say amid after against among around become becomes became " +
                "city country world state states government police court case cases law " +
                "company companies business market stock price prices deal deals money " +
                "user users team teams work works working works without within another every " +
                "name names type types data file files code page pages site sites web " +
                "read reading writes write written support supports supporting help helps"
            ).split(' ').filter { it.isNotEmpty() }.toHashSet()

        fun fetchHours(source: Source, hours: Int) = if (source == Source.HN) 24 else hours

        fun slotFor(source: Source, hours: Int, ghOfficial: Boolean = false) =
            if (source == Source.HN) "ui:hn"
            else if (ghOfficial) "ui:gh:off:$hours"
            else "ui:gh:$hours"

        fun tabKey(source: Source, ghOfficial: Boolean, spikes: Boolean = false): String = when {
            spikes -> "spikes"
            source == Source.HN -> "hn"
            ghOfficial -> "gh:trending"
            else -> "gh:new"
        }

        fun normalizeTabHours(ghOfficial: Boolean, hours: Int): Int {
            val normalized = normalizeHours(hours)
            return if (ghOfficial && normalized == 12) 1 else normalized
        }

        fun normalizeTabFilter(ghOfficial: Boolean, filter: Filter): Filter =
            if (ghOfficial && (filter == Filter.SECURITY || filter == Filter.RISING)) Filter.ALL
            else filter

        /** Higher = stronger recent spike. Used only by the Spikes tab. */
        fun spikeScore(story: Story, now: Long): Double {
            var score = story.heat.coerceAtLeast(0.0)
            if (story.has(app.omalauncher.trending.logic.Flags.BREAKOUT)) score *= 2.6
            if (story.has(app.omalauncher.trending.logic.Flags.ACCELERATING)) score *= 2.1
            if (story.has(app.omalauncher.trending.logic.Flags.RISING)) score *= 1.45
            if (story.has(app.omalauncher.trending.logic.Flags.NEW_AND_TRENDING)) score *= 1.35

            if (story.id.startsWith("gh:trending:")) {
                val period = story.extra.coerceAtLeast(0.0)
                val total = story.score.coerceAtLeast(1.0)
                val burst = (period / total).coerceIn(0.0, 2.0)
                score = maxOf(score, period / 12.0) * (1.0 + burst * 2.0) + period * 0.02
            }

            if (story.t > 0L) {
                val ageH = ((now - story.t) / 3600.0).coerceAtLeast(0.2)
                when {
                    ageH <= 3.0 -> score *= 1.45
                    ageH <= 8.0 -> score *= 1.2
                    ageH >= 36.0 -> score *= 0.75
                }
            }

            if (story.spark.size >= 2) {
                val last = story.spark.last()
                val prev = story.spark[story.spark.size / 2]
                val jump = (last - prev).coerceAtLeast(0.0)
                score += jump * 1.8
            }
            return score
        }

        val ALL_SLOTS = setOf(
            "ui:hn", "ui:gh:1", "ui:gh:6", "ui:gh:12", "ui:gh:24",
            "ui:gh:off:1", "ui:gh:off:6", "ui:gh:off:12", "ui:gh:off:24"
        )
    }
}

/** Saved controls for one feed tab. */
private data class TabMemory(
    val hours: Int,
    val mode: Mode,
    val filter: Filter,
    val topic: String,
    val lang: String,
    val bar: Int,
    val spikesView: SpikesView = SpikesView.STORIES,
    val spikesCreatedWithin: Int = 24,
    val spikesSource: SpikesSource = SpikesSource.ALL,
)

/** Identity for the Spikes merge+rank cache (before list limit). */
private data class SpikesMergeKey(
    val hnFetched: Long,
    val offFetched: Long,
    val neuFetched: Long,
    val createdWithin: Int,
    val spikesSource: SpikesSource,
    val topic: String,
    val lang: String,
    val filter: Filter,
    val query: String,
    val hideSpam: Boolean,
    val interests: List<String>,
    val sensitivity: Int,
    val unreadOnly: Boolean,
    val seen: List<String>,
)

/** Cached Spikes merge result; [ranked] is pre-limit. */
private data class SpikesMergeCache(
    val key: SpikesMergeKey,
    val ranked: List<Story>,
    val topics: List<Topic>,
    val langs: List<Lang>,
    val hotBars: List<Double>,
    val newBars: List<Double>,
    val total: Long,
    val fetched: Long,
    val stale: Boolean,
    val hidden: Int,
    val derived: Boolean,
    val counts: Counts,
    val peakFallback: Double,
    val peakHeatFallback: Double,
)

private fun normalizeSpikesCreatedWithin(hours: Int): Int =
    if (hours == 6 || hours == 12 || hours == 24) hours else 24
