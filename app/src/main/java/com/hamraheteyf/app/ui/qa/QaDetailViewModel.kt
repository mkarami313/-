package com.hamraheteyf.app.ui.qa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.*
import com.hamraheteyf.app.ui.common.AuthorView
import com.hamraheteyf.app.ui.common.resolveAuthor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class QaDetailState(
    val question: Question? = null,
    val questionAuthor: AuthorView? = null,
    val answers: List<Pair<Answer, AuthorView>> = emptyList(),
    val currentUserId: String? = null,
    val isAuthor: Boolean = false,
)

class QaDetailViewModel(
    private val questionId: String,
    private val questionRepo: QuestionRepository,
    private val userRepo: UserRepository,
    private val reportRepo: ReportRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    val message = MutableStateFlow<String?>(null)
    fun clearMessage() { message.value = null }

    val state: StateFlow<QaDetailState> = combine(
        questionRepo.observeQuestion(questionId),
        questionRepo.observeAnswers(questionId),
        userRepo.observeUsers(),
        userRepo.observeAllProfessionals(),
        auth.currentUserId,
    ) { question, answers, users, professionals, currentId ->
        val qAuthor = question?.let { resolveAuthor(users, professionals, it.authorId, it.isAnonymous) }
        QaDetailState(
            question = question,
            questionAuthor = qAuthor,
            answers = answers.map { a -> a to resolveAuthor(users, professionals, a.authorId) },
            currentUserId = currentId,
            isAuthor = question != null && currentId != null && question.authorId == currentId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QaDetailState())

    private fun run(block: suspend () -> DataResult<*>) {
        viewModelScope.launch {
            val r = block()
            if (r is DataResult.Error) message.value = r.message
        }
    }

    fun answer(body: String) = run { questionRepo.createAnswer(questionId, body) }
    fun toggleHelpful(answerId: String) = run { questionRepo.toggleHelpful(answerId) }
    fun deleteQuestion(onDeleted: () -> Unit) = viewModelScope.launch {
        when (val r = questionRepo.deleteQuestion(questionId)) {
            is DataResult.Success -> onDeleted()
            is DataResult.Error -> message.value = r.message
        }
    }
    fun setClosed(closed: Boolean) = run { questionRepo.setCommentsClosed(questionId, closed) }
    fun report(type: ContentType, id: String, reason: ReportReason, note: String) = run {
        reportRepo.createReport(type, id, reason, note).also { if (it is DataResult.Success) message.value = "گزارش شما ثبت شد." }
    }
}
