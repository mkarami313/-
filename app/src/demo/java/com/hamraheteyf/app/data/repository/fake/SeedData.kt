package com.hamraheteyf.app.data.repository.fake

import com.hamraheteyf.app.data.model.*

/**
 * Non-real demo data used in DEMO mode.
 *
 * IMPORTANT: all names, emails and content here are fictional and for demonstration only.
 * Nothing in this file represents a real person, a real credential, or verified medical
 * advice. Verified badges below exist purely to exercise the UI.
 */
object SeedData {

    // User ids referenced across the app/tests.
    const val U_ADMIN = "u_admin"
    const val U_FAMILY1 = "u_family1"
    const val U_FAMILY2 = "u_family2"
    const val U_PROF_VERIFIED = "u_prof_v"
    const val U_PROF_PENDING = "u_prof_p"
    const val U_EDUCATOR = "u_edu"
    const val U_COMMUNITY = "u_comm"

    /** Shared password for all seeded demo accounts. */
    const val DEMO_PASSWORD = "demo1234"

    const val EMAIL_ADMIN = "admin@example.com"
    const val EMAIL_FAMILY1 = "zahra@example.com"

    private const val BASE = 1_730_000_000_000L // fixed, fictional timestamps
    private fun t(hoursAgo: Long) = BASE - hoursAgo * 3_600_000L

    fun credentials(): Map<String, String> = listOf(
        EMAIL_ADMIN,
        EMAIL_FAMILY1,
        "parisa@example.com",
        "dr.verified@example.com",
        "newpro@example.com",
        "teacher@example.com",
        "volunteer@example.com",
    ).associate { it to DEMO_PASSWORD }

    fun initial(): Db {
        val users = listOf(
            User(
                id = U_ADMIN, displayName = "مدیر سامانه", accountType = AccountType.COMMUNITY,
                email = EMAIL_ADMIN, isAdmin = true, bio = "حساب مدیریت نمونه برای بررسی گزارش‌ها و تأیید تخصص‌ها.",
                joinedAt = t(2000), updatedAt = t(2000),
            ),
            User(
                id = U_FAMILY1, displayName = "زهرا", accountType = AccountType.FAMILY,
                email = EMAIL_FAMILY1, city = "تهران", bio = "مادر یک کودک در طیف اوتیسم.",
                privacy = PrivacySettings(showCity = true), joinedAt = t(400), updatedAt = t(400),
            ),
            User(
                id = U_FAMILY2, displayName = "خانوادهٔ نمونه", accountType = AccountType.FAMILY,
                email = "parisa@example.com", city = "اصفهان", bio = "تازه با این فضا آشنا شده‌ام.",
                joinedAt = t(300), updatedAt = t(300),
            ),
            User(
                id = U_PROF_VERIFIED, displayName = "دکتر نمونه", accountType = AccountType.PROFESSIONAL,
                email = "dr.verified@example.com", city = "تهران",
                bio = "روان‌پزشک کودک و نوجوان (نمونهٔ آزمایشی).",
                joinedAt = t(900), updatedAt = t(100),
            ),
            User(
                id = U_PROF_PENDING, displayName = "متخصص تازه‌وارد", accountType = AccountType.PROFESSIONAL,
                email = "newpro@example.com", city = "شیراز",
                bio = "گفتاردرمانگر (در انتظار بررسی).",
                joinedAt = t(80), updatedAt = t(80),
            ),
            User(
                id = U_EDUCATOR, displayName = "مربی نمونه", accountType = AccountType.EDUCATOR,
                email = "teacher@example.com", city = "مشهد",
                bio = "مربی مهارت‌های ارتباطی.", joinedAt = t(700), updatedAt = t(120),
            ),
            User(
                id = U_COMMUNITY, displayName = "همراه داوطلب", accountType = AccountType.COMMUNITY,
                email = "volunteer@example.com", bio = "دوست دارم تجربه‌هایم را به اشتراک بگذارم.",
                joinedAt = t(500), updatedAt = t(500),
            ),
        )

        val professionals = listOf(
            ProfessionalProfile(
                id = "p_prof_v", userId = U_PROF_VERIFIED, title = "روان‌پزشک کودک و نوجوان",
                workAreas = listOf("ارزیابی", "مشاورهٔ خانواده"), city = "تهران", offersOnline = true,
                experience = "نمونهٔ آزمایشی سابقه.", verificationStatus = VerificationStatus.VERIFIED,
                createdAt = t(900), updatedAt = t(100),
            ),
            ProfessionalProfile(
                id = "p_prof_p", userId = U_PROF_PENDING, title = "گفتاردرمانگر",
                workAreas = listOf("گفتاردرمانی"), city = "شیراز", offersOnline = false,
                verificationStatus = VerificationStatus.PENDING, createdAt = t(80), updatedAt = t(80),
            ),
            ProfessionalProfile(
                id = "p_edu", userId = U_EDUCATOR, title = "مربی مهارت‌های ارتباطی",
                workAreas = listOf("آموزش", "بازی‌درمانی"), city = "مشهد", offersOnline = true,
                verificationStatus = VerificationStatus.VERIFIED, createdAt = t(700), updatedAt = t(120),
            ),
        )

        val verifications = listOf(
            VerificationRequest(
                id = "v_prof_v", userId = U_PROF_VERIFIED, professionalTitle = "روان‌پزشک کودک و نوجوان",
                licenseNumber = "DEMO-0000", credentialNote = "مدرک نمونه (آزمایشی).",
                status = VerificationStatus.VERIFIED, reviewedByAdminId = U_ADMIN,
                createdAt = t(880), updatedAt = t(100),
            ),
            VerificationRequest(
                id = "v_prof_p", userId = U_PROF_PENDING, professionalTitle = "گفتاردرمانگر",
                licenseNumber = "DEMO-1111", credentialNote = "در انتظار بررسی مدارک (آزمایشی).",
                status = VerificationStatus.PENDING, createdAt = t(80), updatedAt = t(80),
            ),
        )

        val children = listOf(
            ChildProfilePrivate(
                id = "c_1", familyUserId = U_FAMILY1, nickname = "ستاره", ageRange = AgeRange.AGE_3_6,
                interests = listOf("موسیقی", "نقاشی"), isPublic = false, createdAt = t(390), updatedAt = t(390),
            ),
        )

        val questions = listOf(
            Question(
                id = "q_1", authorId = U_FAMILY1, category = QuestionCategory.COMMUNICATION,
                title = "راهکارهای ساده برای تقویت ارتباط کلامی در خانه؟",
                body = "به‌دنبال تمرین‌های ساده و بدون فشار برای استفاده در خانه هستم.",
                answerCount = 2, helpfulCount = 3, createdAt = t(40), updatedAt = t(40),
            ),
            Question(
                id = "q_2", authorId = U_FAMILY2, isAnonymous = true, category = QuestionCategory.SCHOOL,
                title = "آماده‌سازی برای شروع مدرسه",
                body = "برای انتقال آرام‌تر به محیط مدرسه چه کارهایی می‌توان انجام داد؟",
                answerCount = 1, helpfulCount = 1, createdAt = t(20), updatedAt = t(20),
            ),
            Question(
                id = "q_3", authorId = U_FAMILY2, category = QuestionCategory.DAILY_SKILLS,
                title = "روتین روزانهٔ آرام",
                body = "تجربهٔ شما برای ساختن یک روتین روزانهٔ قابل پیش‌بینی چیست؟",
                answerCount = 0, helpfulCount = 0, createdAt = t(10), updatedAt = t(10),
            ),
        )

        val answers = listOf(
            Answer(
                id = "a_1", questionId = "q_1", authorId = U_PROF_VERIFIED,
                body = "تمرین‌های کوتاه و مبتنی بر علاقهٔ کودک معمولاً مؤثرترند. این نکته جایگزین ارزیابی تخصصی حضوری نیست.",
                helpfulByUserIds = setOf(U_FAMILY1, U_FAMILY2, U_COMMUNITY), createdAt = t(38), updatedAt = t(38),
            ),
            Answer(
                id = "a_2", questionId = "q_1", authorId = U_COMMUNITY,
                body = "ما از کتاب‌های تصویری ساده استفاده کردیم و کمک‌کننده بود.",
                helpfulByUserIds = emptySet(), createdAt = t(30), updatedAt = t(30),
            ),
            Answer(
                id = "a_3", questionId = "q_2", authorId = U_EDUCATOR,
                body = "بازدید از مدرسه پیش از شروع و آشناسازی تدریجی با محیط می‌تواند کمک کند.",
                helpfulByUserIds = setOf(U_FAMILY2), createdAt = t(18), updatedAt = t(18),
            ),
        )

        val groups = listOf(
            Group(
                id = "g_public", ownerId = U_FAMILY1, title = "خانواده‌های تازه‌وارد",
                description = "فضایی برای پرسش‌های اولیه و آشنایی.", category = "حمایت از مراقبان",
                city = "تهران", visibility = GroupVisibility.PUBLIC,
                rules = "با احترام گفتگو کنیم و اطلاعات خصوصی کودک را منتشر نکنیم.",
                memberCount = 2, createdAt = t(200), updatedAt = t(50),
            ),
            Group(
                id = "g_private", ownerId = U_EDUCATOR, title = "تجربه‌های مدرسه",
                description = "گروه خصوصی برای گفتگوی خانواده‌ها و مربیان دربارهٔ مدرسه.",
                category = "مدرسه", city = "مشهد", visibility = GroupVisibility.PRIVATE,
                rules = "محتوای این گروه خصوصی است.", memberCount = 2, createdAt = t(180), updatedAt = t(60),
            ),
        )

        val memberships = listOf(
            GroupMembership("m_1", "g_public", U_FAMILY1, MembershipRole.OWNER, MembershipStatus.ACTIVE, t(200), t(200)),
            GroupMembership("m_2", "g_public", U_PROF_VERIFIED, MembershipRole.MEMBER, MembershipStatus.ACTIVE, t(90), t(90)),
            GroupMembership("m_3", "g_private", U_EDUCATOR, MembershipRole.OWNER, MembershipStatus.ACTIVE, t(180), t(180)),
            GroupMembership("m_4", "g_private", U_FAMILY2, MembershipRole.MEMBER, MembershipStatus.ACTIVE, t(70), t(70)),
        )

        val posts = listOf(
            GroupPost("gp_1", "g_public", U_FAMILY1, "خوش آمدید! اینجا می‌توانید پرسش‌های اولیه‌تان را مطرح کنید.", createdAt = t(48), updatedAt = t(48)),
        )

        val guides = listOf(
            GuideArticle(
                id = "guide_1", title = "شروع یک گفتگوی آرام با کودک",
                body = "این راهنما چند ایدهٔ عمومی برای ایجاد فضای ارتباطی آرام ارائه می‌دهد. محتوای این راهنما توصیهٔ شخصی پزشکی نیست.",
                category = "ارتباط", authorName = "تیم محتوای نمونه", authorIsVerifiedProfessional = true,
                sources = listOf("منبع نمونه ۱"), lastReviewedAt = t(60), createdAt = t(300), updatedAt = t(60),
            ),
            GuideArticle(
                id = "guide_2", title = "آماده‌شدن برای جلسهٔ مدرسه",
                body = "فهرستی از پرسش‌هایی که می‌توانید در جلسهٔ مدرسه بپرسید. این مطلب جایگزین مشاورهٔ تخصصی نیست.",
                category = "مدرسه", authorName = "تیم محتوای نمونه",
                sources = listOf("منبع نمونه ۲"), lastReviewedAt = t(90), createdAt = t(280), updatedAt = t(90),
            ),
        )

        val notifications = listOf(
            Notification("n_1", U_FAMILY1, NotificationType.QUESTION_ANSWERED, "پاسخ تازه", "به یکی از پرسش‌های شما پاسخ داده شد.", relatedId = "q_1", read = false, createdAt = t(38)),
            Notification("n_2", U_FAMILY1, NotificationType.SAFETY_NOTICE, "یادآوری ایمنی", "به‌یاد داشته باشید این اپ جایگزین خدمات پزشکی فوری نیست.", read = true, createdAt = t(100)),
        )

        return Db(
            users = users,
            professionals = professionals,
            verifications = verifications,
            children = children,
            questions = questions,
            answers = answers,
            groups = groups,
            memberships = memberships,
            posts = posts,
            guides = guides,
            notifications = notifications,
        )
    }
}
