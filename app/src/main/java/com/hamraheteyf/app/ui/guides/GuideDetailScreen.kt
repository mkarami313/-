package com.hamraheteyf.app.ui.guides

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.GuideArticle
import com.hamraheteyf.app.data.repository.AuthRepository
import com.hamraheteyf.app.data.repository.GuideRepository
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GuideDetailViewModel(
    private val guideId: String,
    private val repo: GuideRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    val guide: StateFlow<GuideArticle?> = repo.observeGuide(guideId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val currentUserId: StateFlow<String?> = auth.currentUserId
    fun toggleSaved() = viewModelScope.launch { repo.toggleSaved(guideId) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideDetailScreen(guideId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: GuideDetailViewModel = viewModel(factory = appViewModelFactory {
        GuideDetailViewModel(guideId, container.guideRepository, container.authRepository)
    })
    val guide by vm.guide.collectAsState()
    val me by vm.currentUserId.collectAsState()
    val saved = guide?.savedByUserIds?.contains(me) == true

    Scaffold(
        topBar = {
            com.hamraheteyf.app.ui.common.ScreenHeader(
                "راهنما", onBack = onBack,
                actions = {
                    val (openReport, reportDialog) = com.hamraheteyf.app.ui.common.rememberReporter("GUIDE", guideId)
                    IconButton(onClick = openReport) {
                        Icon(Icons.Outlined.Flag, contentDescription = "گزارش راهنما", tint = androidx.compose.ui.graphics.Color.White)
                    }
                    reportDialog()
                    IconButton(onClick = { vm.toggleSaved() }) {
                        Icon(if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = "ذخیره", tint = androidx.compose.ui.graphics.Color.White)
                    }
                },
            )
        },
    ) { padding ->
        val g = guide
        if (g == null) {
            EmptyState("این راهنما در دسترس نیست.", Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row { CategoryChip(g.category); Spacer(Modifier.width(8.dp)); if (g.authorIsVerifiedProfessional) AuthorBadgeChip(com.hamraheteyf.app.data.model.AuthorBadge.PROFESSIONAL_VERIFIED) }
            Spacer(Modifier.height(10.dp))
            Text(g.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text("نویسنده: ${g.authorName}", style = MaterialTheme.typography.labelMedium, color = mutedColor())
            Spacer(Modifier.height(12.dp))
            InfoBanner("این محتوا جنبهٔ آموزشی عمومی دارد و توصیهٔ شخصی پزشکی یا جایگزین مراجعه به متخصص نیست.")
            Spacer(Modifier.height(12.dp))
            Text(g.body, style = MaterialTheme.typography.bodyLarge)
            if (g.sources.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("منابع:", style = MaterialTheme.typography.titleMedium)
                g.sources.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
            if (g.lastReviewedAt > 0) {
                Spacer(Modifier.height(8.dp))
                Text("آخرین بازبینی: ${relativeTimeFa(g.lastReviewedAt)}", style = MaterialTheme.typography.labelMedium, color = mutedColor())
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
