package com.hamraheteyf.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Calendar

/** Pure-Kotlin Gregorian <-> Jalali (Solar Hijri) conversion (the jalaali algorithm, 33-year cycle breaks). */
object Jalali {
    data class Ymd(val y: Int, val m: Int, val d: Int)

    val MONTHS = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val WEEKDAYS = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    private val breaks = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178)

    private fun div(a: Int, b: Int) = a / b
    private fun mod(a: Int, b: Int) = a - (a / b) * b

    private class Cal(val leap: Int, val gy: Int, val march: Int)

    private fun cal(jy: Int): Cal {
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return Cal(leap, gy, march)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        val d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) + div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
        return d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
    }

    private fun d2g(jdn: Int): Ymd {
        var x = 4 * jdn + 139361631
        x += div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(x, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(x, 1461) - 100100 + div(8 - gm, 6)
        return Ymd(gy, gm, gd)
    }

    private fun j2d(jy: Int, jm: Int, jd: Int): Int {
        val c = cal(jy)
        return g2d(c.gy, 3, c.march) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1
    }

    private fun d2j(jdn: Int): Ymd {
        val gy = d2g(jdn).y
        var jy = gy - 621
        val c = cal(jy)
        val j1 = g2d(gy, 3, c.march)
        var k = jdn - j1
        if (k >= 0) {
            if (k <= 185) return Ymd(jy, 1 + div(k, 31), mod(k, 31) + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (c.leap == 1) k += 1
        }
        return Ymd(jy, 7 + div(k, 30), mod(k, 30) + 1)
    }

    fun toJalali(gy: Int, gm: Int, gd: Int): Ymd = d2j(g2d(gy, gm, gd))
    fun toGregorian(jy: Int, jm: Int, jd: Int): Ymd = d2g(j2d(jy, jm, jd))

    fun isLeap(jy: Int) = cal(jy).leap == 0
    fun monthLength(jy: Int, jm: Int) = when { jm <= 6 -> 31; jm <= 11 -> 30; isLeap(jy) -> 30; else -> 29 }

    /** 0 = Saturday … 6 = Friday, for a Gregorian date. */
    fun weekdayIndex(gy: Int, gm: Int, gd: Int): Int = mod(g2d(gy, gm, gd) + 2, 7)

    // ---- ISO ("yyyy-MM-dd") helpers ----

    fun iso(y: Ymd) = "%04d-%02d-%02d".format(y.y, y.m, y.d)

    fun parseIso(s: String): Ymd? {
        val p = s.trim().take(10).split('-')
        if (p.size != 3) return null
        val y = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val d = p[2].toIntOrNull() ?: return null
        return if (m in 1..12 && d in 1..31) Ymd(y, m, d) else null
    }

    fun todayIso(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun nowTime(): String {
        val c = Calendar.getInstance()
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    fun isoToJalali(iso: String): Ymd? = parseIso(iso)?.let { toJalali(it.y, it.m, it.d) }

    fun jalaliToIso(jy: Int, jm: Int, jd: Int): String = iso(toGregorian(jy, jm, jd))

    /** "۱۴۰۵/۰۸/۱۰" */
    fun format(j: Ymd): String = "%04d/%02d/%02d".format(j.y, j.m, j.d).toPersianDigits()

    /** ISO gregorian -> "۱۴۰۵/۰۸/۱۰" (or the input unchanged if unparseable). */
    fun display(iso: String): String = isoToJalali(iso)?.let { format(it) } ?: iso

    /** ISO gregorian -> "شنبه ۱۰ آبان ۱۴۰۵". */
    fun displayLong(iso: String): String {
        val g = parseIso(iso) ?: return iso
        val j = toJalali(g.y, g.m, g.d)
        return "${WEEKDAYS[weekdayIndex(g.y, g.m, g.d)]} ${j.d.toPersianDigits()} ${MONTHS[j.m - 1]} ${j.y.toPersianDigits()}"
    }

    /** "۸:۰۵" style time from "08:05". */
    fun displayTime(hhmm: String): String = hhmm.toPersianDigits()

    private fun asciiDigits(s: String) = s.map { c ->
        when (c) { in '۰'..'۹' -> '0' + (c - '۰'); in '٠'..'٩' -> '0' + (c - '٠'); else -> c }
    }.joinToString("")

    /** Finds a Jalali date like 1405/08/15 (any digit script, separators / - .) inside free text -> ISO gregorian. */
    fun findJalaliInText(text: String): String? {
        val m = Regex("(1[34]\\d{2})\\s*[/\\-.]\\s*(\\d{1,2})\\s*[/\\-.]\\s*(\\d{1,2})").find(asciiDigits(text)) ?: return null
        val (y, mo, d) = m.destructured
        val jy = y.toInt(); val jm = mo.toInt(); val jd = d.toInt()
        if (jm !in 1..12 || jd !in 1..monthLength(jy, jm)) return null
        return jalaliToIso(jy, jm, jd)
    }

    /** Finds an HH:mm time inside free text. */
    fun findTimeInText(text: String): String? {
        val m = Regex("(\\d{1,2})\\s*[:٫]\\s*(\\d{2})").find(asciiDigits(text)) ?: return null
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        return if (h in 0..23 && mi in 0..59) "%02d:%02d".format(h, mi) else null
    }

    /** Epoch millis (device time zone) for ISO date + "HH:mm"; null if unparseable. */
    fun epochMs(isoDate: String, hhmm: String): Long? {
        val g = parseIso(isoDate) ?: return null
        val p = hhmm.split(':')
        val h = p.getOrNull(0)?.toIntOrNull() ?: return null
        val mi = p.getOrNull(1)?.toIntOrNull() ?: return null
        val c = Calendar.getInstance()
        c.clear()
        c.set(g.y, g.m - 1, g.d, h, mi, 0)
        return c.timeInMillis
    }

    /** Epoch millis for a free-text moment such as «شنبه ۱۴۰۵/۰۸/۱۵ ساعت ۱۰:۰۰». */
    fun epochMsFromText(text: String): Long? {
        val d = findJalaliInText(text) ?: return null
        return epochMs(d, findTimeInText(text) ?: "09:00")
    }

    /** Day index (days since epoch, local) for grouping/stat bucketing of an ISO date. */
    fun dayNumber(iso: String): Int? = parseIso(iso)?.let { g2d(it.y, it.m, it.d) }
}

/** A Jalali calendar picker dialog. [initialIso] and the result are ISO gregorian ("yyyy-MM-dd"). */
@Composable
fun JalaliDatePickerDialog(initialIso: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val start = Jalali.isoToJalali(initialIso) ?: Jalali.isoToJalali(Jalali.todayIso())!!
    var year by remember { mutableIntStateOf(start.y) }
    var month by remember { mutableIntStateOf(start.m) }
    var day by remember { mutableIntStateOf(start.d) }
    val len = Jalali.monthLength(year, month)
    if (day > len) day = len
    val first = Jalali.toGregorian(year, month, 1)
    val offset = Jalali.weekdayIndex(first.y, first.m, first.d)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (month == 12) { month = 1; year++ } else month++ }) { Icon(Icons.Outlined.ChevronLeft, "ماه بعد") }
                    Text(
                        "${Jalali.MONTHS[month - 1]} ${year.toPersianDigits()}",
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { if (month == 1) { month = 12; year-- } else month-- }) { Icon(Icons.Outlined.ChevronRight, "ماه قبل") }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach {
                        Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = mutedColor())
                    }
                }
                val cells = List(offset) { 0 } + (1..len).toList()
                LazyVerticalGrid(GridCells.Fixed(7), Modifier.heightIn(max = 280.dp)) {
                    items(cells.size) { i ->
                        val d = cells[i]
                        if (d == 0) Spacer(Modifier.size(40.dp)) else {
                            val sel = d == day
                            Surface(
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.padding(2.dp).size(36.dp).clickable { day = d },
                            ) { Box(contentAlignment = Alignment.Center) { Text(d.toPersianDigits(), style = MaterialTheme.typography.bodyMedium) } }
                        }
                    }
                }
                TextButton(onClick = { Jalali.isoToJalali(Jalali.todayIso())?.let { year = it.y; month = it.m; day = it.d } }) { Text("امروز") }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(Jalali.jalaliToIso(year, month, day)) }) { Text("تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

/** A 24-hour time picker dialog; result is "HH:mm". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogFa(initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val p = initial.split(':')
    val state = rememberTimePickerState(p.getOrNull(0)?.toIntOrNull() ?: 8, p.getOrNull(1)?.toIntOrNull() ?: 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب ساعت") },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
        confirmButton = { TextButton(onClick = { onPick("%02d:%02d".format(state.hour, state.minute)) }) { Text("تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

/** Read-only field that opens the Jalali picker. Holds/returns ISO gregorian. */
@Composable
fun JalaliDateField(label: String, iso: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text("$label: ${if (iso.isBlank()) "انتخاب" else Jalali.display(iso)}")
    }
    if (open) JalaliDatePickerDialog(iso, { open = false }) { onChange(it); open = false }
}

@Composable
fun TimeField(label: String, hhmm: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text("$label: ${if (hhmm.isBlank()) "انتخاب" else Jalali.displayTime(hhmm)}")
    }
    if (open) TimePickerDialogFa(hhmm.ifBlank { "08:00" }, { open = false }) { onChange(it); open = false }
}
