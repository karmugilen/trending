package app.omalauncher.trending.notify

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.omalauncher.trending.core.GhQuery
import app.omalauncher.trending.core.TrendingCore
import app.omalauncher.trending.data.FeedClient
import app.omalauncher.trending.data.SettingsStore
import app.omalauncher.trending.logic.Source
import kotlinx.coroutines.CancellationException
import java.io.IOException

class WatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = SettingsStore.get(applicationContext).value
        if (!settings.notify) return Result.success()

        val now = System.currentTimeMillis() / 1000
        val dayAgo = now - 24 * 3600
        val threeDaysAgo = now - 72 * 3600
        val slots = mutableListOf<String>()
        var attempts = 0
        var ioFailures = 0

        suspend fun fetch(slot: String, source: Source, origin: Long, url: () -> String) {
            attempts++
            try {
                val body = FeedClient.shared.fetch(url())
                TrendingCore.ingest(slot, source, origin, now, body, append = false)
                slots.add(slot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is IOException) ioFailures++
                Log.w("WatchWorker", "could not fetch $slot", e)
            }
        }

        if (settings.notifyHn) {
            fetch("watch:hn", Source.HN, dayAgo) { TrendingCore.hnUrl(dayAgo) }
        }
        if (settings.notifyGh && settings.notifyBreaking) {
            fetch("watch:gh", Source.GH, dayAgo) { TrendingCore.ghUrl(GhQuery.NEW_REPOS, dayAgo) }
        }
        if (settings.notifyGh && settings.notifySecurity) {
            fetch("watch:ghsec", Source.GH, threeDaysAgo) { TrendingCore.ghUrl(GhQuery.SECURITY, threeDaysAgo) }
        }
        if (settings.notifyGh && settings.interests.isNotEmpty()) {
            fetch("watch:ghint", Source.GH, threeDaysAgo) {
                TrendingCore.ghUrl(GhQuery.INTERESTS, threeDaysAgo, settings.interests)
            }
        }

        if (slots.isNotEmpty()) {
            AlertCenter.get(applicationContext).check(slots)
            return Result.success()
        }
        return if (attempts > 0 && ioFailures == attempts) Result.retry() else Result.success()
    }
}
