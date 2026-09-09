package com.shiftalarm.app.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shiftalarm.app.data.AppData

@Composable
fun SettingsScreen(
    data: AppData,
    onChange: (AppData) -> Unit
) {
    val context = LocalContext.current
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    var hasExactAlarm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("權限設定", style = MaterialTheme.typography.headlineSmall)

        // 鬧鐘和提醒
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarm) {
            Button(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("授予「鬧鐘和提醒」權限")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text("✅ 已授予「鬧鐘和提醒」權限", color = MaterialTheme.colorScheme.primary)
        }

        // 電池優化豁免
        Button(
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("豁免電池優化")
        }

        // 勿擾模式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("允許勿擾模式繞過")
            }
        }

        // 全螢幕通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Button(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("允許全螢幕鬧鐘通知")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "注意：授予「鬧鐘和提醒」權限後，App 才會出現在「特殊權限 → 鬧鐘和提醒」列表中。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}