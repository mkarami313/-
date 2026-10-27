package com.hamraheteyf.app.data.repository.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.ProfessionalDirectoryItem
import com.hamraheteyf.app.data.repository.ProfessionalFilter
import com.hamraheteyf.app.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

private fun ferr(message: String): DataResult<Nothing> = DataResult.Error(message)
private const val NOT_SIGNED_IN = "ابتدا وارد حساب خود شوید."

/**
 * Users, professional profiles, verification requests and private child profiles in Firestore.
 * The professional profile document id equals the owner's uid (one profile per user).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseUserRepository : UserRepository {

    private val db = firestore()
    private fun uid(): String? = currentUid()

    override fun observeUser(userId: String): Flow<User?> =
        db.collection(Fs.USERS).document(userId).asDocFlow(::userFromDoc)

    override fun observeCurrentUser(): Flow<User?> =
        authUidFlow().flatMapLatest { id -> if (id == null) flowOf(null) else observeUser(id) }

    override fun observeUsers(): Flow<List<User>> =
        db.collection(Fs.USERS).asListFlow(::userFromDoc)

    override fun observeAllProfessionals(): Flow<List<ProfessionalProfile>> =
        db.collection(Fs.PROFESSIONALS).asListFlow(::professionalFromDoc)

    override suspend fun updateProfile(displayName: String, city: String?, bio: String?, photoUrl: String?): DataResult<Unit> {
        val id = uid() ?: return ferr(NOT_SIGNED_IN)
        if (displayName.trim().isEmpty()) return ferr("نام نمایشی را وارد کنید.")
        return runUpdate(Fs.USERS, id, mapOf(
            "displayName" to displayName.trim(),
            "city" to city?.trim()?.ifEmpty { null },
            "bio" to bio?.trim()?.ifEmpty { null },
            "photoUrl" to photoUrl,
            "updatedAt" to now(),
        ))
    }

    override suspend fun updatePrivacy(settings: PrivacySettings): DataResult<Unit> {
        val id = uid() ?: return ferr(NOT_SIGNED_IN)
        return runUpdate(Fs.USERS, id, mapOf(
            "privacy" to mapOf(
                "showCity" to settings.showCity,
                "showBio" to settings.showBio,
                "allowMessageRequests" to settings.allowMessageRequests,
            ),
            "updatedAt" to now(),
        ))
    }

    override suspend fun updateNotificationSettings(settings: NotificationSettings): DataResult<Unit> {
        val id = uid() ?: return ferr(NOT_SIGNED_IN)
        return runUpdate(Fs.USERS, id, mapOf(
            "notificationSettings" to mapOf(
                "questionAnswered" to settings.questionAnswered,
                "newMessage" to settings.newMessage,
                "groupJoinRequest" to settings.groupJoinRequest,
                "groupReply" to settings.groupReply,
                "verificationResult" to settings.verificationResult,
                "safetyNotice" to settings.safetyNotice,
            ),
            "updatedAt" to now(),
        ))
    }

    override suspend fun blockUser(targetUserId: String): DataResult<Unit> {
        val id = uid() ?: return ferr(NOT_SIGNED_IN)
        if (id == targetUserId) return ferr("نمی‌توانید خودتان را مسدود کنید.")
        return runUpdate(Fs.USERS, id, mapOf("blockedUserIds" to FieldValue.arrayUnion(targetUserId)))
    }

    override suspend fun unblockUser(targetUserId: String): DataResult<Unit> {
        val id = uid() ?: return ferr(NOT_SIGNED_IN)
        return runUpdate(Fs.USERS, id, mapOf("blockedUserIds" to FieldValue.arrayRemove(targetUserId)))
    }

    override fun observeProfessional(userId: String): Flow<ProfessionalProfile?> =
        db.collection(Fs.PROFESSIONALS).document(userId).asDocFlow(::professionalFromDoc)

    override suspend fun upsertProfessionalProfile(
        title: String, workAreas: List<String>, city: String?, offersOnline: Boolean, experience: String?,
    ): DataResult<Unit> {
        val id = uid() ?: return ferr(NOT_SIGNED_IN)
        if (title.trim().isEmpty()) return ferr("عنوان تخصص را وارد کنید.")
        return try {
            val ref = db.collection(Fs.PROFESSIONALS).document(id)
            val existing = ref.get().await()
            val currentStatus = if (existing.exists()) existing.getString("verificationStatus") else VerificationStatus.NONE.name
            ref.set(mapOf(
                "userId" to id,
                "title" to title.trim(),
                "workAreas" to workAreas,
                "city" to city?.trim()?.ifEmpty { null },
                "offersOnline" to offersOnline,
                "experience" to experience?.trim()?.ifEmpty { null },
                // Preserve verification status; only admins change it.
                "verificationStatus" to (currentStatus ?: VerificationStatus.NONE.name),
                "updatedAt" to now(),
                "createdAt" to (existing.getLong("createdAt") ?: now()),
            )).await()
            DataResult.Success(Unit)
        } catch (e: Exception) {
            ferr(friendly(e))
        }
    }

    override fun searchProfessionals(filter: ProfessionalFilter): Flow<List<ProfessionalDirectoryItem>> =
        combine(observeUsers(), observeAllProfessionals()) { users, professionals ->
            professionals.mapNotNull { prof ->
                val user = users.find { it.id == prof.userId } ?: return@mapNotNull null
                if (user.status == AccountStatus.BLOCKED) return@mapNotNull null
                ProfessionalDirectoryItem(user, prof, resolveAuthorBadge(user.accountType, prof.verificationStatus))
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

    override suspend fun submitVerificationRequest(professionalTitle: String, licenseNumber: String?, credentialNote: String?): DataResult<Unit> {
        val id = uid() ?: return ferr(NOT_SIGNED_IN)
        if (professionalTitle.trim().isEmpty()) return ferr("عنوان تخصص را وارد کنید.")
        return try {
            val request = VerificationRequest(
                id = "", userId = id, professionalTitle = professionalTitle.trim(),
                licenseNumber = licenseNumber?.trim()?.ifEmpty { null },
                credentialNote = credentialNote?.trim()?.ifEmpty { null },
                status = VerificationStatus.PENDING, createdAt = now(), updatedAt = now(),
            )
            db.collection(Fs.VERIFICATIONS).add(request.toMap()).await()
            // Reflect "pending" on the public profile WITHOUT granting the verified badge.
            db.collection(Fs.PROFESSIONALS).document(id).set(
                mapOf("userId" to id, "title" to professionalTitle.trim(),
                    "verificationStatus" to VerificationStatus.PENDING.name, "updatedAt" to now()),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()
            DataResult.Success(Unit)
        } catch (e: Exception) {
            ferr(friendly(e))
        }
    }

    override fun observeMyVerification(): Flow<VerificationRequest?> =
        authUidFlow().flatMapLatest { id ->
            if (id == null) flowOf(null)
            else db.collection(Fs.VERIFICATIONS).whereEqualTo("userId", id)
                .asListFlow(::verificationFromDoc).map { list -> list.maxByOrNull { it.updatedAt } }
        }

    override fun observeChildren(): Flow<List<ChildProfilePrivate>> =
        authUidFlow().flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else db.collection(Fs.CHILDREN).whereEqualTo("familyUserId", id).asListFlow(::childFromDoc)
        }

    override suspend fun addChild(nickname: String?, ageRange: AgeRange, interests: List<String>): DataResult<Unit> {
        val id = uid() ?: return ferr(NOT_SIGNED_IN)
        return try {
            val child = ChildProfilePrivate(
                id = "", familyUserId = id, nickname = nickname?.trim()?.ifEmpty { null },
                ageRange = ageRange, interests = interests, isPublic = false, createdAt = now(), updatedAt = now(),
            )
            db.collection(Fs.CHILDREN).add(child.toMap()).await()
            DataResult.Success(Unit)
        } catch (e: Exception) { ferr(friendly(e)) }
    }

    override suspend fun deleteChild(childId: String): DataResult<Unit> = try {
        db.collection(Fs.CHILDREN).document(childId).delete().await()
        DataResult.Success(Unit)
    } catch (e: Exception) { ferr(friendly(e)) }

    override suspend fun exportMyData(): DataResult<String> {
        val id = uid() ?: return ferr(NOT_SIGNED_IN)
        return try {
            val user = userFromDoc(db.collection(Fs.USERS).document(id).get().await())
            val questions = db.collection(Fs.QUESTIONS).whereEqualTo("authorId", id).get().await().size()
            val answers = db.collection(Fs.ANSWERS).whereEqualTo("authorId", id).get().await().size()
            val children = db.collection(Fs.CHILDREN).whereEqualTo("familyUserId", id).get().await().size()
            val sb = StringBuilder()
            sb.appendLine("# نسخهٔ اطلاعات حساب شما")
            sb.appendLine("نام نمایشی: ${user.displayName}")
            sb.appendLine("نوع حساب: ${user.accountType.label}")
            sb.appendLine("ایمیل: ${user.email ?: "-"}")
            sb.appendLine("تعداد پرسش‌ها: $questions")
            sb.appendLine("تعداد پاسخ‌ها: $answers")
            sb.appendLine("تعداد پروفایل‌های کودک: $children")
            DataResult.Success(sb.toString())
        } catch (e: Exception) { ferr(friendly(e)) }
    }

    private suspend fun runUpdate(collection: String, id: String, data: Map<String, Any?>): DataResult<Unit> = try {
        db.collection(collection).document(id).update(data).await()
        DataResult.Success(Unit)
    } catch (e: Exception) { ferr(friendly(e)) }
}

internal fun now(): Long = System.currentTimeMillis()

internal fun friendly(e: Exception): String {
    val msg = e.message ?: return "خطایی رخ داد. دوباره تلاش کنید."
    return when {
        msg.contains("PERMISSION_DENIED", true) || msg.contains("permission", true) ->
            "اجازهٔ این عملیات را ندارید."
        msg.contains("network", true) || msg.contains("UNAVAILABLE", true) ->
            "اتصال اینترنت را بررسی کنید."
        else -> "خطایی رخ داد. دوباره تلاش کنید."
    }
}
