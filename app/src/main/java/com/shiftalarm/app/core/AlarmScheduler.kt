package com.shiftalarm.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shiftalarm.app.data.AlarmEntry

object AlarmScheduler {

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

    fun schedule(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Critical: Check if we have permission to schedule exact alarms (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            android.util.Log.w("AlarmScheduler", "Cannot schedule exact alarm: SCHEDULE_EXACT_ALARM permission not granted")
            return
        }
        
        // For user-facing alarms (work and normal), use setAlarmClock for better doze mode support
        if (entry.kind in listOf("work", "normal") && !entry.isSnooze) {
            val info = AlarmManager.AlarmClockInfo(entry.triggerAt, pendingActivity(context, entry))
            try {
                am.setAlarmClock(info, pending(context, entry))
            } catch (e: SecurityException) {
                // Fallback to exact alarm if setAlarmClock fails
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.triggerAt, pending(context, entry))
                } catch (e2: SecurityException) {
                    android.util.Log.e("AlarmScheduler", "Failed to schedule alarm", e2)
                }
            }
        } else {
            // Snooze and test alarms use exact alarm
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.triggerAt, pending(context, entry))
            } catch (e: SecurityException) {
                android.util.Log.e("AlarmScheduler", "Failed to schedule alarm", e)
            }
        }
    }

    fun cancel(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, entry))
    }
}