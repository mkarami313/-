package com.hamraheteyf.app.ui.navigation

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Rules of the admin's «بخش‌های اپ» switches, kept free of Android/Compose so they can be unit-tested.
 * The server enforces the same rules (CORE_FEATURES in hamrah-api.py); this copy only shapes the screen.
 */
object ModuleSwitches {
    /** Never switchable: home, «من» (settings + the admin panel itself) and the forum (the home feed is built from it). */
    val LOCKED: Set<String> = setOf("home", "profile", "qa")

    /** Ids the admin may switch, in the given (display) order. */
    fun switchable(allIds: List<String>): List<String> = allIds.filter { it !in LOCKED }

    /** The disabled set after one switch; a locked id never enters it. */
    fun afterToggle(disabled: Set<String>, id: String, on: Boolean): Set<String> =
        if (on || id in LOCKED) disabled - id else disabled + id

    /** Short warning for sections that older app versions still link to after they are switched off. */
    fun legacyWarning(id: String): String? = when (id) {
        "groups" -> "در نسخه‌های ۱.۴.۰ و قدیمی‌ترِ اپ، میان‌برِ «کانون‌ها» در صفحهٔ انجمن می‌ماند و با لمس، پیامِ «این بخش فعال نیست» می‌دهد."
        "guides" -> "در نسخه‌های ۱.۴.۰ و قدیمی‌ترِ اپ، زبانهٔ «آموزش‌ها» پنهان می‌شود ولی پیوندهای «مطالبِ من» و «ذخیره‌شده‌ها» در صفحهٔ «من» به پیامِ خطا می‌رسند."
        "messages" -> "فقط زبانهٔ «پیام‌ها» پنهان می‌شود؛ دکمهٔ «پیام» در پروفایلِ کاربران همچنان گفتگو باز می‌کند."
        else -> null
    }
}

/** Modules the server has switched off. Persisted so the last known state applies offline. */
object FeatureFlags {
    private val _disabled = MutableStateFlow<Set<String>>(emptySet())
    val disabled: StateFlow<Set<String>> get() = _disabled

    fun load(context: Context) {
        val saved = context.getSharedPreferences("hamrah_features", Context.MODE_PRIVATE).getStringSet("disabled", emptySet())
        _disabled.value = saved?.toSet().orEmpty()
    }

    fun update(context: Context, ids: Set<String>) {
        if (ids == _disabled.value) return
        _disabled.value = ids
        context.getSharedPreferences("hamrah_features", Context.MODE_PRIVATE).edit().putStringSet("disabled", ids).apply()
    }
}
