package com.hamraheteyf.app

import com.hamraheteyf.app.ui.modules.Rec
import com.hamraheteyf.app.ui.modules.Reminders
import org.junit.Assert.*
import org.junit.Test

class RemindersReconcileTest {
    private val now = 1_800_000_000_000L
    private fun slot(id: String, at: Long?, booked: Boolean = true, whenText: String = "") =
        Rec(id, buildMap { put("when", whenText); if (at != null) put("epochMs", at.toString()) }, false, "دکتر", 0, if (booked) "BOOKED" else "OPEN", booked, "")

    @Test fun wantsOnlyFutureBookedSlots() {
        val w = Reminders.wantedAppointments(listOf(slot("a", now + 5_000), slot("b", now - 5_000), slot("c", now + 9_000, booked = false)), now)
        assertEquals(setOf("slot_a"), w.keys)
        assertEquals(now + 5_000, w.getValue("slot_a").second)
    }

    @Test fun slotAtPrefersServerEpoch() {
        assertEquals(1234L, Reminders.slotAt(slot("a", 1234L, whenText = "شنبه ۱۴۰۵/۰۸/۱۵ ساعت ۱۰:۰۰")))
        assertNull(Reminders.slotAt(slot("a", null, whenText = "بدون تاریخ")))
    }

    @Test fun planCancelsVanishedSlotsAndSchedulesNewOrChanged() {
        val have = mapOf("slot_old" to ("t" to 1L), "slot_keep" to ("t" to 2L), "slot_moved" to ("t" to 3L), "manual_1" to ("x" to 9L))
        val want = mapOf("slot_keep" to ("t" to 2L), "slot_moved" to ("t" to 30L), "slot_new" to ("n" to 4L))
        val plan = Reminders.planAppointments(want, have)
        assertEquals(listOf("slot_old"), plan.cancel)          // manual_1 is never touched
        assertEquals(setOf("slot_moved", "slot_new"), plan.schedule.keys)
    }

    @Test fun emptyServerListCancelsAllSlotAlarms() {
        val plan = Reminders.planAppointments(emptyMap(), mapOf("slot_a" to ("t" to 1L), "slot_b" to ("t" to 2L)))
        assertEquals(setOf("slot_a", "slot_b"), plan.cancel.toSet())
        assertTrue(plan.schedule.isEmpty())
    }

    @Test fun rescheduleIsIdempotent() {
        val m = mapOf("slot_a" to ("t" to 1L))
        val plan = Reminders.planAppointments(m, m)
        assertTrue(plan.cancel.isEmpty() && plan.schedule.isEmpty())
    }
}
