package com.hamraheteyf.app.data.repository.fake

import kotlinx.serialization.Serializable
import com.hamraheteyf.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * An immutable snapshot of the whole demo database. Every mutation produces a new [Db],
 * so all derived flows recompute automatically — a tiny reactive store with no backend.
 */
@Serializable
data class Db(
    val users: List<User> = emptyList(),
    val professionals: List<ProfessionalProfile> = emptyList(),
    val verifications: List<VerificationRequest> = emptyList(),
    val children: List<ChildProfilePrivate> = emptyList(),
    val questions: List<Question> = emptyList(),
    val answers: List<Answer> = emptyList(),
    val groups: List<Group> = emptyList(),
    val memberships: List<GroupMembership> = emptyList(),
    val posts: List<GroupPost> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val messages: List<Message> = emptyList(),
    val guides: List<GuideArticle> = emptyList(),
    val reports: List<Report> = emptyList(),
    val notifications: List<Notification> = emptyList(),
    val adminActions: List<AdminAction> = emptyList(),
)

/**
 * Holds the single mutable snapshot plus the signed-in user id and (demo-only) credentials.
 * Thread-safe enough for a single-process demo; a real backend replaces this entirely.
 *
 * SECURITY NOTE: storing plain-text passwords here is acceptable ONLY because this is an
 * offline demo store that never leaves the device/test process. The real backend uses
 * Firebase Authentication and never stores passwords in the app.
 */
class FakeStore(seed: Db, seedCredentials: Map<String, String>) {

    val state = MutableStateFlow(seed)
    val currentUserId = MutableStateFlow<String?>(null)

    private val credentials = HashMap(seedCredentials) // email(lowercase) -> password
    private val idCounter = AtomicLong(1000)

    fun now(): Long = System.currentTimeMillis()
    fun newId(prefix: String): String = "${prefix}_${idCounter.incrementAndGet()}"

    @Synchronized
    fun update(block: (Db) -> Db) {
        state.value = block(state.value)
    }

    @Synchronized
    fun setPassword(email: String, password: String) {
        credentials[email.trim().lowercase()] = password
    }

    @Synchronized
    fun checkPassword(email: String, password: String): Boolean =
        credentials[email.trim().lowercase()] == password

    @Synchronized
    fun hasEmail(email: String): Boolean =
        credentials.containsKey(email.trim().lowercase())

    @Synchronized
    fun removePassword(email: String) {
        credentials.remove(email.trim().lowercase())
    }

    companion object {
        /** Shared password for all seeded demo accounts. */
        const val DEMO_PASSWORD = "demo1234"
    }
}
