package com.hamraheteyf.app.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

private fun download(url: String): Bitmap? = runCatching {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.connectTimeout = 15000; c.readTimeout = 30000
        if (c.responseCode !in 200..299) return null
        decodeSampled(c.inputStream.use { it.readBytes() }, 900)
    } finally { c.disconnect() }
}.getOrNull()

/** Loads an image over HTTP with a small in-memory cache (no third-party image library). */
@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var bmp by remember(url) { mutableStateOf(ImageCache.get(url)) }
    LaunchedEffect(url) {
        if (bmp == null) {
            bmp = withContext(Dispatchers.IO) { download(url) }?.also { ImageCache.put(url, it) }
        }
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = contentScale)
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
