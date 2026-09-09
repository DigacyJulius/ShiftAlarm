package com.shiftalarm.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE = "com.shiftalarm.app.FIRE_ALARM"
        const val EXTRA_ID = "alarm_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id == -1L) return

        AlarmRingingActivity.ensureChannel(context)

        // Path A: Try to launch the ringing activity directly (most reliable on Android 14+)
        val activityIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_ID, id)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
        }
        runCatching { context.startActivity(activityIntent) }

        // Path B: Also start the foreground service (posts notification + FullScreenIntent as fallback)
        val serviceIntent = Intent(context, AlarmForegroundService::class.java).apply {
            putExtra(EXTRA_ID, id)
        }
        runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
    }
}
