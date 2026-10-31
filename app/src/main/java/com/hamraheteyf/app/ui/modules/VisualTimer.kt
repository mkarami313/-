package com.hamraheteyf.app.ui.modules

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.delay

private enum class TimerState { IDLE, RUNNING, PAUSED, DONE }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualTimerScreen(onBack: () -> Unit) {
    val view = LocalView.current
    val ctx = LocalContext.current.applicationContext
    // A timer started earlier keeps running (and rings via an alarm) while the screen is closed; pick it back up.
    val resumeLeft = remember { (Reminders.timerEnd(ctx) - System.currentTimeMillis()).takeIf { it > 0 } }
    var totalMs by remember { mutableLongStateOf(if (resumeLeft != null) Reminders.timerTotal(ctx).coerceAtLeast(resumeLeft) else 5 * 60_000L) }
    var state by remember { mutableStateOf(if (resumeLeft != null) TimerState.RUNNING else TimerState.IDLE) }
    var remainingMs by remember { mutableLongStateOf(resumeLeft ?: totalMs) }
    var endAt by remember { mutableLongStateOf(if (resumeLeft != null) SystemClock.elapsedRealtime() + resumeLeft else 0L) }
    var custom by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        Reminders.timerScreenOpen = true
        onDispose { Reminders.timerScreenOpen = false }
    }

    DisposableEffect(state) {
        view.keepScreenOn = state == TimerState.RUNNING
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(state, endAt) {
        if (state != TimerState.RUNNING) return@LaunchedEffect
        while (true) {
            val left = endAt - SystemClock.elapsedRealtime()
            if (left <= 0) { remainingMs = 0; state = TimerState.DONE; break }
            remainingMs = left
            delay(200)
        }
    }
    LaunchedEffect(state) {
        if (state != TimerState.DONE) return@LaunchedEffect
        Reminders.stopTimer(ctx)
        Reminders.vibrateDone(ctx)
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35) }.getOrNull()
        repeat(3) {
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 350) }
            delay(900)
        }
        runCatching { tone?.release() }
    }

    fun pick(min: Int) { totalMs = min * 60_000L; remainingMs = totalMs; state = TimerState.IDLE }
    fun start() {
        endAt = SystemClock.elapsedRealtime() + remainingMs; state = TimerState.RUNNING
        Reminders.startTimer(ctx, System.currentTimeMillis() + remainingMs, totalMs)
    }
    fun pause() { remainingMs = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0); state = TimerState.PAUSED; Reminders.stopTimer(ctx) }
    fun reset() { remainingMs = totalMs; state = TimerState.IDLE; Reminders.stopTimer(ctx) }

    val progress = if (totalMs <= 0) 0f else (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val ringColor = when { state == TimerState.DONE -> Color(0xFF43A047); progress > 0.5f -> Color(0xFF43A047); progress > 0.2f -> Color(0xFFFFA000); else -> Color(0xFFE53935) }
    val track = MaterialTheme.colorScheme.surfaceVariant

    Scaffold(topBar = { ScreenHeader("تایمر تصویری", onBack = onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val spokenLeft = if (state == TimerState.DONE) "تایمر تمام شد" else {
                val sec = ((remainingMs + 999) / 1000).toInt()
                "تایمر: ${(sec / 60).toPersianDigits()} دقیقه و ${(sec % 60).toPersianDigits()} ثانیه باقی مانده"
            }
            Box(Modifier.size(280.dp).semantics(mergeDescendants = true) { contentDescription = spokenLeft }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 34f
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(ringColor, -90f, -360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                val secs = ((remainingMs + 999) / 1000).toInt()
                Text(
                    if (state == TimerState.DONE) "تمام شد 🎉" else "%d:%02d".format(secs / 60, secs % 60).toPersianDigits(),
                    fontSize = if (state == TimerState.DONE) 34.sp else 56.sp, textAlign = TextAlign.Center,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    TimerState.IDLE -> Button(onClick = { start() }, enabled = remainingMs > 0, modifier = Modifier.heightIn(min = 52.dp)) { Text("شروع", fontSize = 18.sp) }
                    TimerState.RUNNING -> Button(onClick = { pause() }, modifier = Modifier.heightIn(min = 52.dp)) { Text("مکث", fontSize = 18.sp) }
                    TimerState.PAUSED -> Button(onClick = { start() }, modifier = Modifier.heightIn(min = 52.dp)) { Text("ادامه", fontSize = 18.sp) }
                    TimerState.DONE -> Button(onClick = { reset() }, modifier = Modifier.heightIn(min = 52.dp)) { Text("دوباره", fontSize = 18.sp) }
                }
                if (state == TimerState.RUNNING || state == TimerState.PAUSED) OutlinedButton(onClick = { reset() }, modifier = Modifier.heightIn(min = 52.dp)) { Text("از نو", fontSize = 18.sp) }
            }
            if (state == TimerState.IDLE || state == TimerState.DONE) {
                SectionLabel("مدت")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1, 2, 5, 10, 15).forEach { m -> FilterChip(totalMs == m * 60_000L, { pick(m) }, label = { Text("${m.toPersianDigits()} دقیقه") }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(custom, { custom = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("دقیقهٔ دلخواه") }, singleLine = true, modifier = Modifier.width(160.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    OutlinedButton(enabled = (custom.toIntOrNull() ?: 0) in 1..180, onClick = { pick(custom.toInt()) }) { Text("تنظیم") }
                }
            }
            Text("وقتی تایمر تمام می‌شود لرزش و صدا می‌دهد؛ حتی اگر برنامه بسته باشد با یک اعلان خبر می‌دهد.", style = MaterialTheme.typography.labelMedium, color = mutedColor(), textAlign = TextAlign.Center)
        }
    }
}
