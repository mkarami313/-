package com.hamraheteyf.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.AuthRepository
import com.hamraheteyf.app.data.repository.ExportFile
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

    /** Runs [block], toasts the outcome and reports success to [onDone]; [busy] is true meanwhile. */
    private fun runBusy(success: String?, onDone: (Boolean) -> Unit = {}, block: suspend () -> DataResult<*>) = viewModelScope.launch {
        busy.value = true
        val ok = when (val r = block()) {
            is DataResult.Success -> { success?.let { message.value = it }; true }
            is DataResult.Error -> { message.value = r.message; false }
        }
        busy.value = false
        onDone(ok)
    }

    val busy = MutableStateFlow(false)

    /** [photoId]: null keeps the current avatar, "" removes it, otherwise a freshly uploaded file id. */
    fun updateProfile(name: String, city: String, bio: String, photoId: String? = null, onDone: (Boolean) -> Unit = {}) {
        if (name.isBlank()) { message.value = "نام نمایشی را وارد کنید."; onDone(false); return }
        runBusy("پروفایل به‌روزرسانی شد.", onDone) { userRepo.updateProfileWithPhoto(name.trim(), city.trim(), bio.trim(), photoId) }
    }

    fun setMessage(text: String) { message.value = text }

    /** Like [runBusy] but hands the error text to [onResult] (null = success) so a dialog can show it inline. */
    private fun runBusyInline(block: suspend () -> DataResult<*>, onResult: (String?) -> Unit) = viewModelScope.launch {
        busy.value = true
        val err = (block() as? DataResult.Error)?.message
        busy.value = false
        onResult(err)
    }

    fun changePassword(current: String, new: String, onResult: (String?) -> Unit) =
        runBusyInline({ auth.changePassword(current, new) }) { err ->
            if (err == null) message.value = "رمز عبور تغییر کرد؛ دستگاه‌های دیگر از حساب خارج شدند."
            onResult(err)
        }

    fun updateChild(id: String, nickname: String, ageRange: AgeRange, interests: List<String>, notes: String, onDone: (Boolean) -> Unit = {}) =
        runBusy("ذخیره شد.", onDone) { userRepo.updateChild(id, nickname.trim().ifEmpty { null }, ageRange, interests, notes.trim()) }

    fun updatePrivacy(settings: PrivacySettings) = run { userRepo.updatePrivacy(settings) }
    fun updateNotificationSettings(settings: NotificationSettings) = run { userRepo.updateNotificationSettings(settings) }

    fun upsertProfessional(
        title: String, areas: List<String>, city: String, online: Boolean, experience: String,
        photoId: String? = null, fee: String? = null, hours: String? = null,
    ) = run("پروفایل تخصصی ذخیره شد.") {
        userRepo.upsertProfessionalProfile(title, areas, city, online, experience, photoId, fee, hours)
    }

    fun submitVerification(
        title: String, license: String, note: String,
        requestedType: AccountType? = null, documentRefs: List<String> = emptyList(),
    ) = run("درخواست تأیید ثبت شد و پس از بررسی مدیر پاسخ داده می‌شود.") {
        userRepo.submitVerificationRequest(title, license, note, requestedType, documentRefs)
    }

    fun addChild(nickname: String, ageRange: AgeRange, interests: List<String>, onDone: (Boolean) -> Unit = {}) =
        runBusy("ثبت شد.", onDone) { userRepo.addChild(nickname, ageRange, interests) }

    fun deleteChild(id: String) = run("حذف شد.") { userRepo.deleteChild(id) }

    fun unblock(userId: String) = run("رفع مسدودیت انجام شد.") { userRepo.unblockUser(userId) }

    val exportFile = MutableStateFlow<ExportFile?>(null)
    fun clearExportFile() { exportFile.value = null }

    fun exportMyData() = viewModelScope.launch {
        busy.value = true
        when (val r = userRepo.exportMyDataFile()) {
            is DataResult.Success -> exportFile.value = r.data
            is DataResult.Error -> message.value = r.message
        }
        busy.value = false
    }

    fun logout() = auth.logout()

    fun deleteAccount(password: String, onResult: (String?) -> Unit) = runBusyInline({ auth.deleteAccount(password) }, onResult)
}
