package com.hamraheteyf.app.ui.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.Group
import com.hamraheteyf.app.data.repository.GroupRepository
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.EmptyState
import com.hamraheteyf.app.ui.common.GroupCard
import com.hamraheteyf.app.ui.common.ScreenHeader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsListViewModel(private val repo: GroupRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val groups: StateFlow<List<Group>> = query.flatMapLatest { repo.observeGroups(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val myGroups: StateFlow<List<Group>> = repo.observeMyGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun setQuery(q: String) { query.value = q }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsListScreen(onOpenGroup: (String) -> Unit, onCreate: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: GroupsListViewModel = viewModel(factory = appViewModelFactory { GroupsListViewModel(container.groupRepository) })
    val groups by vm.groups.collectAsState()
    val myGroups by vm.myGroups.collectAsState()
    val query by vm.query.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        topBar = { ScreenHeader("گروه‌ها") },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("گروه تازه") })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { vm.setQuery(it) }, label = { Text("جست‌وجوی گروه") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("همهٔ گروه‌ها") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("گروه‌های من") })
            }
            val list = if (tab == 0) groups else myGroups
            if (list.isEmpty()) {
                EmptyState(if (tab == 0) "گروهی یافت نشد. می‌توانید یک گروه تازه بسازید." else "هنوز عضو هیچ گروهی نیستید.")
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { g -> GroupCard(g) { onOpenGroup(g.id) } }
                }
            }
        }
    }
}
