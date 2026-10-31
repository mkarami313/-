package com.hamraheteyf.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.AgeRange
import com.hamraheteyf.app.data.model.ChildProfilePrivate
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.BigPrimaryButton
import com.hamraheteyf.app.ui.common.InfoBanner
import com.hamraheteyf.app.ui.common.mutedColor

private fun parseInterests(raw: String) = raw.split("،", ",").map { it.trim() }.filter { it.isNotEmpty() }

private const val DELETE_WORD = "حذف"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildFields(
    nickname: String, onNickname: (String) -> Unit,
    ageRange: AgeRange, onAge: (AgeRange) -> Unit,
    interests: String, onInterests: (String) -> Unit,
    notes: String? = null, onNotes: (String) -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    OutlinedTextField(value = nickname, onValueChange = { onNickname(it.take(40)) }, label = { Text("نام مستعار (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }) {
        OutlinedTextField(
            value = ageRange.label, onValueChange = {}, readOnly = true, label = { Text("بازهٔ سنی") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menu) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            AgeRange.entries.forEach { a -> DropdownMenuItem(text = { Text(a.label) }, onClick = { onAge(a); menu = false }) }
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(value = interests, onValueChange = onInterests, label = { Text("علاقه‌ها (با «،» جدا کنید)") }, modifier = Modifier.fillMaxWidth())
    if (notes != null) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = notes, onValueChange = { onNotes(it.take(1000)) }, label = { Text("یادداشت خصوصی (اختیاری)") },
            supportingText = { Text("فقط خودتان می‌بینید", color = mutedColor()) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
        )
    }
}

@Composable
private fun EditChildDialog(child: ChildProfilePrivate, busy: Boolean, onDismiss: () -> Unit, onSave: (String, AgeRange, List<String>, String) -> Unit) {
    var nickname by remember(child.id) { mutableStateOf(child.nickname.orEmpty()) }
    var age by remember(child.id) { mutableStateOf(child.ageRange) }
    var interests by remember(child.id) { mutableStateOf(child.interests.joinToString("، ")) }
    var notes by remember(child.id) { mutableStateOf(child.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("ویرایش اطلاعات کودک") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { ChildFields(nickname, { nickname = it }, age, { age = it }, interests, { interests = it }, notes, { notes = it }) } },
        confirmButton = { TextButton(enabled = !busy, onClick = { onSave(nickname, age, parseInterests(interests), notes) }) { Text(if (busy) "در حال ذخیره…" else "ذخیره") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun DeleteChildDialog(child: ChildProfilePrivate, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val expected = child.nickname?.trim().orEmpty().ifEmpty { DELETE_WORD }
    var typed by remember(child.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حذف اطلاعات کودک") },
        text = {
            Column {
                Text("با حذف این کودک، همهٔ سوابق وابسته به او (پرونده، ثبت رفتار، برنامه‌ها و…) هم پاک می‌شود و برگشت‌پذیر نیست.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text("برای تأیید، «$expected» را بنویسید.", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("تأیید حذف") })
            }
        },
        confirmButton = {
            TextButton(enabled = typed.trim() == expected, onClick = onConfirm) { Text("حذف همیشگی", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildrenScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: AccountViewModel = viewModel(factory = appViewModelFactory { AccountViewModel(container.authRepository, container.userRepository) })
    val children by vm.children.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    var nickname by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf("") }
    var ageRange by remember { mutableStateOf(AgeRange.UNSPECIFIED) }
    var editing by remember { mutableStateOf<ChildProfilePrivate?>(null) }
    var deleting by remember { mutableStateOf<ChildProfilePrivate?>(null) }

    editing?.let { c ->
        EditChildDialog(c, busy, onDismiss = { editing = null }) { n, a, i, nt -> vm.updateChild(c.id, n, a, i, nt) { ok -> if (ok) editing = null } }
    }
    deleting?.let { c ->
        DeleteChildDialog(c, onDismiss = { deleting = null }) { vm.deleteChild(c.id); deleting = null }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { com.hamraheteyf.app.ui.common.ScreenHeader("اطلاعات کودک", onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            InfoBanner("این اطلاعات خصوصی و اختیاری است و به‌صورت عمومی نمایش داده نمی‌شود. لطفاً از ثبت نام کامل، آدرس یا تصویر خودداری کنید.")
            Spacer(Modifier.height(16.dp))

            children.forEach { child ->
                val label = child.nickname?.takeIf { it.isNotBlank() } ?: "بدون نام مستعار"
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                            Text("بازهٔ سنی: ${child.ageRange.label}", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
                            if (child.interests.isNotEmpty()) Text("علاقه‌ها: ${child.interests.joinToString("، ")}", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
                        }
                        IconButton(onClick = { editing = child }) { Icon(Icons.Outlined.Edit, contentDescription = "ویرایش $label") }
                        IconButton(onClick = { deleting = child }) { Icon(Icons.Outlined.Delete, contentDescription = "حذف $label", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("افزودن", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ChildFields(nickname, { nickname = it }, ageRange, { ageRange = it }, interests, { interests = it })
            Spacer(Modifier.height(16.dp))
            BigPrimaryButton(text = if (busy) "در حال ثبت…" else "افزودن", enabled = !busy, onClick = {
                vm.addChild(nickname.trim(), ageRange, parseInterests(interests)) { ok ->
                    if (ok) { nickname = ""; interests = ""; ageRange = AgeRange.UNSPECIFIED }
                }
            })
            Spacer(Modifier.height(24.dp))
        }
    }
}
