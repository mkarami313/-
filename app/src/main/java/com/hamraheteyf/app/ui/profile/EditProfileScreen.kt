package com.hamraheteyf.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.BigPrimaryButton
import com.hamraheteyf.app.ui.common.mutedColor
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.modules.ImagePickRow
import com.hamraheteyf.app.ui.modules.uploadImageId
import kotlinx.coroutines.launch

private const val MAX_NAME = 60
private const val MAX_CITY = 60
private const val BIO_LIMIT = 300
private val FILE_ID = Regex("[0-9a-f]{32}")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val api = container.serverApi
    val scope = rememberCoroutineScope()
    val vm: AccountViewModel = viewModel(factory = appViewModelFactory { AccountViewModel(container.authRepository, container.userRepository) })
    val user by vm.currentUser.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    var name by remember(user?.id) { mutableStateOf(user?.displayName ?: "") }
    var city by remember(user?.id) { mutableStateOf(user?.city ?: "") }
    var bio by remember(user?.id) { mutableStateOf(user?.bio ?: "") }
    val maxBio = maxOf(BIO_LIMIT, user?.bio?.length ?: 0)
    val currentPhotoId = remember(user?.photoUrl) { user?.photoUrl?.let { FILE_ID.find(it)?.value }.orEmpty() }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var photoCleared by rememberSaveable { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var nameTouched by remember { mutableStateOf(false) }

    val saving = busy || uploading

    fun save() {
        nameTouched = true
        if (name.isBlank()) return
        scope.launch {
            var photoId: String? = if (photoCleared && photoBytes == null) "" else null
            val bytes = photoBytes
            if (bytes != null) {
                if (api == null) { vm.setMessage("بارگذاری عکس ممکن نیست."); return@launch }
                uploading = true
                val r = api.uploadImageId(bytes)
                uploading = false
                when (r) {
                    is ApiResult.Ok -> photoId = r.data
                    is ApiResult.Fail -> { vm.setMessage(r.message); return@launch }
                }
            }
            vm.updateProfile(name, city, bio, photoId) { ok -> if (ok) { photoBytes = null; photoCleared = false } }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { com.hamraheteyf.app.ui.common.ScreenHeader("ویرایش پروفایل", onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            ImagePickRow(
                api = api, label = "عکس پروفایل",
                imageId = if (photoCleared) "" else currentPhotoId, newBytes = photoBytes,
                onPicked = { photoBytes = it; photoCleared = false },
                onClear = { photoBytes = null; photoCleared = true },
                onError = { vm.setMessage(it) }, size = 80.dp,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(MAX_NAME); nameTouched = true }, label = { Text("نام نمایشی") },
                singleLine = true, isError = nameTouched && name.isBlank(),
                supportingText = { if (nameTouched && name.isBlank()) Text("نام نمایشی را وارد کنید.") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = city, onValueChange = { city = it.take(MAX_CITY) }, label = { Text("شهر یا منطقه (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = bio, onValueChange = { bio = it.take(maxBio) }, label = { Text("معرفی کوتاه (اختیاری)") },
                supportingText = { Text("${bio.length.toPersianDigits()} از ${maxBio.toPersianDigits()}", color = mutedColor()) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            )
            Spacer(Modifier.height(20.dp))
            BigPrimaryButton(text = if (saving) "در حال ذخیره…" else "ذخیره", enabled = !saving, onClick = { save() })
        }
    }
}
