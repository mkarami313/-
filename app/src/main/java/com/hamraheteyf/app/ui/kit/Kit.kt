package com.hamraheteyf.app.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.R
import com.hamraheteyf.app.community.Person
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.ui.common.RemoteImage
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.theme.*

/*
 * The app's design kit. Every new screen is built from these pieces so spacing, type and color
 * stay consistent: one 12dp gutter, white cards with a hairline border, 16dp corners, Vazirmatn.
 * Dense but calm — lists over tiles, real content above the fold.
 */

/** Flat top bar on the surface color. [title] null on home shows the wordmark. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String?,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = {
            if (title == null) Wordmark()
            else Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = muted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "بازگشت") }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        scrollBehavior = scrollBehavior,
    )
}

/** Logo + name, used on the home top bar and the login screen. */
@Composable
fun Wordmark(size: Dp = 30.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(size).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(8.dp))
        Text("همراهِ طیف", style = MaterialTheme.typography.titleLarge, color = Teal, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun muted(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/** White card with a hairline border; the one card style of the app. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(Dimens.cardPad),
    border: Color = MaterialTheme.colorScheme.outlineVariant,
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val m = modifier.fillMaxWidth()
    if (onClick != null) {
        Surface(onClick = onClick, modifier = m, shape = shape, color = container, border = BorderStroke(1.dp, border)) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = m, shape = shape, color = container, border = BorderStroke(1.dp, border)) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

/** Section title with an optional «همه» action on the far side. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = Dimens.l, bottom = Dimens.s), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp))
        }
    }
}

private val AvatarPalette = listOf(Color(0xFF0E6E62), Color(0xFFE8794A), Color(0xFF3B5BDB), Color(0xFFC2255C), Color(0xFF2B8A3E), Color(0xFF7048E8))
private val FileIdRe = Regex("([0-9a-f]{32})")

/** Photo avatar when the user has one, else a tinted initial. */
@Composable
fun UserAvatar(name: String, photoUrl: String?, size: Dp = Dimens.avatar, anonymous: Boolean = false) {
    val c = if (anonymous) Color(0xFF868E96) else AvatarPalette[(name.hashCode() and 0x7fffffff) % AvatarPalette.size]
    val wantsPhoto = !photoUrl.isNullOrBlank() && !anonymous
    // Only touch the container when there is a photo: previews/screenshots render initials without one.
    val api = if (wantsPhoto) LocalAppContainer.current.serverApi else null
    val url = photoUrl?.takeIf { wantsPhoto }?.let { p -> FileIdRe.find(p)?.groupValues?.get(1)?.let { id -> api?.fileUrl(id) } ?: p }
    Box(Modifier.size(size).clip(CircleShape).background(c.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        if (url != null) RemoteImage(url, Modifier.fillMaxSize())
        else if (anonymous) Icon(Icons.Outlined.PersonOff, null, tint = c, modifier = Modifier.size(size * 0.55f))
        else Text(name.trim().firstOrNull()?.toString() ?: "؟", color = c, fontWeight = FontWeight.Bold,
            style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall)
    }
}

/** Name + badge line used in topic/post headers. */
@Composable
fun AuthorLine(person: Person, time: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, size: Dp = Dimens.avatar) {
    Row(modifier.then(if (onClick != null && person.id.isNotEmpty()) Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(person.name, person.photoUrl, size, person.anonymous)
        Spacer(Modifier.width(Dimens.s))
        Column(Modifier.weight(1f, fill = false)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(person.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (person.expert) { Spacer(Modifier.width(4.dp)); Icon(VerifiedIcon, "متخصصِ تأییدشده", tint = Expert, modifier = Modifier.size(15.dp)) }
            }
            val sub = listOfNotNull(person.badge.takeIf { it.isNotBlank() }, time).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = muted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Filled «verified» check for expert badges. */
val VerifiedIcon: ImageVector get() = Icons.Filled.Verified

/** Small rounded label. */
@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.primary, container: Color = color.copy(alpha = 0.12f), icon: ImageVector? = null) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(3.dp)) }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** Icon + number, for reply/like/view counters. */
@Composable
fun Counter(icon: ImageVector, value: Int, tint: Color = muted(), contentDescription: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(3.dp))
        Text(value.toPersianDigits(), style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** Horizontal choice chips (sorts, categories). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceRow(items: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.lazy.LazyRow(modifier, contentPadding = PaddingValues(horizontal = Dimens.gutter), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(items.size) { i ->
            val it = items[i]
            FilterChip(
                selected = it == selected, onClick = { onSelect(it) }, label = { Text(label(it), style = MaterialTheme.typography.labelLarge) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White,
                ),
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = it == selected,
                    borderColor = MaterialTheme.colorScheme.outline, selectedBorderColor = Color.Transparent),
            )
        }
    }
}

/** Centered empty view with an icon and an optional action button. */
@Composable
fun EmptyView(icon: ImageVector, title: String, message: String? = null, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = Dimens.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(Dimens.m))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) { Spacer(Modifier.height(4.dp)); Text(message, style = MaterialTheme.typography.bodyMedium, color = muted(), textAlign = TextAlign.Center) }
        if (action != null && onAction != null) { Spacer(Modifier.height(Dimens.m)); Button(onClick = onAction) { Text(action) } }
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) =
    EmptyView(Icons.Outlined.CloudOff, "مشکلی پیش آمد", message, "تلاشِ دوباره", onRetry, modifier)

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 3.dp) }
}

/** Footer that loads the next page when it scrolls into view. */
@Composable
fun LoadMoreFooter(hasMore: Boolean, loading: Boolean, onLoadMore: () -> Unit) {
    if (!hasMore) return
    LaunchedEffect(Unit) { if (!loading) onLoadMore() }
    Box(Modifier.fillMaxWidth().padding(Dimens.l), contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else TextButton(onClick = onLoadMore) { Text("بیشتر") }
    }
}

/** True when the list has scrolled near its end — for infinite scrolling. */
@Composable
fun LazyListState.nearEnd(buffer: Int = 4): Boolean {
    val s by remember { derivedStateOf { layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    return s >= layoutInfo.totalItemsCount - buffer
}

/** Round colored icon used for forum/category entries. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

/** Maps the server's icon keys (forums, learning categories) to Material icons. */
fun iconFor(key: String?): ImageVector = when (key) {
    "search" -> Icons.Outlined.ManageSearch
    "healing" -> Icons.Outlined.Healing
    "home" -> Icons.Outlined.Home
    "school" -> Icons.Outlined.School
    "cake" -> Icons.Outlined.Cake
    "family" -> Icons.Outlined.FamilyRestroom
    "gavel" -> Icons.Outlined.Gavel
    "favorite" -> Icons.Outlined.FavoriteBorder
    "medical" -> Icons.Outlined.MedicalServices
    "chat" -> Icons.Outlined.Forum
    "info" -> Icons.Outlined.Info
    "record_voice" -> Icons.Outlined.RecordVoiceOver
    "touch" -> Icons.Outlined.TouchApp
    "psychology" -> Icons.Outlined.Psychology
    "toys" -> Icons.Outlined.Toys
    else -> Icons.Outlined.Topic
}

/** Persian one-decimal rating like «۴٫۵». */
fun ratingFa(v: Double): String = String.format(java.util.Locale.US, "%.1f", v).replace('.', '٫').toPersianDigits()

/** Thin divider inset past the avatar column. */
@Composable
fun InsetDivider(start: Dp = 56.dp) = HorizontalDivider(Modifier.padding(start = start), color = MaterialTheme.colorScheme.outlineVariant)
