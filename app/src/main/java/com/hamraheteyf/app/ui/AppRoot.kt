package com.hamraheteyf.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.ui.admin.AdminScreen
import com.hamraheteyf.app.ui.auth.LoginScreen
import com.hamraheteyf.app.ui.auth.RegisterScreen
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
import com.hamraheteyf.app.ui.navigation.Routes
import com.hamraheteyf.app.ui.notifications.NotificationsScreen
import com.hamraheteyf.app.ui.profile.*
import com.hamraheteyf.app.ui.qa.QaCreateScreen
import com.hamraheteyf.app.ui.qa.QaDetailScreen
import com.hamraheteyf.app.ui.qa.QaListScreen

@Composable
fun AppRoot() {
    val container = LocalAppContainer.current
    val userId by container.authRepository.currentUserId.collectAsState()
    if (userId == null) AuthNavHost() else MainNavHost()
}

@Composable
private fun AuthNavHost() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) { LoginScreen(onGoToRegister = { nav.navigate(Routes.REGISTER) }) }
        composable(Routes.REGISTER) { RegisterScreen(onBack = { nav.popBackStack() }) }
    }
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Routes.HOME, "خانه", Icons.Outlined.Home),
    BottomItem(Routes.QA, "پرسش‌وپاسخ", Icons.Outlined.Forum),
    BottomItem(Routes.GROUPS, "گروه‌ها", Icons.Outlined.Groups),
    BottomItem(Routes.MESSAGES, "پیام‌ها", Icons.Outlined.Mail),
    BottomItem(Routes.PROFILE, "پروفایل", Icons.Outlined.Person),
)

@Composable
private fun MainNavHost() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomItems.map { it.route }) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenQuestion = { nav.navigate(Routes.qaDetail(it)) },
                    onOpenGroup = { nav.navigate(Routes.groupDetail(it)) },
                    onOpenGuide = { nav.navigate(Routes.guideDetail(it)) },
                    onAskQuestion = { nav.navigate(Routes.QA_CREATE) },
                    onOpenGroups = { nav.navigate(Routes.GROUPS) },
                    onOpenGuides = { nav.navigate(Routes.GUIDES) },
                    onOpenDirectory = { nav.navigate(Routes.DIRECTORY) },
                    onOpenNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                )
            }
            composable(Routes.QA) {
                QaListScreen(onOpenQuestion = { nav.navigate(Routes.qaDetail(it)) }, onAsk = { nav.navigate(Routes.QA_CREATE) })
            }
            composable(Routes.GROUPS) {
                GroupsListScreen(onOpenGroup = { nav.navigate(Routes.groupDetail(it)) }, onCreate = { nav.navigate(Routes.GROUP_CREATE) })
            }
            composable(Routes.MESSAGES) {
                ConversationsListScreen(onOpen = { nav.navigate(Routes.conversation(it)) })
            }
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

            composable(Routes.QA_CREATE) { QaCreateScreen(onBack = { nav.popBackStack() }, onCreated = { nav.popBackStack() }) }
            composable(Routes.QA_DETAIL) { entry ->
                QaDetailScreen(questionId = entry.arguments?.getString("questionId").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable(Routes.GROUP_CREATE) {
                GroupCreateScreen(onBack = { nav.popBackStack() }, onCreated = { id ->
                    nav.popBackStack(); nav.navigate(Routes.groupDetail(id))
                })
            }
            composable(Routes.GROUP_DETAIL) { entry ->
                GroupDetailScreen(groupId = entry.arguments?.getString("groupId").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable(Routes.CONVERSATION) { entry ->
                ConversationScreen(conversationId = entry.arguments?.getString("conversationId").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable(Routes.DIRECTORY) {
                DirectoryScreen(onBack = { nav.popBackStack() }, onOpenProfile = { nav.navigate(Routes.profileView(it)) })
            }
            composable(Routes.PROFILE_VIEW) { entry ->
                ProfileViewScreen(
                    userId = entry.arguments?.getString("userId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenConversation = { nav.navigate(Routes.conversation(it)) },
                )
            }
            composable(Routes.GUIDES) { GuidesScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.guideDetail(it)) }) }
            composable(Routes.GUIDE_DETAIL) { entry ->
                GuideDetailScreen(guideId = entry.arguments?.getString("guideId").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.EDIT_PROFILE) { EditProfileScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.PROFESSIONAL_EDIT) { ProfessionalEditScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.VERIFICATION) { VerificationScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.CHILDREN) { ChildrenScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.ADMIN) { AdminScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.LEGAL_RULES) { CommunityRulesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.LEGAL_PRIVACY) { PrivacyPolicyScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
