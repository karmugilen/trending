package app.omalauncher.trending.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.omalauncher.trending.MainActivity
import app.omalauncher.trending.R
import app.omalauncher.trending.core.TrendingCore
import app.omalauncher.trending.logic.Alert
import app.omalauncher.trending.logic.AlertKind
import app.omalauncher.trending.logic.Source

object Notifier {
    const val EXTRA_STORY_ID = "story_id"
    const val EXTRA_OPEN_URL = "open_url"

    private const val COLOR = 0xFFE68E0D.toInt()
    private const val INTENT_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel("breaking", "Rising fast", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel("security", "Security alerts", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel("interests", "Your interests", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    /** Returns the number of alert notifications submitted, excluding summaries. */
    /** True when the app may show notifications right now. */
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun post(context: Context, alerts: List<Alert>, suppressed: Int): Int {
        val manager = NotificationManagerCompat.from(context)
        if (!canPost(context) || alerts.isEmpty()) return 0
        createChannels(context)

        val now = System.currentTimeMillis() / 1000
        var posted = 0
        for ((kind, group) in alerts.groupBy { it.kind }) {
            val delivered = mutableListOf<Alert>()
            for (alert in group) {
                val score = TrendingCore.fmtCompact(alert.score)
                val stats = listOf(
                    if (alert.source == Source.HN) "$score points" else "$score stars",
                    "${TrendingCore.fmtCompact(alert.heat)} per hour",
                    TrendingCore.fmtAgo(alert.t, now),
                ).joinToString(" · ")
                val detail = listOf(alert.blurb, stats).filter { it.isNotBlank() }.joinToString("\n\n")
                val builder = base(context, kind)
                    .setContentTitle(title(alert))
                    .setContentText(alert.title)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(
                        listOf(alert.title, detail).joinToString("\n\n"),
                    ))
                    .setWhen(alert.t * 1000L)
                    .setContentIntent(openIntent(context, alert.id, alert.id, alert.url))
                    .addAction(0, "Open Trending", openIntent(context, "${alert.id}:app", alert.id, url = null))
                if (alert.source == Source.HN && alert.talk.isNotBlank()) {
                    builder.addAction(0, "Discussion", openIntent(context, "${alert.id}:comments", alert.id, alert.talk))
                }
                try {
                    manager.notify(alert.id.hashCode(), builder.build())
                    delivered.add(alert)
                    posted++
                } catch (_: SecurityException) {
                    // Permission can change between the check and notify.
                }
            }
            if (delivered.size >= 2) {
                val more = if (suppressed > 0) " · $suppressed more in the app" else ""
                val summary = "${count(kind, delivered.size)}$more"
                val style = NotificationCompat.InboxStyle().setSummaryText(summary)
                delivered.forEach { style.addLine(it.title) }
                val notification = base(context, kind)
                    .setContentTitle(label(kind))
                    .setContentText(summary)
                    .setStyle(style)
                    .setContentIntent(openIntent(context, "summary:${kind.key}", storyId = null, url = null))
                    .setGroupSummary(true)
                    .setSilent(true)
                    .build()
                try {
                    manager.notify("summary:${kind.key}", 0, notification)
                } catch (_: SecurityException) {
                    // Keep any children already posted.
                }
            }
        }
        return posted
    }

    fun postTest(context: Context) {
        val now = System.currentTimeMillis() / 1000
        post(
            context,
            listOf(
                Alert(
                    id = "notification-preview",
                    kind = AlertKind.BREAKING,
                    source = Source.HN,
                    title = "A new project is taking off on Hacker News",
                    url = "https://news.ycombinator.com/",
                    talk = "https://news.ycombinator.com/",
                    score = 128.0,
                    extra = 32.0,
                    heat = 85.0,
                    t = now - 90 * 60,
                    reason = "85 per hour",
                    blurb = "This is how your Trending alerts will look.",
                    meta = "",
                ),
            ),
            suppressed = 0,
        )
    }

    private fun base(context: Context, kind: AlertKind): NotificationCompat.Builder =
        NotificationCompat.Builder(context, if (kind == AlertKind.INTEREST) "interests" else kind.key)
            .setSmallIcon(R.drawable.ic_stat_trending)
            .setColor(COLOR)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(if (kind == AlertKind.SECURITY) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(if (kind == AlertKind.INTEREST) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setGroup("trending.${kind.key}")
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)

    private fun label(kind: AlertKind): String = when (kind) {
        AlertKind.BREAKING -> "Rising fast"
        AlertKind.SECURITY -> "Security alerts"
        AlertKind.INTEREST -> "Your interests"
    }

    private fun count(kind: AlertKind, n: Int): String = when (kind) {
        AlertKind.BREAKING -> "$n stories rising fast"
        AlertKind.SECURITY -> "$n security alerts"
        AlertKind.INTEREST -> "$n matches for your interests"
    }

    private fun title(alert: Alert): String {
        val source = if (alert.source == Source.HN) "Hacker News" else "GitHub"
        return when (alert.kind) {
            AlertKind.BREAKING -> "Rising fast on $source"
            AlertKind.SECURITY -> {
                // "security" is the generic label; a CVE id or "RCE" says more.
                val what = alert.reason.takeIf { it.isNotBlank() && it != "security" }
                if (what != null) "Security · $what · $source" else "Security alert on $source"
            }
            AlertKind.INTEREST -> "Matches “${alert.reason}” on $source"
        }
    }

    /**
     * Opens [MainActivity] so the tap can mark [storyId] as read (dims the row)
     * and optionally launch [url] in the browser / Custom Tab.
     */
    private fun openIntent(
        context: Context,
        key: String,
        storyId: String?,
        url: String?,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction("${context.packageName}.notification.$key")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (!storyId.isNullOrBlank()) intent.putExtra(EXTRA_STORY_ID, storyId)
        if (!url.isNullOrBlank()) intent.putExtra(EXTRA_OPEN_URL, url)
        return PendingIntent.getActivity(context, key.hashCode(), intent, INTENT_FLAGS)
    }
}
