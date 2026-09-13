@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.shiftalarm.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import com.shiftalarm.app.calendar.CalendarReader
import com.shiftalarm.app.calendar.CalInfo
import com.shiftalarm.app.core.L10n
import com.shiftalarm.app.core.Monetization
import com.shiftalarm.app.core.RuleEngine
import com.shiftalarm.app.core.t
import com.shiftalarm.app.data.AppData
import com.shiftalarm.app.data.NormalAlarm
import com.shiftalarm.app.data.ShiftConfig
import com.shiftalarm.app.data.WorkProfile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            Text(
                t("Work Profiles", "工作地點設定檔"),
                fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Text(
                t(
                    "Event title contains the location keyword → profile matched; the shift code letter in the title picks the shift type; off-day keyword → no alarm.",
                    "事件標題含「地點關鍵字」→ 命中設定檔；再按標題嘅「更份代號」決定更種；標題含「休息日關鍵字」→ 唔排鬧鐘。"
                ),
                fontSize = 13.sp
            )
        }
        if (data.profiles.isEmpty()) {
            item { Text(t("(No profiles yet — tap below to add one)", "（未有設定檔，撳下面新增）"), fontSize = 14.sp) }
        }
        items(data.profiles) { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().clickable { editing = p }.padding(16.dp)
                ) {
                    Text(
                        p.name.ifEmpty { t("(Unnamed)", "（未命名）") },
                        fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        t("Keywords: ", "地點關鍵字：") + p.keywords.ifEmpty { t("(none)", "（未設）") } +
                            t("｜Off: ", "｜休息日：") + p.offKeyword.ifEmpty { t("(none)", "（無）") },
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    p.shifts.forEach { s ->
                        Text(
                            s.name + "（" + s.keyword.ifEmpty { t("no code", "無代號") } + "）" + s.wakeTime +
                                t(" wake·backup ", " 起·後備 ") + s.alarmCount + t("·every ", " 粒·每 ") + s.intervalMin + t(" min", " 分"),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        item {
            Button(onClick = { editing = WorkProfile() }, Modifier.fillMaxWidth()) {
                Text(t("＋ Add work profile", "＋ 新增地點設定檔"))
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
        item {
            Text(
                if (isNew) t("Add work profile", "新增地點設定檔")
                else t("Edit work profile", "編輯地點設定檔"),
                fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("Location name (e.g. CMC)", "地點名稱（例：CMC）")) }
            )
        }
        item {
            OutlinedTextField(
                value = keywords,
                onValueChange = { keywords = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("Location keywords (comma-separated, matched against event location + title, e.g. cmc)", "地點關鍵字（逗號分隔，比對事件地點＋標題，例：cmc）")) }
            )
        }
        item {
            OutlinedTextField(
                value = offKeyword,
                onValueChange = { offKeyword = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("Off-day keyword (title containing this = no alarm, e.g. off)", "休息日關鍵字（標題含此字＝唔排鬧鐘，例：off）")) }
            )
        }
        item {
            Text(
                t("Shift types (code + wake time each)", "更種設定（各自代號＋起身時間）"),
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
        }
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
                onClick = { shifts = shifts + ShiftConfig(name = t("New shift", "新更種")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(t("＋ Add shift type", "＋ 新增更種"))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    onDone(WorkProfile(initial.id, name, keywords, offKeyword, shifts))
                }) { Text(t("Save", "儲存")) }
                OutlinedButton(onClick = { onDone(null) }) { Text(t("Cancel", "取消")) }
                if (!isNew) {
                    TextButton(onClick = onDelete) {
                        Text(t("Delete", "刪除"), color = MaterialTheme.colorScheme.error)
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
                label = { Text(t("Shift name (e.g. Early)", "更種名稱（例：早更）")) }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = cfg.keyword,
                onValueChange = { onChange(cfg.copy(keyword = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("Shift code (title containing this = this shift, e.g. a)", "更份代號（事件標題含此字＝用呢個更，例：a）")) }
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Wake time", "起身時間"), Modifier.weight(1f))
                OutlinedButton(onClick = { showPicker = true }) { Text(cfg.wakeTime) }
            }
            Spacer(Modifier.height(8.dp))
            Stepper(t("Backup alarms (anti-snooze)", "後備鬧鐘數（防貪睡）"), cfg.alarmCount, 0, 9) { onChange(cfg.copy(alarmCount = it)) }
            Stepper(t("Backup interval (minutes)", "後備間隔（分鐘）"), cfg.intervalMin, 1, 30) { onChange(cfg.copy(intervalMin = it)) }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDelete) {
                Text(t("Delete this shift", "刪除此更"), color = MaterialTheme.colorScheme.error)
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
                }) { Text(t("OK", "確定")) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(t("Cancel", "取消")) }
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
    val zh = mapOf(
        Calendar.SUNDAY to "日", Calendar.MONDAY to "一", Calendar.TUESDAY to "二",
        Calendar.WEDNESDAY to "三", Calendar.THURSDAY to "四", Calendar.FRIDAY to "五",
        Calendar.SATURDAY to "六"
    )
    val en = mapOf(
        Calendar.SUNDAY to "Sun", Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue",
        Calendar.WEDNESDAY to "Wed", Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri",
        Calendar.SATURDAY to "Sat"
    )
    return if (L10n.lang == "zh") "週" + (zh[dow] ?: "") else (en[dow] ?: "")
}

@Composable
fun NormalAlarmsScreen(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(t("Alarms", "一般鬧鐘"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            t(
                "Custom alarms independent of the roster — one-off or weekly repeating.",
                "獨立於更期嘅自訂鬧鐘，可設一次性或每週重複。"
            ),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(data.normalAlarms) { na ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("%02d:%02d".format(na.hour, na.minute), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(na.label.ifEmpty { t("Alarm", "鬧鐘") }, fontSize = 14.sp)
                            Text(
                                if (na.days.isEmpty()) t("One-off", "一次性")
                                else t("Repeat: ", "重複：") + na.days.sortedBy { it }.map { dayName(it) }.joinToString("、"),
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
                            Icon(
                                Icons.Filled.Delete,
                                t("Delete", "刪除"),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { showAdd = true }, Modifier.fillMaxWidth()) { Text(t("＋ Add alarm", "＋ 新增鬧鐘")) }
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
        title = { Text(t("Add alarm", "新增一般鬧鐘")) },
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
            }) { Text(t("OK", "確定")) }
        },
        dismissButton = {
            TextButton(onClick = { onDone(null) }) { Text(t("Cancel", "取消")) }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimePicker(state = state)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("Label", "標籤")) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    t(
                        "Repeat (none = one-off; scroll sideways for all 7 days)",
                        "重複（唔揀＝一次性，可左右滑動看全部七日）"
                    ),
                    fontSize = 12.sp
                )
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
    var showTutorial by remember { mutableStateOf(false) }
    var showDeviceCals by remember { mutableStateOf(false) }
    if (showTutorial) {
        TutorialScreen(onFinished = { showTutorial = false })
        return
    }
    if (showDeviceCals) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { showDeviceCals = false }) { Text(t("← Back to settings", "← 返回設定")) }
            DeviceCalendarsScreen(data, persistThenSync)
        }
        return
    }
    if (showDiagnostics) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { showDiagnostics = false }) { Text(t("← Back to settings", "← 返回設定")) }
            DiagnosticsScreen(data, persistThenSync)
        }
        return
    }
    // Deleted alarms whose original fire time has passed are hidden —
    // e.g. a deleted 09:00 alarm disappears from this list at 09:01.
    // Legacy entries without a recorded time are still shown.
    val deletedList = data.dismissedAlarmMeta.entries.toList()
        .filter { (data.dismissedAlarmTimes[it.key] ?: Long.MAX_VALUE) > now }
    val deletedDateFmt = remember(L10n.lang) { L10n.newShortDateFmt() }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text(t("Settings", "設定"), fontSize = 22.sp, fontWeight = FontWeight.Bold) }

        item { Text(t("Language", "語言"), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en" to "English", "zh" to "中文").forEach { (value, label) ->
                    if (s.language == value) {
                        Button(onClick = {
                            L10n.lang = value
                            persistThenSync { d -> d.copy(settings = d.settings.copy(language = value)) }
                        }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = {
                            L10n.lang = value
                            persistThenSync { d -> d.copy(settings = d.settings.copy(language = value)) }
                        }) { Text(label) }
                    }
                }
            }
        }

        item { Text(t("Appearance", "外觀"), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        item {
            val options = listOf(
                "system" to t("Follow system", "跟隨系統"),
                "light" to t("Light", "淺色"),
                "dark" to t("Dark", "深色")
            )
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

        item {
            Text(
                t("iCal URL (roster source)", "iCal 網址（更期來源）"),
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
        }
        item {
            var icalUrl by remember(s.icalUrl) { mutableStateOf(s.icalUrl) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t(
                        "Paste your Google Calendar secret iCal address (Settings → Import & export → Secret address). The app fetches it every hour. Priority: imported .ics file → device calendars → iCal URL. Shifts entered on the Calendar page always count.",
                        "貼上 Google Calendar 的「私人 iCal 網址」（齒輪設定 → 匯入和匯出／整合日曆 → 私人網址）。App 每小時自動抓取一次。優先次序：匯入嘅 .ics 檔案 → 裝置日曆 → iCal 網址。「日曆」分頁手動填嘅更一定會計。"
                    ),
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = icalUrl,
                    onValueChange = { icalUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("Secret iCal address", "iCal 私人網址")) }
                )
                Button(onClick = {
                    persistThenSync { d -> d.copy(settings = d.settings.copy(icalUrl = icalUrl.trim())) }
                }) { Text(t("Save & sync now", "儲存並立即同步")) }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t("Device calendars (roster source)", "裝置日曆（更期來源）"),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    t(
                        "Read shifts straight from the calendar apps on this phone (Google Calendar, Samsung Calendar, …). Pick which calendars to use. Used only when no .ics file is imported.",
                        "直接讀取手機上日曆 app（Google 日曆、Samsung 日曆等）嘅更期，揀選用邊個日曆。只喺冇匯入 .ics 檔案時先會用。"
                    ),
                    fontSize = 12.sp
                )
                OutlinedButton(
                    onClick = { showDeviceCals = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (s.deviceCalendarIds.isEmpty()) t("Select calendars", "揀選日曆")
                        else t("Select calendars (", "揀選日曆（已選 ") + s.deviceCalendarIds.size + t(" selected)", " 個）")
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField(t("Snooze interval (minutes)", "貪睡間隔（分鐘）"), s.snoozeMinutes) { v -> persistThenSync { d -> d.copy(settings = d.settings.copy(snoozeMinutes = v)) } }
                IntField(t("Days to schedule (min 7)", "預排日數（日，最少 7）"), s.lookaheadDays) { v -> persistThenSync { d -> d.copy(settings = d.settings.copy(lookaheadDays = v.coerceAtLeast(7))) } }
            }
        }

        item {
            Text(
                t("Region alarm (for travel)", "地區鬧鐘（旅遊用）"),
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
        }
        item {
            var showTzPicker by remember { mutableStateOf(false) }
            val tzLabel = if (s.alarmTimezone.isBlank()) t("Follow device", "跟隨裝置") else zoneLabel(s.alarmTimezone)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t(
                        "Normal alarms ring at the selected region's local time. Pick Hong Kong and your 07:00 alarm still rings at 07:00 Hong Kong time while you're in Tokyo. Roster alarms follow the device timezone.",
                        "一般鬧鐘會喺選定地區嘅當地時間響。例如揀咗香港，去到東京旅行，07:00 鬧鐘照樣喺香港時間 07:00 響。更期鬧鐘則跟裝置時區。"
                    ),
                    fontSize = 12.sp
                )
                OutlinedButton(
                    onClick = { showTzPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t("Alarm timezone: ", "鬧鐘時區：") + tzLabel) }
            }
            if (showTzPicker) {
                var tzQuery by remember { mutableStateOf("") }
                val candidates =
                    (listOf("" to t("Follow device", "跟隨裝置")) +
                        WORLD_CITIES.map { it.zone to cityLabel(it) })
                        .filter { (zone, label) ->
                            zone.isEmpty() ||
                                label.contains(tzQuery, ignoreCase = true) ||
                                zone.contains(tzQuery, ignoreCase = true)
                        }
                AlertDialog(
                    onDismissRequest = { showTzPicker = false },
                    title = { Text(t("Pick alarm timezone", "揀鬧鐘時區")) },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = tzQuery,
                                onValueChange = { tzQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(t("Search city / country", "搜尋城市／國家")) }
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(Modifier.height(340.dp)) {
                                items(candidates, key = { it.first + it.second }) { (zone, label) ->
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
                        TextButton(onClick = { showTzPicker = false }) { Text(t("Close", "關閉")) }
                    }
                )
            }
        }

        // Monetization section — hidden while ads/purchases are disabled
        // globally (Monetization.ENABLED = false keeps the code intact).
        if (Monetization.ENABLED) {
            item { Text(t("Ads", "廣告"), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            item {
                if (s.adsRemoved) {
                    Text(t("✓ Ads removed — thank you!", "✓ 已移除廣告，多謝支持！"), fontSize = 14.sp)
                } else {
                    var purchaseMsg by remember { mutableStateOf<String?>(null) }
                    var adUnit by remember(s.adUnitId) { mutableStateOf(s.adUnitId) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            t(
                                "The ad banner only shows at the top — never full-page. A one-time purchase removes it forever.",
                                "廣告條只會顯示喺頂部，唔會彈出全頁廣告。一次性購買即可永久移除廣告。"
                            ),
                            fontSize = 12.sp
                        )
                        Button(onClick = {
                            val activity = context as? android.app.Activity
                            if (activity == null) {
                                purchaseMsg = t("Cannot start purchase", "無法啟動購買流程")
                                return@Button
                            }
                            com.shiftalarm.app.core.AdsBilling.purchase(activity) { ok, msg ->
                                purchaseMsg = msg
                                if (ok) persistThenSync { d ->
                                    d.copy(settings = d.settings.copy(adsRemoved = true))
                                }
                            }
                        }) { Text(t("Remove ads (one-time purchase)", "移除廣告（一次性購買）")) }
                        purchaseMsg?.let { Text(it, fontSize = 12.sp) }
                        OutlinedTextField(
                            value = adUnit,
                            onValueChange = { adUnit = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(t("AdMob ad unit id (optional, blank = test ads)", "AdMob 廣告單元 ID（選填，留空＝測試廣告）")) }
                        )
                        OutlinedButton(onClick = {
                            persistThenSync { d ->
                                d.copy(settings = d.settings.copy(adUnitId = adUnit.trim()))
                            }
                        }) { Text(t("Save ad unit id", "儲存廣告單元 ID")) }
                    }
                }
            }
        }

        item {
            Text(
                t("Deleted alarms", "已刪除鬧鐘管理"),
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
        }
        item {
            Text(
                t(
                    "Tap Restore to re-schedule a deleted alarm immediately (if its time hasn't passed). Deleting/dismissing only affects that single alarm. Deleted alarms whose original time has passed disappear from this list automatically.",
                    "撳「還原」會即刻重新排嗰粒鬧鐘（如果時間仲未過）。每次刪除／解除只會影響嗰一粒鬧鐘，同日其他鬧鐘唔會受影響。過咗原定時間嘅已刪鬧鐘會自動從呢度消失。"
                ),
                fontSize = 12.sp
            )
        }
        if (deletedList.isEmpty()) {
            item { Text(t("No deleted roster alarms.", "暫時未有已刪除嘅更期鬧鐘。"), fontSize = 12.sp) }
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
                    }) { Text(t("Restore", "還原")) }
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
                ) { Text(t("Restore all deleted roster alarms", "還原全部已刪除嘅更期鬧鐘")) }
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
                }) { Text(t("Exempt battery optimization (strongly recommended)", "豁免電池優化（強烈建議）")) }

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
                    }) { Text(t("Allow exact alarms", "允許精確鬧鐘")) }
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
                        }) { Text(t("Allow full-screen alarm notifications", "允許全螢幕鬧鐘通知")) }
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
                    }) { Text(t("Allow Do-Not-Disturb override", "允許勿擾模式繞過")) }
                }

                // Diagnostics moved here from the bottom nav bar.
                OutlinedButton(
                    onClick = { showDiagnostics = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t("Diagnostics / calendar sync data", "診斷／日曆同步資料")) }

                // In-app tutorial, reopenable anytime.
                OutlinedButton(
                    onClick = { showTutorial = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t("Tutorial (how to use)", "教學（使用方法）")) }
            }
        }
        item {
            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrDefault("?")
            Text(
                t("Version ", "版本 ") + version,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

// ---------- 裝置日曆選擇 ----------

/** Pick which device calendars (CalendarProvider) hold the user's roster. */
@Composable
fun DeviceCalendarsScreen(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasPerm by remember {
        mutableStateOf(CalendarReader.hasPermission(context))
    }
    var cals by remember { mutableStateOf<List<CalInfo>>(emptyList()) }
    var selected by remember(data.settings.deviceCalendarIds) {
        mutableStateOf(data.settings.deviceCalendarIds.toMutableSet())
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPerm = granted }

    LaunchedEffect(hasPerm) {
        if (hasPerm) {
            cals = withContext(Dispatchers.IO) { CalendarReader.listCalendars(context) }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            t("Device calendars", "裝置日曆"),
            fontSize = 22.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        if (!hasPerm) {
            Text(
                t(
                    "To read shifts from the calendar apps on this phone, allow calendar access first. The app only reads calendars you pick below.",
                    "要讀取手機日曆 app 嘅更期，請先允許日曆存取權限。App 只會讀取下面你揀嘅日曆。"
                ),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { permLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                Text(t("Allow calendar access", "允許日曆存取"))
            }
        } else {
            Text(
                t(
                    "Tick the calendars that hold your roster. Event titles still need to match your work profile keywords (e.g. \"cmc a\").",
                    "剔選載有你更期嘅日曆。事件標題仍要符合地點設定檔嘅關鍵字（例：cmc a）。"
                ),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(cals) { cal ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = if (cal.id in selected) (selected - cal.id).toMutableSet()
                            else (selected + cal.id).toMutableSet()
                        },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = cal.id in selected,
                            onCheckedChange = {
                                selected = if (it) (selected + cal.id).toMutableSet()
                                else (selected - cal.id).toMutableSet()
                            }
                        )
                        Column {
                            Text(cal.name.ifBlank { "(" + cal.id + ")" }, fontSize = 15.sp)
                            Text(
                                cal.account, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (cals.isEmpty()) {
                    item {
                        Text(
                            t("No calendars found on this device.", "呢部裝置搵唔到任何日曆。"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    persistThenSync { d ->
                        d.copy(settings = d.settings.copy(deviceCalendarIds = selected.toSet()))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(t("Save & sync now", "儲存並立即同步")) }
            if (selected.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        selected = mutableSetOf()
                        persistThenSync { d ->
                            d.copy(settings = d.settings.copy(deviceCalendarIds = emptySet()))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t("Stop using device calendars", "停用裝置日曆")) }
            }
        }
    }
}
