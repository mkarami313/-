package com.hamraheteyf.app.ui.learn

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

// STUB — implemented by the learning workstream. Signatures are the navigation contract; keep them.

/** «آموزش‌ها» tab: categories, featured, latest videos & articles, «ارسالِ مطلب», admin queue entry. */
@Composable
fun LearnHubScreen(
    onOpenItem: (String) -> Unit,
    onOpenList: (categoryId: String?, mode: String) -> Unit,
    onSubmit: () -> Unit,
    onReview: () -> Unit,
) { Text("آموزش‌ها") }

/** mode: "" published, "video", "article", "saved", "mine". */
@Composable
fun LearnListScreen(categoryId: String?, mode: String, onBack: () -> Unit, onOpenItem: (String) -> Unit) { Text("فهرست") }

@Composable
fun LearnItemScreen(id: String, onBack: () -> Unit, onEdit: (String) -> Unit, onOpenProfile: (String) -> Unit) { Text(id) }

/** New submission (editId null) or edit/resubmit. */
@Composable
fun LearnSubmitScreen(editId: String?, onBack: () -> Unit, onDone: () -> Unit) { Text("ارسالِ مطلب") }

/** Admin review queue. */
@Composable
fun LearnReviewScreen(onBack: () -> Unit, onOpenItem: (String) -> Unit) { Text("بررسی") }
