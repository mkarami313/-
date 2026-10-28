package com.hamraheteyf.app.di

import android.content.Context
import com.hamraheteyf.app.BuildConfig
import com.hamraheteyf.app.data.repository.*
import com.hamraheteyf.app.data.repository.fake.*
import com.hamraheteyf.app.data.repository.server.*
import com.hamraheteyf.app.net.HamrahApi

/**
 * The app's dependency graph. The UI depends only on repository interfaces, so swapping
 * the backend (SERVER ↔ DEMO) means providing a different [AppContainer] — no UI/ViewModel
 * changes required.
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
 * SERVER backend: the self-hosted API (see [BuildConfig.API_BASE_URL]). All repositories
 * share one [ServerStore], which mirrors the server-scoped snapshot locally and forwards
 * every mutation to the API. A persisted session is restored at startup via [ServerStore.bootstrap].
 */
class ServerAppContainer(context: Context) : AppContainer {
    private val store = ServerStore(HamrahApi(context)).also { it.bootstrap() }

    override val authRepository: AuthRepository = ServerAuthRepository(store)
    override val userRepository: UserRepository = ServerUserRepository(store)
    override val questionRepository: QuestionRepository = ServerQuestionRepository(store)
    override val groupRepository: GroupRepository = ServerGroupRepository(store)
    override val conversationRepository: ConversationRepository = ServerConversationRepository(store)
    override val guideRepository: GuideRepository = ServerGuideRepository(store)
    override val reportRepository: ReportRepository = ServerReportRepository(store)
    override val notificationRepository: NotificationRepository = ServerNotificationRepository(store)
    override val adminRepository: AdminRepository = ServerAdminRepository(store)
}

/**
 * DEMO backend: a single in-memory [FakeStore] shared by all repositories, seeded with
 * fictional data. No network, no keys, fully offline. Used by unit tests and offline preview.
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

/** Chooses the backend from the build config. SERVER by default; DEMO for offline/tests. */
fun createAppContainer(context: Context): AppContainer = when (BuildConfig.BACKEND) {
    "DEMO" -> DemoAppContainer()
    else -> ServerAppContainer(context)
}
