package com.hamraheteyf.app

import com.hamraheteyf.app.net.StateReply
import com.hamraheteyf.app.net.StateReplyParser
import com.hamraheteyf.app.security.CredentialEnvelope
import com.hamraheteyf.app.ui.modules.ReminderTimes
import com.hamraheteyf.app.update.HashVerdict
import com.hamraheteyf.app.update.UpdateRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Calendar
import java.util.TimeZone
import javax.crypto.Cipher

class ReminderTimesTest {
    @Test fun parsesPersianAndLatinDigits() {
        assertEquals(listOf(8 to 30, 20 to 0), ReminderTimes.parse("۸:۳۰ و 20.00"))
    }

    @Test fun ignoresInvalidTimes() {
        assertEquals(emptyList<Pair<Int, Int>>(), ReminderTimes.parse("25:00 و 10:75 و بدون ساعت"))
    }

    @Test fun nextDailyRollsToTomorrowWhenPassed() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear(); cal.set(2026, Calendar.OCTOBER, 31, 10, 0, 0)
        val now = cal.timeInMillis
        val later = ReminderTimes.nextDaily(now, 12, 0, Calendar.getInstance(TimeZone.getTimeZone("UTC")))
        val earlier = ReminderTimes.nextDaily(now, 8, 0, Calendar.getInstance(TimeZone.getTimeZone("UTC")))
        assertEquals(2 * 3600_000L, later - now)
        assertEquals(22 * 3600_000L, earlier - now)
    }

    @Test fun nextDailyNeverReturnsNow() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear(); cal.set(2026, Calendar.OCTOBER, 31, 10, 0, 0)
        val now = cal.timeInMillis
        assertEquals(24 * 3600_000L, ReminderTimes.nextDaily(now, 10, 0, Calendar.getInstance(TimeZone.getTimeZone("UTC"))) - now)
    }
}

class UpdateRulesTest {
    private val h = "a".repeat(64)

    @Test fun blankOrMalformedExpectedHashIsRefused() {
        assertEquals(HashVerdict.MISSING, UpdateRules.hashVerdict("", h))
        assertEquals(HashVerdict.MISSING, UpdateRules.hashVerdict("   ", h))
        assertEquals(HashVerdict.MISSING, UpdateRules.hashVerdict("abc", "abc"))
    }

    @Test fun matchIsCaseInsensitiveAndMismatchDetected() {
        assertEquals(HashVerdict.OK, UpdateRules.hashVerdict(h.uppercase(), h))
        assertEquals(HashVerdict.MISMATCH, UpdateRules.hashVerdict(h, "b".repeat(64)))
    }

    @Test fun onlyHttpsUrls() {
        assertTrue(UpdateRules.isSecureUrl("https://tizbin.org/hamrah/a.apk"))
        assertFalse(UpdateRules.isSecureUrl("http://tizbin.org/hamrah/a.apk"))
    }

    @Test fun sameAppRules() {
        assertFalse(UpdateRules.sameApp("a.b", "x.y", setOf("c"), setOf("c")))
        assertFalse(UpdateRules.sameApp("a.b", null, emptySet(), emptySet()))
        assertTrue(UpdateRules.sameApp("a.b", "a.b", setOf("c"), setOf("c", "d")))
        assertFalse(UpdateRules.sameApp("a.b", "a.b", setOf("c"), setOf("d")))
        assertTrue(UpdateRules.sameApp("a.b", "a.b", emptySet(), setOf("d")))
    }
}

class StateReplyParserTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun unchangedReply() {
        assertEquals(StateReply.Unchanged, StateReplyParser.parse(obj("""{"ok":true,"data":{"unchanged":true}}""")))
    }

    @Test fun freshReplyCarriesEtag() {
        val r = StateReplyParser.parse(obj("""{"ok":true,"state":{"etag":"v7","users":[]}}"""))
        assertTrue(r is StateReply.Fresh)
        assertEquals("v7", (r as StateReply.Fresh).etag)
    }

    @Test fun freshReplyWithoutEtagIsStillFresh() {
        val r = StateReplyParser.parse(obj("""{"ok":true,"state":{"users":[]}}""")) as StateReply.Fresh
        assertNull(r.etag)
    }

    @Test fun garbageIsInvalid() {
        assertEquals(StateReply.Invalid, StateReplyParser.parse(null))
        assertEquals(StateReply.Invalid, StateReplyParser.parse(obj("""{"ok":true}""")))
    }

    @Test fun unchangedFalseFallsThroughToState() {
        val r = StateReplyParser.parse(obj("""{"data":{"unchanged":false},"state":{"etag":"x"}}"""))
        assertTrue(r is StateReply.Fresh)
    }
}

class CredentialEnvelopeTest {
    private val oaep = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding"

    @Test fun roundTripThroughRsaWrap() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val sealed = CredentialEnvelope.seal("a@b.ir\np\nss wörd") { k ->
            Cipher.getInstance(oaep).apply { init(Cipher.ENCRYPT_MODE, kp.public) }.doFinal(k)
        }
        val plain = CredentialEnvelope.open(sealed) { w ->
            Cipher.getInstance(oaep).apply { init(Cipher.DECRYPT_MODE, kp.private) }.doFinal(w)
        }
        // password may itself contain newlines: only the first one splits.
        assertEquals("a@b.ir" to "p\nss wörd", CredentialEnvelope.split(plain))
    }

    @Test fun wrongKeyFailsInsteadOfReturningGarbage() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val sealed = CredentialEnvelope.seal("a@b.ir\nx") { it }
        assertThrows(Exception::class.java) { CredentialEnvelope.open(sealed) { ByteArray(32) } }
        assertNull(CredentialEnvelope.split("no-newline"))
        assertNotNull(kp)
    }
}
