package com.hamraheteyf.app

import com.hamraheteyf.app.net.ImageHosts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Images load from our own API host only (a profile photo must not be able to point at a tracker). */
class ImageHostsTest {
    private val base = "https://proj.tizbin.org/hamrah/api/"
    private val id = "0123456789abcdef0123456789abcdef"

    private fun ok(url: String) = ImageHosts.allowed(url, base)

    @Test fun ownFilesAreAllowedBareAndSigned() {
        assertTrue(ok(base + "files/" + id))
        assertTrue(ok(base + "files/$id?exp=1900000000&sig=abcdef"))
        assertTrue(ok("https://PROJ.tizbin.org/hamrah/api/files/$id"))
        assertTrue(ok("https://proj.tizbin.org:443/hamrah/api/files/$id"))
    }

    @Test fun otherHostsAreRefused() {
        assertFalse(ok("https://tracker.example/p.png"))
        assertFalse(ok("https://tracker.example/hamrah/api/files/$id"))
        assertFalse(ok("https://proj.tizbin.org.evil.example/hamrah/api/files/$id"))
        assertFalse(ok("https://evil.example/?x=https://proj.tizbin.org/hamrah/api/files/$id"))
        assertFalse(ok("https://proj.tizbin.org@evil.example/files/$id"))
        assertFalse(ok("https://tizbin.org/hamrah/api/files/$id"))
    }

    @Test fun otherSchemesPortsAndJunkAreRefused() {
        assertFalse(ok("http://proj.tizbin.org/hamrah/api/files/$id"))
        assertFalse(ok("https://proj.tizbin.org:8443/hamrah/api/files/$id"))
        assertFalse(ok("//proj.tizbin.org/hamrah/api/files/$id"))
        assertFalse(ok("files/$id"))
        assertFalse(ok("file:///data/data/x"))
        assertFalse(ok("content://media/1"))
        assertFalse(ok("javascript:alert(1)"))
        assertFalse(ok(""))
        assertFalse(ok("not a url"))
    }
}
