package com.shiftalarm.app.calendar

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
