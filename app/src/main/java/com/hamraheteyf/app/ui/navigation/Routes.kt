package com.hamraheteyf.app.ui.navigation

/** Centralized navigation routes. Parameterized routes expose a builder. */
object Routes {
    // Auth
    const val LOGIN = "login"
    const val REGISTER = "register"

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

    const val LEGAL_RULES = "legal_rules"
    const val LEGAL_PRIVACY = "legal_privacy"
}
