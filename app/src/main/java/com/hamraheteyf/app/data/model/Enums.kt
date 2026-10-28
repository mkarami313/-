package com.hamraheteyf.app.data.model

import kotlinx.serialization.Serializable

/**
 * Enumerations shared across the data model.
 *
 * Each enum carries a Persian [label] for display. Persisted values use the
 * stable enum [name] (English), so UI language can change without migrating data.
 */

/** Account type chosen at sign-up. Choosing [PROFESSIONAL] does NOT grant a verified badge. */
@Serializable
enum class AccountType(val label: String) {
    FAMILY("خانواده یا مراقب کودک"),
    PROFESSIONAL("پزشک یا متخصص سلامت"),
    EDUCATOR("مربی یا آموزگار"),
    SOCIAL_WORKER("مددکار اجتماعی یا مشاور"),
    COMMUNITY("عضو جامعه / همراه داوطلب");
}

/** Lifecycle state of an account. */
@Serializable
enum class AccountStatus(val label: String) {
    ACTIVE("عادی"),
    LIMITED("محدود"),
    BLOCKED("مسدود"),
    PENDING_REVIEW("در انتظار بررسی");
}

/** Verification state of a professional profile. Only an admin can move this forward. */
@Serializable
enum class VerificationStatus(val label: String) {
    NONE("بدون درخواست"),
    PENDING("در انتظار بررسی"),
    VERIFIED("تأییدشده"),
    REJECTED("ردشده"),
    MORE_INFO_NEEDED("نیازمند اطلاعات بیشتر");
}

/**
 * The badge shown next to authored content. Derived from the author's account type
 * and verification status — never set directly by the user.
 */
@Serializable
enum class AuthorBadge(val label: String) {
    FAMILY("خانواده"),
    COMMUNITY("عضو جامعه"),
    PROFESSIONAL_UNVERIFIED("متخصص تأییدنشده"),
    PROFESSIONAL_VERIFIED("متخصص تأییدشده");
}

@Serializable
enum class QuestionCategory(val label: String) {
    COMMUNICATION("ارتباط"),
    EDUCATION("آموزش"),
    SCHOOL("مدرسه"),
    DAILY_SKILLS("مهارت‌های روزمره"),
    SUPPORT_SERVICES("خدمات حمایتی"),
    FAMILY_EXPERIENCE("تجربهٔ خانواده");
}

@Serializable
enum class QuestionSort(val label: String) {
    NEWEST("تازه‌ترین"),
    MOST_HELPFUL("مفیدترین");
}

@Serializable
enum class GroupVisibility(val label: String) {
    PUBLIC("عمومی"),
    PRIVATE("خصوصی");
}

@Serializable
enum class MembershipRole(val label: String) {
    OWNER("مدیر گروه"),
    MODERATOR("مدیر کمکی"),
    MEMBER("عضو");
}

@Serializable
enum class MembershipStatus(val label: String) {
    REQUESTED("در انتظار تأیید"),
    ACTIVE("عضو"),
    REMOVED("خارج‌شده");
}

@Serializable
enum class ConversationStatus(val label: String) {
    REQUESTED("درخواست گفتگو"),
    ACCEPTED("فعال"),
    DECLINED("ردشده"),
    ENDED("پایان‌یافته"),
    BLOCKED("مسدود");
}

@Serializable
enum class ReportReason(val label: String) {
    DANGEROUS_ADVICE("اطلاعات خطرناک یا توصیهٔ پزشکی نامطمئن"),
    HARASSMENT("آزار، توهین یا تهدید"),
    PRIVACY("افشای اطلاعات خصوصی"),
    SPAM("تبلیغ یا هرزنامه"),
    IMPERSONATION("جعل هویت یا ادعای تخصص نادرست"),
    OTHER("سایر موارد");
}

@Serializable
enum class ReportStatus(val label: String) {
    OPEN("باز"),
    UNDER_REVIEW("در حال بررسی"),
    ACTIONED("رسیدگی‌شده"),
    DISMISSED("رد شد");
}

/** Type of content a report or notification points to. */
@Serializable
enum class ContentType(val label: String) {
    QUESTION("پرسش"),
    ANSWER("پاسخ"),
    GROUP_POST("پست گروه"),
    MESSAGE("پیام"),
    USER("کاربر"),
    GUIDE("راهنما");
}

@Serializable
enum class NotificationType(val label: String) {
    QUESTION_ANSWERED("پاسخ به پرسش"),
    NEW_MESSAGE("پیام جدید"),
    GROUP_JOIN_REQUEST("درخواست عضویت در گروه"),
    GROUP_REPLY("پاسخ در گروه"),
    VERIFICATION_RESULT("نتیجهٔ بررسی تخصص"),
    SAFETY_NOTICE("اطلاعیهٔ ایمنی یا تغییر قوانین");
}

@Serializable
enum class AdminActionType(val label: String) {
    VERIFY_PROFESSIONAL("تأیید تخصص"),
    REJECT_PROFESSIONAL("رد تخصص"),
    REQUEST_MORE_INFO("درخواست اطلاعات بیشتر"),
    VIEW_CREDENTIAL("مشاهدهٔ مدرک"),
    HIDE_CONTENT("پنهان‌کردن محتوا"),
    DELETE_CONTENT("حذف محتوا"),
    LIMIT_ACCOUNT("محدودکردن حساب"),
    BLOCK_ACCOUNT("مسدودکردن حساب"),
    RESOLVE_REPORT("رسیدگی به گزارش"),
    PUBLISH_GUIDE("انتشار راهنما");
}
