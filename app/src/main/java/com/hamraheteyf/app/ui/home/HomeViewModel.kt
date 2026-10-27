package com.hamraheteyf.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.*
import com.hamraheteyf.app.ui.common.AuthorView
import com.hamraheteyf.app.ui.common.resolveAuthor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    userRepo: UserRepository,
    questionRepo: QuestionRepository,
    groupRepo: GroupRepository,
    guideRepo: GuideRepository,
    notificationRepo: NotificationRepository,
) : ViewModel() {

    val currentUser: StateFlow<User?> =
        userRepo.observeCurrentUser().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentQuestions: StateFlow<List<Pair<Question, AuthorView>>> =
        combine(
            questionRepo.observeQuestions(QuestionSort.NEWEST, null),
            userRepo.observeUsers(),
            userRepo.observeAllProfessionals(),
        ) { questions, users, professionals ->
            questions.take(5).map { q ->
                q to resolveAuthor(users, professionals, q.authorId, q.isAnonymous)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<Group>> =
        groupRepo.observeGroups("").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val guides: StateFlow<List<GuideArticle>> =
        guideRepo.observeGuides(null).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> =
        notificationRepo.observeUnreadCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
