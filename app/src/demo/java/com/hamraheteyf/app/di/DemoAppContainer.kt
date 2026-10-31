package com.hamraheteyf.app.di

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.BuildConfig
import com.hamraheteyf.app.data.repository.*
import com.hamraheteyf.app.data.repository.fake.*

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

/** Debug/test builds: the in-memory backend when BuildConfig.BACKEND == "DEMO". Release has a null stub (src/release). */
fun demoContainerOrNull(): AppContainer? = if (BuildConfig.BACKEND == "DEMO") DemoAppContainer() else null

/** Sign-in hint for the DEMO backend; nothing in release. */
@Composable
fun DemoLoginHint() {
    if (BuildConfig.BACKEND != "DEMO") return
    Spacer(Modifier.height(24.dp))
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Text("حالت نمایشی", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "برای آزمایش می‌توانید با ایمیل ${SeedData.EMAIL_FAMILY1} (خانواده) یا ${SeedData.EMAIL_ADMIN} (مدیر) و رمز ${SeedData.DEMO_PASSWORD} وارد شوید.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
