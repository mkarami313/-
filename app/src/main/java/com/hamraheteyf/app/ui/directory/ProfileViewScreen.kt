package com.hamraheteyf.app.ui.directory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.*
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PublicProfileState(
    val user: User? = null,
    val professional: ProfessionalProfile? = null,
    val badge: AuthorBadge? = null,
    val isSelf: Boolean = false,
)

class ProfileViewModel(
    private val userId: String,
    private val userRepo: UserRepository,
    private val convRepo: ConversationRepository,
    auth: AuthRepository,
) : ViewModel() {
    val message = MutableStateFlow<String?>(null)
    fun clearMessage() { message.value = null }

    val state: StateFlow<PublicProfileState> = combine(
        userRepo.observeUser(userId), userRepo.observeProfessional(userId), auth.currentUserId,
    ) { user, prof, meId ->
        PublicProfileState(
            user = user,
            professional = prof,
            badge = user?.let { resolveAuthorBadge(it.accountType, prof?.verificationStatus) },
            isSelf = meId == userId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PublicProfileState())

    fun requestConversation(onOpened: (String) -> Unit) = viewModelScope.launch {
        when (val r = convRepo.requestConversation(userId)) {
            is DataResult.Success -> onOpened(r.data)
            is DataResult.Error -> message.value = r.message
        }
    }

    fun block() = viewModelScope.launch {
        when (val r = userRepo.blockUser(userId)) {
            is DataResult.Success -> message.value = "کاربر مسدود شد."
            is DataResult.Error -> message.value = r.message
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileViewScreen(userId: String, onBack: () -> Unit, onOpenConversation: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: ProfileViewModel = viewModel(factory = appViewModelFactory {
        ProfileViewModel(userId, container.userRepository, container.conversationRepository, container.authRepository)
    })
    val state by vm.state.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            com.hamraheteyf.app.ui.common.ScreenHeader("پروفایل", onBack = onBack)
        },
    ) { padding ->
        val user = state.user
        if (user == null) {
            EmptyState("این پروفایل در دسترس نیست.", Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.displayName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                state.badge?.let { AuthorBadgeChip(it) }
            }
            Spacer(Modifier.height(4.dp))
            Text(user.accountType.label, style = MaterialTheme.typography.labelMedium, color = mutedColor())

            if (user.privacy.showCity && !user.city.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("شهر: ${user.city}", style = MaterialTheme.typography.bodyMedium)
            }
            if (user.privacy.showBio && !user.bio.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(user.bio!!, style = MaterialTheme.typography.bodyMedium)
            }

            state.professional?.let { prof ->
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("اطلاعات تخصصی", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("عنوان: ${prof.title}", style = MaterialTheme.typography.bodyMedium)
                if (prof.workAreas.isNotEmpty()) Text("حوزه‌ها: ${prof.workAreas.joinToString("، ")}", style = MaterialTheme.typography.bodyMedium)
                Text(if (prof.offersOnline) "امکان پاسخ‌گویی آنلاین: دارد" else "امکان پاسخ‌گویی آنلاین: ندارد", style = MaterialTheme.typography.bodyMedium)
                Text("وضعیت تأیید: ${prof.verificationStatus.label}", style = MaterialTheme.typography.bodyMedium)
                prof.experience?.let { Text("سابقه: $it", style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(8.dp))
                Text("مدارک حرفه‌ای در پروفایل عمومی نمایش داده نمی‌شوند و فقط توسط مدیران بررسی می‌شوند.", style = MaterialTheme.typography.labelMedium, color = mutedColor())
            }

            if (!state.isSelf) {
                Spacer(Modifier.height(20.dp))
                InfoBanner("گفتگو جایگزین تشخیص، درمان یا مراقبت اورژانسی نیست و هیچ هزینه‌ای ندارد.")
                Spacer(Modifier.height(12.dp))
                BigPrimaryButton(text = "درخواست گفتگو", onClick = { vm.requestConversation(onOpenConversation) })
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.block() }, modifier = Modifier.fillMaxWidth()) { Text("مسدودکردن کاربر") }
                com.hamraheteyf.app.ui.common.ReportLink("USER", userId, "گزارش این کاربر")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
