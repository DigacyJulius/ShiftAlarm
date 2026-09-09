package com.shiftalarm.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiftalarm.app.core.AlarmScheduler
import com.shiftalarm.app.core.CountdownNotificationManager
import com.shiftalarm.app.core.PermissionGateScreen
import com.shiftalarm.app.core.SyncEngine
import com.shiftalarm.app.data.AlarmEntry
import com.shiftalarm.app.data.AppData
import com.shiftalarm.app.data.Store
import com.shiftalarm.app.ui.DiagnosticsScreen
import com.shiftalarm.app.ui.NormalAlarmsScreen
import com.shiftalarm.app.ui.ProfilesScreen
import com.shiftalarm.app.ui.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    secondary = Color(0xFF625B71),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color.Black,
    secondary = Color(0xFFCCC2DC),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.READ_CALENDAR

        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())

        // Check for upcoming alarms and show countdown notification if needed
        lifecycleScope.launch {
            val countdownManager = CountdownNotificationManager(this@MainActivity)
            countdownManager.checkAndShowCountdown()
        }

        setContent {
            var permissionsGranted by remember { mutableStateOf(false) }

            if (!permissionsGranted) {
                PermissionGateScreen(onAllGranted = { permissionsGranted = true })
            } else {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { Store(context) }
    val data by remember { store.data }.collectAsStateWithLifecycle(initialValue = AppData())
    var tab by remember { mutableStateOf(0) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val dayFmt = remember { SimpleDateFormat("MM/dd (E)", Locale.TRADITIONAL_CHINESE) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.TRADITIONAL_CHINESE) }

    fun doSync() {
        scope.launch {
            SyncEngine.sync(context)
            syncMessage = "已同步 ${java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}"
            kotlinx.coroutines.delay(2000)
            syncMessage = null
        }
    }

    LaunchedEffect(Unit) { doSync() }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("首頁") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Place, null) },
                        label = { Text("地點設定檔") }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Alarm, null) },
                        label = { Text("一般鬧鐘") }
                    )
                    NavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("設定") }
                    )
                    NavigationBarItem(
                        selected = tab == 4,
                        onClick = { tab = 4 },
                        icon = { Icon(Icons.Default.BugReport, null) },
                        label = { Text("診斷") }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(data, onSync = { doSync() }, syncMessage)
                    1 -> ProfilesScreen(data, onChange = { scope.launch { store.save(it) } })
                    2 -> NormalAlarmsScreen(data, onChange = { scope.launch { store.save(it) } })
                    3 -> SettingsScreen(data, onChange = { scope.launch { store.save(it) } })
                    4 -> DiagnosticsScreen()
                }
            }
        }
    }
}

@Composable
fun HomeScreen(data: AppData, onSync: () -> Unit, syncMessage: String?) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val upcoming = data.scheduled.filter { it.triggerAt > now }.sortedBy { it.triggerAt }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("未來鬧鐘", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onSync) { Text("立即同步") }
        }
        if (syncMessage != null) {
            Text(syncMessage, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))

        if (upcoming.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暫無即將響起的鬧鐘", color = Color.Gray)
            }
        } else {
            LazyColumn {
                items(upcoming) { entry ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.label, fontWeight = FontWeight.Medium)
                                Text("${dayFmt.format(Date(entry.triggerAt))} ${timeFmt.format(Date(entry.triggerAt))}")
                            }
                            IconButton(onClick = {
                                AlarmScheduler.cancel(context, entry)
                                val newData = data.copy(
                                    scheduled = data.scheduled.filterNot { it.id == entry.id }
                                )
                                androidx.lifecycle.lifecycleScope.launch {
                                    Store(context).save(newData)
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "刪除")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val test = AlarmEntry(
                    id = System.currentTimeMillis(),
                    triggerAt = System.currentTimeMillis() + 10_000,
                    label = "測試鬧鐘",
                    type = "test"
                )
                AlarmScheduler.schedule(context, test)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("測試鬧鐘（10秒後）")
        }
    }
}