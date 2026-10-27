package com.hamraheteyf.app.di

import com.hamraheteyf.app.BuildConfig
import com.hamraheteyf.app.data.repository.*
import com.hamraheteyf.app.data.repository.fake.*

/**
 * The app's dependency graph. The UI depends only on repository interfaces, so swapping
 * the demo backend for a real one (Firebase) means providing a different [AppContainer]
 * implementation — no UI/ViewModel changes required.
 */
interface AppContainer {
    val authRepository: AuthRepository
    val userRepository: UserRepository
    val questionRepository: QuestionRepository
    val groupRepository: GroupRepository
    val conversationRepository: ConversationRepository
    val guideRepository: GuideRepository
    val reportRepository: ReportRepository
    val notificationRepository: NotificationRepository
    val adminRepository: AdminRepository
}

/**
 * DEMO backend: a single in-memory [FakeStore] shared by all repositories, seeded with
 * fictional data. No network, no keys, fully offline.
 */
class DemoAppContainer : AppContainer {
    private val store = FakeStore(SeedData.initial(), SeedData.credentials())

    override val authRepository: AuthRepository = FakeAuthRepository(store)
    override val userRepository: UserRepository = FakeUserRepository(store)
    override val questionRepository: QuestionRepository = FakeQuestionRepository(store)
    override val groupRepository: GroupRepository = FakeGroupRepository(store)
    override val conversationRepository: ConversationRepository = FakeConversationRepository(store)
    override val guideRepository: GuideRepository = FakeGuideRepository(store)
    override val reportRepository: ReportRepository = FakeReportRepository(store)
    override val notificationRepository: NotificationRepository = FakeNotificationRepository(store)
    override val adminRepository: AdminRepository = FakeAdminRepository(store)
}

/**
 * Chooses the backend from the build config. FIREBASE activates automatically when
 * google-services.json is present at build time (see docs/FIREBASE.md); otherwise DEMO.
 */
fun createAppContainer(): AppContainer = when (BuildConfig.BACKEND) {
    "FIREBASE" -> FirebaseAppContainer()
    else -> DemoAppContainer()
}
