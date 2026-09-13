package com.shiftalarm.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    /**
     * Schedule an alarm that fires even when the app is closed and the
     * device is in Doze — same strategy as the original working code
     * (commit 58f548a / a251560):
     *   1. setExactAndAllowWhileIdle() — exact, Doze-proof (needs the
     *      "Alarms & reminders" permission; USE_EXACT_ALARM is also declared
     *      so it is normally auto-granted).
     *   2. setAlarmClock() — still fires in Doze, needs the same permission
     *      on Android 14+ (rejected with SecurityException there).
     *   3. setAndAllowWhileIdle() — the ORIGINAL Doze fallback from commit
     *      58f548a: no permission needed, not exact but still wakes the
     *      device in Doze. An alarm is NEVER silently dropped.
     */
    fun schedule(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, entry.triggerAt, pending(context, entry)
            )
            return
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm rejected, trying setAlarmClock", e)
        }

        try {
            val info = AlarmManager.AlarmClockInfo(entry.triggerAt, pendingActivity(context, entry))
            am.setAlarmClock(info, pending(context, entry))
            return
        } catch (e: SecurityException) {
            Log.w(TAG, "setAlarmClock rejected, using inexact Doze fallback", e)
        }

        // Commit-58 fallback: inexact but Doze-capable, no permission needed.
        runCatching {
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, entry.triggerAt, pending(context, entry)
            )
        }.onFailure {
            Log.e(TAG, "Failed to schedule alarm ${entry.id}", it)
        }
    }

    fun cancel(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, entry))
    }
}
