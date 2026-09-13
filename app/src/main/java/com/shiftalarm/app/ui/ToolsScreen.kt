package com.shiftalarm.app.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftalarm.app.data.AppData
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Curated city list for the world clock picker (label, IANA zone id).
val WORLD_CITIES: List<Pair<String, String>> = listOf(
    "香港" to "Asia/Hong_Kong",
    "台北" to "Asia/Taipei",
    "東京" to "Asia/Tokyo",
    "首爾" to "Asia/Seoul",
    "新加坡" to "Asia/Singapore",
    "吉隆坡" to "Asia/Kuala_Lumpur",
    "曼谷" to "Asia/Bangkok",
    "雅加達" to "Asia/Jakarta",
    "馬尼拉" to "Asia/Manila",
    "上海" to "Asia/Shanghai",
    "北京" to "Asia/Shanghai",
    "澳門" to "Asia/Macau",
    "悉尼" to "Australia/Sydney",
    "墨爾本" to "Australia/Melbourne",
    "奧克蘭" to "Pacific/Auckland",
    "倫敦" to "Europe/London",
    "巴黎" to "Europe/Paris",
    "柏林" to "Europe/Berlin",
    "羅馬" to "Europe/Rome",
    "馬德里" to "Europe/Madrid",
    "阿姆斯特丹" to "Europe/Amsterdam",
    "蘇黎世" to "Europe/Zurich",
    "斯德哥爾摩" to "Europe/Stockholm",
    "莫斯科" to "Europe/Moscow",
    "伊斯坦堡" to "Europe/Istanbul",
    "杜拜" to "Asia/Dubai",
    "多哈" to "Asia/Qatar",
    "利雅得" to "Asia/Riyadh",
    "開羅" to "Africa/Cairo",
    "約翰內斯堡" to "Africa/Johannesburg",
    "紐約" to "America/New_York",
    "多倫多" to "America/Toronto",
    "芝加哥" to "America/Chicago",
    "丹佛" to "America/Denver",
    "洛杉磯" to "America/Los_Angeles",
    "三藩市" to "America/Los_Angeles",
    "溫哥華" to "America/Vancouver",
    "墨西哥城" to "America/Mexico_City",
    "聖保羅" to "America/Sao_Paulo",
    "布宜諾斯艾利斯" to "America/Buenos_Aires",
    "檀香山" to "Pacific/Honolulu"
)

fun zoneLabel(zoneId: String): String =
    WORLD_CITIES.firstOrNull { it.second == zoneId }?.first ?: zoneId

@Composable
fun ToolsScreen(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("世界時鐘") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("碼錶") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("計時") })
        }
        when (tab) {
            0 -> WorldClockView(data, persistThenSync)
            1 -> StopwatchView()
            2 -> TimerView(data, persistThenSync)
        }
    }
}

// ---------- 世界時鐘 ----------

@Composable
fun WorldClockView(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val zones = if (data.worldClocks.isEmpty()) listOf("Asia/Hong_Kong") else data.worldClocks
    var showPicker by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("世界時鐘", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        item {
            Text(
                "加入城市之後，隨時睇到各地時間，去旅行換時區都唔怕。",
                fontSize = 13.sp
            )
        }
        items(zones, key = { it }) { zone ->
            val zoneId = remember(zone) { runCatching { ZoneId.of(zone) }.getOrNull() }
            if (zoneId != null) {
                val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zoneId)
                val localDate = LocalDate.now()
                val dayDiff = time.toLocalDate().compareTo(localDate)
                val dayText = when {
                    dayDiff > 0 -> "（+1日）"
                    dayDiff < 0 -> "（−1日）"
                    else -> ""
                }
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(zoneLabel(zone), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(
                                time.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.TRADITIONAL_CHINESE)) + dayText,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            time.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = {
                            persistThenSync { d -> d.copy(worldClocks = d.worldClocks - zone) }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "刪除")
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("＋ 加入城市") }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showPicker) {
        var query by remember { mutableStateOf("") }
        val candidates = WORLD_CITIES.filter {
            it.first.contains(query) || it.second.contains(query, ignoreCase = true)
        }.filter { it.second !in zones }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("加入城市") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("搜尋城市") }
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.height(320.dp)) {
                        items(candidates, key = { it.second + it.first }) { (label, zone) ->
                            Text(
                                label + "（" + zone + "）",
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        persistThenSync { d ->
                                            d.copy(worldClocks = (d.worldClocks + zone).distinct())
                                        }
                                        showPicker = false
                                    }
                                    .padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("關閉") }
            }
        )
    }
}

// ---------- 碼錶 ----------

@Composable
fun StopwatchView() {
    var running by remember { mutableStateOf(false) }
    // Accumulated elapsed time while paused, and the anchor when running.
    var accumulated by remember { mutableStateOf(0L) }
    var anchor by remember { mutableStateOf(0L) }
    var tick by remember { mutableStateOf(0L) }
    val laps = remember { mutableStateListOf<Long>() }

    LaunchedEffect(running) {
        while (running) {
            tick = SystemClock.elapsedRealtime()
            delay(47)
        }
    }
    val elapsed = accumulated + (if (running) tick - anchor else 0L)
    val cs = elapsed / 10 % 100
    val s = elapsed / 1000 % 60
    val m = elapsed / 60000 % 60
    val h = elapsed / 3600000

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("碼錶", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text(
            String.format(Locale.US, "%02d:%02d:%02d.%02d", h, m, s, cs),
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (running) {
                    accumulated += SystemClock.elapsedRealtime() - anchor
                    running = false
                } else {
                    anchor = SystemClock.elapsedRealtime()
                    tick = anchor
                    running = true
                }
            }) { Text(if (running) "暫停" else if (elapsed > 0) "繼續" else "開始") }
            OutlinedButton(
                onClick = {
                    if (running) laps.add(0, elapsed) else { running = false; accumulated = 0; laps.clear() }
                },
                enabled = running || elapsed > 0
            ) { Text(if (running) "圈數" else "重設") }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(laps.size) { i ->
                val lap = laps[i]
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("第 " + (laps.size - i) + " 圈", Modifier.weight(1f), fontSize = 14.sp)
                    Text(
                        String.format(
                            Locale.US, "%02d:%02d:%02d.%02d",
                            lap / 3600000, lap / 60000 % 60, lap / 1000 % 60, lap / 10 % 100
                        ),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ---------- 計時 ----------

@Composable
fun TimerView(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    var h by remember { mutableIntStateOf(0) }
    var m by remember { mutableIntStateOf(5) }
    var s by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val active = data.timerEndAt > now

    LaunchedEffect(active) {
        while (active && data.timerEndAt > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }

    val remaining = (data.timerEndAt - now).coerceAtLeast(0L)
    val rs = remaining / 1000 % 60
    val rm = remaining / 60000 % 60
    val rh = remaining / 3600000

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("計時器", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (active) {
            Text(
                String.format(Locale.US, "%02d:%02d:%02d", rh, rm, rs),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = {
                com.shiftalarm.app.core.TimerReceiver.cancel(ctx)
                persistThenSync { d -> d.copy(timerEndAt = 0) }
                now = System.currentTimeMillis()
            }) { Text("取消計時") }
            Text(
                "就算閂咗 app，時間到都會響通知。",
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stepper("時", h) { h = (h + it).coerceIn(0, 23) }
                Stepper("分", m) { m = (m + it).coerceIn(0, 59) }
                Stepper("秒", s) { s = (s + it).coerceIn(0, 59) }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val duration = (h * 3600 + m * 60 + s) * 1000L
                    if (duration > 0) {
                        val endAt = System.currentTimeMillis() + duration
                        persistThenSync { d -> d.copy(timerEndAt = endAt) }
                        com.shiftalarm.app.core.TimerReceiver.schedule(ctx, endAt)
                        now = System.currentTimeMillis()
                    }
                },
                enabled = h > 0 || m > 0 || s > 0
            ) { Text("開始計時") }
            Text(
                "計時用系統鬧鐘運行，閂咗 app 都會準時響通知。",
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 13.sp)
        Text(String.format(Locale.US, "%02d", value), fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Row {
            OutlinedButton(onClick = { onChange(+1) }) { Text("＋") }
            OutlinedButton(onClick = { onChange(-1) }) { Text("－") }
        }
    }
}
