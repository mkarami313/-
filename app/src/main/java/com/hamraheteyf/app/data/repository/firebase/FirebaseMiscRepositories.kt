package com.hamraheteyf.app.data.repository.firebase

import com.google.firebase.firestore.FieldValue
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

// ---------------------------------------------------------------------------------------
// Conversations & messages (text only)
// ---------------------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseConversationRepository : ConversationRepository {

    private val db = firestore()

    override fun observeConversations(): Flow<List<Conversation>> =
        authUidFlow().flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else db.collection(Fs.CONVERSATIONS).whereArrayContains("participantIds", id)
                .asListFlow(::conversationFromDoc)
                .map { list -> list.filter { it.status != ConversationStatus.DECLINED }.sortedByDescending { it.updatedAt } }
        }

    override fun observeConversation(conversationId: String): Flow<Conversation?> =
        db.collection(Fs.CONVERSATIONS).document(conversationId).asDocFlow(::conversationFromDoc)
            .map { conv ->
                val me = currentUid()
                conv?.takeIf { me != null && me in it.participantIds }
            }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        db.collection(Fs.MESSAGES).whereEqualTo("conversationId", conversationId).asListFlow(::messageFromDoc)
            .map { list -> list.sortedBy { it.createdAt } }

    override suspend fun requestConversation(recipientUserId: String): DataResult<String> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        if (id == recipientUserId) return fbError("نمی‌توانید با خودتان گفتگو شروع کنید.")
        return try {
            val recipientSnap = db.collection(Fs.USERS).document(recipientUserId).get().await()
            if (!recipientSnap.exists()) return fbError("کاربر یافت نشد.")
            val recipient = userFromDoc(recipientSnap)
            if (!recipient.privacy.allowMessageRequests) return fbError("این کاربر درخواست گفتگو نمی‌پذیرد.")
            if (id in recipient.blockedUserIds) return fbError("امکان ارسال درخواست به این کاربر وجود ندارد.")
            val me = userFromDoc(db.collection(Fs.USERS).document(id).get().await())
            if (recipientUserId in me.blockedUserIds) return fbError("این کاربر در فهرست مسدودشده‌های شماست.")

            val existing = db.collection(Fs.CONVERSATIONS).whereArrayContains("participantIds", id).get().await()
                .documents.map { conversationFromDoc(it) }
                .firstOrNull { recipientUserId in it.participantIds && it.status != ConversationStatus.DECLINED }
            if (existing != null) return DataResult.Success(existing.id)

            val conv = Conversation(id = "", participantIds = listOf(id, recipientUserId), initiatorId = id,
                status = ConversationStatus.REQUESTED, createdAt = now(), updatedAt = now())
            val ref = db.collection(Fs.CONVERSATIONS).add(conv.toMap()).await()
            pushNotification(recipientUserId, NotificationType.NEW_MESSAGE, "درخواست گفتگو", "یک درخواست گفتگوی تازه دارید.", ref.id)
            DataResult.Success(ref.id)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    override suspend fun acceptConversation(conversationId: String): DataResult<Unit> =
        setConvStatus(conversationId, ConversationStatus.ACCEPTED) { conv, id -> conv.initiatorId != id }

    override suspend fun declineConversation(conversationId: String): DataResult<Unit> =
        setConvStatus(conversationId, ConversationStatus.DECLINED) { _, _ -> true }

    override suspend fun endConversation(conversationId: String): DataResult<Unit> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        return try {
            db.collection(Fs.CONVERSATIONS).document(conversationId)
                .update(mapOf("status" to ConversationStatus.ENDED.name, "endedByUserId" to id, "updatedAt" to now())).await()
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    override suspend fun toggleMute(conversationId: String): DataResult<Unit> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        return try {
            val snap = db.collection(Fs.CONVERSATIONS).document(conversationId).get().await()
            val conv = conversationFromDoc(snap)
            val op = if (id in conv.mutedByUserIds) FieldValue.arrayRemove(id) else FieldValue.arrayUnion(id)
            db.collection(Fs.CONVERSATIONS).document(conversationId).update("mutedByUserIds", op).await()
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    override suspend fun sendMessage(conversationId: String, body: String): DataResult<Unit> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        if (body.trim().isEmpty()) return fbError("پیام خالی است.")
        return try {
            val conv = conversationFromDoc(db.collection(Fs.CONVERSATIONS).document(conversationId).get().await())
            if (id !in conv.participantIds) return fbError("اجازهٔ دسترسی ندارید.")
            if (conv.status != ConversationStatus.ACCEPTED) return fbError("گفتگو فعال نیست.")
            val message = Message(id = "", conversationId = conversationId, senderId = id, body = body.trim(), createdAt = now())
            db.collection(Fs.MESSAGES).add(message.toMap()).await()
            db.collection(Fs.CONVERSATIONS).document(conversationId)
                .update(mapOf("lastMessagePreview" to body.trim().take(40), "updatedAt" to now())).await()
            conv.participantIds.firstOrNull { it != id }?.let {
                pushNotification(it, NotificationType.NEW_MESSAGE, "پیام جدید", "پیام جدید دارید.", conversationId)
            }
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    private suspend fun setConvStatus(
        conversationId: String, status: ConversationStatus, allowed: (Conversation, String) -> Boolean,
    ): DataResult<Unit> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        return try {
            val conv = conversationFromDoc(db.collection(Fs.CONVERSATIONS).document(conversationId).get().await())
            if (id !in conv.participantIds) return fbError("اجازهٔ دسترسی ندارید.")
            if (!allowed(conv, id)) return fbError("این عملیات مجاز نیست.")
            db.collection(Fs.CONVERSATIONS).document(conversationId)
                .update(mapOf("status" to status.name, "updatedAt" to now())).await()
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }
}

// ---------------------------------------------------------------------------------------
// Guides
// ---------------------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseGuideRepository : GuideRepository {

    private val db = firestore()

    override fun observeGuides(category: String?): Flow<List<GuideArticle>> =
        db.collection(Fs.GUIDES).asListFlow(::guideFromDoc).map { list ->
            list.filter { it.status == ContentStatus.PUBLISHED && (category.isNullOrBlank() || it.category == category) }
                .sortedByDescending { it.updatedAt }
        }

    override fun observeGuide(guideId: String): Flow<GuideArticle?> =
        db.collection(Fs.GUIDES).document(guideId).asDocFlow(::guideFromDoc)

    override fun observeSaved(): Flow<List<GuideArticle>> =
        authUidFlow().flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else db.collection(Fs.GUIDES).whereArrayContains("savedByUserIds", id).asListFlow(::guideFromDoc)
        }

    override suspend fun toggleSaved(guideId: String): DataResult<Unit> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        return try {
            val guide = guideFromDoc(db.collection(Fs.GUIDES).document(guideId).get().await())
            val op = if (id in guide.savedByUserIds) FieldValue.arrayRemove(id) else FieldValue.arrayUnion(id)
            db.collection(Fs.GUIDES).document(guideId).update("savedByUserIds", op).await()
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    override suspend fun upsertGuide(guide: GuideArticle): DataResult<Unit> = try {
        if (guide.id.isBlank()) {
            db.collection(Fs.GUIDES).add(guide.copy(createdAt = now(), updatedAt = now()).toMap()).await()
        } else {
            db.collection(Fs.GUIDES).document(guide.id).set(guide.copy(updatedAt = now()).toMap()).await()
        }
        DataResult.Success(Unit)
    } catch (e: Exception) { fbError(friendly(e)) }
}

// ---------------------------------------------------------------------------------------
// Reports
// ---------------------------------------------------------------------------------------

class FirebaseReportRepository : ReportRepository {

    private val db = firestore()

    override suspend fun createReport(contentType: ContentType, contentId: String, reason: ReportReason, note: String?): DataResult<Unit> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        return try {
            val report = Report(id = "", reporterId = id, contentType = contentType, contentId = contentId,
                reason = reason, note = note?.trim()?.ifEmpty { null }, status = ReportStatus.OPEN, createdAt = now(), updatedAt = now())
            db.collection(Fs.REPORTS).add(report.toMap()).await()
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    override fun observeOpenReports(): Flow<List<Report>> =
        db.collection(Fs.REPORTS).asListFlow(::reportFromDoc).map { list ->
            list.filter { it.status == ReportStatus.OPEN || it.status == ReportStatus.UNDER_REVIEW }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun resolveReport(reportId: String, newStatus: ReportStatus, resolutionNote: String): DataResult<Unit> {
        val adminId = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        return try {
            db.collection(Fs.REPORTS).document(reportId).update(mapOf(
                "status" to newStatus.name, "resolutionNote" to resolutionNote, "reviewedByAdminId" to adminId, "updatedAt" to now())).await()
            logAdminAction(adminId, AdminActionType.RESOLVE_REPORT, ContentType.USER, reportId, resolutionNote)
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }
}

// ---------------------------------------------------------------------------------------
// Notifications
// ---------------------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseNotificationRepository : NotificationRepository {

    private val db = firestore()

    override fun observeNotifications(): Flow<List<Notification>> =
        authUidFlow().flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else db.collection(Fs.NOTIFICATIONS).whereEqualTo("userId", id).asListFlow(::notificationFromDoc)
                .map { list -> list.sortedByDescending { it.createdAt } }
        }

    override fun observeUnreadCount(): Flow<Int> =
        observeNotifications().map { list -> list.count { !it.read } }

    override suspend fun markRead(notificationId: String): DataResult<Unit> = try {
        db.collection(Fs.NOTIFICATIONS).document(notificationId).update("read", true).await()
        DataResult.Success(Unit)
    } catch (e: Exception) { fbError(friendly(e)) }

    override suspend fun markAllRead(): DataResult<Unit> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        return try {
            db.collection(Fs.NOTIFICATIONS).whereEqualTo("userId", id).whereEqualTo("read", false).get().await()
                .documents.forEach { runCatching { it.reference.update("read", true).await() } }
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }
}

// ---------------------------------------------------------------------------------------
// Admin
// ---------------------------------------------------------------------------------------

class FirebaseAdminRepository : AdminRepository {

    private val db = firestore()

    override fun observeVerificationRequests(): Flow<List<VerificationRequest>> =
        db.collection(Fs.VERIFICATIONS).asListFlow(::verificationFromDoc).map { list ->
            list.sortedWith(compareBy<VerificationRequest> { it.status != VerificationStatus.PENDING }.thenByDescending { it.updatedAt })
        }

    override fun observeAdminActions(): Flow<List<AdminAction>> =
        db.collection(Fs.ADMIN_ACTIONS).asListFlow(::adminActionFromDoc).map { list -> list.sortedByDescending { it.createdAt } }

    override suspend fun viewCredential(requestId: String): DataResult<VerificationRequest> {
        val adminId = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        return try {
            val snap = db.collection(Fs.VERIFICATIONS).document(requestId).get().await()
            if (!snap.exists()) return fbError("درخواست یافت نشد.")
            val request = verificationFromDoc(snap)
            logAdminAction(adminId, AdminActionType.VIEW_CREDENTIAL, ContentType.USER, request.userId, "مشاهدهٔ مدرک برای بررسی")
            DataResult.Success(request)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    override suspend fun reviewVerification(requestId: String, decision: VerificationStatus, reason: String): DataResult<Unit> {
        val adminId = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        if (reason.trim().isEmpty()) return fbError("ثبت دلیل تصمیم الزامی است.")
        return try {
            val snap = db.collection(Fs.VERIFICATIONS).document(requestId).get().await()
            if (!snap.exists()) return fbError("درخواست یافت نشد.")
            val request = verificationFromDoc(snap)
            db.collection(Fs.VERIFICATIONS).document(requestId).update(mapOf(
                "status" to decision.name, "reviewerNote" to reason.trim(), "reviewedByAdminId" to adminId, "updatedAt" to now())).await()
            db.collection(Fs.PROFESSIONALS).document(request.userId)
                .update(mapOf("verificationStatus" to decision.name, "updatedAt" to now())).await()
            val action = when (decision) {
                VerificationStatus.VERIFIED -> AdminActionType.VERIFY_PROFESSIONAL
                VerificationStatus.REJECTED -> AdminActionType.REJECT_PROFESSIONAL
                else -> AdminActionType.REQUEST_MORE_INFO
            }
            logAdminAction(adminId, action, ContentType.USER, request.userId, reason.trim())
            pushNotification(request.userId, NotificationType.VERIFICATION_RESULT, "نتیجهٔ بررسی تخصص",
                "وضعیت بررسی تخصص شما به «${decision.label}» تغییر کرد.")
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    override suspend fun hideContent(type: ContentType, contentId: String, reason: String): DataResult<Unit> =
        setContentStatus(type, contentId, ContentStatus.HIDDEN, AdminActionType.HIDE_CONTENT, reason)

    override suspend fun deleteContent(type: ContentType, contentId: String, reason: String): DataResult<Unit> =
        setContentStatus(type, contentId, ContentStatus.REMOVED, AdminActionType.DELETE_CONTENT, reason)

    private suspend fun setContentStatus(type: ContentType, contentId: String, status: ContentStatus, action: AdminActionType, reason: String): DataResult<Unit> {
        val adminId = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        if (reason.trim().isEmpty()) return fbError("ثبت دلیل تصمیم الزامی است.")
        val collection = when (type) {
            ContentType.QUESTION -> Fs.QUESTIONS
            ContentType.ANSWER -> Fs.ANSWERS
            ContentType.GROUP_POST -> Fs.POSTS
            ContentType.GUIDE -> Fs.GUIDES
            else -> return fbError("این نوع محتوا پشتیبانی نمی‌شود.")
        }
        return try {
            db.collection(collection).document(contentId).update(mapOf("status" to status.name)).await()
            logAdminAction(adminId, action, type, contentId, reason.trim())
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    override suspend fun setAccountStatus(userId: String, status: AccountStatus, reason: String): DataResult<Unit> {
        val adminId = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        if (reason.trim().isEmpty()) return fbError("ثبت دلیل تصمیم الزامی است.")
        val action = if (status == AccountStatus.BLOCKED) AdminActionType.BLOCK_ACCOUNT else AdminActionType.LIMIT_ACCOUNT
        return try {
            db.collection(Fs.USERS).document(userId).update(mapOf("status" to status.name, "updatedAt" to now())).await()
            logAdminAction(adminId, action, ContentType.USER, userId, reason.trim())
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }
}

/** Appends an audited admin action. */
internal suspend fun logAdminAction(adminId: String, type: AdminActionType, targetType: ContentType, targetId: String, reason: String) {
    runCatching {
        val action = AdminAction(id = "", adminId = adminId, actionType = type, targetType = targetType, targetId = targetId, reason = reason, createdAt = now())
        firestore().collection(Fs.ADMIN_ACTIONS).add(action.toMap()).await()
    }
}
