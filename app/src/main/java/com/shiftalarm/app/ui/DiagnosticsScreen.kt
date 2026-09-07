package com.shiftalarm.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shiftalarm.app.calendar.CalendarReader
import com.shiftalarm.app.calendar.CalInfo
import com.shiftalarm.app.calendar.EventPreview
import com.shiftalarm.app.data.AppData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(data: AppData) {
    val context = LocalContext.current
    var calendars by remember { mutableStateOf<List<CalInfo>>(emptyList()) }
    var counts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var previews by remember { mutableStateOf<List<EventPreview>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching {
            calendars = CalendarReader.listCalendars(context)
            val now = System.currentTimeMillis()
            val from = now - 12L * 3600_000L
            val to = now + data.settings.lookaheadDays.toLong() * 86400_000L
            counts = CalendarReader.countEventsPerCalendar(context, from, to)
            previews = CalendarReader.previewEvents(context, from, to, 200)
        }
        loading = false
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dayFmt = remember { SimpleDateFormat("M月d日 (E)", Locale.TRADITIONAL_CHINESE) }
    val logTimeFmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val calNames = calendars.associate { it.id to it.name }
    val byDay = previews.groupBy { dayFmt.format(Date(it.begin)) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("診斷 / 日程", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        item { Text("顯示 App 實際讀到嘅日曆數據——呢度有嘅嘢，先會被用嚟排鬧鐘。", fontSize = 13.sp) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("權限狀態", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    val hasCalendar = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_CALENDAR
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasNotif = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    Text(
                        if (hasCalendar) "✓ 日曆權限已授予" else "✗ 日曆權限未授予（去系統設定開）",
                        fontSize = 13.sp,
                        color = if (hasCalendar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        if (hasNotif) "✓ 通知權限已授予" else "✗ 通知權限未授予",
                        fontSize = 13.sp,
                        color = if (hasNotif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item { Text("各日曆：過去12小時至未來 " + data.settings.lookaheadDays + " 日嘅事件數", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        if (loading) {
            item { Text("載入中…", fontSize = 13.sp) }
        }
        items(calendars) { cal ->
            val n = counts[cal.id] ?: 0
            val selected = data.settings.calendarIds.isEmpty() || data.settings.calendarIds.contains(cal.id)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            cal.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (n > 0) n.toString() + " 個事件" else "0 個事件",
                            fontSize = 14.sp,
                            color = if (n > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Text(cal.account, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Text(
                        if (selected) "同步來源：✓ 包含" else "同步來源：✗ 已排除（設定頁嘅日曆來源）",
                        fontSize = 12.sp
                    )
                }
            }
        }
        if (calendars.isEmpty() && !loading) {
            item {
                Text(
                    "搵唔到任何日曆——請檢查權限同 Google Calendar 同步。",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.error
                )
            }
        }

        item { Spacer(Modifier.height(6.dp)) }
        item { Text("日程預覽（按日分組，唔理邊個日曆）", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        if (previews.isEmpty() && !loading) {
            item {
                Text(
                    "呢段時間內完全冇事件——① 更期唔喺呢個時間範圍（過咗或太遠）？② 手機 Google Calendar 未同步（開 Google Calendar App 拉落嚟刷新）？",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.error
                )
            }
        }
        byDay.forEach { (day, events) ->
            item {
                Spacer(Modifier.height(4.dp))
                Text(day, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            items(events) { ev ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                ev.title.ifEmpty { "（無標題）" },
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(timeFmt.format(Date(ev.begin)), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            (calNames[ev.calendarId] ?: ("日曆 #" + ev.calendarId)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(6.dp)) }
        item { Text("同步記錄（最近 10 次）", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        if (data.syncLogs.isEmpty()) {
            item { Text("（暫無記錄——返首頁撳一次「立即同步」）", fontSize = 13.sp) }
        }
        items(data.syncLogs) { log ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(logTimeFmt.format(Date(log.time)), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Text(log.text, fontSize = 13.sp)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
