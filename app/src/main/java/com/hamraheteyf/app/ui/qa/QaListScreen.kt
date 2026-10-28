package com.hamraheteyf.app.ui.qa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.QuestionCategory
import com.hamraheteyf.app.data.model.QuestionSort
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.EmptyState
import com.hamraheteyf.app.ui.common.FilterDropdown
import com.hamraheteyf.app.ui.common.QuestionCard
import com.hamraheteyf.app.ui.common.ScreenHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QaListScreen(onOpenQuestion: (String) -> Unit, onAsk: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: QaListViewModel = viewModel(factory = appViewModelFactory { QaListViewModel(container.questionRepository, container.userRepository) })
    val items by vm.items.collectAsState()
    val sort by vm.sort.collectAsState()
    val category by vm.category.collectAsState()

    Scaffold(
        topBar = { ScreenHeader("پرسش‌وپاسخ") },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAsk, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("پرسش بپرس") })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Sort + category as compact dropdowns (no overflowing chip rows).
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilterDropdown(
                    label = "مرتب‌سازی",
                    selected = sort.label,
                    options = QuestionSort.entries.map { it.label },
                    onSelected = { vm.setSort(QuestionSort.entries[it]) },
                    modifier = Modifier.weight(1f),
                )
                val catOptions = listOf("همه") + QuestionCategory.entries.map { it.label }
                FilterDropdown(
                    label = "دسته‌بندی",
                    selected = category?.label ?: "همه",
                    options = catOptions,
                    onSelected = { vm.setCategory(if (it == 0) null else QuestionCategory.entries[it - 1]) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (items.isEmpty()) {
                EmptyState("پرسشی با این فیلترها یافت نشد. می‌توانید اولین پرسش را ثبت کنید.")
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items, key = { it.first.id }) { (q, author) ->
                        QuestionCard(q, author) { onOpenQuestion(q.id) }
                    }
                }
            }
        }
    }
}
