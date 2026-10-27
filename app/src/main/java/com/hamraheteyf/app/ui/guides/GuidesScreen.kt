package com.hamraheteyf.app.ui.guides

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.GuideArticle
import com.hamraheteyf.app.data.repository.GuideRepository
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.EmptyState
import com.hamraheteyf.app.ui.common.GuideCard
import kotlinx.coroutines.flow.*

class GuidesViewModel(repo: GuideRepository) : ViewModel() {
    val guides: StateFlow<List<GuideArticle>> = repo.observeGuides(null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val saved: StateFlow<List<GuideArticle>> = repo.observeSaved()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidesScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: GuidesViewModel = viewModel(factory = appViewModelFactory { GuidesViewModel(container.guideRepository) })
    val guides by vm.guides.collectAsState()
    val saved by vm.saved.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("راهنماها و منابع") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("همه") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("ذخیره‌شده‌ها") })
            }
            val list = if (tab == 0) guides else saved
            if (list.isEmpty()) {
                EmptyState(if (tab == 0) "راهنمایی برای نمایش وجود ندارد." else "هنوز راهنمایی ذخیره نکرده‌اید.")
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { g -> GuideCard(g) { onOpen(g.id) } }
                }
            }
        }
    }
}
