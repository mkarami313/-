package com.hamraheteyf.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.AdminRepository
import com.hamraheteyf.app.data.repository.ReportRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AdminViewModel(
    private val adminRepo: AdminRepository,
    private val reportRepo: ReportRepository,
) : ViewModel() {

    val message = MutableStateFlow<String?>(null)
    val credentialDialog = MutableStateFlow<VerificationRequest?>(null)
    fun clearMessage() { message.value = null }
    fun clearCredentialDialog() { credentialDialog.value = null }

    val verificationRequests: StateFlow<List<VerificationRequest>> =
        adminRepo.observeVerificationRequests().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reports: StateFlow<List<Report>> =
        reportRepo.observeOpenReports().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val actions: StateFlow<List<AdminAction>> =
        adminRepo.observeAdminActions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun run(success: String? = null, block: suspend () -> DataResult<*>) = viewModelScope.launch {
        when (val r = block()) {
            is DataResult.Success -> success?.let { message.value = it }
            is DataResult.Error -> message.value = r.message
        }
    }

    fun viewCredential(requestId: String) = viewModelScope.launch {
        when (val r = adminRepo.viewCredential(requestId)) {
            is DataResult.Success -> credentialDialog.value = r.data
            is DataResult.Error -> message.value = r.message
        }
    }

    fun review(requestId: String, decision: VerificationStatus, reason: String) =
        run("تصمیم ثبت شد.") { adminRepo.reviewVerification(requestId, decision, reason) }

    fun resolveReport(reportId: String, status: ReportStatus, note: String) =
        run("گزارش رسیدگی شد.") { reportRepo.resolveReport(reportId, status, note) }

    fun hideReportedContent(report: Report, reason: String) =
        run("محتوا پنهان شد.") { adminRepo.hideContent(report.contentType, report.contentId, reason) }

    fun deleteReportedContent(report: Report, reason: String) =
        run("محتوا حذف شد.") { adminRepo.deleteContent(report.contentType, report.contentId, reason) }
}
