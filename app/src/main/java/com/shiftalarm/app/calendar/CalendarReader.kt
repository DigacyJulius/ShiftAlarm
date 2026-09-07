package com.shiftalarm.app.calendar

import android.content.Context
import android.provider.CalendarContract
import com.shiftalarm.app.data.AppSettings

data class CalEvent(
    val instanceId: Long,
    val begin: Long,
    val end: Long,
    val title: String,
    val location: String,
    val calendarId: Long
)

data class CalInfo(
    val id: Long,
    val name: String,
    val account: String
)

data class EventPreview(
    val title: String,
    val begin: Long,
    val calendarId: Long
)

object CalendarReader {

    fun listCalendars(context: Context): List<CalInfo> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        val result = mutableListOf<CalInfo>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection, null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    result += CalInfo(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "")
                }
            }
        }
        return result
    }

    private fun instancesUri(fromMillis: Long, toMillis: Long): android.net.Uri {
        return CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(fromMillis.toString())
            .appendPath(toMillis.toString())
            .build()
    }

    fun queryEvents(
        context: Context,
        fromMillis: Long,
        toMillis: Long,
        settings: AppSettings
    ): List<CalEvent> {
        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.CALENDAR_ID
        )
        val result = mutableListOf<CalEvent>()
        context.contentResolver.query(
            instancesUri(fromMillis, toMillis), projection, null, null,
            CalendarContract.Instances.BEGIN + " ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val instanceId = c.getLong(0)
                val eventId = c.getLong(1)
                val begin = c.getLong(2)
                val end = c.getLong(3)
                val title = c.getString(4) ?: ""
                val calendarId = c.getLong(5)
                if (settings.calendarIds.isNotEmpty() && !settings.calendarIds.contains(calendarId)) continue
                val location = lookupLocation(context, eventId)
                result += CalEvent(instanceId, begin, end, title, location, calendarId)
            }
        }
        return result
    }

    fun countEventsPerCalendar(context: Context, fromMillis: Long, toMillis: Long): Map<Long, Int> {
        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.CALENDAR_ID
        )
        val result = mutableMapOf<Long, Int>()
        runCatching {
            context.contentResolver.query(
                instancesUri(fromMillis, toMillis), projection, null, null,
                CalendarContract.Instances.BEGIN + " ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val calId = c.getLong(1)
                    result[calId] = (result[calId] ?: 0) + 1
                }
            }
        }
        return result
    }

    fun previewEvents(
        context: Context,
        fromMillis: Long,
        toMillis: Long,
        limit: Int
    ): List<EventPreview> {
        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.TITLE
        )
        val result = mutableListOf<EventPreview>()
        runCatching {
            context.contentResolver.query(
                instancesUri(fromMillis, toMillis), projection, null, null,
                CalendarContract.Instances.BEGIN + " ASC"
            )?.use { c ->
                while (c.moveToNext() && result.size < limit) {
                    result += EventPreview(c.getString(3) ?: "", c.getLong(2), c.getLong(1))
                }
            }
        }
        return result
    }

    private fun lookupLocation(context: Context, eventId: Long): String {
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events.EVENT_LOCATION),
                CalendarContract.Events._ID + "=?",
                arrayOf(eventId.toString()), null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) ?: "" else ""
            } ?: ""
        }.getOrDefault("")
    }
}
