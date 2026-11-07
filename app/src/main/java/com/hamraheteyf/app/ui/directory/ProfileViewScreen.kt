@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import com.hamraheteyf.app.community.PublicProfile
import com.hamraheteyf.app.community.rememberCommunity
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.Expert
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/** What the screen shows, merged from the local snapshot (when the user is in it) and the server's public profile. */
private class ProfileView(
    val name: String, val photoUrl: String?, val badge: String, val expert: Boolean, val accountLabel: String?,
    val city: String?, val bio: String?, val joinedAt: Long?,
)

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
    var requesting by remember { mutableStateOf(false) }

    // Forum authors are not in the local snapshot: the server's public profile fills the gap (and adds stats).
    val client = rememberCommunity()
    var remoteTick by remember { mutableIntStateOf(0) }
    var remote by remember { mutableStateOf<PublicProfile?>(null) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    var remoteDone by remember { mutableStateOf(false) }
    LaunchedEffect(userId, client, remoteTick, state.blocked) {
        if (client == null) { remoteDone = true; return@LaunchedEffect }
        when (val r = client.profile(userId)) {
            is ApiResult.Ok -> { remote = r.data; remoteError = null }
            is ApiResult.Fail -> remoteError = r.message
        }
        remoteDone = true
    }

    val local = state.user
    val ru = remote?.user
    val view = when {
        local != null -> ProfileView(
            name = local.displayName, photoUrl = local.photoUrl ?: ru?.photoUrl,
            badge = state.badge?.label ?: ru?.badge.orEmpty(),
            expert = state.professional?.verificationStatus == VerificationStatus.VERIFIED || ru?.expert == true,
            accountLabel = local.accountType.label,
            city = local.city?.takeIf { local.privacy.showCity } ?: ru?.city,
            bio = local.bio?.takeIf { local.privacy.showBio } ?: ru?.bio,
            joinedAt = local.joinedAt.takeIf { it > 0 } ?: ru?.joinedAt,
        )
        ru != null -> ProfileView(
            name = ru.name, photoUrl = ru.photoUrl, badge = ru.badge, expert = ru.expert,
            accountLabel = ru.accountType?.let { t -> runCatching { AccountType.valueOf(t).label }.getOrNull() },
            city = ru.city, bio = ru.bio, joinedAt = ru.joinedAt,
        )
        // The server hides people I blocked («کاربر پیدا نشد»), so a forum author I blocked has no profile to
        // load — still show a minimal page, or the «برداشتنِ مسدودیت» button would be unreachable here.
        state.blocked && remoteDone -> ProfileView(
            name = "کاربرِ مسدودشده", photoUrl = null, badge = "", expert = false, accountLabel = null,
            city = null, bio = null, joinedAt = null,
        )
        else -> null
    }
    val blocked = state.blocked || (local == null && remote?.blockedByMe == true)
    val canMessage = remote?.canMessage ?: (local != null)
    val prof = state.professional
    val verifiedPro = prof?.verificationStatus == VerificationStatus.VERIFIED || (prof == null && ru?.expert == true)

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("مسدودکردن ${view?.name.orEmpty()}؟") },
            text = { Text("این کاربر دیگر نمی‌تواند به شما پیام بدهد یا درخواست گفتگو بفرستد و شما هم او را در فهرست‌ها نمی‌بینید. هر زمان می‌توانید از همین صفحه یا از تنظیمات مسدودیت را بردارید.") },
            confirmButton = { TextButton(onClick = { confirmBlock = false; vm.block() }) { Text("مسدود شود", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("انصراف") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar("پروفایل", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (view == null) {
            when {
                !remoteDone -> LoadingView(Modifier.padding(padding))
                remoteError != null -> ErrorView(remoteError.orEmpty(), { remoteDone = false; remoteTick++ }, Modifier.padding(padding))
                else -> EmptyView(Icons.Outlined.PersonOff, "این پروفایل در دسترس نیست.", modifier = Modifier.padding(padding))
            }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
            Spacer(Modifier.height(Dimens.s))
            // ---- header
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val photo = prof?.photoId?.let { id -> client?.fileUrl(id) }
                    if (photo != null) RemoteImage(photo, Modifier.size(72.dp).clip(CircleShape), displaySize = 72.dp) else UserAvatar(view.name, view.photoUrl, 72.dp)
                    Spacer(Modifier.width(Dimens.m))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(view.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (view.expert) { Spacer(Modifier.width(4.dp)); Icon(VerifiedIcon, "متخصصِ تأییدشده", tint = Expert, modifier = Modifier.size(20.dp)) }
                        }
                        if (prof != null && prof.title.isNotBlank()) Text(prof.title, style = MaterialTheme.typography.bodyMedium)
                        else view.accountLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted()) }
                        if (view.badge.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Pill(view.badge, if (view.expert) Expert else MaterialTheme.colorScheme.primary)
                        }
                        val meta = listOfNotNull(
                            view.city?.takeIf { it.isNotBlank() },
                            view.joinedAt?.takeIf { it > 0 }?.let { "عضو از ${joinedFa(it)}" },
                        ).joinToString(" · ")
                        if (meta.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(meta, style = MaterialTheme.typography.labelMedium, color = muted()) }
                    }
                }
                remote?.stats?.let { st ->
                    Spacer(Modifier.height(Dimens.m))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(top = Dimens.s)) {
                        Stat("موضوع", st.topics, Modifier.weight(1f)); Stat("پاسخ", st.replies, Modifier.weight(1f)); Stat("پسندِ دریافتی", st.likes, Modifier.weight(1f))
                    }
                }
                if (!state.isSelf) {
                    Spacer(Modifier.height(Dimens.m))
                    if (blocked) {
                        Text("این کاربر را مسدود کرده‌اید.", style = MaterialTheme.typography.bodyMedium, color = muted())
                        Spacer(Modifier.height(Dimens.s))
                        OutlinedButton(onClick = { vm.unblock() }, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touch)) { Text("برداشتنِ مسدودیت") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
                            if (canMessage) Button(onClick = {
                                requesting = true
                                vm.requestConversation(onOpenConversation).invokeOnCompletion { requesting = false }
                            }, enabled = !requesting, modifier = Modifier.weight(1f).heightIn(min = Dimens.touch)) {
                                Icon(Icons.Outlined.Mail, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("پیام")
                            }
                            OutlinedButton(onClick = { confirmBlock = true }, modifier = Modifier.then(if (canMessage) Modifier else Modifier.weight(1f)).heightIn(min = Dimens.touch)) { Text("مسدود کردن") }
                        }
                        if (!canMessage) Text("این کاربر درخواستِ گفتگو نمی‌پذیرد.", style = MaterialTheme.typography.labelMedium, color = muted(), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            val bio = view.bio
            if (!bio.isNullOrBlank()) {
                SectionTitle("درباره")
                AppCard { Text(bio, style = MaterialTheme.typography.bodyMedium) }
            }

            prof?.let { p ->
                SectionTitle("اطلاعاتِ تخصصی")
                AppCard {
                    if (p.workAreas.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.workAreas.forEach { Pill(it) }
                        }
                        Spacer(Modifier.height(Dimens.s))
                    }
                    InfoLine(Icons.Outlined.Videocam, if (p.offersOnline) "پاسخ‌گوییِ آنلاین دارد" else "پاسخ‌گوییِ آنلاین ندارد")
                    p.city?.takeIf { it.isNotBlank() }?.let { InfoLine(Icons.Outlined.Place, it) }
                    p.experience?.takeIf { it.isNotBlank() }?.let { InfoLine(Icons.Outlined.WorkHistory, "سابقه: $it") }
                    p.fee?.takeIf { it.isNotBlank() }?.let { InfoLine(Icons.Outlined.Payments, "هزینه: $it") }
                    p.hours?.takeIf { it.isNotBlank() }?.let { InfoLine(Icons.Outlined.Schedule, "ساعتِ حضور: $it") }
                    if (state.isSelf) InfoLine(Icons.Outlined.Verified, "وضعیتِ تأیید: ${p.verificationStatus.label}")
                    Spacer(Modifier.height(4.dp))
                    Text("مدارکِ حرفه‌ای در پروفایلِ عمومی نمایش داده نمی‌شوند و فقط مدیران آن‌ها را بررسی می‌کنند.", style = MaterialTheme.typography.labelSmall, color = muted())
                }
            }

            if (!state.isSelf && !blocked && prof?.verificationStatus == VerificationStatus.VERIFIED) {
                Spacer(Modifier.height(Dimens.m))
                AppCard { OpenSlots(userId, snackbar) }
            }

            if (verifiedPro) {
                Spacer(Modifier.height(Dimens.m))
                AppCard { ReviewsSection("pro", userId, canReview = !state.isSelf && !blocked) }
            }

            if (!state.isSelf) {
                Spacer(Modifier.height(Dimens.m))
                Text("گفتگو جایگزینِ تشخیص، درمان یا مراقبتِ اورژانسی نیست و هیچ هزینه‌ای ندارد.", style = MaterialTheme.typography.labelSmall, color = muted())
                ReportLink("USER", userId, "گزارشِ این کاربر")
            }
            Spacer(Modifier.height(Dimens.xl))
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toPersianDigits(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = muted())
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = muted(), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Dimens.s))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun joinedFa(ms: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val j = Jalali.toJalali(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    return "${Jalali.MONTHS[j.m - 1]} ${j.y.toPersianDigits()}"
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
