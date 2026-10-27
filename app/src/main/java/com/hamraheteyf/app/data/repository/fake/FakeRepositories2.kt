package com.hamraheteyf.app.data.repository.fake

import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

// ---------------------------------------------------------------------------------------
// Conversations & messages (text-only; no attachments in v1)
// ---------------------------------------------------------------------------------------

class FakeConversationRepository(private val store: FakeStore) : ConversationRepository {

    private fun uid(): String? = store.currentUserId.value

    override fun observeConversations(): Flow<List<Conversation>> =
        combine(store.currentUserId, store.state) { id, db ->
            if (id == null) emptyList()
            else db.conversations.filter { id in it.participantIds && it.status != ConversationStatus.DECLINED }
                .sortedByDescending { it.updatedAt }
        }

    override fun observeConversation(conversationId: String): Flow<Conversation?> =
        combine(store.currentUserId, store.state) { id, db ->
            db.conversations.find { it.id == conversationId && id != null && id in it.participantIds }
        }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        combine(store.currentUserId, store.state) { id, db ->
            val conv = db.conversations.find { it.id == conversationId } ?: return@combine emptyList()
            if (id == null || id !in conv.participantIds) emptyList()
            else db.messages.filter { it.conversationId == conversationId }.sortedBy { it.createdAt }
        }

    override suspend fun requestConversation(recipientUserId: String): DataResult<String> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        if (id == recipientUserId) return err("نمی‌توانید با خودتان گفتگو شروع کنید.")
        val db = store.state.value
        val recipient = db.users.find { it.id == recipientUserId } ?: return err("کاربر یافت نشد.")
        if (!recipient.privacy.allowMessageRequests) return err("این کاربر درخواست گفتگو نمی‌پذیرد.")
        val me = db.users.find { it.id == id }
        if (recipientUserId in (me?.blockedUserIds ?: emptySet())) return err("این کاربر در فهرست مسدودشده‌های شماست.")
        if (id in recipient.blockedUserIds) return err("امکان ارسال درخواست به این کاربر وجود ندارد.")

        val existing = db.conversations.find { it.participantIds.toSet() == setOf(id, recipientUserId) && it.status != ConversationStatus.DECLINED }
        if (existing != null) return DataResult.Success(existing.id)

        val convId = store.newId("conv")
        store.update { cur ->
            cur.copy(conversations = cur.conversations + Conversation(
                id = convId, participantIds = listOf(id, recipientUserId), initiatorId = id,
                status = ConversationStatus.REQUESTED, createdAt = store.now(), updatedAt = store.now(),
            )).addNotification(store, recipientUserId, NotificationType.NEW_MESSAGE, "درخواست گفتگو", "یک درخواست گفتگوی تازه دارید.", relatedId = convId)
        }
        return DataResult.Success(convId)
    }

    override suspend fun acceptConversation(conversationId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val conv = store.state.value.conversations.find { it.id == conversationId } ?: return err("گفتگو یافت نشد.")
        if (conv.initiatorId == id) return err("پذیرش گفتگو با طرف مقابل است.")
        if (id !in conv.participantIds) return err("اجازهٔ دسترسی ندارید.")
        store.update { db -> db.copy(conversations = db.conversations.map { if (it.id == conversationId) it.copy(status = ConversationStatus.ACCEPTED, updatedAt = store.now()) else it }) }
        return DataResult.Success(Unit)
    }

    override suspend fun declineConversation(conversationId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val conv = store.state.value.conversations.find { it.id == conversationId } ?: return err("گفتگو یافت نشد.")
        if (id !in conv.participantIds) return err("اجازهٔ دسترسی ندارید.")
        store.update { db -> db.copy(conversations = db.conversations.map { if (it.id == conversationId) it.copy(status = ConversationStatus.DECLINED, updatedAt = store.now()) else it }) }
        return DataResult.Success(Unit)
    }

    override suspend fun endConversation(conversationId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val conv = store.state.value.conversations.find { it.id == conversationId } ?: return err("گفتگو یافت نشد.")
        if (id !in conv.participantIds) return err("اجازهٔ دسترسی ندارید.")
        store.update { db -> db.copy(conversations = db.conversations.map { if (it.id == conversationId) it.copy(status = ConversationStatus.ENDED, endedByUserId = id, updatedAt = store.now()) else it }) }
        return DataResult.Success(Unit)
    }

    override suspend fun toggleMute(conversationId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        store.update { db ->
            db.copy(conversations = db.conversations.map {
                if (it.id == conversationId && id in it.participantIds) {
                    val muted = if (id in it.mutedByUserIds) it.mutedByUserIds - id else it.mutedByUserIds + id
                    it.copy(mutedByUserIds = muted)
                } else it
            })
        }
        return DataResult.Success(Unit)
    }

    override suspend fun sendMessage(conversationId: String, body: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val conv = store.state.value.conversations.find { it.id == conversationId } ?: return err("گفتگو یافت نشد.")
        if (id !in conv.participantIds) return err("اجازهٔ دسترسی ندارید.")
        if (conv.status != ConversationStatus.ACCEPTED) return err("گفتگو فعال نیست.")
        if (body.trim().isEmpty()) return err("پیام خالی است.")
        val other = conv.participantIds.first { it != id }
        store.update { db ->
            db.copy(
                messages = db.messages + Message(id = store.newId("msg"), conversationId = conversationId, senderId = id, body = body.trim(), createdAt = store.now()),
                conversations = db.conversations.map { if (it.id == conversationId) it.copy(lastMessagePreview = body.trim().take(40), updatedAt = store.now()) else it },
            ).addNotification(store, other, NotificationType.NEW_MESSAGE, "پیام جدید", "پیام جدید دارید.", relatedId = conversationId)
        }
        return DataResult.Success(Unit)
    }
}

// ---------------------------------------------------------------------------------------
// Guides
// ---------------------------------------------------------------------------------------

class FakeGuideRepository(private val store: FakeStore) : GuideRepository {

    private fun uid(): String? = store.currentUserId.value

    override fun observeGuides(category: String?): Flow<List<GuideArticle>> =
        store.state.map { db ->
            db.guides.filter { it.status == ContentStatus.PUBLISHED }
                .filter { category.isNullOrBlank() || it.category == category }
                .sortedByDescending { it.updatedAt }
        }

    override fun observeGuide(guideId: String): Flow<GuideArticle?> =
        store.state.map { db -> db.guides.find { it.id == guideId } }

    override fun observeSaved(): Flow<List<GuideArticle>> =
        combine(store.currentUserId, store.state) { id, db ->
            if (id == null) emptyList() else db.guides.filter { id in it.savedByUserIds }
        }

    override suspend fun toggleSaved(guideId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        store.update { db ->
            db.copy(guides = db.guides.map {
                if (it.id == guideId) {
                    val saved = if (id in it.savedByUserIds) it.savedByUserIds - id else it.savedByUserIds + id
                    it.copy(savedByUserIds = saved)
                } else it
            })
        }
        return DataResult.Success(Unit)
    }

    override suspend fun upsertGuide(guide: GuideArticle): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val me = store.state.value.users.find { it.id == id }
        if (me?.isAdmin != true) return err("فقط مدیر می‌تواند راهنما را ویرایش کند.")
        store.update { db ->
            val exists = db.guides.any { it.id == guide.id }
            db.copy(guides = if (exists) db.guides.map { if (it.id == guide.id) guide.copy(updatedAt = store.now()) else it }
            else db.guides + guide.copy(createdAt = store.now(), updatedAt = store.now()))
        }
        return DataResult.Success(Unit)
    }
}

// ---------------------------------------------------------------------------------------
// Reports
// ---------------------------------------------------------------------------------------

class FakeReportRepository(private val store: FakeStore) : ReportRepository {

    private fun uid(): String? = store.currentUserId.value

    override suspend fun createReport(contentType: ContentType, contentId: String, reason: ReportReason, note: String?): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        store.update { db ->
            db.copy(reports = db.reports + Report(
                id = store.newId("r"), reporterId = id, contentType = contentType, contentId = contentId,
                reason = reason, note = note?.trim()?.ifEmpty { null }, status = ReportStatus.OPEN,
                createdAt = store.now(), updatedAt = store.now(),
            ))
        }
        return DataResult.Success(Unit)
    }

    override fun observeOpenReports(): Flow<List<Report>> =
        combine(store.currentUserId, store.state) { id, db ->
            val me = db.users.find { it.id == id }
            if (me?.isAdmin != true) emptyList()
            else db.reports.filter { it.status == ReportStatus.OPEN || it.status == ReportStatus.UNDER_REVIEW }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun resolveReport(reportId: String, newStatus: ReportStatus, resolutionNote: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val me = store.state.value.users.find { it.id == id }
        if (me?.isAdmin != true) return err("فقط مدیر می‌تواند گزارش را رسیدگی کند.")
        store.update { db ->
            db.copy(
                reports = db.reports.map { if (it.id == reportId) it.copy(status = newStatus, resolutionNote = resolutionNote, reviewedByAdminId = id, updatedAt = store.now()) else it },
                adminActions = db.adminActions + AdminAction(
                    id = store.newId("aa"), adminId = id, actionType = AdminActionType.RESOLVE_REPORT,
                    targetType = ContentType.USER, targetId = reportId, reason = resolutionNote, createdAt = store.now(),
                ),
            )
        }
        return DataResult.Success(Unit)
    }
}

// ---------------------------------------------------------------------------------------
// Notifications
// ---------------------------------------------------------------------------------------

class FakeNotificationRepository(private val store: FakeStore) : NotificationRepository {

    override fun observeNotifications(): Flow<List<Notification>> =
        combine(store.currentUserId, store.state) { id, db ->
            if (id == null) emptyList() else db.notifications.filter { it.userId == id }.sortedByDescending { it.createdAt }
        }

    override fun observeUnreadCount(): Flow<Int> =
        combine(store.currentUserId, store.state) { id, db ->
            if (id == null) 0 else db.notifications.count { it.userId == id && !it.read }
        }

    override suspend fun markRead(notificationId: String): DataResult<Unit> {
        val id = store.currentUserId.value ?: return err(NOT_SIGNED_IN)
        store.update { db -> db.copy(notifications = db.notifications.map { if (it.id == notificationId && it.userId == id) it.copy(read = true) else it }) }
        return DataResult.Success(Unit)
    }

    override suspend fun markAllRead(): DataResult<Unit> {
        val id = store.currentUserId.value ?: return err(NOT_SIGNED_IN)
        store.update { db -> db.copy(notifications = db.notifications.map { if (it.userId == id) it.copy(read = true) else it }) }
        return DataResult.Success(Unit)
    }
}

// ---------------------------------------------------------------------------------------
// Admin (auditable)
// ---------------------------------------------------------------------------------------

class FakeAdminRepository(private val store: FakeStore) : AdminRepository {

    private fun requireAdmin(): String? {
        val id = store.currentUserId.value ?: return null
        return if (store.state.value.users.find { it.id == id }?.isAdmin == true) id else null
    }

    override fun observeVerificationRequests(): Flow<List<VerificationRequest>> =
        combine(store.currentUserId, store.state) { id, db ->
            val me = db.users.find { it.id == id }
            if (me?.isAdmin != true) emptyList()
            else db.verifications.sortedWith(
                compareBy<VerificationRequest> { it.status != VerificationStatus.PENDING }.thenByDescending { it.updatedAt }
            )
        }

    override fun observeAdminActions(): Flow<List<AdminAction>> =
        combine(store.currentUserId, store.state) { id, db ->
            val me = db.users.find { it.id == id }
            if (me?.isAdmin != true) emptyList() else db.adminActions.sortedByDescending { it.createdAt }
        }

    override suspend fun viewCredential(requestId: String): DataResult<VerificationRequest> {
        val adminId = requireAdmin() ?: return err("دسترسی مدیر لازم است.")
        val request = store.state.value.verifications.find { it.id == requestId } ?: return err("درخواست یافت نشد.")
        // Viewing a private credential is itself an audited action.
        store.update { db ->
            db.copy(adminActions = db.adminActions + AdminAction(
                id = store.newId("aa"), adminId = adminId, actionType = AdminActionType.VIEW_CREDENTIAL,
                targetType = ContentType.USER, targetId = request.userId, reason = "مشاهدهٔ مدرک برای بررسی", createdAt = store.now(),
            ))
        }
        return DataResult.Success(request)
    }

    override suspend fun reviewVerification(requestId: String, decision: VerificationStatus, reason: String): DataResult<Unit> {
        val adminId = requireAdmin() ?: return err("دسترسی مدیر لازم است.")
        if (reason.trim().isEmpty()) return err("ثبت دلیل تصمیم الزامی است.")
        val request = store.state.value.verifications.find { it.id == requestId } ?: return err("درخواست یافت نشد.")
        val actionType = when (decision) {
            VerificationStatus.VERIFIED -> AdminActionType.VERIFY_PROFESSIONAL
            VerificationStatus.REJECTED -> AdminActionType.REJECT_PROFESSIONAL
            else -> AdminActionType.REQUEST_MORE_INFO
        }
        store.update { db ->
            db.copy(
                verifications = db.verifications.map { if (it.id == requestId) it.copy(status = decision, reviewerNote = reason.trim(), reviewedByAdminId = adminId, updatedAt = store.now()) else it },
                professionals = db.professionals.map { if (it.userId == request.userId) it.copy(verificationStatus = decision, updatedAt = store.now()) else it },
                adminActions = db.adminActions + AdminAction(
                    id = store.newId("aa"), adminId = adminId, actionType = actionType,
                    targetType = ContentType.USER, targetId = request.userId, reason = reason.trim(), createdAt = store.now(),
                ),
            ).addNotification(store, request.userId, NotificationType.VERIFICATION_RESULT, "نتیجهٔ بررسی تخصص", "وضعیت بررسی تخصص شما به «${decision.label}» تغییر کرد.")
        }
        return DataResult.Success(Unit)
    }

    override suspend fun hideContent(type: ContentType, contentId: String, reason: String): DataResult<Unit> =
        setContentStatus(type, contentId, ContentStatus.HIDDEN, AdminActionType.HIDE_CONTENT, reason)

    override suspend fun deleteContent(type: ContentType, contentId: String, reason: String): DataResult<Unit> =
        setContentStatus(type, contentId, ContentStatus.REMOVED, AdminActionType.DELETE_CONTENT, reason)

    private fun setContentStatus(type: ContentType, contentId: String, status: ContentStatus, action: AdminActionType, reason: String): DataResult<Unit> {
        val adminId = requireAdmin() ?: return err("دسترسی مدیر لازم است.")
        if (reason.trim().isEmpty()) return err("ثبت دلیل تصمیم الزامی است.")
        store.update { db ->
            db.copy(
                questions = if (type == ContentType.QUESTION) db.questions.map { if (it.id == contentId) it.copy(status = status) else it } else db.questions,
                answers = if (type == ContentType.ANSWER) db.answers.map { if (it.id == contentId) it.copy(status = status) else it } else db.answers,
                posts = if (type == ContentType.GROUP_POST) db.posts.map { if (it.id == contentId) it.copy(status = status) else it } else db.posts,
                guides = if (type == ContentType.GUIDE) db.guides.map { if (it.id == contentId) it.copy(status = status) else it } else db.guides,
                adminActions = db.adminActions + AdminAction(id = store.newId("aa"), adminId = adminId, actionType = action, targetType = type, targetId = contentId, reason = reason.trim(), createdAt = store.now()),
            )
        }
        return DataResult.Success(Unit)
    }

    override suspend fun setAccountStatus(userId: String, status: AccountStatus, reason: String): DataResult<Unit> {
        val adminId = requireAdmin() ?: return err("دسترسی مدیر لازم است.")
        if (reason.trim().isEmpty()) return err("ثبت دلیل تصمیم الزامی است.")
        val action = if (status == AccountStatus.BLOCKED) AdminActionType.BLOCK_ACCOUNT else AdminActionType.LIMIT_ACCOUNT
        store.update { db ->
            db.copy(
                users = db.users.map { if (it.id == userId) it.copy(status = status, updatedAt = store.now()) else it },
                adminActions = db.adminActions + AdminAction(id = store.newId("aa"), adminId = adminId, actionType = action, targetType = ContentType.USER, targetId = userId, reason = reason.trim(), createdAt = store.now()),
            )
        }
        return DataResult.Success(Unit)
    }
}
