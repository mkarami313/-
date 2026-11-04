package com.hamraheteyf.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.hamraheteyf.app.ui.kit.AppTopBar

/**
 * Legacy screen header, now the same flat surface top bar as the design kit's [AppTopBar]
 * (title, back arrow, actions) with a hairline under it — so older screens match the new ones.
 * Prefer [AppTopBar] in new code.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        AppTopBar(title = title, onBack = onBack, actions = actions)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
