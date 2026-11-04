package com.hamraheteyf.app.ui.forum

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

// STUB — implemented by the forum workstream.

@Composable
fun TopicScreen(
    topicId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenForum: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onEdit: (String) -> Unit,
) { Text(topicId) }
