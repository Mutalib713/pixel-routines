package com.mosman.routines

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The reminder itself: a full screen you have to answer, not a notification you can scroll
 * past. Shows over the lock screen and wakes the display, the same way an alarm clock does.
 *
 * Launched by the full-screen intent on the reminder notification rather than by
 * startActivity, because an app in the background cannot start an activity directly.
 */
class ReminderActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get on screen even from a locked, sleeping phone.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        runCatching {
            getSystemService(android.app.KeyguardManager::class.java)
                ?.requestDismissKeyguard(this, null)
        }

        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val routineId = intent.getLongExtra(EXTRA_ROUTINE, 0L)
        val snoozeMin = intent.getIntExtra(EXTRA_SNOOZE, 10)

        startAlerting()
        enableEdgeToEdge()
        setContent {
            PixelRoutinesTheme {
                ReminderScreen(
                    text = text,
                    snoozeMin = snoozeMin,
                    onDone = { finishWith(routineId) },
                    onSnooze = {
                        Reminders.snooze(this, routineId, text, snoozeMin)
                        finishWith(routineId)
                    },
                )
            }
        }
    }

    /** Ringing has to stop when the reminder is answered, and only then. */
    private fun finishWith(routineId: Long) {
        stopAlerting()
        runCatching {
            getSystemService(NotificationManager::class.java)
                .cancel(Reminders.notificationId(routineId))
        }
        finish()
    }

    private fun startAlerting() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                // The alarm stream keeps it audible even on silent, which is the whole
                // point of a reminder you asked not to miss.
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                play()
            }
        }
        runCatching {
            vibrator = if (Build.VERSION.SDK_INT >= 31)
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 600, 700), 0)
            )
        }
    }

    private fun stopAlerting() {
        runCatching { ringtone?.stop() }
        runCatching { vibrator?.cancel() }
        ringtone = null
    }

    // Back must not dismiss a reminder silently — answer it with Done or Snooze.
    override fun onDestroy() {
        stopAlerting()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TEXT = "text"
        const val EXTRA_ROUTINE = "routine"
        const val EXTRA_SNOOZE = "snooze"

        fun intent(ctx: Context, routineId: Long, text: String, snoozeMin: Int): Intent =
            Intent(ctx, ReminderActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_ROUTINE, routineId)
                .putExtra(EXTRA_SNOOZE, snoozeMin)
    }
}

@Composable
private fun ReminderScreen(
    text: String,
    snoozeMin: Int,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
) {
    // Swallow back so a reminder can't be flicked away without a decision.
    BackHandler(enabled = true) {}

    val now = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.8f))
            Icon(
                Icons.Filled.Alarm, null,
                Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                now,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text.ifBlank { "Reminder" },
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Filled.Check, null)
                Spacer(Modifier.width(10.dp))
                Text("Done", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Filled.Snooze, null)
                Spacer(Modifier.width(10.dp))
                Text("Snooze $snoozeMin min", fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
