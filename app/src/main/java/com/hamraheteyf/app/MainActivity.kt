package com.hamraheteyf.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.ui.common.NotificationsOffBanner
import com.hamraheteyf.app.ui.common.OfflineBanner
import com.hamraheteyf.app.ui.common.SessionExpiredBanner
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.push.PushNotifier
import com.hamraheteyf.app.security.CredentialVault
import com.hamraheteyf.app.security.LocalCredentialVault
import com.hamraheteyf.app.ui.AppRoot
import com.hamraheteyf.app.ui.theme.HamrahTheme

// FragmentActivity (not plain ComponentActivity) so AndroidX BiometricPrompt can attach.
class MainActivity : FragmentActivity() {

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppForeground.install(application)
        PushNotifier.ensureChannel(this)
        maybeRequestNotificationPermission()
        val container = (application as HamrahApplication).container
        val vault = CredentialVault(this)
        setContent {
            CompositionLocalProvider(
                LocalAppContainer provides container,
                LocalCredentialVault provides vault,
            ) {
                HamrahTheme {
                    Box(Modifier.fillMaxSize()) {
                        AppRoot()
                        val offline by container.offline.collectAsState()
                        val expired by container.sessionExpired.collectAsState()
                        val uid by container.authRepository.currentUserId.collectAsState()
                        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            if (uid != null) {
                                OfflineBanner(offline)
                                NotificationsOffBanner()
                            } else if (expired) {
                                SessionExpiredBanner()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

