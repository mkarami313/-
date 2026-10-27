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
