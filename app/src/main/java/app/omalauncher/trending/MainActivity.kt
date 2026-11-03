package app.omalauncher.trending

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.omalauncher.trending.data.SavedStore
import app.omalauncher.trending.data.SettingsStore
import app.omalauncher.trending.data.ThemeMode
import app.omalauncher.trending.logic.Story
import app.omalauncher.trending.notify.Notifier
import app.omalauncher.trending.notify.WatchScheduler
import app.omalauncher.trending.ui.RadarScreen
import app.omalauncher.trending.ui.SavedScreen
import app.omalauncher.trending.ui.SettingsScreen
import app.omalauncher.trending.ui.TrendingActions
import app.omalauncher.trending.ui.TrendingScreen
import app.omalauncher.trending.ui.TrendingTheme
import app.omalauncher.trending.ui.isDarkTheme

class MainActivity : ComponentActivity() {
    private val model: TrendingViewModel by viewModels()
    private val store by lazy { SettingsStore.get(this) }
    private val savedStore by lazy { SavedStore.get(this) }
    private var notificationsAllowed by mutableStateOf(false)

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = canNotify()
        if (granted) {
            WatchScheduler.checkNow(this)
        } else if (Build.VERSION.SDK_INT >= 33 && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            // Denied for good; only system settings can turn it back on.
            openNotificationSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by store.flow.collectAsState()
            val ui by model.ui.collectAsState()
            val saved by savedStore.flow.collectAsState()
            val briefing by model.briefing.collectAsState()
            val dark = isDarkTheme(settings)
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            TrendingTheme(settings) {
                var page by rememberSaveable { mutableStateOf(PAGE_FEED) }
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "page",
                ) { current ->
                    if (current == PAGE_RADAR) {
                        RadarScreen(
                            briefing = briefing,
                            nowSec = ui.nowSec,
                            seen = ui.seen,
                            onBack = { page = PAGE_FEED },
                            onOpen = { story -> model.markSeen(story.id); openUrl(story.url) },
                            onOpenThread = { story -> model.markSeen(story.id); openUrl(story.talk) },
                            onToggleSave = { story -> savedStore.toggle(story, ui.nowSec) },
                            isSaved = { id -> saved.any { it.story.id == id } },
                            onMarkCaughtUp = model::markBriefingCaughtUp,
                            onRefresh = model::loadBriefing,
                        )
                    } else if (current == PAGE_SAVED) {
                        SavedScreen(
                            saved = saved,
                            nowSec = ui.nowSec,
                            onBack = { page = PAGE_FEED },
                            onOpen = { story -> model.markSeen(story.id); openUrl(story.url) },
                            onOpenThread = { story -> model.markSeen(story.id); openUrl(story.talk) },
                            onShare = ::share,
                            onRemove = { story -> savedStore.toggle(story, ui.nowSec) },
                            onRestore = savedStore::restore,
                        )
                    } else if (current != PAGE_FEED) {
                        SettingsScreen(
                            settings = settings,
                            notificationsAllowed = notificationsAllowed,
                            onChange = store::update,
                            onBack = { page = PAGE_FEED },
                            onRequestNotifications = ::requestNotifications,
                            onTestNotification = {
                                if (canNotify()) Notifier.postTest(this@MainActivity) else requestNotifications()
                            },
                            onCheckNow = {
                                WatchScheduler.checkNow(this@MainActivity)
                                Toast.makeText(this@MainActivity, "Checking for trending news…", Toast.LENGTH_SHORT).show()
                            },
                            startAtAlerts = current == PAGE_ALERTS,
                        )
                    } else {
                        TrendingScreen(
                            ui = ui,
                            chartExpanded = settings.chartExpanded,
                            notifying = settings.notify && notificationsAllowed,
                            saved = remember(saved) { saved.mapTo(HashSet()) { it.story.id } },
                            hasInterests = settings.interests.isNotEmpty(),
                            actions = TrendingActions(
                                onSource = model::selectSource,
                                onMode = model::selectMode,
                                onHours = model::selectHours,
                                onRefresh = model::refresh,
                                onQuery = model::setQuery,
                                onShowMore = model::showMore,
                                onOpen = { url, id ->
                                    model.markSeen(id)
                                    openUrl(url)
                                },
                                onShare = ::share,
                                onCopy = ::copyLink,
                                onMarkSeen = model::markSeen,
                                onMarkUnseen = model::markUnseen,
                                onClearSeen = model::clearSeen,
                                onRestoreSeen = model::restoreSeen,
                                onChartExpanded = { open -> store.update { it.copy(chartExpanded = open) } },
                                onSettings = { page = PAGE_SETTINGS },
                                onNotificationSettings = { page = PAGE_ALERTS },
                                onFilter = model::selectFilter,
                                onTopic = model::toggleTopic,
                                onLang = model::toggleLang,
                                onBar = model::toggleBar,
                                onClearNarrowing = model::clearNarrowing,
                                onRadar = { model.loadBriefing(); page = PAGE_RADAR },
                                onGhOfficial = model::selectGhOfficial,
                                onToggleSaved = { story -> savedStore.toggle(story, System.currentTimeMillis() / 1000) },
                                onSaved = { page = PAGE_SAVED },
                                onDiscussions = model::discussions,
                            ),
                        )
                    }
                }
            }
        }
        // After composition so Custom Tabs / browser open reliably from a cold start.
        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        model.onStart()
    }

    override fun onResume() {
        super.onResume()
        // The user may have changed the permission in system settings.
        notificationsAllowed = canNotify()
    }

    /** Mark the story as read (dims the feed row) and open its link when a notification was tapped. */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val id = intent.getStringExtra(Notifier.EXTRA_STORY_ID)
        val url = intent.getStringExtra(Notifier.EXTRA_OPEN_URL)
        if (id.isNullOrBlank() && url.isNullOrBlank()) return
        intent.removeExtra(Notifier.EXTRA_STORY_ID)
        intent.removeExtra(Notifier.EXTRA_OPEN_URL)
        if (!id.isNullOrBlank()) {
            model.markSeen(id)
            NotificationManagerCompat.from(this).cancel(id.hashCode())
        }
        if (!url.isNullOrBlank()) openUrl(url)
    }

    override fun onStop() {
        model.onStop()
        super.onStop()
    }

    private fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotifications() {
        when {
            canNotify() -> notificationsAllowed = true
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> openNotificationSettings()
        }
    }

    private fun openNotificationSettings() {
        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Turn on notifications for Trending in system settings", Toast.LENGTH_LONG).show()
        }
    }

    private fun share(story: Story) {
        val text = if (story.talk.isNotEmpty() && story.talk != story.url && story.id.startsWith("hn:")) {
            "${story.title}\n${story.url}\n\nDiscussion: ${story.talk}"
        } else {
            "${story.title}\n${story.url}"
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, story.title)
            .putExtra(Intent.EXTRA_TEXT, text)
        try {
            startActivity(Intent.createChooser(send, "Share"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app can share this", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyLink(story: Story) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(story.title, story.url))
        // Android 13+ shows its own confirmation.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
    }

    private fun openUrl(url: String) {
        val uri = Uri.parse(url)
        if (store.value.openInApp) {
            val scheme = if (darkNow()) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT
            try {
                CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setColorScheme(scheme)
                    .setUrlBarHidingEnabled(true)
                    .build()
                    .launchUrl(this, uri)
                return
            } catch (_: ActivityNotFoundException) {
                // No browser with Custom Tabs; fall through to any app that can open it.
            }
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show()
        }
    }

    /** [isDarkTheme] outside composition, for the Custom Tab colors. */
    private fun darkNow(): Boolean = when (store.value.themeMode) {
        ThemeMode.SYSTEM ->
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }

    private companion object {
        const val PAGE_FEED = "feed"
        const val PAGE_RADAR = "radar"
        const val PAGE_SETTINGS = "settings"
        /** Settings, scrolled to the alerts section. */
        const val PAGE_ALERTS = "alerts"
        const val PAGE_SAVED = "saved"
    }
}
