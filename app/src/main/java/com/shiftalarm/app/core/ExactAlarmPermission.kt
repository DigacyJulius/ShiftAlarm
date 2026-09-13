package com.shiftalarm.app.core

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

object ExactAlarmPermission {

    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun openSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            // Below Android 12 exact alarms need no special permission.
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @Composable
    fun PermissionNudgeDialog(
        onDismiss: () -> Unit,
        onOpenSettings: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("需要精確鬧鐘權限") },
            text = {
                Text(
                    "Android 14 起，App 必須獲得「設定鬧鐘與提醒」權限，\n" +
                    "鬧鐘才能在息屏/省電模式下準時響鈴。\n\n" +
                    "點擊下方按鈕前往系統設定開啟。"
                )
            },
            confirmButton = {
                TextButton(onClick = { onOpenSettings(); onDismiss() }) {
                    Text("前往設定", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("稍後") }
            }
        )
    }
}