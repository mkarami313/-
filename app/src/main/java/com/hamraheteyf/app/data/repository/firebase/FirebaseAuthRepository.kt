package com.hamraheteyf.app.data.repository.firebase

import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.AuthRepository
import com.hamraheteyf.app.data.repository.fake.DELETED_USER_ID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private fun authError(message: String): DataResult<Nothing> = DataResult.Error(message)

/** Firebase Authentication + the user profile document in Firestore. */
class FirebaseAuthRepository : AuthRepository {

    private val auth = firebaseAuth()
    private val db = firestore()

    private val _currentUserId = MutableStateFlow(auth.currentUser?.uid)
    override val currentUserId: StateFlow<String?> = _currentUserId

    init {
        auth.addAuthStateListener { _currentUserId.value = it.currentUser?.uid }
    }

    override suspend fun register(
        displayName: String,
        email: String,
        password: String,
        accountType: AccountType,
        acceptedTerms: Boolean,
    ): DataResult<User> {
        val name = displayName.trim()
        val mail = email.trim()
        if (name.isEmpty()) return authError("نام نمایشی را وارد کنید.")
        if (!mail.contains("@")) return authError("ایمیل معتبر وارد کنید.")
        if (password.length < 6) return authError("رمز عبور باید حداقل ۶ نویسه باشد.")
        if (!acceptedTerms) return authError("برای ادامه، قوانین و حریم خصوصی را بپذیرید.")
        val result = try {
            auth.createUserWithEmailAndPassword(mail, password).await()
        } catch (e: Exception) {
            return authError(friendlyAuthError(e, "ایجاد حساب ناموفق بود."))
        }
        val uid = result.user?.uid ?: return authError("ایجاد حساب ناموفق بود.")
        val now = System.currentTimeMillis()
        val user = User(
            id = uid, displayName = name, accountType = accountType, email = mail,
            status = AccountStatus.ACTIVE, joinedAt = now, updatedAt = now,
        )
        return try {
            // NonCancellable is critical: creating the account auto-signs-in, which immediately
            // navigates away and cancels this coroutine's scope. Without this, the profile write
            // would be cancelled mid-flight and never reach Firestore.
            withContext(NonCancellable) {
                db.collection(Fs.USERS).document(uid).set(user.toMap()).await()
            }
            DataResult.Success(user)
        } catch (e: Exception) {
            // The auth account was created but the profile write failed: roll back so the user
            // is not left signed-in without a profile, and can retry cleanly.
            runCatching { auth.currentUser?.delete()?.await() }
            auth.signOut()
            authError("ثبت‌نام کامل نشد (ذخیرهٔ پروفایل): " + friendlyAuthError(e, "دوباره تلاش کنید."))
        }
    }

    override suspend fun login(email: String, password: String): DataResult<User> {
        val result = try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
        } catch (e: Exception) {
            return authError(friendlyAuthError(e, "ایمیل یا رمز عبور نادرست است."))
        }
        val uid = result.user?.uid ?: return authError("ورود ناموفق بود.")
        return try {
            val snapshot = db.collection(Fs.USERS).document(uid).get().await()
            val user = if (snapshot.exists()) userFromDoc(snapshot) else ensureUserDoc(uid, result.user?.email)
            when {
                user.status == AccountStatus.BLOCKED -> {
                    auth.signOut()
                    authError("این حساب مسدود شده است.")
                }
                else -> DataResult.Success(user)
            }
        } catch (e: Exception) {
            authError(friendlyAuthError(e, "ورود ناموفق بود. اتصال اینترنت را بررسی کنید."))
        }
    }

    /**
     * Self-heal: creates a minimal profile document when an authenticated user has none
     * (e.g. an interrupted sign-up). The user can complete their profile from Settings.
     */
    private suspend fun ensureUserDoc(uid: String, email: String?): User {
        val now = System.currentTimeMillis()
        val user = User(
            id = uid,
            displayName = email?.substringBefore("@")?.ifBlank { null } ?: "کاربر",
            accountType = AccountType.FAMILY,
            email = email,
            status = AccountStatus.ACTIVE,
            joinedAt = now, updatedAt = now,
        )
        // NonCancellable: login success also navigates away and cancels this scope; the
        // self-heal write must still complete.
        withContext(NonCancellable) {
            db.collection(Fs.USERS).document(uid).set(user.toMap()).await()
        }
        return user
    }

    override fun logout() {
        auth.signOut()
    }

    override suspend fun sendPasswordReset(email: String): DataResult<Unit> = try {
        auth.sendPasswordResetEmail(email.trim()).await()
        DataResult.Success(Unit)
    } catch (e: Exception) {
        // Report success regardless, to avoid revealing whether an email is registered.
        DataResult.Success(Unit)
    }

    override suspend fun deleteAccount(): DataResult<Unit> {
        val uid = auth.currentUser?.uid ?: return authError("ابتدا وارد حساب خود شوید.")
        return try {
            // Best-effort client-side cleanup of personal data. For complete/guaranteed
            // deletion of dependent data, a Cloud Function on user delete is recommended.
            suspend fun deleteWhere(collection: String, field: String) {
                db.collection(collection).whereEqualTo(field, uid).get().await()
                    .documents.forEach { runCatching { it.reference.delete().await() } }
            }
            suspend fun anonymizeAuthored(collection: String, extra: Map<String, Any?> = emptyMap()) {
                db.collection(collection).whereEqualTo("authorId", uid).get().await()
                    .documents.forEach {
                        runCatching { it.reference.update(mapOf("authorId" to DELETED_USER_ID) + extra).await() }
                    }
            }
            deleteWhere(Fs.CHILDREN, "familyUserId")
            deleteWhere(Fs.PROFESSIONALS, "userId")
            deleteWhere(Fs.VERIFICATIONS, "userId")
            deleteWhere(Fs.MEMBERSHIPS, "userId")
            deleteWhere(Fs.NOTIFICATIONS, "userId")
            anonymizeAuthored(Fs.QUESTIONS, mapOf("isAnonymous" to true))
            anonymizeAuthored(Fs.ANSWERS)
            anonymizeAuthored(Fs.POSTS)
            db.collection(Fs.USERS).document(uid).delete().await()
            auth.currentUser?.delete()?.await()
            DataResult.Success(Unit)
        } catch (e: Exception) {
            authError("حذف حساب کامل نشد. ممکن است لازم باشد دوباره وارد شوید و تلاش کنید.")
        }
    }
}

/** Maps common Firebase exceptions to friendly Persian messages. */
private fun friendlyAuthError(e: Exception, fallback: String): String {
    val msg = e.message ?: return fallback
    return when {
        msg.contains("already in use", true) -> "این ایمیل قبلاً ثبت شده است."
        msg.contains("badly formatted", true) -> "ایمیل معتبر وارد کنید."
        msg.contains("password is invalid", true) || msg.contains("INVALID_LOGIN", true) -> "ایمیل یا رمز عبور نادرست است."
        msg.contains("no user record", true) -> "حساب یافت نشد."
        msg.contains("network", true) -> "اتصال اینترنت را بررسی کنید."
        else -> fallback
    }
}
