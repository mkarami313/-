package com.hamraheteyf.app.ui.forum

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.CommunityClient
import com.hamraheteyf.app.community.QuoteRef
import com.hamraheteyf.app.ui.kit.muted
import com.hamraheteyf.app.ui.theme.Dimens

/** Reply bar pinned to the bottom of a topic: quote chip, attachments, text field, send. */
@Composable
fun ReplyComposer(
    canReply: Boolean,
    hint: String,
    text: String,
    onText: (String) -> Unit,
    quote: QuoteRef?,
    onClearQuote: () -> Unit,
    uploads: ImageUploads,
    client: CommunityClient?,
    sending: Boolean,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 2.dp, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                .padding(horizontal = Dimens.gutter, vertical = Dimens.s),
        ) {
            if (!canReply) {
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, tint = muted(), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Dimens.s))
                    Text(hint.ifBlank { "امکانِ پاسخ در این گفتگو نیست." }, style = MaterialTheme.typography.bodyMedium, color = muted())
                }
                return@Column
            }
            if (quote != null) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
                    Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FormatQuote, null, tint = muted(), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text("پاسخ به ${quote.authorName}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(quote.excerpt, style = MaterialTheme.typography.labelSmall, color = muted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = onClearQuote) { Icon(Icons.Outlined.Close, "حذفِ نقلِ قول", Modifier.size(18.dp)) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            AttachmentStrip(uploads, client, Modifier.padding(bottom = 6.dp), size = 56.dp)
            val pick = rememberImagePicker(uploads)
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = pick, enabled = uploads.remaining > 0 && !sending) {
                    Icon(Icons.Outlined.AddPhotoAlternate, "افزودنِ تصویر")
                }
                OutlinedTextField(
                    value = text, onValueChange = onText, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    placeholder = { Text("پاسخِ شما…") }, maxLines = 5, enabled = !sending,
                    shape = RoundedCornerShape(22.dp),
                )
                Spacer(Modifier.width(4.dp))
                val ready = text.isNotBlank() && !uploads.busy && !sending
                FilledIconButton(onClick = onSend, enabled = ready, modifier = Modifier.padding(bottom = 4.dp).size(44.dp)) {
                    if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, "ارسال")
                }
            }
        }
    }
}
