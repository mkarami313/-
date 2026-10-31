package com.hamraheteyf.app.ui.modules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.delay

private fun typeLabel(t: String) = when (t) {
    "question" -> "پرسش"; "group" -> "گروه"; "guide" -> "راهنما"; "user" -> "متخصص/کاربر"
    "donation" -> "دست‌به‌دست"; "course" -> "دوره"; "place" -> "مرکز"; "event" -> "رویداد"; else -> t
}

@Composable
fun SearchScreen(onBack: () -> Unit, onOpen: (SearchHit) -> Unit) {
    val client = rememberRecClient()
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { hits = null; error = null; return@LaunchedEffect }
        delay(350)
        when (val r = client?.search(q.trim())) {
            is ApiResult.Ok -> { hits = r.data; error = null }
            is ApiResult.Fail -> error = r.message
            null -> error = "جست‌وجو در حالت آفلاین در دسترس نیست."
        }
    }
    Scaffold(topBar = { ScreenHeader("جست‌وجو", onBack = onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(q, { q = it }, singleLine = true, label = { Text("پرسش، گروه، راهنما، دوره، مرکز…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth().padding(12.dp))
            when {
                error != null -> ErrorState(error!!)
                hits == null -> Text("حداقل دو حرف بنویسید.", Modifier.padding(16.dp), color = mutedColor())
                hits!!.isEmpty() -> EmptyState("نتیجه‌ای پیدا نشد.")
                else -> LazyColumn(contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hits!!, key = { it.type + it.id }) { h ->
                        CompactCard(onClick = { onOpen(h) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(h.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                    if (h.sub.isNotBlank()) Text(h.sub, style = MaterialTheme.typography.labelMedium, color = mutedColor(), maxLines = 1)
                                }
                                CategoryChip(typeLabel(h.type))
                            }
                        }
                    }
                }
            }
        }
    }
}
