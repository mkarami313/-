package com.hamraheteyf.app.di

import com.hamraheteyf.app.data.repository.*
import com.hamraheteyf.app.data.repository.firebase.*

/**
 * Real backend: every repository is backed by Firebase Authentication + Firestore.
 * Activated when BuildConfig.BACKEND == "FIREBASE" (i.e. google-services.json is present).
 *
 * The Firebase SDK auto-initializes the default [com.google.firebase.FirebaseApp] from the
 * google-services.json resources, so no manual initialization is needed here.
 */
class FirebaseAppContainer : AppContainer {
    override val authRepository: AuthRepository = FirebaseAuthRepository()
    override val userRepository: UserRepository = FirebaseUserRepository()
    override val questionRepository: QuestionRepository = FirebaseQuestionRepository()
    override val groupRepository: GroupRepository = FirebaseGroupRepository()
    override val conversationRepository: ConversationRepository = FirebaseConversationRepository()
    override val guideRepository: GuideRepository = FirebaseGuideRepository()
    override val reportRepository: ReportRepository = FirebaseReportRepository()
    override val notificationRepository: NotificationRepository = FirebaseNotificationRepository()
    override val adminRepository: AdminRepository = FirebaseAdminRepository()
}
