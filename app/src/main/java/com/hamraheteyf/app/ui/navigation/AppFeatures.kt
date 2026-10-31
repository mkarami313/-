package com.hamraheteyf.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.navigation.compose.composable
import com.hamraheteyf.app.ui.admin.AdminScreen
import com.hamraheteyf.app.ui.directory.DirectoryScreen
import com.hamraheteyf.app.ui.directory.ProfileViewScreen
import com.hamraheteyf.app.ui.groups.GroupCreateScreen
import com.hamraheteyf.app.ui.groups.GroupDetailScreen
import com.hamraheteyf.app.ui.groups.GroupsListScreen
import com.hamraheteyf.app.ui.guides.GuideDetailScreen
import com.hamraheteyf.app.ui.guides.GuidesScreen
import com.hamraheteyf.app.ui.home.HomeScreen
import com.hamraheteyf.app.ui.legal.CommunityRulesScreen
import com.hamraheteyf.app.ui.legal.PrivacyPolicyScreen
import com.hamraheteyf.app.ui.messages.ConversationScreen
import com.hamraheteyf.app.ui.messages.ConversationsListScreen
import com.hamraheteyf.app.ui.notifications.NotificationsScreen
import com.hamraheteyf.app.ui.profile.*
import com.hamraheteyf.app.ui.qa.QaCreateScreen
import com.hamraheteyf.app.ui.qa.QaDetailScreen
import com.hamraheteyf.app.ui.qa.QaListScreen

/** The registry of every module in the app. Order values decide tab / tile order. */
object AppFeatures {
    private val home = AppFeature("home", "خانه", Icons.Outlined.Home, Routes.HOME, order = 0, bottomTab = true) { nav ->
        composable(Routes.HOME) {
            HomeScreen(
                tiles = all.filter { it.homeTile },
                onNavigate = { nav.navigate(it) { launchSingleTop = true } },
                onOpenQuestion = { nav.navigate(Routes.qaDetail(it)) },
                onOpenGroup = { nav.navigate(Routes.groupDetail(it)) },
                onOpenGuide = { nav.navigate(Routes.guideDetail(it)) },
            )
        }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen(onBack = { nav.popBackStack() }) }
    }

    private val qa = AppFeature("qa", "پرسش‌ها", Icons.Outlined.Forum, Routes.QA, order = 10, bottomTab = true, homeTile = true) { nav ->
        composable(Routes.QA) {
            QaListScreen(onOpenQuestion = { nav.navigate(Routes.qaDetail(it)) }, onAsk = { nav.navigate(Routes.QA_CREATE) })
        }
        composable(Routes.QA_CREATE) { QaCreateScreen(onBack = { nav.popBackStack() }, onCreated = { nav.popBackStack() }) }
        composable(Routes.QA_DETAIL) { e ->
            QaDetailScreen(questionId = e.arguments?.getString("questionId").orEmpty(), onBack = { nav.popBackStack() })
        }
    }

    private val groups = AppFeature("groups", "گروه‌ها", Icons.Outlined.Groups, Routes.GROUPS, order = 20, bottomTab = true, homeTile = true) { nav ->
        composable(Routes.GROUPS) {
            GroupsListScreen(onOpenGroup = { nav.navigate(Routes.groupDetail(it)) }, onCreate = { nav.navigate(Routes.GROUP_CREATE) })
        }
        composable(Routes.GROUP_CREATE) {
            GroupCreateScreen(onBack = { nav.popBackStack() }, onCreated = { id -> nav.popBackStack(); nav.navigate(Routes.groupDetail(id)) })
        }
        composable(Routes.GROUP_DETAIL) { e ->
            GroupDetailScreen(groupId = e.arguments?.getString("groupId").orEmpty(), onBack = { nav.popBackStack() })
        }
    }

    private val messages = AppFeature("messages", "پیام‌ها", Icons.Outlined.Mail, Routes.MESSAGES, order = 30, bottomTab = true, homeTile = true) { nav ->
        composable(Routes.MESSAGES) { ConversationsListScreen(onOpen = { nav.navigate(Routes.conversation(it)) }) }
        composable(Routes.CONVERSATION) { e ->
            ConversationScreen(conversationId = e.arguments?.getString("conversationId").orEmpty(), onBack = { nav.popBackStack() })
        }
    }

    private val guides = AppFeature("guides", "راهنماها", Icons.Outlined.MenuBook, Routes.GUIDES, order = 40, homeTile = true) { nav ->
        composable(Routes.GUIDES) { GuidesScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.guideDetail(it)) }) }
        composable(Routes.GUIDE_DETAIL) { e ->
            GuideDetailScreen(guideId = e.arguments?.getString("guideId").orEmpty(), onBack = { nav.popBackStack() })
        }
    }

    private val directory = AppFeature("directory", "متخصصان", Icons.Outlined.PersonSearch, Routes.DIRECTORY, order = 50, homeTile = true) { nav ->
        composable(Routes.DIRECTORY) { DirectoryScreen(onBack = { nav.popBackStack() }, onOpenProfile = { nav.navigate(Routes.profileView(it)) }) }
        composable(Routes.PROFILE_VIEW) { e ->
            ProfileViewScreen(
                userId = e.arguments?.getString("userId").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenConversation = { nav.navigate(Routes.conversation(it)) },
            )
        }
    }

    private val profile = AppFeature("profile", "پروفایل", Icons.Outlined.Person, Routes.PROFILE, order = 90, bottomTab = true) { nav ->
        composable(Routes.PROFILE) {
            ProfileTabScreen(
                onEditProfile = { nav.navigate(Routes.EDIT_PROFILE) },
                onProfessional = { nav.navigate(Routes.PROFESSIONAL_EDIT) },
                onVerification = { nav.navigate(Routes.VERIFICATION) },
                onChildren = { nav.navigate(Routes.CHILDREN) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onAdmin = { nav.navigate(Routes.ADMIN) },
                onRules = { nav.navigate(Routes.LEGAL_RULES) },
                onPrivacy = { nav.navigate(Routes.LEGAL_PRIVACY) },
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.EDIT_PROFILE) { EditProfileScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PROFESSIONAL_EDIT) { ProfessionalEditScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.VERIFICATION) { VerificationScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.CHILDREN) { ChildrenScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.ADMIN) { AdminScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.LEGAL_RULES) { CommunityRulesScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.LEGAL_PRIVACY) { PrivacyPolicyScreen(onBack = { nav.popBackStack() }) }
    }

    /** Add new modules here. */
    val all: List<AppFeature> = listOf(home, qa, groups, messages, guides, directory, profile).sortedBy { it.order }
    val bottomTabs: List<AppFeature> = all.filter { it.bottomTab }
}
