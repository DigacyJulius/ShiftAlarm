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
import java.util.Calendar

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
                    "貼上 Google Calendar 的「私人 iCal 網址」（齒輪設定 → 匯入和匯出／整合日曆 → 私人網址）。App 每小時自動抓取一次。若已在「診斷」分頁匯入過 .ics 檔案，檔案優先。",
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
            Text(
                "更時段界線（僅用於冇更份代號命中時嘅後備判斷，24小時制）",
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField("早更開始（時）", s.morningStart) { v -> persistThenSync { d -> d.copy(settings = d.settings.copy(morningStart = v)) } }
                IntField("早更結束（時）", s.morningEnd) { v -> persistThenSync { d -> d.copy(settings = d.settings.copy(morningEnd = v)) } }
                IntField("午更開始（時）", s.afternoonStart) { v -> persistThenSync { d -> d.copy(settings = d.settings.copy(afternoonStart = v)) } }
                IntField("午更結束（時）", s.afternoonEnd) { v -> persistThenSync { d -> d.copy(settings = d.settings.copy(afternoonEnd = v)) } }
                IntField("晚更開始（時）", s.nightStart) { v -> persistThenSync { d -> d.copy(settings = d.settings.copy(nightStart = v)) } }
                IntField("晚更結束（時）", s.nightEnd) { v -> persistThenSync { d -> d.copy(settings = d.settings.copy(nightEnd = v)) } }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField("貪睡間隔（分鐘）", s.snoozeMinutes) { v -> persistThenSync { d -> d.copy(settings = d.settings.copy(snoozeMinutes = v)) } }
                IntField("預排日數（日）", s.lookaheadDays) { v -> persistThenSync { d -> d.copy(settings = d.settings.copy(lookaheadDays = v)) } }
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
