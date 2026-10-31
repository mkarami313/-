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
import com.hamraheteyf.app.ui.common.Jalali
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
    private const val KEY_STEPS = "steps"
    private const val KEY_TIMER_END = "timer_end"
    private const val KEY_TIMER_TOTAL = "timer_total"
    const val APPT_LEAD_MS = 60L * 60L * 1000L
    const val TIMER_KEY = "timer:visual"

    /** True while the visual-timer screen is on screen (it rings in-app, so the alarm notification is skipped). */
    @Volatile var timerScreenOpen = false

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
        val steps = read(context, KEY_STEPS)
        for (i in 0 until steps.length()) runCatching { schedule(context, steps.getJSONObject(i)) }
        val now = System.currentTimeMillis()
        val timerEnd = prefs(context).getLong(KEY_TIMER_END, 0L)
        if (timerEnd > now) armTimer(context, timerEnd) else if (timerEnd != 0L) prefs(context).edit().remove(KEY_TIMER_END).remove(KEY_TIMER_TOTAL).apply()
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
        val client = RecClient(api)
        when (val r = client.list("med")) {
            is ApiResult.Ok -> sync(ctx, r.data)
            is ApiResult.Fail -> {}
        }
        reconcileAppointments(ctx, client)
    }

    // ------------------------------------------------------------ appointment reconcile

    fun slotApptId(slotId: String) = "slot_$slotId"
    fun slotApptTitle(r: Rec) = "نوبت با ${r.ownerName}: ${r["when"]}"
    /** Server-provided epochMs when present, else the time parsed from the Persian text. */
    fun slotAt(r: Rec): Long? = r["epochMs"].toLongOrNull()?.takeIf { it > 0 } ?: Jalali.epochMsFromText(r["when"])

    /**
     * Makes local appointment alarms mirror the server's booked list: re-arms changed/new ones and
     * cancels alarms of slots that were deleted, cancelled or are no longer booked by this user.
     * Only runs its removals when the list was fetched successfully.
     */
    suspend fun reconcileAppointments(ctx: Context, client: RecClient) {
        val booked = (client.list("slot", booked = true) as? ApiResult.Ok)?.data ?: return
        val want = wantedAppointments(booked, System.currentTimeMillis())
        val stored = read(ctx, KEY_APPTS)
        val have = HashMap<String, Pair<String, Long>>()
        for (i in 0 until stored.length()) stored.getJSONObject(i).let { have[it.getString("id")] = it.getString("title") to it.getLong("at") }
        val plan = planAppointments(want, have)
        plan.cancel.forEach { cancel(ctx, it) }
        plan.schedule.forEach { (id, v) -> scheduleAppointment(ctx, id, v.first, v.second) }
    }

    /** Future slots this user holds, as alarm id → (title, start epoch ms). */
    fun wantedAppointments(slots: List<Rec>, nowMs: Long): Map<String, Pair<String, Long>> {
        val want = LinkedHashMap<String, Pair<String, Long>>()
        slots.filter { it.iBooked || it.status == "BOOKED" }.forEach { r ->
            val at = slotAt(r) ?: return@forEach
            if (at > nowMs) want[slotApptId(r.id)] = slotApptTitle(r) to at
        }
        return want
    }

    class AppointmentPlan(val cancel: List<String>, val schedule: Map<String, Pair<String, Long>>)

    /** Diff between wanted and already-armed appointment alarms; only `slot_*` alarms are ever cancelled. */
    fun planAppointments(want: Map<String, Pair<String, Long>>, have: Map<String, Pair<String, Long>>) = AppointmentPlan(
        cancel = have.keys.filter { it.startsWith("slot_") && it !in want },
        schedule = want.filter { (id, v) -> have[id] != v },
    )

    // ------------------------------------------------------------ step alarms (daily, per schedule step, off by default)

    /** idx → (hour, minute) of the stored alarms for one visual schedule. */
    fun stepAlarms(ctx: Context, schedId: String): Map<Int, Pair<Int, Int>> {
        val a = read(ctx, KEY_STEPS); val out = HashMap<Int, Pair<Int, Int>>()
        for (i in 0 until a.length()) a.getJSONObject(i).let { if (it.optString("sid") == schedId) out[it.getInt("idx")] = it.getInt("h") to it.getInt("m") }
        return out
    }

    /** Replaces all step alarms of [schedId]; an empty [times] turns them all off. */
    fun setStepAlarms(ctx: Context, schedId: String, title: String, steps: List<String>, times: Map<Int, Pair<Int, Int>>) {
        val old = read(ctx, KEY_STEPS); val keep = JSONArray()
        for (i in 0 until old.length()) old.getJSONObject(i).let { if (it.optString("sid") == schedId) cancelKey(ctx, it.getString("key")) else keep.put(it) }
        val fresh = ArrayList<JSONObject>()
        times.toSortedMap().forEach { (idx, hm) ->
            if (idx !in steps.indices) return@forEach
            fresh += JSONObject().put("key", "step:$schedId:$idx").put("sid", schedId).put("idx", idx)
                .put("title", "یادآوری: $title").put("body", steps[idx]).put("h", hm.first).put("m", hm.second)
        }
        fresh.forEach { keep.put(it) }
        prefs(ctx).edit().putString(KEY_STEPS, keep.toString()).apply()
        fresh.forEach { runCatching { schedule(ctx, it) } }
    }

    /** Drops alarms of deleted schedules / removed steps and refreshes titles and step texts after an edit. */
    fun reconcileSteps(ctx: Context, schedules: Map<String, Pair<String, List<String>>>) {
        val old = read(ctx, KEY_STEPS); val keep = JSONArray(); var changed = false
        for (i in 0 until old.length()) {
            val it = old.getJSONObject(i)
            val sc = schedules[it.optString("sid")]
            val idx = it.getInt("idx")
            if (sc == null || idx !in sc.second.indices) { cancelKey(ctx, it.getString("key")); changed = true; continue }
            val title = "یادآوری: ${sc.first}"; val body = sc.second[idx]
            if (it.getString("title") != title || it.getString("body") != body) {
                it.put("title", title).put("body", body); changed = true
                runCatching { schedule(ctx, it) }
            }
            keep.put(it)
        }
        if (changed) prefs(ctx).edit().putString(KEY_STEPS, keep.toString()).apply()
    }

    // ------------------------------------------------------------ visual timer (rings even when the app is closed)

    fun startTimer(ctx: Context, endWallMs: Long, totalMs: Long) {
        prefs(ctx).edit().putLong(KEY_TIMER_END, endWallMs).putLong(KEY_TIMER_TOTAL, totalMs).apply()
        armTimer(ctx, endWallMs)
    }

    fun stopTimer(ctx: Context) {
        cancelKey(ctx, TIMER_KEY)
        prefs(ctx).edit().remove(KEY_TIMER_END).remove(KEY_TIMER_TOTAL).apply()
    }

    /** Wall-clock end of a running timer (0 if none) and its full length. */
    fun timerEnd(ctx: Context): Long = prefs(ctx).getLong(KEY_TIMER_END, 0L)
    fun timerTotal(ctx: Context): Long = prefs(ctx).getLong(KEY_TIMER_TOTAL, 0L)

    private fun armTimer(ctx: Context, endWallMs: Long) {
        val o = JSONObject().put("key", TIMER_KEY).put("title", "تایمر تمام شد").put("body", "زمان تایمر تصویری به پایان رسید").put("once", true)
        setAlarm(ctx, endWallMs, pending(ctx, TIMER_KEY, o))
    }

    /** Strong double buzz; needs the (install-time) VIBRATE permission. */
    @Suppress("DEPRECATION")
    fun vibrateDone(ctx: Context) {
        runCatching {
            val v = ctx.getSystemService(android.os.Vibrator::class.java) ?: return
            if (!v.hasVibrator()) return
            val pattern = longArrayOf(0, 500, 250, 500, 250, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            else v.vibrate(pattern, -1)
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
        val steps = read(ctx, KEY_STEPS)
        for (i in 0 until steps.length()) cancelKey(ctx, steps.getJSONObject(i).getString("key"))
        cancelKey(ctx, TIMER_KEY)
        val appts = read(ctx, KEY_APPTS)
        for (i in 0 until appts.length()) {
            val id = appts.getJSONObject(i).getString("id")
            cancelKey(ctx, apptKey(id, "at")); cancelKey(ctx, apptKey(id, "pre"))
        }
        prefs(ctx).edit().clear().apply()
    }

    fun hasAny(ctx: Context): Boolean = read(ctx, KEY).length() > 0 || read(ctx, KEY_APPTS).length() > 0 || read(ctx, KEY_STEPS).length() > 0

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
        if (o.getString("key") == TIMER_KEY) {
            prefs(context).edit().remove(KEY_TIMER_END).remove(KEY_TIMER_TOTAL).apply()
            if (timerScreenOpen && com.hamraheteyf.app.AppForeground.isForeground.value) return  // the screen already rang
            vibrateDone(context)
        }
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
