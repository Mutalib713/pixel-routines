package com.mosman.routines.drafts

import com.mosman.routines.Action
import com.mosman.routines.Condition
import com.mosman.routines.Routine
import com.mosman.routines.Trigger

enum class ConflictSeverity {
    WARNING,
    CONFLICT;

    fun label(): String = when (this) {
        WARNING -> "Potential overlap"
        CONFLICT -> "Conflicting action"
    }
}

data class ConflictWarning(
    val severity: ConflictSeverity,
    val existingRoutineId: Long,
    val existingRoutineName: String,
    val message: String,
    val settingName: String,
)

/**
 * Analyzes routine proposals against existing saved routines to detect:
 * 1. Schedule overlaps (same days and time windows)
 * 2. Competing actions modifying the same setting (e.g. Vibrate vs Sound, DND on vs off)
 */
object RoutineConflictAnalyzer {

    fun analyze(
        proposed: Routine,
        existingRoutines: List<Routine>,
    ): List<ConflictWarning> {
        val conflicts = mutableListOf<ConflictWarning>()

        for (existing in existingRoutines) {
            // Don't compare against itself when editing
            if (existing.id == proposed.id) continue
            // Only compare against enabled routines
            if (!existing.enabled) continue

            val scheduleOverlaps = checkScheduleOverlap(proposed, existing)
            if (!scheduleOverlaps) continue

            // Check setting collisions between actions
            val settingCollisions = findSettingCollisions(proposed.actions, existing.actions)
            for (collision in settingCollisions) {
                conflicts.add(
                    ConflictWarning(
                        severity = if (collision.isDirectlyOpposing) ConflictSeverity.CONFLICT else ConflictSeverity.WARNING,
                        existingRoutineId = existing.id,
                        existingRoutineName = existing.name,
                        message = collision.message(proposed.name, existing.name),
                        settingName = collision.settingName,
                    )
                )
            }
        }

        return conflicts
    }

    private data class SettingCollision(
        val settingName: String,
        val isDirectlyOpposing: Boolean,
        val proposedDetail: String,
        val existingDetail: String,
    ) {
        fun message(proposedName: String, existingName: String): String =
            "Both “${proposedName.ifBlank { "New Routine" }}” ($proposedDetail) and “$existingName” ($existingDetail) modify $settingName at overlapping times."
    }

    private fun findSettingCollisions(
        proposedActions: List<Action>,
        existingActions: List<Action>,
    ): List<SettingCollision> {
        val results = mutableListOf<SettingCollision>()

        // 1. Ringer
        val propRinger = proposedActions.filterIsInstance<Action.Ringer>().firstOrNull()
        val existRinger = existingActions.filterIsInstance<Action.Ringer>().firstOrNull()
        if (propRinger != null && existRinger != null) {
            results.add(
                SettingCollision(
                    settingName = "Ringer mode",
                    isDirectlyOpposing = propRinger.mode != existRinger.mode,
                    proposedDetail = propRinger.mode.label(),
                    existingDetail = existRinger.mode.label(),
                )
            )
        }

        // 2. DND
        val propDnd = proposedActions.filterIsInstance<Action.Dnd>().firstOrNull()
        val existDnd = existingActions.filterIsInstance<Action.Dnd>().firstOrNull()
        if (propDnd != null && existDnd != null) {
            results.add(
                SettingCollision(
                    settingName = "Do Not Disturb",
                    isDirectlyOpposing = propDnd.on != existDnd.on,
                    proposedDetail = if (propDnd.on) "Turn ON" else "Turn OFF",
                    existingDetail = if (existDnd.on) "Turn ON" else "Turn OFF",
                )
            )
        }

        // 3. Brightness
        val propBright = proposedActions.filterIsInstance<Action.Brightness>().firstOrNull()
        val existBright = existingActions.filterIsInstance<Action.Brightness>().firstOrNull()
        if (propBright != null && existBright != null && propBright.percent != existBright.percent) {
            results.add(
                SettingCollision(
                    settingName = "Brightness",
                    isDirectlyOpposing = true,
                    proposedDetail = "${propBright.percent}%",
                    existingDetail = "${existBright.percent}%",
                )
            )
        }

        // 4. Dark theme
        val propDark = proposedActions.filterIsInstance<Action.DarkTheme>().firstOrNull()
        val existDark = existingActions.filterIsInstance<Action.DarkTheme>().firstOrNull()
        if (propDark != null && existDark != null) {
            results.add(
                SettingCollision(
                    settingName = "Dark theme",
                    isDirectlyOpposing = propDark.on != existDark.on,
                    proposedDetail = if (propDark.on) "Turn ON" else "Turn OFF",
                    existingDetail = if (existDark.on) "Turn ON" else "Turn OFF",
                )
            )
        }

        // 5. Battery saver
        val propSaver = proposedActions.filterIsInstance<Action.BatterySaver>().firstOrNull()
        val existSaver = existingActions.filterIsInstance<Action.BatterySaver>().firstOrNull()
        if (propSaver != null && existSaver != null) {
            results.add(
                SettingCollision(
                    settingName = "Battery Saver",
                    isDirectlyOpposing = propSaver.on != existSaver.on,
                    proposedDetail = if (propSaver.on) "Turn ON" else "Turn OFF",
                    existingDetail = if (existSaver.on) "Turn ON" else "Turn OFF",
                )
            )
        }

        // 6. Wi-Fi
        val propWifi = proposedActions.filterIsInstance<Action.WifiToggle>().firstOrNull()
        val existWifi = existingActions.filterIsInstance<Action.WifiToggle>().firstOrNull()
        if (propWifi != null && existWifi != null) {
            results.add(
                SettingCollision(
                    settingName = "Wi-Fi",
                    isDirectlyOpposing = propWifi.on != existWifi.on,
                    proposedDetail = if (propWifi.on) "Turn ON" else "Turn OFF",
                    existingDetail = if (existWifi.on) "Turn ON" else "Turn OFF",
                )
            )
        }

        // 7. Bluetooth
        val propBt = proposedActions.filterIsInstance<Action.BluetoothToggle>().firstOrNull()
        val existBt = existingActions.filterIsInstance<Action.BluetoothToggle>().firstOrNull()
        if (propBt != null && existBt != null) {
            results.add(
                SettingCollision(
                    settingName = "Bluetooth",
                    isDirectlyOpposing = propBt.on != existBt.on,
                    proposedDetail = if (propBt.on) "Turn ON" else "Turn OFF",
                    existingDetail = if (existBt.on) "Turn ON" else "Turn OFF",
                )
            )
        }

        return results
    }

    private fun checkScheduleOverlap(r1: Routine, r2: Routine): Boolean {
        val t1Times = r1.triggers.filterIsInstance<Trigger.TimeOfDay>()
        val t2Times = r2.triggers.filterIsInstance<Trigger.TimeOfDay>()

        // Case 1: Both have TimeOfDay triggers
        for (a in t1Times) {
            for (b in t2Times) {
                val commonDays = a.days.intersect(b.days)
                if (commonDays.isNotEmpty()) {
                    // Check if times are within 60 minutes of each other or identical
                    val minA = a.hour * 60 + a.minute
                    val minB = b.hour * 60 + b.minute
                    if (kotlin.math.abs(minA - minB) <= 60) {
                        return true
                    }
                }
            }
        }

        // Case 2: One has TimeOfDay, the other has a BetweenHours condition
        val conds1 = r1.conditions.filterIsInstance<Condition.BetweenHours>()
        val conds2 = r2.conditions.filterIsInstance<Condition.BetweenHours>()

        for (a in t1Times) {
            for (w in conds2) {
                val minA = a.hour * 60 + a.minute
                val startW = w.startH * 60 + w.startM
                val endW = w.endH * 60 + w.endM
                if (isTimeInWindow(minA, startW, endW)) return true
            }
        }

        for (b in t2Times) {
            for (w in conds1) {
                val minB = b.hour * 60 + b.minute
                val startW = w.startH * 60 + w.startM
                val endW = w.endH * 60 + w.endM
                if (isTimeInWindow(minB, startW, endW)) return true
            }
        }

        // Case 3: Both have BetweenHours conditions that overlap
        for (w1 in conds1) {
            for (w2 in conds2) {
                if (windowsOverlap(w1, w2)) return true
            }
        }

        return false
    }

    private fun isTimeInWindow(timeMin: Int, startMin: Int, endMin: Int): Boolean {
        return if (startMin <= endMin) {
            timeMin in startMin..endMin
        } else {
            // Over midnight window
            timeMin >= startMin || timeMin <= endMin
        }
    }

    private fun windowsOverlap(w1: Condition.BetweenHours, w2: Condition.BetweenHours): Boolean {
        val s1 = w1.startH * 60 + w1.startM
        val e1 = w1.endH * 60 + w1.endM
        val s2 = w2.startH * 60 + w2.startM
        val e2 = w2.endH * 60 + w2.endM
        // If either doesn't wrap midnight
        return if (s1 <= e1 && s2 <= e2) {
            maxOf(s1, s2) <= minOf(e1, e2)
        } else {
            // Conservative check for wrapping windows
            true
        }
    }
}
