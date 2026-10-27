package com.hamraheteyf.app

import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.*
import com.hamraheteyf.app.data.repository.*
import com.hamraheteyf.app.data.repository.fake.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests covering the acceptance-critical flows against the in-memory repositories:
 * login, data-access isolation, question/answer, group creation & membership, reporting,
 * verified-badge separation and account deletion.
 */
class RepositoriesTest {

    private fun fixture(): Fixture {
        val store = FakeStore(SeedData.initial(), SeedData.credentials())
        return Fixture(
            store = store,
            auth = FakeAuthRepository(store),
            users = FakeUserRepository(store),
            questions = FakeQuestionRepository(store),
            groups = FakeGroupRepository(store),
            reports = FakeReportRepository(store),
            admin = FakeAdminRepository(store),
        )
    }

    private data class Fixture(
        val store: FakeStore,
        val auth: AuthRepository,
        val users: UserRepository,
        val questions: QuestionRepository,
        val groups: GroupRepository,
        val reports: ReportRepository,
        val admin: AdminRepository,
    )

    private suspend fun Fixture.loginFamily1() {
        assertTrue(auth.login(SeedData.EMAIL_FAMILY1, FakeStore.DEMO_PASSWORD) is DataResult.Success)
    }

    @Test
    fun login_succeeds_with_correct_credentials() = runTest {
        val f = fixture()
        val result = f.auth.login(SeedData.EMAIL_ADMIN, FakeStore.DEMO_PASSWORD)
        assertTrue(result is DataResult.Success)
        assertEquals(SeedData.U_ADMIN, f.auth.currentUserId.value)
    }

    @Test
    fun login_fails_with_wrong_password() = runTest {
        val f = fixture()
        val result = f.auth.login(SeedData.EMAIL_FAMILY1, "wrong")
        assertTrue(result is DataResult.Error)
        assertNull(f.auth.currentUserId.value)
    }

    @Test
    fun register_does_not_grant_verified_badge() = runTest {
        val f = fixture()
        val reg = f.auth.register("کاربر تست", "newpro2@example.com", "secret1", AccountType.PROFESSIONAL, acceptedTerms = true)
        assertTrue(reg is DataResult.Success)

        // Submitting a verification request must NOT produce a verified profile.
        assertTrue(f.users.submitVerificationRequest("گفتاردرمانگر", "X", "نمونه") is DataResult.Success)
        val myId = f.auth.currentUserId.value!!
        val profile = f.users.observeProfessional(myId).first()
        assertEquals(VerificationStatus.PENDING, profile?.verificationStatus)
        val badge = resolveAuthorBadge(AccountType.PROFESSIONAL, profile?.verificationStatus)
        assertEquals(AuthorBadge.PROFESSIONAL_UNVERIFIED, badge)
    }

    @Test
    fun registration_requires_accepting_terms() = runTest {
        val f = fixture()
        val reg = f.auth.register("کاربر", "t@example.com", "secret1", AccountType.FAMILY, acceptedTerms = false)
        assertTrue(reg is DataResult.Error)
    }

    @Test
    fun admin_can_verify_and_badge_becomes_verified() = runTest {
        val f = fixture()
        assertTrue(f.auth.login(SeedData.EMAIL_ADMIN, FakeStore.DEMO_PASSWORD) is DataResult.Success)
        // The pending request belongs to U_PROF_PENDING (seed v_prof_p).
        assertTrue(f.admin.reviewVerification("v_prof_p", VerificationStatus.VERIFIED, "مدارک بررسی شد") is DataResult.Success)
        val profile = f.users.observeProfessional(SeedData.U_PROF_PENDING).first()
        assertEquals(VerificationStatus.VERIFIED, profile?.verificationStatus)
    }

    @Test
    fun create_question_requires_login_then_appears_in_feed() = runTest {
        val f = fixture()
        assertTrue(f.questions.createQuestion("عنوان", "متن پرسش", QuestionCategory.SCHOOL, false) is DataResult.Error)

        f.loginFamily1()
        val create = f.questions.createQuestion("عنوان تست", "متن پرسش تست", QuestionCategory.SCHOOL, false)
        assertTrue(create is DataResult.Success)
        val list = f.questions.observeQuestions(QuestionSort.NEWEST, null).first()
        assertTrue(list.any { it.title == "عنوان تست" })
    }

    @Test
    fun answer_increments_count_and_notifies_author() = runTest {
        val f = fixture()
        // q_1 author is family1; answer as community member.
        assertTrue(f.auth.login("volunteer@example.com", FakeStore.DEMO_PASSWORD) is DataResult.Success)
        val before = f.questions.observeQuestion("q_1").first()!!.answerCount
        assertTrue(f.questions.createAnswer("q_1", "یک پاسخ تست") is DataResult.Success)
        val after = f.questions.observeQuestion("q_1").first()!!.answerCount
        assertEquals(before + 1, after)

        // Author (family1) should have a new notification.
        f.loginFamily1()
        val notifs = FakeNotificationRepository(f.store).observeNotifications().first()
        assertTrue(notifs.any { it.type == NotificationType.QUESTION_ANSWERED })
    }

    @Test
    fun private_child_data_is_isolated_per_user() = runTest {
        val f = fixture()
        f.loginFamily1()
        assertTrue(f.users.observeChildren().first().isNotEmpty()) // family1 has a seeded child

        assertTrue(f.auth.login("parisa@example.com", FakeStore.DEMO_PASSWORD) is DataResult.Success) // family2
        assertTrue(f.users.observeChildren().first().isEmpty()) // cannot see family1's child
    }

    @Test
    fun private_group_posts_hidden_from_non_members() = runTest {
        val f = fixture()
        // family1 is NOT a member of the private group g_private.
        f.loginFamily1()
        assertTrue(f.groups.observePosts("g_private").first().isEmpty())
        assertNull(f.groups.observeGroup("g_private").first()) // not even visible

        // family2 IS a member.
        assertTrue(f.auth.login("parisa@example.com", FakeStore.DEMO_PASSWORD) is DataResult.Success)
        assertNotNull(f.groups.observeGroup("g_private").first())
    }

    @Test
    fun create_group_makes_creator_owner_and_join_works() = runTest {
        val f = fixture()
        f.loginFamily1()
        val create = f.groups.createGroup("گروه تست", "توضیح", null, null, GroupVisibility.PUBLIC, null)
        assertTrue(create is DataResult.Success)
        val groupId = (create as DataResult.Success<String>).data
        val myMembership = f.groups.observeMyMembership(groupId).first()
        assertEquals(MembershipRole.OWNER, myMembership?.role)

        // Another user joins a public group immediately.
        assertTrue(f.auth.login("volunteer@example.com", FakeStore.DEMO_PASSWORD) is DataResult.Success)
        assertTrue(f.groups.joinOrRequest(groupId) is DataResult.Success)
        assertEquals(MembershipStatus.ACTIVE, f.groups.observeMyMembership(groupId).first()?.status)
    }

    @Test
    fun private_group_join_requires_approval() = runTest {
        val f = fixture()
        f.loginFamily1()
        assertTrue(f.groups.joinOrRequest("g_private") is DataResult.Success)
        assertEquals(MembershipStatus.REQUESTED, f.groups.observeMyMembership("g_private").first()?.status)

        // Owner (educator) approves.
        assertTrue(f.auth.login("teacher@example.com", FakeStore.DEMO_PASSWORD) is DataResult.Success)
        assertTrue(f.groups.approveMember("g_private", SeedData.U_FAMILY1) is DataResult.Success)

        f.loginFamily1()
        assertEquals(MembershipStatus.ACTIVE, f.groups.observeMyMembership("g_private").first()?.status)
    }

    @Test
    fun report_is_visible_only_to_admin() = runTest {
        val f = fixture()
        f.loginFamily1()
        assertTrue(f.reports.createReport(ContentType.QUESTION, "q_2", ReportReason.SPAM, "نمونه") is DataResult.Success)
        // Non-admin sees no open reports.
        assertTrue(f.reports.observeOpenReports().first().isEmpty())

        assertTrue(f.auth.login(SeedData.EMAIL_ADMIN, FakeStore.DEMO_PASSWORD) is DataResult.Success)
        assertTrue(f.reports.observeOpenReports().first().isNotEmpty())
    }

    @Test
    fun delete_account_removes_private_data_and_anonymizes_public_content() = runTest {
        val f = fixture()
        f.loginFamily1()
        val before = f.questions.observeQuestions(QuestionSort.NEWEST, null).first().count { it.authorId == SeedData.U_FAMILY1 }
        assertTrue(before > 0)

        assertTrue(f.auth.deleteAccount() is DataResult.Success)
        assertNull(f.auth.currentUserId.value)

        val db = f.store.state.value
        assertTrue(db.users.none { it.id == SeedData.U_FAMILY1 })          // user removed
        assertTrue(db.children.none { it.familyUserId == SeedData.U_FAMILY1 }) // private child removed
        assertTrue(db.questions.none { it.authorId == SeedData.U_FAMILY1 })    // authored questions anonymized
        assertTrue(db.questions.any { it.authorId == DELETED_USER_ID && it.isAnonymous }) // tombstoned
    }

    @Test
    fun professional_directory_excludes_credentials() = runTest {
        val f = fixture()
        f.loginFamily1()
        val results = f.users.searchProfessionals(ProfessionalFilter(verifiedOnly = true)).first()
        assertTrue(results.isNotEmpty())
        // The directory item only exposes public profile + user; there is no credential field on it.
        results.forEach { item ->
            assertEquals(VerificationStatus.VERIFIED, item.profile.verificationStatus)
        }
    }
}
