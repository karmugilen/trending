package app.omalauncher.trending.logic

enum class Source(val code: Int, val key: String) {
    HN(0, "hn"),
    GH(1, "gh");

    companion object {
        fun from(key: String?): Source = if (key == "gh") GH else HN
    }
}

/** HOT is most points/stars, NEW is newest, RISING is points or stars per hour. */
enum class Mode(val code: Int, val key: String) {
    HOT(0, "hot"),
    NEW(1, "new"),
    RISING(2, "rising");

    companion object {
        fun from(key: String?): Mode = entries.firstOrNull { it.key == key } ?: HOT
    }
}

/** Bits in [Story.flags]. Rust sets them; keep in sync with core/src/signals.rs. */
object Flags {
    const val RISING = 1
    const val SECURITY = 2
    const val SPAM = 4
    const val INTEREST = 8
    const val ACCELERATING = 16
    const val BREAKOUT = 32
    const val STACK_AFFECTED = 64
    const val CROSS_SOURCE = 128
}

data class Story(
    val id: String,
    val title: String,
    val url: String,
    val talk: String,
    val score: Double,
    val extra: Double,
    val meta: String,
    val blurb: String,
    val t: Long,
    /** Points or stars per hour since posting. */
    val heat: Double = 0.0,
    val flags: Int = 0,
    /** Security label such as "CVE-2026-1234" or "RCE" when [Flags.SECURITY] is set. */
    val tag: String = "",
    /** Interest keyword that matched when [Flags.INTEREST] is set. */
    val matched: String = "",
    /** Site the link points to, like "nytimes.com". Empty for GitHub and Ask HN posts. */
    val domain: String = "",
    val sparkline: String = "",
    val affectsStack: String = "",
    val crossSource: Boolean = false,
) {
    fun has(flag: Int): Boolean = flags and flag != 0
}

data class Briefing(
    val periodStart: Long,
    val periodEnd: Long,
    val readTimeMins: Int,
    val brokeOut: List<Story>,
    val affectsYou: List<Story>,
    val crossSource: List<Story>,
    val forYou: List<Story>,
    val caughtUpCount: Int,
)

data class BucketWindow(
    val hours: Int,
    val step: Long,
    val bars: Int,
    val origin: Long,
)

data class Feed(
    val hotBars: List<Double>,
    val newBars: List<Double>,
    val shown: List<Story>,
    val peak: Double,
    val peakHeat: Double,
    val total: Long,
    /** Unix seconds when the data behind this feed was downloaded. */
    val fetched: Long,
    /** True when the data was downloaded for an older window than the current one. */
    val stale: Boolean,
    /** Rows dropped as likely spam. */
    val hidden: Int,
    /** Rows that pass the search before the list limit; more than shown.size means "show more". */
    val matching: Int,
    /** Built from a wider download. Exact for Hacker News, a preview for GitHub. */
    val derived: Boolean,
    /** What the window is talking about, strongest first. */
    val topics: List<Topic> = emptyList(),
    /** GitHub languages in the window. */
    val langs: List<Lang> = emptyList(),
    /** Rows each quick filter would list. */
    val counts: Counts = Counts(),
)

data class Topic(
    val word: String,
    val count: Int,
    /** Times more common now than earlier in the download; 0 when unknown. */
    val lift: Double,
    /** Much more common than before: an emerging topic. */
    val fresh: Boolean,
)

data class Lang(val name: String, val count: Int)

data class Counts(val security: Int = 0, val interest: Int = 0, val rising: Int = 0, val unread: Int = 0)

/** Quick filters above the list. [flags] are [Flags] bits a row needs one of. */
enum class Filter(val key: String, val flags: Int) {
    ALL("all", 0),
    RISING("rising", Flags.RISING),
    SECURITY("security", Flags.SECURITY),
    INTERESTS("interests", Flags.INTEREST),
    UNREAD("unread", 0),
}

/** Hours the chips allow. Anything else is the 6h default, matching the owl app. */
fun normalizeHours(hours: Int): Int =
    if (hours == 1 || hours == 6 || hours == 12 || hours == 24) hours else 6

enum class AlertKind(val key: String) {
    BREAKING("breaking"),
    SECURITY("security"),
    INTEREST("interest");

    companion object {
        fun from(key: String?): AlertKind = entries.firstOrNull { it.key == key } ?: BREAKING
    }
}

data class Alert(
    val id: String,
    val kind: AlertKind,
    val source: Source,
    val title: String,
    val url: String,
    val talk: String,
    val score: Double,
    val extra: Double,
    val heat: Double,
    val t: Long,
    /** Short why: "CVE-2026-1234", "game", or "85 pts/h". */
    val reason: String,
    val blurb: String,
    val meta: String,
)

data class AlertResult(
    val alerts: List<Alert>,
    /** Opaque dedupe state. Persist it and pass it back next time. */
    val state: String,
    /** Candidates that qualified but were not returned because of the cap. */
    val suppressed: Int,
)

/** What the user wants to be told about. Mirrors AlertConfig in core/src/alerts.rs. */
data class AlertConfig(
    val breaking: Boolean,
    val security: Boolean,
    val interests: List<String>,
    /** 0 low, 1 normal, 2 high. */
    val sensitivity: Int,
    val hn: Boolean,
    val gh: Boolean,
    val max: Int = 4,
)
