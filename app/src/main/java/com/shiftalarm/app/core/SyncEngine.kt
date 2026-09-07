package com.shiftalarm.app.core

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shiftalarm.app.calendar.CalEvent
import com.shiftalarm.app.calendar.IcalSource
import com.shiftalarm.app.data.AlarmEntry
import com.shiftalarm.app.data.Store
import com.shiftalarm.app.data.SyncLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class SyncResult(
    val total: Int,
    val matchedEvents: Int,
    val offDays: Int,
    val eventsRead: Int,
    val errors: List<String>,
    val next: AlarmEntry?
)

object SyncEngine {

    suspend fun sync(context: Context): SyncResult = withContext(Dispatchers.IO) {
        val store = Store(context)
        val data = store.data.first()
        val now = System.currentTimeMillis()
        val dismissed = data.dismissedGroups
            .filterValues { now - it < TimeUnit.HOURS.toMillis(48) }
        val dismissedIds = data.dismissedAlarmIds
        val errors = mutableListOf<String>()
        var matchedEvents = 0
        var offDays = 0
        var eventsRead = 0

        val workEntries = mutableListOf<AlarmEntry>()
        if (data.profiles.isNotEmpty()) {
            val from = now - TimeUnit.HOURS.toMillis(12)
            val to = now + TimeUnit.DAYS.toMillis(data.settings.lookaheadDays.toLong())
            val events: List<CalEvent> = when {
                data.icalEvents.isNotEmpty() ->
                    data.icalEvents.filter { it.begin >= from && it.begin <= to }
                data.settings.icalUrl.isNotBlank() -> {
                    try {
                        IcalSource.fetchEventsFromUrl(data.settings.icalUrl, from, to)
                    } catch (e: Exception) {
                        errors += "iCal \u6293\u53d6\u5931\u6557\uff1a" + (e.message ?: e.toString())
                        emptyList()
                    }
                }
                else -> emptyList()
            }
            eventsRead = events.size
            for (ev in events) {
                val profile = RuleEngine.matchProfile(ev, data.profiles) ?: continue
                if (dismissed.containsKey(ev.instanceId)) continue
                if (RuleEngine.isOffDay(ev, profile)) {
                    offDays++
                    continue
                }
                val alarms = RuleEngine.buildWorkAlarms(ev, profile, data.settings)
                    .filter { it.triggerAt > now && it.id !in dismissedIds }
                if (alarms.isNotEmpty()) matchedEvents++
                workEntries += alarms
            }
        }

        val normalEntries = mutableListOf<AlarmEntry>()
        for (na in data.normalAlarms.filter { it.enabled }) {
            val baseId = 200_000_000L + (na.id % 10_000_000L) * 10L
            for (d in 0..data.settings.lookaheadDays) {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, na.hour)
                cal.set(Calendar.MINUTE, na.minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (d > 0) cal.add(Calendar.DAY_OF_YEAR, d)
                val t = cal.timeInMillis
                if (t <= now) continue
                if (na.days.isEmpty()) {
                    normalEntries += AlarmEntry(
                        baseId + d.coerceAtMost(9), na.id, t,
                        na.label.ifEmpty { "\u4e00\u822c\u9b27\u9418" }, "normal"
                    )
                    break
                } else if (na.days.contains(cal.get(Calendar.DAY_OF_WEEK))) {
                    normalEntries += AlarmEntry(
                        baseId + d.coerceAtMost(9), na.id, t,
                        na.label.ifEmpty { "\u4e00\u822c\u9b27\u9418" }, "normal"
                    )
                }
            }
        }

        val snoozes = data.scheduled.filter { it.isSnooze && it.triggerAt > now }
        val tests = data.scheduled.filter { it.kind == "test" && it.triggerAt > now }

        val all = (workEntries + normalEntries + snoozes + tests).sortedBy { it.triggerAt }

        for (old in data.scheduled) AlarmScheduler.cancel(context, old)
        for (e in all) AlarmScheduler.schedule(context, e)

        val logText = when {
            errors.isNotEmpty() -> "\u5931\u6557\uff1a" + errors.joinToString("\uff1b")
            all.isEmpty() -> "\u6392\u5514\u5230\u9b27\u9418\uff08\u8b80\u5230 " + eventsRead + " \u500b\u4e8b\u4ef6\u3001\u547d\u4e2d " + matchedEvents + " \u500b\u66f4\u3001\u4f11\u606f\u65e5 " + offDays + "\uff09"
            else -> "\u6392\u5497 " + all.size + " \u7c92\u9b27\u9418\uff08\u8b80\u5230 " + eventsRead + " \u500b\u4e8b\u4ef6\u3001\u547d\u4e2d " + matchedEvents + " \u500b\u66f4\u3001\u4f11\u606f\u65e5 " + offDays + "\uff09"
        }
        val newLogs = (listOf(SyncLog(System.currentTimeMillis(), logText)) + data.syncLogs).take(10)

        val newData = data.copy(scheduled = all, dismissedGroups = dismissed, syncLogs = newLogs)
        store.save(newData)
        SyncResult(all.size, matchedEvents, offDays, eventsRead, errors, all.firstOrNull())
    }

    fun schedulePeriodicSync(context: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "shift_sync", ExistingPeriodicWorkPolicy.KEEP, req
        )
    }
}
