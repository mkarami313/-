package com.hamraheteyf.app

import com.hamraheteyf.app.ui.modules.localTodayAsUtcMidnight
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ShareReportDatesTest {
    private val tehran = TimeZone.getTimeZone("Asia/Tehran")
    private fun utc(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(y, m, d, h, min) }.timeInMillis

    @Test fun justAfterLocalMidnightIsTodayNotYesterday() {
        // 2026-11-07 00:30 Tehran (UTC+3:30) == 2026-11-06 21:00 UTC
        assertEquals(utc(2026, 10, 7), localTodayAsUtcMidnight(utc(2026, 10, 6, 21, 0), tehran))
    }

    @Test fun midDayStaysSameDay() {
        assertEquals(utc(2026, 10, 7), localTodayAsUtcMidnight(utc(2026, 10, 7, 9, 0), tehran))
    }
}
