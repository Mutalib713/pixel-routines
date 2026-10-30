package com.mosman.routines.drafts

import com.mosman.routines.Access
import com.mosman.routines.Action
import com.mosman.routines.Condition
import com.mosman.routines.Routine
import com.mosman.routines.Trigger

/**
 * Result of validating a RoutineDraft before it can be presented or approved.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val requiredAccess: Set<Access> = emptySet(),
)

/**
 * Enforces safety and sanity rules for all routine proposals from AI, Lens, or templates.
 * Every routine draft MUST pass validation before being stored or scheduled.
 */
object RoutineDraftValidator {

    fun validate(draft: RoutineDraft): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val requiredAccess = mutableSetOf<Access>()

        when (draft.operation) {
            DraftOperation.CREATE -> {
                if (draft.proposedRoutine == null) {
                    errors.add("Proposed routine is missing.")
                } else {
                    validateRoutine(draft.proposedRoutine, errors, warnings, requiredAccess)
                }
            }

            DraftOperation.UPDATE -> {
                if (draft.targetRoutineId == null) {
                    errors.add("Target routine ID is required for update.")
                }
                if (draft.proposedRoutine == null) {
                    errors.add("Proposed updated routine is missing.")
                } else {
                    validateRoutine(draft.proposedRoutine, errors, warnings, requiredAccess)
                }
            }

            DraftOperation.ENABLE,
            DraftOperation.DISABLE,
            DraftOperation.DELETE -> {
                if (draft.targetRoutineId == null) {
                    errors.add("Target routine ID is required for this operation.")
                }
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            requiredAccess = requiredAccess,
        )
    }

    private fun validateRoutine(
        routine: Routine,
        errors: MutableList<String>,
        warnings: MutableList<String>,
        access: MutableSet<Access>,
    ) {
        if (routine.name.isBlank()) {
            errors.add("Routine name cannot be blank.")
        } else if (routine.name.length > 100) {
            errors.add("Routine name is too long (maximum 100 characters).")
        }

        if (routine.triggers.isEmpty()) {
            errors.add("At least one trigger is required.")
        } else {
            routine.triggers.forEachIndexed { idx, trigger ->
                validateTrigger("Trigger ${idx + 1}", trigger, errors)
            }
        }

        if (routine.actions.isEmpty()) {
            errors.add("At least one action is required.")
        } else {
            routine.actions.forEachIndexed { idx, action ->
                validateAction("Action ${idx + 1}", action, errors, access)
            }
        }

        routine.conditions.forEachIndexed { idx, condition ->
            validateCondition("Condition ${idx + 1}", condition, errors)
        }

        routine.endTriggers.forEachIndexed { idx, trigger ->
            validateTrigger("End condition ${idx + 1}", trigger, errors)
        }

        routine.endActions.forEachIndexed { idx, action ->
            validateAction("End action ${idx + 1}", action, errors, access)
        }
    }

    private fun validateTrigger(prefix: String, trigger: Trigger, errors: MutableList<String>) {
        when (trigger) {
            is Trigger.TimeOfDay -> {
                if (trigger.hour !in 0..23) errors.add("$prefix: hour must be between 0 and 23.")
                if (trigger.minute !in 0..59) errors.add("$prefix: minute must be between 0 and 59.")
                if (trigger.days.isEmpty()) errors.add("$prefix: at least one day must be selected.")
                if (trigger.days.any { it !in 1..7 }) errors.add("$prefix: days must be between 1 and 7.")
            }

            is Trigger.Battery -> {
                if (trigger.level !in 1..100) errors.add("$prefix: battery level must be between 1% and 100%.")
            }

            is Trigger.Location -> {
                if (trigger.lat !in -90.0..90.0) errors.add("$prefix: latitude must be between -90 and 90.")
                if (trigger.lng !in -180.0..180.0) errors.add("$prefix: longitude must be between -180 and 180.")
                if (trigger.radius <= 0f) errors.add("$prefix: radius must be greater than 0 meters.")
                if (trigger.place.isBlank()) errors.add("$prefix: place name cannot be blank.")
            }

            is Trigger.Sun -> {
                if (trigger.offsetMin !in -720..720) {
                    errors.add("$prefix: sun offset minutes must be between -720 and 720.")
                }
            }

            is Trigger.NotificationFrom -> {
                if (trigger.pkg.isBlank() && trigger.label.isBlank()) {
                    errors.add("$prefix: app package or label is required.")
                }
            }

            is Trigger.AppOpened -> {
                if (trigger.pkg.isBlank()) errors.add("$prefix: package name is required.")
            }

            is Trigger.NfcTag -> {
                if (trigger.id.isBlank() && trigger.label.isBlank()) {
                    errors.add("$prefix: NFC tag identifier is required.")
                }
            }

            is Trigger.Power,
            is Trigger.Headset,
            is Trigger.Bluetooth,
            is Trigger.Wifi,
            is Trigger.Screen,
            is Trigger.Airplane,
            is Trigger.CalendarEvent,
            is Trigger.Motion,
            is Trigger.Gesture -> {
                // Structurally sound by definition
            }
        }
    }

    private fun validateAction(
        prefix: String,
        action: Action,
        errors: MutableList<String>,
        access: MutableSet<Access>,
    ) {
        val needed = action.access()
        if (needed != Access.NONE) {
            access.add(needed)
        }

        when (action) {
            is Action.Volume -> {
                if (action.percent !in 0..100) errors.add("$prefix: volume percent must be between 0 and 100.")
            }

            is Action.Brightness -> {
                if (action.percent !in 0..100) errors.add("$prefix: brightness percent must be between 0 and 100.")
            }

            is Action.Wait -> {
                if (action.seconds !in 1..30) errors.add("$prefix: wait duration must be between 1 and 30 seconds.")
            }

            is Action.Remind -> {
                if (action.text.isBlank()) errors.add("$prefix: reminder text cannot be blank.")
                if (action.snoozeMin !in 1..120) errors.add("$prefix: snooze must be between 1 and 120 minutes.")
            }

            is Action.Call -> {
                if (action.number.isBlank() && action.who.isBlank()) {
                    errors.add("$prefix: phone number or recipient name is required.")
                }
            }

            is Action.SendSms -> {
                if (action.number.isBlank() && action.who.isBlank()) {
                    errors.add("$prefix: recipient phone number is required.")
                }
            }

            is Action.Message -> {
                if (action.number.isBlank() && action.who.isBlank()) {
                    errors.add("$prefix: recipient is required.")
                }
            }

            is Action.LaunchApp -> {
                if (action.pkg.isBlank()) errors.add("$prefix: app package name is required.")
            }

            is Action.OpenUrl -> {
                if (action.url.isBlank()) errors.add("$prefix: URL cannot be blank.")
            }

            is Action.Ringer,
            is Action.Dnd,
            is Action.AutoRotate,
            is Action.DarkTheme,
            is Action.BatterySaver,
            is Action.WifiToggle,
            is Action.BluetoothToggle,
            is Action.AirplaneToggle,
            is Action.Flashlight,
            is Action.Notify,
            is Action.Media,
            is Action.Speak,
            is Action.ReplyNotification -> {
                // Structurally sound
            }
        }
    }

    private fun validateCondition(prefix: String, condition: Condition, errors: MutableList<String>) {
        when (condition) {
            is Condition.OnDays -> {
                if (condition.days.isEmpty()) errors.add("$prefix: days set cannot be empty.")
                if (condition.days.any { it !in 1..7 }) errors.add("$prefix: days must be between 1 and 7.")
            }

            is Condition.BetweenHours -> {
                if (condition.startH !in 0..23 || condition.endH !in 0..23) {
                    errors.add("$prefix: hours must be between 0 and 23.")
                }
                if (condition.startM !in 0..59 || condition.endM !in 0..59) {
                    errors.add("$prefix: minutes must be between 0 and 59.")
                }
            }

            is Condition.BatteryUnder -> {
                if (condition.level !in 1..100) errors.add("$prefix: battery level must be between 1% and 100%.")
            }

            is Condition.WhileCharging -> {
                // Structurally sound
            }
        }
    }
}
