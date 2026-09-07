package com.shiftalarm.app.core

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shiftalarm.app.calendar.CalendarReader
import com.shiftalarm.app.data.AlarmEntry
import com.shiftalarm.app.data.Store
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
        val errors = mutableListOf<String>()
        var matchedEvents = 0
        var offDays = 0
        var eventsRead = 0

        val workEntries = mutableListOf<AlarmEntry>()
        if (data.profiles.isNotEmpty()) {
            try {
                val from = now - TimeUnit.HOURS.toMillis(12)
                val to = now + TimeUnit.DAYS.toMillis(data.settings.lookaheadDays.toLong())
                val events = CalendarReader.queryEvents(context, from, to, data.settings)
                eventsRead = events.size
                for (ev in events) {
                    val profile = RuleEngine.matchProfile(ev, data.profiles) ?: continue
                    if (dismissed.containsKey(ev.instanceId)) continue
                    if (RuleEngine.isOffDay(ev, profile)) {
                        offDays++
                        continue
                    }
                    val alarms = RuleEngine.buildWorkAlarms(ev, profile, data.settings)
                        .filter { it.triggerAt > now }
                    if (alarms.isNotEmpty()) matchedEvents++
                    workEntries += alarms
                }
            } catch (e: Exception) {
                errors += "\u8b80\u53d6\u65e5\u66c6\u5931\u6557\uff1a" + (e.message ?: e.toString())
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

        val newData = data.copy(scheduled = all, dismissedGroups = dismissed)
        store.save(newData)
        SyncResult(all.size, matchedEvents, offDays, eventsRead, errors, all.firstOrNull())
    }

    fun schedulePeriodicSync(context: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "shift_sync", ExistingPeriodicWorkPolicy.KEEP, req
        )
    }
}
