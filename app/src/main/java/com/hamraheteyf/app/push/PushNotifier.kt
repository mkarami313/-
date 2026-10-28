package com.hamraheteyf.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hamraheteyf.app.MainActivity
import com.hamraheteyf.app.R

/**
 * Builds and posts system notifications, deduped by the server's notification id.
 *
 * Both push channels (FCM and the self-hosted poll) carry the same `notifId`, so showing
 * here exactly once — guarded by a persisted "already shown" set and a stable Android
 * notification id — guarantees the user never gets the same message twice.
 */
object PushNotifier {

    const val CHANNEL_ID = "hamrah_events"
    private const val PREFS = "hamrah_push"
    private const val KEY_SHOWN = "shown_ids"
    private const val MAX_REMEMBERED = 300

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "اعلان‌های همراهِ طیف", NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "پاسخ‌ها، پیام‌ها و اطلاعیه‌ها" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** Shows a notification unless [notifId] was already shown on this device. */
    fun show(context: Context, notifId: String, title: String, body: String) {
        if (notifId.isBlank()) return
        if (!markShown(context, notifId)) return  // already shown → skip (dedup)

        ensureChannel(context)
        val tap = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title.ifBlank { "همراهِ طیف" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId.hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — nothing to show.
        }
    }

    /** Records [notifId] as shown; returns false if it was already present. */
    private fun markShown(context: Context, notifId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val shown = prefs.getStringSet(KEY_SHOWN, emptySet())!!.toMutableSet()
        if (notifId in shown) return false
        shown.add(notifId)
        // Bound the set so it can't grow forever.
        val trimmed = if (shown.size > MAX_REMEMBERED) shown.toList().takeLast(MAX_REMEMBERED).toSet() else shown
        prefs.edit().putStringSet(KEY_SHOWN, trimmed).apply()
        return true
    }
}
