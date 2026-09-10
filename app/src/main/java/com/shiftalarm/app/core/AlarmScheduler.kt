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
     * Schedule an alarm that fires even when the app is closed and the device
     * is in Doze.
     *
     * All alarms use setExactAndAllowWhileIdle() — the standard alarm API,
     * gated on the "Alarms & reminders" permission (enforced by the permission
     * gate) and proven to work by the test alarm. If the permission is missing
     * or revoked mid-flight, fall back to setAlarmClock(), which needs no
     * special permission and still fires in Doze — but is only a fallback,
     * because some OEM builds do not reliably honour several concurrent
     * alarm-clock registrations from the same app. Either way, the alarm is
     * never silently dropped.
     */
    fun schedule(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (canScheduleExact(am)) {
            try {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, entry.triggerAt, pending(context, entry)
                )
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm rejected, falling back to setAlarmClock", e)
            }
        } else {
            Log.w(TAG, "Exact alarm permission not granted, using setAlarmClock fallback")
        }

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
