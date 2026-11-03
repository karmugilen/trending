package app.omalauncher.trending.core

import app.omalauncher.trending.logic.Alert
import app.omalauncher.trending.logic.AlertConfig
import app.omalauncher.trending.logic.AlertKind
import app.omalauncher.trending.logic.AlertResult
import app.omalauncher.trending.logic.BucketWindow
import app.omalauncher.trending.logic.Counts
import app.omalauncher.trending.logic.Feed
import app.omalauncher.trending.logic.Lang
import app.omalauncher.trending.logic.Mode
import app.omalauncher.trending.logic.Source
import app.omalauncher.trending.logic.Story
import app.omalauncher.trending.logic.Topic
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

/** Which GitHub search to build. */
enum class GhQuery(val code: Int) {
    /** Most-starred repos created since the origin. */
    NEW_REPOS(0),
    /** Repos about CVEs, exploits, and vulnerabilities. */
    SECURITY(1),
    /** Repos matching the user's interest keywords. */
    INTERESTS(2),
}

/** One downloaded page after Rust parsed and stored it. */
data class Chunk(
    /** Rows on this page. */
    val count: Int,
    /** Oldest row time on this page, 0 if none. Used to ask Hacker News for the next older page. */
    val oldest: Long,
    /** Rows now held for the slot. */
    val stored: Int,
)

data class SlotInfo(val fetched: Long, val origin: Long, val count: Int)

data class FeedOptions(
    val hideSpam: Boolean,
    val interests: List<String>,
    val sensitivity: Int,
    /** Search box text. Every word must start a word in the title, description or meta. */
    val query: String = "",
    /** Rows to list. */
    val limit: Int = 30,
    /** A topic chip. Rows have to mention it. */
    val topic: String = "",
    /** A GitHub language chip. */
    val lang: String = "",
    /** A chart bar index, or -1 for the whole window. */
    val bar: Int = -1,
    /** [app.omalauncher.trending.logic.Flags] bits; rows need one. 0 for any. */
    val only: Int = 0,
    /** Ids the user has opened. */
    val seen: List<String> = emptyList(),
    val unreadOnly: Boolean = false,
)

class CoreException(message: String) : Exception(message)

/**
 * Calls into libtrending.so. The native names are the @JvmStatic method names,
 * so R8 has to keep this class.
 *
 * Rust keeps a cache of parsed rows per "slot" (any string, such as "ui:hn:6"
 * or "watch:gh"). Download a page, [ingest] it into a slot, then ask that slot
 * for a [feed] or [alerts] as often as needed without parsing again.
 */
class TrendingCore private constructor() {
    companion object {
        init {
            System.loadLibrary("trending")
        }

        fun window(nowSec: Long, hours: Int): BucketWindow {
            val json = JSONObject(windowJson(nowSec, hours))
            return BucketWindow(
                hours = json.getInt("hours"),
                step = json.getLong("step"),
                bars = json.getInt("bars"),
                origin = json.getLong("origin"),
            )
        }

        fun githubStamp(originSec: Long): String = stamp(originSec)

        /** Hacker News stories created after [originSec]. Pass [beforeSec] > 0 for the page older than that time. */
        fun hnUrl(originSec: Long, beforeSec: Long = 0): String = hnUrlNative(originSec, beforeSec)

        fun ghUrl(kind: GhQuery, originSec: Long, keywords: List<String> = emptyList()): String =
            ghUrlNative(kind.code, originSec, JSONArray(keywords).toString())

        /** Parse [body] and store it in [slot]. [append] merges into the slot instead of replacing it. */
        fun ingest(slot: String, source: Source, originSec: Long, nowSec: Long, body: String, append: Boolean): Chunk {
            val json = JSONObject(ingestJson(slot, source.code, originSec, nowSec, body, append))
            if (!json.optBoolean("ok", false)) {
                throw CoreException(json.optString("trouble").ifEmpty { troubleMessage(source, null) })
            }
            return Chunk(json.optInt("count"), json.optLong("oldest"), json.optInt("stored"))
        }

        /** Feed for [slot], or null when nothing is stored there yet. */
        fun feed(slot: String, mode: Mode, hours: Int, nowSec: Long, options: FeedOptions): Feed? {
            val opts = JSONObject()
                .put("hideSpam", options.hideSpam)
                .put("interests", JSONArray(options.interests))
                .put("sensitivity", options.sensitivity)
                .put("query", options.query)
                .put("limit", options.limit)
                .put("topic", options.topic)
                .put("lang", options.lang)
                .put("bar", options.bar)
                .put("only", options.only)
                .put("seen", JSONArray(options.seen))
                .put("unreadOnly", options.unreadOnly)
            val json = JSONObject(feedJson(slot, mode.code, hours, nowSec, opts.toString()))
            if (!json.optBoolean("ok", false)) return null
            return decodeFeed(json)
        }

        fun slotInfo(slot: String): SlotInfo? {
            val json = JSONObject(slotInfoJson(slot))
            if (!json.optBoolean("ok", false)) return null
            return SlotInfo(json.optLong("fetched"), json.optLong("origin"), json.optInt("count"))
        }

        /** Compact JSON of a slot for the disk cache, or null when empty. */
        fun export(slot: String): String? = exportSlot(slot)

        /** Restore a slot from [export] output. Keeps whatever is newer. */
        fun import(slot: String, json: String): Boolean = importSlot(slot, json)

        fun alerts(slots: List<String>, config: AlertConfig, state: String, nowSec: Long): AlertResult {
            val cfg = JSONObject()
                .put("breaking", config.breaking)
                .put("security", config.security)
                .put("interests", JSONArray(config.interests))
                .put("sensitivity", config.sensitivity)
                .put("hn", config.hn)
                .put("gh", config.gh)
                .put("max", config.max)
            val json = JSONObject(alertsJson(JSONArray(slots).toString(), cfg.toString(), state.ifEmpty { "{}" }, nowSec))
            val array = json.optJSONArray("alerts") ?: JSONArray()
            val alerts = List(array.length()) { i ->
                val a = array.getJSONObject(i)
                Alert(
                    id = a.optString("id"),
                    kind = AlertKind.from(a.optString("kind")),
                    source = Source.from(a.optString("source")),
                    title = a.optString("title"),
                    url = a.optString("url"),
                    talk = a.optString("talk"),
                    score = a.optDouble("score", 0.0),
                    extra = a.optDouble("extra", 0.0),
                    heat = a.optDouble("heat", 0.0),
                    t = a.optLong("t"),
                    reason = a.optString("reason"),
                    blurb = a.optString("blurb"),
                    meta = a.optString("meta"),
                )
            }
            return AlertResult(alerts, json.optString("state", state), json.optInt("suppressed"))
        }

        /** Hacker News search for threads about [link]. */
        fun discussUrl(link: String): String = discussUrlNative(link)

        fun parseStack(rawContent: String): List<String> {
            val json = JSONArray(parseStackJson(rawContent))
            return List(json.length()) { json.getString(it) }
        }

        fun briefing(
            sinceSec: Long,
            nowSec: Long,
            hnSlot: String,
            ghSlot: String,
            stackDependencies: List<String>,
            interests: List<String>,
            sensitivity: Int,
        ): app.omalauncher.trending.logic.Briefing {
            val raw = briefingNative(
                sinceSec,
                nowSec,
                hnSlot,
                ghSlot,
                JSONArray(stackDependencies).toString(),
                JSONArray(interests).toString(),
                sensitivity,
            )
            val json = JSONObject(raw)
            return app.omalauncher.trending.logic.Briefing(
                periodStart = json.optLong("period_start", sinceSec),
                periodEnd = json.optLong("period_end", nowSec),
                readTimeMins = json.optInt("read_time_mins", 3),
                brokeOut = (json.optJSONArray("broke_out") ?: JSONArray()).let { arr ->
                    List(arr.length()) { story(arr.getJSONObject(it)) }
                },
                affectsYou = (json.optJSONArray("affects_you") ?: JSONArray()).let { arr ->
                    List(arr.length()) { story(arr.getJSONObject(it)) }
                },
                crossSource = (json.optJSONArray("cross_source") ?: JSONArray()).let { arr ->
                    List(arr.length()) { story(arr.getJSONObject(it)) }
                },
                forYou = (json.optJSONArray("for_you") ?: JSONArray()).let { arr ->
                    List(arr.length()) { story(arr.getJSONObject(it)) }
                },
                caughtUpCount = json.optInt("caught_up_count", 0),
            )
        }

        fun exportHistory(): String = exportHistoryNative()
        fun importHistory(json: String): Boolean = importHistoryNative(json)

        /** Threads from a [discussUrl] answer, best first, without [skipId]. */
        fun discussions(body: String, skipId: String): List<Story> {
            val json = JSONObject(discussionsJson(body, skipId))
            if (!json.optBoolean("ok", false)) {
                throw CoreException(json.optString("trouble").ifEmpty { troubleMessage(Source.HN, null) })
            }
            val array = json.optJSONArray("stories") ?: return emptyList()
            return List(array.length()) { i -> story(array.getJSONObject(i)) }
        }

        fun fmtCompact(n: Double): String = compact(n)

        fun fmtAgo(t: Long, now: Long): String = ago(t, now)

        fun fmtBarLabel(epochSec: Long, hours: Int): String {
            val offsetSec = TimeZone.getDefault().getOffset(epochSec * 1000L) / 1000
            return barLabel(epochSec, hours, offsetSec)
        }

        fun troubleMessage(source: Source, httpCode: Int?): String =
            troubleText(source.code, httpCode ?: -1)

        private fun decodeFeed(json: JSONObject): Feed {
            val shownJson = json.optJSONArray("shown")
            val shown = ArrayList<Story>(shownJson?.length() ?: 0)
            if (shownJson != null) {
                for (i in 0 until shownJson.length()) {
                    val item = shownJson.optJSONObject(i) ?: continue
                    shown.add(story(item))
                }
            }
            return Feed(
                hotBars = doubles(json, "hotBars"),
                newBars = doubles(json, "newBars"),
                shown = shown,
                peak = json.optDouble("peak", 1.0),
                peakHeat = json.optDouble("peakHeat", 1.0),
                total = json.optLong("total"),
                fetched = json.optLong("fetched"),
                stale = json.optBoolean("stale", false),
                hidden = json.optInt("hidden"),
                matching = json.optInt("matching", shown.size),
                derived = json.optBoolean("derived", false),
                topics = objects(json, "topics") {
                    Topic(it.optString("word"), it.optInt("count"), it.optDouble("lift", 0.0), it.optBoolean("fresh"))
                },
                langs = objects(json, "langs") { Lang(it.optString("name"), it.optInt("count")) },
                counts = json.optJSONObject("counts")?.let {
                    Counts(it.optInt("security"), it.optInt("interest"), it.optInt("rising"), it.optInt("unread"))
                } ?: Counts(),
            )
        }

        private fun story(item: JSONObject) = Story(
            id = item.optString("id"),
            title = item.optString("title"),
            url = item.optString("url"),
            talk = item.optString("talk"),
            score = item.optDouble("score", 0.0),
            extra = item.optDouble("extra", 0.0),
            meta = item.optString("meta"),
            blurb = item.optString("blurb"),
            t = item.optLong("t"),
            heat = item.optDouble("heat", 0.0),
            flags = item.optInt("flags"),
            tag = item.optString("tag"),
            matched = item.optString("matched"),
            domain = item.optString("domain"),
            sparkline = item.optString("sparkline"),
            affectsStack = item.optString("affects_stack"),
            crossSource = item.optBoolean("cross_source", false),
        )

        private fun <T> objects(json: JSONObject, key: String, read: (JSONObject) -> T): List<T> {
            val array = json.optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(read) }
        }

        private fun doubles(json: JSONObject, key: String): List<Double> {
            val array = json.optJSONArray(key) ?: return emptyList()
            return List(array.length()) { index -> array.optDouble(index, 0.0) }
        }

        @JvmStatic external fun windowJson(nowSec: Long, hours: Int): String

        @JvmStatic external fun stamp(originSec: Long): String

        @JvmStatic external fun compact(n: Double): String

        @JvmStatic external fun ago(t: Long, now: Long): String

        @JvmStatic external fun barLabel(epochSec: Long, hours: Int, offsetSec: Int): String

        @JvmStatic external fun troubleText(source: Int, code: Int): String

        @JvmStatic external fun hnUrlNative(originSec: Long, beforeSec: Long): String

        @JvmStatic external fun ghUrlNative(kind: Int, originSec: Long, keywordsJson: String): String

        @JvmStatic external fun ingestJson(
            slot: String,
            source: Int,
            originSec: Long,
            nowSec: Long,
            body: String,
            append: Boolean,
        ): String

        @JvmStatic external fun feedJson(slot: String, mode: Int, hours: Int, nowSec: Long, optsJson: String): String

        @JvmStatic external fun slotInfoJson(slot: String): String

        @JvmStatic external fun exportSlot(slot: String): String?

        @JvmStatic external fun importSlot(slot: String, json: String): Boolean

        @JvmStatic external fun discussUrlNative(link: String): String

        @JvmStatic external fun discussionsJson(body: String, skipId: String): String

        @JvmStatic external fun alertsJson(slotsJson: String, configJson: String, stateJson: String, nowSec: Long): String

        @JvmStatic external fun parseStackJson(raw: String): String

        @JvmStatic external fun briefingNative(
            sinceSec: Long,
            nowSec: Long,
            hnSlot: String,
            ghSlot: String,
            stackJson: String,
            interestsJson: String,
            sensitivity: Int,
        ): String

        @JvmStatic external fun exportHistoryNative(): String

        @JvmStatic external fun importHistoryNative(json: String): Boolean
    }
}
