package com.shiftalarm.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Bug
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiftalarm.app.core.AlarmScheduler
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

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncEngine.schedulePeriodicSync(this)
        requestPermissions()
        setContent {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
                AppRoot()
            }
        }
    }

    private fun requestPermissions() {
        val wanted = mutableListOf(Manifest.permission.READ_CALENDAR)
        if (Build.VERSION.SDK_INT >= 33) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { Store(context) }
    val scope = rememberCoroutineScope()
    val data by remember { store.data }.collectAsStateWithLifecycle(initialValue = AppData())
    var tab by remember { mutableStateOf(0) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    fun doSync() {
        scope.launch {
            syncMessage = "同步中…"
            val r = SyncEngine.sync(context)
            syncMessage = when {
                r.errors.isNotEmpty() ->
                    "同步失敗：" + r.errors.joinToString("；")
                data.profiles.isEmpty() ->
                    "同步完成，但排唔到任何鬧鐘：仲未有地點設定檔。去「地點設定檔」→ 新增（例：名稱 CMC、地點關鍵字 cmc），儲存後再撳同步。"
                r.eventsRead == 0 ->
                    "同步完成，但喺日曆讀到 0 個事件。檢查：① Google Calendar App 內睇唔睇到啲更期（要先同步落手機）？② 設定→日曆來源有冇揀錯？③ 更期係咪喺未來 " + data.settings.lookaheadDays + " 日內？（去「診斷」分頁睇詳情）"
                r.matchedEvents == 0 && r.offDays == 0 ->
                    "同步完成：讀到 " + r.eventsRead + " 個事件，但冇一個命中設定檔。檢查事件標題（例：cmc a）同設定檔嘅「地點關鍵字」係咪一致。（去「診斷」分頁睇事件標題）"
                r.total == 0 ->
                    "同步完成：命中 " + r.matchedEvents + " 個更、" + r.offDays + " 個休息日，但全部起身時間已過。"
                else ->
                    "同步完成：命中 " + r.matchedEvents + " 個更、跳過 " + r.offDays + " 個休息日，共排 " + r.total + " 粒鬧鐘"
            }
        }
    }

    LaunchedEffect(Unit) { doSync() }

    fun persistThenSync(transform: (AppData) -> AppData) {
        scope.launch {
            val current = store.data.first()
            store.save(transform(current))
            SyncEngine.sync(context)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, null) },
                    label = { Text("首頁") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Place, null) },
                    label = { Text("地點設定檔") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Alarm, null) },
                    label = { Text("一般鬧鐘") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text("設定") }
                )
                NavigationBarItem(
                    selected = tab == 4,
                    onClick = { tab = 4 },
                    icon = { Icon(Icons.Filled.Bug, null) },
                    label = { Text("診斷") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> HomeScreen(
                    data = data,
                    syncMessage = syncMessage,
                    onSync = { doSync() },
                    onTest = { scope.launch { scheduleTestAlarm(context, store) } }
                )
                1 -> ProfilesScreen(data, persistThenSync = ::persistThenSync)
                2 -> NormalAlarmsScreen(data, persistThenSync = ::persistThenSync)
                3 -> SettingsScreen(data, persistThenSync = ::persistThenSync)
                else -> DiagnosticsScreen(data)
            }
        }
    }
}

suspend fun scheduleTestAlarm(context: Context, store: Store) {
    val data = store.data.first()
    val test = AlarmEntry(
        id = 260_000_000L,
        groupId = 0L,
        triggerAt = System.currentTimeMillis() + 15_000L,
        label = "測試鬧鐘",
        kind = "test"
    )
    val oldTests = data.scheduled.filter { it.kind == "test" }
    oldTests.forEach { AlarmScheduler.cancel(context, it) }
    val kept = data.scheduled.filterNot { it.kind == "test" } + test
    AlarmScheduler.schedule(context, test)
    store.save(data.copy(scheduled = kept))
}

fun relative(from: Long, to: Long): String {
    val diff = (to - from) / 60000L
    val h = diff / 60
    val m = diff % 60
    return if (h > 0) "${h} 小時 ${m} 分鐘" else "${m} 分鐘"
}

@Composable
fun HomeScreen(
    data: AppData,
    syncMessage: String?,
    onSync: () -> Unit,
    onTest: () -> Unit
) {
    val now = System.currentTimeMillis()
    val upcoming = data.scheduled.filter { it.triggerAt > now }.sortedBy { it.triggerAt }
    val next = upcoming.firstOrNull()
    val dayFmt = remember { SimpleDateFormat("M月d日 (E)", Locale.TRADITIONAL_CHINESE) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val byDay = upcoming.groupBy { dayFmt.format(Date(it.triggerAt)) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("下一個鬧鐘", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    if (next != null) {
                        Text(timeFmt.format(Date(next.triggerAt)), fontSize = 54.sp, fontWeight = FontWeight.Bold)
                        Text(next.label, fontSize = 16.sp)
                        Text("將於 " + relative(now, next.triggerAt) + " 後響起", fontSize = 13.sp)
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text("暫時未有排程鬧鐘。\n\n設定「地點設定檔」或「一般鬧鐘」之後，呢度會顯示下一個鬧鐘。")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSync) { Text("立即同步") }
                OutlinedButton(onClick = onTest) { Text("測試鬧鐘（15秒後）") }
            }
        }
        if (syncMessage != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        syncMessage,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        byDay.forEach { (day, entries) ->
            item {
                Spacer(Modifier.height(6.dp))
                Text(day, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(entries) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(timeFmt.format(Date(e.triggerAt)), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(0.dp))
                        Text("　" + e.label, fontSize = 15.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
