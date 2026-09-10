package com.shiftalarm.app.core

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun PermissionGateScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    var missingPermissions by remember { mutableStateOf(listOf<String>()) }
    var recheckTrigger by remember { mutableStateOf(0) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Re-evaluate everything once the dialog is answered.
        recheckTrigger++
    }

    fun checkPermissions() {
        val missing = mutableListOf<String>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. 通知權限 (Android 13+) - needed for the full-screen alarm alert
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add("通知權限")
        }

        // 2. 鬧鐘和提醒 (Exact Alarm) - MOST IMPORTANT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            missing.add("鬧鐘和提醒")
        }

        // 3. 勿擾模式繞過
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !nm.isNotificationPolicyAccessGranted) {
            missing.add("勿擾模式繞過")
        }

        // 4. 全螢幕通知 (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !nm.canUseFullScreenIntent()) {
            missing.add("全螢幕鬧鐘通知")
        }

        // 5. 在其他應用上層顯示 - lets the ringing page pop up immediately
        //    even when the app is closed and another app is in the foreground
        if (!Settings.canDrawOverlays(context)) {
            missing.add("在其他應用上層顯示")
        }

        // 6. 電池優化豁免
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            missing.add("電池優化豁免")
        }

        missingPermissions = missing

        if (missing.isEmpty()) {
            onAllGranted()
        }
    }

    LaunchedEffect(Unit) {
        checkPermissions()
    }

    // Re-check automatically whenever the user returns from a system settings
    // screen, so the gate clears itself without tapping the button.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, recheckTrigger) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "需要重要權限",
            fontSize = 22.sp,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "為了讓鬧鐘在 App 關閉或手機休眠時仍然正常響起，請授予以下權限：",
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))

        missingPermissions.forEach { perm ->
            Button(onClick = {
                when (perm) {
                    "通知權限" -> {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    "鬧鐘和提醒" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    }
                    "勿擾模式繞過" -> {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }
                    "全螢幕鬧鐘通知" -> {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                    "在其他應用上層顯示" -> {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                    "電池優化豁免" -> {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }
            }) {
                Text("授予：$perm")
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = { checkPermissions() }) {
            Text("我已授予權限，重新檢查")
        }
    }
}
