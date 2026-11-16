package com.hamraheteyf.app

import com.hamraheteyf.app.ui.navigation.AppFeatures
import com.hamraheteyf.app.ui.navigation.ModuleSwitches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules of the admin panel's «بخش‌های اپ» switches. */
class ModuleSwitchesTest {
    /** KNOWN_FEATURES in server/hamrah-api.py: the server refuses any other id. */
    private val serverKnown = setOf(
        "home", "qa", "groups", "messages", "guides", "directory", "donations", "childfile",
        "booking", "tools", "courses", "places", "profile", "cards",
    )

    @Test fun everySectionOfTheAppIsKnownToTheServer() {
        val ids = AppFeatures.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(serverKnown, ids.toSet())
        assertTrue(AppFeatures.all.all { it.title.isNotBlank() })
    }

    @Test fun coreSectionsAreNeverSwitchable() {
        val ids = AppFeatures.all.map { it.id }
        val sw = ModuleSwitches.switchable(ids)
        assertEquals(setOf("home", "profile", "qa"), ModuleSwitches.LOCKED)
        assertTrue(sw.none { it in ModuleSwitches.LOCKED })
        assertEquals(ids.size - 3, sw.size)
        assertEquals(ids.filter { it !in ModuleSwitches.LOCKED }, sw)   // display order kept
        // the four sections hidden on the live server must stay switchable from the panel
        assertTrue(sw.containsAll(listOf("directory", "booking", "donations", "courses")))
    }

    @Test fun toggleAddsAndRemovesButNeverLocksOutACoreSection() {
        val d0 = setOf("booking")
        assertEquals(setOf("booking", "courses"), ModuleSwitches.afterToggle(d0, "courses", on = false))
        assertEquals(emptySet<String>(), ModuleSwitches.afterToggle(d0, "booking", on = true))
        assertEquals(d0, ModuleSwitches.afterToggle(d0, "booking", on = false))
        assertEquals(d0, ModuleSwitches.afterToggle(d0, "courses", on = true))
        for (core in ModuleSwitches.LOCKED) {
            assertFalse(core in ModuleSwitches.afterToggle(d0, core, on = false))
            assertEquals(d0, ModuleSwitches.afterToggle(d0 + core, core, on = false))
        }
    }

    @Test fun sectionsThatOlderVersionsStillLinkToCarryAWarning() {
        assertNotNull(ModuleSwitches.legacyWarning("groups"))
        assertNotNull(ModuleSwitches.legacyWarning("guides"))
        assertTrue(ModuleSwitches.legacyWarning("groups")!!.contains("کانون‌ها"))
        assertNull(ModuleSwitches.legacyWarning("courses"))
        assertNull(ModuleSwitches.legacyWarning("home"))
    }
}
