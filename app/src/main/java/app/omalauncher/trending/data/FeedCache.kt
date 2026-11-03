package app.omalauncher.trending.data

import android.content.Context
import android.util.Log
import app.omalauncher.trending.core.TrendingCore
import java.io.File

/**
 * Keeps Rust's parsed slots on disk, so a cold start shows the last feed
 * at once and the app still has something to show offline. Files hold the
 * compact parsed rows, not the raw API pages.
 */
class FeedCache(context: Context) {
    private val dir = File(context.applicationContext.cacheDir, "feeds")

    /** Load [slot] from disk into Rust unless Rust already has something newer. */
    fun restore(slot: String): Boolean {
        val file = fileFor(slot)
        if (!file.isFile) return false
        return try {
            TrendingCore.import(slot, file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "dropping unreadable cache $slot", e)
            file.delete()
            false
        }
    }

    /** Delete cached slots that are not in [keep], such as ones from older versions. */
    fun prune(keep: Set<String>) {
        val names = keep.map { fileFor(it).name }.toSet()
        dir.listFiles()?.filter { it.name !in names }?.forEach { it.delete() }
    }

    fun save(slot: String) {
        val json = TrendingCore.export(slot) ?: return
        try {
            dir.mkdirs()
            val tmp = File(dir, "${fileFor(slot).name}.tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(fileFor(slot))) tmp.delete()
        } catch (e: Exception) {
            Log.w(TAG, "could not cache $slot", e)
        }
    }

    private fun fileFor(slot: String) = File(dir, slot.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    private companion object {
        const val TAG = "FeedCache"
    }
}
