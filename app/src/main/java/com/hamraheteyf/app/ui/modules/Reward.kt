package com.hamraheteyf.app.ui.modules

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.launch

private val Gold = Color(0xFFFFB300)

private fun Rec.goalN() = this["goal"].toIntOrNull()?.coerceIn(1, 30) ?: 5
private fun Rec.starsN() = this["stars"].toIntOrNull()?.coerceAtLeast(0) ?: 0

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RewardScreen(onBack: () -> Unit) {
    val client = rememberRecClient()
    val children = rememberChildren()
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Rec?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Rec?>(null) }
    var celebrate by remember { mutableStateOf<Rec?>(null) }
    var lastAdded by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    val st = rememberRecs(client, "reward", tick)
    val shown = remember(st.items, filter) { st.items?.filter { filter.isBlank() || it["childId"] == filter || it["childId"].isBlank() } }

    fun setStars(r: Rec, n: Int, reached: Boolean) {
        scope.launch {
            when (val res = client!!.save("reward", r.id, r.f + ("stars" to n.toString()))) {
                is ApiResult.Fail -> snack.showSnackbar(res.message)
                is ApiResult.Ok -> { lastAdded = r.id; if (reached) celebrate = r.copy(f = r.f + ("stars" to n.toString())) }
            }
            tick++
        }
    }

    Scaffold(
        topBar = { ScreenHeader("جدول ستاره", onBack = onBack) },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = { AddFab("جدول جدید") { adding = true } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ChildChipRow(children, filter, { filter = it }, modifier = Modifier.padding(12.dp, 8.dp))
            RecListBody(RecState(shown, st.error), "هنوز جدولی ندارید. یک هدف بسازید و برای هر کار خوب یک ستاره بدهید.", { tick++ }) { r ->
                val goal = r.goalN(); val stars = r.starsN()
                CompactCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r["emoji"].ifBlank { "🌟" }, fontSize = 30.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r["title"], style = MaterialTheme.typography.titleSmall)
                            val sub = listOf(children.nameOf(r["childId"]), r["goal"].let { "هدف: ${goal.toPersianDigits()} ستاره" }).filter { it.isNotBlank() }.joinToString(" · ")
                            Text(sub, style = MaterialTheme.typography.labelMedium, color = mutedColor())
                        }
                        OwnerActions({ editing = r }, { deleting = r })
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(goal) { i -> StarCell(filled = i < stars, popping = r.id == lastAdded && i == stars - 1) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { setStars(r, stars + 1, stars + 1 >= goal) }, enabled = stars < goal) { Text("⭐ یک ستاره بده") }
                        Text("${stars.toPersianDigits()} از ${goal.toPersianDigits()}", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.weight(1f))
                        if (stars > 0) TextButton(onClick = { setStars(r, 0, false) }) { Text("شروع دوباره") }
                    }
                }
            }
        }
    }

    celebrate?.let { r ->
        val inf = rememberInfiniteTransition(label = "party")
        val s by inf.animateFloat(0.85f, 1.25f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "s")
        AlertDialog(
            onDismissRequest = { celebrate = null },
            title = { Text("آفرین! 🎉", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("🎊🏆🎊", fontSize = 56.sp, modifier = Modifier.scale(s))
                    Spacer(Modifier.height(8.dp))
                    Text("به هدفِ «${r["title"]}» رسید!", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
            },
            confirmButton = { TextButton(onClick = { celebrate = null }) { Text("ممنون") } },
            dismissButton = { TextButton(onClick = { celebrate = null; setStars(r, 0, false) }) { Text("شروع دوباره") } },
        )
    }

    if ((adding || editing != null) && client != null) {
        val init = editing
        RecEditorDialog(client, "reward", if (init == null) "جدول ستارهٔ جدید" else "ویرایش جدول", init,
            mapOf("emoji" to "🌟", "goal" to "5", "stars" to "0", "childId" to filter.ifBlank { children.singleOrNull()?.id.orEmpty() }),
            { adding = false; editing = null }, { adding = false; editing = null; tick++ },
            canSave = { it["childId"].orEmpty().isNotBlank() && it["title"].orEmpty().isNotBlank() }) { v, _ ->
            ChildChipRow(children, v["childId"].orEmpty(), { v["childId"] = it }, allLabel = null)
            OutlinedTextField(v["title"].orEmpty(), { v["title"] = it }, label = { Text("عنوان *") }, supportingText = { Text("مثلاً «مسواک زدن بدون کمک»") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(v["emoji"].orEmpty(), { v["emoji"] = it.take(8) }, label = { Text("ایموجی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(v["goal"].orEmpty(), { v["goal"] = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("تعداد ستارهٔ لازم (۱ تا ۳۰)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        }
    }
    deleting?.let { d -> ConfirmDialog("این جدول حذف شود؟", onYes = { deleting = null; scope.launch { client?.delete("reward", d.id); tick++ } }, onDismiss = { deleting = null }) }
}

@Composable
private fun StarCell(filled: Boolean, popping: Boolean) {
    val target = if (filled) 1f else 0.9f
    val s by animateFloatAsState(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "star")
    val pop by animateFloatAsState(if (popping) 1.35f else 1f, spring(Spring.DampingRatioHighBouncy), label = "pop")
    Icon(
        if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder, null,
        tint = if (filled) Gold else MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(38.dp).scale(s * pop),
    )
}
