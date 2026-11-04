@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hamraheteyf.app.ui.modules

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.hamraheteyf.app.R
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.hexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

// ------------------------------------------------------------------ data (bundled in assets/cards)

data class PicCard(val id: String, val word: String, val category: String) {
    val path get() = "cards/$category/$id.webp"
}

data class PicCategory(val id: String, val title: String, val color: String, val cards: List<PicCard>)

/** The picture-card set ships inside the APK, so it works offline. */
object PicCards {
    @Volatile private var cached: List<PicCategory>? = null
    private val bitmaps = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun load(ctx: Context): List<PicCategory> = cached ?: synchronized(this) {
        cached ?: runCatching {
            val o = JSONObject(ctx.assets.open("cards/cards.json").bufferedReader().use { it.readText() })
            val cats = o.getJSONArray("categories")
            (0 until cats.length()).map { i ->
                val c = cats.getJSONObject(i)
                val arr = c.getJSONArray("cards")
                PicCategory(c.getString("id"), c.getString("title"), c.getString("color"),
                    (0 until arr.length()).map { j -> arr.getJSONObject(j).let { PicCard(it.getString("id"), it.getString("word"), c.getString("id")) } })
            }.filter { it.cards.isNotEmpty() }
        }.getOrDefault(emptyList()).also { cached = it }
    }

    fun bitmap(ctx: Context, card: PicCard, sample: Int = 1): Bitmap? {
        val key = card.path + "@" + sample
        bitmaps.get(key)?.let { return it }
        return runCatching {
            ctx.assets.open(card.path).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }.getOrNull()?.also { bitmaps.put(key, it) }
    }
}

@Composable
private fun rememberCards(): List<PicCategory> {
    val ctx = LocalContext.current
    var cats by remember { mutableStateOf<List<PicCategory>?>(null) }
    LaunchedEffect(Unit) { cats = withContext(Dispatchers.IO) { PicCards.load(ctx) } }
    return cats ?: emptyList()
}

@Composable
private fun CardImage(card: PicCard, modifier: Modifier = Modifier, sample: Int = 2) {
    val ctx = LocalContext.current
    var img by remember(card.path, sample) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(card.path, sample) { img = withContext(Dispatchers.IO) { PicCards.bitmap(ctx, card, sample)?.asImageBitmap() } }
    Box(modifier.background(Color(0xFFFFFBF2))) {
        img?.let { Image(it, card.word, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

// ------------------------------------------------------------------ hub

/** «کارت‌های آموزشی»: categories, the guessing game and printable PDFs. */
@Composable
fun CardsHubScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onGame: () -> Unit, onPrint: () -> Unit) {
    val cats = rememberCards()
    Scaffold(topBar = { AppTopBar("کارت‌های آموزشی", onBack = onBack, subtitle = "${cats.sumOf { it.cards.size }.toPersianDigits()} کارت در ${cats.size.toPersianDigits()} دسته") }) { pad ->
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(Dimens.gutter),
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap), verticalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
            item(span = { GridItemSpan(2) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
                    ActionTile("بازیِ «کدام است؟»", "کلمه را بشنو و تصویرش را پیدا کن", Icons.Outlined.SportsEsports, Color(0xFF7048E8), Modifier.weight(1f), onGame)
                    ActionTile("چاپِ کارت‌ها", "فایلِ PDF برای چاپ در خانه", Icons.Outlined.Print, Color(0xFF0E6E62), Modifier.weight(1f), onPrint)
                }
            }
            items(cats, key = { it.id }) { c ->
                val color = hexColor(c.color)
                Surface(onClick = { onOpen(c.id) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column {
                        Row(Modifier.fillMaxWidth().aspectRatio(2f)) {
                            c.cards.take(2).forEach { CardImage(it, Modifier.weight(1f).fillMaxHeight(), sample = 4) }
                        }
                        Box(Modifier.fillMaxWidth().height(4.dp).background(color))
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(c.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text("${c.cards.size.toPersianDigits()} کارت", style = MaterialTheme.typography.labelMedium, color = muted())
                        }
                    }
                }
            }
            item(span = { GridItemSpan(2) }) {
                Text("روی هر کارت بزنید تا کلمه بلند خوانده شود. کارت‌ها بدونِ اینترنت هم کار می‌کنند؛ خواندنِ صدا اینترنت می‌خواهد.",
                    style = MaterialTheme.typography.bodySmall, color = muted(), modifier = Modifier.padding(vertical = Dimens.s))
            }
        }
    }
}

@Composable
private fun ActionTile(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium, color = color.copy(alpha = 0.10f), border = BorderStroke(1.dp, color.copy(alpha = 0.25f))) {
        Column(Modifier.padding(Dimens.cardPad)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = muted(), maxLines = 2)
        }
    }
}

// ------------------------------------------------------------------ one category

@Composable
fun CardsCategoryScreen(categoryId: String, onBack: () -> Unit) {
    val cats = rememberCards()
    val cat = cats.firstOrNull { it.id == categoryId }
    val speak = rememberSpeaker()
    var viewer by rememberSaveable { mutableStateOf<Int?>(null) }
    var showWords by rememberSaveable { mutableStateOf(true) }
    Scaffold(topBar = {
        AppTopBar(cat?.title ?: "کارت‌ها", onBack = onBack, actions = {
            IconButton(onClick = { showWords = !showWords }) {
                Icon(if (showWords) Icons.Outlined.TextFields else Icons.Outlined.TextFormat, if (showWords) "پنهان کردنِ کلمه‌ها" else "نمایشِ کلمه‌ها")
            }
        })
    }) { pad ->
        if (cat == null) { LoadingView(Modifier.padding(pad)); return@Scaffold }
        val color = hexColor(cat.color)
        LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(Dimens.gutter),
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap), verticalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
            items(cat.cards.size) { i ->
                val card = cat.cards[i]
                Surface(onClick = { viewer = i; speak(card.word) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(2.dp, color.copy(alpha = 0.55f))) {
                    Column {
                        CardImage(card, Modifier.fillMaxWidth().aspectRatio(1f))
                        if (showWords) Text(card.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.08f)).padding(vertical = 6.dp))
                    }
                }
            }
        }
        viewer?.let { start -> CardViewer(cat, start, showWords, onSpeak = { speak(it) }, onDismiss = { viewer = null }) }
    }
}

/** Full-screen swipeable card; each page speaks its word. */
@Composable
private fun CardViewer(cat: PicCategory, start: Int, showWords: Boolean, onSpeak: (String) -> Unit, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { cat.cards.size }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(pager.currentPage) { if (!first) onSpeak(cat.cards[pager.currentPage].word); first = false }
    val color = hexColor(cat.color)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFFFFBF2)) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "بستن") }
                    Text("${(pager.currentPage + 1).toPersianDigits()} از ${cat.cards.size.toPersianDigits()}", style = MaterialTheme.typography.labelLarge,
                        color = muted(), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    IconButton(onClick = { onSpeak(cat.cards[pager.currentPage].word) }) { Icon(Icons.AutoMirrored.Outlined.VolumeUp, "بخوان") }
                }
                HorizontalPager(pager, Modifier.weight(1f)) { p ->
                    val card = cat.cards[p]
                    Column(Modifier.fillMaxSize().padding(Dimens.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Surface(shape = RoundedCornerShape(24.dp), border = BorderStroke(4.dp, color), modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            .clickable { onSpeak(card.word) }) {
                            CardImage(card, Modifier.fillMaxSize(), sample = 1)
                        }
                        Spacer(Modifier.height(Dimens.l))
                        if (showWords) Text(card.word, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ game

/** «کدام است؟»: hear (and read) a word, tap the matching picture among four. */
@Composable
fun CardsGameScreen(onBack: () -> Unit) {
    val cats = rememberCards()
    val speak = rememberSpeaker()
    val scope = rememberCoroutineScope()
    var catId by rememberSaveable { mutableStateOf<String?>(null) }
    var round by remember { mutableIntStateOf(0) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    var showWord by rememberSaveable { mutableStateOf(true) }
    val pool = remember(cats, catId) { (if (catId == null) cats.flatMap { it.cards.take(30) } else cats.firstOrNull { it.id == catId }?.cards.orEmpty()) }
    val q = remember(pool, round) {
        if (pool.size < 4) null else {
            val opts = pool.shuffled().take(4)
            opts to opts.random()
        }
    }
    LaunchedEffect(q) { q?.let { delay(300); speak(it.second.word) } }
    Scaffold(topBar = {
        AppTopBar("کدام است؟", onBack = onBack, subtitle = "امتیاز: ${score.toPersianDigits()}", actions = {
            IconButton(onClick = { showWord = !showWord }) { Icon(if (showWord) Icons.Outlined.TextFields else Icons.Outlined.TextFormat, "نمایش یا پنهان کردنِ کلمه") }
        })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ChoiceRow(listOf<String?>(null) + cats.map { it.id }, catId, label = { id -> cats.firstOrNull { it.id == id }?.title ?: "همه" },
                onSelect = { catId = it; round++; picked = null }, modifier = Modifier.padding(vertical = Dimens.s))
            if (q == null) { LoadingView(); return@Column }
            val (opts, answer) = q
            Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (showWord) Text(answer.word, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = { speak(answer.word) }) { Icon(Icons.AutoMirrored.Outlined.VolumeUp, "دوباره بخوان") }
            }
            Spacer(Modifier.height(Dimens.s))
            LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), contentPadding = PaddingValues(Dimens.gutter),
                horizontalArrangement = Arrangement.spacedBy(Dimens.m), verticalArrangement = Arrangement.spacedBy(Dimens.m)) {
                items(opts, key = { it.category + it.id }) { c ->
                    val state = when { picked == null -> 0; c == answer && picked != null -> 1; picked == c.category + c.id -> 2; else -> 0 }
                    val scale by animateFloatAsState(if (state == 1) 1.05f else 1f, label = "pick")
                    val border = when (state) { 1 -> Color(0xFF2B8A3E); 2 -> Color(0xFFE03131); else -> MaterialTheme.colorScheme.outlineVariant }
                    Surface(shape = RoundedCornerShape(18.dp), border = BorderStroke(if (state == 0) 1.dp else 4.dp, border), modifier = Modifier.scale(scale).clickable(enabled = picked == null || state == 2) {
                        picked = c.category + c.id
                        if (c == answer) {
                            score++; speak("آفرین! ${answer.word}")
                            scope.launch { delay(1600); picked = null; round++ }
                        } else {
                            speak(answer.word)
                            scope.launch { delay(900); if (picked == c.category + c.id) picked = null }
                        }
                    }) { CardImage(c, Modifier.fillMaxWidth().aspectRatio(1f)) }
                }
            }
            TextButton(onClick = { picked = null; round++ }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = Dimens.s)) { Text("سؤالِ بعدی") }
        }
    }
}

// ------------------------------------------------------------------ print

/** Builds a printable A4 PDF of chosen categories (cut along the gray lines) and shares it. */
@Composable
fun CardsPrintScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cats = rememberCards()
    val scope = rememberCoroutineScope()
    val chosen = remember { mutableStateMapOf<String, Boolean>() }
    var perPage by rememberSaveable { mutableIntStateOf(6) }
    var withWords by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val count = cats.filter { chosen[it.id] == true }.sumOf { it.cards.size }
    Scaffold(topBar = { AppTopBar("چاپِ کارت‌ها", onBack = onBack) }, bottomBar = {
        Surface(shadowElevation = 6.dp) {
            Button(onClick = {
                busy = true
                scope.launch {
                    val f = withContext(Dispatchers.IO) { runCatching { buildCardsPdf(ctx, cats.filter { chosen[it.id] == true }, perPage, withWords) }.getOrNull() }
                    busy = false
                    if (f == null) Toast.makeText(ctx, "ساختِ فایل ممکن نشد.", Toast.LENGTH_LONG).show() else sharePdf(ctx, f)
                }
            }, enabled = count > 0 && !busy, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Dimens.gutter).height(Dimens.touch + 4.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else Text(if (count == 0) "یک دسته انتخاب کنید" else "ساختِ PDF (${count.toPersianDigits()} کارت، ${((count + perPage - 1) / perPage).toPersianDigits()} صفحه)")
            }
        }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(Dimens.gutter), verticalArrangement = Arrangement.spacedBy(Dimens.s)) {
            item {
                Text("کارت‌ها را روی کاغذِ A4 چاپ کنید، از روی خط‌ها ببرید و برای دوام‌بیشتر روی مقوا بچسبانید.", style = MaterialTheme.typography.bodyMedium, color = muted())
                Spacer(Modifier.height(Dimens.s))
                Text("اندازه", style = MaterialTheme.typography.titleSmall)
                ChoiceRow(listOf(4, 6, 12), perPage, label = { "${it.toPersianDigits()} کارت در صفحه" }, onSelect = { perPage = it }, modifier = Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("نوشتنِ کلمه زیرِ تصویر", Modifier.weight(1f)); Switch(withWords, { withWords = it })
                }
                Spacer(Modifier.height(Dimens.s))
                Text("دسته‌ها", style = MaterialTheme.typography.titleSmall)
            }
            items(cats, key = { it.id }) { c ->
                AppCard(onClick = { chosen[c.id] = chosen[c.id] != true }, padding = PaddingValues(horizontal = Dimens.cardPad, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(chosen[c.id] == true, { chosen[c.id] = it })
                        Box(Modifier.size(10.dp).clip(CircleShape).background(hexColor(c.color)))
                        Spacer(Modifier.width(8.dp))
                        Text(c.title, Modifier.weight(1f))
                        Text("${c.cards.size.toPersianDigits()} کارت", style = MaterialTheme.typography.labelMedium, color = muted())
                    }
                }
            }
        }
    }
}

private fun buildCardsPdf(ctx: Context, cats: List<PicCategory>, perPage: Int, withWords: Boolean): File {
    val (cols, rows) = when (perPage) { 4 -> 2 to 2; 12 -> 3 to 4; else -> 2 to 3 }
    val pw = 595; val ph = 842; val margin = 24f
    val cw = (pw - 2 * margin) / cols; val ch = (ph - 2 * margin) / rows
    val font = ResourcesCompat.getFont(ctx, R.font.vazirmatn_bold) ?: Typeface.DEFAULT_BOLD
    val doc = PdfDocument()
    val cut = Paint().apply { color = android.graphics.Color.rgb(190, 190, 190); style = Paint.Style.STROKE; strokeWidth = 0.6f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 4f), 0f) }
    val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textAlign = Paint.Align.CENTER; color = android.graphics.Color.rgb(30, 30, 30) }
    val img = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val cards = cats.flatMap { c -> c.cards.map { it to c.color } }
    cards.chunked(perPage).forEachIndexed { pi, chunk ->
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pi + 1).create())
        val cv = page.canvas
        chunk.forEachIndexed { i, (card, color) ->
            val x = margin + (i % cols) * cw; val y = margin + (i / cols) * ch
            cv.drawRect(x, y, x + cw, y + ch, cut)
            val pad = 10f
            val labelH = if (withWords) ch * 0.18f else 0f
            val side = minOf(cw - 2 * pad, ch - 2 * pad - labelH)
            val left = x + (cw - side) / 2; val top = y + pad
            frame.color = android.graphics.Color.parseColor(color)
            val r = RectF(left, top, left + side, top + side)
            PicCards.bitmap(ctx, card, 1)?.let { cv.drawBitmap(it, null, r, img) }
            cv.drawRoundRect(r, 14f, 14f, frame)
            if (withWords) {
                text.textSize = labelH * 0.55f
                cv.drawText(card.word, x + cw / 2, top + side + labelH * 0.72f, text)
            }
        }
        doc.finishPage(page)
    }
    val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
    val f = File(dir, "hamrah-cards.pdf")
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    return f
}

private fun sharePdf(ctx: Context, f: File) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "چاپ یا ارسالِ کارت‌ها").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
