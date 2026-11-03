package app.omalauncher.trending

import android.app.Application
import app.omalauncher.trending.data.SettingsStore
import app.omalauncher.trending.notify.Notifier
import app.omalauncher.trending.notify.WatchScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TrendingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app.omalauncher.trending.data.FeedClient.init(cacheDir)
        Notifier.createChannels(this)
        scope.launch {
            val store = SettingsStore.get(this@TrendingApp)
            store.flow.map { it.notify to it.intervalMin }.distinctUntilChanged().collect {
                WatchScheduler.apply(this@TrendingApp, store.value)
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
