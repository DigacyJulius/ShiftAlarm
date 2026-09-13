@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.shiftalarm.app.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftalarm.app.core.RuleEngine
import com.shiftalarm.app.data.AppData
import com.shiftalarm.app.data.NormalAlarm
import com.shiftalarm.app.data.ShiftConfig
import com.shiftalarm.app.data.WorkProfile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- 地點設定檔 ----------

@Composable
fun ProfilesScreen(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    var editing by remember { mutableStateOf<WorkProfile?>(null) }
    val current = editing
    if (current != null) {
        val isNew = data.profiles.none { it.id == current.id }
        ProfileEditScreen(
            initial = current,
            isNew = isNew,
            onDone = { updated ->
                if (updated != null) {
                    persistThenSync { d ->
                        d.copy(profiles = (d.profiles.filterNot { it.id == updated.id } + updated).sortedBy { it.name })
                    }
                }
                editing = null
            },
            onDelete = {
                persistThenSync { d -> d.copy(profiles = d.profiles.filterNot { it.id == current.id }) }
                editing = null
            }
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("工作地點設定檔", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "事件標題含「地點關鍵字」→ 命中設定檔；再按標題嘅「更份代號」決定更種；標題含「休息日關鍵字」→ 唔排鬧鐘。",
                fontSize = 13.sp
            )
        }
        if (data.profiles.isEmpty()) {
            item { Text("（未有設定檔，撳下面新增）", fontSize = 14.sp) }
        }
        items(data.profiles) { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().clickable { editing = p }.padding(16.dp)
                ) {
                    Text(p.name.ifEmpty { "（未命名）" }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "地點關鍵字：" + p.keywords.ifEmpty { "（未設）" } +
                            "｜休息日：" + p.offKeyword.ifEmpty { "（無）" },
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    p.shifts.forEach { s ->
                        Text(
                            s.name + "（" + s.keyword.ifEmpty { "無代號" } + "）" + s.wakeTime +
                                " 起·後備 " + s.alarmCount + " 粒·每 " + s.intervalMin + " 分",
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        item {
            Button(onClick = { editing = WorkProfile() }, Modifier.fillMaxWidth()) {
                Text("＋ 新增地點設定檔")
            }
        }
    }
}

@Composable
fun ProfileEditScreen(
    initial: WorkProfile,
    isNew: Boolean,
    onDone: (WorkProfile?) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var keywords by remember { mutableStateOf(initial.keywords) }
    var offKeyword by remember { mutableStateOf(initial.offKeyword) }
    var shifts by remember { mutableStateOf(initial.shifts) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text(if (isNew) "新增地點設定檔" else "編輯地點設定檔", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("地點名稱（例：CMC）") }
            )
        }
        item {
            OutlinedTextField(
                value = keywords,
                onValueChange = { keywords = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("地點關鍵字（逗號分隔，比對事件地點＋標題，例：cmc）") }
            )
        }
        item {
            OutlinedTextField(
                value = offKeyword,
                onValueChange = { offKeyword = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("休息日關鍵字（標題含此字＝唔排鬧鐘，例：off）") }
            )
        }
        item { Text("更種設定（各自代號＋起身時間）", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        itemsIndexed(shifts) { idx, s ->
            ShiftEditor(
                cfg = s,
                onDelete = {
                    if (shifts.size > 1) shifts = shifts.filterIndexed { i, _ -> i != idx }
                },
                onChange = { updated ->
                    shifts = shifts.mapIndexed { i, old -> if (i == idx) updated else old }
                }
            )
        }
        item {
            OutlinedButton(
                onClick = { shifts = shifts + ShiftConfig(name = "新更種") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("＋ 新增更種")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    onDone(WorkProfile(initial.id, name, keywords, offKeyword, shifts))
                }) { Text("儲存") }
                OutlinedButton(onClick = { onDone(null) }) { Text("取消") }
                if (!isNew) {
                    TextButton(onClick = onDelete) {
                        Text("刪除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun ShiftEditor(cfg: ShiftConfig, onDelete: () -> Unit, onChange: (ShiftConfig) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = cfg.name,
                onValueChange = { onChange(cfg.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("更種名稱（例：早更）") }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = cfg.keyword,
                onValueChange = { onChange(cfg.copy(keyword = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("更份代號（事件標題含此字＝用呢個更，例：a）") }
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("起身時間", Modifier.weight(1f))
                OutlinedButton(onClick = { showPicker = true }) { Text(cfg.wakeTime) }
            }
            Spacer(Modifier.height(8.dp))
            Stepper("後備鬧鐘數（防貪睡）", cfg.alarmCount, 0, 9) { onChange(cfg.copy(alarmCount = it)) }
            Stepper("後備間隔（分鐘）", cfg.intervalMin, 1, 30) { onChange(cfg.copy(intervalMin = it)) }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDelete) {
                Text("刪除此更", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (showPicker) {
        val (h, m) = RuleEngine.parseTime(cfg.wakeTime)
        val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(cfg.copy(wakeTime = "%02d:%02d".format(state.hour, state.minute)))
                    showPicker = false
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = state) }
        )
    }
}

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        OutlinedButton(onClick = { if (value > min) onChange(value - 1) }) { Text("−") }
        Text("  $value  ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { if (value < max) onChange(value + 1) }) { Text("＋") }
    }
}

// ---------- 一般鬧鐘 ----------

fun dayName(dow: Int): String {
    val names = mapOf(
        Calendar.SUNDAY to "日", Calendar.MONDAY to "一", Calendar.TUESDAY to "二",
        Calendar.WEDNESDAY to "三", Calendar.THURSDAY to "四", Calendar.FRIDAY to "五",
        Calendar.SATURDAY to "六"
    )
    return "週" + (names[dow] ?: "")
}

@Composable
fun NormalAlarmsScreen(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("一般鬧鐘", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("獨立於更期嘅自訂鬧鐘，可設一次性或每週重複。", fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(data.normalAlarms) { na ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("%02d:%02d".format(na.hour, na.minute), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(na.label.ifEmpty { "鬧鐘" }, fontSize = 14.sp)
                            Text(
                                if (na.days.isEmpty()) "一次性"
                                else "重複：" + na.days.sortedBy { it }.map { dayName(it) }.joinToString("、"),
                                fontSize = 12.sp
                            )
                        }
                        Switch(checked = na.enabled, onCheckedChange = { on ->
                            persistThenSync { d ->
                                d.copy(normalAlarms = d.normalAlarms.map {
                                    if (it.id == na.id) it.copy(enabled = on) else it
                                })
                            }
                        })
                        IconButton(onClick = {
                            persistThenSync { d ->
                                d.copy(normalAlarms = d.normalAlarms.filterNot { it.id == na.id })
                            }
                        }) {
                            Icon(Icons.Filled.Delete, "刪除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { showAdd = true }, Modifier.fillMaxWidth()) { Text("＋ 新增鬧鐘") }
    }
    if (showAdd) {
        AddNormalAlarmDialog { na ->
            showAdd = false
            if (na != null) {
                persistThenSync { d -> d.copy(normalAlarms = d.normalAlarms + na) }
            }
        }
    }
}

@Composable
fun AddNormalAlarmDialog(onDone: (NormalAlarm?) -> Unit) {
    val state = rememberTimePickerState(initialHour = 7, initialMinute = 0, is24Hour = true)
    var label by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(setOf<Int>()) }
    val allDays = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("新增一般鬧鐘") },
        confirmButton = {
            TextButton(onClick = {
                onDone(
                    NormalAlarm(
                        id = System.currentTimeMillis(),
                        hour = state.hour,
                        minute = state.minute,
                        label = label,
                        days = days
                    )
                )
            }) { Text("確定") }
        },
        dismissButton = {
            TextButton(onClick = { onDone(null) }) { Text("取消") }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimePicker(state = state)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("標籤") }
                )
                Spacer(Modifier.height(8.dp))
                Text("重複（唔揀＝一次性，可左右滑動看全部七日）", fontSize = 12.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    allDays.forEach { d ->
                        FilterChip(
                            selected = days.contains(d),
                            onClick = {
                                days = if (days.contains(d)) days - d else days + d
                            },
                            label = { Text(dayName(d)) }
                        )
                    }
                }
            }
        }
    )
}

// ---------- 設定 ----------

@Composable
fun SettingsScreen(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    val context = LocalContext.current
    val s = data.settings
    val now = System.currentTimeMillis()
    // Diagnostics now lives behind a button here instead of occupying a
    // bottom nav tab.
    var showDiagnostics by remember { mutableStateOf(false) }
    if (showDiagnostics) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { showDiagnostics = false }) { Text("← 返回設定") }
            DiagnosticsScreen(data, persistThenSync)
        }
        return
    }
    // Deleted alarms whose original fire time has passed are hidden —
    // e.g. a deleted 09:00 alarm disappears from this list at 09:01.
    // Legacy entries without a recorded time are still shown.
    val deletedList = data.dismissedAlarmMeta.entries.toList()
        .filter { (data.dismissedAlarmTimes[it.key] ?: Long.MAX_VALUE) > now }
    val deletedDateFmt = remember { SimpleDateFormat("M月d日 HH:mm", Locale.TRADITIONAL_CHINESE) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("設定", fontSize = 22.sp, fontWeight = FontWeight.Bold) }

        item { Text("外觀", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        item {
            val options = listOf("system" to "跟隨系統", "light" to "淺色", "dark" to "深色")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, label) ->
                    if (s.darkMode == value) {
                        Button(onClick = {
                            persistThenSync { d -> d.copy(settings = d.settings.copy(darkMode = value)) }
                        }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = {
                            persistThenSync { d -> d.copy(settings = d.settings.copy(darkMode = value)) }
                        }) { Text(label) }
                    }
                }
            }
        }

        item { Text("iCal 網址（更期來源）", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        item {
            var icalUrl by remember(s.icalUrl) { mutableStateOf(s.icalUrl) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "貼上 Google Calendar 的「私人 iCal 網址」（齒輪設定 → 匯入和匯出／整合日曆 → 私人網址）。App 每小時自動抓取一次。若已在「診斷」入面匯入過 .ics 檔案，檔案優先。",
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = icalUrl,
                    onValueChange = { icalUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("iCal 私人網址") }
                )
                Button(onClick = {
                    persistThenSync { d -> d.copy(settings = d.settings.copy(icalUrl = icalUrl.trim())) }
                }) { Text("儲存並立即同步") }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField("貪睡間隔（分鐘）", s.snoozeMinutes) { v -> persistThenSync { d -> d.copy(settings = d.settings.copy(snoozeMinutes = v)) } }
                IntField("預排日數（日，最少 7）", s.lookaheadDays) { v -> persistThenSync { d -> d.copy(settings = d.settings.copy(lookaheadDays = v.coerceAtLeast(7))) } }
            }
        }

        item { Text("地區鬧鐘（旅遊用）", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        item {
            var showTzPicker by remember { mutableStateOf(false) }
            val tzLabel = if (s.alarmTimezone.isBlank()) "跟隨裝置" else zoneLabel(s.alarmTimezone)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "一般鬧鐘會喺選定地區嘅當地時間響。例如揀咗香港，去到東京旅行，07:00 鬧鐘照樣喺香港時間 07:00 響。更期鬧鐘則跟裝置時區。",
                    fontSize = 12.sp
                )
                OutlinedButton(
                    onClick = { showTzPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("鬧鐘時區：" + tzLabel) }
            }
            if (showTzPicker) {
                var tzQuery by remember { mutableStateOf("") }
                val candidates = listOf("跟隨裝置" to "") +
                    WORLD_CITIES.filter {
                        it.first.contains(tzQuery) || it.second.contains(tzQuery, ignoreCase = true)
                    }
                AlertDialog(
                    onDismissRequest = { showTzPicker = false },
                    title = { Text("揀鬧鐘時區") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = tzQuery,
                                onValueChange = { tzQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("搜尋城市") }
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(Modifier.height(320.dp)) {
                                items(candidates, key = { it.first + it.second }) { (label, zone) ->
                                    Text(
                                        label,
                                        fontSize = 15.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                persistThenSync { d ->
                                                    d.copy(settings = d.settings.copy(alarmTimezone = zone))
                                                }
                                                showTzPicker = false
                                            }
                                            .padding(vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showTzPicker = false }) { Text("關閉") }
                    }
                )
            }
        }

        item { Text("廣告", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        item {
            if (s.adsRemoved) {
                Text("✓ 已移除廣告，多謝支持！", fontSize = 14.sp)
            } else {
                var purchaseMsg by remember { mutableStateOf<String?>(null) }
                var adUnit by remember(s.adUnitId) { mutableStateOf(s.adUnitId) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "廣告條只會顯示喺頂部，唔會彈出全頁廣告。一次性購買即可永久移除廣告。",
                        fontSize = 12.sp
                    )
                    Button(onClick = {
                        val activity = context as? android.app.Activity
                        if (activity == null) {
                            purchaseMsg = "無法啟動購買流程"
                            return@Button
                        }
                        com.shiftalarm.app.core.AdsBilling.purchase(activity) { ok, msg ->
                            purchaseMsg = msg
                            if (ok) persistThenSync { d ->
                                d.copy(settings = d.settings.copy(adsRemoved = true))
                            }
                        }
                    }) { Text("移除廣告（一次性購買）") }
                    purchaseMsg?.let { Text(it, fontSize = 12.sp) }
                    OutlinedTextField(
                        value = adUnit,
                        onValueChange = { adUnit = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("AdMob 廣告單元 ID（選填，留空＝測試廣告）") }
                    )
                    OutlinedButton(onClick = {
                        persistThenSync { d ->
                            d.copy(settings = d.settings.copy(adUnitId = adUnit.trim()))
                        }
                    }) { Text("儲存廣告單元 ID") }
                }
            }
        }

        item { Text("已刪除鬧鐘管理", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        item { Text("撳「還原」會即刻重新排嗰粒鬧鐘（如果時間仲未過）。每次刪除／解除只會影響嗰一粒鬧鐘，同日其他鬧鐘唔會受影響。過咗原定時間嘅已刪鬧鐘會自動從呢度消失。", fontSize = 12.sp) }
        if (deletedList.isEmpty()) {
            item { Text("暫時未有已刪除嘅更期鬧鐘。", fontSize = 12.sp) }
        }
        items(deletedList) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prefix the original fire date/time so same-label alarms
                    // on different days can be told apart.
                    val whenText = data.dismissedAlarmTimes[entry.key]
                        ?.let { deletedDateFmt.format(Date(it)) + " " } ?: ""
                    Text(whenText + entry.value, Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = {
                        persistThenSync { d ->
                            d.copy(
                                dismissedAlarmIds = d.dismissedAlarmIds - entry.key,
                                dismissedAlarmMeta = d.dismissedAlarmMeta - entry.key,
                                dismissedAlarmTimes = d.dismissedAlarmTimes - entry.key
                            )
                        }
                    }) { Text("還原") }
                }
            }
        }
        if (data.dismissedAlarmIds.isNotEmpty() || data.dismissedGroups.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = {
                        persistThenSync { d ->
                            d.copy(
                                dismissedAlarmIds = emptySet(),
                                dismissedAlarmMeta = emptyMap(),
                                dismissedAlarmTimes = emptyMap(),
                                dismissedGroups = emptyMap()
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("還原全部已刪除嘅更期鬧鐘") }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                android.net.Uri.parse("package:" + context.packageName)
                            )
                        )
                    }
                }) { Text("豁免電池優化（強烈建議）") }

                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    Button(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    android.net.Uri.parse("package:" + context.packageName)
                                )
                            )
                        }
                    }) { Text("允許精確鬧鐘") }
                }

                if (Build.VERSION.SDK_INT >= 34) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    if (!nm.canUseFullScreenIntent()) {
                        Button(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                        android.net.Uri.parse("package:" + context.packageName)
                                    )
                                )
                            }
                        }) { Text("允許全螢幕鬧鐘通知") }
                    }
                }

                // DND / Notification Policy Access
                val nmPolicy = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (Build.VERSION.SDK_INT >= 23 && !nmPolicy.isNotificationPolicyAccessGranted) {
                    Button(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            )
                        }
                    }) { Text("允許勿擾模式繞過") }
                }

                // Diagnostics moved here from the bottom nav bar.
                OutlinedButton(
                    onClick = { showDiagnostics = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("診斷／日曆同步資料") }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun IntField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            t.toIntOrNull()?.let { onChange(it.coerceIn(0, 48)) }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}
