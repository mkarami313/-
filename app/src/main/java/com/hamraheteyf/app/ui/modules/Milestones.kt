@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hamraheteyf.app.ui.modules

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Apricot
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.Expert
import com.hamraheteyf.app.ui.theme.VerifiedGreen

/*
 * Developmental milestones, adapted into Persian from the CDC «Learn the Signs. Act Early.» checklists
 * (U.S. government work, public domain). They describe what most children (about 75%) do by each age.
 */

private class Domain(val key: String, val title: String, val icon: ImageVector)

private val DOMAINS = listOf(
    Domain("social", "اجتماعی / هیجانی", Icons.Outlined.Favorite),
    Domain("lang", "زبان / ارتباط", Icons.Outlined.RecordVoiceOver),
    Domain("cog", "شناختی (یادگیری، فکر، حلِ مسئله)", Icons.Outlined.Psychology),
    Domain("move", "حرکتی / رشدِ جسمی", Icons.Outlined.DirectionsRun),
)

private class AgeStage(val key: String, val label: String, val items: Map<String, List<String>>)

private val STAGES = listOf(
    AgeStage("2m", "۲ ماهگی", mapOf(
        "social" to listOf("وقتی با او حرف می‌زنید یا بغلش می‌کنید آرام می‌شود", "به صورتِ شما نگاه می‌کند", "وقتی نزدیکش می‌روید خوشحال به نظر می‌رسد", "وقتی با او حرف می‌زنید یا لبخند می‌زنید، لبخند می‌زند"),
        "lang" to listOf("صداهایی غیر از گریه درمی‌آورد", "به صداهای بلند واکنش نشان می‌دهد"),
        "cog" to listOf("وقتی حرکت می‌کنید با نگاه دنبالتان می‌کند", "چند ثانیه به یک اسباب‌بازی نگاه می‌کند"),
        "move" to listOf("روی شکم که باشد سرش را بالا نگه می‌دارد", "هر دو دست و هر دو پا را حرکت می‌دهد", "دست‌هایش را برای لحظه‌ای باز می‌کند"),
    )),
    AgeStage("4m", "۴ ماهگی", mapOf(
        "social" to listOf("خودش لبخند می‌زند تا توجهِ شما را جلب کند", "وقتی می‌خواهید بخندانیدش، ریز می‌خندد", "با نگاه، حرکت یا صدا توجهِ شما را جلب می‌کند یا نگه می‌دارد"),
        "lang" to listOf("صداهایی مثلِ «اووو» و «آآآ» درمی‌آورد", "وقتی با او حرف می‌زنید با صدا جواب می‌دهد", "سرش را به سمتِ صدای شما می‌چرخاند"),
        "cog" to listOf("وقتی گرسنه است، با دیدنِ سینه یا شیشهٔ شیر دهانش را باز می‌کند", "با علاقه به دست‌هایش نگاه می‌کند"),
        "move" to listOf("وقتی بغلش کرده‌اید سرش را بدونِ کمک ثابت نگه می‌دارد", "اسباب‌بازی‌ای را که در دستش می‌گذارید نگه می‌دارد", "با دستش به اسباب‌بازی ضربه می‌زند", "دست‌ها را به دهانش می‌برد", "روی شکم، خودش را روی آرنج یا ساعد بالا می‌کشد"),
    )),
    AgeStage("6m", "۶ ماهگی", mapOf(
        "social" to listOf("آدم‌های آشنا را می‌شناسد", "دوست دارد خودش را در آینه ببیند", "می‌خندد"),
        "lang" to listOf("به نوبت با شما صدا درمی‌آورد", "با لب‌هایش صدای «پْرررر» درمی‌آورد", "جیغ‌های کوچکِ شادی می‌کشد"),
        "cog" to listOf("چیزها را برای شناختن در دهانش می‌گذارد", "برای گرفتنِ اسباب‌بازیِ دلخواهش دست دراز می‌کند", "وقتی غذای بیشتری نمی‌خواهد لب‌هایش را می‌بندد"),
        "move" to listOf("از شکم به پشت غلت می‌زند", "روی شکم، با دست‌های صاف خودش را بالا می‌کشد", "در حالتِ نشسته به دست‌هایش تکیه می‌دهد"),
    )),
    AgeStage("9m", "۹ ماهگی", mapOf(
        "social" to listOf("در برابرِ غریبه‌ها خجالتی، چسبنده یا ترسان است", "چند حالتِ چهره نشان می‌دهد (شاد، غمگین، عصبانی، متعجب)", "وقتی صدایش می‌زنید نگاه می‌کند", "وقتی می‌روید واکنش نشان می‌دهد (نگاه می‌کند، دست دراز می‌کند یا گریه می‌کند)", "در بازیِ «دالی» لبخند می‌زند یا می‌خندد"),
        "lang" to listOf("صداهای گوناگون مثلِ «ماماما» و «بابابا» درمی‌آورد", "برای بغل شدن دست‌هایش را بالا می‌برد"),
        "cog" to listOf("دنبالِ چیزی می‌گردد که از جلوی چشمش افتاده", "دو چیز را به هم می‌کوبد"),
        "move" to listOf("خودش به حالتِ نشسته درمی‌آید", "چیزی را از یک دست به دستِ دیگر می‌دهد", "با انگشتانش غذا را به سمتِ خودش می‌کشد", "بدونِ کمک می‌نشیند"),
    )),
    AgeStage("12m", "۱۲ ماهگی", mapOf(
        "social" to listOf("با شما بازی‌هایی مثلِ «دست‌دستی» می‌کند"),
        "lang" to listOf("«بای‌بای» دست تکان می‌دهد", "پدر یا مادر را «ماما»، «بابا» یا نامِ ویژهٔ دیگری صدا می‌زند", "«نه» را می‌فهمد (با شنیدنش کمی مکث می‌کند یا دست می‌کشد)"),
        "cog" to listOf("چیزی را در ظرف می‌گذارد، مثلِ مکعب در لیوان", "دنبالِ چیزی می‌گردد که دیده شما پنهانش کرده‌اید"),
        "move" to listOf("خودش را بالا می‌کشد و می‌ایستد", "با گرفتنِ مبل یا وسایل راه می‌رود", "وقتی لیوانِ بی‌درپوش را نگه می‌دارید از آن می‌نوشد", "چیزها را بینِ شست و انگشتِ اشاره برمی‌دارد"),
    )),
    AgeStage("15m", "۱۵ ماهگی", mapOf(
        "social" to listOf("هنگامِ بازی از بچه‌های دیگر تقلید می‌کند", "چیزی را که دوست دارد به شما نشان می‌دهد", "وقتی هیجان‌زده است دست می‌زند", "عروسک یا اسباب‌بازیِ دیگری را بغل می‌کند", "محبت نشان می‌دهد (بغل، نوازش یا بوسه)"),
        "lang" to listOf("سعی می‌کند غیر از «ماما» و «بابا» یکی دو کلمهٔ دیگر بگوید", "وقتی نامِ چیزِ آشنایی را می‌برید به آن نگاه می‌کند", "دستوری را که با کلمه و اشاره همراه است انجام می‌دهد", "برای خواستنِ چیزی یا کمک گرفتن اشاره می‌کند"),
        "cog" to listOf("سعی می‌کند چیزها را درست به کار ببرد، مثلِ تلفن، لیوان یا کتاب", "دست‌کم دو شیءِ کوچک را روی هم می‌گذارد"),
        "move" to listOf("خودش چند قدم برمی‌دارد", "با انگشتانش کمی غذا به دهان می‌گذارد"),
    )),
    AgeStage("18m", "۱۸ ماهگی", mapOf(
        "social" to listOf("از شما دور می‌شود ولی نگاه می‌کند که نزدیکش باشید", "به چیزِ جالبی اشاره می‌کند تا به شما نشان دهد", "دست‌هایش را برای شستن جلو می‌آورد", "چند صفحه از کتاب را با شما نگاه می‌کند", "در لباس پوشیدن کمک می‌کند (دست را در آستین می‌برد یا پا را بالا می‌آورد)"),
        "lang" to listOf("سعی می‌کند غیر از «ماما» و «بابا» سه کلمه یا بیشتر بگوید", "دستورِ یک‌مرحله‌ای را بدونِ اشاره انجام می‌دهد (مثلاً «بده به من»)"),
        "cog" to listOf("در کارهای خانه از شما تقلید می‌کند، مثلِ جارو زدن", "به شکلِ ساده با اسباب‌بازی بازی می‌کند، مثلِ هل دادنِ ماشین"),
        "move" to listOf("بدونِ گرفتنِ چیزی راه می‌رود", "خط‌خطی می‌کند", "از لیوانِ بی‌درپوش می‌نوشد و گاهی می‌ریزد", "با انگشتانش غذا می‌خورد", "سعی می‌کند با قاشق غذا بخورد", "بدونِ کمک از مبل یا صندلی بالا و پایین می‌رود"),
    )),
    AgeStage("2y", "۲ سالگی", mapOf(
        "social" to listOf("وقتی کسی آسیب دیده یا ناراحت است متوجه می‌شود", "در موقعیتِ تازه به چهرهٔ شما نگاه می‌کند تا ببیند چه واکنشی نشان دهد"),
        "lang" to listOf("وقتی می‌پرسید، به تصویرهای کتاب اشاره می‌کند", "دست‌کم دو کلمه را با هم می‌گوید، مثلِ «شیر بیشتر»", "وقتی می‌پرسید، دست‌کم به دو عضوِ بدن اشاره می‌کند", "غیر از دست تکان دادن و اشاره، حرکت‌های دیگری هم به کار می‌برد، مثلِ بوس فرستادن یا سر تکان دادن به نشانهٔ «آره»"),
        "cog" to listOf("چیزی را با یک دست نگه می‌دارد و با دستِ دیگر کاری می‌کند", "سعی می‌کند کلید، دکمه یا دستگیرهٔ اسباب‌بازی را به کار بیندازد", "هم‌زمان با بیش از یک اسباب‌بازی بازی می‌کند"),
        "move" to listOf("به توپ ضربه می‌زند", "می‌دود", "با کمک یا بی‌کمک چند پله را راه می‌رود (نه چهاردست‌وپا)", "با قاشق غذا می‌خورد"),
    )),
    AgeStage("3y", "۳ سالگی", mapOf(
        "social" to listOf("وقتی از او جدا می‌شوید (مثلاً در مهدکودک) تا ۱۰ دقیقه آرام می‌شود", "متوجهِ بچه‌های دیگر می‌شود و به بازیِ آن‌ها می‌پیوندد"),
        "lang" to listOf("در گفت‌وگو دست‌کم دو بار رفت‌وبرگشت با شما حرف می‌زند", "سؤال‌هایی با «کی»، «چی»، «کجا» یا «چرا» می‌پرسد", "وقتی می‌پرسید، می‌گوید در تصویر یا کتاب چه کاری انجام می‌شود", "وقتی می‌پرسید، نامِ کوچکش را می‌گوید", "بیشترِ وقت‌ها آن‌قدر واضح حرف می‌زند که دیگران می‌فهمند"),
        "cog" to listOf("وقتی نشانش می‌دهید، دایره می‌کشد", "وقتی هشدار می‌دهید، به چیزهای داغ مثلِ اجاق دست نمی‌زند"),
        "move" to listOf("چیزها را به نخ می‌کشد، مثلِ مهره‌های درشت یا ماکارونی", "بعضی لباس‌ها را خودش می‌پوشد، مثلِ شلوارِ گشاد یا کاپشن", "از چنگال استفاده می‌کند"),
    )),
    AgeStage("4y", "۴ سالگی", mapOf(
        "social" to listOf("در بازی نقشِ چیزِ دیگری را بازی می‌کند (معلم، قهرمان، سگ)", "اگر بچه‌ای نباشد می‌خواهد برای بازی پیشِ بچه‌ها برود", "دیگرانی را که آسیب دیده یا غمگین‌اند دلداری می‌دهد", "از خطر دوری می‌کند، مثلاً از جای بلند نمی‌پرد", "دوست دارد «کمک‌کننده» باشد", "رفتارش را با جایی که هست هماهنگ می‌کند (کتابخانه، پارک، مهمانی)"),
        "lang" to listOf("جمله‌های چهار کلمه‌ای یا بلندتر می‌گوید", "بخش‌هایی از یک شعر، ترانه یا قصه را می‌گوید", "دست‌کم دربارهٔ یک اتفاقِ روزش حرف می‌زند", "به سؤال‌های ساده مثلِ «کاپشن برای چیست؟» جواب می‌دهد"),
        "cog" to listOf("رنگِ چند چیز را نام می‌برد", "در قصه‌ای که خوب می‌شناسد، می‌گوید بعدش چه می‌شود", "آدمکی با سه عضوِ بدن یا بیشتر می‌کشد"),
        "move" to listOf("بیشترِ وقت‌ها توپِ بزرگ را می‌گیرد", "با نظارتِ بزرگ‌تر، برای خودش غذا می‌کشد یا آب می‌ریزد", "بعضی دکمه‌ها را باز می‌کند", "مداد را بینِ انگشتان و شست نگه می‌دارد (نه با مشت)"),
    )),
    AgeStage("5y", "۵ سالگی", mapOf(
        "social" to listOf("در بازی با بچه‌ها قانون را رعایت می‌کند یا نوبت را نگه می‌دارد", "برای شما آواز می‌خواند، می‌رقصد یا نمایش اجرا می‌کند", "کارهای سادهٔ خانه را انجام می‌دهد، مثلِ جفت کردنِ جوراب‌ها یا جمع کردنِ سفره"),
        "lang" to listOf("قصه‌ای را که شنیده یا ساخته با دست‌کم دو اتفاق تعریف می‌کند", "بعد از خواندنِ کتاب یا قصه، به سؤال‌های ساده دربارهٔ آن جواب می‌دهد", "گفت‌وگو را با بیش از سه رفت‌وبرگشت ادامه می‌دهد", "کلمه‌های هم‌قافیهٔ ساده را می‌شناسد یا می‌سازد (مثلِ توپ / خوب)"),
        "cog" to listOf("تا ۱۰ می‌شمارد", "وقتی به عددهای ۱ تا ۵ اشاره می‌کنید، بعضی را نام می‌برد", "کلمه‌های زمانی مثلِ «دیروز»، «فردا»، «صبح» یا «شب» را به کار می‌برد", "هنگامِ فعالیت (غیر از تماشای صفحه) ۵ تا ۱۰ دقیقه توجهش را نگه می‌دارد", "بعضی حروفِ نامش را می‌نویسد", "وقتی به بعضی حروف اشاره می‌کنید نامشان را می‌گوید"),
        "move" to listOf("بعضی دکمه‌ها را می‌بندد", "روی یک پا لی‌لی می‌کند"),
    )),
)

private fun prefs(ctx: Context) = ctx.getSharedPreferences("hamrah_milestones", Context.MODE_PRIVATE)

/** Developmental milestones checklist by age (CDC «Learn the Signs. Act Early.» — public domain). */
@Composable
fun MilestonesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var ageKey by rememberSaveable { mutableStateOf(prefs(ctx).getString("lastAge", null) ?: STAGES.first().key) }
    val stage = STAGES.firstOrNull { it.key == ageKey } ?: STAGES.first()
    var seen by remember(stage.key) { mutableStateOf(prefs(ctx).getStringSet(stage.key, emptySet()).orEmpty().toSet()) }
    fun toggle(id: String) {
        val next = if (id in seen) seen - id else seen + id
        seen = next
        prefs(ctx).edit().putStringSet(stage.key, next).apply()
    }
    val total = stage.items.values.sumOf { it.size }
    val ids = stage.items.flatMap { (d, l) -> l.indices.map { "$d:$it" } }.toSet()
    val done = seen.count { it in ids }
    val missing = total - done

    Scaffold(topBar = { AppTopBar("نقاطِ عطفِ رشد", onBack = onBack, subtitle = "چک‌لیستِ سنی برای والدین") }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = Dimens.xl)) {
            item("ages") {
                Spacer(Modifier.height(Dimens.s))
                ChoiceRow(STAGES, stage, { it.label }, { ageKey = it.key; prefs(ctx).edit().putString("lastAge", it.key).apply() })
            }
            item("summary") {
                AppCard(Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(if (missing == 0) Icons.Outlined.TaskAlt else Icons.Outlined.ChecklistRtl, if (missing == 0) VerifiedGreen else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Dimens.m))
                        Column(Modifier.weight(1f)) {
                            Text("${done.toPersianDigits()} از ${total.toPersianDigits()} مورد را دیده‌اید", style = MaterialTheme.typography.titleSmall)
                            Text("${stage.label}: کارهایی که بیشترِ کودکان (حدودِ ۷۵٪) تا این سن انجام می‌دهند.", style = MaterialTheme.typography.labelMedium, color = muted())
                        }
                    }
                    Spacer(Modifier.height(Dimens.s))
                    LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Dimens.s))
                    if (missing > 0) {
                        Text(
                            "${missing.toPersianDigits()} مورد را هنوز ندیده‌اید. هر کودک مسیر و سرعتِ خودش را دارد و این فهرست ابزارِ تشخیص نیست. " +
                                "اگر دربارهٔ این موارد پرسشی دارید، در ویزیتِ بعدی با پزشکِ کودک یا یک متخصصِ رشدِ کودک گفت‌وگو کنید؛ " +
                                "گفت‌وگوی زودهنگام کمک می‌کند اگر حمایتی لازم بود، زودتر شروع شود.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text("همهٔ موارد این سن را دیده‌اید. می‌توانید سنِ بعدی را هم ببینید تا بدانید چه چیزهایی در راه است.", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("اگر کودک مهارتی را که قبلاً داشت دیگر انجام نمی‌دهد، این را هم با پزشکش در میان بگذارید.", style = MaterialTheme.typography.labelSmall, color = muted())
                }
            }
            DOMAINS.forEach { d ->
                val list = stage.items[d.key].orEmpty()
                if (list.isEmpty()) return@forEach
                item("h_${d.key}") {
                    Row(Modifier.fillMaxWidth().padding(start = Dimens.gutter, end = Dimens.gutter, top = Dimens.l, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(d.icon, null, tint = domainColor(d.key), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Dimens.s))
                        Text(d.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        val n = list.indices.count { "${d.key}:$it" in seen }
                        Text("${n.toPersianDigits()}/${list.size.toPersianDigits()}", style = MaterialTheme.typography.labelMedium, color = muted())
                    }
                }
                list.forEachIndexed { i, text ->
                    val id = "${d.key}:$i"
                    item("${stage.key}_$id") {
                        val on = id in seen
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Dimens.touch).clickable { toggle(id) }.padding(horizontal = Dimens.gutter - 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(on, { toggle(id) })
                            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                                color = if (on) muted() else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            item("source") {
                Spacer(Modifier.height(Dimens.l))
                Text("منبع: مرکز کنترل و پیشگیری بیماری‌های آمریکا (CDC)، برنامهٔ «نشانه‌ها را بشناسید، زود اقدام کنید» — برگردانِ فارسی و ساده‌شده.",
                    style = MaterialTheme.typography.labelSmall, color = muted(), modifier = Modifier.padding(horizontal = Dimens.gutter))
                Text("تیک‌ها فقط روی همین گوشی ذخیره می‌شوند.", style = MaterialTheme.typography.labelSmall, color = muted(),
                    modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun domainColor(key: String) = when (key) {
    "social" -> Apricot
    "lang" -> Expert
    "cog" -> MaterialTheme.colorScheme.primary
    else -> VerifiedGreen
}
