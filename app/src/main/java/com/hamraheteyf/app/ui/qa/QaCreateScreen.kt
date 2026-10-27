package com.hamraheteyf.app.ui.qa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.hamraheteyf.app.data.model.QuestionCategory
import com.hamraheteyf.app.data.repository.QuestionRepository
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.BigPrimaryButton
import com.hamraheteyf.app.ui.common.SafetyBanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class QaCreateViewModel(private val repo: QuestionRepository) : ViewModel() {
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun create(title: String, body: String, category: QuestionCategory, anonymous: Boolean, onDone: () -> Unit) {
        loading.value = true; error.value = null
        viewModelScope.launch {
            when (val r = repo.createQuestion(title, body, category, anonymous)) {
                is DataResult.Success -> { loading.value = false; onDone() }
                is DataResult.Error -> { loading.value = false; error.value = r.message }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QaCreateScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: QaCreateViewModel = viewModel(factory = appViewModelFactory { QaCreateViewModel(container.questionRepository) })
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(QuestionCategory.COMMUNICATION) }
    var anonymous by rememberSaveable { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پرسش تازه") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SafetyBanner()
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(
                    "لطفاً اطلاعات شناسایی‌کنندهٔ کودک (مانند نام کامل، تصویر یا آدرس) و اطلاعات اضطراری را در پرسش عمومی منتشر نکنید.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان پرسش") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("متن پرسش") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))

            Spacer(Modifier.height(12.dp))
            ExposedDropdownMenuBox(expanded = categoryMenu, onExpandedChange = { categoryMenu = it }) {
                OutlinedTextField(
                    value = category.label, onValueChange = {}, readOnly = true,
                    label = { Text("دسته‌بندی") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenu) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    QuestionCategory.entries.forEach { c ->
                        DropdownMenuItem(text = { Text(c.label) }, onClick = { category = c; categoryMenu = false })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = anonymous, onCheckedChange = { anonymous = it })
                Spacer(Modifier.width(8.dp))
                Text("انتشار به‌صورت ناشناس", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "نام ناشناس فقط از دید عموم پنهان است؛ مدیران برای رسیدگی به سوءاستفاده می‌توانند طبق سیاست حریم خصوصی به اطلاعات لازم دسترسی داشته باشند.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(20.dp))
            BigPrimaryButton(
                text = if (loading) "در حال ثبت…" else "ثبت پرسش",
                onClick = { vm.create(title, body, category, anonymous, onCreated) },
                enabled = !loading,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
