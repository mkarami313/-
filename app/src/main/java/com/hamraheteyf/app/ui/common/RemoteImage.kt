package com.hamraheteyf.app.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hamraheteyf.app.net.FileUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL

private object ImageCache : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
}

private fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Size-capped (≈50 MB, least-recently-used first) on-disk cache of downloaded image bytes. */
object ImageDiskCache {
    private const val MAX_BYTES = 50L * 1024 * 1024
    private var puts = 0

    private fun dir(ctx: Context) = File(ctx.cacheDir, "img").apply { mkdirs() }
    private fun file(ctx: Context, url: String) =
        File(dir(ctx), MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".img")

    internal fun get(ctx: Context, url: String): ByteArray? = runCatching {
        val f = file(ctx, url)
        if (f.length() > 0) { f.setLastModified(System.currentTimeMillis()); f.readBytes() } else null
    }.getOrNull()

    internal fun put(ctx: Context, url: String, bytes: ByteArray) {
        runCatching {
            val f = file(ctx, url)
            val tmp = File(f.parentFile, f.name + ".part")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(f)) tmp.delete()
            if (++puts % 20 == 0) prune(ctx)
        }
    }

    fun prune(ctx: Context) = runCatching {
        val files = dir(ctx).listFiles().orEmpty().sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        for (f in files) { if (total <= MAX_BYTES) break; total -= f.length(); f.delete() }
    }

    fun clear(ctx: Context) { runCatching { dir(ctx).listFiles()?.forEach { it.delete() } }; ImageCache.evictAll() }
}

private class Fetched(val bytes: ByteArray?, val retryable: Boolean, val code: Int = 0)

private fun fetchBytes(url: String): Fetched = try {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.connectTimeout = 15000; c.readTimeout = 30000
        val code = c.responseCode
        if (code in 200..299) Fetched(c.inputStream.use { it.readBytes() }, false, code)
        else Fetched(null, code >= 500 || code == 408 || code == 429, code)
    } finally { c.disconnect() }
} catch (_: Exception) { Fetched(null, true) }

private val RETRY_DELAYS_MS = longArrayOf(0, 1000, 3000)

/** Memory → disk → network (3 attempts with back-off). Null means it failed. */
private suspend fun loadBitmap(ctx: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
    val key = FileUrls.cacheKey(url)
    ImageDiskCache.get(ctx, key)?.let { b -> decodeSampled(b, 900)?.let { return@withContext it } }
    var resigned = false
    for (d in RETRY_DELAYS_MS) {
        if (d > 0) delay(d)
        val r = fetchBytes(FileUrls.resolveUrl(url))
        val bytes = r.bytes
        if (bytes != null) {
            val bmp = decodeSampled(bytes, 900) ?: return@withContext null
            ImageDiskCache.put(ctx, key, bytes)
            return@withContext bmp
        }
        // Expired / rejected signature: fetch a fresh signed URL once and try again straight away.
        if ((r.code == 401 || r.code == 403) && !resigned && FileUrls.idOf(url) != null) {
            resigned = true
            FileUrls.resolveUrl(url, force = true)
            val again = fetchBytes(FileUrls.resolveUrl(url))
            val b2 = again.bytes
            if (b2 != null) {
                val bmp = decodeSampled(b2, 900) ?: return@withContext null
                ImageDiskCache.put(ctx, key, b2)
                return@withContext bmp
            }
            if (!again.retryable) break
            continue
        }
        if (!r.retryable) break
    }
    null
}

/** Loads an image over HTTP (memory + disk cache, retry with back-off). A failed load shows a tap-to-retry placeholder. */
@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, contentDescription: String? = null) {
    val ctx = LocalContext.current.applicationContext
    val key = FileUrls.cacheKey(url)  // a re-signed URL for the same file must not reload the image
    var bmp by remember(key) { mutableStateOf(ImageCache.get(key)) }
    var failed by remember(key) { mutableStateOf(false) }
    var attempt by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key, attempt) {
        if (bmp == null) {
            failed = false
            val loaded = loadBitmap(ctx, url)
            if (loaded != null) { ImageCache.put(key, loaded); bmp = loaded } else failed = true
        }
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
    } else if (failed) {
        Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClickLabel = "تلاش دوباره") { attempt++ }
                .semantics { this.contentDescription = "بارگذاری تصویر ناموفق بود. برای تلاش دوباره بزنید" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

/** Reads a picked image, fixes its rotation, shrinks it to ≤1280px and returns JPEG bytes (≈ well under 1 MB). */
suspend fun prepareImageForUpload(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val raw = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        var bmp = decodeSampled(raw, 1280) ?: return@runCatching null
        val rotation = runCatching {
            android.media.ExifInterface(raw.inputStream()).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (rotation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
        val scale = 1280f / maxOf(bmp.width, bmp.height)
        if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
    }.getOrNull()
}
