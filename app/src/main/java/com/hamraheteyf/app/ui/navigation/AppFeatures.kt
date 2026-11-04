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
import com.hamraheteyf.app.ui.forum.*
import com.hamraheteyf.app.ui.groups.GroupEditScreen
import com.hamraheteyf.app.ui.groups.GroupScreen
import com.hamraheteyf.app.ui.groups.GroupsHubScreen
import com.hamraheteyf.app.ui.learn.*
import com.hamraheteyf.app.ui.home.HomeScreen
import com.hamraheteyf.app.ui.legal.CommunityRulesScreen
import com.hamraheteyf.app.ui.legal.PrivacyPolicyScreen
import com.hamraheteyf.app.ui.messages.ConversationScreen
import com.hamraheteyf.app.ui.messages.ConversationsListScreen
import com.hamraheteyf.app.ui.notifications.NotificationsScreen
import com.hamraheteyf.app.ui.profile.*
import com.hamraheteyf.app.ui.modules.*

/** The registry of every module in the app. Order values decide tab / tile order. */
object AppFeatures {
    private val home = AppFeature("home", "خانه", Icons.Outlined.Home, Routes.HOME, order = 0, bottomTab = true) { nav ->
        composable(Routes.HOME) {
            val disabled by FeatureFlags.disabled.collectAsState()
            HomeScreen(
                tiles = enabled(disabled).filter { it.homeTile },
                onNavigate = { nav.navigate(it) { launchSingleTop = true } },
            )
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it) })
        }
    }

    /** «انجمن»: forums, clinic, topics. Feature id stays "qa" so server switches keep working. */
    private val forum = AppFeature("qa", "انجمن", Icons.Outlined.Forum, Routes.FORUM, order = 10, bottomTab = true) { nav ->
        composable(Routes.FORUM) {
            ForumHubScreen(
                onOpenForum = { nav.navigate(Routes.forumList(it)) },
                onOpenTopic = { nav.navigate(Routes.topic(it)) },
                onNewTopic = { nav.navigate(Routes.topicNew(forumId = it)) },
                onOpenClinic = { nav.navigate(Routes.CLINIC) },
                onOpenGroups = { nav.navigate(Routes.GROUPS) },
                onOpenGroup = { nav.navigate(Routes.groupDetail(it)) },
                onSearch = { nav.navigate(Routes.SEARCH) },
            )
        }
        composable(Routes.FORUM_LIST) { e ->
            ForumTopicsScreen(
                forumId = e.arguments?.getString("forumId").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenForum = { nav.navigate(Routes.forumList(it)) },
                onOpenTopic = { nav.navigate(Routes.topic(it)) },
                onNewTopic = { nav.navigate(Routes.topicNew(forumId = it)) },
            )
        }
        composable(Routes.CLINIC) {
            ClinicScreen(onBack = { nav.popBackStack() }, onOpenTopic = { nav.navigate(Routes.topic(it)) },
                onAsk = { nav.navigate(Routes.topicNew(forumId = it ?: "clinic")) })
        }
        composable(Routes.TOPIC) { e ->
            TopicScreen(
                topicId = e.arguments?.getString("topicId").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenProfile = { nav.navigate(Routes.profileView(it)) },
                onOpenForum = { nav.navigate(Routes.forumList(it)) },
                onOpenGroup = { nav.navigate(Routes.groupDetail(it)) },
                onEdit = { nav.navigate(Routes.topicEdit(it)) },
            )
        }
        composable(Routes.TOPIC_NEW) { e ->
            TopicComposerScreen(
                forumId = e.arguments?.getString("forumId")?.ifBlank { null },
                groupId = e.arguments?.getString("groupId")?.ifBlank { null },
                editTopicId = null,
                onBack = { nav.popBackStack() },
                onDone = { id -> nav.popBackStack(); nav.navigate(Routes.topic(id)) },
            )
        }
        composable(Routes.TOPIC_EDIT) { e ->
            TopicComposerScreen(
                forumId = null, groupId = null, editTopicId = e.arguments?.getString("topicId"),
                onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() },
            )
        }
    }

    /** «کانون‌ها»: groups v2 (reached from the forum hub and home). */
    private val groups = AppFeature("groups", "کانون‌ها", Icons.Outlined.Groups, Routes.GROUPS, order = 20, homeTile = true) { nav ->
        composable(Routes.GROUPS) {
            GroupsHubScreen(onBack = { nav.popBackStack() }, onOpenGroup = { nav.navigate(Routes.groupDetail(it)) },
                onCreate = { nav.navigate(Routes.groupEdit()) })
        }
        composable(Routes.GROUP_EDIT) { e ->
            GroupEditScreen(
                groupId = e.arguments?.getString("groupId")?.ifBlank { null },
                onBack = { nav.popBackStack() },
                onSaved = { id -> nav.popBackStack(); nav.navigate(Routes.groupDetail(id)) { launchSingleTop = true } },
            )
        }
        composable(Routes.GROUP_DETAIL) { e ->
            GroupScreen(
                groupId = e.arguments?.getString("groupId").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenTopic = { nav.navigate(Routes.topic(it)) },
                onNewTopic = { nav.navigate(Routes.topicNew(groupId = it)) },
                onOpenProfile = { nav.navigate(Routes.profileView(it)) },
                onEdit = { nav.navigate(Routes.groupEdit(it)) },
            )
        }
    }

    /** «آموزش‌ها»: videos + articles by category, user submissions, admin review. Id stays "guides". */
    private val learn = AppFeature("guides", "آموزش‌ها", Icons.Outlined.PlayCircle, Routes.LEARN, order = 25, bottomTab = true) { nav ->
        composable(Routes.LEARN) {
            LearnHubScreen(
                onOpenItem = { nav.navigate(Routes.learnItem(it)) },
                onOpenList = { c, m -> nav.navigate(Routes.learnList(c, m)) },
                onSubmit = { nav.navigate(Routes.learnSubmit()) },
                onReview = { nav.navigate(Routes.LEARN_REVIEW) },
            )
        }
        composable(Routes.LEARN_LIST) { e ->
            LearnListScreen(
                categoryId = e.arguments?.getString("categoryId")?.ifBlank { null },
                mode = e.arguments?.getString("mode").orEmpty(),
                onBack = { nav.popBackStack() }, onOpenItem = { nav.navigate(Routes.learnItem(it)) },
            )
        }
        composable(Routes.LEARN_ITEM) { e ->
            LearnItemScreen(
                id = e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.learnSubmit(it)) }, onOpenProfile = { nav.navigate(Routes.profileView(it)) },
            )
        }
        composable(Routes.LEARN_SUBMIT) { e ->
            LearnSubmitScreen(editId = e.arguments?.getString("id")?.ifBlank { null }, onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
        }
        composable(Routes.LEARN_REVIEW) { LearnReviewScreen(onBack = { nav.popBackStack() }, onOpenItem = { nav.navigate(Routes.learnItem(it)) }) }
        // legacy guide links (old notifications) open the library
        composable(Routes.GUIDES) { LearnListScreen(categoryId = null, mode = "article", onBack = { nav.popBackStack() }, onOpenItem = { nav.navigate(Routes.learnItem(it)) }) }
        composable(Routes.GUIDE_DETAIL) { e ->
            LearnItemScreen(id = "l_" + e.arguments?.getString("guideId").orEmpty(), onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.learnSubmit(it)) }, onOpenProfile = { nav.navigate(Routes.profileView(it)) })
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
                onFirstThen = { nav.navigate(Routes.TOOLS_FIRSTTHEN) }, onTimer = { nav.navigate(Routes.TOOLS_TIMER) },
                onMilestones = { nav.navigate(Routes.TOOLS_MILESTONES) }, onCards = { nav.navigate(Routes.CARDS) })
        }
        composable(Routes.TOOLS_SCHEDULE) { ScheduleScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_STORIES) { StoriesScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_AAC) { AacScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_LOG) { BehaviorLogScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_EMERGENCY) { EmergencyScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_REWARD) { RewardScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_FIRSTTHEN) { FirstThenScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_TIMER) { VisualTimerScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TOOLS_MILESTONES) { MilestonesScreen(onBack = { nav.popBackStack() }) }
    }

    /** «کارت‌های آموزشی»: picture cards bundled in the app (offline), game and printable PDF. */
    private val cards = AppFeature("cards", "کارت‌های آموزشی", Icons.Outlined.Style, Routes.CARDS, order = 37, homeTile = true) { nav ->
        composable(Routes.CARDS) {
            CardsHubScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.cardsCategory(it)) },
                onGame = { nav.navigate(Routes.CARDS_GAME) }, onPrint = { nav.navigate(Routes.CARDS_PRINT) })
        }
        composable(Routes.CARDS_CATEGORY) { e -> CardsCategoryScreen(e.arguments?.getString("category").orEmpty(), onBack = { nav.popBackStack() }) }
        composable(Routes.CARDS_GAME) { CardsGameScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.CARDS_PRINT) { CardsPrintScreen(onBack = { nav.popBackStack() }) }
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
                    "question", "topic" -> Routes.topic(h.id); "group" -> Routes.groupDetail(h.id); "guide", "learn" -> Routes.learnItem(h.id)
                    "user" -> Routes.profileView(h.id); "donation" -> Routes.donationDetail(h.id); "course" -> Routes.courseDetail(h.id)
                    else -> Routes.PLACES
                }
                nav.navigate(r)
            })
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

    private val profile = AppFeature("profile", "من", Icons.Outlined.Person, Routes.PROFILE, order = 90, bottomTab = true) { nav ->
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
                onNavigate = { nav.navigate(it) },
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
    val all: List<AppFeature> = listOf(home, forum, groups, learn, messages, donations, childfile, booking, cards, tools, courses, places, directory, profile).sortedBy { it.order }
    val bottomTabs: List<AppFeature> = all.filter { it.bottomTab }

    /** Modules currently enabled by the server (core ones are never disabled). */
    fun enabled(disabled: Set<String>): List<AppFeature> = all.filter { it.id !in disabled }
}
