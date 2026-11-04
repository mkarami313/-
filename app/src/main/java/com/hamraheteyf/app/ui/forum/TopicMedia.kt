package com.hamraheteyf.app.ui.forum

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hamraheteyf.app.community.CommunityClient
import com.hamraheteyf.app.ui.common.RemoteImage
import com.hamraheteyf.app.ui.common.toPersianDigits

/** Image grid for a post: one wide image, or rows of two/three squares. Tap opens the viewer. */
@Composable
fun ImageGrid(ids: List<String>, client: CommunityClient?, modifier: Modifier = Modifier) {
    if (ids.isEmpty() || client == null) return
    var open by remember { mutableStateOf<Int?>(null) }
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (ids.size == 1) {
            RemoteImage(client.fileUrl(ids[0]), Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(shape).clickable { open = 0 })
        } else {
            val perRow = if (ids.size == 2 || ids.size == 4) 2 else 3
            ids.chunked(perRow).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEachIndexed { c, id ->
                        val index = r * perRow + c
                        RemoteImage(client.fileUrl(id), Modifier.weight(1f).aspectRatio(1f).clip(shape).clickable { open = index })
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    open?.let { ImageViewerDialog(ids.map { client.fileUrl(it) }, it) { open = null } }
}

/** Full-screen viewer: swipe between images, pinch to zoom, double-tap to reset. */
@Composable
fun ImageViewerDialog(urls: List<String>, start: Int, onDismiss: () -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pager = rememberPagerState(initialPage = start) { urls.size }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page -> ZoomableImage(urls[page]) }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(8.dp)) {
                Icon(Icons.Outlined.Close, "بستن", tint = MaterialTheme.colorScheme.inverseOnSurface)
            }
            if (urls.size > 1) {
                Text(
                    "${(pager.currentPage + 1).toPersianDigits()} از ${urls.size.toPersianDigits()}",
                    color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(url: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) }
            .pointerInput(Unit) {
                // Pinch always zooms; one-finger pans only while zoomed, so the pager still swipes at 1×.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pinch = event.changes.count { it.pressed } >= 2
                        if (pinch || scale > 1f) {
                            if (pinch) scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        RemoteImage(url, Modifier.fillMaxWidth().graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
        }, contentScale = ContentScale.Fit)
    }
}
