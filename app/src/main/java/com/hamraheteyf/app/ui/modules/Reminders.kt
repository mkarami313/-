package com.hamraheteyf.app.ui.modules

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hamraheteyf.app.MainActivity
import com.hamraheteyf.app.R
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.net.HamrahApi
import com.hamraheteyf.app.push.PushNotifier
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Pure time helpers for [Reminders] (no Android types, unit-tested). */
object ReminderTimes {
    private val TIME = Regex("(\\d{1,2})\\s*[:٫.]\\s*(\\d{2})")

    fun digits(s: String) = s.map { c ->
        when (c) {
            in '۰'..'۹' -> '0' + (c - '۰')
            in '٠'..'٩' -> '0' + (c - '٠')
            else -> c
        }
    }.joinToString("")

    /** All valid (hour, minute) pairs found in free text such as "۸:۳۰ و 20.00". */
    fun parse(text: String): List<Pair<Int, Int>> =
        TIME.findAll(digits(text)).mapNotNull { t ->
            val h = t.groupValues[1].toInt(); val m = t.groupValues[2].toInt()
            if (h in 0..23 && m in 0..59) h to m else null
        }.toList()

    /** Next wall-clock occurrence of [hour]:[minute] strictly after now (+1s slack). */
    fun nextDaily(nowMs: Long, hour: Int, minute: Int, cal: Calendar = Calendar.getInstance()): Long {
        cal.timeInMillis = nowMs
        cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= nowMs + 1000) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}

/**
 * Local reminders: daily medication times (rebuilt from the server list) and one-shot
 * appointment alerts. Exact alarms are used when allowed; otherwise inexact-while-idle.
 * Everything is rebuilt after reboot, app update and clock/timezone changes ([BootReceiver]).
 */
object Reminders {
    private const val PREFS = "hamrah_reminders"
    private const val KEY = "items"
    private const val KEY_APPTS = "appts"
    const val APPT_LEAD_MS = 60L * 60L * 1000L

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun read(c: Context, key: String) = runCatching { JSONArray(prefs(c).getString(key, "[]")) }.getOrDefault(JSONArray())

    // ------------------------------------------------------------ medications (daily)

    fun sync(context: Context, meds: List<Rec>) {
        val items = JSONArray()
        meds.forEach { m ->
            ReminderTimes.parse(m["times"]).forEach { (h, mi) ->
                items.put(JSONObject().apply {
                    put("key", "${m.id}@$h:$mi"); put("title", "یادآوری: ${m["name"]}")
                    put("body", listOf(m["dose"], m["childName"]).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "زمان مصرف یا انجام کار" })
                    put("h", h); put("m", mi)
                })
            }
        }
        val old = read(context, KEY)
        for (i in 0 until old.length()) cancelKey(context, old.getJSONObject(i).getString("key"))
        prefs(context).edit().putString(KEY, items.toString()).apply()
        scheduleAll(context)
    }

    /** Re-arms every stored alarm (meds + future appointments). Safe to call any time. */
    fun scheduleAll(context: Context) {
        val meds = read(context, KEY)
        for (i in 0 until meds.length()) runCatching { schedule(context, meds.getJSONObject(i)) }
        val now = System.currentTimeMillis()
        val keep = JSONArray()
        val appts = read(context, KEY_APPTS)
        for (i in 0 until appts.length()) {
            val a = appts.getJSONObject(i)
            if (a.getLong("at") > now) { keep.put(a); armAppointment(context, a) }
        }
        if (keep.length() != appts.length()) prefs(context).edit().putString(KEY_APPTS, keep.toString()).apply()
    }

    /** Fetches the medication list and rebuilds alarms; call on app start / sign-in. */
    suspend fun refreshFromServer(ctx: Context, api: HamrahApi) {
        when (val r = RecClient(api).list("med")) {
            is ApiResult.Ok -> sync(ctx, r.data)
            is ApiResult.Fail -> {}
        }
    }

    // ------------------------------------------------------------ appointments (one-shot)

    /** Notifies at [atEpochMs] and one hour before it. Re-scheduling the same [id] replaces it. */
    fun scheduleAppointment(ctx: Context, id: String, title: String, atEpochMs: Long) {
        cancel(ctx, id)
        if (atEpochMs <= System.currentTimeMillis()) return
        val a = JSONObject().put("id", id).put("title", title).put("at", atEpochMs)
        val list = read(ctx, KEY_APPTS).apply { put(a) }
        prefs(ctx).edit().putString(KEY_APPTS, list.toString()).apply()
        armAppointment(ctx, a)
    }

    fun cancel(ctx: Context, id: String) {
        val list = read(ctx, KEY_APPTS)
        val keep = JSONArray()
        for (i in 0 until list.length()) {
            val a = list.getJSONObject(i)
            if (a.getString("id") == id) { cancelKey(ctx, apptKey(id, "at")); cancelKey(ctx, apptKey(id, "pre")) } else keep.put(a)
        }
        // Also cancel when nothing is stored (alarms may outlive prefs after a partial clear).
        cancelKey(ctx, apptKey(id, "at")); cancelKey(ctx, apptKey(id, "pre"))
        prefs(ctx).edit().putString(KEY_APPTS, keep.toString()).apply()
    }

    /** Removes every alarm and stored reminder (sign-out). */
    fun clearAll(ctx: Context) {
        val meds = read(ctx, KEY)
        for (i in 0 until meds.length()) cancelKey(ctx, meds.getJSONObject(i).getString("key"))
        val appts = read(ctx, KEY_APPTS)
        for (i in 0 until appts.length()) {
            val id = appts.getJSONObject(i).getString("id")
            cancelKey(ctx, apptKey(id, "at")); cancelKey(ctx, apptKey(id, "pre"))
        }
        prefs(ctx).edit().clear().apply()
    }

    fun hasAny(ctx: Context): Boolean = read(ctx, KEY).length() > 0 || read(ctx, KEY_APPTS).length() > 0

    /** True when reminders exist but the OS will not show their notifications. */
    fun notificationsBlocked(ctx: Context): Boolean = hasAny(ctx) && !NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun apptKey(id: String, part: String) = "appt:$id:$part"

    private fun armAppointment(ctx: Context, a: JSONObject) {
        val id = a.getString("id"); val title = a.getString("title"); val at = a.getLong("at")
        val now = System.currentTimeMillis()
        if (at - APPT_LEAD_MS > now) {
            val o = JSONObject().put("key", apptKey(id, "pre")).put("title", "یادآوری: $title").put("body", "یک ساعت دیگر").put("once", true)
            setAlarm(ctx, at - APPT_LEAD_MS, pending(ctx, o.getString("key"), o))
        }
        if (at > now) {
            val o = JSONObject().put("key", apptKey(id, "at")).put("title", title).put("body", "زمان نوبت یا قرار فرا رسیده است").put("once", true)
            setAlarm(ctx, at, pending(ctx, o.getString("key"), o))
        }
    }

    // ------------------------------------------------------------ alarm plumbing

    private fun pending(context: Context, key: String, o: JSONObject?): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).putExtra("key", key)
        if (o != null) i.putExtra("item", o.toString())
        return PendingIntent.getBroadcast(context, key.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun cancelKey(context: Context, key: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, key, null))
    }

    private fun setAlarm(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    internal fun schedule(context: Context, o: JSONObject) {
        setAlarm(context, ReminderTimes.nextDaily(System.currentTimeMillis(), o.getInt("h"), o.getInt("m")), pending(context, o.getString("key"), o))
    }

    internal fun fire(context: Context, o: JSONObject) {
        PushNotifier.ensureChannel(context)
        val tap = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, PushNotifier.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify).setContentTitle(o.getString("title")).setContentText(o.getString("body"))
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(tap).build()
        try { NotificationManagerCompat.from(context).notify(o.getString("key").hashCode(), n) } catch (_: SecurityException) {}
        if (!o.optBoolean("once")) schedule(context, o)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val raw = intent.getStringExtra("item") ?: return
        runCatching { Reminders.fire(context, JSONObject(raw)) }
    }
}

/** Re-arms alarms after boot, app update, clock/timezone change or exact-alarm permission change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> runCatching { Reminders.scheduleAll(context) }
        }
    }
}
