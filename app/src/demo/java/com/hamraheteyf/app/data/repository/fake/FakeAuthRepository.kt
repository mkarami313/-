package com.hamraheteyf.app.data.repository.fake

import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Demo-only sign-in against [FakeStore]'s credential map. Not part of the release build (src/demo). */
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
