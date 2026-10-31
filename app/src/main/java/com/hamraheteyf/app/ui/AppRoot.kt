package com.hamraheteyf.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.ui.auth.LoginScreen
import com.hamraheteyf.app.ui.auth.RegisterScreen
import com.hamraheteyf.app.ui.auth.ResetPasswordScreen
import com.hamraheteyf.app.ui.onboarding.OnboardingPrefs
import com.hamraheteyf.app.ui.modules.EmergencyCardPublicScreen
import com.hamraheteyf.app.ui.navigation.AppFeatures
import com.hamraheteyf.app.ui.navigation.FeatureFlags
import com.hamraheteyf.app.ui.navigation.Routes

@Composable
fun AppRoot() {
    val container = LocalAppContainer.current
    val userId by container.authRepository.currentUserId.collectAsState()
    if (userId == null) AuthNavHost() else { MainNavHost(); com.hamraheteyf.app.update.UpdateHost() }
}

@Composable
private fun AuthNavHost() {
    val nav = rememberNavController()
    var resetEmail by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    NavHost(nav, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onGoToRegister = { nav.navigate(Routes.REGISTER) },
                onGoToReset = { email -> resetEmail = email; nav.navigate(Routes.RESET) },
                onEmergencyCard = { nav.navigate(Routes.EMERGENCY_CARD_PUBLIC) },
            )
        }
        composable(Routes.EMERGENCY_CARD_PUBLIC) { EmergencyCardPublicScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.RESET) { ResetPasswordScreen(onBack = { nav.popBackStack() }, initialEmail = resetEmail) }
        composable(Routes.REGISTER) { RegisterScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun MainNavHost() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val disabled by FeatureFlags.disabled.collectAsState()
    val tabs = AppFeatures.enabled(disabled).filter { it.bottomTab }
    val ctx = LocalContext.current
    val uid by LocalAppContainer.current.authRepository.currentUserId.collectAsState()
    val start = remember(uid) { if (uid != null && !OnboardingPrefs.isDone(ctx, uid!!)) Routes.ONBOARDING else Routes.HOME }

    // «من متخصص هستم» chosen at sign-up / onboarding: continue straight into verification once on Home.
    LaunchedEffect(currentRoute) {
        if (currentRoute == Routes.HOME && OnboardingPrefs.takePendingPro(ctx)) nav.navigate(Routes.VERIFICATION)
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in tabs.map { it.entryRoute }) {
                NavigationBar {
                    tabs.forEach { f ->
                        NavigationBarItem(
                            selected = currentRoute == f.entryRoute,
                            onClick = {
                                nav.navigate(f.entryRoute) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(f.icon, contentDescription = f.title) },
                            label = { Text(f.title, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(padding)) {
            AppFeatures.all.forEach { f -> f.routes(this, nav) }
        }
    }
}
