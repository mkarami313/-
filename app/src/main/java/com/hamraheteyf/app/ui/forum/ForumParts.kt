package com.hamraheteyf.app.ui.forum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.Forum
import com.hamraheteyf.app.ui.common.relativeTimeFa
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.*

/** Prominent entry to «کلینیک». */
@Composable
fun ClinicEntryCard(clinic: Forum?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier, onClick = onClick, container = ExpertContainer.copy(alpha = 0.45f), border = Expert.copy(alpha = 0.25f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.MedicalServices, Expert, 44.dp)
            Spacer(Modifier.width(Dimens.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("کلینیک: پرسش از متخصص", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Icon(VerifiedIcon, null, tint = Expert, modifier = Modifier.size(15.dp))
                }
                Text("گفتاردرمان، کاردرمان، روان‌شناس و پزشکِ تأییدشده پاسخ می‌دهند",
                    style = MaterialTheme.typography.bodySmall, color = muted(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (clinic != null && clinic.topicCount > 0) {
                    Text("${clinic.topicCount.toPersianDigits()} پرسش", style = MaterialTheme.typography.labelSmall, color = Expert)
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, tint = muted())
        }
    }
}

/** Entry to «کانون‌ها» (local / topical groups). */
@Composable
fun GroupsEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.Groups, Apricot, 44.dp)
            Spacer(Modifier.width(Dimens.m))
            Column(Modifier.weight(1f)) {
                Text("کانون‌ها", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("گروه‌های شهری و موضوعی؛ با خانواده‌های نزدیکِ خودتان آشنا شوید",
                    style = MaterialTheme.typography.bodySmall, color = muted(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, tint = muted())
        }
    }
}

/** One top-level forum as a compact row with its sub-forums as chips underneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForumTreeCard(forum: Forum, onOpenForum: (String) -> Unit, modifier: Modifier = Modifier) {
    val color = hexColor(forum.color)
    AppCard(modifier, padding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { onOpenForum(forum.id) }.padding(Dimens.cardPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(iconFor(forum.icon), color)
            Spacer(Modifier.width(Dimens.m))
            Column(Modifier.weight(1f)) {
                Text(forum.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (forum.description.isNotBlank()) {
                    Text(forum.description, style = MaterialTheme.typography.bodySmall, color = muted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val meta = listOfNotNull(
                    "${forum.topicCount.toPersianDigits()} موضوع",
                    forum.lastPostAt?.let { "آخرین: ${relativeTimeFa(it)}" },
                ).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.labelSmall, color = muted())
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, tint = muted())
        }
        if (forum.children.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = Dimens.cardPad, vertical = Dimens.s),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                forum.children.forEach { c -> SubForumChip(c.title, c.topicCount, color) { onOpenForum(c.id) } }
            }
        }
    }
}

@Composable
private fun SubForumChip(title: String, count: Int, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = color.copy(alpha = 0.08f), modifier = Modifier.heightIn(min = 36.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (count > 0) {
                Spacer(Modifier.width(5.dp))
                Text(count.toPersianDigits(), style = MaterialTheme.typography.labelSmall, color = muted())
            }
        }
    }
}

/** Horizontal chips of sub-forums. [selected] null + [allLabel] adds an «all» chip (clinic filter). */
@Composable
fun SubForumChips(kids: List<Forum>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier, allLabel: String? = null) {
    val entries: List<Pair<String?, String>> = (if (allLabel != null) listOf(null to allLabel) else emptyList()) + kids.map { it.id to it.title }
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = Dimens.gutter), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(entries.size) { i ->
            val (id, title) = entries[i]
            val sel = allLabel != null && id == selected
            AssistChip(
                onClick = { onSelect(id) },
                label = { Text(title, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = if (allLabel == null) ({ Icon(Icons.Outlined.Topic, null, Modifier.size(16.dp)) }) else null,
                shape = RoundedCornerShape(50),
                colors = if (sel) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else AssistChipDefaults.assistChipColors(),
                border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}
