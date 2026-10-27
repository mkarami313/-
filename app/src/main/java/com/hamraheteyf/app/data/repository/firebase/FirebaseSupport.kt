package com.hamraheteyf.app.data.repository.firebase

import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.Notification
import com.hamraheteyf.app.data.model.NotificationType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Firestore collection names. Must match backend/firestore.rules. */
object Fs {
    const val USERS = "users"
    const val PROFESSIONALS = "professionals"
    const val VERIFICATIONS = "verifications"
    const val CHILDREN = "children"
    const val QUESTIONS = "questions"
    const val ANSWERS = "answers"
    const val GROUPS = "groups"
    const val MEMBERSHIPS = "memberships"
    const val POSTS = "posts"
    const val CONVERSATIONS = "conversations"
    const val MESSAGES = "messages"
    const val GUIDES = "guides"
    const val REPORTS = "reports"
    const val NOTIFICATIONS = "notifications"
    const val ADMIN_ACTIONS = "adminActions"
}

internal const val FB_NOT_SIGNED_IN = "ابتدا وارد حساب خود شوید."
internal fun fbError(message: String): DataResult<Nothing> = DataResult.Error(message)

internal fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
internal fun firebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
internal fun currentUid(): String? = firebaseAuth().currentUser?.uid

/**
 * Writes a notification document for [userId]. Best-effort (failures are swallowed) so a
 * notification write can never fail the primary action. In production this ideally moves to a
 * Cloud Function trigger; done client-side here for a backend without Functions.
 */
internal suspend fun pushNotification(
    userId: String,
    type: NotificationType,
    title: String,
    body: String,
    relatedId: String? = null,
) {
    runCatching {
        val n = Notification(
            id = "", userId = userId, type = type, title = title, body = body,
            relatedId = relatedId, read = false, createdAt = System.currentTimeMillis(),
        )
        firestore().collection(Fs.NOTIFICATIONS).add(n.toMap()).await()
    }
}

/** Emits the signed-in user's uid (or null) and re-emits whenever auth state changes. */
internal fun authUidFlow(): Flow<String?> = callbackFlow {
    val a = firebaseAuth()
    val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
    a.addAuthStateListener(listener)
    awaitClose { a.removeAuthStateListener(listener) }
}

/** Parses an enum by name, falling back to [default] for unknown/missing values. */
internal inline fun <reified E : Enum<E>> parseEnum(value: String?, default: E): E =
    value?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

internal fun DocumentSnapshot.strList(key: String): List<String> =
    (get(key) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

/** A live list of mapped documents from a query. Emits [] on error so the UI degrades gracefully. */
internal fun <T> Query.asListFlow(mapper: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            trySend(emptyList())
            return@addSnapshotListener
        }
        val items = snapshot?.documents.orEmpty().mapNotNull { runCatching { mapper(it) }.getOrNull() }
        trySend(items)
    }
    awaitClose { registration.remove() }
}

/** A live single mapped document (or null if missing). */
internal fun <T> DocumentReference.asDocFlow(mapper: (DocumentSnapshot) -> T?): Flow<T?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            trySend(null)
            return@addSnapshotListener
        }
        val value = snapshot?.takeIf { it.exists() }?.let { runCatching { mapper(it) }.getOrNull() }
        trySend(value)
    }
    awaitClose { registration.remove() }
}
