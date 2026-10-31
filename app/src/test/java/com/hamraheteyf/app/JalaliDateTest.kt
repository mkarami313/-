package com.hamraheteyf.app

import com.hamraheteyf.app.ui.common.Jalali
import org.junit.Assert.*
import org.junit.Test

class JalaliDateTest {
    private fun pair(g: Triple<Int, Int, Int>, j: Triple<Int, Int, Int>) {
        val toJ = Jalali.toJalali(g.first, g.second, g.third)
        assertEquals(j, Triple(toJ.y, toJ.m, toJ.d))
        val toG = Jalali.toGregorian(j.first, j.second, j.third)
        assertEquals(g, Triple(toG.y, toG.m, toG.d))
    }

    @Test fun knownPairs() {
        pair(Triple(1970, 1, 1), Triple(1348, 10, 11))
        pair(Triple(2000, 1, 1), Triple(1378, 10, 11))
        pair(Triple(2026, 10, 31), Triple(1405, 8, 9))
    }

    @Test fun nowruzBoundaries() {
        pair(Triple(2021, 3, 21), Triple(1400, 1, 1))
        pair(Triple(2024, 3, 20), Triple(1403, 1, 1))
        pair(Triple(2025, 3, 21), Triple(1404, 1, 1))
        pair(Triple(2021, 3, 20), Triple(1399, 12, 30))
        pair(Triple(2025, 3, 20), Triple(1403, 12, 30))
    }

    @Test fun leapYears() {
        listOf(1395, 1399, 1403, 1408, 1412).forEach { assertTrue("$it", Jalali.isLeap(it)) }
        listOf(1400, 1401, 1402, 1404, 1405).forEach { assertFalse("$it", Jalali.isLeap(it)) }
        assertEquals(30, Jalali.monthLength(1403, 12))
        assertEquals(29, Jalali.monthLength(1404, 12))
        assertEquals(31, Jalali.monthLength(1405, 6))
        assertEquals(30, Jalali.monthLength(1405, 7))
    }

    @Test fun roundTripEveryDayOverDecades() {
        var y = 1395; var m = 1; var d = 1
        repeat(365 * 20) {
            val g = Jalali.toGregorian(y, m, d)
            val back = Jalali.toJalali(g.y, g.m, g.d)
            assertEquals(Triple(y, m, d), Triple(back.y, back.m, back.d))
            d++
            if (d > Jalali.monthLength(y, m)) { d = 1; m++; if (m > 12) { m = 1; y++ } }
        }
    }

    @Test fun displayAndParsing() {
        assertEquals("۱۴۰۵/۰۸/۰۹", Jalali.display("2026-10-31"))
        assertEquals("2026-11-06", Jalali.findJalaliInText("شنبه ۱۴۰۵/۰۸/۱۵ ساعت ۱۰:۰۰"))
        assertEquals("10:00", Jalali.findTimeInText("شنبه ۱۴۰۵/۰۸/۱۵ ساعت ۱۰:۰۰"))
        assertNull(Jalali.findJalaliInText("فردا"))
        assertNull(Jalali.findJalaliInText("1405/13/01"))
    }

    @Test fun weekday() {
        assertEquals(6, Jalali.weekdayIndex(2026, 10, 30)) // Friday
        assertEquals(0, Jalali.weekdayIndex(2026, 10, 31)) // Saturday
    }
}
