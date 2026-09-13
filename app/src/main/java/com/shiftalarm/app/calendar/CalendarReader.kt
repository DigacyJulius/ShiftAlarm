package com.shiftalarm.app.calendar

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.serialization.Serializable

@Serializable
data class CalEvent(
    val instanceId: Long,
    val begin: Long,
    val end: Long,
    val title: String,
    val location: String,
    val calendarId: Long
)

data class EventPreview(
    val title: String,
    val begin: Long,
    val calendarId: Long
)

data class CalInfo(
    val id: Long,
    val name: String,
    val account: String
)

object CalendarReader {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

    fun listCalendars(context: Context): List<CalInfo> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            android.provider.CalendarContract.Calendars._ID,
            android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            android.provider.CalendarContract.Calendars.ACCOUNT_NAME,
            android.provider.CalendarContract.Calendars.CALENDAR_COLOR
        )
        val result = mutableListOf<CalInfo>()
        runCatching {
            context.contentResolver.query(
                android.provider.CalendarContract.Calendars.CONTENT_URI,
                projection, null, null,
                android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    result += CalInfo(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "")
                }
            }
        }
        return result
    }

    private fun instancesUri(fromMillis: Long, toMillis: Long): android.net.Uri =
        android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(fromMillis.toString())
            .appendPath(toMillis.toString())
            .build()

    /**
     * Read events from the user's device calendars within a time window.
     * Returns events from ALL calendars when [calendarIds] is empty; only
     * the selected ones otherwise. Throws SecurityException if the
     * READ_CALENDAR permission is missing — callers handle that.
     */
    fun queryEvents(
        context: Context,
        fromMillis: Long,
        toMillis: Long,
        calendarIds: Set<Long>
    ): List<CalEvent> {
        val projection = arrayOf(
            android.provider.CalendarContract.Instances._ID,
            android.provider.CalendarContract.Instances.BEGIN,
            android.provider.CalendarContract.Instances.END,
            android.provider.CalendarContract.Instances.TITLE,
            android.provider.CalendarContract.Instances.EVENT_LOCATION,
            android.provider.CalendarContract.Instances.CALENDAR_ID
        )
        val result = mutableListOf<CalEvent>()
        context.contentResolver.query(
            instancesUri(fromMillis, toMillis), projection, null, null,
            android.provider.CalendarContract.Instances.BEGIN + " ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val calId = c.getLong(5)
                if (calendarIds.isNotEmpty() && calId !in calendarIds) continue
                result += CalEvent(
                    instanceId = c.getLong(0),
                    begin = c.getLong(1),
                    end = c.getLong(2),
                    title = c.getString(3) ?: "",
                    location = c.getString(4) ?: "",
                    calendarId = calId
                )
            }
        }
        return result
    }
}
