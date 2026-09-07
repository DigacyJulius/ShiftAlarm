package com.shiftalarm.app.calendar

import android.content.Context
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object IcalSource {

    fun fetchEventsFromUrl(url: String, fromMillis: Long, toMillis: Long): List<CalEvent> {
        val text = download(url)
        return parseAll(text).filter { it.begin >= fromMillis && it.begin <= toMillis }
    }

    fun parseFromUri(context: Context, uri: Uri): List<CalEvent> {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw IllegalStateException("\u8b80\u5514\u5230\u6a94\u6848")
        return parseAll(text)
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = true
        try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun parseAll(text: String): List<CalEvent> {
        val lines = unfold(text.split("\r\n", "\n"))
        val events = mutableListOf<CalEvent>()
        var inEvent = false
        var summary = ""
        var location = ""
        var dtstart = 0L
        var dtend = 0L
        var uid = ""
        for (raw in lines) {
            val line = raw.trimEnd()
            when {
                line == "BEGIN:VEVENT" -> {
                    inEvent = true
                    summary = ""
                    location = ""
                    dtstart = 0L
                    dtend = 0L
                    uid = ""
                }
                line == "END:VEVENT" -> {
                    if (inEvent && dtstart > 0L) {
                        events += CalEvent(
                            instanceId = ((uid.ifEmpty { summary + dtstart }).hashCode().toLong() and 0x7FFFFFFFL),
                            begin = dtstart,
                            end = if (dtend > 0L) dtend else dtstart,
                            title = summary,
                            location = location,
                            calendarId = -1L
                        )
                    }
                    inEvent = false
                }
                inEvent && line.startsWith("SUMMARY:") -> summary = unescape(line.substring(8))
                inEvent && line.startsWith("LOCATION:") -> location = unescape(line.substring(9))
                inEvent && line.startsWith("UID:") -> uid = line.substring(4)
                inEvent && (line.startsWith("DTSTART") || line.startsWith("DTEND")) -> {
                    val t = parseDateTime(line)
                    if (line.startsWith("DTSTART")) dtstart = t else dtend = t
                }
            }
        }
        return events
    }

    private fun unfold(rawLines: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (l in rawLines) {
            if ((l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty()) {
                out[out.size - 1] = out.last() + l.substring(1)
            } else {
                out.add(l)
            }
        }
        return out
    }

    private fun parseDateTime(line: String): Long {
        val value = line.substringAfter(":")
        return try {
            when {
                value.endsWith("Z") -> {
                    val f = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
                    f.timeZone = TimeZone.getTimeZone("UTC")
                    f.parse(value)?.time ?: 0L
                }
                value.length == 8 -> {
                    val f = SimpleDateFormat("yyyyMMdd", Locale.US)
                    f.timeZone = TimeZone.getDefault()
                    f.parse(value)?.time ?: 0L
                }
                else -> {
                    val f = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
                    f.timeZone = TimeZone.getDefault()
                    f.parse(value)?.time ?: 0L
                }
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun unescape(s: String): String =
        s.replace("\\,", ",").replace("\\;", ";").replace("\\n", " ").replace("\\N", " ")
}
