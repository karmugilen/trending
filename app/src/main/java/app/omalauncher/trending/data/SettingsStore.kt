package app.omalauncher.trending.data

import android.content.Context
import android.content.SharedPreferences
import app.omalauncher.trending.logic.AlertConfig
import app.omalauncher.trending.logic.Mode
import app.omalauncher.trending.logic.Source
import app.omalauncher.trending.logic.normalizeHours
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    /** Dark with true-black surfaces for OLED screens. */
    BLACK("black");

    companion object {
        fun from(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: DARK
    }
}

data class Settings(
    val source: Source = Source.HN,
    val mode: Mode = Mode.HOT,
    val hours: Int = 6,
    val seen: List<String> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** Index into ui.Palettes. 0 is the original amber. */
    val palette: Int = 0,
    /** Material You wallpaper colors on Android 12+. Overrides [palette] when on. */
    val dynamicColor: Boolean = true,
    val hideSpam: Boolean = true,
    /** Full activity charts above the list, or the compact strip. */
    val chartExpanded: Boolean = true,
    /** Open links in a Custom Tab over the app instead of the browser app. */
    val openInApp: Boolean = true,
    /** Master switch for background checks and notifications. */
    val notify: Boolean = false,
    val notifyBreaking: Boolean = true,
    val notifySecurity: Boolean = true,
    val interests: List<String> = emptyList(),
    /** 0 low, 1 normal, 2 high. */
    val sensitivity: Int = 1,
    /** Background check period. WorkManager's floor is 15. */
    val intervalMin: Int = 30,
    val notifyHn: Boolean = true,
    val notifyGh: Boolean = true,
    val stackDependencies: List<String> = emptyList(),
    val lastBriefingSeenSec: Long = 0L,
    val ghOfficial: Boolean = false,
) {
    fun alertConfig(): AlertConfig = AlertConfig(
        breaking = notifyBreaking,
        security = notifySecurity,
        interests = interests,
        sensitivity = sensitivity,
        hn = notifyHn,
        gh = notifyGh,
    )
}

/**
 * One process-wide store so the screen, settings, and the background worker
 * all see the same values. Read [flow]; write with [update].
 */
class SettingsStore private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("trending", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val flow: StateFlow<Settings> = state.asStateFlow()
    val value: Settings get() = state.value

    fun update(transform: (Settings) -> Settings) {
        synchronized(this) {
            val next = clean(transform(state.value))
            if (next == state.value) return
            state.value = next
            write(next)
        }
    }

    private fun clean(s: Settings): Settings = s.copy(
        hours = normalizeHours(s.hours),
        seen = s.seen.take(MAX_SEEN),
        sensitivity = s.sensitivity.coerceIn(0, 2),
        intervalMin = s.intervalMin.coerceAtLeast(15),
        interests = s.interests.map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }.take(MAX_INTERESTS),
    )

    private fun read(): Settings = clean(
        Settings(
            source = Source.from(prefs.getString(KEY_SRC, "hn")),
            mode = Mode.from(prefs.getString(KEY_MODE, "hot")),
            hours = prefs.getInt(KEY_WIN, 6),
            seen = strings(KEY_SEEN),
            themeMode = ThemeMode.from(prefs.getString("theme", null)),
            palette = prefs.getInt("palette", 0),
            dynamicColor = prefs.getBoolean("dynamic", true),
            hideSpam = prefs.getBoolean("hideSpam", true),
            // Before schema 2 the compact strip was the default; everyone starts on the full charts once.
            chartExpanded = prefs.getInt(KEY_SCHEMA, 1) < SCHEMA || prefs.getBoolean("chartExpanded", true),
            openInApp = prefs.getBoolean("openInApp", true),
            notify = prefs.getBoolean("notify", false),
            notifyBreaking = prefs.getBoolean("notifyBreaking", true),
            notifySecurity = prefs.getBoolean("notifySecurity", true),
            interests = strings("interests"),
            sensitivity = prefs.getInt("sensitivity", 1),
            intervalMin = prefs.getInt("intervalMin", 30),
            notifyHn = prefs.getBoolean("notifyHn", true),
            notifyGh = prefs.getBoolean("notifyGh", true),
            stackDependencies = strings("stackDependencies"),
            lastBriefingSeenSec = prefs.getLong("lastBriefingSeenSec", 0L),
            ghOfficial = prefs.getBoolean("ghOfficial", false),
        ),
    )

    private fun write(s: Settings) {
        prefs.edit()
            .putString(KEY_SRC, s.source.key)
            .putString(KEY_MODE, s.mode.key)
            .putInt(KEY_WIN, s.hours)
            .putString(KEY_SEEN, JSONArray(s.seen).toString())
            .putString("theme", s.themeMode.key)
            .putInt("palette", s.palette)
            .putBoolean("dynamic", s.dynamicColor)
            .putBoolean("hideSpam", s.hideSpam)
            .putInt(KEY_SCHEMA, SCHEMA)
            .putBoolean("chartExpanded", s.chartExpanded)
            .putBoolean("openInApp", s.openInApp)
            .putBoolean("notify", s.notify)
            .putBoolean("notifyBreaking", s.notifyBreaking)
            .putBoolean("notifySecurity", s.notifySecurity)
            .putString("interests", JSONArray(s.interests).toString())
            .putInt("sensitivity", s.sensitivity)
            .putInt("intervalMin", s.intervalMin)
            .putBoolean("notifyHn", s.notifyHn)
            .putBoolean("notifyGh", s.notifyGh)
            .putString("stackDependencies", JSONArray(s.stackDependencies).toString())
            .putLong("lastBriefingSeenSec", s.lastBriefingSeenSec)
            .putBoolean("ghOfficial", s.ghOfficial)
            .apply()
    }

    private fun strings(key: String): List<String> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            List(array.length()) { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY_SRC = "src"
        private const val KEY_MODE = "mode"
        private const val KEY_WIN = "win"
        private const val KEY_SEEN = "seen"
        private const val KEY_SCHEMA = "schema"
        private const val SCHEMA = 2
        private const val MAX_SEEN = 200
        private const val MAX_INTERESTS = 12

        @Volatile private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
