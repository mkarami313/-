package com.hamraheteyf.app.community

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.net.HamrahApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

// ------------------------------------------------------------------ models (server community v2)

@Serializable
data class Person(
    val id: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val badge: String = "",
    val expert: Boolean = false,
    val anonymous: Boolean = false,
    val role: String? = null,
    val status: String? = null,
    val since: Long? = null,
)

@Serializable
data class Forum(
    val id: String,
    val parentId: String? = null,
    val title: String,
    val description: String = "",
    val icon: String = "",
    val color: String = "",
    val kind: String = "forum",
    val topicCount: Int = 0,
    val lastPostAt: Long? = null,
    val children: List<Forum> = emptyList(),
) {
    val isClinic get() = kind == "clinic"
}

@Serializable
data class TopicCard(
    val id: String,
    val forumId: String? = null,
    val groupId: String? = null,
    val groupTitle: String? = null,
    val title: String,
    val excerpt: String = "",
    val author: Person = Person(),
    val images: List<String> = emptyList(),
    val hasPoll: Boolean = false,
    val kind: String = "discussion",
    val status: String = "PUBLISHED",
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val createdAt: Long = 0,
    val lastPostAt: Long = 0,
    val replyCount: Int = 0,
    val likeCount: Int = 0,
    val viewCount: Int = 0,
    val expertReplyCount: Int = 0,
    val answered: Boolean = false,
    val unread: Boolean = false,
    val bookmarked: Boolean = false,
    val mine: Boolean = false,
)

@Serializable
data class Poll(
    val question: String = "",
    val options: List<String> = emptyList(),
    val counts: List<Int> = emptyList(),
    val multi: Boolean = false,
    val closesAt: Long? = null,
    val closed: Boolean = false,
    val voters: Int = 0,
    val myVotes: List<Int> = emptyList(),
)

@Serializable
data class TopicFull(
    val id: String,
    val forumId: String? = null,
    val groupId: String? = null,
    val title: String,
    val body: String = "",
    val author: Person = Person(),
    val images: List<String> = emptyList(),
    val kind: String = "discussion",
    val status: String = "PUBLISHED",
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val createdAt: Long = 0,
    val editedAt: Long? = null,
    val replyCount: Int = 0,
    val likeCount: Int = 0,
    val viewCount: Int = 0,
    val expertReplyCount: Int = 0,
    val poll: Poll? = null,
    val liked: Boolean = false,
    val followed: Boolean = false,
    val bookmarked: Boolean = false,
    val acceptedPostId: String? = null,
    val mine: Boolean = false,
    val canEdit: Boolean = false,
    val canDelete: Boolean = false,
    val canModerate: Boolean = false,
    val canAccept: Boolean = false,
)

@Serializable
data class QuoteRef(val id: String, val authorName: String = "", val excerpt: String = "")

@Serializable
data class ForumPost(
    val id: String,
    val author: Person = Person(),
    val body: String = "",
    val images: List<String> = emptyList(),
    val quote: QuoteRef? = null,
    val status: String = "PUBLISHED",
    val expert: Boolean = false,
    val createdAt: Long = 0,
    val editedAt: Long? = null,
    val likeCount: Int = 0,
    val liked: Boolean = false,
    val mine: Boolean = false,
    val accepted: Boolean = false,
    val canEdit: Boolean = false,
    val canDelete: Boolean = false,
)

@Serializable
data class Ref(val id: String, val title: String = "", val kind: String = "", val parentId: String? = null)

@Serializable
data class TopicPage(
    val topic: TopicFull,
    val forum: Ref? = null,
    val group: Ref? = null,
    val posts: List<ForumPost> = emptyList(),
    val page: Int = 0,
    val pages: Int = 1,
    val total: Int = 0,
    val canReply: Boolean = false,
    val replyHint: String = "",
)

@Serializable
data class TopicList(val items: List<TopicCard> = emptyList(), val hasMore: Boolean = false, val page: Int = 0)

@Serializable
data class LearnCategory(val id: String, val title: String, val icon: String = "", val color: String = "", val count: Int = 0)

@Serializable
data class LearnItem(
    val id: String,
    val kind: String = "article",
    val categoryId: String? = null,
    val title: String,
    val summary: String = "",
    val body: String? = null,
    val coverId: String? = null,
    val videoId: String? = null,
    val videoUrl: String? = null,
    val durationSec: Int? = null,
    val author: Person = Person(),
    val status: String = "PUBLISHED",
    val featured: Boolean = false,
    val viewCount: Int = 0,
    val createdAt: Long = 0,
    val publishedAt: Long? = null,
    val saved: Boolean = false,
    val mine: Boolean = false,
    val reviewNote: String? = null,
    val canEdit: Boolean = false,
    val canReview: Boolean = false,
) {
    val isVideo get() = kind == "video"
}

@Serializable
data class LearnList(val items: List<LearnItem> = emptyList(), val hasMore: Boolean = false, val page: Int = 0)

@Serializable
data class HomeFeed(
    val hot: List<TopicCard> = emptyList(),
    val clinic: List<TopicCard> = emptyList(),
    val fresh: List<TopicCard> = emptyList(),
    val learn: List<LearnItem> = emptyList(),
    val followedUnread: Int = 0,
    val pendingReview: Int = 0,
)

@Serializable
data class Review(val author: Person = Person(), val rating: Int = 0, val body: String = "", val createdAt: Long = 0, val updatedAt: Long = 0, val mine: Boolean = false)

@Serializable
data class ReviewList(val items: List<Review> = emptyList(), val count: Int = 0, val average: Double? = null)

@Serializable
data class RatingSummary(val count: Int = 0, val average: Double = 0.0)

@Serializable
data class GroupItem(
    val id: String,
    val title: String = "",
    val description: String = "",
    val category: String? = null,
    val city: String? = null,
    val ageBand: String? = null,
    val visibility: String = "PUBLIC",
    val memberCount: Int = 0,
    val coverId: String? = null,
    val myStatus: String? = null,
    val myRole: String? = null,
    val updatedAt: Long = 0,
) {
    val isPrivate get() = visibility == "PRIVATE"
    val isMember get() = myStatus == "ACTIVE"
    val isPending get() = myStatus == "REQUESTED"
}

@Serializable
data class GroupDirectory(val items: List<GroupItem> = emptyList(), val cities: List<String> = emptyList())

@Serializable
data class GroupDetail(
    val id: String,
    val title: String = "",
    val description: String = "",
    val rules: String = "",
    val category: String? = null,
    val city: String? = null,
    val ageBand: String? = null,
    val visibility: String = "PUBLIC",
    val coverId: String? = null,
    val memberCount: Int = 0,
    val ownerId: String? = null,
    val status: String? = null,
)

@Serializable
data class GroupInfo(
    val group: GroupDetail,
    val myStatus: String? = null,
    val myRole: String? = null,
    val notify: Boolean = true,
    val canManage: Boolean = false,
    val isOwner: Boolean = false,
    val members: List<Person> = emptyList(),
    val requests: List<Person> = emptyList(),
)

@Serializable
data class UserStats(val topics: Int = 0, val replies: Int = 0, val likes: Int = 0)

@Serializable
data class PublicProfile(
    val user: PublicUser,
    val stats: UserStats = UserStats(),
    val canMessage: Boolean = false,
    val blockedByMe: Boolean = false,
)

@Serializable
data class PublicUser(
    val id: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val badge: String = "",
    val expert: Boolean = false,
    val accountType: String? = null,
    val city: String? = null,
    val bio: String? = null,
    val joinedAt: Long? = null,
)

@Serializable
data class PollDraft(val question: String, val options: List<String>, val multi: Boolean = false, val days: Int = 0)

/** Sort keys understood by the server's forumTopics. */
enum class TopicSort(val key: String, val label: String) {
    ACTIVE("active", "تازه‌ترین فعالیت"),
    HOT("hot", "داغ‌ترین"),
    NEW("new", "جدیدترین"),
    TOP("top", "پربحث‌ترین"),
    UNANSWERED("unanswered", "بی‌پاسخ"),
}

// ------------------------------------------------------------------ client

/**
 * Talks to the paged community actions (forum, clinic, learning library, reviews, groups v2).
 * Every call is a LIGHT action: the server never piggybacks the whole state, so each screen pays
 * only for the page it shows.
 */
class CommunityClient(val api: HamrahApi) {

    private suspend fun call(name: String, args: JsonObject = JsonObject(emptyMap())): ApiResult<JsonElement?> =
        when (val r = api.action(name, args.toString())) {
            is ApiResult.Ok -> ApiResult.Ok(r.data.data)
            is ApiResult.Fail -> r
        }

    private suspend fun <T> get(name: String, args: JsonObject, ser: KSerializer<T>, key: String? = null): ApiResult<T> =
        when (val r = call(name, args)) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> runCatching {
                val el = r.data ?: JsonObject(emptyMap())
                ApiResult.Ok(api.json.decodeFromJsonElement(ser, if (key != null) el.jsonObject[key]!! else el))
            }.getOrElse { ApiResult.Fail("پاسخ سرور نامعتبر است.") }
        }

    private suspend fun done(name: String, args: JsonObject): ApiResult<Unit> =
        when (val r = call(name, args)) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> ApiResult.Ok(Unit)
        }

    private suspend fun idOf(name: String, args: JsonObject): ApiResult<String> =
        when (val r = call(name, args)) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> (r.data?.jsonObject?.get("id") as? JsonPrimitive)?.content?.let { ApiResult.Ok(it) }
                ?: ApiResult.Fail("پاسخ سرور نامعتبر است.")
        }

    private suspend fun flag(name: String, args: JsonObject, key: String): ApiResult<Boolean> =
        when (val r = call(name, args)) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> ApiResult.Ok((r.data?.jsonObject?.get(key) as? JsonPrimitive)?.content == "true")
        }

    private fun strings(v: List<String>) = JsonArray(v.map { JsonPrimitive(it) })

    fun fileUrl(id: String) = api.fileUrl(id)

    // ---- forum
    suspend fun forumTree() = get("forumTree", JsonObject(emptyMap()), ListSerializer(Forum.serializer()), "forums")

    suspend fun home() = get("forumHome", JsonObject(emptyMap()), HomeFeed.serializer())

    suspend fun topics(
        forumId: String? = null, groupId: String? = null, sort: TopicSort = TopicSort.ACTIVE, page: Int = 0,
        q: String? = null, clinic: Boolean = false, mine: Boolean = false, bookmarked: Boolean = false,
        followed: Boolean = false, authorId: String? = null,
    ) = get("forumTopics", buildJsonObject {
        forumId?.let { put("forumId", it) }; groupId?.let { put("groupId", it) }
        put("sort", sort.key); put("page", page)
        if (!q.isNullOrBlank()) put("q", q)
        if (clinic) put("clinic", true); if (mine) put("mine", true)
        if (bookmarked) put("bookmarked", true); if (followed) put("followed", true)
        authorId?.let { put("authorId", it) }
    }, TopicList.serializer())

    /** [page] null = last page (newest replies), as forums usually open. */
    suspend fun topic(id: String, page: Int? = null) = get("forumTopic", buildJsonObject {
        put("topicId", id); if (page != null) put("page", page)
    }, TopicPage.serializer())

    suspend fun createTopic(
        title: String, body: String, forumId: String? = null, groupId: String? = null,
        images: List<String> = emptyList(), anonymous: Boolean = false, poll: PollDraft? = null,
    ) = idOf("forumCreateTopic", buildJsonObject {
        put("title", title); put("body", body)
        forumId?.let { put("forumId", it) }; groupId?.let { put("groupId", it) }
        put("images", strings(images)); put("anonymous", anonymous)
        if (poll != null) put("poll", buildJsonObject {
            put("question", poll.question); put("options", strings(poll.options)); put("multi", poll.multi); put("days", poll.days)
        })
    })

    /** Returns the page count after the reply so the screen can jump to it. */
    suspend fun reply(topicId: String, body: String, quoteId: String? = null, images: List<String> = emptyList()): ApiResult<Int> =
        when (val r = call("forumReply", buildJsonObject {
            put("topicId", topicId); put("body", body); quoteId?.let { put("quoteId", it) }; put("images", strings(images))
        })) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> ApiResult.Ok((r.data?.jsonObject?.get("pages") as? JsonPrimitive)?.content?.toIntOrNull() ?: 1)
        }

    suspend fun edit(id: String, body: String, title: String? = null, images: List<String>? = null) = done("forumEdit", buildJsonObject {
        put("id", id); put("body", body); title?.let { put("title", it) }; images?.let { put("images", strings(it)) }
    })

    suspend fun delete(id: String) = done("forumDelete", buildJsonObject { put("id", id) })

    suspend fun like(id: String): ApiResult<Pair<Boolean, Int>> = when (val r = call("forumLike", buildJsonObject { put("id", id) })) {
        is ApiResult.Fail -> r
        is ApiResult.Ok -> {
            val o = r.data?.jsonObject
            ApiResult.Ok(((o?.get("liked") as? JsonPrimitive)?.content == "true") to ((o?.get("likeCount") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0))
        }
    }

    suspend fun bookmark(topicId: String) = flag("forumBookmark", buildJsonObject { put("topicId", topicId) }, "bookmarked")
    suspend fun follow(topicId: String) = flag("forumFollow", buildJsonObject { put("topicId", topicId) }, "followed")
    suspend fun vote(topicId: String, options: List<Int>) = get("forumVote", buildJsonObject {
        put("topicId", topicId); put("options", JsonArray(options.map { JsonPrimitive(it) }))
    }, Poll.serializer(), "poll")

    /** op: pin|unpin|lock|unlock|accept(postId)|move(forumId) */
    suspend fun moderate(topicId: String, op: String, postId: String? = null, forumId: String? = null) = done("forumModerate", buildJsonObject {
        put("topicId", topicId); put("op", op); postId?.let { put("postId", it) }; forumId?.let { put("forumId", it) }
    })

    suspend fun report(type: String, id: String, reason: String, note: String = "") = done("createReport", buildJsonObject {
        put("contentType", type); put("contentId", id); put("reason", reason); if (note.isNotBlank()) put("note", note)
    })

    // ---- learning library
    suspend fun learnCategories() = get("learnCategories", JsonObject(emptyMap()), ListSerializer(LearnCategory.serializer()), "categories")

    suspend fun learnList(
        categoryId: String? = null, kind: String? = null, q: String? = null, page: Int = 0,
        saved: Boolean = false, mine: Boolean = false, review: Boolean = false,
    ) = get("learnList", buildJsonObject {
        categoryId?.let { put("categoryId", it) }; kind?.let { put("kind", it) }
        if (!q.isNullOrBlank()) put("q", q); put("page", page)
        if (saved) put("saved", true); if (mine) put("mine", true); if (review) put("review", true)
    }, LearnList.serializer())

    suspend fun learnGet(id: String) = get("learnGet", buildJsonObject { put("id", id) }, LearnItem.serializer(), "item")

    suspend fun learnSubmit(
        id: String?, kind: String, categoryId: String, title: String, summary: String, body: String,
        coverId: String?, videoId: String?, videoUrl: String?,
    ): ApiResult<Pair<String, String>> = when (val r = call("learnSubmit", buildJsonObject {
        id?.let { put("id", it) }; put("kind", kind); put("categoryId", categoryId); put("title", title)
        put("summary", summary); put("body", body); coverId?.let { put("coverId", it) }
        videoId?.let { put("videoId", it) }; videoUrl?.let { put("videoUrl", it) }
    })) {
        is ApiResult.Fail -> r
        is ApiResult.Ok -> {
            val o = r.data?.jsonObject
            ApiResult.Ok(((o?.get("id") as? JsonPrimitive)?.content ?: "") to ((o?.get("status") as? JsonPrimitive)?.content ?: ""))
        }
    }

    /** op: approve|reject(note)|hide|unhide|feature|unfeature|delete */
    suspend fun learnReview(id: String, op: String, note: String = "", featured: Boolean = false) = done("learnReview", buildJsonObject {
        put("id", id); put("op", op); if (note.isNotBlank()) put("note", note); if (featured) put("featured", true)
    })

    suspend fun learnDelete(id: String) = done("learnDelete", buildJsonObject { put("id", id) })
    suspend fun learnSave(id: String) = flag("learnSave", buildJsonObject { put("id", id) }, "saved")

    // ---- reviews
    suspend fun reviews(type: String, id: String) = get("reviewList", buildJsonObject { put("targetType", type); put("targetId", id) }, ReviewList.serializer())
    suspend fun saveReview(type: String, id: String, rating: Int, body: String) = done("reviewSave", buildJsonObject {
        put("targetType", type); put("targetId", id); put("rating", rating); put("body", body)
    })
    suspend fun deleteReview(type: String, id: String) = done("reviewDelete", buildJsonObject { put("targetType", type); put("targetId", id) })
    suspend fun ratingSummary(type: String, ids: List<String>): ApiResult<Map<String, RatingSummary>> =
        when (val r = call("reviewSummary", buildJsonObject { put("targetType", type); put("ids", strings(ids)) })) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> runCatching {
                val o = r.data!!.jsonObject["summary"]!!.jsonObject
                ApiResult.Ok(o.mapValues { api.json.decodeFromJsonElement(RatingSummary.serializer(), it.value) })
            }.getOrElse { ApiResult.Ok(emptyMap()) }
        }

    // ---- groups v2
    suspend fun groupDirectory(q: String? = null, city: String? = null, my: Boolean = false) = get("groupDirectory", buildJsonObject {
        if (!q.isNullOrBlank()) put("q", q); if (!city.isNullOrBlank()) put("city", city); if (my) put("my", true)
    }, GroupDirectory.serializer())

    suspend fun groupInfo(id: String) = get("groupInfo", buildJsonObject { put("groupId", id) }, GroupInfo.serializer())
    /**
     * The server rewrites category and ageBand on every update (missing = cleared), so callers that
     * don't edit them must pass the values they loaded to keep them.
     */
    suspend fun groupUpdate(
        id: String, title: String, description: String, rules: String, city: String, visibility: String, coverId: String?,
        category: String? = null, ageBand: String? = null,
    ) =
        done("groupUpdate", buildJsonObject {
            put("groupId", id); put("title", title); put("description", description); put("rules", rules)
            put("city", city); put("visibility", visibility); put("coverId", coverId ?: "")
            category?.let { put("category", it) }; ageBand?.let { put("ageBand", it) }
        })
    suspend fun groupArchive(id: String) = done("groupArchive", buildJsonObject { put("groupId", id) })
    suspend fun groupSetRole(id: String, userId: String, role: String) = done("groupSetRole", buildJsonObject { put("groupId", id); put("userId", userId); put("role", role) })
    suspend fun groupCancelRequest(id: String) = done("groupCancelRequest", buildJsonObject { put("groupId", id) })
    suspend fun groupNotify(id: String, on: Boolean) = done("groupNotify", buildJsonObject { put("groupId", id); put("on", on) })
    suspend fun createGroup(title: String, description: String, visibility: String, city: String, rules: String) = idOf("createGroup", buildJsonObject {
        put("title", title); put("description", description); put("visibility", visibility); put("city", city); put("rules", rules)
    })
    suspend fun join(id: String) = done("joinOrRequest", buildJsonObject { put("groupId", id) })
    suspend fun leave(id: String) = done("leave", buildJsonObject { put("groupId", id) })
    suspend fun approve(id: String, userId: String) = done("approveMember", buildJsonObject { put("groupId", id); put("userId", userId) })
    suspend fun removeMember(id: String, userId: String) = done("removeMember", buildJsonObject { put("groupId", id); put("userId", userId) })

    // ---- people
    suspend fun profile(userId: String) = get("userProfile", buildJsonObject { put("userId", userId) }, PublicProfile.serializer())

    // ---- uploads
    /** Image picked from the gallery → resized JPEG → file id. */
    suspend fun uploadImage(context: Context, uri: Uri): ApiResult<String> {
        val bytes = com.hamraheteyf.app.ui.common.prepareImageForUpload(context, uri)
            ?: return ApiResult.Fail("خواندنِ تصویر ممکن نشد.")
        return when (val r = api.upload(bytes, "image/jpeg", "image.jpg")) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> idFromUrl(r.data)?.let { ApiResult.Ok(it) } ?: ApiResult.Fail("بارگذاری ناموفق بود.")
        }
    }

    /**
     * Streams an mp4 from the gallery to /api/upload-video (≤250 MB) with progress 0..100.
     * Cancelling the caller really stops it: a watcher closes the connection the moment the coroutine is
     * cancelled (a write blocked on a slow network would otherwise keep going), and the CancellationException
     * is rethrown instead of being reported as a network failure.
     */
    suspend fun uploadVideo(context: Context, uri: Uri, onProgress: (Int) -> Unit): ApiResult<String> = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val size = runCatching { cr.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: -1L
        if (size <= 0) return@withContext ApiResult.Fail("حجمِ فایل مشخص نیست.")
        if (size > 250L * 1024 * 1024) return@withContext ApiResult.Fail("حجمِ ویدیو باید کمتر از ۲۵۰ مگابایت باشد.")
        val c = try {
            URL(com.hamraheteyf.app.BuildConfig.API_BASE_URL + "upload-video").openConnection() as HttpURLConnection
        } catch (e: java.io.IOException) {
            return@withContext ApiResult.Fail("بارگذاریِ ویدیو ممکن نشد. اتصال اینترنت را بررسی کنید.", network = true)
        }
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { c.disconnect() }
        }
        try {
            c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 120000; c.doOutput = true
            c.setFixedLengthStreamingMode(size)
            c.setRequestProperty("Content-Type", "video/mp4")
            c.setRequestProperty("X-Filename", "video.mp4")
            api.token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            (cr.openInputStream(uri) ?: throw java.io.IOException("no stream")).use { inp ->
                c.outputStream.use { out ->
                    val buf = ByteArray(256 * 1024); var sent = 0L; var n: Int
                    while (inp.read(buf).also { n = it } > 0) {
                        ensureActive()
                        out.write(buf, 0, n); sent += n
                        onProgress(((sent * 100) / size).toInt().coerceAtMost(100))
                    }
                }
            }
            val ok = c.responseCode in 200..299
            val text = (if (ok) c.inputStream else (c.errorStream ?: c.inputStream)).bufferedReader().use(BufferedReader::readText)
            val obj = runCatching { api.json.parseToJsonElement(text).jsonObject }.getOrNull()
            val id = (obj?.get("file") as? JsonObject)?.get("id")?.let { (it as? JsonPrimitive)?.content }
            if (ok && id != null) ApiResult.Ok(id)
            else ApiResult.Fail((obj?.get("error") as? JsonPrimitive)?.content ?: "بارگذاریِ ویدیو ناموفق بود.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ensureActive()  // an IOException caused by our own disconnect-on-cancel is a cancellation, not a network error
            ApiResult.Fail("بارگذاریِ ویدیو قطع شد. اتصال اینترنت را بررسی کنید.", network = true)
        } finally {
            watcher.cancel()
            c.disconnect()
        }
    }

    companion object {
        private val ID = Regex("([0-9a-f]{32})")
        fun idFromUrl(url: String?): String? = url?.let { ID.find(it)?.groupValues?.get(1) }
    }
}

/** The screen-level handle. Null only in the offline DEMO build. */
@Composable
fun rememberCommunity(): CommunityClient? {
    val api = LocalAppContainer.current.serverApi
    return remember(api) { api?.let { CommunityClient(it) } }
}
