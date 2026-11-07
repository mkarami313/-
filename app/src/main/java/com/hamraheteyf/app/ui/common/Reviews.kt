@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hamraheteyf.app.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.RatingSummary
import com.hamraheteyf.app.community.Review
import com.hamraheteyf.app.community.ReviewList
import com.hamraheteyf.app.community.rememberCommunity
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.kit.AuthorLine
import com.hamraheteyf.app.ui.kit.muted
import com.hamraheteyf.app.ui.kit.ratingFa
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.Gold
import kotlinx.coroutines.launch

/** Five stars for [rating] (0..5, halves shown). */
@Composable
fun StarRow(rating: Double, size: Dp = 16.dp) {
    Row {
        for (i in 1..5) {
            val icon = when {
                rating >= i - 0.25 -> Icons.Filled.Star
                rating >= i - 0.75 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Outlined.StarOutline
            }
            Icon(icon, null, tint = Gold, modifier = Modifier.size(size))
        }
    }
}

/** «★ ۴٫۵ (۱۲)» inline summary for list rows; nothing when there are no reviews. */
@Composable
fun RatingInline(summary: RatingSummary?, modifier: Modifier = Modifier) {
    if (summary == null || summary.count <= 0) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(2.dp))
        Text(ratingFa(summary.average), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(3.dp))
        Text("(${summary.count.toPersianDigits()} نظر)", style = MaterialTheme.typography.labelSmall, color = muted())
    }
}

/**
 * Ratings & reviews of a professional ("pro", id = their userId) or a place ("place", id = the place record id):
 * average + stars + count, the list, and «ثبتِ نظر» / «ویرایشِ نظرِ من» / delete mine.
 */
@Composable
fun ReviewsSection(targetType: String, targetId: String, canReview: Boolean) {
    val client = rememberCommunity()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var data by remember(targetType, targetId) { mutableStateOf<ReviewList?>(null) }
    var error by remember(targetType, targetId) { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(client, targetType, targetId, tick) {
        if (client == null) { error = "نظرها در حالتِ آفلاین در دسترس نیست."; return@LaunchedEffect }
        when (val r = client.reviews(targetType, targetId)) {
            is ApiResult.Ok -> { data = r.data; error = null }
            is ApiResult.Fail -> error = r.message
        }
    }
    val mine = data?.items?.firstOrNull { it.mine }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Dimens.s)) {
            Text("نظرها و امتیاز", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (canReview && data != null) TextButton(onClick = { editing = true }, modifier = Modifier.heightIn(min = Dimens.touch)) {
                Icon(Icons.Outlined.RateReview, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                Text(if (mine != null) "ویرایشِ نظرِ من" else "ثبتِ نظر")
            }
        }
        val d = data
        when {
            d == null && error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { error = null; tick++ }) { Text("تلاشِ دوباره") }
            }
            d == null -> Box(Modifier.fillMaxWidth().padding(Dimens.m), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
            d.count == 0 || d.items.isEmpty() -> Text(
                if (canReview) "هنوز نظری ثبت نشده است. اولین نفر باشید." else "هنوز نظری ثبت نشده است.",
                style = MaterialTheme.typography.bodyMedium, color = muted(),
            )
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ratingFa(d.average ?: 0.0), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(Dimens.m))
                    Column {
                        StarRow(d.average ?: 0.0, 18.dp)
                        Text("از ${d.count.toPersianDigits()} نظر", style = MaterialTheme.typography.labelMedium, color = muted())
                    }
                }
                Spacer(Modifier.height(Dimens.s))
                d.items.forEach { r ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ReviewRow(r, onEdit = { editing = true }, onDelete = { confirmDelete = true })
                }
            }
        }
    }

    if (editing && client != null) ReviewDialog(mine, saving, onDismiss = { editing = false }) { rating, text ->
        if (saving) return@ReviewDialog
        saving = true
        scope.launch {
            when (val r = client.saveReview(targetType, targetId, rating, text)) {
                is ApiResult.Ok -> { editing = false; tick++; Toast.makeText(ctx, "نظرِ شما ثبت شد.", Toast.LENGTH_SHORT).show() }
                is ApiResult.Fail -> Toast.makeText(ctx, r.message, Toast.LENGTH_LONG).show()
            }
            saving = false
        }
    }
    if (confirmDelete && client != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("حذفِ نظرِ من") },
        text = { Text("نظر و امتیازِ شما حذف شود؟") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                scope.launch {
                    when (val r = client.deleteReview(targetType, targetId)) {
                        is ApiResult.Ok -> tick++
                        is ApiResult.Fail -> Toast.makeText(ctx, r.message, Toast.LENGTH_LONG).show()
                    }
                }
            }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("انصراف") } },
    )
}

@Composable
private fun ReviewRow(r: Review, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuthorLine(r.author, relativeTimeFa(if (r.updatedAt > 0) r.updatedAt else r.createdAt), Modifier.weight(1f), size = 30.dp)
            StarRow(r.rating.toDouble(), 14.dp)
        }
        if (r.body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(r.body, style = MaterialTheme.typography.bodyMedium)
        }
        if (r.mine) Row {
            TextButton(onClick = onEdit) { Text("ویرایش") }
            TextButton(onClick = onDelete) { Text("حذف", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ReviewDialog(mine: Review?, saving: Boolean, onDismiss: () -> Unit, onSave: (Int, String) -> Unit) {
    var rating by rememberSaveable { mutableIntStateOf(mine?.rating ?: 0) }
    var text by rememberSaveable { mutableStateOf(mine?.body.orEmpty()) }
    val labels = listOf("", "ضعیف", "متوسط", "خوب", "خیلی خوب", "عالی")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mine != null) "ویرایشِ نظرِ من" else "ثبتِ نظر") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    for (i in 1..5) IconButton(onClick = { rating = i }, enabled = !saving) {
                        Icon(if (i <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline, "${i.toPersianDigits()} ستاره", tint = Gold, modifier = Modifier.size(32.dp))
                    }
                }
                Text(if (rating == 0) "امتیاز را انتخاب کنید" else labels[rating], style = MaterialTheme.typography.labelLarge,
                    color = if (rating == 0) muted() else Gold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(Dimens.s))
                OutlinedTextField(text, { text = it.take(2000) }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
                    placeholder = { Text("تجربهٔ خود را محترمانه بنویسید (اختیاری)") })
                Text("نظرِ شما با نامتان نمایش داده می‌شود.", style = MaterialTheme.typography.labelSmall, color = muted())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(rating, text.trim()) }, enabled = rating in 1..5 && !saving) {
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("ثبت")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
