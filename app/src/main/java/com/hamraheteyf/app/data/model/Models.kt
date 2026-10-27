package com.hamraheteyf.app.data.model

/**
 * Domain models for «همراهِ طیف».
 *
 * Conventions:
 *  - Every entity has a stable String [id], and [createdAt]/[updatedAt] epoch-millis timestamps.
 *  - "Access rule" notes on each model state who may read/write it; these are enforced
 *    in the repository layer (demo) and in backend/firestore.rules (real backend).
 *  - Sensitive data (child info, professional credentials, private messages) lives in
 *    dedicated models that are never embedded in public content.
 */

/** Coarse age bucket — we deliberately avoid exact birth dates for children. */
enum class AgeRange(val label: String) {
    UNDER_3("زیر ۳ سال"),
    AGE_3_6("۳ تا ۶ سال"),
    AGE_7_12("۷ تا ۱۲ سال"),
    AGE_13_18("۱۳ تا ۱۸ سال"),
    OVER_18("بالای ۱۸ سال"),
    UNSPECIFIED("نامشخص");
}

/** Per-user privacy choices. Defaults are conservative (hide by default). */
data class PrivacySettings(
    val showCity: Boolean = false,
    val showBio: Boolean = true,
    val allowMessageRequests: Boolean = true,
)

/** Per-user notification category toggles. */
data class NotificationSettings(
    val questionAnswered: Boolean = true,
    val newMessage: Boolean = true,
    val groupJoinRequest: Boolean = true,
    val groupReply: Boolean = true,
    val verificationResult: Boolean = true,
    val safetyNotice: Boolean = true,
)

/**
 * A registered account.
 * Access rule: public profile fields are readable by any signed-in user (subject to
 * [privacy]). [email], [notificationSettings] and [blockedUserIds] are private to the
 * owner (and admins for abuse handling, logged as an [AdminAction]).
 */
data class User(
    val id: String,
    val displayName: String,
    val accountType: AccountType,
    val email: String? = null,            // private
    val photoUrl: String? = null,         // optional
    val city: String? = null,             // optional; shown only if privacy.showCity
    val bio: String? = null,              // short intro
    val status: AccountStatus = AccountStatus.ACTIVE,
    val isAdmin: Boolean = false,         // demo-only flag; real backend uses custom claims
    val privacy: PrivacySettings = PrivacySettings(),
    val notificationSettings: NotificationSettings = NotificationSettings(),
    val blockedUserIds: Set<String> = emptySet(),
    val joinedAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * Public-facing professional details.
 * Access rule: readable by any signed-in user. Credentials are NOT stored here —
 * they live in [VerificationRequest], which is admin-only.
 * [verificationStatus] can only be changed by an admin.
 */
data class ProfessionalProfile(
    val id: String,
    val userId: String,
    val title: String,                    // عنوان تخصص
    val workAreas: List<String> = emptyList(),
    val city: String? = null,
    val offersOnline: Boolean = false,
    val experience: String? = null,       // optional free text
    val verificationStatus: VerificationStatus = VerificationStatus.NONE,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * Private verification dossier reviewed by admins.
 * Access rule: admin-only (never exposed in public profile or search).
 * [documentRefs] are storage paths; deleting the request must delete the documents.
 */
data class VerificationRequest(
    val id: String,
    val userId: String,
    val professionalTitle: String,
    val licenseNumber: String? = null,    // private
    val credentialNote: String? = null,   // private
    val documentRefs: List<String> = emptyList(), // admin-only storage references
    val status: VerificationStatus = VerificationStatus.PENDING,
    val reviewerNote: String? = null,
    val reviewedByAdminId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * Minimal, private child information.
 * Access rule: owner-only by default ([isPublic] must be explicitly opted into by the
 * family; even then only [nickname]/[ageRange]/[interests] may be shown). Never embedded
 * in public questions/answers/posts.
 */
data class ChildProfilePrivate(
    val id: String,
    val familyUserId: String,
    val nickname: String? = null,         // optional alias, not a real name
    val ageRange: AgeRange = AgeRange.UNSPECIFIED,
    val interests: List<String> = emptyList(),
    val isPublic: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** Publication state shared by user-generated content. */
enum class ContentStatus(val label: String) {
    PUBLISHED("منتشرشده"),
    HIDDEN("پنهان‌شده"),
    REMOVED("حذف‌شده");
}

/**
 * A public question.
 * Access rule: readable by any signed-in user when [status] == PUBLISHED. Editable/
 * deletable by [authorId]; hideable/removable by admins. When [isAnonymous], the author
 * identity is hidden from the public UI but retained for abuse handling.
 */
data class Question(
    val id: String,
    val authorId: String,
    val isAnonymous: Boolean = false,
    val category: QuestionCategory,
    val title: String,
    val body: String,
    val status: ContentStatus = ContentStatus.PUBLISHED,
    val commentsClosed: Boolean = false,
    val answerCount: Int = 0,
    val helpfulCount: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * An answer to a [Question].
 * Access rule: as for [Question]. [helpfulByUserIds] records who marked it helpful.
 */
data class Answer(
    val id: String,
    val questionId: String,
    val authorId: String,
    val body: String,
    val status: ContentStatus = ContentStatus.PUBLISHED,
    val helpfulByUserIds: Set<String> = emptySet(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val helpfulCount: Int get() = helpfulByUserIds.size
}

/**
 * A group.
 * Access rule: PUBLIC groups are discoverable by all signed-in users. PRIVATE groups and
 * their posts are visible only to ACTIVE members. Managed by OWNER/MODERATOR memberships.
 */
data class Group(
    val id: String,
    val ownerId: String,
    val title: String,
    val description: String,
    val category: String? = null,
    val city: String? = null,
    val visibility: GroupVisibility = GroupVisibility.PUBLIC,
    val rules: String? = null,
    val memberCount: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class GroupMembership(
    val id: String,
    val groupId: String,
    val userId: String,
    val role: MembershipRole = MembershipRole.MEMBER,
    val status: MembershipStatus = MembershipStatus.ACTIVE,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** A post inside a group. Access rule follows the group's visibility/membership. */
data class GroupPost(
    val id: String,
    val groupId: String,
    val authorId: String,
    val body: String,
    val status: ContentStatus = ContentStatus.PUBLISHED,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * A 1:1 private conversation.
 * Access rule: only the two [participantIds] can read it. Messaging only starts after the
 * recipient ACCEPTs the request. No attachments in v1.
 */
data class Conversation(
    val id: String,
    val participantIds: List<String>,
    val initiatorId: String,
    val status: ConversationStatus = ConversationStatus.REQUESTED,
    val mutedByUserIds: Set<String> = emptySet(),
    val endedByUserId: String? = null,
    val lastMessagePreview: String? = null, // generic preview; never shown in push payload
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** A text message. Access rule follows the parent [Conversation]. */
data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val createdAt: Long = 0L,
)

/**
 * An educational guide/resource.
 * Access rule: readable by all when [status] == PUBLISHED. Authored/edited by admins in v1.
 */
data class GuideArticle(
    val id: String,
    val title: String,
    val body: String,
    val category: String,
    val authorName: String,
    val authorId: String? = null,
    val authorIsVerifiedProfessional: Boolean = false,
    val status: ContentStatus = ContentStatus.PUBLISHED,
    val sources: List<String> = emptyList(),
    val savedByUserIds: Set<String> = emptySet(),
    val lastReviewedAt: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * A user-filed report.
 * Access rule: creatable by any signed-in user; readable/resolvable by admins only.
 */
data class Report(
    val id: String,
    val reporterId: String,
    val contentType: ContentType,
    val contentId: String,
    val reason: ReportReason,
    val note: String? = null,
    val status: ReportStatus = ReportStatus.OPEN,
    val reviewedByAdminId: String? = null,
    val resolutionNote: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * A notification.
 * Access rule: readable only by [userId]. [body] is intentionally generic (e.g. "پیام جدید
 * دارید") so sensitive content never appears in a device push payload.
 */
data class Notification(
    val id: String,
    val userId: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val relatedId: String? = null,
    val read: Boolean = false,
    val createdAt: Long = 0L,
)

/**
 * An audited administrative action.
 * Access rule: admin-only. Every sensitive admin operation (including viewing a credential)
 * must append an [AdminAction] with a [reason].
 */
data class AdminAction(
    val id: String,
    val adminId: String,
    val actionType: AdminActionType,
    val targetType: ContentType,
    val targetId: String,
    val reason: String,
    val createdAt: Long = 0L,
)
