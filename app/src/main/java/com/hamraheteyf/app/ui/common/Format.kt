package com.hamraheteyf.app.ui.common

/** Formatting helpers for Persian display (digits and relative time). */

private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

/** Converts ASCII digits in [input] to Persian digits. */
fun String.toPersianDigits(): String = buildString {
    for (c in this@toPersianDigits) {
        if (c in '0'..'9') append(persianDigits[c - '0']) else append(c)
    }
}

fun Int.toPersianDigits(): String = toString().toPersianDigits()

/** A friendly, approximate Persian relative time (e.g. «۳ ساعت پیش»). */
fun relativeTimeFa(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - epochMillis).coerceAtLeast(0)
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "همین حالا"
        minutes < 60 -> "${minutes.toInt().toPersianDigits()} دقیقه پیش"
        hours < 24 -> "${hours.toInt().toPersianDigits()} ساعت پیش"
        days < 30 -> "${days.toInt().toPersianDigits()} روز پیش"
        else -> "${(days / 30).toInt().toPersianDigits()} ماه پیش"
    }
}
