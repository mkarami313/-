package com.hamraheteyf.app.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.*
import com.hamraheteyf.app.data.repository.server.ServerConversationRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ConversationState(
    val loaded: Boolean = false,
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val currentUserId: String? = null,
    val otherUserId: String? = null,
    val otherName: String = "",
    val otherPhoto: String? = null,
    val otherBadge: AuthorBadge? = null,
    val otherVerifiedProfessional: Boolean = false,
    val isInitiator: Boolean = false,
    val isMuted: Boolean = false,
    val otherBlocked: Boolean = false,
)

class ConversationViewModel(
    private val conversationId: String,
    private val convRepo: ConversationRepository,
    private val userRepo: UserRepository,
    private val reportRepo: ReportRepository,
) : ViewModel() {

    val message = MutableStateFlow<String?>(null)
    fun clearMessage() { message.value = null }

    /** True while a send is in flight (the composer keeps its text until the server confirms). */
    val sending = MutableStateFlow(false)
    val busy = MutableStateFlow(false)

    val state: StateFlow<ConversationState> = combine(
        convRepo.observeConversation(conversationId),
        convRepo.observeMessages(conversationId),
        userRepo.observeUsers(),
        userRepo.observeAllProfessionals(),
        userRepo.observeCurrentUser(),
    ) { conv, messages, users, professionals, me ->
        val meId = me?.id
        if (conv == null) return@combine ConversationState(loaded = true, currentUserId = meId)
        val otherId = conv.participantIds.firstOrNull { it != meId }
        val other = users.find { it.id == otherId }
        val prof = professionals.find { it.userId == otherId }
        ConversationState(
            loaded = true,
            conversation = conv,
            messages = messages,
            currentUserId = meId,
            otherUserId = otherId,
            otherName = other?.displayName ?: "کاربر",
            otherPhoto = other?.photoUrl,
            otherBadge = other?.let { resolveAuthorBadge(it.accountType, prof?.verificationStatus) },
            otherVerifiedProfessional = prof?.verificationStatus == VerificationStatus.VERIFIED,
            isInitiator = conv.initiatorId == meId,
            isMuted = meId != null && meId in conv.mutedByUserIds,
            otherBlocked = otherId != null && me != null && otherId in me.blockedUserIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConversationState())

    private fun run(success: String? = null, onOk: () -> Unit = {}, block: suspend () -> DataResult<*>) = viewModelScope.launch {
        busy.value = true
        when (val r = block()) {
            is DataResult.Success -> { success?.let { message.value = it }; onOk() }
            is DataResult.Error -> message.value = r.message
        }
        busy.value = false
    }

    fun accept() = run { convRepo.acceptConversation(conversationId) }
    fun decline() = run("گفتگو رد شد.") { convRepo.declineConversation(conversationId) }
    fun end() = run("گفتگو پایان یافت.") { convRepo.endConversation(conversationId) }
    fun toggleMute() = run { convRepo.toggleMute(conversationId) }

    /** Sends [body]; [onSent] runs only after the server accepted it, so a failed send keeps the draft. */
    fun send(body: String, onSent: () -> Unit) {
        if (sending.value || body.isBlank()) return
        viewModelScope.launch {
            sending.value = true
            when (val r = convRepo.sendMessage(conversationId, body.trim())) {
                is DataResult.Success -> onSent()
                is DataResult.Error -> message.value = r.message.ifBlank { "پیام ارسال نشد؛ دوباره تلاش کنید." }
            }
            sending.value = false
        }
    }

    /** Ended conversation → ask the other person again; the server reopens it as a fresh request. */
    fun restart(onOpened: (String) -> Unit) {
        val otherId = state.value.otherUserId ?: return
        viewModelScope.launch {
            busy.value = true
            when (val r = convRepo.requestConversation(otherId)) {
                is DataResult.Success -> { message.value = "درخواستِ گفتگو دوباره فرستاده شد."; onOpened(r.data.ifBlank { conversationId }) }
                is DataResult.Error -> message.value = r.message
            }
            busy.value = false
        }
    }

    private var lastMarked: String? = null

    /** Tells the server I've seen everything up to the newest message (once per newest message). */
    fun markRead() {
        val st = state.value
        val conv = st.conversation ?: return
        val me = st.currentUserId ?: return
        val newest = st.messages.lastOrNull()
        val marker = newest?.id ?: "open"
        if (marker == lastMarked) return
        val seen = conv.readAt[me] ?: 0L
        if (newest != null && conv.readAt.isNotEmpty() && newest.createdAt <= seen) { lastMarked = marker; return }
        lastMarked = marker
        val repo = convRepo as? ServerConversationRepository ?: return
        // A failed call (offline) must be retried on the next chance, not remembered as done.
        viewModelScope.launch { if (repo.markConversationRead(conversationId) is DataResult.Error && lastMarked == marker) lastMarked = null }
    }

    fun blockOther() {
        val otherId = state.value.otherUserId ?: return
        run("کاربر مسدود شد.") { userRepo.blockUser(otherId) }
    }
    fun unblockOther() {
        val otherId = state.value.otherUserId ?: return
        run("مسدودیت کاربر برداشته شد.") { userRepo.unblockUser(otherId) }
    }
    fun report(reason: ReportReason, note: String) {
        val otherId = state.value.otherUserId ?: return
        run("گزارش ثبت شد.") { reportRepo.createReport(ContentType.USER, otherId, reason, note) }
    }
}
