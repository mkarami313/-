package com.hamraheteyf.app.ui.directory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.repository.ProfessionalDirectoryItem
import com.hamraheteyf.app.data.repository.ProfessionalFilter
import com.hamraheteyf.app.data.repository.UserRepository
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class DirectoryViewModel(private val userRepo: UserRepository) : ViewModel() {
    val filter = MutableStateFlow(ProfessionalFilter())
    val results: StateFlow<List<ProfessionalDirectoryItem>> = filter
        .flatMapLatest { userRepo.searchProfessionals(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { filter.value = filter.value.copy(query = q) }
    fun setCity(c: String) { filter.value = filter.value.copy(city = c.ifBlank { null }) }
    fun setOnlineOnly(v: Boolean) { filter.value = filter.value.copy(onlineOnly = v) }
    fun setSpecialty(sp: String?) { filter.value = filter.value.copy(specialty = sp) }

    val specialties: StateFlow<List<String>> = userRepo.searchProfessionals(ProfessionalFilter())
        .map { l -> l.flatMap { it.profile.workAreas }.map { it.trim() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(12) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryScreen(onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: DirectoryViewModel = viewModel(factory = appViewModelFactory { DirectoryViewModel(container.userRepository) })
    val results by vm.results.collectAsState()
    val filter by vm.filter.collectAsState()
    val specialties by vm.specialties.collectAsState()

    Scaffold(
        topBar = {
            com.hamraheteyf.app.ui.common.ScreenHeader("متخصصان", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(value = filter.query, onValueChange = { vm.setQuery(it) }, label = { Text("جست‌وجو بر اساس نام یا تخصص") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = filter.city ?: "", onValueChange = { vm.setCity(it) }, label = { Text("شهر") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = filter.onlineOnly, onClick = { vm.setOnlineOnly(!filter.onlineOnly) }, label = { Text("پاسخ‌گویی آنلاین") }, modifier = Modifier.padding(end = 8.dp))
                    specialties.forEach { sp ->
                        FilterChip(
                            selected = filter.specialty == sp, onClick = { vm.setSpecialty(if (filter.specialty == sp) null else sp) },
                            label = { Text(sp) }, modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Text("فقط متخصصان تأییدشده در این فهرست دیده می‌شوند.", style = MaterialTheme.typography.labelMedium, color = mutedColor())
            }
            InfoBanner("اطلاعات این فهرست جایگزین مشاورهٔ پزشکی یا درمانی نیست. پیش از هر تصمیم درمانی با متخصص حضوری مشورت کنید.", Modifier.padding(horizontal = 16.dp), dismissKey = "directory_medical_notice")
            Spacer(Modifier.height(8.dp))
            InfoBanner("نشان تأیید فقط به‌معنای بررسی مدارک ارائه‌شده است و تضمین کیفیت یا نتیجهٔ درمان نیست.", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            if (results.isEmpty()) {
                EmptyState("متخصصی با این فیلترها یافت نشد.")
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(results, key = { it.user.id }) { item ->
                        Card(Modifier.fillMaxWidth().clickable { onOpenProfile(item.user.id) }) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.user.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    AuthorBadgeChip(item.badge)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(item.profile.title, style = MaterialTheme.typography.bodyMedium)
                                val meta = buildList {
                                    item.profile.city?.let { add(it) }
                                    if (item.profile.offersOnline) add("آنلاین")
                                    if (item.profile.workAreas.isNotEmpty()) add(item.profile.workAreas.joinToString("، "))
                                }.joinToString(" · ")
                                if (meta.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(meta, style = MaterialTheme.typography.labelMedium, color = mutedColor())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
