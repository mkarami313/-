package com.hamraheteyf.app.data.repository.fake

import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * In-memory implementations of every repository, sharing a single [FakeStore].
 *
 * These make the app fully runnable with no backend (DEMO mode) and are also the test
 * fixtures. Access rules (who can read/write what) are enforced here exactly as the real
 * backend enforces them via security rules — see backend/firestore.rules.
 */

/** Sentinel used to anonymize content authored by a user who deleted their account. */
const val DELETED_USER_ID = "u_deleted"
const val NOT_SIGNED_IN = "ابتدا وارد حساب خود شوید."

internal fun err(message: String): DataResult<Nothing> = DataResult.Error(message)

/** Appends a notification, honoring the recipient's per-category toggles. */
internal fun Db.addNotification(
    store: FakeStore,
    userId: String,
    type: NotificationType,
    title: String,
    body: String,
    relatedId: String? = null,
): Db {
    val recipient = users.find { it.id == userId } ?: return this
    val enabled = when (type) {
        NotificationType.QUESTION_ANSWERED -> recipient.notificationSettings.questionAnswered
        NotificationType.NEW_MESSAGE -> recipient.notificationSettings.newMessage
        NotificationType.GROUP_JOIN_REQUEST -> recipient.notificationSettings.groupJoinRequest
        NotificationType.GROUP_REPLY -> recipient.notificationSettings.groupReply
        NotificationType.VERIFICATION_RESULT -> recipient.notificationSettings.verificationResult
        NotificationType.SAFETY_NOTICE -> recipient.notificationSettings.safetyNotice
        NotificationType.DONATION -> true
        NotificationType.BOOKING -> true
    }
    if (!enabled) return this
    val n = Notification(
        id = store.newId("n"), userId = userId, type = type, title = title,
        body = body, relatedId = relatedId, read = false, createdAt = store.now(),
    )
    return copy(notifications = notifications + n)
}

// ---------------------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------------------

class FakeAuthRepository(private val store: FakeStore) : AuthRepository {

    override val currentUserId: StateFlow<String?> get() = store.currentUserId

    override suspend fun register(
        displayName: String,
        email: String,
        password: String,
        accountType: AccountType,
        acceptedTerms: Boolean,
    ): DataResult<User> {
        val name = displayName.trim()
        val mail = email.trim().lowercase()
        if (name.isEmpty()) return err("نام نمایشی را وارد کنید.")
        if (!mail.contains("@") || mail.length < 5) return err("ایمیل معتبر وارد کنید.")
        if (password.length < 6) return err("رمز عبور باید حداقل ۶ نویسه باشد.")
        if (!acceptedTerms) return err("برای ادامه، قوانین و حریم خصوصی را بپذیرید.")
        if (store.hasEmail(mail)) return err("این ایمیل قبلاً ثبت شده است.")

        val user = User(
            id = store.newId("u"),
            displayName = name,
            accountType = accountType,
            email = mail,
            status = AccountStatus.ACTIVE,
            joinedAt = store.now(),
            updatedAt = store.now(),
        )
        store.setPassword(mail, password)
        store.update { it.copy(users = it.users + user) }
        store.currentUserId.value = user.id
        return DataResult.Success(user)
    }

    override suspend fun login(email: String, password: String): DataResult<User> {
        val mail = email.trim().lowercase()
        if (!store.checkPassword(mail, password)) return err("ایمیل یا رمز عبور نادرست است.")
        val user = store.state.value.users.find { it.email?.lowercase() == mail }
            ?: return err("حساب یافت نشد.")
        if (user.status == AccountStatus.BLOCKED) return err("این حساب مسدود شده است.")
        store.currentUserId.value = user.id
        return DataResult.Success(user)
    }

    override fun logout() {
        store.currentUserId.value = null
    }

    override suspend fun sendPasswordReset(email: String): DataResult<Unit> {
        // Always report success to avoid revealing whether an email is registered.
        return DataResult.Success(Unit)
    }

    override suspend fun deleteAccount(): DataResult<Unit> {
        val uid = store.currentUserId.value ?: return err(NOT_SIGNED_IN)
        val user = store.state.value.users.find { it.id == uid } ?: return err("حساب یافت نشد.")
        user.email?.let { store.removePassword(it) }

        store.update { db ->
            db.copy(
                // Delete personal data outright.
                users = db.users.filterNot { it.id == uid },
                professionals = db.professionals.filterNot { it.userId == uid },
                verifications = db.verifications.filterNot { it.userId == uid },
                children = db.children.filterNot { it.familyUserId == uid },
                notifications = db.notifications.filterNot { it.userId == uid },
                memberships = db.memberships.filterNot { it.userId == uid },
                conversations = db.conversations.filterNot { uid in it.participantIds },
                messages = db.messages.filterNot { msg ->
                    db.conversations.any { uid in it.participantIds && it.id == msg.conversationId }
                },
                // Anonymize public contributions so threads stay readable (documented policy).
                questions = db.questions.map {
                    if (it.authorId == uid) it.copy(authorId = DELETED_USER_ID, isAnonymous = true) else it
                },
                answers = db.answers.map {
                    if (it.authorId == uid) it.copy(authorId = DELETED_USER_ID) else it
                },
                posts = db.posts.map {
                    if (it.authorId == uid) it.copy(authorId = DELETED_USER_ID) else it
                },
            )
        }
        store.currentUserId.value = null
        return DataResult.Success(Unit)
    }
}

// ---------------------------------------------------------------------------------------
// Users / professionals / children / verification
// ---------------------------------------------------------------------------------------

class FakeUserRepository(private val store: FakeStore) : UserRepository {

    private fun uid(): String? = store.currentUserId.value

    override fun observeUser(userId: String): Flow<User?> =
        store.state.map { db -> db.users.find { it.id == userId } }

    override fun observeCurrentUser(): Flow<User?> =
        combine(store.currentUserId, store.state) { id, db -> db.users.find { it.id == id } }

    override fun observeUsers(): Flow<List<User>> = store.state.map { it.users }

    override fun observeAllProfessionals(): Flow<List<ProfessionalProfile>> = store.state.map { it.professionals }

    override suspend fun updateProfile(
        displayName: String, city: String?, bio: String?, photoUrl: String?,
    ): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        if (displayName.trim().isEmpty()) return err("نام نمایشی را وارد کنید.")
        store.update { db ->
            db.copy(users = db.users.map {
                if (it.id == id) it.copy(
                    displayName = displayName.trim(),
                    city = city?.trim()?.ifEmpty { null },
                    bio = bio?.trim()?.ifEmpty { null },
                    photoUrl = photoUrl, updatedAt = store.now(),
                ) else it
            })
        }
        return DataResult.Success(Unit)
    }

    override suspend fun updatePrivacy(settings: PrivacySettings): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        store.update { db -> db.copy(users = db.users.map { if (it.id == id) it.copy(privacy = settings, updatedAt = store.now()) else it }) }
        return DataResult.Success(Unit)
    }

    override suspend fun updateNotificationSettings(settings: NotificationSettings): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        store.update { db -> db.copy(users = db.users.map { if (it.id == id) it.copy(notificationSettings = settings, updatedAt = store.now()) else it }) }
        return DataResult.Success(Unit)
    }

    override suspend fun blockUser(targetUserId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        if (id == targetUserId) return err("نمی‌توانید خودتان را مسدود کنید.")
        store.update { db -> db.copy(users = db.users.map { if (it.id == id) it.copy(blockedUserIds = it.blockedUserIds + targetUserId) else it }) }
        return DataResult.Success(Unit)
    }

    override suspend fun unblockUser(targetUserId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        store.update { db -> db.copy(users = db.users.map { if (it.id == id) it.copy(blockedUserIds = it.blockedUserIds - targetUserId) else it }) }
        return DataResult.Success(Unit)
    }

    override fun observeProfessional(userId: String): Flow<ProfessionalProfile?> =
        store.state.map { db -> db.professionals.find { it.userId == userId } }

    override suspend fun upsertProfessionalProfile(
        title: String, workAreas: List<String>, city: String?, offersOnline: Boolean, experience: String?,
    ): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val user = store.state.value.users.find { it.id == id } ?: return err("حساب یافت نشد.")
        if (!user.accountType.canRequestVerification()) return err("این نوع حساب پروفایل تخصصی ندارد.")
        if (title.trim().isEmpty()) return err("عنوان تخصص را وارد کنید.")
        store.update { db ->
            val existing = db.professionals.find { it.userId == id }
            val updated = if (existing != null) {
                existing.copy(
                    title = title.trim(), workAreas = workAreas, city = city?.trim()?.ifEmpty { null },
                    offersOnline = offersOnline, experience = experience?.trim()?.ifEmpty { null },
                    updatedAt = store.now(),
                    // verificationStatus is intentionally preserved; only admins change it.
                )
            } else {
                ProfessionalProfile(
                    id = store.newId("p"), userId = id, title = title.trim(), workAreas = workAreas,
                    city = city?.trim()?.ifEmpty { null }, offersOnline = offersOnline,
                    experience = experience?.trim()?.ifEmpty { null },
                    verificationStatus = VerificationStatus.NONE,
                    createdAt = store.now(), updatedAt = store.now(),
                )
            }
            db.copy(professionals = db.professionals.filterNot { it.userId == id } + updated)
        }
        return DataResult.Success(Unit)
    }

    override fun searchProfessionals(filter: ProfessionalFilter): Flow<List<ProfessionalDirectoryItem>> =
        store.state.map { db ->
            db.professionals.mapNotNull { prof ->
                val user = db.users.find { it.id == prof.userId } ?: return@mapNotNull null
                if (user.status == AccountStatus.BLOCKED) return@mapNotNull null
                val badge = resolveAuthorBadge(user.accountType, prof.verificationStatus)
                ProfessionalDirectoryItem(user, prof, badge)
            }.filter { item ->
                val q = filter.query.trim()
                (q.isEmpty() || item.user.displayName.contains(q) || item.profile.title.contains(q) ||
                    item.profile.workAreas.any { it.contains(q) }) &&
                    (filter.city.isNullOrBlank() || item.profile.city?.contains(filter.city) == true) &&
                    (filter.specialty.isNullOrBlank() || item.profile.title.contains(filter.specialty) ||
                        item.profile.workAreas.any { it.contains(filter.specialty) }) &&
                    (!filter.onlineOnly || item.profile.offersOnline) &&
                    (!filter.verifiedOnly || item.profile.verificationStatus == VerificationStatus.VERIFIED)
            }.sortedByDescending { it.profile.verificationStatus == VerificationStatus.VERIFIED }
        }

    override suspend fun submitVerificationRequest(
        professionalTitle: String, licenseNumber: String?, credentialNote: String?,
    ): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val user = store.state.value.users.find { it.id == id } ?: return err("حساب یافت نشد.")
        if (!user.accountType.canRequestVerification()) return err("این نوع حساب امکان درخواست تأیید تخصص ندارد.")
        if (professionalTitle.trim().isEmpty()) return err("عنوان تخصص را وارد کنید.")
        store.update { db ->
            val request = VerificationRequest(
                id = store.newId("v"), userId = id, professionalTitle = professionalTitle.trim(),
                licenseNumber = licenseNumber?.trim()?.ifEmpty { null },
                credentialNote = credentialNote?.trim()?.ifEmpty { null },
                status = VerificationStatus.PENDING, createdAt = store.now(), updatedAt = store.now(),
            )
            // Reflect "pending" on the public profile WITHOUT granting the verified badge.
            val profiles = if (db.professionals.any { it.userId == id }) {
                db.professionals.map { if (it.userId == id) it.copy(verificationStatus = VerificationStatus.PENDING, updatedAt = store.now()) else it }
            } else {
                db.professionals + ProfessionalProfile(
                    id = store.newId("p"), userId = id, title = professionalTitle.trim(),
                    verificationStatus = VerificationStatus.PENDING, createdAt = store.now(), updatedAt = store.now(),
                )
            }
            db.copy(
                verifications = db.verifications.filterNot { it.userId == id && it.status == VerificationStatus.PENDING } + request,
                professionals = profiles,
            )
        }
        return DataResult.Success(Unit)
    }

    override fun observeMyVerification(): Flow<VerificationRequest?> =
        combine(store.currentUserId, store.state) { id, db ->
            db.verifications.filter { it.userId == id }.maxByOrNull { it.updatedAt }
        }

    override fun observeChildren(): Flow<List<ChildProfilePrivate>> =
        combine(store.currentUserId, store.state) { id, db ->
            if (id == null) emptyList() else db.children.filter { it.familyUserId == id }
        }

    override suspend fun addChild(nickname: String?, ageRange: AgeRange, interests: List<String>): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        store.update { db ->
            db.copy(children = db.children + ChildProfilePrivate(
                id = store.newId("c"), familyUserId = id, nickname = nickname?.trim()?.ifEmpty { null },
                ageRange = ageRange, interests = interests, isPublic = false,
                createdAt = store.now(), updatedAt = store.now(),
            ))
        }
        return DataResult.Success(Unit)
    }

    override suspend fun deleteChild(childId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        store.update { db -> db.copy(children = db.children.filterNot { it.id == childId && it.familyUserId == id }) }
        return DataResult.Success(Unit)
    }

    override suspend fun exportMyData(): DataResult<String> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val db = store.state.value
        val user = db.users.find { it.id == id } ?: return err("حساب یافت نشد.")
        val sb = StringBuilder()
        sb.appendLine("# نسخهٔ اطلاعات حساب شما (نمونه)")
        sb.appendLine("نام نمایشی: ${user.displayName}")
        sb.appendLine("نوع حساب: ${user.accountType.label}")
        sb.appendLine("ایمیل: ${user.email ?: "-"}")
        sb.appendLine("شهر: ${user.city ?: "-"}")
        sb.appendLine("تعداد پرسش‌ها: ${db.questions.count { it.authorId == id }}")
        sb.appendLine("تعداد پاسخ‌ها: ${db.answers.count { it.authorId == id }}")
        sb.appendLine("تعداد پروفایل‌های کودک: ${db.children.count { it.familyUserId == id }}")
        sb.appendLine("عضویت در گروه‌ها: ${db.memberships.count { it.userId == id && it.status == MembershipStatus.ACTIVE }}")
        return DataResult.Success(sb.toString())
    }
}

// ---------------------------------------------------------------------------------------
// Questions & answers
// ---------------------------------------------------------------------------------------

class FakeQuestionRepository(private val store: FakeStore) : QuestionRepository {

    private fun uid(): String? = store.currentUserId.value

    override fun observeQuestions(sort: QuestionSort, category: QuestionCategory?): Flow<List<Question>> =
        store.state.map { db ->
            db.questions
                .filter { it.status == ContentStatus.PUBLISHED }
                .filter { category == null || it.category == category }
                .let { list ->
                    when (sort) {
                        QuestionSort.NEWEST -> list.sortedByDescending { it.createdAt }
                        QuestionSort.MOST_HELPFUL -> list.sortedWith(
                            compareByDescending<Question> { it.helpfulCount }.thenByDescending { it.createdAt }
                        )
                    }
                }
        }

    override fun observeQuestion(questionId: String): Flow<Question?> =
        store.state.map { db -> db.questions.find { it.id == questionId && it.status != ContentStatus.REMOVED } }

    override fun observeAnswers(questionId: String): Flow<List<Answer>> =
        store.state.map { db ->
            db.answers
                .filter { it.questionId == questionId && it.status == ContentStatus.PUBLISHED }
                .sortedWith(compareByDescending<Answer> { it.helpfulCount }.thenByDescending { it.createdAt })
        }

    override suspend fun createQuestion(
        title: String, body: String, category: QuestionCategory, anonymous: Boolean,
    ): DataResult<String> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        if (title.trim().length < 3) return err("عنوان پرسش را کامل‌تر بنویسید.")
        if (body.trim().length < 5) return err("متن پرسش را کامل‌تر بنویسید.")
        val qId = store.newId("q")
        store.update { db ->
            db.copy(questions = db.questions + Question(
                id = qId, authorId = id, isAnonymous = anonymous, category = category,
                title = title.trim(), body = body.trim(), createdAt = store.now(), updatedAt = store.now(),
            ))
        }
        return DataResult.Success(qId)
    }

    override suspend fun editQuestion(questionId: String, title: String, body: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val q = store.state.value.questions.find { it.id == questionId } ?: return err("پرسش یافت نشد.")
        if (q.authorId != id) return err("فقط نویسنده می‌تواند پرسش را ویرایش کند.")
        store.update { db -> db.copy(questions = db.questions.map { if (it.id == questionId) it.copy(title = title.trim(), body = body.trim(), updatedAt = store.now()) else it }) }
        return DataResult.Success(Unit)
    }

    override suspend fun deleteQuestion(questionId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val q = store.state.value.questions.find { it.id == questionId } ?: return err("پرسش یافت نشد.")
        if (q.authorId != id) return err("فقط نویسنده می‌تواند پرسش را حذف کند.")
        store.update { db ->
            db.copy(
                questions = db.questions.filterNot { it.id == questionId },
                answers = db.answers.filterNot { it.questionId == questionId },
            )
        }
        return DataResult.Success(Unit)
    }

    override suspend fun setCommentsClosed(questionId: String, closed: Boolean): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val q = store.state.value.questions.find { it.id == questionId } ?: return err("پرسش یافت نشد.")
        val me = store.state.value.users.find { it.id == id }
        if (q.authorId != id && me?.isAdmin != true) return err("اجازهٔ این کار را ندارید.")
        store.update { db -> db.copy(questions = db.questions.map { if (it.id == questionId) it.copy(commentsClosed = closed, updatedAt = store.now()) else it }) }
        return DataResult.Success(Unit)
    }

    override suspend fun createAnswer(questionId: String, body: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val q = store.state.value.questions.find { it.id == questionId } ?: return err("پرسش یافت نشد.")
        if (q.commentsClosed) return err("امکان پاسخ به این پرسش بسته شده است.")
        if (body.trim().length < 2) return err("متن پاسخ را بنویسید.")
        store.update { db ->
            val answer = Answer(
                id = store.newId("a"), questionId = questionId, authorId = id, body = body.trim(),
                createdAt = store.now(), updatedAt = store.now(),
            )
            var next = db.copy(
                answers = db.answers + answer,
                questions = db.questions.map { if (it.id == questionId) it.copy(answerCount = it.answerCount + 1, updatedAt = store.now()) else it },
            )
            if (q.authorId != id && q.authorId != DELETED_USER_ID) {
                next = next.addNotification(
                    store, q.authorId, NotificationType.QUESTION_ANSWERED,
                    "پاسخ تازه", "به یکی از پرسش‌های شما پاسخ داده شد.", relatedId = questionId,
                )
            }
            next
        }
        return DataResult.Success(Unit)
    }

    override suspend fun toggleHelpful(answerId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        store.update { db ->
            val answer = db.answers.find { it.id == answerId } ?: return@update db
            val updatedAnswer = if (id in answer.helpfulByUserIds)
                answer.copy(helpfulByUserIds = answer.helpfulByUserIds - id)
            else answer.copy(helpfulByUserIds = answer.helpfulByUserIds + id)
            val answers = db.answers.map { if (it.id == answerId) updatedAnswer else it }
            // Keep the question's helpful tally in sync with the sum across its answers.
            val total = answers.filter { it.questionId == answer.questionId }.sumOf { it.helpfulCount }
            db.copy(
                answers = answers,
                questions = db.questions.map { if (it.id == answer.questionId) it.copy(helpfulCount = total) else it },
            )
        }
        return DataResult.Success(Unit)
    }
}

// ---------------------------------------------------------------------------------------
// Groups
// ---------------------------------------------------------------------------------------

class FakeGroupRepository(private val store: FakeStore) : GroupRepository {

    private fun uid(): String? = store.currentUserId.value

    private fun Db.isActiveMember(groupId: String, userId: String?): Boolean =
        userId != null && memberships.any { it.groupId == groupId && it.userId == userId && it.status == MembershipStatus.ACTIVE }

    private fun Db.canManage(groupId: String, userId: String?): Boolean =
        userId != null && memberships.any {
            it.groupId == groupId && it.userId == userId && it.status == MembershipStatus.ACTIVE &&
                (it.role == MembershipRole.OWNER || it.role == MembershipRole.MODERATOR)
        }

    override fun observeGroups(query: String): Flow<List<Group>> =
        combine(store.currentUserId, store.state) { id, db ->
            db.groups.filter { g ->
                g.visibility == GroupVisibility.PUBLIC || db.isActiveMember(g.id, id)
            }.filter { g ->
                val q = query.trim()
                q.isEmpty() || g.title.contains(q) || g.description.contains(q) ||
                    g.category?.contains(q) == true || g.city?.contains(q) == true
            }.sortedByDescending { it.updatedAt }
        }

    override fun observeGroup(groupId: String): Flow<Group?> =
        combine(store.currentUserId, store.state) { id, db ->
            val g = db.groups.find { it.id == groupId } ?: return@combine null
            if (g.visibility == GroupVisibility.PUBLIC || db.isActiveMember(groupId, id)) g else null
        }

    override fun observeMemberships(groupId: String): Flow<List<GroupMembership>> =
        store.state.map { db -> db.memberships.filter { it.groupId == groupId && it.status != MembershipStatus.REMOVED } }

    override fun observeMyMembership(groupId: String): Flow<GroupMembership?> =
        combine(store.currentUserId, store.state) { id, db ->
            db.memberships.find { it.groupId == groupId && it.userId == id && it.status != MembershipStatus.REMOVED }
        }

    override fun observeMyGroups(): Flow<List<Group>> =
        combine(store.currentUserId, store.state) { id, db ->
            if (id == null) emptyList()
            else db.groups.filter { db.isActiveMember(it.id, id) }.sortedByDescending { it.updatedAt }
        }

    override fun observePosts(groupId: String): Flow<List<GroupPost>> =
        combine(store.currentUserId, store.state) { id, db ->
            val g = db.groups.find { it.id == groupId } ?: return@combine emptyList()
            if (g.visibility == GroupVisibility.PRIVATE && !db.isActiveMember(groupId, id)) emptyList()
            else db.posts.filter { it.groupId == groupId && it.status == ContentStatus.PUBLISHED }.sortedByDescending { it.createdAt }
        }

    override suspend fun createGroup(
        title: String, description: String, category: String?, city: String?,
        visibility: GroupVisibility, rules: String?,
    ): DataResult<String> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        if (title.trim().length < 3) return err("عنوان گروه را کامل‌تر بنویسید.")
        val gId = store.newId("g")
        store.update { db ->
            db.copy(
                groups = db.groups + Group(
                    id = gId, ownerId = id, title = title.trim(), description = description.trim(),
                    category = category?.trim()?.ifEmpty { null }, city = city?.trim()?.ifEmpty { null },
                    visibility = visibility, rules = rules?.trim()?.ifEmpty { null }, memberCount = 1,
                    createdAt = store.now(), updatedAt = store.now(),
                ),
                memberships = db.memberships + GroupMembership(
                    id = store.newId("m"), groupId = gId, userId = id,
                    role = MembershipRole.OWNER, status = MembershipStatus.ACTIVE,
                    createdAt = store.now(), updatedAt = store.now(),
                ),
            )
        }
        return DataResult.Success(gId)
    }

    override suspend fun joinOrRequest(groupId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val g = store.state.value.groups.find { it.id == groupId } ?: return err("گروه یافت نشد.")
        val existing = store.state.value.memberships.find { it.groupId == groupId && it.userId == id }
        if (existing != null && existing.status != MembershipStatus.REMOVED) return err("شما قبلاً در این گروه عضو یا در انتظار تأیید هستید.")
        val newStatus = if (g.visibility == GroupVisibility.PUBLIC) MembershipStatus.ACTIVE else MembershipStatus.REQUESTED
        store.update { db ->
            var next = db.copy(
                memberships = db.memberships.filterNot { it.groupId == groupId && it.userId == id } + GroupMembership(
                    id = store.newId("m"), groupId = groupId, userId = id,
                    role = MembershipRole.MEMBER, status = newStatus,
                    createdAt = store.now(), updatedAt = store.now(),
                ),
                groups = db.groups.map { if (it.id == groupId && newStatus == MembershipStatus.ACTIVE) it.copy(memberCount = it.memberCount + 1, updatedAt = store.now()) else it },
            )
            if (newStatus == MembershipStatus.REQUESTED) {
                next = next.addNotification(store, g.ownerId, NotificationType.GROUP_JOIN_REQUEST, "درخواست عضویت", "یک درخواست عضویت تازه در گروه شما ثبت شد.", relatedId = groupId)
            }
            next
        }
        return DataResult.Success(Unit)
    }

    override suspend fun leave(groupId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val membership = store.state.value.memberships.find { it.groupId == groupId && it.userId == id && it.status != MembershipStatus.REMOVED }
            ?: return err("شما عضو این گروه نیستید.")
        if (membership.role == MembershipRole.OWNER) return err("مدیر گروه نمی‌تواند گروه را ترک کند؛ ابتدا مدیریت را واگذار کنید.")
        store.update { db ->
            db.copy(
                memberships = db.memberships.filterNot { it.id == membership.id },
                groups = db.groups.map { if (it.id == groupId && membership.status == MembershipStatus.ACTIVE) it.copy(memberCount = (it.memberCount - 1).coerceAtLeast(0), updatedAt = store.now()) else it },
            )
        }
        return DataResult.Success(Unit)
    }

    override suspend fun approveMember(groupId: String, userId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        if (!store.state.value.canManage(groupId, id)) return err("اجازهٔ مدیریت این گروه را ندارید.")
        store.update { db ->
            db.copy(
                memberships = db.memberships.map { if (it.groupId == groupId && it.userId == userId && it.status == MembershipStatus.REQUESTED) it.copy(status = MembershipStatus.ACTIVE, updatedAt = store.now()) else it },
                groups = db.groups.map { if (it.id == groupId) it.copy(memberCount = it.memberCount + 1, updatedAt = store.now()) else it },
            )
        }
        return DataResult.Success(Unit)
    }

    override suspend fun removeMember(groupId: String, userId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        if (!store.state.value.canManage(groupId, id)) return err("اجازهٔ مدیریت این گروه را ندارید.")
        val target = store.state.value.memberships.find { it.groupId == groupId && it.userId == userId }
        if (target?.role == MembershipRole.OWNER) return err("نمی‌توان مدیر گروه را حذف کرد.")
        store.update { db ->
            db.copy(
                memberships = db.memberships.filterNot { it.groupId == groupId && it.userId == userId },
                groups = db.groups.map { if (it.id == groupId && target?.status == MembershipStatus.ACTIVE) it.copy(memberCount = (it.memberCount - 1).coerceAtLeast(0), updatedAt = store.now()) else it },
            )
        }
        return DataResult.Success(Unit)
    }

    override suspend fun createPost(groupId: String, body: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        if (!store.state.value.isActiveMember(groupId, id)) return err("برای ارسال پست باید عضو فعال گروه باشید.")
        if (body.trim().length < 2) return err("متن پست را بنویسید.")
        store.update { db ->
            db.copy(
                posts = db.posts + GroupPost(id = store.newId("gp"), groupId = groupId, authorId = id, body = body.trim(), createdAt = store.now(), updatedAt = store.now()),
                groups = db.groups.map { if (it.id == groupId) it.copy(updatedAt = store.now()) else it },
            )
        }
        return DataResult.Success(Unit)
    }

    override suspend fun deletePost(postId: String): DataResult<Unit> {
        val id = uid() ?: return err(NOT_SIGNED_IN)
        val post = store.state.value.posts.find { it.id == postId } ?: return err("پست یافت نشد.")
        if (post.authorId != id && !store.state.value.canManage(post.groupId, id)) return err("اجازهٔ حذف این پست را ندارید.")
        store.update { db -> db.copy(posts = db.posts.filterNot { it.id == postId }) }
        return DataResult.Success(Unit)
    }
}
