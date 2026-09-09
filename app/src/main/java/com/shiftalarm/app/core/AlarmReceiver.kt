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

        val serviceIntent = Intent(context, AlarmForegroundService::class.java).apply {
            putExtra(EXTRA_ID, id)
        }
        runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
    }
}
