package com.hamraheteyf.app.data.repository.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.hamraheteyf.app.data.model.*

/**
 * Explicit mappers between domain models and Firestore documents.
 *
 * We map by hand (instead of Firestore's reflection-based toObject) because the models use
 * enums and Sets and have non-default constructor params. Conventions:
 *  - the Firestore document id is the model id (not duplicated in fields),
 *  - enums are stored by [Enum.name],
 *  - Sets are stored as arrays and read back with [toSet],
 *  - timestamps are epoch-millis Longs.
 */

private fun Map<*, *>?.b(key: String, default: Boolean): Boolean = this?.get(key) as? Boolean ?: default

// ---- User ----

fun User.toMap(): Map<String, Any?> = mapOf(
    "displayName" to displayName,
    "accountType" to accountType.name,
    "email" to email,
    "photoUrl" to photoUrl,
    "city" to city,
    "bio" to bio,
    "status" to status.name,
    "isAdmin" to isAdmin,
    "privacy" to mapOf(
        "showCity" to privacy.showCity,
        "showBio" to privacy.showBio,
        "allowMessageRequests" to privacy.allowMessageRequests,
    ),
    "notificationSettings" to mapOf(
        "questionAnswered" to notificationSettings.questionAnswered,
        "newMessage" to notificationSettings.newMessage,
        "groupJoinRequest" to notificationSettings.groupJoinRequest,
        "groupReply" to notificationSettings.groupReply,
        "verificationResult" to notificationSettings.verificationResult,
        "safetyNotice" to notificationSettings.safetyNotice,
    ),
    "blockedUserIds" to blockedUserIds.toList(),
    "joinedAt" to joinedAt,
    "updatedAt" to updatedAt,
)

fun userFromDoc(d: DocumentSnapshot): User {
    val privacy = d.get("privacy") as? Map<*, *>
    val notif = d.get("notificationSettings") as? Map<*, *>
    return User(
        id = d.id,
        displayName = d.getString("displayName") ?: "",
        accountType = parseEnum(d.getString("accountType"), AccountType.FAMILY),
        email = d.getString("email"),
        photoUrl = d.getString("photoUrl"),
        city = d.getString("city"),
        bio = d.getString("bio"),
        status = parseEnum(d.getString("status"), AccountStatus.ACTIVE),
        isAdmin = d.getBoolean("isAdmin") ?: false,
        privacy = PrivacySettings(
            showCity = privacy.b("showCity", false),
            showBio = privacy.b("showBio", true),
            allowMessageRequests = privacy.b("allowMessageRequests", true),
        ),
        notificationSettings = NotificationSettings(
            questionAnswered = notif.b("questionAnswered", true),
            newMessage = notif.b("newMessage", true),
            groupJoinRequest = notif.b("groupJoinRequest", true),
            groupReply = notif.b("groupReply", true),
            verificationResult = notif.b("verificationResult", true),
            safetyNotice = notif.b("safetyNotice", true),
        ),
        blockedUserIds = d.strList("blockedUserIds").toSet(),
        joinedAt = d.getLong("joinedAt") ?: 0L,
        updatedAt = d.getLong("updatedAt") ?: 0L,
    )
}

// ---- ProfessionalProfile ----

fun ProfessionalProfile.toMap(): Map<String, Any?> = mapOf(
    "userId" to userId,
    "title" to title,
    "workAreas" to workAreas,
    "city" to city,
    "offersOnline" to offersOnline,
    "experience" to experience,
    "verificationStatus" to verificationStatus.name,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

fun professionalFromDoc(d: DocumentSnapshot) = ProfessionalProfile(
    id = d.id,
    userId = d.getString("userId") ?: "",
    title = d.getString("title") ?: "",
    workAreas = d.strList("workAreas"),
    city = d.getString("city"),
    offersOnline = d.getBoolean("offersOnline") ?: false,
    experience = d.getString("experience"),
    verificationStatus = parseEnum(d.getString("verificationStatus"), VerificationStatus.NONE),
    createdAt = d.getLong("createdAt") ?: 0L,
    updatedAt = d.getLong("updatedAt") ?: 0L,
)

// ---- VerificationRequest ----

fun VerificationRequest.toMap(): Map<String, Any?> = mapOf(
    "userId" to userId,
    "professionalTitle" to professionalTitle,
    "licenseNumber" to licenseNumber,
    "credentialNote" to credentialNote,
    "documentRefs" to documentRefs,
    "status" to status.name,
    "reviewerNote" to reviewerNote,
    "reviewedByAdminId" to reviewedByAdminId,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

fun verificationFromDoc(d: DocumentSnapshot) = VerificationRequest(
    id = d.id,
    userId = d.getString("userId") ?: "",
    professionalTitle = d.getString("professionalTitle") ?: "",
    licenseNumber = d.getString("licenseNumber"),
    credentialNote = d.getString("credentialNote"),
    documentRefs = d.strList("documentRefs"),
    status = parseEnum(d.getString("status"), VerificationStatus.PENDING),
    reviewerNote = d.getString("reviewerNote"),
    reviewedByAdminId = d.getString("reviewedByAdminId"),
    createdAt = d.getLong("createdAt") ?: 0L,
    updatedAt = d.getLong("updatedAt") ?: 0L,
)

// ---- ChildProfilePrivate ----

fun ChildProfilePrivate.toMap(): Map<String, Any?> = mapOf(
    "familyUserId" to familyUserId,
    "nickname" to nickname,
    "ageRange" to ageRange.name,
    "interests" to interests,
    "isPublic" to isPublic,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

fun childFromDoc(d: DocumentSnapshot) = ChildProfilePrivate(
    id = d.id,
    familyUserId = d.getString("familyUserId") ?: "",
    nickname = d.getString("nickname"),
    ageRange = parseEnum(d.getString("ageRange"), AgeRange.UNSPECIFIED),
    interests = d.strList("interests"),
    isPublic = d.getBoolean("isPublic") ?: false,
    createdAt = d.getLong("createdAt") ?: 0L,
    updatedAt = d.getLong("updatedAt") ?: 0L,
)

// ---- Question ----

fun Question.toMap(): Map<String, Any?> = mapOf(
    "authorId" to authorId,
    "isAnonymous" to isAnonymous,
    "category" to category.name,
    "title" to title,
    "body" to body,
    "status" to status.name,
    "commentsClosed" to commentsClosed,
    "answerCount" to answerCount,
    "helpfulCount" to helpfulCount,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

fun questionFromDoc(d: DocumentSnapshot) = Question(
    id = d.id,
    authorId = d.getString("authorId") ?: "",
    isAnonymous = d.getBoolean("isAnonymous") ?: false,
    category = parseEnum(d.getString("category"), QuestionCategory.FAMILY_EXPERIENCE),
    title = d.getString("title") ?: "",
    body = d.getString("body") ?: "",
    status = parseEnum(d.getString("status"), ContentStatus.PUBLISHED),
    commentsClosed = d.getBoolean("commentsClosed") ?: false,
    answerCount = (d.getLong("answerCount") ?: 0L).toInt(),
    helpfulCount = (d.getLong("helpfulCount") ?: 0L).toInt(),
    createdAt = d.getLong("createdAt") ?: 0L,
    updatedAt = d.getLong("updatedAt") ?: 0L,
)

// ---- Answer ----

fun Answer.toMap(): Map<String, Any?> = mapOf(
    "questionId" to questionId,
    "authorId" to authorId,
    "body" to body,
    "status" to status.name,
    "helpfulByUserIds" to helpfulByUserIds.toList(),
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

fun answerFromDoc(d: DocumentSnapshot) = Answer(
    id = d.id,
    questionId = d.getString("questionId") ?: "",
    authorId = d.getString("authorId") ?: "",
    body = d.getString("body") ?: "",
    status = parseEnum(d.getString("status"), ContentStatus.PUBLISHED),
    helpfulByUserIds = d.strList("helpfulByUserIds").toSet(),
    createdAt = d.getLong("createdAt") ?: 0L,
    updatedAt = d.getLong("updatedAt") ?: 0L,
)

// ---- Group ----

fun Group.toMap(): Map<String, Any?> = mapOf(
    "ownerId" to ownerId,
    "title" to title,
    "description" to description,
    "category" to category,
    "city" to city,
    "visibility" to visibility.name,
    "rules" to rules,
    "memberCount" to memberCount,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

fun groupFromDoc(d: DocumentSnapshot) = Group(
    id = d.id,
    ownerId = d.getString("ownerId") ?: "",
    title = d.getString("title") ?: "",
    description = d.getString("description") ?: "",
    category = d.getString("category"),
    city = d.getString("city"),
    visibility = parseEnum(d.getString("visibility"), GroupVisibility.PUBLIC),
    rules = d.getString("rules"),
    memberCount = (d.getLong("memberCount") ?: 0L).toInt(),
    createdAt = d.getLong("createdAt") ?: 0L,
    updatedAt = d.getLong("updatedAt") ?: 0L,
)

// ---- GroupMembership ----

fun GroupMembership.toMap(): Map<String, Any?> = mapOf(
    "groupId" to groupId,
    "userId" to userId,
    "role" to role.name,
    "status" to status.name,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

fun membershipFromDoc(d: DocumentSnapshot) = GroupMembership(
    id = d.id,
    groupId = d.getString("groupId") ?: "",
    userId = d.getString("userId") ?: "",
    role = parseEnum(d.getString("role"), MembershipRole.MEMBER),
    status = parseEnum(d.getString("status"), MembershipStatus.ACTIVE),
    createdAt = d.getLong("createdAt") ?: 0L,
    updatedAt = d.getLong("updatedAt") ?: 0L,
)

// ---- GroupPost ----

fun GroupPost.toMap(): Map<String, Any?> = mapOf(
    "groupId" to groupId,
    "authorId" to authorId,
    "body" to body,
    "status" to status.name,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

fun postFromDoc(d: DocumentSnapshot) = GroupPost(
    id = d.id,
    groupId = d.getString("groupId") ?: "",
    authorId = d.getString("authorId") ?: "",
    body = d.getString("body") ?: "",
    status = parseEnum(d.getString("status"), ContentStatus.PUBLISHED),
    createdAt = d.getLong("createdAt") ?: 0L,
    updatedAt = d.getLong("updatedAt") ?: 0L,
)

// ---- Conversation ----

fun Conversation.toMap(): Map<String, Any?> = mapOf(
    "participantIds" to participantIds,
    "initiatorId" to initiatorId,
    "status" to status.name,
    "mutedByUserIds" to mutedByUserIds.toList(),
    "endedByUserId" to endedByUserId,
    "lastMessagePreview" to lastMessagePreview,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

fun conversationFromDoc(d: DocumentSnapshot) = Conversation(
    id = d.id,
    participantIds = d.strList("participantIds"),
    initiatorId = d.getString("initiatorId") ?: "",
    status = parseEnum(d.getString("status"), ConversationStatus.REQUESTED),
    mutedByUserIds = d.strList("mutedByUserIds").toSet(),
    endedByUserId = d.getString("endedByUserId"),
    lastMessagePreview = d.getString("lastMessagePreview"),
    createdAt = d.getLong("createdAt") ?: 0L,
    updatedAt = d.getLong("updatedAt") ?: 0L,
)

// ---- Message ----

fun Message.toMap(): Map<String, Any?> = mapOf(
    "conversationId" to conversationId,
    "senderId" to senderId,
    "body" to body,
    "createdAt" to createdAt,
)

fun messageFromDoc(d: DocumentSnapshot) = Message(
    id = d.id,
    conversationId = d.getString("conversationId") ?: "",
    senderId = d.getString("senderId") ?: "",
    body = d.getString("body") ?: "",
    createdAt = d.getLong("createdAt") ?: 0L,
)

// ---- GuideArticle ----

fun GuideArticle.toMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "body" to body,
    "category" to category,
    "authorName" to authorName,
    "authorId" to authorId,
    "authorIsVerifiedProfessional" to authorIsVerifiedProfessional,
    "status" to status.name,
    "sources" to sources,
    "savedByUserIds" to savedByUserIds.toList(),
    "lastReviewedAt" to lastReviewedAt,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

fun guideFromDoc(d: DocumentSnapshot) = GuideArticle(
    id = d.id,
    title = d.getString("title") ?: "",
    body = d.getString("body") ?: "",
    category = d.getString("category") ?: "",
    authorName = d.getString("authorName") ?: "",
    authorId = d.getString("authorId"),
    authorIsVerifiedProfessional = d.getBoolean("authorIsVerifiedProfessional") ?: false,
    status = parseEnum(d.getString("status"), ContentStatus.PUBLISHED),
    sources = d.strList("sources"),
    savedByUserIds = d.strList("savedByUserIds").toSet(),
    lastReviewedAt = d.getLong("lastReviewedAt") ?: 0L,
    createdAt = d.getLong("createdAt") ?: 0L,
    updatedAt = d.getLong("updatedAt") ?: 0L,
)

// ---- Report ----

fun Report.toMap(): Map<String, Any?> = mapOf(
    "reporterId" to reporterId,
    "contentType" to contentType.name,
    "contentId" to contentId,
    "reason" to reason.name,
    "note" to note,
    "status" to status.name,
    "reviewedByAdminId" to reviewedByAdminId,
    "resolutionNote" to resolutionNote,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

fun reportFromDoc(d: DocumentSnapshot) = Report(
    id = d.id,
    reporterId = d.getString("reporterId") ?: "",
    contentType = parseEnum(d.getString("contentType"), ContentType.QUESTION),
    contentId = d.getString("contentId") ?: "",
    reason = parseEnum(d.getString("reason"), ReportReason.OTHER),
    note = d.getString("note"),
    status = parseEnum(d.getString("status"), ReportStatus.OPEN),
    reviewedByAdminId = d.getString("reviewedByAdminId"),
    resolutionNote = d.getString("resolutionNote"),
    createdAt = d.getLong("createdAt") ?: 0L,
    updatedAt = d.getLong("updatedAt") ?: 0L,
)

// ---- Notification ----

fun Notification.toMap(): Map<String, Any?> = mapOf(
    "userId" to userId,
    "type" to type.name,
    "title" to title,
    "body" to body,
    "relatedId" to relatedId,
    "read" to read,
    "createdAt" to createdAt,
)

fun notificationFromDoc(d: DocumentSnapshot) = Notification(
    id = d.id,
    userId = d.getString("userId") ?: "",
    type = parseEnum(d.getString("type"), NotificationType.SAFETY_NOTICE),
    title = d.getString("title") ?: "",
    body = d.getString("body") ?: "",
    relatedId = d.getString("relatedId"),
    read = d.getBoolean("read") ?: false,
    createdAt = d.getLong("createdAt") ?: 0L,
)

// ---- AdminAction ----

fun AdminAction.toMap(): Map<String, Any?> = mapOf(
    "adminId" to adminId,
    "actionType" to actionType.name,
    "targetType" to targetType.name,
    "targetId" to targetId,
    "reason" to reason,
    "createdAt" to createdAt,
)

fun adminActionFromDoc(d: DocumentSnapshot) = AdminAction(
    id = d.id,
    adminId = d.getString("adminId") ?: "",
    actionType = parseEnum(d.getString("actionType"), AdminActionType.RESOLVE_REPORT),
    targetType = parseEnum(d.getString("targetType"), ContentType.USER),
    targetId = d.getString("targetId") ?: "",
    reason = d.getString("reason") ?: "",
    createdAt = d.getLong("createdAt") ?: 0L,
)
