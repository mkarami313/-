@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hamraheteyf.app.ui.learn

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.hamraheteyf.app.community.CommunityClient
import com.hamraheteyf.app.community.LearnItem
import com.hamraheteyf.app.community.rememberCommunity
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.net.FileUrls
import com.hamraheteyf.app.ui.common.RemoteImage
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Danger
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.Gold
import com.hamraheteyf.app.ui.theme.hexColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnItemScreen(id: String, onBack: () -> Unit, onEdit: (String) -> Unit, onOpenProfile: (String) -> Unit) {
    val client = rememberCommunity()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val cats by rememberLearnCategories(client)
    var tick by remember { mutableIntStateOf(0) }
    var item by remember { mutableStateOf<LearnItem?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id, tick, client) {
        if (client == null) { error = "این بخش در حالتِ آفلاین در دسترس نیست."; return@LaunchedEffect }
        when (val r = client.learnGet(id)) {
            is ApiResult.Ok -> { item = r.data; error = null }
            is ApiResult.Fail -> if (item == null) error = r.message else snackbar.showSnackbar(r.message)
        }
    }
    RefreshOnResume { if (item != null) tick++ }

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var positionMs by rememberSaveable(id) { mutableLongStateOf(0L) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var approving by remember { mutableStateOf(false) }
    var rejecting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val it = item
    // Uploaded video → signed URL → one player for the screen (survives the fullscreen switch).
    val videoSrc by produceState<String?>(null, it?.videoId) { value = it?.videoId?.let { v -> FileUrls.resolve(v) } }
    val player = rememberExoPlayer(videoSrc, positionMs) { positionMs = it }

    if (fullscreen && player != null) {
        FullscreenEffect()
        BackHandler { fullscreen = false }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerSurface(player, Modifier.fillMaxSize())
            FullscreenButton(true, Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing)) { fullscreen = false }
        }
        return
    }

    fun review(op: String, note: String = "", featured: Boolean = false, done: String) {
        val c = client ?: return
        busy = true
        scope.launch {
            val r = c.learnReview(id, op, note, featured)
            busy = false
            when (r) {
                is ApiResult.Ok -> { snackbar.showSnackbar(done); tick++ }
                is ApiResult.Fail -> snackbar.showSnackbar(r.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(it?.kindLabel() ?: "آموزش", onBack = onBack, actions = {
                if (it != null) {
                    if (it.status == "PUBLISHED") IconButton(onClick = {
                        val c = client ?: return@IconButton
                        scope.launch {
                            when (val r = c.learnSave(it.id)) {
                                is ApiResult.Ok -> { item = it.copy(saved = r.data); snackbar.showSnackbar(if (r.data) "ذخیره شد." else "از ذخیره‌شده‌ها برداشته شد.") }
                                is ApiResult.Fail -> snackbar.showSnackbar(r.message)
                            }
                        }
                    }) { Icon(if (it.saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, if (it.saved) "برداشتن از ذخیره‌شده‌ها" else "ذخیره") }
                    IconButton(onClick = { share(ctx, it) }) { Icon(Icons.Outlined.Share, "اشتراک‌گذاری") }
                    val canDelete = it.mine || it.canReview
                    if (it.canEdit || canDelete || it.canReview) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "گزینه‌های بیشتر") }
                        DropdownMenu(menu, { menu = false }) {
                            if (it.canEdit) DropdownMenuItem({ Text("ویرایش") }, { menu = false; onEdit(it.id) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
                            if (it.canReview && it.status == "PUBLISHED") {
                                DropdownMenuItem({ Text(if (it.featured) "برداشتن از ویژه‌ها" else "ویژه کردن") },
                                    { menu = false; review(if (it.featured) "unfeature" else "feature", done = "انجام شد.") },
                                    leadingIcon = { Icon(Icons.Outlined.Star, null) })
                                DropdownMenuItem({ Text("پنهان کردن") }, { menu = false; review("hide", done = "پنهان شد.") },
                                    leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) })
                            }
                            if (it.canReview && it.status == "HIDDEN") DropdownMenuItem({ Text("نمایشِ دوباره") },
                                { menu = false; review("unhide", done = "دوباره منتشر شد.") }, leadingIcon = { Icon(Icons.Outlined.Visibility, null) })
                            if (canDelete) DropdownMenuItem({ Text("حذف", color = MaterialTheme.colorScheme.error) }, { menu = false; confirmDelete = true },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) })
                        }
                    }
                }
            })
        },
        bottomBar = {
            if (it != null && it.canReview && (it.status == "PENDING" || it.status == "REJECTED")) {
                Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(Dimens.gutter, Dimens.s), horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
                        OutlinedButton(onClick = { rejecting = true }, enabled = !busy && it.status == "PENDING",
                            modifier = Modifier.weight(1f).heightIn(min = Dimens.touch)) { Text("رد") }
                        Button(onClick = { approving = true }, enabled = !busy, modifier = Modifier.weight(2f).heightIn(min = Dimens.touch)) { Text("تأیید و انتشار") }
                    }
                }
            }
        },
    ) { pad ->
        when {
            it == null && error != null -> ErrorView(error.orEmpty(), { error = null; tick++ }, Modifier.padding(pad))
            it == null -> LoadingView(Modifier.padding(pad))
            else -> Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
                val cat = cats.firstOrNull { c -> c.id == it.categoryId }
                // ---- media
                when {
                    it.isVideo && it.videoId != null -> Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                        if (player != null) {
                            PlayerSurface(player, Modifier.fillMaxSize())
                            FullscreenButton(false, Modifier.align(Alignment.TopStart)) { fullscreen = true }
                        } else CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    }
                    it.isVideo && aparatHash(it.videoUrl) != null -> AparatPlayer(aparatHash(it.videoUrl).orEmpty(), it.videoUrl.orEmpty())
                    it.coverId != null && client != null -> RemoteImage(client.fileUrl(it.coverId), Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                }
                Column(Modifier.padding(horizontal = Dimens.gutter)) {
                    Spacer(Modifier.height(Dimens.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (it.status != "PUBLISHED") StatusPill(it.status)
                        if (it.featured) Pill("ویژه", Gold, icon = Icons.Outlined.Star)
                        cat?.let { c -> Pill(c.title, hexColor(c.color), icon = iconFor(c.icon)) }
                        Pill(it.kindLabel(), muted(), MaterialTheme.colorScheme.surfaceVariant)
                    }
                    Spacer(Modifier.height(Dimens.s))
                    Text(it.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Dimens.s))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AuthorLine(it.author, whenFa(it.shownAt()), Modifier.weight(1f),
                            onClick = if (!it.author.anonymous && it.author.id.isNotBlank()) { { onOpenProfile(it.author.id) } } else null)
                        if (it.status == "PUBLISHED") Counter(Icons.Outlined.Visibility, it.viewCount, contentDescription = "بازدید")
                    }
                    if (it.status == "REJECTED" && !it.reviewNote.isNullOrBlank()) {
                        Spacer(Modifier.height(Dimens.m))
                        AppCard(border = Danger.copy(alpha = 0.4f), container = Danger.copy(alpha = 0.06f)) {
                            Text("این مطلب منتشر نشد", style = MaterialTheme.typography.titleSmall, color = Danger)
                            Text("دلیل: ${it.reviewNote}", style = MaterialTheme.typography.bodyMedium)
                            if (it.canEdit) {
                                Spacer(Modifier.height(Dimens.s))
                                Button(onClick = { onEdit(it.id) }, modifier = Modifier.heightIn(min = Dimens.touch)) { Text("ویرایش و ارسالِ دوباره") }
                            }
                        }
                    } else if (it.status == "PENDING" && it.mine && !it.canReview) {
                        Spacer(Modifier.height(Dimens.m))
                        AppCard(container = Gold.copy(alpha = 0.08f), border = Gold.copy(alpha = 0.4f)) {
                            Text("این مطلب در انتظارِ بررسیِ مدیر است. پس از انتشار به شما خبر می‌دهیم.", style = MaterialTheme.typography.bodyMedium)
                            if (it.canEdit) TextButton(onClick = { onEdit(it.id) }) { Text("ویرایش") }
                        }
                    }
                    if (it.summary.isNotBlank()) {
                        Spacer(Modifier.height(Dimens.m))
                        Text(it.summary, style = MaterialTheme.typography.bodyLarge, color = muted())
                    }
                    if (!it.body.isNullOrBlank()) {
                        Spacer(Modifier.height(Dimens.m))
                        ArticleBody(it.body)
                    }
                    Spacer(Modifier.height(Dimens.xl))
                }
            }
        }
    }

    val cur = item
    if (confirmDelete && cur != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("حذفِ مطلب") },
        text = { Text("«${cur.title}» حذف شود؟ این کار برگشت‌پذیر نیست.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                val c = client ?: return@TextButton
                scope.launch {
                    when (val r = c.learnDelete(cur.id)) {
                        is ApiResult.Ok -> onBack()
                        is ApiResult.Fail -> snackbar.showSnackbar(r.message)
                    }
                }
            }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("انصراف") } },
    )
    if (approving && cur != null) ApproveDialog(cur.title, { approving = false }) { f -> approving = false; review("approve", featured = f, done = "منتشر شد.") }
    if (rejecting && cur != null) RejectDialog(cur.title, { rejecting = false }) { n -> rejecting = false; review("reject", note = n, done = "رد شد و به فرستنده خبر داده شد.") }
}

private fun share(ctx: Context, item: LearnItem) {
    val text = buildString {
        append(item.title)
        if (item.summary.isNotBlank()) append("\n\n").append(item.summary)
        item.videoUrl?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
        append("\n\n— از بخشِ آموزش‌های اپِ «همراهِ طیف»")
    }
    runCatching {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "اشتراک‌گذاری")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Renders plain text with «## » headings, «- » bullets and blank-line paragraphs. */
@Composable
internal fun ArticleBody(body: String) {
    val blocks = remember(body) { parseBlocks(body) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Block.Heading -> Text(b.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                is Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    b.items.forEach { t ->
                        Row {
                            Box(Modifier.padding(top = 10.dp, end = 8.dp).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Text(t, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                    }
                }
                is Block.Para -> Text(b.text, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.15f))
            }
        }
    }
}

private sealed interface Block {
    data class Heading(val text: String) : Block
    data class Bullets(val items: List<String>) : Block
    data class Para(val text: String) : Block
}

private fun parseBlocks(body: String): List<Block> {
    val out = mutableListOf<Block>()
    val para = StringBuilder()
    val bullets = mutableListOf<String>()
    fun flush() {
        if (para.isNotBlank()) out += Block.Para(para.toString().trim()); para.clear()
        if (bullets.isNotEmpty()) out += Block.Bullets(bullets.toList()); bullets.clear()
    }
    for (raw in body.replace("\r", "").lines()) {
        val l = raw.trim()
        when {
            l.isEmpty() -> flush()
            l.startsWith("#") -> { flush(); out += Block.Heading(l.trimStart('#').trim()) }
            l.startsWith("- ") || l.startsWith("• ") || l.startsWith("* ") -> {
                if (para.isNotBlank()) { out += Block.Para(para.toString().trim()); para.clear() }
                bullets += l.substring(2).trim()
            }
            else -> {
                if (bullets.isNotEmpty()) { out += Block.Bullets(bullets.toList()); bullets.clear() }
                if (para.isNotEmpty()) para.append('\n'); para.append(l)
            }
        }
    }
    flush()
    return out
}

// ------------------------------------------------------------------ video

/** One ExoPlayer per source URL; released when the screen leaves. Paused when the app goes to background. */
@Composable
private fun rememberExoPlayer(src: String?, startMs: Long, onPosition: (Long) -> Unit): ExoPlayer? {
    if (src == null) return null
    val ctx = LocalContext.current.applicationContext
    val player = remember(src) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(src))
            if (startMs > 0) seekTo(startMs)
            prepare()
        }
    }
    val posCb by rememberUpdatedState(onPosition)
    // Keep the position in saved state so a rotation (activity re-creation) resumes where it was.
    LaunchedEffect(player) {
        while (true) { delay(1000); if (player.playbackState != Player.STATE_IDLE) posCb(player.currentPosition) }
    }
    @Suppress("DEPRECATION")
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(player, owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) player.pause() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); posCb(player.currentPosition); player.release() }
    }
    return player
}

@Composable
private fun PlayerSurface(player: ExoPlayer, modifier: Modifier) {
    AndroidView(
        factory = { c -> PlayerView(c).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            this.player = player
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        } },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

@Composable
private fun FullscreenButton(on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.padding(4.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            Icon(if (on) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, if (on) "خروج از تمام‌صفحه" else "تمام‌صفحه", tint = Color.White)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** Landscape + hidden system bars while composed; restored when leaving fullscreen. */
@Composable
private fun FullscreenEffect() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val before = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val ctl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (!activity.isChangingConfigurations) {
                activity.requestedOrientation = if (before == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else before
                ctl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

/** Aparat embed in a WebView, plus a button to open it in the browser/app. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AparatPlayer(hash: String, url: String) {
    val ctx = LocalContext.current
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            AndroidView(
                factory = { c -> WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    setBackgroundColor(android.graphics.Color.BLACK)
                    loadUrl("https://www.aparat.com/video/video/embed/videohash/$hash/vt/frame")
                } },
                onRelease = { w -> w.stopLoading(); w.loadUrl("about:blank"); w.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        TextButton(onClick = { openUrl(ctx, url.ifBlank { "https://www.aparat.com/v/$hash" }) },
            modifier = Modifier.padding(horizontal = Dimens.gutter - 8.dp).heightIn(min = Dimens.touch)) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("پخش در آپارات")
        }
    }
}
