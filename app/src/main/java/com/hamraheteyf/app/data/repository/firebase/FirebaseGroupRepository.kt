package com.hamraheteyf.app.data.repository.firebase

import com.google.firebase.firestore.FieldValue
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.GroupRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseGroupRepository : GroupRepository {

    private val db = firestore()

    /** Group ids the signed-in user is an ACTIVE member of (live). */
    private fun myActiveGroupIds(): Flow<Set<String>> =
        authUidFlow().flatMapLatest { id ->
            if (id == null) flowOf(emptySet())
            else db.collection(Fs.MEMBERSHIPS).whereEqualTo("userId", id)
                .asListFlow(::membershipFromDoc)
                .map { memberships -> memberships.filter { it.status == MembershipStatus.ACTIVE }.map { it.groupId }.toSet() }
        }

    override fun observeGroups(query: String): Flow<List<Group>> =
        combine(db.collection(Fs.GROUPS).asListFlow(::groupFromDoc), myActiveGroupIds()) { groups, myIds ->
            groups.filter { it.visibility == GroupVisibility.PUBLIC || it.id in myIds }
                .filter {
                    val q = query.trim()
                    q.isEmpty() || it.title.contains(q) || it.description.contains(q) ||
                        it.category?.contains(q) == true || it.city?.contains(q) == true
                }
                .sortedByDescending { it.updatedAt }
        }

    override fun observeGroup(groupId: String): Flow<Group?> =
        combine(db.collection(Fs.GROUPS).document(groupId).asDocFlow(::groupFromDoc), myActiveGroupIds()) { group, myIds ->
            group?.takeIf { it.visibility == GroupVisibility.PUBLIC || it.id in myIds }
        }

    override fun observeMemberships(groupId: String): Flow<List<GroupMembership>> =
        db.collection(Fs.MEMBERSHIPS).whereEqualTo("groupId", groupId).asListFlow(::membershipFromDoc)
            .map { list -> list.filter { it.status != MembershipStatus.REMOVED } }

    override fun observeMyMembership(groupId: String): Flow<GroupMembership?> =
        authUidFlow().flatMapLatest { id ->
            if (id == null) flowOf(null)
            else db.collection(Fs.MEMBERSHIPS).whereEqualTo("groupId", groupId).whereEqualTo("userId", id)
                .asListFlow(::membershipFromDoc)
                .map { list -> list.firstOrNull { it.status != MembershipStatus.REMOVED } }
        }

    override fun observeMyGroups(): Flow<List<Group>> =
        combine(db.collection(Fs.GROUPS).asListFlow(::groupFromDoc), myActiveGroupIds()) { groups, myIds ->
            groups.filter { it.id in myIds }.sortedByDescending { it.updatedAt }
        }

    override fun observePosts(groupId: String): Flow<List<GroupPost>> =
        combine(
            db.collection(Fs.GROUPS).document(groupId).asDocFlow(::groupFromDoc),
            db.collection(Fs.POSTS).whereEqualTo("groupId", groupId).asListFlow(::postFromDoc),
            myActiveGroupIds(),
        ) { group, posts, myIds ->
            if (group == null) emptyList()
            else if (group.visibility == GroupVisibility.PRIVATE && group.id !in myIds) emptyList()
            else posts.filter { it.status == ContentStatus.PUBLISHED }.sortedByDescending { it.createdAt }
        }

    override suspend fun createGroup(
        title: String, description: String, category: String?, city: String?,
        visibility: GroupVisibility, rules: String?,
    ): DataResult<String> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        if (title.trim().length < 3) return fbError("عنوان گروه را کامل‌تر بنویسید.")
        return try {
            val group = Group(id = "", ownerId = id, title = title.trim(), description = description.trim(),
                category = category?.trim()?.ifEmpty { null }, city = city?.trim()?.ifEmpty { null },
                visibility = visibility, rules = rules?.trim()?.ifEmpty { null }, memberCount = 1,
                createdAt = now(), updatedAt = now())
            val ref = db.collection(Fs.GROUPS).add(group.toMap()).await()
            val ownerMembership = GroupMembership(id = "", groupId = ref.id, userId = id,
                role = MembershipRole.OWNER, status = MembershipStatus.ACTIVE, createdAt = now(), updatedAt = now())
            db.collection(Fs.MEMBERSHIPS).add(ownerMembership.toMap()).await()
            DataResult.Success(ref.id)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    override suspend fun joinOrRequest(groupId: String): DataResult<Unit> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        return try {
            val groupSnap = db.collection(Fs.GROUPS).document(groupId).get().await()
            if (!groupSnap.exists()) return fbError("گروه یافت نشد.")
            val group = groupFromDoc(groupSnap)
            val existing = db.collection(Fs.MEMBERSHIPS).whereEqualTo("groupId", groupId).whereEqualTo("userId", id)
                .get().await().documents.map { membershipFromDoc(it) }
            if (existing.any { it.status != MembershipStatus.REMOVED }) return fbError("شما قبلاً در این گروه عضو یا در انتظار تأیید هستید.")
            val status = if (group.visibility == GroupVisibility.PUBLIC) MembershipStatus.ACTIVE else MembershipStatus.REQUESTED
            val membership = GroupMembership(id = "", groupId = groupId, userId = id,
                role = MembershipRole.MEMBER, status = status, createdAt = now(), updatedAt = now())
            db.collection(Fs.MEMBERSHIPS).add(membership.toMap()).await()
            if (status == MembershipStatus.ACTIVE) {
                db.collection(Fs.GROUPS).document(groupId).update("memberCount", FieldValue.increment(1)).await()
            } else {
                pushNotification(group.ownerId, NotificationType.GROUP_JOIN_REQUEST,
                    "درخواست عضویت", "یک درخواست عضویت تازه در گروه شما ثبت شد.", groupId)
            }
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    override suspend fun leave(groupId: String): DataResult<Unit> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        return try {
            val docs = db.collection(Fs.MEMBERSHIPS).whereEqualTo("groupId", groupId).whereEqualTo("userId", id)
                .get().await().documents
            val membership = docs.map { it to membershipFromDoc(it) }.firstOrNull { it.second.status != MembershipStatus.REMOVED }
                ?: return fbError("شما عضو این گروه نیستید.")
            if (membership.second.role == MembershipRole.OWNER) return fbError("مدیر گروه نمی‌تواند گروه را ترک کند؛ ابتدا مدیریت را واگذار کنید.")
            membership.first.reference.delete().await()
            if (membership.second.status == MembershipStatus.ACTIVE) {
                db.collection(Fs.GROUPS).document(groupId).update("memberCount", FieldValue.increment(-1)).await()
            }
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    override suspend fun approveMember(groupId: String, userId: String): DataResult<Unit> = try {
        val docs = db.collection(Fs.MEMBERSHIPS).whereEqualTo("groupId", groupId).whereEqualTo("userId", userId).get().await()
        docs.documents.forEach {
            if (membershipFromDoc(it).status == MembershipStatus.REQUESTED) {
                it.reference.update(mapOf("status" to MembershipStatus.ACTIVE.name, "updatedAt" to now())).await()
            }
        }
        db.collection(Fs.GROUPS).document(groupId).update("memberCount", FieldValue.increment(1)).await()
        DataResult.Success(Unit)
    } catch (e: Exception) { fbError(friendly(e)) }

    override suspend fun removeMember(groupId: String, userId: String): DataResult<Unit> = try {
        val docs = db.collection(Fs.MEMBERSHIPS).whereEqualTo("groupId", groupId).whereEqualTo("userId", userId).get().await()
        var wasActive = false
        docs.documents.forEach {
            val m = membershipFromDoc(it)
            if (m.role != MembershipRole.OWNER) {
                if (m.status == MembershipStatus.ACTIVE) wasActive = true
                it.reference.delete().await()
            }
        }
        if (wasActive) db.collection(Fs.GROUPS).document(groupId).update("memberCount", FieldValue.increment(-1)).await()
        DataResult.Success(Unit)
    } catch (e: Exception) { fbError(friendly(e)) }

    override suspend fun createPost(groupId: String, body: String): DataResult<Unit> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        if (body.trim().length < 2) return fbError("متن پست را بنویسید.")
        return try {
            val post = GroupPost(id = "", groupId = groupId, authorId = id, body = body.trim(), createdAt = now(), updatedAt = now())
            db.collection(Fs.POSTS).add(post.toMap()).await()
            db.collection(Fs.GROUPS).document(groupId).update("updatedAt", now()).await()
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    override suspend fun deletePost(postId: String): DataResult<Unit> = try {
        db.collection(Fs.POSTS).document(postId).delete().await()
        DataResult.Success(Unit)
    } catch (e: Exception) { fbError(friendly(e)) }
}
