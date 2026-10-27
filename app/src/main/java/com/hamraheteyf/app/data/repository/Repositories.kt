package com.hamraheteyf.app.data.repository

import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository contracts. Each has a demo (in-memory) implementation and can be backed by a
 * real service (e.g. Firebase) without touching the UI/ViewModel layer.
 */

/** Filter for the professional directory. */
data class ProfessionalFilter(
    val query: String = "",
    val city: String? = null,
    val specialty: String? = null,
    val onlineOnly: Boolean = false,
    val verifiedOnly: Boolean = false,
)

/** A search result in the professional directory (public fields only — never credentials). */
data class ProfessionalDirectoryItem(
    val user: User,
    val profile: ProfessionalProfile,
    val badge: AuthorBadge,
)

interface AuthRepository {
    /** Null when signed out. */
    val currentUserId: StateFlow<String?>

    suspend fun register(
        displayName: String,
        email: String,
        password: String,
        accountType: AccountType,
        acceptedTerms: Boolean,
    ): DataResult<User>

    suspend fun login(email: String, password: String): DataResult<User>

    fun logout()

    suspend fun sendPasswordReset(email: String): DataResult<Unit>

    /** Deletes the signed-in account and its personal data (see policy in docs). */
    suspend fun deleteAccount(): DataResult<Unit>
}

interface UserRepository {
    fun observeUser(userId: String): Flow<User?>
    fun observeCurrentUser(): Flow<User?>

    /** Public user list, used to resolve author names/badges in feeds. */
    fun observeUsers(): Flow<List<User>>
    /** Public professional profiles, used to resolve verified badges in feeds. */
    fun observeAllProfessionals(): Flow<List<ProfessionalProfile>>

    suspend fun updateProfile(
        displayName: String,
        city: String?,
        bio: String?,
        photoUrl: String?,
    ): DataResult<Unit>

    suspend fun updatePrivacy(settings: PrivacySettings): DataResult<Unit>
    suspend fun updateNotificationSettings(settings: NotificationSettings): DataResult<Unit>

    suspend fun blockUser(targetUserId: String): DataResult<Unit>
    suspend fun unblockUser(targetUserId: String): DataResult<Unit>

    // Professional profile (public). Verification is handled by AdminRepository.
    fun observeProfessional(userId: String): Flow<ProfessionalProfile?>
    suspend fun upsertProfessionalProfile(
        title: String,
        workAreas: List<String>,
        city: String?,
        offersOnline: Boolean,
        experience: String?,
    ): DataResult<Unit>

    fun searchProfessionals(filter: ProfessionalFilter): Flow<List<ProfessionalDirectoryItem>>

    /** Submits a private verification request. Does NOT grant the badge. */
    suspend fun submitVerificationRequest(
        professionalTitle: String,
        licenseNumber: String?,
        credentialNote: String?,
    ): DataResult<Unit>

    fun observeMyVerification(): Flow<VerificationRequest?>

    // Private child profiles (owner-only).
    fun observeChildren(): Flow<List<ChildProfilePrivate>>
    suspend fun addChild(
        nickname: String?,
        ageRange: AgeRange,
        interests: List<String>,
    ): DataResult<Unit>
    suspend fun deleteChild(childId: String): DataResult<Unit>

    /** Produces a user-facing export of the signed-in user's own data (for data-access requests). */
    suspend fun exportMyData(): DataResult<String>
}

interface QuestionRepository {
    fun observeQuestions(sort: QuestionSort, category: QuestionCategory?): Flow<List<Question>>
    fun observeQuestion(questionId: String): Flow<Question?>
    fun observeAnswers(questionId: String): Flow<List<Answer>>

    suspend fun createQuestion(
        title: String,
        body: String,
        category: QuestionCategory,
        anonymous: Boolean,
    ): DataResult<String>

    suspend fun editQuestion(questionId: String, title: String, body: String): DataResult<Unit>
    suspend fun deleteQuestion(questionId: String): DataResult<Unit>
    suspend fun setCommentsClosed(questionId: String, closed: Boolean): DataResult<Unit>

    suspend fun createAnswer(questionId: String, body: String): DataResult<Unit>
    suspend fun toggleHelpful(answerId: String): DataResult<Unit>
}

interface GroupRepository {
    fun observeGroups(query: String): Flow<List<Group>>
    fun observeGroup(groupId: String): Flow<Group?>
    fun observeMemberships(groupId: String): Flow<List<GroupMembership>>
    fun observeMyMembership(groupId: String): Flow<GroupMembership?>
    fun observeMyGroups(): Flow<List<Group>>
    fun observePosts(groupId: String): Flow<List<GroupPost>>

    suspend fun createGroup(
        title: String,
        description: String,
        category: String?,
        city: String?,
        visibility: GroupVisibility,
        rules: String?,
    ): DataResult<String>

    /** Joins a public group, or files a join request for a private group. */
    suspend fun joinOrRequest(groupId: String): DataResult<Unit>
    suspend fun leave(groupId: String): DataResult<Unit>
    suspend fun approveMember(groupId: String, userId: String): DataResult<Unit>
    suspend fun removeMember(groupId: String, userId: String): DataResult<Unit>

    suspend fun createPost(groupId: String, body: String): DataResult<Unit>
    suspend fun deletePost(postId: String): DataResult<Unit>
}

interface ConversationRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeConversation(conversationId: String): Flow<Conversation?>
    fun observeMessages(conversationId: String): Flow<List<Message>>

    suspend fun requestConversation(recipientUserId: String): DataResult<String>
    suspend fun acceptConversation(conversationId: String): DataResult<Unit>
    suspend fun declineConversation(conversationId: String): DataResult<Unit>
    suspend fun endConversation(conversationId: String): DataResult<Unit>
    suspend fun toggleMute(conversationId: String): DataResult<Unit>
    suspend fun sendMessage(conversationId: String, body: String): DataResult<Unit>
}

interface GuideRepository {
    fun observeGuides(category: String?): Flow<List<GuideArticle>>
    fun observeGuide(guideId: String): Flow<GuideArticle?>
    fun observeSaved(): Flow<List<GuideArticle>>
    suspend fun toggleSaved(guideId: String): DataResult<Unit>
    // Admin authoring:
    suspend fun upsertGuide(guide: GuideArticle): DataResult<Unit>
}

interface ReportRepository {
    suspend fun createReport(
        contentType: ContentType,
        contentId: String,
        reason: ReportReason,
        note: String?,
    ): DataResult<Unit>

    // Admin:
    fun observeOpenReports(): Flow<List<Report>>
    suspend fun resolveReport(
        reportId: String,
        newStatus: ReportStatus,
        resolutionNote: String,
    ): DataResult<Unit>
}

interface NotificationRepository {
    fun observeNotifications(): Flow<List<Notification>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun markRead(notificationId: String): DataResult<Unit>
    suspend fun markAllRead(): DataResult<Unit>
}

interface AdminRepository {
    fun observeVerificationRequests(): Flow<List<VerificationRequest>>
    fun observeAdminActions(): Flow<List<AdminAction>>

    /** Logs a [AdminActionType.VIEW_CREDENTIAL] action and returns the private request. */
    suspend fun viewCredential(requestId: String): DataResult<VerificationRequest>

    suspend fun reviewVerification(
        requestId: String,
        decision: VerificationStatus,
        reason: String,
    ): DataResult<Unit>

    suspend fun hideContent(type: ContentType, contentId: String, reason: String): DataResult<Unit>
    suspend fun deleteContent(type: ContentType, contentId: String, reason: String): DataResult<Unit>
    suspend fun setAccountStatus(userId: String, status: AccountStatus, reason: String): DataResult<Unit>
}
