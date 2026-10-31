package com.hamraheteyf.app.di

import androidx.compose.runtime.Composable

/** Release builds ship no demo backend (see src/demo, which only debug and unit tests compile). */
fun demoContainerOrNull(): AppContainer? = null

@Composable
fun DemoLoginHint() {}
