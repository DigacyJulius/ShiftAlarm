package com.shiftalarm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftalarm.app.core.L10n
import com.shiftalarm.app.core.t
import com.shiftalarm.app.data.AppData
import com.shiftalarm.app.data.ShiftEntry
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * In-app shift calendar: for users without a calendar app, tap any day and
 * pick the work profile + shift (or off). Entries feed the alarm engine just
 * like roster events from iCal / device calendars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val today = remember { LocalDate.now() }
    val monthFmt = remember(L10n.lang) {
        DateTimeFormatter.ofPattern("MMMM yyyy", L10n.locale)
    }

    fun entryFor(date: LocalDate, profileId: Long): ShiftEntry? =
        data.manualShifts.firstOrNull { it.date == date.toString() && it.profileId == profileId }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // Month header with navigation.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
            }
            Text(
                month.format(monthFmt),
                modifier = Modifier.weight(1f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
        TextButton(
            onClick = { month = YearMonth.from(today) },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(t("Jump to today", "回到今天")) }
        Spacer(Modifier.height(4.dp))

        // Weekday header.
        Row(Modifier.fillMaxWidth()) {
            // Week starts on Sunday to match dayName().
            val names = listOf(
                dayName(java.util.Calendar.SUNDAY), dayName(java.util.Calendar.MONDAY),
                dayName(java.util.Calendar.TUESDAY), dayName(java.util.Calendar.WEDNESDAY),
                dayName(java.util.Calendar.THURSDAY), dayName(java.util.Calendar.FRIDAY),
                dayName(java.util.Calendar.SATURDAY)
            )
            names.forEach {
                Text(
                    it, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // Day grid.
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value % 7 // Sunday=0
        val daysInMonth = month.lengthOfMonth()
        val cells = List(leading) { null } + (1..daysInMonth).map { month.atDay(it) }
        val weeks = (cells + List((7 - cells.size % 7) % 7) { null }).chunked(7)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            weeks.forEach { week ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { date ->
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(
                                    when (date) {
                                        selected -> MaterialTheme.colorScheme.primaryContainer
                                        today -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    },
                                    CircleShape
                                )
                                .clickable { date?.let { selected = it } }
                        ) {
                            val entries = date?.let { d ->
                                data.manualShifts.filter { it.date == d.toString() }
                            } ?: emptyList()
                            Column(
                                Modifier.fillMaxSize().padding(top = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    date?.dayOfMonth?.toString() ?: "",
                                    fontSize = 14.sp,
                                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal
                                )
                                entries.take(2).forEach { e ->
                                    val p = data.profiles.firstOrNull { it.id == e.profileId }
                                    val code = if (e.off) {
                                        t("off", "休")
                                    } else {
                                        p?.shifts?.firstOrNull { it.name == e.shiftName }?.keyword
                                            ?.ifBlank { e.shiftName.take(4) } ?: e.shiftName.take(4)
                                    }
                                    Text(
                                        code, fontSize = 9.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            t(
                "Tap a day to enter your shift. Entries here always count — even without iCal or device calendars.",
                "撳日期填更期。呢度填嘅更一定會計入鬧鐘——就算冇 iCal 或裝置日曆都用得。"
            ),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Day editor: pick the shift (or off) for each work profile.
    selected?.let { date ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = {
                Text(
                    date.format(DateTimeFormatter.ofPattern(
                        if (L10n.lang == "zh") "M月d日 (E)" else "EEE, MMM d", L10n.locale
                    ))
                )
            },
            text = {
                if (data.profiles.isEmpty()) {
                    Text(
                        t(
                            "No work profiles yet. Create one on the Roster page first (e.g. name CMC), then pick shifts here.",
                            "仲未有地點設定檔。先去「更期」新增（例：名稱 CMC），再返嚟揀更份。"
                        )
                    )
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        data.profiles.forEach { profile ->
                            val entry = entryFor(date, profile.id)
                            Text(
                                profile.name.ifBlank { t("(profile)", "（設定檔）") },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                            ShiftChips(
                                profile = profile,
                                selectedShift = if (entry != null && !entry.off) entry.shiftName else null,
                                selectedOff = entry?.off == true,
                                onPick = { shiftName, off ->
                                    persistThenSync { d ->
                                        val others = d.manualShifts.filterNot {
                                            it.date == date.toString() && it.profileId == profile.id
                                        }
                                        val keep = if (shiftName == null && !off) emptyList()
                                        else listOf(
                                            ShiftEntry(
                                                date = date.toString(),
                                                profileId = profile.id,
                                                shiftName = shiftName ?: "",
                                                off = off
                                            )
                                        )
                                        d.copy(manualShifts = others + keep)
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text(t("Done", "完成")) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShiftChips(
    profile: com.shiftalarm.app.data.WorkProfile,
    selectedShift: String?,
    selectedOff: Boolean,
    onPick: (shiftName: String?, off: Boolean) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        profile.shifts.forEach { shift ->
            val isSelected = shift.name == selectedShift
            FilterChip(
                selected = isSelected,
                onClick = {
                    // Tapping the already-selected chip clears the day.
                    onPick(if (isSelected) null else shift.name, false)
                },
                label = {
                    Text(
                        shift.name + if (shift.keyword.isBlank()) "" else " (${shift.keyword})"
                    )
                }
            )
        }
        FilterChip(
            selected = selectedOff,
            onClick = { onPick(null, !selectedOff) },
            label = { Text(t("Off", "休息")) }
        )
    }
}
