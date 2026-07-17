package com.mosman.routines

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract

data class CalEvent(val title: String, val begin: Long, val end: Long)

/** Reads upcoming calendar events so routines can follow your timetable. */
object Calendars {

    /** The next event matching [titleContains] within the next week. */
    @SuppressLint("MissingPermission")
    fun nextMatching(ctx: Context, titleContains: String, atStart: Boolean): CalEvent? {
        if (!Permissions.hasCalendar(ctx)) return null
        val now = System.currentTimeMillis()
        val until = now + 7L * 24 * 60 * 60 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, now - 60L * 60 * 1000)   // catch one already running
            ContentUris.appendId(this, until)
        }.build()

        return runCatching {
            ctx.contentResolver.query(uri, arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
            ), null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(0) ?: ""
                    val begin = c.getLong(1)
                    val end = c.getLong(2)
                    if (titleContains.isNotBlank() && !title.contains(titleContains, true)) continue
                    val at = if (atStart) begin else end
                    if (at > now) return@use CalEvent(title, begin, end)
                }
                null
            }
        }.getOrNull()
    }

    /** List of visible calendar event titles, to help the user pick a filter. */
    @SuppressLint("MissingPermission")
    fun upcomingTitles(ctx: Context, limit: Int = 15): List<String> {
        if (!Permissions.hasCalendar(ctx)) return emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, now)
            ContentUris.appendId(this, now + 14L * 24 * 60 * 60 * 1000)
        }.build()
        return runCatching {
            ctx.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE),
                null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                buildList {
                    while (c.moveToNext() && size < limit) {
                        c.getString(0)?.takeIf { it.isNotBlank() }?.let { if (it !in this) add(it) }
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }
}
