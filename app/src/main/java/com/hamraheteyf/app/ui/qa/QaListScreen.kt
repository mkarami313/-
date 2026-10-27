package com.hamraheteyf.app.ui.qa

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.hamraheteyf.app.ui.common.QuestionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QaListScreen(onOpenQuestion: (String) -> Unit, onAsk: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: QaListViewModel = viewModel(factory = appViewModelFactory { QaListViewModel(container.questionRepository, container.userRepository) })
    val items by vm.items.collectAsState()
    val sort by vm.sort.collectAsState()
    val category by vm.category.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("پرسش‌وپاسخ") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAsk, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("پرسش بپرس") })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Sort selector (explicit sorting criterion as required).
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                QuestionSort.entries.forEach { s ->
                    FilterChip(selected = sort == s, onClick = { vm.setSort(s) }, label = { Text(s.label) }, modifier = Modifier.padding(end = 8.dp))
                }
            }
            // Category filter row.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                FilterChip(selected = category == null, onClick = { vm.setCategory(null) }, label = { Text("همه") }, modifier = Modifier.padding(end = 8.dp))
                QuestionCategory.entries.forEach { c ->
                    FilterChip(selected = category == c, onClick = { vm.setCategory(c) }, label = { Text(c.label) }, modifier = Modifier.padding(end = 8.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
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
