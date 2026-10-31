package com.hamraheteyf.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.data.model.Group
import com.hamraheteyf.app.data.model.GuideArticle
import com.hamraheteyf.app.data.model.Question

/** Small colored avatar showing a name's first letter. Color is derived from the name. */
@Composable
fun Avatar(name: String, size: Int = 40) {
    val palette = listOf(
        Color(0xFF2F8F7F), Color(0xFFF6A94A), Color(0xFF6C8EBF),
        Color(0xFFB07BAC), Color(0xFF5BA17A), Color(0xFFD98C5F),
    )
    val color = palette[(name.hashCode() and 0x7fffffff) % palette.size]
    val letter = name.trim().firstOrNull()?.toString() ?: "؟"
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CategoryChip(text: String, accent: Boolean = false) {
    val container = if (accent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (accent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(color = container, shape = MaterialTheme.shapes.small) {
        Text(
            text, style = MaterialTheme.typography.labelMedium, color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SoftCard(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

@Composable
fun QuestionCard(question: Question, author: AuthorView, onClick: () -> Unit) {
    SoftCard(onClick) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryChip(question.category.label)
                Spacer(Modifier.width(8.dp))
                author.badge?.let { AuthorBadgeChip(it) }
            }
            Spacer(Modifier.height(6.dp))
            Text(question.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(question.body, style = MaterialTheme.typography.bodyMedium, color = mutedColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(author.name, size = 28)
                Spacer(Modifier.width(8.dp))
                Text("${author.name} · ${relativeTimeFa(question.createdAt)}", style = MaterialTheme.typography.labelMedium, color = mutedColor())
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "پاسخ‌ها", modifier = Modifier.size(16.dp), tint = mutedColor())
                Spacer(Modifier.width(4.dp))
                Text(question.answerCount.toPersianDigits(), style = MaterialTheme.typography.labelMedium, color = mutedColor())
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Outlined.ThumbUp, contentDescription = "مفید", modifier = Modifier.size(16.dp), tint = mutedColor())
                Spacer(Modifier.width(4.dp))
                Text(question.helpfulCount.toPersianDigits(), style = MaterialTheme.typography.labelMedium, color = mutedColor())
            }
        }
    }
}

@Composable
fun GroupCard(group: Group, onClick: () -> Unit) {
    SoftCard(onClick) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(group.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                CategoryChip(group.visibility.label, accent = true)
            }
            Spacer(Modifier.height(6.dp))
            Text(group.description, style = MaterialTheme.typography.bodyMedium, color = mutedColor(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            val meta = buildList {
                group.category?.let { add(it) }
                group.city?.let { add(it) }
                add("${group.memberCount.toPersianDigits()} عضو")
            }.joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.labelMedium, color = mutedColor())
        }
    }
}

@Composable
fun GuideCard(guide: GuideArticle, onClick: () -> Unit) {
    SoftCard(onClick) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryChip(guide.category)
                    if (guide.authorIsVerifiedProfessional) {
                        Spacer(Modifier.width(8.dp))
                        AuthorBadgeChip(com.hamraheteyf.app.data.model.AuthorBadge.PROFESSIONAL_VERIFIED)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(guide.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("نویسنده: ${guide.authorName}", style = MaterialTheme.typography.labelMedium, color = mutedColor())
            }
        }
    }
}
