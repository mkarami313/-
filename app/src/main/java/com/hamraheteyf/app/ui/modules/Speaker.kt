package com.hamraheteyf.app.ui.modules

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.hamraheteyf.app.AppForeground
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.net.HamrahApi
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

private fun speechChunks(text: String): List<String> {
    val out = mutableListOf<String>(); val cur = StringBuilder()
    for (part in text.split(Regex("(?<=[.!؟?!،\\n])"))) {
        if (cur.length + part.length > 250 && cur.isNotEmpty()) { out += cur.toString().trim(); cur.clear() }
        cur.append(part)
    }
    if (cur.isNotBlank()) out += cur.toString().trim()
    return out.filter { it.isNotBlank() }.flatMap { it.chunked(280) }
}

/** Disk upkeep for audio caches (cacheDir/tts and leftover recordings). */
object SpeechCache {
    private const val MAX_BYTES = 40L * 1024 * 1024
    private const val REC_MAX_AGE_MS = 60L * 60 * 1000

    fun dir(ctx: Context) = File(ctx.cacheDir, "tts").apply { mkdirs() }

    /** Trims the clip cache to its size cap (oldest first) and drops stale recordings. */
    fun prune(ctx: Context) = runCatching {
        val files = dir(ctx).listFiles().orEmpty().sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        for (f in files) { if (total <= MAX_BYTES) break; total -= f.length(); f.delete() }
        val cutoff = System.currentTimeMillis() - REC_MAX_AGE_MS
        ctx.cacheDir.listFiles { f -> f.name.startsWith("rec_") && f.name.endsWith(".m4a") && f.lastModified() < cutoff }?.forEach { it.delete() }
    }

    /** Removes everything cached for the signed-in user (sign-out). */
    fun clear(ctx: Context) = runCatching {
        dir(ctx).listFiles()?.forEach { it.delete() }
        ctx.cacheDir.listFiles { f -> f.name.startsWith("rec_") }?.forEach { it.delete() }
    }
}

/**
 * Plays speech: a family-recorded clip when the item has one, otherwise a server-side Persian
 * voice (stock Android TTS has no Persian), otherwise — per item — the phone's own voice if it
 * has Persian. The device engine is only bound the first time it is actually needed.
 */
class Speaker(private val ctx: Context, private val api: HamrahApi?, private val scope: CoroutineScope) {
    private var job: Job? = null
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private val dir = SpeechCache.dir(ctx)

    init { scope.launch(Dispatchers.IO) { SpeechCache.prune(ctx) } }

    fun release() { job?.cancel(); runCatching { player?.release() }; tts?.shutdown(); tts = null }

    operator fun invoke(text: String, audioId: String? = null) = say(listOf(text to audioId))

    fun say(items: List<Pair<String, String?>>) {
        job?.cancel(); tts?.stop(); runCatching { player?.stop() }
        job = scope.launch {
            var noVoice = false
            for ((text, audioId) in items) {
                val files = if (!audioId.isNullOrBlank()) fetchClip(audioId)?.let { listOf(it) }
                else speechChunks(text).map { c -> fetchTts(c) }.takeIf { l -> l.isNotEmpty() && l.all { it != null } }?.filterNotNull()
                if (files != null) { files.forEach { play(it) }; continue }
                val t = deviceTts()
                if (t != null && t.isLanguageAvailable(Locale("fa")) >= 0) speakDevice(t, text) else noVoice = true
            }
            if (noVoice) Toast.makeText(ctx, "برای پخش صدا، اینترنت لازم است.", Toast.LENGTH_LONG).show()
        }
    }

    fun playLocal(f: File) { job?.cancel(); job = scope.launch { play(f) } }

    private suspend fun deviceTts(): TextToSpeech? {
        tts?.let { return it }
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine<TextToSpeech?> { cont ->
                val holder = arrayOfNulls<TextToSpeech>(1)
                holder[0] = TextToSpeech(ctx.applicationContext) { st ->
                    val t = holder[0]
                    if (st == TextToSpeech.SUCCESS && t != null) {
                        runCatching { t.language = Locale("fa") }
                        if (cont.isActive) cont.resume(t) {}
                    } else { t?.shutdown(); if (cont.isActive) cont.resume(null) {} }
                }
                cont.invokeOnCancellation { holder[0]?.shutdown() }
            }
        }?.also { tts = it }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private suspend fun speakDevice(t: TextToSpeech, text: String) {
        withTimeoutOrNull(60_000) {
            suspendCancellableCoroutine<Unit> { cont ->
                val id = "u${System.nanoTime()}"
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(Unit) {} }
                    override fun onError(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(Unit) {} }
                })
                if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS && cont.isActive) cont.resume(Unit) {}
                cont.invokeOnCancellation { runCatching { t.stop() } }
            }
        }
    }

    private suspend fun fetchClip(id: String): File? = withContext(Dispatchers.IO) {
        val f = File(dir, "clip_$id.snd")
        if (f.exists() && f.length() > 0) { f.setLastModified(System.currentTimeMillis()); return@withContext f }
        val url = api?.fileUrl(id) ?: return@withContext null
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                if (c.responseCode !in 200..299) return@runCatching null
                val tmp = File(dir, "clip_$id.part")
                c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                if (tmp.length() > 0 && tmp.renameTo(f)) f else { tmp.delete(); null }
            } finally { c.disconnect() }
        }.getOrNull()
    }

    private suspend fun fetchTts(chunk: String): File? {
        val f = File(dir, sha1(chunk) + ".snd")
        if (f.exists() && f.length() > 0) { f.setLastModified(System.currentTimeMillis()); return f }
        val b = api?.tts(chunk) ?: return null
        withContext(Dispatchers.IO) { f.writeBytes(b) }
        return f
    }

    private suspend fun play(file: File) = suspendCancellableCoroutine<Unit> { cont ->
        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setDataSource(file.path)
            mp.setOnCompletionListener { mp.release(); if (cont.isActive) cont.resume(Unit) {} }
            mp.setOnErrorListener { _, _, _ -> mp.release(); if (cont.isActive) cont.resume(Unit) {}; true }
            cont.invokeOnCancellation { runCatching { mp.stop(); mp.release() } }
            mp.prepare(); mp.start()
        }.onFailure { runCatching { mp.release() }; if (cont.isActive) cont.resume(Unit) {} }
    }
}

@Composable
fun rememberSpeaker(): Speaker {
    val ctx = LocalContext.current
    val api = LocalAppContainer.current.serverApi
    val scope = rememberCoroutineScope()
    val sp = remember(api) { Speaker(ctx, api, scope) }
    DisposableEffect(sp) { onDispose { sp.release() } }
    return sp
}

/** Records a short voice clip (AAC in an MP4 container, max 15 s) into the app cache. Cancels itself when the app is backgrounded. */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var removeBgHook: (() -> Unit)? = null

    fun start(onAutoStop: () -> Unit): Boolean {
        cancel()
        SpeechCache.prune(ctx)
        val f = File(ctx.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        return runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(32000); r.setAudioEncodingBitRate(64000)
            r.setMaxDuration(15_000)
            r.setOutputFile(f.path)
            r.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onAutoStop() }
            r.prepare(); r.start()
            rec = r; file = f
            (ctx.applicationContext as? Application)?.let { AppForeground.install(it) }
            removeBgHook = AppForeground.addOnBackground { cancel() }
            true
        }.getOrElse { runCatching { r.release() }; f.delete(); false }
    }

    /** Stops and returns the clip, or null if nothing usable was recorded. */
    fun stop(): File? {
        removeBgHook?.invoke(); removeBgHook = null
        val r = rec ?: return null
        rec = null
        val ok = runCatching { r.stop(); true }.getOrDefault(false)
        runCatching { r.release() }
        return file?.takeIf { ok && it.length() > 1000 }
    }

    /** Aborts a running recording and deletes its file. */
    fun cancel() {
        removeBgHook?.invoke(); removeBgHook = null
        val r = rec ?: return
        rec = null
        runCatching { r.stop() }; runCatching { r.release() }
        file?.delete()
    }

    companion object {
        /** Call once a recording has been uploaded or abandoned. */
        fun discard(f: File?) { runCatching { f?.delete() } }
    }
}
