package com.hamraheteyf.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.data.model.Group
import com.hamraheteyf.app.data.model.GroupVisibility
import com.hamraheteyf.app.data.model.GuideArticle
import com.hamraheteyf.app.data.model.Question

@Composable
fun CategoryChip(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun QuestionCard(question: Question, author: AuthorView, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryChip(question.category.label)
                Spacer(Modifier.width(8.dp))
                author.badge?.let { AuthorBadgeChip(it) }
            }
            Spacer(Modifier.height(8.dp))
            Text(question.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(question.body, style = MaterialTheme.typography.bodyMedium, color = mutedColor(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
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
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(group.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Text(group.visibility.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(group.description, style = MaterialTheme.typography.bodyMedium, color = mutedColor(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
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
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryChip(guide.category)
                Spacer(Modifier.width(8.dp))
                if (guide.authorIsVerifiedProfessional) AuthorBadgeChip(com.hamraheteyf.app.data.model.AuthorBadge.PROFESSIONAL_VERIFIED)
            }
            Spacer(Modifier.height(8.dp))
            Text(guide.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("نویسنده: ${guide.authorName}", style = MaterialTheme.typography.labelMedium, color = mutedColor())
        }
    }
}
