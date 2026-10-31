package com.hamraheteyf.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.BigPrimaryButton
import com.hamraheteyf.app.ui.common.InfoBanner
import com.hamraheteyf.app.ui.common.mutedColor
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.modules.ImagePickRow
import com.hamraheteyf.app.ui.modules.uploadImageId
import kotlinx.coroutines.launch

private const val MAX_AREAS = 20
private const val MAX_AREA_LEN = 40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalEditScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val vm: AccountViewModel = viewModel(factory = appViewModelFactory { AccountViewModel(container.authRepository, container.userRepository) })
    val prof by vm.professional.collectAsState()
    val me by vm.currentUser.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    var title by remember(prof) { mutableStateOf(prof?.title ?: "") }
    var areas by remember(prof) { mutableStateOf(prof?.workAreas?.joinToString("، ") ?: "") }
    var online by remember(prof) { mutableStateOf(prof?.offersOnline ?: false) }
    var experience by remember(prof) { mutableStateOf(prof?.experience ?: "") }
    var fee by remember(prof) { mutableStateOf(prof?.fee ?: "") }
    var hours by remember(prof) { mutableStateOf(prof?.hours ?: "") }
    var photoId by remember(prof) { mutableStateOf(prof?.photoId.orEmpty()) }
    var newPhoto by remember(prof) { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }

    val areaList = areas.split("،", ",").map { it.trim() }.filter { it.isNotEmpty() }
    val titleError = when {
        title.isNotEmpty() && title.trim().length < 2 -> "عنوان تخصص بسیار کوتاه است."
        else -> null
    }
    val areasError = when {
        areaList.size > MAX_AREAS -> "حداکثر ${MAX_AREAS.toPersianDigits()} حوزه مجاز است."
        areaList.any { it.length > MAX_AREA_LEN } -> "هر حوزه حداکثر ${MAX_AREA_LEN.toPersianDigits()} حرف باشد."
        else -> null
    }
    val valid = title.trim().length >= 2 && areasError == null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { com.hamraheteyf.app.ui.common.ScreenHeader("پروفایل تخصصی", onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            InfoBanner("این اطلاعات عمومی است. مدارک و شمارهٔ مجوز در این بخش ثبت نمی‌شوند؛ آن‌ها فقط در بخش «بررسی تخصص» و برای مدیران ارسال می‌شوند.")
            Spacer(Modifier.height(16.dp))
            ImagePickRow(
                api = container.serverApi, label = "عکس پروفایل", imageId = photoId, newBytes = newPhoto,
                onPicked = { newPhoto = it }, onClear = { newPhoto = null; photoId = "" }, onError = { photoError = it },
            )
            photoError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title, onValueChange = { title = it.take(80) }, label = { Text("عنوان تخصص") }, singleLine = true,
                isError = titleError != null, supportingText = titleError?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = areas, onValueChange = { areas = it.take(600) }, label = { Text("حوزه‌های کاری (با «،» جدا کنید)") },
                isError = areasError != null, supportingText = areasError?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = experience, onValueChange = { experience = it.take(500) }, label = { Text("سابقهٔ کاری (اختیاری)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = fee, onValueChange = { fee = it.take(100) }, label = { Text("هزینهٔ ویزیت (اختیاری، مثلاً «۵۰۰ هزار تومان / جلسه»)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = hours, onValueChange = { hours = it.take(200) }, label = { Text("ساعت‌های حضور (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = online, onCheckedChange = { online = it })
                Spacer(Modifier.width(8.dp))
                Text("امکان پاسخ‌گویی آنلاین دارم")
            }
            Spacer(Modifier.height(12.dp))
            me?.let { u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = u.privacy.showCity, enabled = !u.city.isNullOrBlank(),
                        onCheckedChange = { vm.updatePrivacy(u.privacy.copy(showCity = it)) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("نمایش شهرم در فهرست متخصصان", Modifier.weight(1f))
                }
                Text(
                    if (u.city.isNullOrBlank()) "شهر را از «ویرایش پروفایل» وارد کنید؛ شهر فقط از همان‌جا خوانده می‌شود."
                    else "شهر: ${u.city} (از «ویرایش پروفایل»)؛ فقط وقتی این گزینه روشن باشد دیده می‌شود.",
                    style = MaterialTheme.typography.labelMedium, color = mutedColor(),
                )
            }
            Spacer(Modifier.height(20.dp))
            BigPrimaryButton(text = if (busy) "در حال ذخیره…" else "ذخیره", enabled = valid && !busy, onClick = {
                scope.launch {
                    busy = true; photoError = null
                    var pid = photoId
                    newPhoto?.let { b ->
                        val api = container.serverApi
                        if (api == null) { photoError = "بارگذاری عکس در حالت آفلاین ممکن نیست."; busy = false; return@launch }
                        when (val r = api.uploadImageId(b)) {
                            is ApiResult.Ok -> pid = r.data
                            is ApiResult.Fail -> { photoError = r.message; busy = false; return@launch }
                        }
                    }
                    vm.upsertProfessional(title.trim(), areaList.distinct(), if (me?.privacy?.showCity == true) me?.city.orEmpty().take(60) else "", online, experience.trim(), pid.ifBlank { null }, fee.trim(), hours.trim())
                    busy = false
                }
            })
            Spacer(Modifier.height(24.dp))
        }
    }
}
