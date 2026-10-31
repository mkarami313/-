package com.hamraheteyf.app.data.repository.server

import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.*
import com.hamraheteyf.app.data.repository.fake.*
import com.hamraheteyf.app.net.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/**
 * Server-backed repositories.
 *
 * Every `observe*` delegates to the corresponding `fake` implementation reading the local
 * mirror [ServerStore.mirror] — identical filtering/sorting, now fed by server-scoped data.
 * Every mutation POSTs to the API and lets the returned snapshot refresh the mirror.
 */

// ------------------------------------------------------------------------- Auth

class ServerAuthRepository(private val store: ServerStore) : AuthRepository {

    override val currentUserId: StateFlow<String?> get() = store.currentUserId

    override suspend fun register(
        displayName: String, email: String, password: String,
        accountType: AccountType, acceptedTerms: Boolean,
    ): DataResult<User> {
        val body = argsJson(
            "displayName" to displayName, "email" to email, "password" to password,
            "accountType" to accountType, "acceptedTerms" to acceptedTerms,
        )
        return when (val r = store.api.register(body)) {
            is ApiResult.Ok -> { store.onAuthenticated(r.data); DataResult.Success(r.data.user) }
            is ApiResult.Fail -> DataResult.Error(r.message)
        }
    }

    override suspend fun login(email: String, password: String): DataResult<User> {
        val body = argsJson("email" to email, "password" to password)
        return when (val r = store.api.login(body)) {
            is ApiResult.Ok -> { store.onAuthenticated(r.data); DataResult.Success(r.data.user) }
            is ApiResult.Fail -> DataResult.Error(r.message)
        }
    }

    override fun logout() = store.signOut()

    override suspend fun sendPasswordReset(email: String): DataResult<Unit> {
        store.api.reset(argsJson("email" to email))
        return DataResult.Success(Unit)
    }

    override suspend fun deleteAccount(): DataResult<Unit> {
        val r = store.actUnit("deleteAccount", "{}")
        if (r is DataResult.Success) store.signOut()
        return r
    }

    override suspend fun deleteAccount(password: String): DataResult<Unit> {
        val r = store.actUnit("deleteAccount", argsJson("password" to password))
        if (r is DataResult.Success) store.signOut()
        return r
    }

    override suspend fun changePassword(current: String, new: String): DataResult<Unit> =
        when (val r = store.api.action("changePassword", argsJson("current" to current, "new" to new))) {
            is ApiResult.Ok -> {
                // The server revoked every old session; adopt the fresh token before anything else polls.
                ServerStore.stringField(r.data.data, "token")?.let { store.api.token = it }
                r.data.state?.let { store.refresh(force = true) }
                DataResult.Success(Unit)
            }
            is ApiResult.Fail -> DataResult.Error(r.message)
        }
}

// ------------------------------------------------------------------------- Users

class ServerUserRepository(private val store: ServerStore) : UserRepository {
    private val read = FakeUserRepository(store.mirror)
    private val json get() = store.api.json

    override fun observeUser(userId: String): Flow<User?> = read.observeUser(userId)
    override fun observeCurrentUser(): Flow<User?> = read.observeCurrentUser()
    override fun observeUsers(): Flow<List<User>> = read.observeUsers()
    override fun observeAllProfessionals(): Flow<List<ProfessionalProfile>> = read.observeAllProfessionals()
    override fun observeProfessional(userId: String): Flow<ProfessionalProfile?> = read.observeProfessional(userId)
    override fun observeMyVerification(): Flow<VerificationRequest?> = read.observeMyVerification()
    override fun observeChildren(): Flow<List<ChildProfilePrivate>> = read.observeChildren()
    override fun searchProfessionals(filter: ProfessionalFilter): Flow<List<ProfessionalDirectoryItem>> =
        kotlinx.coroutines.flow.combine(read.searchProfessionals(filter), read.observeCurrentUser()) { items, me ->
            val blocked = me?.blockedUserIds.orEmpty()
            if (blocked.isEmpty()) items else items.filterNot { it.user.id in blocked }
        }

    override suspend fun updateProfile(displayName: String, city: String?, bio: String?, photoUrl: String?) =
        store.actUnit("updateProfile", argsJson(*buildList<Pair<String, Any?>> {
            add("displayName" to displayName); add("city" to city); add("bio" to bio)
            if (photoUrl != null) add("photoUrl" to photoUrl)
        }.toTypedArray()))

    override suspend fun updateProfileWithPhoto(displayName: String, city: String?, bio: String?, photoId: String?) =
        store.actUnit("updateProfile", argsJson(*buildList<Pair<String, Any?>> {
            add("displayName" to displayName); add("city" to city); add("bio" to bio)
            if (photoId != null) add("photoId" to photoId)
        }.toTypedArray()))

    override suspend fun updatePrivacy(settings: PrivacySettings) =
        store.actUnit("updatePrivacy", argsJson(
            "settings" to json.encodeToJsonElement(PrivacySettings.serializer(), settings)))

    override suspend fun updateNotificationSettings(settings: NotificationSettings) =
        store.actUnit("updateNotificationSettings", argsJson(
            "settings" to json.encodeToJsonElement(NotificationSettings.serializer(), settings)))

    override suspend fun blockUser(targetUserId: String) =
        store.actUnit("blockUser", argsJson("targetUserId" to targetUserId))

    override suspend fun unblockUser(targetUserId: String) =
        store.actUnit("unblockUser", argsJson("targetUserId" to targetUserId))

    override suspend fun upsertProfessionalProfile(
        title: String, workAreas: List<String>, city: String?, offersOnline: Boolean, experience: String?,
        photoId: String?, fee: String?, hours: String?,
    ) = store.actUnit("upsertProfessionalProfile", argsJson(
        "title" to title, "workAreas" to workAreas, "city" to city,
        "offersOnline" to offersOnline, "experience" to experience,
        "photoId" to photoId, "fee" to fee, "hours" to hours))

    override suspend fun submitVerificationRequest(
        professionalTitle: String, licenseNumber: String?, credentialNote: String?,
        requestedType: AccountType?, documentRefs: List<String>,
    ) = store.actUnit("submitVerificationRequest", argsJson(
        "professionalTitle" to professionalTitle, "licenseNumber" to licenseNumber,
        "credentialNote" to credentialNote,
        "requestedType" to requestedType, "accountType" to requestedType,
        "documentRefs" to documentRefs))

    override suspend fun addChild(nickname: String?, ageRange: AgeRange, interests: List<String>) =
        store.actUnit("addChild", argsJson(
            "nickname" to nickname, "ageRange" to ageRange, "interests" to interests))

    override suspend fun deleteChild(childId: String) =
        store.actUnit("deleteChild", argsJson("childId" to childId, "confirm" to true))

    override suspend fun updateChild(childId: String, nickname: String?, ageRange: AgeRange, interests: List<String>, notes: String?) =
        store.actUnit("updateChild", argsJson("id" to childId, "fields" to store.api.json.parseToJsonElement(
            argsJson(*buildList<Pair<String, Any?>> {
                // The server rejects an empty nickname, so a blank one is simply left unchanged.
                if (!nickname.isNullOrBlank()) add("nickname" to nickname)
                add("ageRange" to ageRange); add("interests" to interests)
                if (notes != null) add("notes" to notes)
            }.toTypedArray()))))

    override suspend fun exportMyDataFile(): DataResult<ExportFile> =
        when (val r = store.act("exportMyData", "{}")) {
            is DataResult.Success -> {
                val json = ServerStore.stringField(r.data, "json")
                if (json.isNullOrBlank()) DataResult.Error("پاسخ سرور نامعتبر است.")
                else DataResult.Success(ExportFile(
                    ServerStore.stringField(r.data, "filename") ?: "hamrah-export.json", json,
                    ServerStore.stringField(r.data, "text").orEmpty()))
            }
            is DataResult.Error -> DataResult.Error(r.message)
        }

    override suspend fun exportMyData(): DataResult<String> =
        when (val r = store.act("exportMyData", "{}")) {
            is DataResult.Success -> DataResult.Success(ServerStore.stringField(r.data, "text") ?: "")
            is DataResult.Error -> DataResult.Error(r.message)
        }
}

// ------------------------------------------------------------------------- Questions

class ServerQuestionRepository(private val store: ServerStore) : QuestionRepository {
    private val read = FakeQuestionRepository(store.mirror)

    override fun observeQuestions(sort: QuestionSort, category: QuestionCategory?): Flow<List<Question>> =
        read.observeQuestions(sort, category)
    override fun observeQuestion(questionId: String): Flow<Question?> = read.observeQuestion(questionId)
    override fun observeAnswers(questionId: String): Flow<List<Answer>> = read.observeAnswers(questionId)

    override suspend fun createQuestion(
        title: String, body: String, category: QuestionCategory, anonymous: Boolean,
    ): DataResult<String> = when (val r = store.act("createQuestion", argsJson(
        "title" to title, "body" to body, "category" to category, "anonymous" to anonymous))) {
        is DataResult.Success -> DataResult.Success(ServerStore.idOf(r.data) ?: "")
        is DataResult.Error -> DataResult.Error(r.message)
    }

    override suspend fun editQuestion(questionId: String, title: String, body: String) =
        store.actUnit("editQuestion", argsJson("questionId" to questionId, "title" to title, "body" to body))

    override suspend fun deleteQuestion(questionId: String) =
        store.actUnit("deleteQuestion", argsJson("questionId" to questionId))

    override suspend fun setCommentsClosed(questionId: String, closed: Boolean) =
        store.actUnit("setCommentsClosed", argsJson("questionId" to questionId, "closed" to closed))

    override suspend fun createAnswer(questionId: String, body: String) =
        store.actUnit("createAnswer", argsJson("questionId" to questionId, "body" to body))

    override suspend fun toggleHelpful(answerId: String) =
        store.actUnit("toggleHelpful", argsJson("answerId" to answerId))
}

// ------------------------------------------------------------------------- Groups

class ServerGroupRepository(private val store: ServerStore) : GroupRepository {
    private val read = FakeGroupRepository(store.mirror)

    override fun observeGroups(query: String): Flow<List<Group>> = read.observeGroups(query)
    override fun observeGroup(groupId: String): Flow<Group?> = read.observeGroup(groupId)
    override fun observeMemberships(groupId: String): Flow<List<GroupMembership>> = read.observeMemberships(groupId)
    override fun observeMyMembership(groupId: String): Flow<GroupMembership?> = read.observeMyMembership(groupId)
    override fun observeMyGroups(): Flow<List<Group>> = read.observeMyGroups()
    override fun observePosts(groupId: String): Flow<List<GroupPost>> = read.observePosts(groupId)

    override suspend fun createGroup(
        title: String, description: String, category: String?, city: String?,
        visibility: GroupVisibility, rules: String?,
    ): DataResult<String> = when (val r = store.act("createGroup", argsJson(
        "title" to title, "description" to description, "category" to category,
        "city" to city, "visibility" to visibility, "rules" to rules))) {
        is DataResult.Success -> DataResult.Success(ServerStore.idOf(r.data) ?: "")
        is DataResult.Error -> DataResult.Error(r.message)
    }

    override suspend fun joinOrRequest(groupId: String) =
        store.actUnit("joinOrRequest", argsJson("groupId" to groupId))

    override suspend fun leave(groupId: String) =
        store.actUnit("leave", argsJson("groupId" to groupId))

    override suspend fun approveMember(groupId: String, userId: String) =
        store.actUnit("approveMember", argsJson("groupId" to groupId, "userId" to userId))

    override suspend fun removeMember(groupId: String, userId: String) =
        store.actUnit("removeMember", argsJson("groupId" to groupId, "userId" to userId))

    override suspend fun createPost(groupId: String, body: String) =
        store.actUnit("createPost", argsJson("groupId" to groupId, "body" to body))

    override suspend fun deletePost(postId: String) =
        store.actUnit("deletePost", argsJson("postId" to postId))
}

// ------------------------------------------------------------------------- Conversations

class ServerConversationRepository(private val store: ServerStore) : ConversationRepository {
    private val read = FakeConversationRepository(store.mirror)

    override fun observeConversations(): Flow<List<Conversation>> = read.observeConversations()
    override fun observeConversation(conversationId: String): Flow<Conversation?> =
        read.observeConversation(conversationId)
    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        read.observeMessages(conversationId)

    override suspend fun requestConversation(recipientUserId: String): DataResult<String> =
        when (val r = store.act("requestConversation", argsJson("recipientUserId" to recipientUserId))) {
            is DataResult.Success -> DataResult.Success(ServerStore.idOf(r.data) ?: "")
            is DataResult.Error -> DataResult.Error(r.message)
        }

    override suspend fun acceptConversation(conversationId: String) =
        store.actUnit("acceptConversation", argsJson("conversationId" to conversationId))

    override suspend fun declineConversation(conversationId: String) =
        store.actUnit("declineConversation", argsJson("conversationId" to conversationId))

    override suspend fun endConversation(conversationId: String) =
        store.actUnit("endConversation", argsJson("conversationId" to conversationId))

    override suspend fun toggleMute(conversationId: String) =
        store.actUnit("toggleMute", argsJson("conversationId" to conversationId))

    override suspend fun sendMessage(conversationId: String, body: String) =
        store.actUnit("sendMessage", argsJson("conversationId" to conversationId, "body" to body))
}

// ------------------------------------------------------------------------- Guides

class ServerGuideRepository(private val store: ServerStore) : GuideRepository {
    private val read = FakeGuideRepository(store.mirror)
    private val json get() = store.api.json

    override fun observeGuides(category: String?): Flow<List<GuideArticle>> = read.observeGuides(category)
    override fun observeGuide(guideId: String): Flow<GuideArticle?> = read.observeGuide(guideId)
    override fun observeSaved(): Flow<List<GuideArticle>> = read.observeSaved()

    override suspend fun toggleSaved(guideId: String) =
        store.actUnit("toggleSaved", argsJson("guideId" to guideId))

    override suspend fun upsertGuide(guide: GuideArticle) =
        store.actUnit("upsertGuide", argsJson(
            "guide" to json.encodeToJsonElement(GuideArticle.serializer(), guide)))
}

// ------------------------------------------------------------------------- Reports

class ServerReportRepository(private val store: ServerStore) : ReportRepository {
    private val read = FakeReportRepository(store.mirror)

    override fun observeOpenReports(): Flow<List<Report>> = read.observeOpenReports()

    override suspend fun createReport(
        contentType: ContentType, contentId: String, reason: ReportReason, note: String?,
    ) = store.actUnit("createReport", argsJson(
        "contentType" to contentType, "contentId" to contentId, "reason" to reason, "note" to note))

    override suspend fun resolveReport(reportId: String, newStatus: ReportStatus, resolutionNote: String) =
        store.actUnit("resolveReport", argsJson(
            "reportId" to reportId, "newStatus" to newStatus, "resolutionNote" to resolutionNote))
}

// ------------------------------------------------------------------------- Notifications

class ServerNotificationRepository(private val store: ServerStore) : NotificationRepository {
    private val read = FakeNotificationRepository(store.mirror)

    override fun observeNotifications(): Flow<List<Notification>> = read.observeNotifications()
    override fun observeUnreadCount(): Flow<Int> = read.observeUnreadCount()

    override suspend fun markRead(notificationId: String) =
        store.actUnit("markRead", argsJson("notificationId" to notificationId))

    override suspend fun markAllRead() = store.actUnit("markAllRead", "{}")
}

// ------------------------------------------------------------------------- Admin

class ServerAdminRepository(private val store: ServerStore) : AdminRepository {
    private val read = FakeAdminRepository(store.mirror)
    private val json get() = store.api.json

    override fun observeVerificationRequests(): Flow<List<VerificationRequest>> =
        read.observeVerificationRequests()
    override fun observeAdminActions(): Flow<List<AdminAction>> = read.observeAdminActions()

    override suspend fun viewCredential(requestId: String): DataResult<VerificationRequest> =
        when (val r = store.act("viewCredential", argsJson("requestId" to requestId))) {
            is DataResult.Success -> {
                val obj = ServerStore.objectField(r.data, "request")
                if (obj != null)
                    DataResult.Success(json.decodeFromJsonElement(VerificationRequest.serializer(), obj))
                else DataResult.Error("درخواست یافت نشد.")
            }
            is DataResult.Error -> DataResult.Error(r.message)
        }

    override suspend fun reviewVerification(requestId: String, decision: VerificationStatus, reason: String) =
        store.actUnit("reviewVerification", argsJson(
            "requestId" to requestId, "decision" to decision, "reason" to reason))

    override suspend fun hideContent(type: ContentType, contentId: String, reason: String) =
        store.actUnit("hideContent", argsJson("type" to type, "contentId" to contentId, "reason" to reason))

    override suspend fun deleteContent(type: ContentType, contentId: String, reason: String) =
        store.actUnit("deleteContent", argsJson("type" to type, "contentId" to contentId, "reason" to reason))

    override suspend fun setAccountStatus(userId: String, status: AccountStatus, reason: String) =
        store.actUnit("setAccountStatus", argsJson("userId" to userId, "status" to status, "reason" to reason))
}
