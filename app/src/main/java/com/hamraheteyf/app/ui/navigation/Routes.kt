package com.hamraheteyf.app.ui.navigation

/** Centralized navigation routes. Parameterized routes expose a builder. */
object Routes {
    // Auth
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val RESET = "reset_password"
    const val EMERGENCY_CARD_PUBLIC = "emergency_card_public"
    const val ONBOARDING = "onboarding"
    const val SHARES_RECEIVED = "shares_received"
    const val SHARE_VIEW = "share_view/{id}"
    fun shareView(id: String) = "share_view/$id"

    // Bottom-nav tabs
    const val HOME = "home"
    const val QA = "qa"
    const val GROUPS = "groups"
    const val MESSAGES = "messages"
    const val PROFILE = "profile"

    // Details / secondary
    const val QA_CREATE = "qa_create"
    const val QA_DETAIL = "qa_detail/{questionId}"
    fun qaDetail(id: String) = "qa_detail/$id"

    const val GROUP_CREATE = "group_create"
    const val GROUP_DETAIL = "group_detail/{groupId}"
    fun groupDetail(id: String) = "group_detail/$id"

    const val CONVERSATION = "conversation/{conversationId}"
    fun conversation(id: String) = "conversation/$id"

    const val DIRECTORY = "directory"
    const val PROFILE_VIEW = "profile_view/{userId}"
    fun profileView(id: String) = "profile_view/$id"

    const val GUIDES = "guides"
    const val GUIDE_DETAIL = "guide_detail/{guideId}"
    fun guideDetail(id: String) = "guide_detail/$id"

    const val DONATIONS = "donations"
    const val DONATION_CREATE = "donation_create"
    const val DONATION_DETAIL = "donation_detail/{donationId}"
    fun donationDetail(id: String) = "donation_detail/$id"

    const val CHILDFILE = "childfile"
    const val BOOKING = "booking"
    const val TOOLS = "tools"
    const val TOOLS_SCHEDULE = "tools_schedule"
    const val TOOLS_STORIES = "tools_stories"
    const val TOOLS_AAC = "tools_aac"
    const val TOOLS_LOG = "tools_log"
    const val TOOLS_EMERGENCY = "tools_emergency"
    const val TOOLS_REWARD = "tools_reward"
    const val TOOLS_FIRSTTHEN = "tools_firstthen"
    const val TOOLS_TIMER = "tools_timer"
    const val COURSES = "courses"
    const val COURSE_DETAIL = "course_detail/{courseId}"
    fun courseDetail(id: String) = "course_detail/$id"
    const val PLACES = "places"
    const val SEARCH = "search"

    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val EDIT_PROFILE = "edit_profile"
    const val PROFESSIONAL_EDIT = "professional_edit"
    const val VERIFICATION = "verification"
    const val CHILDREN = "children"
    const val ADMIN = "admin"

    // Community v2 — forum, clinic, groups v2
    const val FORUM = "forum"
    const val FORUM_LIST = "forum_list/{forumId}"
    fun forumList(id: String) = "forum_list/$id"
    const val TOPIC = "topic/{topicId}"
    fun topic(id: String) = "topic/$id"
    const val TOPIC_NEW = "topic_new?forumId={forumId}&groupId={groupId}"
    fun topicNew(forumId: String? = null, groupId: String? = null) = "topic_new?forumId=${forumId.orEmpty()}&groupId=${groupId.orEmpty()}"
    const val TOPIC_EDIT = "topic_edit/{topicId}"
    fun topicEdit(id: String) = "topic_edit/$id"
    const val CLINIC = "clinic"
    const val GROUP_EDIT = "group_edit?groupId={groupId}"
    fun groupEdit(id: String? = null) = "group_edit?groupId=${id.orEmpty()}"

    // Learning library (آموزش‌ها)
    const val LEARN = "learn"
    const val LEARN_LIST = "learn_list?categoryId={categoryId}&mode={mode}"
    /** mode: "" (published), "video", "article", "saved", "mine" */
    fun learnList(categoryId: String? = null, mode: String = "") = "learn_list?categoryId=${categoryId.orEmpty()}&mode=$mode"
    const val LEARN_ITEM = "learn_item/{id}"
    fun learnItem(id: String) = "learn_item/$id"
    const val LEARN_SUBMIT = "learn_submit?id={id}"
    fun learnSubmit(id: String? = null) = "learn_submit?id=${id.orEmpty()}"
    const val LEARN_REVIEW = "learn_review"
    const val TOOLS_MILESTONES = "tools_milestones"
    const val CARDS = "cards"
    const val CARDS_CATEGORY = "cards/{category}"
    fun cardsCategory(id: String) = "cards/$id"
    const val CARDS_GAME = "cards_game"
    const val CARDS_PRINT = "cards_print"

    /** Where a notification leads. relatedId is a topic id (t_…), conversation (conv_…), group (g_…),
     *  learning item ("learn:l_…"), verification or user id. */
    fun forNotification(type: String?, relatedId: String?): String? {
        val id = relatedId?.takeIf { it.isNotBlank() } ?: return null
        return when {
            id.startsWith("learn:") -> learnItem(id.removePrefix("learn:"))
            id.startsWith("t_") -> topic(id)
            id.startsWith("conv_") -> conversation(id)
            id.startsWith("g_") -> groupDetail(id)
            id.startsWith("don_") -> donationDetail(id)
            type == "VERIFICATION_RESULT" -> VERIFICATION
            type == "BOOKING" -> BOOKING
            else -> null
        }
    }

    const val LEGAL_RULES = "legal_rules"
    const val LEGAL_PRIVACY = "legal_privacy"
}
