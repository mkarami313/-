package com.hamraheteyf.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.AuthRepository
import com.hamraheteyf.app.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Shared ViewModel for the account area (profile hub, settings and sub-screens). All
 * sub-screens read the same repositories, so a single VM keeps the logic in one place.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModel(
    private val auth: AuthRepository,
    private val userRepo: UserRepository,
) : ViewModel() {

    val message = MutableStateFlow<String?>(null)
    val exportText = MutableStateFlow<String?>(null)
    fun clearMessage() { message.value = null }
    fun clearExport() { exportText.value = null }

    val currentUser: StateFlow<User?> =
        userRepo.observeCurrentUser().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val professional: StateFlow<ProfessionalProfile?> =
        auth.currentUserId.flatMapLatest { id -> if (id == null) flowOf(null) else userRepo.observeProfessional(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val verification: StateFlow<VerificationRequest?> =
        userRepo.observeMyVerification().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val children: StateFlow<List<ChildProfilePrivate>> =
        userRepo.observeChildren().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedUsers: StateFlow<List<User>> =
        combine(userRepo.observeCurrentUser(), userRepo.observeUsers()) { me, users ->
            val ids = me?.blockedUserIds ?: emptySet()
            users.filter { it.id in ids }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun run(success: String? = null, block: suspend () -> DataResult<*>) = viewModelScope.launch {
        when (val r = block()) {
            is DataResult.Success -> success?.let { message.value = it }
            is DataResult.Error -> message.value = r.message
        }
    }

    fun updateProfile(name: String, city: String, bio: String) =
        run("پروفایل به‌روزرسانی شد.") { userRepo.updateProfile(name, city, bio, null) }

    fun updatePrivacy(settings: PrivacySettings) = run { userRepo.updatePrivacy(settings) }
    fun updateNotificationSettings(settings: NotificationSettings) = run { userRepo.updateNotificationSettings(settings) }

    fun upsertProfessional(title: String, areas: List<String>, city: String, online: Boolean, experience: String) =
        run("پروفایل تخصصی ذخیره شد.") { userRepo.upsertProfessionalProfile(title, areas, city, online, experience) }

    fun submitVerification(title: String, license: String, note: String) =
        run("درخواست تأیید ثبت شد و پس از بررسی مدیر پاسخ داده می‌شود.") {
            userRepo.submitVerificationRequest(title, license, note)
        }

    fun addChild(nickname: String, ageRange: AgeRange, interests: List<String>) =
        run("ثبت شد.") { userRepo.addChild(nickname, ageRange, interests) }

    fun deleteChild(id: String) = run("حذف شد.") { userRepo.deleteChild(id) }

    fun unblock(userId: String) = run("رفع مسدودیت انجام شد.") { userRepo.unblockUser(userId) }

    fun exportMyData() = viewModelScope.launch {
        when (val r = userRepo.exportMyData()) {
            is DataResult.Success -> exportText.value = r.data
            is DataResult.Error -> message.value = r.message
        }
    }

    fun logout() = auth.logout()

    fun deleteAccount() = viewModelScope.launch {
        when (val r = auth.deleteAccount()) {
            is DataResult.Success -> { /* root switches to auth automatically */ }
            is DataResult.Error -> message.value = r.message
        }
    }
}
