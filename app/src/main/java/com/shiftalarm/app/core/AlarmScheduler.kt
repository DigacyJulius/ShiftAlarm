package com.shiftalarm.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.shiftalarm.app.data.AlarmEntry

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    private fun pending(context: Context, entry: AlarmEntry): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ID, entry.id)
        }
        return PendingIntent.getBroadcast(
            context,
            entry.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pendingActivity(context: Context, entry: AlarmEntry): PendingIntent {
        val intent = Intent(context, AlarmRingingActivity::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ID, entry.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            entry.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canScheduleExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    /**
     * Schedule an alarm so it fires even when the app is closed and the device
     * is in Doze.
     *
     * - User-facing alarms (work/normal) always use [AlarmManager.setAlarmClock],
     *   which needs no special permission, fires reliably in Doze and shows the
     *   system alarm indicator.
     * - Snooze/test alarms prefer an exact alarm when the "Alarms & reminders"
     *   permission is granted, and fall back to setAlarmClock when it is not —
     *   a slightly less precise alarm is always better than silently dropping it.
     */
    fun schedule(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val userFacing = entry.kind in listOf("work", "normal") && !entry.isSnooze

        if (userFacing || !canScheduleExact(am)) {
            scheduleAlarmClock(context, am, entry)
        } else {
            try {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, entry.triggerAt, pending(context, entry)
                )
            } catch (e: SecurityException) {
                // Permission was revoked after our check — never drop the alarm,
                // fall back to the permission-free path instead.
                Log.w(TAG, "Exact alarm rejected, falling back to setAlarmClock", e)
                scheduleAlarmClock(context, am, entry)
            }
        }
    }

    private fun scheduleAlarmClock(context: Context, am: AlarmManager, entry: AlarmEntry) {
        val info = AlarmManager.AlarmClockInfo(entry.triggerAt, pendingActivity(context, entry))
        try {
            am.setAlarmClock(info, pending(context, entry))
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule alarm ${entry.id}", e)
        }
    }

    fun cancel(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, entry))
    }
}
