package com.hamraheteyf.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.security.CredentialVault
import com.hamraheteyf.app.security.LocalCredentialVault
import com.hamraheteyf.app.ui.AppRoot
import com.hamraheteyf.app.ui.theme.HamrahTheme

// FragmentActivity (not plain ComponentActivity) so AndroidX BiometricPrompt can attach.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
}
