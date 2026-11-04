package com.hamraheteyf.app.ui.forum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.Forum
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.hexColor

/** Bottom sheet that lists the forum tree: top-level headers (selectable) and their sub-forums. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumPickerSheet(tree: List<Forum>, selected: String?, onPick: (Forum) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("انتخابِ تالار", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.s))
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = Dimens.xl)) {
            tree.forEach { top ->
                item(key = top.id) {
                    PickerRow(top.title, top.id == selected, header = true, icon = { IconBadge(iconFor(top.icon), hexColor(top.color), 32.dp) }) { onPick(top) }
                }
                top.children.forEach { c ->
                    item(key = c.id) { PickerRow(c.title, c.id == selected, header = false) { onPick(c) } }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(title: String, selected: Boolean, header: Boolean, icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick)
            .padding(start = if (header) Dimens.l else 60.dp, end = Dimens.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { icon(); Spacer(Modifier.width(Dimens.m)) }
        Text(title, style = if (header) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.Check, "انتخاب‌شده", tint = MaterialTheme.colorScheme.primary)
    }
}

/** Poll durations offered in the composer (days; 0 = open-ended). */
val PollDurations = listOf(0 to "بدون پایان", 1 to "۱ روز", 3 to "۳ روز", 7 to "۷ روز", 30 to "۳۰ روز")

/** Question, 2–10 options, multi-choice switch and duration chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PollEditor(
    question: String, onQuestion: (String) -> Unit,
    options: List<String>, onOptions: (List<String>) -> Unit,
    multi: Boolean, onMulti: (Boolean) -> Unit,
    days: Int, onDays: (Int) -> Unit,
) {
    AppCard {
        OutlinedTextField(question, { onQuestion(it.take(200)) }, Modifier.fillMaxWidth(), label = { Text("پرسشِ نظرسنجی") }, singleLine = true)
        Spacer(Modifier.height(Dimens.s))
        options.forEachIndexed { i, o ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    o, { v -> onOptions(options.toMutableList().also { it[i] = v.take(80) }) }, Modifier.weight(1f).padding(vertical = 3.dp),
                    label = { Text("گزینهٔ ${(i + 1).toPersianDigits()}") }, singleLine = true,
                )
                if (options.size > 2) IconButton(onClick = { onOptions(options.toMutableList().also { it.removeAt(i) }) }) {
                    Icon(Icons.Outlined.Close, "حذفِ گزینه")
                }
            }
        }
        if (options.size < 10) TextButton(onClick = { onOptions(options + "") }) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("افزودنِ گزینه")
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onMulti(!multi) }, verticalAlignment = Alignment.CenterVertically) {
            Text("چند گزینه‌ای", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(multi, onMulti)
        }
        Text("مدتِ نظرسنجی", style = MaterialTheme.typography.labelLarge, color = muted())
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PollDurations.forEach { (d, label) -> FilterChip(d == days, { onDays(d) }, { Text(label) }) }
        }
    }
}

/** A labelled switch row with a one-line explanation. */
@Composable
fun SwitchRow(title: String, note: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = muted())
        }
        Spacer(Modifier.width(Dimens.s))
        Switch(checked, onChange)
    }
}
