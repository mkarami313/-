package com.hamraheteyf.app.ui.forum

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

// STUB — implemented by the forum workstream. Signatures are the navigation contract; keep them.

/** «انجمن» tab: forum tree + topic feeds (داغ/جدید/دنبال‌شده/نشان‌شده/من) + entry to clinic and groups. */
@Composable
fun ForumHubScreen(
    onOpenForum: (String) -> Unit,
    onOpenTopic: (String) -> Unit,
    onNewTopic: (String?) -> Unit,
    onOpenClinic: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onSearch: () -> Unit,
) { Text("انجمن") }

/** Topics of one forum (and its sub-forums). */
@Composable
fun ForumTopicsScreen(
    forumId: String,
    onBack: () -> Unit,
    onOpenForum: (String) -> Unit,
    onOpenTopic: (String) -> Unit,
    onNewTopic: (String) -> Unit,
) { Text(forumId) }

/** «کلینیک»: questions to verified professionals. */
@Composable
fun ClinicScreen(onBack: () -> Unit, onOpenTopic: (String) -> Unit, onAsk: (String?) -> Unit) { Text("کلینیک") }
