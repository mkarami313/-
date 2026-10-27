package com.hamraheteyf.app.data.repository.firebase

import com.google.firebase.firestore.FieldValue
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.QuestionRepository
import com.hamraheteyf.app.data.repository.fake.DELETED_USER_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Questions & answers in Firestore. Lists are filtered/sorted client-side to avoid requiring
 * composite indexes for the small v1 data volume (revisit with indexes + pagination at scale).
 */
class FirebaseQuestionRepository : QuestionRepository {

    private val db = firestore()

    override fun observeQuestions(sort: QuestionSort, category: QuestionCategory?): Flow<List<Question>> =
        db.collection(Fs.QUESTIONS).asListFlow(::questionFromDoc).map { list ->
            list.filter { it.status == ContentStatus.PUBLISHED && (category == null || it.category == category) }
                .let { items ->
                    when (sort) {
                        QuestionSort.NEWEST -> items.sortedByDescending { it.createdAt }
                        QuestionSort.MOST_HELPFUL -> items.sortedWith(
                            compareByDescending<Question> { it.helpfulCount }.thenByDescending { it.createdAt })
                    }
                }
        }

    override fun observeQuestion(questionId: String): Flow<Question?> =
        db.collection(Fs.QUESTIONS).document(questionId).asDocFlow(::questionFromDoc)
            .map { if (it?.status == ContentStatus.REMOVED) null else it }

    override fun observeAnswers(questionId: String): Flow<List<Answer>> =
        db.collection(Fs.ANSWERS).whereEqualTo("questionId", questionId).asListFlow(::answerFromDoc)
            .map { list ->
                list.filter { it.status == ContentStatus.PUBLISHED }
                    .sortedWith(compareByDescending<Answer> { it.helpfulCount }.thenByDescending { it.createdAt })
            }

    override suspend fun createQuestion(title: String, body: String, category: QuestionCategory, anonymous: Boolean): DataResult<String> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        if (title.trim().length < 3) return fbError("عنوان پرسش را کامل‌تر بنویسید.")
        if (body.trim().length < 5) return fbError("متن پرسش را کامل‌تر بنویسید.")
        return try {
            val q = Question(id = "", authorId = id, isAnonymous = anonymous, category = category,
                title = title.trim(), body = body.trim(), createdAt = now(), updatedAt = now())
            val ref = db.collection(Fs.QUESTIONS).add(q.toMap()).await()
            DataResult.Success(ref.id)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    override suspend fun editQuestion(questionId: String, title: String, body: String): DataResult<Unit> = try {
        db.collection(Fs.QUESTIONS).document(questionId)
            .update(mapOf("title" to title.trim(), "body" to body.trim(), "updatedAt" to now())).await()
        DataResult.Success(Unit)
    } catch (e: Exception) { fbError(friendly(e)) }

    override suspend fun deleteQuestion(questionId: String): DataResult<Unit> = try {
        // Remove the question and its answers.
        db.collection(Fs.ANSWERS).whereEqualTo("questionId", questionId).get().await()
            .documents.forEach { runCatching { it.reference.delete().await() } }
        db.collection(Fs.QUESTIONS).document(questionId).delete().await()
        DataResult.Success(Unit)
    } catch (e: Exception) { fbError(friendly(e)) }

    override suspend fun setCommentsClosed(questionId: String, closed: Boolean): DataResult<Unit> = try {
        db.collection(Fs.QUESTIONS).document(questionId)
            .update(mapOf("commentsClosed" to closed, "updatedAt" to now())).await()
        DataResult.Success(Unit)
    } catch (e: Exception) { fbError(friendly(e)) }

    override suspend fun createAnswer(questionId: String, body: String): DataResult<Unit> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        if (body.trim().length < 2) return fbError("متن پاسخ را بنویسید.")
        return try {
            val qSnap = db.collection(Fs.QUESTIONS).document(questionId).get().await()
            if (!qSnap.exists()) return fbError("پرسش یافت نشد.")
            val question = questionFromDoc(qSnap)
            if (question.commentsClosed) return fbError("امکان پاسخ به این پرسش بسته شده است.")
            val answer = Answer(id = "", questionId = questionId, authorId = id, body = body.trim(), createdAt = now(), updatedAt = now())
            db.collection(Fs.ANSWERS).add(answer.toMap()).await()
            db.collection(Fs.QUESTIONS).document(questionId)
                .update(mapOf("answerCount" to FieldValue.increment(1), "updatedAt" to now())).await()
            if (question.authorId != id && question.authorId != DELETED_USER_ID) {
                pushNotification(question.authorId, NotificationType.QUESTION_ANSWERED,
                    "پاسخ تازه", "به یکی از پرسش‌های شما پاسخ داده شد.", questionId)
            }
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }

    override suspend fun toggleHelpful(answerId: String): DataResult<Unit> {
        val id = currentUid() ?: return fbError(FB_NOT_SIGNED_IN)
        return try {
            val ref = db.collection(Fs.ANSWERS).document(answerId)
            val snap = ref.get().await()
            if (!snap.exists()) return fbError("پاسخ یافت نشد.")
            val answer = answerFromDoc(snap)
            val liked = id in answer.helpfulByUserIds
            ref.update("helpfulByUserIds", if (liked) FieldValue.arrayRemove(id) else FieldValue.arrayUnion(id)).await()
            // Keep the question's aggregate helpful tally roughly in sync.
            db.collection(Fs.QUESTIONS).document(answer.questionId)
                .update("helpfulCount", FieldValue.increment(if (liked) -1L else 1L)).await()
            DataResult.Success(Unit)
        } catch (e: Exception) { fbError(friendly(e)) }
    }
}
