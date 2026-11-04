package com.hamraheteyf.app.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.hamraheteyf.app.community.*
import com.hamraheteyf.app.ui.forum.*
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
}
