package com.hamraheteyf.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.composable
import com.hamraheteyf.app.ui.admin.AdminScreen
import com.hamraheteyf.app.ui.directory.DirectoryScreen
import com.hamraheteyf.app.ui.directory.ProfileViewScreen
import com.hamraheteyf.app.ui.donation.DonationCreateScreen
import com.hamraheteyf.app.ui.donation.DonationDetailScreen
import com.hamraheteyf.app.ui.donation.DonationListScreen
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
import com.hamraheteyf.app.ui.modules.*
import com.hamraheteyf.app.ui.qa.QaCreateScreen
import com.hamraheteyf.app.ui.qa.QaDetailScreen
import com.hamraheteyf.app.ui.qa.QaListScreen

/** The registry of every module in the app. Order values decide tab / tile order. */
object AppFeatures {
    private val home = AppFeature("home", "خانه", Icons.Outlined.Home, Routes.HOME, order = 0, bottomTab = true) { nav ->
        composable(Routes.HOME) {
            val disabled by FeatureFlags.disabled.collectAsState()
            HomeScreen(
                tiles = enabled(disabled).filter { it.homeTile },
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

    private val donations = AppFeature("donations", "دست‌به‌دست", Icons.Outlined.VolunteerActivism, Routes.DONATIONS, order = 35, homeTile = true) { nav ->
        composable(Routes.DONATIONS) {
            DonationListScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.donationDetail(it)) }, onCreate = { nav.navigate(Routes.DONATION_CREATE) })
        }
        composable(Routes.DONATION_CREATE) {
            DonationCreateScreen(onBack = { nav.popBackStack() }, onCreated = { id -> nav.popBackStack(); nav.navigate(Routes.donationDetail(id)) })
        }
        composable(Routes.DONATION_DETAIL) { e ->
            DonationDetailScreen(
                id = e.arguments?.getString("donationId").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenConversation = { nav.navigate(Routes.conversation(it)) },
            )
        }
    }

    private val childfile = AppFeature("childfile", "پروندهٔ کودک", Icons.Outlined.ChildCare, Routes.CHILDFILE, order = 36, homeTile = true) { nav ->
        composable(Routes.CHILDFILE) { ChildFileScreen(onBack = { nav.popBackStack() }) }
    }

    private val booking = AppFeature("booking", "نوبت‌دهی", Icons.Outlined.EventAvailable, Routes.BOOKING, order = 37, homeTile = true) { nav ->
        composable(Routes.BOOKING) { BookingScreen(onBack = { nav.popBackStack() }, onOpenProfile = { nav.navigate(Routes.profileView(it)) }) }
    }

    private val tools = AppFeature("tools", "ابزارهای روزانه", Icons.Outlined.Extension, Routes.TOOLS, order = 38, homeTile = true) { nav ->
        composable(Routes.TOOLS) {
            ToolsHubScreen(onBack = { nav.popBackStack() }, onSchedule = { nav.navigate(Routes.TOOLS_SCHEDULE) },
                onStories = { nav.navigate(Routes.TOOLS_STORIES) }, onAac = { nav.navigate(Routes.TOOLS_AAC) },
                onLog = { nav.navigate(Routes.TOOLS_LOG) }, onEmergency = { nav.navigate(Routes.TOOLS_EMERGENCY) }, onReward = { nav.navigate(Routes.TOOLS_REWARD) },
                onFirstThen = { nav.navigate(Routes.TOOLS_FIRSTTHEN) }, onTimer = { nav.navigate(Routes.TOOLS_TIMER) })
        }
        composable(Routes.TOOLS_SCHEDULE) { ScheduleScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_STORIES) { StoriesScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_AAC) { AacScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_LOG) { BehaviorLogScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_EMERGENCY) { EmergencyScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_REWARD) { RewardScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_FIRSTTHEN) { FirstThenScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_TIMER) { VisualTimerScreen(onBack = { nav.popBackStack() }) }
    }

    private val courses = AppFeature("courses", "دوره‌ها", Icons.Outlined.School, Routes.COURSES, order = 39, homeTile = true) { nav ->
        composable(Routes.COURSES) { CoursesScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.courseDetail(it)) }) }
        composable(Routes.COURSE_DETAIL) { e -> CourseDetailScreen(courseId = e.arguments?.getString("courseId").orEmpty(), onBack = { nav.popBackStack() }) }
    }

    private val places = AppFeature("places", "مراکز و رویدادها", Icons.Outlined.Place, Routes.PLACES, order = 40, homeTile = true) { nav ->
        composable(Routes.PLACES) { PlacesScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SEARCH) {
            SearchScreen(onBack = { nav.popBackStack() }, onOpen = { h ->
                val r = when (h.type) {
                    "question" -> Routes.qaDetail(h.id); "group" -> Routes.groupDetail(h.id); "guide" -> Routes.guideDetail(h.id)
                    "user" -> Routes.profileView(h.id); "donation" -> Routes.donationDetail(h.id); "course" -> Routes.courseDetail(h.id)
                    else -> Routes.PLACES
                }
                nav.navigate(r)
            })
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
                onSharesReceived = { nav.navigate(Routes.SHARES_RECEIVED) },
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.EDIT_PROFILE) { EditProfileScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PROFESSIONAL_EDIT) { ProfessionalEditScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.VERIFICATION) { VerificationScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.CHILDREN) { ChildrenScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.ADMIN) { AdminScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SHARES_RECEIVED) { SharesReceivedScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.shareView(it)) }) }
        composable(Routes.SHARE_VIEW) { e -> SharedChildViewerScreen(shareId = e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }) }
        composable(Routes.ONBOARDING) {
            com.hamraheteyf.app.ui.onboarding.OnboardingScreen(
                onFinished = { nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true }; launchSingleTop = true } },
            )
        }
        composable(Routes.LEGAL_RULES) { CommunityRulesScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.LEGAL_PRIVACY) { PrivacyPolicyScreen(onBack = { nav.popBackStack() }) }
    }

    /** Add new modules here. */
    val all: List<AppFeature> = listOf(home, qa, groups, messages, donations, childfile, booking, tools, courses, places, guides, directory, profile).sortedBy { it.order }
    val bottomTabs: List<AppFeature> = all.filter { it.bottomTab }

    /** Modules currently enabled by the server (core ones are never disabled). */
    fun enabled(disabled: Set<String>): List<AppFeature> = all.filter { it.id !in disabled }
}
