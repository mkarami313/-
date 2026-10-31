package com.hamraheteyf.app.ui.modules

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hamraheteyf.app.MainActivity
import com.hamraheteyf.app.R
import com.hamraheteyf.app.push.PushNotifier
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Local daily reminders (medication times). Rebuilt from the server list and after reboot. */
object Reminders {
    private const val PREFS = "hamrah_reminders"
    private const val KEY = "items"

    private fun digits(s: String) = s.map { c -> if (c in '۰'..'۹') ('0' + (c - '۰')) else if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.joinToString("")
    private val TIME = Regex("(\\d{1,2})\\s*[:٫.]\\s*(\\d{2})")

    fun sync(context: Context, meds: List<Rec>) {
        val items = JSONArray()
        meds.forEach { m ->
            TIME.findAll(digits(m["times"])).forEach { t ->
                val h = t.groupValues[1].toInt(); val mi = t.groupValues[2].toInt()
                if (h in 0..23 && mi in 0..59) items.put(JSONObject().apply {
                    put("key", "${m.id}@$h:$mi"); put("title", "یادآوری: ${m["name"]}")
                    put("body", listOf(m["dose"], m["childName"]).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "زمان مصرف یا انجام کار" })
                    put("h", h); put("m", mi)
                })
            }
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until old.length()) cancel(context, old.getJSONObject(i).getString("key"))
        prefs.edit().putString(KEY, items.toString()).apply()
        scheduleAll(context)
    }

    fun scheduleAll(context: Context) {
        val arr = runCatching { JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until arr.length()) schedule(context, arr.getJSONObject(i))
    }

    private fun pending(context: Context, key: String, o: JSONObject?): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).putExtra("key", key)
        if (o != null) i.putExtra("item", o.toString())
        return PendingIntent.getBroadcast(context, key.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun cancel(context: Context, key: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, key, null))
    }

    internal fun schedule(context: Context, o: JSONObject) {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, o.getInt("h")); set(Calendar.MINUTE, o.getInt("m")); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pending(context, o.getString("key"), o))
    }

    internal fun fire(context: Context, o: JSONObject) {
        PushNotifier.ensureChannel(context)
        val tap = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, PushNotifier.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify).setContentTitle(o.getString("title")).setContentText(o.getString("body"))
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(tap).build()
        try { NotificationManagerCompat.from(context).notify(o.getString("key").hashCode(), n) } catch (_: SecurityException) {}
        schedule(context, o)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val raw = intent.getStringExtra("item") ?: return
        runCatching { Reminders.fire(context, JSONObject(raw)) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.scheduleAll(context)
    }
}
