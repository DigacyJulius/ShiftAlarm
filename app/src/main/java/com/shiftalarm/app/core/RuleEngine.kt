package com.shiftalarm.app.core

import com.shiftalarm.app.calendar.CalEvent
import com.shiftalarm.app.data.AlarmEntry
import com.shiftalarm.app.data.AppSettings
import com.shiftalarm.app.data.ShiftType
import com.shiftalarm.app.data.WorkProfile
import java.util.Calendar
import java.util.concurrent.TimeUnit

object RuleEngine {

    fun matchProfile(event: CalEvent, profiles: List<WorkProfile>): WorkProfile? {
        val haystack = (event.location + " " + event.title).lowercase()
        return profiles.firstOrNull { p ->
            p.keywords.split(",", "\uff0c")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .any { haystack.contains(it) }
        }
    }

    fun shiftOf(startHour: Int, s: AppSettings): ShiftType {
        val h = if (startHour < 4) startHour + 24 else startHour
        return when {
            h >= s.morningStart && h < s.morningEnd -> ShiftType.MORNING
            h >= s.afternoonStart && h < s.afternoonEnd -> ShiftType.AFTERNOON
            h >= s.nightStart && h < s.nightEnd -> ShiftType.NIGHT
            else -> ShiftType.MORNING
        }
    }

    fun buildWorkAlarms(
        event: CalEvent,
        profile: WorkProfile,
        settings: AppSettings
    ): List<AlarmEntry> {
        val shift = shiftOf(hourOf(event.begin), settings)
        val cfg = when (shift) {
            ShiftType.MORNING -> profile.morning
            ShiftType.AFTERNOON -> profile.afternoon
            ShiftType.NIGHT -> profile.night
        }
        val (h, m) = parseTime(cfg.wakeTime)
        val base = Calendar.getInstance().apply {
            timeInMillis = event.begin
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseId = 100_000_000L + (event.instanceId % 10_000_000L) * 10L
        val result = mutableListOf<AlarmEntry>()
        for (i in 0..cfg.alarmCount.coerceIn(0, 9)) {
            val t = base.timeInMillis + TimeUnit.MINUTES.toMillis(cfg.intervalMin.toLong() * i)
            val suffix = if (i > 0) "\uff08\u5f8c\u5099 $i\uff09" else ""
            result += AlarmEntry(
                id = baseId + i,
                groupId = event.instanceId,
                triggerAt = t,
                label = profile.name + " \u00b7 " + shift.label + suffix,
                kind = "work"
            )
        }
        return result
    }

    fun parseTime(s: String): Pair<Int, Int> {
        val p = s.split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: 5
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        return h.coerceIn(0, 23) to m.coerceIn(0, 59)
    }

    fun hourOf(millis: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        return cal.get(Calendar.HOUR_OF_DAY)
    }
}
