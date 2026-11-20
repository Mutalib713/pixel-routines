package com.mosman.routines

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineTextTest {
    @Test fun overnightEndMovesToTheNextDay() {
        // 22:00 -> 06:00 on weekdays: Friday night must end Saturday morning, not Monday.
        val end = RoutineText.endAt(22, 0, 6, 0, setOf(1, 2, 3, 4, 5))
        assertEquals(setOf(2, 3, 4, 5, 6), end.days)
        // Sunday night ends Monday morning (7 wraps to 1).
        assertEquals(setOf(1), RoutineText.endAt(23, 0, 1, 0, setOf(7)).days)
    }

    @Test fun sameDayEndKeepsTheDays() {
        val end = RoutineText.endAt(8, 0, 10, 0, setOf(1, 2, 3, 4, 5))
        assertEquals(setOf(1, 2, 3, 4, 5), end.days)
        assertEquals(10, end.hour)
    }

    @Test fun parserOvernightRequestEndsNextDay() {
        val r = com.mosman.routines.ui.assistant.RoutineAIParser.parse("do not disturb from 10pm to 6am on weekdays")
        val end = r.draft?.proposedRoutine?.endTriggers?.single() as? Trigger.TimeOfDay
        assertEquals(setOf(2, 3, 4, 5, 6), end?.days)
    }
}
