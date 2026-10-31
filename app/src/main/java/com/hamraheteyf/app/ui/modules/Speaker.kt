package com.hamraheteyf.app.ui.modules

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.net.HamrahApi
import kotlinx.coroutines.*
import java.io.File
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

/**
 * Plays speech: a family-recorded clip when the item has one, otherwise a server-side Persian
 * voice (stock Android TTS has no Persian), otherwise the phone's own voice if it has Persian.
 */
class Speaker(private val ctx: Context, private val api: HamrahApi?, private val scope: CoroutineScope) {
    private var job: Job? = null
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private val dir = File(ctx.cacheDir, "tts").apply { mkdirs() }

    init { tts = TextToSpeech(ctx.applicationContext) { st -> if (st == TextToSpeech.SUCCESS) tts?.language = Locale("fa") } }

    fun release() { job?.cancel(); runCatching { player?.release() }; tts?.shutdown() }

    operator fun invoke(text: String, audioId: String? = null) = say(listOf(text to audioId))

    fun say(items: List<Pair<String, String?>>) {
        job?.cancel(); tts?.stop(); runCatching { player?.stop() }
        job = scope.launch {
            var failed = false
            for ((text, audioId) in items) {
                val files = if (!audioId.isNullOrBlank()) listOfNotNull(fetchClip(audioId)).also { if (it.isEmpty()) failed = true }
                else speechChunks(text).map { c -> fetchTts(c) ?: run { failed = true; null } }.filterNotNull()
                if (failed && files.isEmpty()) break
                files.forEach { play(it) }
            }
            if (failed) {
                val t = tts
                val text = items.joinToString(" ") { it.first }
                if (t != null && t.isLanguageAvailable(Locale("fa")) >= 0) t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u")
                else Toast.makeText(ctx, "برای پخش صدا، اینترنت لازم است.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun playLocal(f: File) { job?.cancel(); job = scope.launch { play(f) } }

    private suspend fun fetchClip(id: String): File? = withContext(Dispatchers.IO) {
        val f = File(dir, "clip_$id.snd")
        if (f.exists() && f.length() > 0) return@withContext f
        runCatching { val b = URL((api ?: return@runCatching null).fileUrl(id)).readBytes(); f.writeBytes(b); f }.getOrNull()
    }

    private suspend fun fetchTts(chunk: String): File? {
        val f = File(dir, sha1(chunk) + ".snd")
        if (f.exists() && f.length() > 0) return f
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

/** Records a short voice clip (AAC in an MP4 container, max 15 s) into the app cache. */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null

    fun start(onAutoStop: () -> Unit): Boolean {
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
            rec = r; file = f; true
        }.getOrElse { runCatching { r.release() }; false }
    }

    /** Stops and returns the clip, or null if nothing usable was recorded. */
    fun stop(): File? {
        val r = rec ?: return null
        rec = null
        val ok = runCatching { r.stop(); true }.getOrDefault(false)
        runCatching { r.release() }
        return file?.takeIf { ok && it.length() > 1000 }
    }

    fun cancel() { runCatching { rec?.stop() }; runCatching { rec?.release() }; rec = null }
}
