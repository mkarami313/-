package com.hamraheteyf.app.ui.common

import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.fake.DELETED_USER_ID

/** A UI-ready view of a content author (name + badge), respecting anonymity and deletion. */
data class AuthorView(
    val userId: String,
    val name: String,
    val badge: AuthorBadge?,
)

/**
 * Resolves the public author view for a piece of content.
 * - anonymous content never reveals the author
 * - content by a deleted/unknown user shows a neutral placeholder with no badge
 */
fun resolveAuthor(
    users: List<User>,
    professionals: List<ProfessionalProfile>,
    authorId: String,
    isAnonymous: Boolean = false,
): AuthorView {
    if (isAnonymous) return AuthorView(authorId, "کاربر ناشناس", null)
    if (authorId == DELETED_USER_ID) return AuthorView(authorId, "کاربر حذف‌شده", null)
    val user = users.find { it.id == authorId }
        ?: return AuthorView(authorId, "کاربر حذف‌شده", null)
    val profile = professionals.find { it.userId == authorId }
    val badge = resolveAuthorBadge(user.accountType, profile?.verificationStatus)
    return AuthorView(authorId, user.displayName, badge)
}
