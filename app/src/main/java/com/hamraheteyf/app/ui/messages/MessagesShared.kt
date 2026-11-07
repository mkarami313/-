package com.hamraheteyf.app.ui.messages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.hamraheteyf.app.data.model.Conversation
import com.hamraheteyf.app.data.model.Message
import com.hamraheteyf.app.data.repository.ConversationRepository
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.ui.common.Jalali
import com.hamraheteyf.app.ui.common.toPersianDigits
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Calendar

/**
 * Messages from the other participant that I haven't seen yet: newer than `readAt[me]`.
 * A server that doesn't track `readAt` at all (empty map) yields 0 rather than flagging the whole history.
 */
fun unreadIn(conversation: Conversation, messages: List<Message>, me: String?): Int {
    if (me == null || conversation.readAt.isEmpty()) return 0
    val seen = conversation.readAt[me] ?: 0L
    return messages.count { it.senderId != me && it.createdAt > seen }
}

/** conversationId → unread count, for every conversation I'm in. */
@OptIn(ExperimentalCoroutinesApi::class)
fun unreadByConversation(repo: ConversationRepository, me: Flow<String?>): Flow<Map<String, Int>> =
    combine(repo.observeConversations(), me) { convs, uid -> convs to uid }.flatMapLatest { (convs, uid) ->
        if (convs.isEmpty() || uid == null) flowOf(emptyMap())
        else combine(convs.map { c -> repo.observeMessages(c.id).map { msgs -> c.id to unreadIn(c, msgs, uid) } }) { it.toMap() }
    }

/** Number of conversations with at least one unread message — the badge on the «پیام‌ها» tab. */
@Composable
fun rememberUnreadConversationCount(): State<Int> {
    val c = LocalAppContainer.current
    val flow = remember(c) {
        // Conversations with a blocked user are hidden from the list, so their unread messages must not light a
        // badge the user can never clear.
        combine(
            unreadByConversation(c.conversationRepository, c.authRepository.currentUserId),
            c.conversationRepository.observeConversations(),
            c.userRepository.observeCurrentUser(),
        ) { m, convs, me ->
            val blocked = me?.blockedUserIds.orEmpty()
            val hidden = if (blocked.isEmpty()) emptySet()
            else convs.filter { cv -> cv.participantIds.any { it != me?.id && it in blocked } }.map { it.id }.toSet()
            m.count { (id, n) -> n > 0 && id !in hidden }
        }
    }
    return flow.collectAsState(0)
}

/** «امروز» / «دیروز» / «شنبه ۱۰ آبان ۱۴۰۵» for a day separator. */
internal fun dayLabel(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    fun key(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.let { String.format(java.util.Locale.US, "%04d-%02d-%02d", it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }
    val k = key(epochMillis)
    return when (k) {
        key(now) -> "امروز"
        key(now - 86_400_000L) -> "دیروز"
        else -> Jalali.displayLong(k)
    }
}

internal fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

/** «۱۴:۰۵» clock time for a bubble. */
internal fun clockFa(epochMillis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return String.format(java.util.Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)).toPersianDigits()
}
