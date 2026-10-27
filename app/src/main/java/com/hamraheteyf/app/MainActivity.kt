package com.hamraheteyf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.ui.AppRoot
import com.hamraheteyf.app.ui.theme.HamrahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HamrahApplication).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                HamrahTheme {
                    AppRoot()
                }
            }
        }
    }
}
