package com.hamraheteyf.app.ui.qa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamraheteyf.app.data.model.Question
import com.hamraheteyf.app.data.model.QuestionCategory
import com.hamraheteyf.app.data.model.QuestionSort
import com.hamraheteyf.app.data.repository.QuestionRepository
import com.hamraheteyf.app.data.repository.UserRepository
import com.hamraheteyf.app.ui.common.AuthorView
import com.hamraheteyf.app.ui.common.resolveAuthor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class QaListViewModel(
    private val questionRepo: QuestionRepository,
    private val userRepo: UserRepository,
) : ViewModel() {

    val sort = MutableStateFlow(QuestionSort.NEWEST)
    val category = MutableStateFlow<QuestionCategory?>(null)

    val items: StateFlow<List<Pair<Question, AuthorView>>> =
        combine(sort, category) { s, c -> s to c }
            .flatMapLatest { (s, c) ->
                combine(
                    questionRepo.observeQuestions(s, c),
                    userRepo.observeUsers(),
                    userRepo.observeAllProfessionals(),
                ) { questions, users, professionals ->
                    questions.map { q -> q to resolveAuthor(users, professionals, q.authorId, q.isAnonymous) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSort(s: QuestionSort) { sort.value = s }
    fun setCategory(c: QuestionCategory?) { category.value = c }
}
