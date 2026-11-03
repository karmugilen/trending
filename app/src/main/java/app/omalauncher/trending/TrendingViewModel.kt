package app.omalauncher.trending

import android.app.Application
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
import app.omalauncher.trending.logic.Feed
import app.omalauncher.trending.logic.Filter
import app.omalauncher.trending.logic.Mode
import app.omalauncher.trending.logic.Source
import app.omalauncher.trending.logic.Story
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
            } catch (_: Exception) {}
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
        if (source == _ui.value.source) return
        settings.update { it.copy(source = source) }
        _ui.value = _ui.value.copy(
            source = source, trouble = null, olderLoading = false, limit = PAGE, topic = "", lang = "", bar = -1,
        )
        switched()
    }

    fun selectMode(mode: Mode) {
        if (mode == _ui.value.mode) return
        settings.update { it.copy(mode = mode) }
        _ui.value = _ui.value.copy(mode = mode, limit = PAGE)
        render()
    }

    fun selectHours(hours: Int) {
        if (hours != 1 && hours != 6 && hours != 12 && hours != 24) return
        val next = normalizeHours(hours)
        if (next == _ui.value.hours) return
        settings.update { it.copy(hours = next) }
        // Bars mean different times in another window, so that choice goes.
        _ui.value = _ui.value.copy(hours = next, trouble = null, olderLoading = false, limit = PAGE, bar = -1)
        switched()
    }

    fun selectGhOfficial(official: Boolean) {
        if (official == _ui.value.ghOfficial) return
        val hours = if (official && _ui.value.hours == 12) 1 else _ui.value.hours
        val filter = if (official && (_ui.value.filter == Filter.SECURITY || _ui.value.filter == Filter.RISING)) Filter.ALL else _ui.value.filter
        settings.update { it.copy(ghOfficial = official, hours = hours) }
        _ui.value = _ui.value.copy(ghOfficial = official, hours = hours, filter = filter, trouble = null, olderLoading = false, limit = PAGE, bar = -1)
        switched()
    }

    fun setQuery(query: String) {
        if (query == _ui.value.query) return
        _ui.value = _ui.value.copy(query = query, limit = PAGE)
        render()
    }

    fun selectFilter(filter: Filter) {
        if (filter == _ui.value.filter) return
        _ui.value = _ui.value.copy(filter = filter, limit = PAGE)
        render()
    }

    /** Tapping the chip in use clears it. */
    fun toggleTopic(word: String) {
        _ui.value = _ui.value.copy(topic = if (_ui.value.topic == word) "" else word, limit = PAGE)
        render()
    }

    fun toggleLang(name: String) {
        _ui.value = _ui.value.copy(lang = if (_ui.value.lang == name) "" else name, limit = PAGE)
        render()
    }

    /** Narrow the list to one chart bar; the same bar again shows the whole window. */
    fun toggleBar(index: Int) {
        _ui.value = _ui.value.copy(bar = if (_ui.value.bar == index) -1 else index, limit = PAGE)
        render()
    }

    fun clearNarrowing() {
        _ui.value = _ui.value.copy(filter = Filter.ALL, topic = "", lang = "", bar = -1, limit = PAGE)
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
            val off = settings.value.ghOfficial
            val ghHours = if (off) listOf(1, 6, 24) else listOf(1, 6, 12, 24)
            val wanted = listOf(Source.HN to 24) + ghHours.map { Source.GH to it }
            for ((source, hours) in wanted) {
                val slot = slotFor(source, hours, off)
                if (slot == loadingSlot && loadJob?.isActive == true) continue
                if (!slotNeedsNetwork(source, hours, PREFETCH_MAX_AGE_SEC)) continue
                try {
                    download(source, hours) { false }
                    afterDownload(slot)
                    // Something on screen may be a preview of this slot.
                    if (slotFor(_ui.value.source, _ui.value.hours, _ui.value.ghOfficial) == slot) render()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A background fill failing is fine; the user's own refresh will say why.
                }
                delay(PREFETCH_GAP_MS)
            }
        }
    }

    private suspend fun download(source: Source, hours: Int, onScreen: () -> Boolean) {
        val official = settings.value.ghOfficial
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
            alerts.check(listOf(slot))
        }
    }

    private fun needsNetwork(maxAgeSec: Long): Boolean =
        slotNeedsNetwork(_ui.value.source, _ui.value.hours, maxAgeSec)

    /** True when the slot is missing, from an older window, or older than [maxAgeSec]. */
    private fun slotNeedsNetwork(source: Source, hours: Int, maxAgeSec: Long): Boolean {
        val info = TrendingCore.slotInfo(slotFor(source, hours, settings.value.ghOfficial)) ?: return true
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
            limit = cur.limit,
            topic = cur.topic,
            lang = cur.lang,
            bar = cur.bar,
            only = cur.filter.flags,
            seen = cur.seen,
            unreadOnly = cur.filter == Filter.UNREAD,
        )
        val feed = TrendingCore.feed(slotFor(cur.source, cur.hours, cur.ghOfficial), cur.mode, cur.hours, now, options)
            ?: previewFor(cur, now, options)
        _ui.value = cur.copy(
            nowSec = now,
            window = TrendingCore.window(now, cur.hours),
            loading = loading,
            trouble = trouble,
            olderLoading = older,
            feed = feed,
        )
        loadBriefing()
    }

    /** A wider GitHub window cut down to this one, shown while the exact list loads. */
    private fun previewFor(cur: TrendingUi, now: Long, options: FeedOptions): Feed? {
        if (cur.source != Source.GH) return null
        return listOf(24, 12, 6).filter { it > cur.hours }
            .firstNotNullOfOrNull { TrendingCore.feed(slotFor(Source.GH, it, cur.ghOfficial), cur.mode, cur.hours, now, options) }
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

        fun fetchHours(source: Source, hours: Int) = if (source == Source.HN) 24 else hours

        fun slotFor(source: Source, hours: Int, ghOfficial: Boolean = false) =
            if (source == Source.HN) "ui:hn"
            else if (ghOfficial) "ui:gh:off:$hours"
            else "ui:gh:$hours"

        val ALL_SLOTS = setOf(
            "ui:hn", "ui:gh:1", "ui:gh:6", "ui:gh:12", "ui:gh:24",
            "ui:gh:off:1", "ui:gh:off:6", "ui:gh:off:12", "ui:gh:off:24"
        )
    }
}
