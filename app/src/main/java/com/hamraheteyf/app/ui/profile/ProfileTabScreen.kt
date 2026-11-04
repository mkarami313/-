package com.hamraheteyf.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.community.UserStats
import com.hamraheteyf.app.community.rememberCommunity
import com.hamraheteyf.app.data.model.AccountType
import com.hamraheteyf.app.data.model.VerificationStatus
import com.hamraheteyf.app.data.model.canRequestVerification
import com.hamraheteyf.app.data.model.resolveAuthorBadge
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.AuthorBadgeChip
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.navigation.Routes
import com.hamraheteyf.app.ui.theme.*

/** «من»: who I am at a glance, my activity, account settings, help — grouped list cards, one gutter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTabScreen(
    onEditProfile: () -> Unit,
    onProfessional: () -> Unit,
    onVerification: () -> Unit,
    onChildren: () -> Unit,
    onSettings: () -> Unit,
    onAdmin: () -> Unit,
    onRules: () -> Unit,
    onPrivacy: () -> Unit,
    onSharesReceived: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {
    val container = LocalAppContainer.current
    val vm: AccountViewModel = viewModel(factory = appViewModelFactory { AccountViewModel(container.authRepository, container.userRepository) })
    val user by vm.currentUser.collectAsState()
    val professional by vm.professional.collectAsState()
    val verification by vm.verification.collectAsState()
    val client = rememberCommunity()
    var confirmLogout by remember { mutableStateOf(false) }

    // Public stats (topics / replies / likes) from the community profile; null while loading or unavailable.
    val uid = user?.id
    val stats by produceState<UserStats?>(null, uid, client) {
        value = if (uid == null || client == null) null
        else (client.profile(uid) as? ApiResult.Ok)?.data?.stats
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                AppTopBar(title = "من")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { padding ->
        val u = user
        if (u == null) {
            // Signed in but the profile hasn't loaded yet. Always offer a way out so the session can't get stuck.
            Column(Modifier.padding(padding).fillMaxSize().padding(Dimens.gutter)) {
                LoadingView()
                Text("در حالِ آماده‌سازیِ حساب…", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Dimens.xs))
                Text("اگر ادامه نیافت، یک‌بار خارج و دوباره وارد شوید.", style = MaterialTheme.typography.bodyMedium, color = muted())
                Spacer(Modifier.height(Dimens.l))
                OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touch)) {
                    Text("خروج از حساب", color = MaterialTheme.colorScheme.error)
                }
            }
            return@Scaffold
        }
        val isPro = u.accountType.canRequestVerification()
        val vStatus = verification?.status ?: professional?.verificationStatus ?: VerificationStatus.NONE

        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter, vertical = Dimens.m),
            verticalArrangement = Arrangement.spacedBy(Dimens.m),
        ) {
            // ---- header
            AppCard(padding = PaddingValues(0.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onEditProfile).padding(Dimens.cardPad),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(u.displayName, u.photoUrl, size = 64.dp)
                    Spacer(Modifier.width(Dimens.m))
                    Column(Modifier.weight(1f)) {
                        Text(u.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        AuthorBadgeChip(resolveAuthorBadge(u.accountType, professional?.verificationStatus))
                        val city = u.city?.takeIf { it.isNotBlank() }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.accountType.label, style = MaterialTheme.typography.labelMedium, color = muted(), maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (city != null) {
                                Text(" · ", style = MaterialTheme.typography.labelMedium, color = muted())
                                Icon(Icons.Outlined.Place, null, tint = muted(), modifier = Modifier.size(13.dp))
                                Text(city, style = MaterialTheme.typography.labelMedium, color = muted(), maxLines = 1)
                            }
                        }
                    }
                    Icon(Icons.Outlined.Edit, "ویرایشِ پروفایل", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = Dimens.s)) {
                    Stat("موضوع", stats?.topics, Modifier.weight(1f))
                    Stat("پاسخ", stats?.replies, Modifier.weight(1f))
                    Stat("پسند", stats?.likes, Modifier.weight(1f))
                }
                if (u.accountType == AccountType.COMMUNITY) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "به‌عنوانِ عضوِ جامعه در انجمن و کانون‌ها مشارکت می‌کنید؛ پروندهٔ کودک و ابزارهای خانواده مخصوصِ حسابِ خانواده است.",
                        style = MaterialTheme.typography.labelMedium, color = muted(), modifier = Modifier.padding(Dimens.cardPad),
                    )
                }
            }

            // ---- my activity
            ListSection("فعالیتِ من") {
                MenuRow(Icons.Outlined.Forum, Teal, "گفتگوهای من") { onNavigate(Routes.FORUM) }
                RowDivider()
                MenuRow(Icons.Outlined.BookmarkBorder, Apricot, "نشان‌شده‌ها") { onNavigate(Routes.FORUM) }
                RowDivider()
                MenuRow(Icons.AutoMirrored.Outlined.Article, Expert, "مطالبِ من") { onNavigate(Routes.learnList(mode = "mine")) }
                RowDivider()
                MenuRow(Icons.Outlined.Bookmarks, Gold, "ذخیره‌شده‌ها") { onNavigate(Routes.learnList(mode = "saved")) }
            }

            // ---- account
            ListSection("حساب") {
                MenuRow(Icons.Outlined.ManageAccounts, Teal, "ویرایشِ پروفایل", onClick = onEditProfile)
                if (isPro || verification != null) { RowDivider(); MenuRow(Icons.Outlined.Badge, Expert, "پروفایلِ تخصصی", onClick = onProfessional) }
                if (!u.isAdmin) {
                    RowDivider()
                    MenuRow(
                        Icons.Outlined.Verified, VerifiedGreen,
                        if (vStatus == VerificationStatus.NONE) "درخواستِ نشانِ متخصص" else "بررسیِ تخصص",
                        trailing = if (vStatus == VerificationStatus.NONE) null else vStatus.label,
                        onClick = onVerification,
                    )
                }
                if (u.accountType == AccountType.FAMILY) { RowDivider(); MenuRow(Icons.Outlined.ChildCare, Apricot, "اطلاعاتِ کودک", trailing = "خصوصی", onClick = onChildren) }
                if (isPro) { RowDivider(); MenuRow(Icons.Outlined.FolderShared, Expert, "پرونده‌های اشتراک‌گذاشته‌شده با من", onClick = onSharesReceived) }
                RowDivider()
                MenuRow(Icons.Outlined.Settings, OnSurfaceMuted, "تنظیمات و حریمِ خصوصی", onClick = onSettings)
            }

            if (u.isAdmin) {
                ListSection("مدیریت") {
                    MenuRow(Icons.Outlined.AdminPanelSettings, Danger, "پنلِ مدیریت", onClick = onAdmin)
                }
            }

            // ---- help
            ListSection("پشتیبانی و قوانین") {
                MenuRow(Icons.Outlined.Gavel, Teal, "قوانینِ جامعه", onClick = onRules)
                RowDivider()
                MenuRow(Icons.Outlined.PrivacyTip, Teal, "سیاستِ حریمِ خصوصی", onClick = onPrivacy)
            }

            AppCard(padding = PaddingValues(0.dp)) {
                MenuRow(Icons.AutoMirrored.Outlined.Logout, Danger, "خروج از حساب", textColor = Danger, chevron = false) { confirmLogout = true }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("خروج از حساب؟") },
            text = { Text("برای استفادهٔ دوباره باید با ایمیل و رمز وارد شوید.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("خروج", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: Int?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value?.toPersianDigits() ?: "–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = muted())
    }
}

@Composable
private fun ListSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = muted(), modifier = Modifier.padding(start = Dimens.xs, bottom = Dimens.s))
        AppCard(padding = PaddingValues(0.dp), content = content)
    }
}

@Composable
private fun RowDivider() = InsetDivider(start = Dimens.cardPad + 36.dp + Dimens.m)

@Composable
private fun MenuRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    trailing: String? = null,
    textColor: Color = Color.Unspecified,
    chevron: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp)
            .padding(horizontal = Dimens.cardPad, vertical = Dimens.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = 36.dp)
        Spacer(Modifier.width(Dimens.m))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Spacer(Modifier.width(Dimens.s))
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = muted(), maxLines = 1)
        }
        if (chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = muted(), modifier = Modifier.size(20.dp))
    }
}
