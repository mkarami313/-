package com.hamraheteyf.app

import com.hamraheteyf.app.net.FileUrlResolver
import com.hamraheteyf.app.net.ResolvedUrls
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FileUrlsTest {
    private val base = "https://x.test/api/"
    private val hour = 3_600_000L
    private var clock = 1_000_000_000_000L
    private val calls = mutableListOf<List<String>>()
    private var online = true
    private var ttlMs = 24 * hour

    private fun resolver() = FileUrlResolver(base, { clock }) { ids ->
        calls += ids
        if (!online) null
        else {
            val exp = (clock + ttlMs) / 1000
            ResolvedUrls(ids.associateWith { "files/$it?exp=$exp&sig=s$exp" }, clock + ttlMs)
        }
    }

    @Test fun bareUrlBeforeResolveThenSignedAndCachedById() = runTest {
        val r = resolver()
        assertEquals(base + "files/abcdef12", r.url("abcdef12"))
        val signed = r.resolve("abcdef12")
        assertTrue(signed.startsWith(base + "files/abcdef12?exp="))
        assertEquals(signed, r.url("abcdef12"))
        r.resolve("abcdef12")
        assertEquals(1, calls.size)  // second resolve served from the cache
    }

    @Test fun refreshesWhenLessThanOneHourLeft() = runTest {
        val r = resolver()
        r.resolve("abcdef12")
        clock += 23 * hour + 1
        assertTrue(r.needsRefresh("abcdef12"))
        assertNotNull(r.cached("abcdef12"))  // still valid for immediate use
        r.resolve("abcdef12")
        assertEquals(2, calls.size)
    }

    @Test fun offlineFallsBackToBareAndBacksOff() = runTest {
        val r = resolver(); online = false
        assertEquals(base + "files/abcdef12", r.resolve("abcdef12"))
        r.resolve("abcdef34")
        assertEquals(1, calls.size)  // failure back-off: no second network attempt right away
        online = true
        assertTrue(r.resolve("abcdef34", force = true).contains("sig="))
    }

    @Test fun forceReSignsEvenIfFresh() = runTest {
        val r = resolver()
        r.resolve("abcdef12"); r.resolve("abcdef12", force = true)
        assertEquals(2, calls.size)
    }

    @Test fun clearDropsSignedUrls() = runTest {
        val r = resolver()
        r.resolve("abcdef12"); r.clear()
        assertEquals(base + "files/abcdef12", r.url("abcdef12"))
    }

    @Test fun idOfRecognisesBareAndSignedUrlsOnly() {
        assertEquals("0123456789abcdef0123456789abcdef", FileUrlResolver.idOf(base + "files/0123456789abcdef0123456789abcdef"))
        assertEquals("0123456789abcdef", FileUrlResolver.idOf(base + "files/0123456789abcdef?exp=1&sig=ab"))
        assertNull(FileUrlResolver.idOf("https://example.com/video.mp4"))
    }
}
