package com.hamraheteyf.app.ui.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.ui.common.InfoBanner

private val COMMUNITY_RULES = """
استفاده از «همراهِ طیف» برای همه رایگان است و هیچ پرداخت، اشتراک یا تبلیغی در آن وجود ندارد.

۱. این برنامه جایگزین پزشک، درمانگر، تشخیص حرفه‌ای یا خدمات اورژانسی نیست. در شرایط اضطراری با خدمات فوریت تماس بگیرید.

۲. هر کاربر مسئول محتوایی است که منتشر می‌کند. با احترام و بدون قضاوت گفتگو کنید.

۳. نشان «تخصص تأییدشده» فقط به‌معنای بررسی مدارک ارائه‌شده است و تضمین نتیجه یا کیفیت درمان نیست.

۴. لطفاً اطلاعات خصوصی خود یا کودک را بی‌دلیل منتشر نکنید؛ از انتشار نام کامل، آدرس، شمارهٔ پرونده یا تصاویر حساس کودک خودداری کنید.

۵. تبلیغ، درخواست پول از خانواده‌ها، فروش خدمات در گروه‌ها یا وعدهٔ درمان قطعی ممنوع است.

۶. محتوای دارای اطلاعات خطرناک، آزار، افشای حریم خصوصی، جعل هویت یا هرزنامه حذف خواهد شد.

۷. سازوکار گزارش: هر محتوا را می‌توانید گزارش کنید. گزارش‌ها توسط مدیران بررسی و در صورت لزوم محتوا پنهان یا حذف و حساب متخلف محدود یا مسدود می‌شود.

این متن یک پیش‌نویس قابل بررسی است و ادعای انطباق حقوقی قطعی ندارد.
""".trimIndent()

private val PRIVACY_POLICY = """
این سیاست حریم خصوصی یک پیش‌نویس است و باید پیش از انتشار واقعی توسط کارشناس حقوقی بازبینی شود.

گردآوری داده:
- اپ می‌کوشد کمترین اطلاعات لازم را جمع‌آوری کند. اطلاعات کودک اختیاری و حداقلی است (نام مستعار، بازهٔ سنی، علاقه‌ها) و به‌صورت عمومی نمایش داده نمی‌شود.
- مدارک حرفه‌ای فقط برای بررسی مدیران ارسال می‌شوند و در پروفایل عمومی دیده نمی‌شوند.

دسترسی:
- هر کاربر فقط به داده‌های مجاز خود و گروه‌هایی که عضو آن‌هاست دسترسی دارد.
- پیام‌ها و داده‌های خصوصی برای عموم قابل خواندن نیستند.

اعلان‌ها:
- در اعلان گوشی، محتوای حساس پیام یا اطلاعات کودک نمایش داده نمی‌شود.

حذف داده:
- می‌توانید حساب و داده‌های شخصی خود را حذف کنید. با حذف حساب، اطلاعات شخصی حذف و مشارکت‌های عمومی ناشناس می‌شوند.
- می‌توانید نسخه‌ای از اطلاعات حساب خود را درخواست کنید.

امنیت:
- ارتباط شبکه فقط از طریق HTTPS انجام می‌شود. این نسخه ادعای رمزگذاری سرتاسری نمی‌کند.

ناشناس‌بودن:
- نام ناشناس فقط از دید عموم پنهان است؛ مدیران برای رسیدگی به سوءاستفاده می‌توانند طبق همین سیاست به اطلاعات لازم دسترسی داشته باشند.
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityRulesScreen(onBack: () -> Unit) = LegalScaffold("قوانین جامعه", onBack) {
    InfoBanner("این متن پیش‌نویس است و ادعای انطباق حقوقی قطعی ندارد.")
    Spacer(Modifier.height(12.dp))
    Text(COMMUNITY_RULES, style = MaterialTheme.typography.bodyLarge)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) = LegalScaffold("سیاست حریم خصوصی", onBack) {
    InfoBanner("این متن پیش‌نویس است و باید توسط کارشناس حقوقی بازبینی شود.")
    Spacer(Modifier.height(12.dp))
    Text(PRIVACY_POLICY, style = MaterialTheme.typography.bodyLarge)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), content = content)
    }
}
