package com.hamraheteyf.app.push

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A notification tap waiting to be opened. [com.hamraheteyf.app.MainActivity] fills it from the
 * launch intent (cold start and onNewIntent); the signed-in nav host navigates with
 * `Routes.forNotification(type, relatedId)` and then [consume]s it.
 */
object PendingDeepLink {
    const val EXTRA_TYPE = "hamrah.notif.type"
    const val EXTRA_RELATED = "hamrah.notif.relatedId"
    const val EXTRA_NOTIF_ID = "hamrah.notif.id"

    /** [notifId] is the server notification id, so opening it can also mark it read in the inbox. */
    data class Target(val type: String?, val relatedId: String?, val notifId: String? = null)

    private val _target = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> get() = _target

    /** Reads the notification extras from [intent], if any, and strips them so they fire once. */
    fun offer(intent: Intent?) {
        if (intent == null) return
        val type = intent.getStringExtra(EXTRA_TYPE)
        val related = intent.getStringExtra(EXTRA_RELATED)
        val notifId = intent.getStringExtra(EXTRA_NOTIF_ID)
        if (type.isNullOrBlank() && related.isNullOrBlank() && notifId.isNullOrBlank()) return
        intent.removeExtra(EXTRA_TYPE)
        intent.removeExtra(EXTRA_RELATED)
        intent.removeExtra(EXTRA_NOTIF_ID)
        _target.value = Target(type, related, notifId)
    }

    fun consume() { _target.value = null }
}
