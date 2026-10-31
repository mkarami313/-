package com.hamraheteyf.app.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

/**
 * One self-contained module of the super-app. A feature owns its routes and declares where it
 * shows up (bottom tab, home grid). Adding a capability = write its screens + one entry in
 * [AppFeatures.all]; navigation, bottom bar and the home grid pick it up automatically.
 */
class AppFeature(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val entryRoute: String,
    val order: Int,
    val bottomTab: Boolean = false,
    val homeTile: Boolean = false,
    val routes: NavGraphBuilder.(NavController) -> Unit,
)
