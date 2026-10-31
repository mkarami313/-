package com.hamraheteyf.app.ui.modules

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.launch

@Composable
fun FirstThenScreen(onBack: () -> Unit) {
    val client = rememberRecClient()
    val api = LocalAppContainer.current.serverApi
    val children = rememberChildren()
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Rec?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Rec?>(null) }
    var playing by remember { mutableStateOf<Rec?>(null) }
    val st = rememberRecs(client, "firstthen", tick)
    val shown = remember(st.items, filter) { st.items?.filter { filter.isBlank() || it["childId"] == filter || it["childId"].isBlank() } }

    playing?.let { r ->
        Dialog(onDismissRequest = { playing = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize()) { FirstThenBoard(r, api) { playing = null } }
        }
    }

    Scaffold(
        topBar = { ScreenHeader("اول ... بعد ...", onBack = onBack) },
        floatingActionButton = { AddFab("تابلوی جدید") { adding = true } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            InfoBanner("به کودک نشان دهید اول چه کاری و بعد چه چیزی در انتظار اوست؛ مثلاً «اول مسواک، بعد کارتون».", Modifier.padding(12.dp, 8.dp), dismissKey = "firstthen_info")
            ChildChipRow(children, filter, { filter = it }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            RecListBody(RecState(shown, st.error), "تابلویی ندارید. «اول» و «بعد» را بسازید.", { tick++ }) { r ->
                CompactCard(onClick = { playing = r }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r["firstEmoji"].ifBlank { "1️⃣" }, fontSize = 26.sp)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text("اول: ${r["first"]}", style = MaterialTheme.typography.titleSmall)
                            Text("بعد: ${r["then"]}", style = MaterialTheme.typography.bodyMedium)
                            val c = children.nameOf(r["childId"])
                            if (c.isNotBlank()) Text(c, style = MaterialTheme.typography.labelMedium, color = mutedColor())
                        }
                        Text(r["thenEmoji"].ifBlank { "2️⃣" }, fontSize = 26.sp)
                        OwnerActions({ editing = r }, { deleting = r })
                    }
                }
            }
        }
    }
    if ((adding || editing != null) && client != null) {
        var newFirst by remember { mutableStateOf<ByteArray?>(null) }
        var newThen by remember { mutableStateOf<ByteArray?>(null) }
        RecEditorDialog(client, "firstthen", if (editing == null) "تابلوی جدید" else "ویرایش تابلو", editing,
            mapOf("childId" to filter.ifBlank { children.singleOrNull()?.id.orEmpty() }),
            { adding = false; editing = null }, { adding = false; editing = null; tick++ },
            canSave = { it["childId"].orEmpty().isNotBlank() && it["first"].orEmpty().isNotBlank() && it["then"].orEmpty().isNotBlank() },
            beforeSave = { data ->
                for ((bytes, key) in listOf(newFirst to "firstImage", newThen to "thenImage")) {
                    if (bytes != null && api != null) when (val u = api.uploadImageId(bytes)) {
                        is ApiResult.Ok -> data[key] = u.data
                        is ApiResult.Fail -> return@RecEditorDialog u.message
                    }
                }
                null
            }) { v, setError ->
            ChildChipRow(children, v["childId"].orEmpty(), { v["childId"] = it }, allLabel = null)
            SectionLabel("اول")
            OutlinedTextField(v["first"].orEmpty(), { v["first"] = it }, label = { Text("اول چه کاری؟ *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(v["firstEmoji"].orEmpty(), { v["firstEmoji"] = it.take(8) }, label = { Text("ایموجی (وقتی عکس ندارد)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ImagePickRow(api, "عکس", v["firstImage"].orEmpty(), newFirst, { newFirst = it }, { newFirst = null; v["firstImage"] = "" }, { setError(it) })
            SectionLabel("بعد")
            OutlinedTextField(v["then"].orEmpty(), { v["then"] = it }, label = { Text("بعد چه چیزی؟ *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(v["thenEmoji"].orEmpty(), { v["thenEmoji"] = it.take(8) }, label = { Text("ایموجی (وقتی عکس ندارد)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ImagePickRow(api, "عکس", v["thenImage"].orEmpty(), newThen, { newThen = it }, { newThen = null; v["thenImage"] = "" }, { setError(it) })
        }
    }
    deleting?.let { d -> ConfirmDialog("این تابلو حذف شود؟", onYes = { deleting = null; scope.launch { client?.delete("firstthen", d.id); tick++ } }, onDismiss = { deleting = null }) }
}

@Composable
private fun FirstThenBoard(r: Rec, api: com.hamraheteyf.app.net.HamrahApi?, onClose: () -> Unit) {
    val speak = rememberSpeaker()
    var done by remember(r.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("بستن") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { speak("اول ${r["first"]}. بعد ${r["then"]}.") }) { Icon(Icons.Outlined.VolumeUp, null); Spacer(Modifier.width(6.dp)); Text("بخوان") }
        }
        BoardCard("اول", r["first"], r["firstEmoji"].ifBlank { "1️⃣" }, r["firstImage"], api, active = !done, finished = done, modifier = Modifier.weight(1f)) { speak("اول ${r["first"]}") }
        BoardCard("بعد", r["then"], r["thenEmoji"].ifBlank { "2️⃣" }, r["thenImage"], api, active = done, finished = false, modifier = Modifier.weight(1f)) { speak("بعد ${r["then"]}") }
        if (!done) Button(onClick = { done = true; speak("آفرین! حالا ${r["then"]}") }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text("تمام شد ✓", fontSize = 20.sp) }
        else OutlinedButton(onClick = { done = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text("از اول", fontSize = 18.sp) }
    }
}

@Composable
private fun BoardCard(label: String, text: String, emoji: String, imageId: String, api: com.hamraheteyf.app.net.HamrahApi?, active: Boolean, finished: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val scale by animateFloatAsState(if (active) 1f else 0.94f, tween(400), label = "scale")
    val alpha by animateFloatAsState(if (finished) 0.45f else if (active) 1f else 0.7f, tween(400), label = "alpha")
    Card(
        onClick = onTap,
        modifier = modifier.fillMaxWidth().scale(scale).alpha(alpha),
        border = BorderStroke(if (active) 4.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (imageId.isNotBlank() && api != null) RemoteImage(api.fileUrl(imageId), Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium), ContentScale.Fit)
                    else Text(emoji, fontSize = 96.sp)
                }
                Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            }
            if (finished) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center).size(96.dp))
        }
    }
}
