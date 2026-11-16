package app.omalauncher.trending.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.omalauncher.trending.MainActivity
import app.omalauncher.trending.R
import app.omalauncher.trending.core.FeedOptions
import app.omalauncher.trending.core.TrendingCore
import app.omalauncher.trending.data.FeedCache
import app.omalauncher.trending.data.SettingsStore
import app.omalauncher.trending.logic.Flags
import app.omalauncher.trending.logic.Mode
import app.omalauncher.trending.logic.Story

/**
 * Home-screen widget listing the hottest breakout / rising posts from cached feeds.
 * Add via long-press home → Widgets → Trending → Breakouts.
 */
class BreakoutWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateOne(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            refreshAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "app.omalauncher.trending.widget.BREAKOUT_REFRESH"

        fun refreshAll(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, BreakoutWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { updateOne(app, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.breakout_widget)
            val open = PendingIntent.getActivity(
                context,
                id,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            val rows = loadBreakouts(context).take(3)
            val rowIds = intArrayOf(R.id.widget_row1, R.id.widget_row2, R.id.widget_row3)
            if (rows.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                rowIds.forEach { views.setViewVisibility(it, View.GONE) }
                views.setTextViewText(R.id.widget_updated, "")
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                rowIds.forEachIndexed { i, viewId ->
                    if (i < rows.size) {
                        views.setViewVisibility(viewId, View.VISIBLE)
                        views.setTextViewText(viewId, rows[i])
                    } else {
                        views.setViewVisibility(viewId, View.GONE)
                    }
                }
                views.setTextViewText(R.id.widget_updated, "live")
            }
            manager.updateAppWidget(id, views)
        }

        private fun loadBreakouts(context: Context): List<String> {
            return try {
                val cache = FeedCache(context)
                cache.restoreHistory()
                val slots = listOf(
                    "ui:hn",
                    "ui:gh:1", "ui:gh:6", "ui:gh:12", "ui:gh:24",
                    "ui:gh:off:1", "ui:gh:off:6", "ui:gh:off:24",
                )
                slots.forEach { cache.restore(it) }

                val settings = SettingsStore.get(context).value
                val now = System.currentTimeMillis() / 1000
                val options = FeedOptions(
                    hideSpam = settings.hideSpam,
                    interests = settings.interests,
                    sensitivity = settings.sensitivity,
                    limit = 40,
                )
                val scored = LinkedHashMap<String, Pair<Story, Double>>()
                fun consider(slot: String, hours: Int) {
                    val feed = TrendingCore.feed(slot, Mode.RISING, hours, now, options) ?: return
                    for (story in feed.shown) {
                        val breakout = story.has(Flags.BREAKOUT) ||
                            (story.has(Flags.RISING) && story.heat >= 25.0) ||
                            (story.id.startsWith("gh:trending:") && story.extra >= 200.0)
                        if (!breakout) continue
                        val score = story.heat.coerceAtLeast(0.0) +
                            (if (story.has(Flags.BREAKOUT)) 40.0 else 0.0) +
                            (if (story.id.startsWith("gh:trending:")) story.extra / 50.0 else 0.0)
                        val prev = scored[story.id]
                        if (prev == null || score > prev.second) {
                            scored[story.id] = story to score
                        }
                    }
                }
                consider("ui:hn", 24)
                consider("ui:gh:6", 6)
                consider("ui:gh:24", 24)
                consider("ui:gh:off:1", 1)
                consider("ui:gh:off:6", 6)
                consider("ui:gh:off:24", 24)

                scored.values
                    .sortedByDescending { it.second }
                    .take(3)
                    .map { (story, _) ->
                        val tag = when {
                            story.id.startsWith("gh:trending:") -> "Trending"
                            story.id.startsWith("gh:") -> "New"
                            else -> "HN"
                        }
                        val heat = if (story.heat >= 1) " · ${TrendingCore.fmtCompact(story.heat)}/h" else ""
                        "⚡ $tag · ${story.title}$heat"
                    }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
