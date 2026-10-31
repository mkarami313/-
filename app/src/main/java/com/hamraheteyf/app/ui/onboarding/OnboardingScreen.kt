package com.hamraheteyf.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.AgeRange
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.BigPrimaryButton
import com.hamraheteyf.app.ui.common.mutedColor
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.navigation.AppFeatures
import com.hamraheteyf.app.ui.navigation.FeatureFlags
import com.hamraheteyf.app.ui.profile.AccountViewModel

private class Goal(val label: String, val features: Set<String>)

private val GOALS = listOf(
    Goal("پیگیری رشد و رفتار کودک", setOf("childfile", "tools")),
    Goal("آموزش و یادگیری", setOf("courses", "guides")),
    Goal("پرسش و هم‌صحبتی با خانواده‌های دیگر", setOf("qa", "groups")),
    Goal("پیدا کردن متخصص و مرکز درمانی", setOf("directory", "booking", "places")),
    Goal("اهدا یا دریافت لوازم دست‌دوم", setOf("donations")),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val container = LocalAppContainer.current
    val ctx = LocalContext.current
    val uid by container.authRepository.currentUserId.collectAsState()
    val vm: AccountViewModel = viewModel(factory = appViewModelFactory { AccountViewModel(container.authRepository, container.userRepository) })
    val children by vm.children.collectAsState()
    val disabled by FeatureFlags.disabled.collectAsState()
    val tiles = remember(disabled) { AppFeatures.enabled(disabled).filter { it.homeTile } }

    val strSet = listSaver<MutableState<Set<String>>, String>(save = { it.value.toList() }, restore = { mutableStateOf(it.toSet()) })
    var step by rememberSaveable { mutableIntStateOf(0) }
    var role by rememberSaveable { mutableStateOf("parent") }
    val goals = rememberSaveable(saver = strSet) { mutableStateOf(setOf<String>()) }
    val picked = rememberSaveable(saver = strSet) { mutableStateOf(emptySet<String>()) }
    var pickedTouched by rememberSaveable { mutableStateOf(false) }
    var nickname by rememberSaveable { mutableStateOf("") }
    var ageIdx by rememberSaveable { mutableIntStateOf(0) }
    var ageMenu by remember { mutableStateOf(false) }
    var added by rememberSaveable { mutableStateOf(false) }
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    fun finish(save: Boolean) {
        val id = uid
        if (id != null) {
            if (save) OnboardingPrefs.setInterests(ctx, id, picked.value)
            OnboardingPrefs.setDone(ctx, id)
        }
        if (role == "pro") OnboardingPrefs.setPendingPro(ctx, true)
        onFinished()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("مرحلهٔ ${(step + 1).toPersianDigits()} از ${3.toPersianDigits()}", style = MaterialTheme.typography.labelLarge, color = mutedColor(), modifier = Modifier.weight(1f))
                TextButton(onClick = { finish(save = false) }) { Text("رد کردن") }
            }
            LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))

            when (step) {
                0 -> {
                    Text("به «همراهِ طیف» خوش آمدید", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("چند پرسش کوتاه تا برنامه را مناسب شما بچینیم. همه‌چیز را بعداً هم می‌توانید عوض کنید.", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
                    Spacer(Modifier.height(16.dp))
                    Text("شما کدام‌اید؟", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("parent" to "والد", "caregiver" to "مراقب یا اطرافیان", "pro" to "متخصص یا مربی").forEach { (k, l) ->
                            FilterChip(selected = role == k, onClick = { role = k }, label = { Text(l) })
                        }
                    }
                    if (role == "pro") Text("پس از ورود، مراحل تأیید تخصص برایتان باز می‌شود.", style = MaterialTheme.typography.bodySmall, color = mutedColor(), modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("چه چیزی برایتان مهم‌تر است؟", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GOALS.forEach { g ->
                            val on = g.label in goals.value
                            FilterChip(selected = on, onClick = { goals.value = if (on) goals.value - g.label else goals.value + g.label }, label = { Text(g.label) })
                        }
                    }
                }
                1 -> {
                    Text("کودک خود را اضافه کنید", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("این اطلاعات خصوصی و اختیاری است. نام مستعار کافی است؛ از ثبت نام کامل یا عکس خودداری کنید.", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
                    Spacer(Modifier.height(16.dp))
                    if (children.isNotEmpty() || added) {
                        Text("کودک اضافه شد. می‌توانید ادامه دهید یا بعداً از پروفایل کودک دیگری بیفزایید.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    } else {
                        OutlinedTextField(nickname, { nickname = it }, label = { Text("نام مستعار") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        ExposedDropdownMenuBox(expanded = ageMenu, onExpandedChange = { ageMenu = it }) {
                            OutlinedTextField(
                                value = AgeRange.entries[ageIdx].label, onValueChange = {}, readOnly = true, label = { Text("بازهٔ سنی") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ageMenu) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            )
                            ExposedDropdownMenu(expanded = ageMenu, onDismissRequest = { ageMenu = false }) {
                                AgeRange.entries.forEachIndexed { i, a -> DropdownMenuItem(text = { Text(a.label) }, onClick = { ageIdx = i; ageMenu = false }) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        BigPrimaryButton(if (busy) "در حال افزودن…" else "افزودن کودک", enabled = nickname.isNotBlank() && !busy, onClick = {
                            vm.addChild(nickname.trim(), AgeRange.entries[ageIdx], emptyList()) { ok -> if (ok) added = true }
                        })
                    }
                }
                else -> {
                    Text("میان‌برهای صفحهٔ اصلی", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("بخش‌هایی که بیشتر استفاده می‌کنید را انتخاب کنید تا اول صفحهٔ اصلی بیایند.", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
                    Spacer(Modifier.height(16.dp))
                    val suggested = remember(goals.value) { GOALS.filter { it.label in goals.value }.flatMap { it.features }.toSet() }
                    LaunchedEffect(suggested) { if (!pickedTouched) picked.value = suggested.filter { s -> tiles.any { it.id == s } }.toSet() }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tiles.forEach { f ->
                            val on = f.id in picked.value
                            FilterChip(selected = on, onClick = { pickedTouched = true; picked.value = if (on) picked.value - f.id else picked.value + f.id }, label = { Text(f.title) })
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("قبلی") }
                Button(
                    onClick = { if (step < 2) step++ else finish(save = true) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text(if (step < 2) "بعدی" else "شروع") }
            }
        }
    }
}
