package app.omalauncher.trending.data

import android.content.Context
import android.util.Log
import app.omalauncher.trending.logic.Story
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A story the user kept, with when they kept it. */
data class Saved(val story: Story, val savedAt: Long)

/**
 * Stories saved for later. They outlive the feed slots, which only hold the
 * last day, so they are kept whole in their own file.
 */
class SavedStore private constructor(context: Context) {
    private val file = File(context.applicationContext.filesDir, "saved.json")
    private val state = MutableStateFlow(read())

    /** Newest first. */
    val flow: StateFlow<List<Saved>> = state.asStateFlow()

    fun isSaved(id: String): Boolean = state.value.any { it.story.id == id }

    /** Save [story], or remove it when it is already saved. Returns true when it is now saved. */
    fun toggle(story: Story, nowSec: Long): Boolean {
        synchronized(this) {
            val current = state.value
            val saved = current.none { it.story.id == story.id }
            val next = if (saved) {
                (listOf(Saved(story, nowSec)) + current).take(MAX)
            } else {
                current.filterNot { it.story.id == story.id }
            }
            state.value = next
            write(next)
            return saved
        }
    }

    fun restore(items: List<Saved>) {
        synchronized(this) {
            state.value = items
            write(items)
        }
    }

    private fun read(): List<Saved> {
        if (!file.isFile) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isEmpty()) return@mapNotNull null
                Saved(
                    story = Story(
                        id = id,
                        title = o.optString("title"),
                        url = o.optString("url"),
                        talk = o.optString("talk"),
                        score = o.optDouble("score", 0.0),
                        extra = o.optDouble("extra", 0.0),
                        meta = o.optString("meta"),
                        blurb = o.optString("blurb"),
                        t = o.optLong("t"),
                        flags = o.optInt("flags"),
                        tag = o.optString("tag"),
                        domain = o.optString("domain"),
                    ),
                    savedAt = o.optLong("savedAt"),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "saved list unreadable", e)
            emptyList()
        }
    }

    private fun write(items: List<Saved>) {
        val array = JSONArray()
        for ((s, at) in items) {
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("title", s.title)
                    .put("url", s.url)
                    .put("talk", s.talk)
                    .put("score", s.score)
                    .put("extra", s.extra)
                    .put("meta", s.meta)
                    .put("blurb", s.blurb)
                    .put("t", s.t)
                    .put("flags", s.flags)
                    .put("tag", s.tag)
                    .put("domain", s.domain)
                    .put("savedAt", at),
            )
        }
        try {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(array.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(array.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not save the saved list", e)
        }
    }

    companion object {
        private const val TAG = "SavedStore"
        private const val MAX = 500

        @Volatile private var instance: SavedStore? = null

        fun get(context: Context): SavedStore =
            instance ?: synchronized(this) {
                instance ?: SavedStore(context).also { instance = it }
            }
    }
}
