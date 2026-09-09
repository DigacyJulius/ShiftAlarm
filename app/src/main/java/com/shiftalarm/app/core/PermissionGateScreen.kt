package com.shiftalarm.app.core

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.core.app.NotificationManagerCompat

@Composable
fun PermissionGateScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    var missingPermissions by remember { mutableStateOf(listOf<String>()) }

    fun checkPermissions() {
        val missing = mutableListOf<String>()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Check notification policy (DND) access
        if (Build.VERSION.SDK_INT >= 23 && !nm.isNotificationPolicyAccessGranted) {
            missing.add("勿擾模式繞過 (Do Not Disturb)")
        }

        // Check full screen intent permission (Android 14+)
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            missing.add("全螢幕鬧鐘通知")
        }

        // Check battery optimization exemption
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "需要權限才能正常使用",
            fontSize = 22.sp,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "為了確保鬧鐘能夠在鎖屏及勿擾模式下正常響起，請授予以下權限：",
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))

        missingPermissions.forEach { perm ->
            Button(onClick = {
                when (perm) {
                    "勿擾模式繞過 (Do Not Disturb)" -> {
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
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = { checkPermissions() }) {
            Text("我已授予權限，重新檢查")
        }
    }
}