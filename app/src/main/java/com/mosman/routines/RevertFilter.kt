package com.mosman.routines

/**
 * Decides which "put it back" actions run when a routine ends.
 *
 * The "keep my Silent / Vibrate" setting (Sacred Rule 8) exists so a routine never turns sound
 * back on after the USER muted the phone. It used to look only at "is the phone muted right
 * now?", which also blocked routines that muted the phone THEMSELVES: Lecture Mode set Vibrate,
 * and at the end of class the setting saw Vibrate and refused to restore Sound.
 *
 * The rule now: if the phone is in exactly the mode this routine set, the routine owns that
 * state and may undo it. If the phone is muted some other way, the user did it, so leave it.
 */
object RevertFilter {

    fun filter(
        snapshot: List<Action>,
        routineActions: List<Action>,
        currentRinger: RingerMode,
        preserveSilentVibrate: Boolean,
    ): List<Action> {
        // Ringer first: restoring Sound before ring volume stops the volume step being blocked
        // by the "phone is muted" check in Actions.
        val ordered = snapshot.sortedBy { if (it is Action.Ringer) 0 else 1 }
        if (!preserveSilentVibrate || currentRinger == RingerMode.SOUND) return ordered

        val routineSetThisMode = routineActions.any { it is Action.Ringer && it.mode == currentRinger }
        if (routineSetThisMode) return ordered

        return ordered.filterNot { a ->
            (a is Action.Ringer && a.mode == RingerMode.SOUND) ||
                (a is Action.Volume && a.percent > 0 &&
                    (a.stream == StreamType.RING || a.stream == StreamType.NOTIFICATION))
        }
    }
}
