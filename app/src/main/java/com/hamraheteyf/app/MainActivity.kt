package com.hamraheteyf.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
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
                    AppRoot()
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

