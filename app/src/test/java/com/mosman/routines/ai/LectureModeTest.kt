package com.mosman.routines.ai

import com.mosman.routines.Action
import com.mosman.routines.RingerMode
import com.mosman.routines.Trigger
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Built from a real KNUST BIT 2 timetable (aSc Timetables, generated 20/10/2026). */
class LectureModeTest {

    private fun slot(course: String, day: Int, s: String, e: String) =
        ClassSlot(course, "", day, LectureMode.minutes(s)!!, LectureMode.minutes(e)!!, "")

    private val week = listOf(
        slot("ENGL 263", 2, "08:00", "09:55"),
        slot("BIT 291", 2, "10:30", "12:25"),
        slot("BIT 261", 2, "15:00", "16:55"),
        slot("BIT 297", 2, "17:00", "18:55"),
        slot("BIT 255", 3, "10:30", "12:25"),
        slot("BIT 293", 3, "15:00", "16:55"),
        slot("BIT 263", 4, "10:30", "12:25"),
        slot("BIT 253", 4, "17:00", "18:55"),
    )

    private fun t(h: Int, m: Int, vararg days: Int) = Trigger.TimeOfDay(h, m, days.toSet())

    @Test fun backToBackClasses_stayOneStretch() {
        // Tuesday 15:00-16:55 then 17:00-18:55: one block, so sound doesn't return at 16:55.
        val tue = LectureMode.blocks(week).filter { it.day == 2 }
        assertEquals(3, tue.size)
        assertTrue(tue.any { it.start == 15 * 60 && it.end == 18 * 60 + 55 })
    }

    @Test fun theWholeWeekIsOneLectureModeRoutine() {
        val rs = LectureMode.build(week, leadMin = 5)
        assertEquals(1, rs.size)
        val r = rs.single()
        assertEquals("Lecture Mode", r.name)
        assertEquals(
            setOf(t(7, 55, 2), t(10, 25, 2, 3, 4), t(14, 55, 2, 3), t(16, 55, 4)),
            r.triggers.toSet(),
        )
        assertEquals(
            setOf(t(9, 55, 2), t(12, 25, 2, 3, 4), t(18, 55, 2, 4), t(16, 55, 3)),
            r.endTriggers.toSet(),
        )
        assertEquals(listOf(Action.Ringer(RingerMode.VIBRATE), Action.Dnd(true)), r.actions)
    }

    @Test fun aBusyTimetable_splitsInsteadOfDroppingClasses() {
        // 10 different hourly classes every weekday: more than 8 distinct start times.
        val busy = (1..5).flatMap { d -> (8..17).map { h -> slot("C$h", d, "%02d:00".format(h), "%02d:30".format(h)) } }
        val rs = LectureMode.build(busy, leadMin = 0)
        assertTrue(rs.size >= 2)
        rs.forEach { assertTrue(it.triggers.size <= 8 && it.endTriggers.size <= 8) }
        assertEquals(10, rs.flatMap { it.triggers }.size) // every start time is covered once
    }

    @Test fun geminisRowsAreCheckedOnThePhone() {
        val json = JSONObject("""{"classes":[
            {"course":"BIT 291","day":2,"start":"10:30","end":"12:25","venue":"SCB-SF1"},
            {"course":"BAD","day":9,"start":"10:30","end":"12:25"},
            {"course":"BACKWARDS","day":3,"start":"12:00","end":"10:00"},
            {"course":"NOT A TIME","day":3,"start":"morning","end":"noon"},
            {"course":"BIT 291","day":2,"start":"10:30","end":"12:25","venue":"SCB-SF1"}
        ]}""")
        val (slots, dropped) = LectureMode.parseClasses(json)
        assertEquals(1, slots.size)   // the duplicate collapses
        assertEquals(3, dropped)
    }

    @Test fun draftsPassTheValidatorAndListTheClasses() {
        val (msg, drafts) = LectureMode.drafts(week, dropped = 0)
        assertEquals(1, drafts.size)
        assertTrue(msg.contains("8 classes"))
        assertTrue(drafts.single().explanation.contains("Tue 08:00–09:55 ENGL 263"))
    }

    @Test fun noClasses_noRoutine() {
        assertTrue(LectureMode.build(emptyList()).isEmpty())
        assertTrue(LectureMode.drafts(emptyList(), 0).second.isEmpty())
    }
}
