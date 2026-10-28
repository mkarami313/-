package com.hamraheteyf.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.AgeRange
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.BigPrimaryButton
import com.hamraheteyf.app.ui.common.InfoBanner
import com.hamraheteyf.app.ui.common.mutedColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildrenScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: AccountViewModel = viewModel(factory = appViewModelFactory { AccountViewModel(container.authRepository, container.userRepository) })
    val children by vm.children.collectAsState()

    var nickname by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf("") }
    var ageRange by remember { mutableStateOf(AgeRange.UNSPECIFIED) }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            com.hamraheteyf.app.ui.common.ScreenHeader("اطلاعات کودک", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            InfoBanner("این اطلاعات خصوصی و اختیاری است و به‌صورت عمومی نمایش داده نمی‌شود. لطفاً از ثبت نام کامل، آدرس یا تصویر خودداری کنید.")
            Spacer(Modifier.height(16.dp))

            children.forEach { child ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(child.nickname ?: "بدون نام مستعار", style = MaterialTheme.typography.titleMedium)
                            Text("بازهٔ سنی: ${child.ageRange.label}", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
                            if (child.interests.isNotEmpty()) Text("علاقه‌ها: ${child.interests.joinToString("، ")}", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
                        }
                        TextButton(onClick = { vm.deleteChild(child.id) }) { Text("حذف") }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("افزودن", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("نام مستعار (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }) {
                OutlinedTextField(
                    value = ageRange.label, onValueChange = {}, readOnly = true, label = { Text("بازهٔ سنی") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menu) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    AgeRange.entries.forEach { a -> DropdownMenuItem(text = { Text(a.label) }, onClick = { ageRange = a; menu = false }) }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = interests, onValueChange = { interests = it }, label = { Text("علاقه‌ها (با «،» جدا کنید)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            BigPrimaryButton(text = "افزودن", onClick = {
                val list = interests.split("،", ",").map { it.trim() }.filter { it.isNotEmpty() }
                vm.addChild(nickname, ageRange, list)
                nickname = ""; interests = ""; ageRange = AgeRange.UNSPECIFIED
            })
            Spacer(Modifier.height(24.dp))
        }
    }
}
