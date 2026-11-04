@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hamraheteyf.app.ui.directory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.community.RatingSummary
import com.hamraheteyf.app.community.rememberCommunity
import com.hamraheteyf.app.data.model.VerificationStatus
import com.hamraheteyf.app.data.repository.ProfessionalDirectoryItem
import com.hamraheteyf.app.data.repository.ProfessionalFilter
import com.hamraheteyf.app.data.repository.UserRepository
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.InfoBanner
import com.hamraheteyf.app.ui.common.RatingInline
import com.hamraheteyf.app.ui.common.RemoteImage
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.Expert
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class DirectoryViewModel(private val userRepo: UserRepository) : ViewModel() {
    val filter = MutableStateFlow(ProfessionalFilter())
    /** null until the first result arrives (so the screen can show a loader instead of «nothing found»). */
    val results: StateFlow<List<ProfessionalDirectoryItem>?> = filter
        .flatMapLatest { userRepo.searchProfessionals(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setQuery(q: String) { filter.value = filter.value.copy(query = q) }
    fun setCity(c: String) { filter.value = filter.value.copy(city = c.ifBlank { null }) }
    fun setOnlineOnly(v: Boolean) { filter.value = filter.value.copy(onlineOnly = v) }
    fun setSpecialty(sp: String?) { filter.value = filter.value.copy(specialty = sp) }

    private val everyone = userRepo.searchProfessionals(ProfessionalFilter())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val specialties: StateFlow<List<String>> = everyone
        .map { l -> l.flatMap { it.profile.workAreas }.map { it.trim() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(12) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cities: StateFlow<List<String>> = everyone
        .map { l -> l.mapNotNull { it.profile.city?.trim()?.takeIf { c -> c.isNotEmpty() } }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(12) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun DirectoryScreen(onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: DirectoryViewModel = viewModel(factory = appViewModelFactory { DirectoryViewModel(container.userRepository) })
    val results by vm.results.collectAsState()
    val filter by vm.filter.collectAsState()
    val specialties by vm.specialties.collectAsState()
    val cities by vm.cities.collectAsState()
    val client = rememberCommunity()
    val ids = results.orEmpty().map { it.user.id }
    val ratings by produceState(emptyMap<String, RatingSummary>(), client, ids) {
        if (client != null && ids.isNotEmpty()) (client.ratingSummary("pro", ids) as? ApiResult.Ok)?.let { value = it.data }
    }

    Scaffold(topBar = { AppTopBar("متخصصان", onBack = onBack, subtitle = "فقط متخصصانِ تأییدشده") }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = Dimens.xl)) {
            item("search") {
                OutlinedTextField(
                    filter.query, { vm.setQuery(it) }, Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = Dimens.s),
                    singleLine = true, shape = RoundedCornerShape(50), textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = { Text("نام، تخصص یا حوزهٔ کاری", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (filter.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Close, "پاک کردن") } },
                )
            }
            item("filters") {
                // One chip row: online + cities + specialties (all toggles).
                val chips = buildList {
                    add(Chip("online", "پاسخ‌گویی آنلاین", filter.onlineOnly) { vm.setOnlineOnly(!filter.onlineOnly) })
                    cities.forEach { c -> add(Chip("c:$c", c, filter.city == c, Icons.Outlined.Place) { vm.setCity(if (filter.city == c) "" else c) }) }
                    specialties.forEach { sp -> add(Chip("s:$sp", sp, filter.specialty == sp) { vm.setSpecialty(if (filter.specialty == sp) null else sp) }) }
                }
                androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = Dimens.gutter), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(chips, key = { it.key }) { ch ->
                        FilterChip(
                            selected = ch.on, onClick = ch.onClick, label = { Text(ch.label, style = MaterialTheme.typography.labelLarge) },
                            leadingIcon = ch.icon?.let { ic -> { Icon(ic, null, Modifier.size(16.dp)) } },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White,
                            ),
                        )
                    }
                }
            }
            item("notice") {
                InfoBanner(
                    "این فهرست جایگزینِ مشاورهٔ پزشکی نیست. نشانِ تأیید فقط یعنی مدارک بررسی شده است، نه تضمینِ نتیجهٔ درمان.",
                    Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.s), dismissKey = "directory_medical_notice",
                )
            }
            val list = results
            when {
                list == null -> item("loading") { LoadingView() }
                list.isEmpty() -> item("empty") {
                    EmptyView(Icons.Outlined.PersonSearch, "متخصصی با این فیلترها پیدا نشد", "فیلترها را کم‌تر کنید یا عبارتِ دیگری را جست‌وجو کنید.")
                }
                else -> {
                    item("count") {
                        Text("${list.size.toPersianDigits()} متخصص", style = MaterialTheme.typography.labelMedium, color = muted(),
                            modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 4.dp))
                    }
                    items(list, key = { it.user.id }) { item ->
                        ExpertCard(item, ratings[item.user.id], client?.let { c -> item.profile.photoId?.let { c.fileUrl(it) } }) { onOpenProfile(item.user.id) }
                    }
                }
            }
        }
    }
}

private class Chip(val key: String, val label: String, val on: Boolean, val icon: androidx.compose.ui.graphics.vector.ImageVector? = null, val onClick: () -> Unit)

@Composable
private fun ExpertCard(item: ProfessionalDirectoryItem, rating: RatingSummary?, photoUrl: String?, onClick: () -> Unit) {
    AppCard(Modifier.padding(horizontal = Dimens.gutter, vertical = 4.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            if (photoUrl != null) RemoteImage(photoUrl, Modifier.size(56.dp).clip(CircleShape))
            else UserAvatar(item.user.displayName, item.user.photoUrl, 56.dp)
            Spacer(Modifier.width(Dimens.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.user.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (item.profile.verificationStatus == VerificationStatus.VERIFIED) {
                        Spacer(Modifier.width(4.dp)); Icon(VerifiedIcon, "متخصصِ تأییدشده", tint = Expert, modifier = Modifier.size(16.dp))
                    }
                }
                if (item.profile.title.isNotBlank()) Text(item.profile.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
                    RatingInline(rating)
                    item.profile.city?.takeIf { it.isNotBlank() }?.let { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Place, null, tint = muted(), modifier = Modifier.size(14.dp))
                            Text(c, style = MaterialTheme.typography.labelMedium, color = muted(), maxLines = 1)
                        }
                    }
                    if (item.profile.offersOnline) Pill("آنلاین", MaterialTheme.colorScheme.primary, icon = Icons.Outlined.Videocam)
                }
                if (item.profile.workAreas.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(item.profile.workAreas.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = muted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
