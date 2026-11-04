package com.hamraheteyf.app.ui.groups

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

// STUB — implemented by the forum workstream (groups v2 replace GroupsListScreen/GroupDetailScreen).

@Composable
fun GroupsHubScreen(onBack: (() -> Unit)?, onOpenGroup: (String) -> Unit, onCreate: () -> Unit) { Text("کانون‌ها") }

@Composable
fun GroupScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenTopic: (String) -> Unit,
    onNewTopic: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onEdit: (String) -> Unit,
) { Text(groupId) }

/** Create (groupId null) or edit a group. [onSaved] gets the group id. */
@Composable
fun GroupEditScreen(groupId: String?, onBack: () -> Unit, onSaved: (String) -> Unit) { Text("گروه") }
