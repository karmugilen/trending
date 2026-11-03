package app.omalauncher.trending.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A non-2xx answer. [retryAfterSec] comes from Retry-After or GitHub's rate limit reset, when sent. */
class HttpStatusException(val code: Int, val retryAfterSec: Long? = null) : IOException("http $code")

/**
 * One shared HTTP client for the screen and the background worker, so they
 * share connections. URLs come from TrendingCore.hnUrl / ghUrl.
 */
class FeedClient private constructor(private val http: OkHttpClient) {

    /** GET [url]. Retries network errors and 5xx twice with backoff; never retries 4xx. */
    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        var attempt = 0
        while (true) {
            try {
                return@withContext get(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                val retryable = e !is HttpStatusException || e.code >= 500
                if (!retryable || attempt >= RETRIES) throw e
                delay(BACKOFF_MS[attempt])
                attempt++
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private suspend fun get(url: String): String =
        suspendCancellableCoroutine { cont ->
            val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
            if (url.startsWith("https://api.github.com/")) {
                builder.header("Accept", "application/vnd.github+json")
                builder.header("X-GitHub-Api-Version", "2022-11-28")
            }
            val call = http.newCall(builder.build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            if (cont.isActive) cont.resumeWithException(HttpStatusException(resp.code, retryAfter(resp)))
                            return
                        }
                        val body = try {
                            resp.body?.string().orEmpty()
                        } catch (e: IOException) {
                            if (cont.isActive) cont.resumeWithException(e)
                            return
                        }
                        if (cont.isActive) cont.resume(body)
                    }
                }
            })
        }

    private fun retryAfter(resp: okhttp3.Response): Long? {
        resp.header("Retry-After")?.trim()?.toLongOrNull()?.let { return it }
        if (resp.header("X-RateLimit-Remaining") == "0") {
            val reset = resp.header("X-RateLimit-Reset")?.toLongOrNull() ?: return null
            return (reset - System.currentTimeMillis() / 1000).coerceAtLeast(1)
        }
        return null
    }

    companion object {
        private const val USER_AGENT = "omalauncher-trending/1.2"
        private const val RETRIES = 2
        private val BACKOFF_MS = longArrayOf(800, 2500)
        private const val CACHE_SIZE = 15L * 1024 * 1024

        @Volatile
        private var instance: FeedClient? = null

        fun init(cacheDir: File) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = buildClient(cacheDir)
                    }
                }
            }
        }

        val shared: FeedClient
            get() = instance ?: synchronized(this) {
                instance ?: buildClient(null).also { instance = it }
            }

        private fun buildClient(cacheDir: File?): FeedClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
            if (cacheDir != null) {
                try {
                    builder.cache(Cache(File(cacheDir, "http_cache"), CACHE_SIZE))
                } catch (_: Exception) {}
            }
            return FeedClient(builder.build())
        }
    }
}
