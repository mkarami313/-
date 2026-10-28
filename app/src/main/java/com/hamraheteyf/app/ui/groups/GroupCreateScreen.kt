package com.hamraheteyf.app.ui.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.GroupVisibility
import com.hamraheteyf.app.data.repository.GroupRepository
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.BigPrimaryButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class GroupCreateViewModel(private val repo: GroupRepository) : ViewModel() {
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    fun create(title: String, description: String, category: String, city: String, visibility: GroupVisibility, rules: String, onDone: (String) -> Unit) {
        loading.value = true; error.value = null
        viewModelScope.launch {
            when (val r = repo.createGroup(title, description, category, city, visibility, rules)) {
                is DataResult.Success -> { loading.value = false; onDone(r.data) }
                is DataResult.Error -> { loading.value = false; error.value = r.message }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreateScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: GroupCreateViewModel = viewModel(factory = appViewModelFactory { GroupCreateViewModel(container.groupRepository) })
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var rules by rememberSaveable { mutableStateOf("") }
    var visibility by rememberSaveable { mutableStateOf(GroupVisibility.PUBLIC) }

    Scaffold(
        topBar = {
            com.hamraheteyf.app.ui.common.ScreenHeader("ساخت گروه", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان گروه") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("توضیح گروه") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("دسته‌بندی (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("شهر (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = rules, onValueChange = { rules = it }, label = { Text("قوانین گروه (اختیاری)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp))

            Spacer(Modifier.height(16.dp))
            Text("نوع گروه", style = MaterialTheme.typography.titleMedium)
            GroupVisibility.entries.forEach { v ->
                Row(Modifier.fillMaxWidth().selectable(selected = visibility == v, onClick = { visibility = v }).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = visibility == v, onClick = { visibility = v })
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(v.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (v == GroupVisibility.PRIVATE) "گروه خصوصی فقط برای اعضای تأییدشده قابل مشاهده است." else "گروه عمومی برای همهٔ کاربران قابل مشاهده است.",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            BigPrimaryButton(
                text = if (loading) "در حال ساخت…" else "ساخت گروه",
                onClick = { vm.create(title, description, category, city, visibility, rules, onCreated) },
                enabled = !loading,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
