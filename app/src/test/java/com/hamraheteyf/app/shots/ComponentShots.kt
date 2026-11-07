package com.hamraheteyf.app.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.hamraheteyf.app.community.*
import com.hamraheteyf.app.ui.common.ReviewsContent
import com.hamraheteyf.app.ui.forum.*
import com.hamraheteyf.app.ui.groups.GroupHeader
import com.hamraheteyf.app.ui.groups.GroupRow
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.HamrahTheme
import org.junit.Rule
import org.junit.Test

/** Visual check of the community building blocks with sample data: `gradle :app:recordPaparazziRelease`. */
class ComponentShots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.Light.NoActionBar", maxPercentDifference = 1.0)

    private val now = System.currentTimeMillis()
    private val mom = Person("u1", "مریم احمدی", badge = "خانواده")
    private val doc = Person("u2", "دکتر سارا رضایی", badge = "گفتاردرمان", expert = true)

    private fun page(content: @Composable ColumnScope.() -> Unit) = paparazzi.snapshot {
        HamrahTheme {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter, vertical = Dimens.s), verticalArrangement = Arrangement.spacedBy(Dimens.cardGap), content = content)
        }
    }

    private val tree = listOf(
        Forum("diag", null, "شک و تشخیص", "از اولین نشانه‌ها تا گرفتنِ تشخیص", "search", "#0E6E62", topicCount = 128, lastPostAt = now - 600_000,
            children = listOf(Forum("a", "diag", "نشانه‌های اولیه", topicCount = 40), Forum("b", "diag", "ارزیابی و تست‌ها", topicCount = 51), Forum("c", "diag", "تازه تشخیص گرفته‌ایم", topicCount = 37))),
        Forum("therapy", null, "درمان و توان‌بخشی", "تجربهٔ درمان‌ها از زبانِ خانواده‌ها", "healing", "#3B5BDB", topicCount = 342, lastPostAt = now - 3_600_000,
            children = listOf(Forum("d", "therapy", "گفتاردرمانی", topicCount = 120), Forum("e", "therapy", "کاردرمانی و یکپارچگی حسی", topicCount = 98), Forum("f", "therapy", "رفتاردرمانی (ABA)", topicCount = 64))),
    )

    @Test fun forumHub() = page {
        ClinicEntryCard(Forum("clinic", null, "کلینیک: پرسش از متخصص", "پاسخِ متخصصانِ تأییدشده", "medical", "#1971C2", "clinic", topicCount = 76), {})
        GroupsEntryCard({})
        tree.forEach { ForumTreeCard(it, {}) }
    }

    @Test fun topic() = page {
        val t = TopicFull("t1", forumId = "daily_sleep", title = "پسرم شب‌ها دیر می‌خوابد؛ چه کنیم؟",
            body = "پسرم چهار سالش است و تشخیصِ طیف دارد. شب‌ها تا ساعتِ دو بیدار می‌ماند و صبح‌ها خسته است. کسی تجربهٔ روتینِ خواب یا ملاتونین دارد؟",
            author = mom, createdAt = now - 7_200_000, replyCount = 12, likeCount = 8, viewCount = 230, expertReplyCount = 1, liked = false,
            poll = Poll("کدام روش برای شما جواب داد؟", listOf("روتینِ ثابت", "ملاتونین با نظرِ پزشک", "کاهشِ صفحه‌نمایش"), listOf(14, 6, 9), voters = 29, myVotes = listOf(0)))
        OpeningPostCard(t, null, collapsed = false, onToggle = {}, onOpenProfile = {}, onLike = {}, onQuote = {},
            poll = { PollCard(t.poll!!, busy = false, onVote = {}) })
        PostCard(ForumPost("p1", doc, "روتینِ ثابتِ پیش از خواب (حمام، کتاب، نورِ کم) در بیشترِ کودکان مؤثر است. برای دارو حتماً با پزشک مشورت کنید.", expert = true, accepted = true, createdAt = now - 3_000_000, likeCount = 15, liked = true),
            1, null, canAccept = true, onOpenProfile = {}, onLike = {}, onQuote = {}, onAccept = {}, onEdit = {}, onDelete = {}, onReport = {})
        PostCard(ForumPost("p2", mom, "ممنون! از امشب امتحان می‌کنیم.", quote = QuoteRef("p1", "دکتر سارا رضایی", "روتینِ ثابتِ پیش از خواب (حمام، کتاب، نورِ کم) در بیشترِ کودکان مؤثر است…"), createdAt = now - 600_000, likeCount = 2, mine = true, canEdit = true, canDelete = true),
            2, null, canAccept = false, onOpenProfile = {}, onLike = {}, onQuote = {}, onAccept = {}, onEdit = {}, onDelete = {}, onReport = {})
        PagerBar(1, 3, {})
    }

    // ---- topic lists, group page, reviews, reply bar (second audit pass)

    private val topics = listOf(
        TopicCard("t1", forumId = "a", title = "پسرم شب‌ها دیر می‌خوابد؛ چه کنیم؟ تجربهٔ روتینِ خواب و ملاتونین",
            excerpt = "پسرم چهار سالش است و تشخیصِ طیف دارد. شب‌ها تا ساعتِ دو بیدار می‌ماند و صبح‌ها خسته است.",
            author = mom, createdAt = now - 7_200_000, lastPostAt = now - 600_000, replyCount = 12, likeCount = 8, viewCount = 1230,
            expertReplyCount = 1, unread = true, pinned = true, hasPoll = true),
        TopicCard("t2", forumId = "b", title = "ارزیابیِ ADOS کجا انجام می‌شود؟", author = Person(name = "کاربرِ ناشناس", anonymous = true),
            kind = "question", createdAt = now - 86_400_000, lastPostAt = now - 86_400_000, bookmarked = true, locked = true),
        TopicCard("t3", groupId = "g1", groupTitle = "خانواده‌های اوتیسم در شیراز", title = "دورهمیِ ماهانه",
            excerpt = "جمعه ساعت ۵ در پارکِ آزادی", author = doc, createdAt = now - 3 * 86_400_000, lastPostAt = now - 3 * 86_400_000,
            replyCount = 3, likeCount = 25, viewCount = 88),
    )

    private val group = GroupInfo(
        GroupDetail("g1", "خانواده‌های اوتیسم در شیراز", "گروهی برای هم‌فکری و دورهمیِ خانواده‌های کودکانِ طیف در شیراز و اطراف.",
            rules = "احترام، بدونِ تبلیغ، حفظِ حریمِ کودکان", city = "شیراز", visibility = "PRIVATE", memberCount = 132, ownerId = "u1"),
        myStatus = "ACTIVE", myRole = "OWNER", notify = true, canManage = true, isOwner = true,
    )

    @Test fun topicRows() = page {
        val titles = mapOf("a" to "نشانه‌های اولیه", "b" to "ارزیابی و تست‌ها")
        topics.forEach { t ->
            TopicRow(t, {}, place = t.forumId?.let { titles[it] } ?: t.groupTitle?.let { "گروهِ $it" }, client = null)
        }
        GroupRow(GroupItem("g1", "خانواده‌های اوتیسم در شیراز", "هم‌فکری و دورهمیِ ماهانه", city = "شیراز", visibility = "PRIVATE", memberCount = 132, myStatus = "REQUESTED"), null, {})
        GroupRow(GroupItem("g2", "گفتاردرمانیِ در خانه", memberCount = 1203, myStatus = "ACTIVE", myRole = "OWNER"), null, {})
    }

    @Test fun topicRowsLargeFont() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.3f))
        topicRows()
    }

    @Test fun groupHeader() = paparazzi.snapshot {
        HamrahTheme {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                GroupHeader(group, null, busy = false, onJoin = {}, onCancel = {}, onNotify = {}, onLeave = {})
                GroupHeader(group.copy(myStatus = null, myRole = null, isOwner = false, canManage = false,
                    group = group.group.copy(rules = "", description = "")), null, busy = false, onJoin = {}, onCancel = {}, onNotify = {}, onLeave = {})
                GroupHeader(group.copy(myStatus = "REQUESTED", myRole = null, isOwner = false), null, busy = false, onJoin = {}, onCancel = {}, onNotify = {}, onLeave = {})
            }
        }
    }

    @Test fun groupHeaderLargeFont() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.3f))
        groupHeader()
    }

    @Test fun reviews() = page {
        AppCard {
            ReviewsContent(
                ReviewList(listOf(
                    Review(mom, 5, "بسیار صبور و دلسوز؛ پسرم بعد از سه ماه چند کلمهٔ تازه یاد گرفت.", now - 86_400_000, mine = true),
                    Review(Person("u3", "علی کریمی", badge = "خانواده"), 3, "", now - 5 * 86_400_000),
                ), count = 12, average = 4.5),
                error = null, canReview = true, onRetry = {}, onWrite = {}, onDelete = {},
            )
        }
        AppCard { ReviewsContent(ReviewList(count = 0), null, canReview = true, onRetry = {}, onWrite = {}, onDelete = {}) }
        AppCard { ReviewsContent(null, "خطای ارتباط با سرور.", canReview = true, onRetry = {}, onWrite = {}, onDelete = {}) }
    }

    @Test fun replyComposer() = paparazzi.snapshot {
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) = Unit
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        HamrahTheme {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                val uploads = ImageUploads(null, LocalContext.current, rememberCoroutineScope(), 6).apply { setExisting(listOf("a".repeat(32))) }
                val empty = ImageUploads(null, LocalContext.current, rememberCoroutineScope(), 6)
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), verticalArrangement = Arrangement.spacedBy(Dimens.l)) {
                    ReplyComposer(true, "", "", {}, null, {}, empty, null, false, {})
                    ReplyComposer(true, "", "ممنون از راهنمایی‌تان؛ ما هم روتینِ حمام و کتاب را امتحان کردیم و جواب داد.", {},
                        QuoteRef("p1", "دکتر سارا رضایی", "روتینِ ثابتِ پیش از خواب (حمام، کتاب، نورِ کم) در بیشترِ کودکان مؤثر است…"), {}, uploads, null, false, {})
                    ReplyComposer(false, "در کلینیک فقط متخصصانِ تأییدشده و پرسش‌کننده پاسخ می‌دهند.", "", {}, null, {}, empty, null, false, {})
                }
            }
        }
    }
}
