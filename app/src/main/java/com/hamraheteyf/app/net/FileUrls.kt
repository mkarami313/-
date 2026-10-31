package com.hamraheteyf.app.net

import com.hamraheteyf.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Result of the server's `fileUrls` action: id → URL relative to the API base, plus the batch expiry (ms). */
data class ResolvedUrls(val urls: Map<String, String>, val expiresAtMs: Long)

/**
 * Signed-URL cache keyed by file id. [url] is instant (cached signed URL, else the legacy bare URL, which
 * servers still accept until they enforce signing); [resolve] fetches/refreshes through [fetch], batching
 * ids that queue up while a request is in flight. Entries are refreshed once less than [REFRESH_MARGIN_MS] remain.
 */
class FileUrlResolver(
    private val base: String,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val fetch: suspend (List<String>) -> ResolvedUrls?,
) {
    private class Entry(val url: String, val expMs: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val mutex = Mutex()
    @Volatile private var failedAt = 0L

    fun bare(id: String) = base + "files/" + id

    /** A still-valid signed URL, or null. */
    fun cached(id: String): String? = cache[id]?.takeIf { it.expMs - now() > MIN_VALID_MS }?.url

    fun needsRefresh(id: String): Boolean = cache[id]?.let { it.expMs - now() < REFRESH_MARGIN_MS } ?: true

    /** Signed URL when we have one, else the bare URL. Never blocks. */
    fun url(id: String): String = cached(id) ?: bare(id)

    /** Signed URL for [id] (fetching if missing/near expiry or [force]); falls back to the bare URL when offline or not permitted. */
    suspend fun resolve(id: String, force: Boolean = false): String {
        if (!force && !needsRefresh(id)) return url(id)
        pending.add(id)
        mutex.withLock {
            if (!force && !needsRefresh(id)) return url(id)
            // A recent failure (offline) must not make every image in a list wait on the network again.
            if (!force && now() - failedAt < FAIL_BACKOFF_MS) { pending.remove(id); return url(id) }
            val batch = (listOf(id) + pending.filter { it != id && needsRefresh(it) }).take(MAX_BATCH)
            pending.removeAll(batch.toSet())
            val r = runCatching { fetch(batch) }.getOrNull()
            if (r == null) failedAt = now() else { failedAt = 0; store(r) }
        }
        return url(id)
    }

    private fun store(r: ResolvedUrls) {
        r.urls.forEach { (id, rel) ->
            val abs = if (rel.startsWith("http")) rel else base + rel.trimStart('/')
            val exp = Regex("[?&]exp=(\\d+)").find(abs)?.groupValues?.get(1)?.toLongOrNull()?.let { it * 1000 } ?: r.expiresAtMs
            cache[id] = Entry(abs, exp)
        }
    }

    fun clear() { cache.clear(); pending.clear(); failedAt = 0 }

    companion object {
        const val REFRESH_MARGIN_MS = 60 * 60_000L
        private const val MIN_VALID_MS = 30_000L
        private const val FAIL_BACKOFF_MS = 15_000L
        private const val MAX_BATCH = 100

        private val ID_RE = Regex("/files/([A-Za-z0-9_-]{8,64})(?:[?#].*)?$")

        /** File id when [url] points at the files endpoint (bare or signed), else null. */
        fun idOf(url: String): String? = ID_RE.find(url)?.groupValues?.get(1)
    }
}

/** App-wide resolver bound to the signed-in [HamrahApi]. Safe to call before [attach] (falls back to bare URLs). */
object FileUrls {
    @Volatile private var resolver: FileUrlResolver? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun attach(api: HamrahApi) {
        resolver = FileUrlResolver(BuildConfig.API_BASE_URL) { ids -> fetchVia(api, ids) }
    }

    fun idOf(url: String): String? = FileUrlResolver.idOf(url)

    /** Cache key for image/audio caches: the file id for files URLs (so a re-signed URL still hits), else the URL. */
    fun cacheKey(url: String): String = idOf(url)?.let { "f:$it" } ?: url

    /** Instant URL for [id]; also warms/refreshes the signed URL in the background. */
    fun url(id: String): String {
        val r = resolver ?: return BuildConfig.API_BASE_URL + "files/" + id
        if (r.needsRefresh(id)) scope.launch { r.resolve(id) }
        return r.url(id)
    }

    suspend fun resolve(id: String, force: Boolean = false): String =
        resolver?.resolve(id, force) ?: (BuildConfig.API_BASE_URL + "files/" + id)

    /** Re-signs a files URL (bare or stale-signed); any other URL is returned unchanged. */
    suspend fun resolveUrl(url: String, force: Boolean = false): String =
        idOf(url)?.let { resolve(it, force) } ?: url

    fun clear() { resolver?.clear() }

    /** Downloads a file by id (signed URL, re-signed once on 401/403). Null on any failure. */
    suspend fun download(id: String, connectMs: Int = 15_000, readMs: Int = 30_000): ByteArray? = withContext(Dispatchers.IO) {
        suspend fun get(url: String): Pair<Int, ByteArray?> = runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = connectMs; c.readTimeout = readMs
                val code = c.responseCode
                code to (if (code in 200..299) c.inputStream.use { it.readBytes() } else null)
            } finally { c.disconnect() }
        }.getOrDefault(0 to null)
        val (code, bytes) = get(resolve(id))
        if (bytes != null) return@withContext bytes
        if (code == 401 || code == 403) get(resolve(id, force = true)).second else null
    }

    private suspend fun fetchVia(api: HamrahApi, ids: List<String>): ResolvedUrls? {
        if (api.token == null) return null
        val args = buildJsonObject { put("ids", JsonArray(ids.map { JsonPrimitive(it) })) }.toString()
        val data = (api.action("fileUrls", args) as? ApiResult.Ok)?.data?.data ?: return null
        val obj = runCatching { data.jsonObject }.getOrNull() ?: return null
        val urls = (obj["urls"] as? JsonObject)?.mapNotNull { (k, v) -> v.jsonPrimitive.contentOrNull?.let { k to it } }?.toMap() ?: return null
        return ResolvedUrls(urls, obj["expiresAt"]?.jsonPrimitive?.longOrNull ?: (System.currentTimeMillis() + 24 * 3600_000L))
    }
}
