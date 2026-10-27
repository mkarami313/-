package com.hamraheteyf.app.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.*
import com.hamraheteyf.app.ui.common.AuthorView
import com.hamraheteyf.app.ui.common.resolveAuthor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MemberView(val membership: GroupMembership, val displayName: String)

data class GroupDetailState(
    val group: Group? = null,
    val myMembership: GroupMembership? = null,
    val canManage: Boolean = false,
    val isMember: Boolean = false,
    val posts: List<Pair<GroupPost, AuthorView>> = emptyList(),
    val pendingRequests: List<MemberView> = emptyList(),
    val activeMembers: List<MemberView> = emptyList(),
    val currentUserId: String? = null,
)

private data class GroupBase(
    val group: Group?,
    val myMembership: GroupMembership?,
    val posts: List<GroupPost>,
    val memberships: List<GroupMembership>,
    val currentId: String?,
)

class GroupDetailViewModel(
    private val groupId: String,
    private val groupRepo: GroupRepository,
    private val userRepo: UserRepository,
    private val reportRepo: ReportRepository,
) : ViewModel() {

    val message = MutableStateFlow<String?>(null)
    fun clearMessage() { message.value = null }

    private val base = combine(
        groupRepo.observeGroup(groupId),
        groupRepo.observeMyMembership(groupId),
        groupRepo.observePosts(groupId),
        groupRepo.observeMemberships(groupId),
        userRepo.observeCurrentUser().map { it?.id },
    ) { group, my, posts, memberships, currentId -> GroupBase(group, my, posts, memberships, currentId) }

    val state: StateFlow<GroupDetailState> = combine(
        base, userRepo.observeUsers(), userRepo.observeAllProfessionals(),
    ) { b, users, professionals ->
        fun name(userId: String) = users.find { it.id == userId }?.displayName ?: "کاربر حذف‌شده"
        val canManage = b.myMembership?.status == MembershipStatus.ACTIVE &&
            (b.myMembership.role == MembershipRole.OWNER || b.myMembership.role == MembershipRole.MODERATOR)
        GroupDetailState(
            group = b.group,
            myMembership = b.myMembership,
            canManage = canManage,
            isMember = b.myMembership?.status == MembershipStatus.ACTIVE,
            posts = b.posts.map { p -> p to resolveAuthor(users, professionals, p.authorId) },
            pendingRequests = b.memberships.filter { it.status == MembershipStatus.REQUESTED }.map { MemberView(it, name(it.userId)) },
            activeMembers = b.memberships.filter { it.status == MembershipStatus.ACTIVE }.map { MemberView(it, name(it.userId)) },
            currentUserId = b.currentId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GroupDetailState())

    private fun run(success: String? = null, block: suspend () -> DataResult<*>) = viewModelScope.launch {
        when (val r = block()) {
            is DataResult.Success -> success?.let { message.value = it }
            is DataResult.Error -> message.value = r.message
        }
    }

    fun join() = run("درخواست/عضویت ثبت شد.") { groupRepo.joinOrRequest(groupId) }
    fun leave() = run("از گروه خارج شدید.") { groupRepo.leave(groupId) }
    fun createPost(body: String) = run { groupRepo.createPost(groupId, body) }
    fun deletePost(id: String) = run("پست حذف شد.") { groupRepo.deletePost(id) }
    fun approve(userId: String) = run("عضو تأیید شد.") { groupRepo.approveMember(groupId, userId) }
    fun remove(userId: String) = run("عضو حذف شد.") { groupRepo.removeMember(groupId, userId) }
    fun reportPost(id: String, reason: ReportReason, note: String) =
        run("گزارش ثبت شد.") { reportRepo.createReport(ContentType.GROUP_POST, id, reason, note) }
}
