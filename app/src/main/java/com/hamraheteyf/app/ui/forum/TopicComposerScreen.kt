package com.hamraheteyf.app.ui.forum

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

// STUB — implemented by the forum workstream.

/** New topic (in a forum or a group) or edit an existing one. [onDone] gets the topic id. */
@Composable
fun TopicComposerScreen(
    forumId: String?,
    groupId: String?,
    editTopicId: String?,
    onBack: () -> Unit,
    onDone: (String) -> Unit,
) { Text("موضوع تازه") }
