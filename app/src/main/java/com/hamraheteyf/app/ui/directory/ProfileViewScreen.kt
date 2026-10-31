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
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.*
import com.hamraheteyf.app.ui.modules.Rec
import com.hamraheteyf.app.ui.modules.Reminders
import com.hamraheteyf.app.ui.modules.rememberRecClient
import com.hamraheteyf.app.ui.modules.rememberRecs
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PublicProfileState(
    val user: User? = null,
    val professional: ProfessionalProfile? = null,
    val badge: AuthorBadge? = null,
    val isSelf: Boolean = false,
    val blocked: Boolean = false,
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
        userRepo.observeUser(userId), userRepo.observeProfessional(userId), auth.currentUserId, userRepo.observeCurrentUser(),
    ) { user, prof, meId, me ->
        PublicProfileState(
            user = user,
            professional = prof,
            badge = user?.let { resolveAuthorBadge(it.accountType, prof?.verificationStatus) },
            isSelf = meId == userId,
            blocked = me?.blockedUserIds?.contains(userId) == true,
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

    fun unblock() = viewModelScope.launch {
        when (val r = userRepo.unblockUser(userId)) {
            is DataResult.Success -> message.value = "مسدودیت کاربر برداشته شد."
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
    var confirmBlock by remember { mutableStateOf(false) }
    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("مسدودکردن ${state.user?.displayName.orEmpty()}؟") },
            text = { Text("این کاربر دیگر نمی‌تواند به شما پیام بدهد یا درخواست گفتگو بفرستد و شما هم او را در فهرست‌ها نمی‌بینید. هر زمان می‌توانید از همین صفحه یا از تنظیمات مسدودیت را بردارید.") },
            confirmButton = { TextButton(onClick = { confirmBlock = false; vm.block() }) { Text("مسدود شود", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("انصراف") } },
        )
    }

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
                if (state.isSelf) Text("وضعیت تأیید: ${prof.verificationStatus.label}", style = MaterialTheme.typography.bodyMedium)
                prof.experience?.let { Text("سابقه: $it", style = MaterialTheme.typography.bodyMedium) }
                prof.fee?.takeIf { it.isNotBlank() }?.let { Text("هزینه: $it", style = MaterialTheme.typography.bodyMedium) }
                prof.hours?.takeIf { it.isNotBlank() }?.let { Text("ساعت حضور: $it", style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(8.dp))
                Text("مدارک حرفه‌ای در پروفایل عمومی نمایش داده نمی‌شوند و فقط توسط مدیران بررسی می‌شوند.", style = MaterialTheme.typography.labelMedium, color = mutedColor())
            }

            if (!state.isSelf && !state.blocked && state.professional?.verificationStatus == VerificationStatus.VERIFIED) {
                OpenSlots(userId, snackbar)
            }

            if (!state.isSelf) {
                Spacer(Modifier.height(20.dp))
                InfoBanner("گفتگو جایگزین تشخیص، درمان یا مراقبت اورژانسی نیست و هیچ هزینه‌ای ندارد.")
                Spacer(Modifier.height(12.dp))
                if (state.blocked) {
                    Text("این کاربر را مسدود کرده‌اید.", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.unblock() }, modifier = Modifier.fillMaxWidth()) { Text("برداشتن مسدودیت") }
                } else {
                    BigPrimaryButton(text = "درخواست گفتگو", onClick = { vm.requestConversation(onOpenConversation) })
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { confirmBlock = true }, modifier = Modifier.fillMaxWidth()) { Text("مسدودکردن کاربر") }
                }
                com.hamraheteyf.app.ui.common.ReportLink("USER", userId, "گزارش این کاربر")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}


@Composable
private fun OpenSlots(ownerId: String, snackbar: SnackbarHostState) {
    val client = rememberRecClient() ?: return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<Rec?>(null) }
    val st = rememberRecs(client, "slot", tick)
    val now = System.currentTimeMillis()
    val slots = st.items.orEmpty()
        .filter { it.ownerId == ownerId && !it.mine && it.status == "OPEN" && (Reminders.slotAt(it)?.let { t -> t > now } ?: true) }
        .sortedBy { Reminders.slotAt(it) ?: Long.MAX_VALUE }
        .take(6)
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text("نوبت‌های آزاد", style = MaterialTheme.typography.titleMedium)
    Text("رزرو رایگان است و فقط برای هماهنگی زمان ملاقات است.", style = MaterialTheme.typography.labelMedium, color = mutedColor())
    Spacer(Modifier.height(6.dp))
    when {
        st.items == null && st.error == null -> Text("در حال بارگذاری…", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
        st.items == null -> Text(st.error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        slots.isEmpty() -> Text("فعلاً نوبت آزادی ثبت نشده است.", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
        else -> slots.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r["when"], style = MaterialTheme.typography.bodyMedium)
                    val meta = listOf(r["place"], r["duration"].takeIf { it.isNotBlank() }?.let { "${it.toPersianDigits()} دقیقه" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = mutedColor())
                }
                Button(onClick = { confirm = r }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) { Text("رزرو") }
            }
        }
    }
    confirm?.let { r ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("رزرو نوبت") },
            text = { Text("نوبت «${r["when"]}» با ${r.ownerName} رزرو شود؟ به متخصص اطلاع داده می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        when (val res = client.simple("slotBook", r.id)) {
                            is ApiResult.Fail -> snackbar.showSnackbar(res.message)
                            is ApiResult.Ok -> {
                                Reminders.slotAt(r)?.let { Reminders.scheduleAppointment(ctx, "slot_${r.id}", "نوبت با ${r.ownerName}: ${r["when"]}", it) }
                                snackbar.showSnackbar("نوبت رزرو شد. در «نوبت‌دهی › رزروهای من» می‌توانید آن را ببینید.")
                            }
                        }
                        tick++
                    }
                }) { Text("رزرو") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("انصراف") } },
        )
    }
}
