package app.omalauncher.trending.notify

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.omalauncher.trending.data.Settings
import java.util.concurrent.TimeUnit

object WatchScheduler {
    private var lastApplied: Pair<Boolean, Int>? = null
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    @Synchronized
    fun apply(context: Context, settings: Settings) {
        val interval = settings.intervalMin.coerceAtLeast(15)
        val next = settings.notify to interval
        if (lastApplied == next) return
        val manager = WorkManager.getInstance(context.applicationContext)
        if (settings.notify) {
            val request = PeriodicWorkRequestBuilder<WatchWorker>(interval.toLong(), TimeUnit.MINUTES)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            manager.enqueueUniquePeriodicWork("trending-watch", ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            manager.cancelUniqueWork("trending-watch")
        }
        lastApplied = next
    }

    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WatchWorker>()
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork("trending-watch-now", ExistingWorkPolicy.REPLACE, request)
    }
}
