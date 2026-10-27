package com.hamraheteyf.app.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ConversationState(
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val currentUserId: String? = null,
    val otherUserId: String? = null,
    val otherName: String = "",
    val otherBadge: AuthorBadge? = null,
    val otherVerifiedProfessional: Boolean = false,
    val isInitiator: Boolean = false,
    val isMuted: Boolean = false,
)

class ConversationViewModel(
    private val conversationId: String,
    private val convRepo: ConversationRepository,
    userRepo: UserRepository,
    private val reportRepo: ReportRepository,
) : ViewModel() {

    val message = MutableStateFlow<String?>(null)
    fun clearMessage() { message.value = null }

    val state: StateFlow<ConversationState> = combine(
        convRepo.observeConversation(conversationId),
        convRepo.observeMessages(conversationId),
        userRepo.observeUsers(),
        userRepo.observeAllProfessionals(),
        userRepo.observeCurrentUser().map { it?.id },
    ) { conv, messages, users, professionals, meId ->
        if (conv == null) return@combine ConversationState(currentUserId = meId)
        val otherId = conv.participantIds.firstOrNull { it != meId }
        val other = users.find { it.id == otherId }
        val prof = professionals.find { it.userId == otherId }
        ConversationState(
            conversation = conv,
            messages = messages,
            currentUserId = meId,
            otherUserId = otherId,
            otherName = other?.displayName ?: "کاربر",
            otherBadge = other?.let { resolveAuthorBadge(it.accountType, prof?.verificationStatus) },
            otherVerifiedProfessional = prof?.verificationStatus == VerificationStatus.VERIFIED,
            isInitiator = conv.initiatorId == meId,
            isMuted = meId != null && meId in conv.mutedByUserIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConversationState())

    private fun run(success: String? = null, block: suspend () -> DataResult<*>) = viewModelScope.launch {
        when (val r = block()) {
            is DataResult.Success -> success?.let { message.value = it }
            is DataResult.Error -> message.value = r.message
        }
    }

    fun accept() = run { convRepo.acceptConversation(conversationId) }
    fun decline() = run("گفتگو رد شد.") { convRepo.declineConversation(conversationId) }
    fun end() = run("گفتگو پایان یافت.") { convRepo.endConversation(conversationId) }
    fun toggleMute() = run { convRepo.toggleMute(conversationId) }
    fun send(body: String) = run { convRepo.sendMessage(conversationId, body) }
    fun report(reason: ReportReason, note: String) {
        val otherId = state.value.otherUserId ?: return
        run("گزارش ثبت شد.") { reportRepo.createReport(ContentType.USER, otherId, reason, note) }
    }
}
