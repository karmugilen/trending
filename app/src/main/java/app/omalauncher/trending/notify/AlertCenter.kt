package app.omalauncher.trending.notify

import android.content.Context
import android.util.Log
import app.omalauncher.trending.core.TrendingCore
import app.omalauncher.trending.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

class AlertCenter private constructor(context: Context) {
    private val context = context.applicationContext
    private val mutex = Mutex()

    suspend fun check(slots: List<String>): Int = try {
        mutex.withLock {
            val settings = SettingsStore.get(context).value
            if (!settings.notify || (!settings.notifyBreaking && !settings.notifySecurity && settings.interests.isEmpty())) {
                return@withLock 0
            }
            // Without permission nothing can be shown; leave the state alone so
            // these rows still notify once the user allows notifications.
            if (!Notifier.canPost(context)) return@withLock 0
            val stateFile = File(context.filesDir, "alert-state.json")
            val state = withContext(Dispatchers.IO) {
                try {
                    if (stateFile.exists()) stateFile.readText().also { JSONObject(it) } else ""
                } catch (e: Exception) {
                    Log.w(TAG, "could not read alert state", e)
                    ""
                }
            }
            val result = withContext(Dispatchers.Default) {
                TrendingCore.alerts(slots, settings.alertConfig(), state, System.currentTimeMillis() / 1000)
            }
            withContext(Dispatchers.IO) {
                val temp = File(context.filesDir, "alert-state.json.tmp")
                try {
                    temp.writeText(result.state)
                    if (!temp.renameTo(stateFile)) throw IOException("could not replace alert state")
                } finally {
                    temp.delete()
                }
            }
            Notifier.post(context, result.alerts, result.suppressed)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "could not check alerts", e)
        0
    }

    companion object {
        private const val TAG = "AlertCenter"
        @Volatile private var instance: AlertCenter? = null

        fun get(context: Context): AlertCenter =
            instance ?: synchronized(this) {
                instance ?: AlertCenter(context).also { instance = it }
            }
    }
}
