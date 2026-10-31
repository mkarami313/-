package com.hamraheteyf.app.ui.onboarding

import android.content.Context

/** Per-device flags for first-run onboarding (keyed per account) and the "I am a professional" hand-off. */
object OnboardingPrefs {
    private fun p(c: Context) = c.getSharedPreferences("hamrah_onboarding", Context.MODE_PRIVATE)

    fun isDone(c: Context, uid: String) = p(c).getBoolean("done_$uid", false)
    fun setDone(c: Context, uid: String) = p(c).edit().putBoolean("done_$uid", true).apply()

    fun setPendingPro(c: Context, v: Boolean) = p(c).edit().putBoolean("pending_pro", v).apply()
    fun takePendingPro(c: Context): Boolean {
        val v = p(c).getBoolean("pending_pro", false)
        if (v) setPendingPro(c, false)
        return v
    }

    fun interests(c: Context, uid: String): Set<String> = p(c).getStringSet("interests_$uid", null) ?: emptySet()
    fun hasInterests(c: Context, uid: String) = p(c).contains("interests_$uid")
    fun setInterests(c: Context, uid: String, ids: Set<String>) = p(c).edit().putStringSet("interests_$uid", ids).apply()
}
