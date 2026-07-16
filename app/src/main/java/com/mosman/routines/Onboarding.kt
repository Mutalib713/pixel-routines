package com.mosman.routines

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** First-run walkthrough. Shown once, then never again (Store.onboarded). */
@Composable
fun Onboarding(tick: Int, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    val last = 3

    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (fadeIn(tween(220)) togetherWith fadeOut(tween(160)))
                },
                label = "onboarding",
            ) { p ->
                when (p) {
                    0 -> Page(Icons.Filled.AutoAwesome, "Pixel Routines",
                        "Your phone, running itself.\nSet it up once — it just happens.")
                    1 -> Page(Icons.Filled.Bolt, "IF this, THEN that",
                        "Pick what starts a routine — a time, arriving somewhere, " +
                            "headphones plugging in, the battery getting low.\n\n" +
                            "Then pick what your phone should do: go silent, dim the screen, " +
                            "turn on dark theme, open an app.")
                    2 -> Page(Icons.Filled.Undo, "It cleans up after itself",
                        "Give a routine an end condition — a time, or leaving a place — and " +
                            "it puts your settings back exactly the way they were.")
                    else -> PermissionsPage(tick)
                }
            }

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(last + 1) { i ->
                    Box(
                        Modifier.size(if (i == page) 22.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == page) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (page < last) page++ else { Store.setOnboarded(ctx); onDone() }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(if (page < last) "Next" else "Get started",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { Store.setOnboarded(ctx); onDone() }) {
                Text(if (page < last) "Skip" else "I'll do this later")
            }
        }
    }
}

@Composable
private fun Page(icon: ImageVector, title: String, body: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(96.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(28.dp))
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermissionsPage(tick: Int) {
    val ctx = LocalContext.current
    val hasDnd = remember(tick) { Permissions.hasDnd(ctx) }
    val hasExact = remember(tick) { Permissions.hasExactAlarm(ctx) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(96.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(24.dp))
        Text("Two quick permissions", fontSize = 26.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Android keeps these behind a switch. You only do this once.",
            fontSize = 14.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))

        PermRow("Do Not Disturb access", "Lets routines switch silent, vibrate and sound",
            hasDnd) { ctx.startActivity(Permissions.dndSettings()) }
        Spacer(Modifier.height(10.dp))
        PermRow("Exact alarms", "Makes timed routines fire on the dot",
            hasExact) { ctx.startActivity(Permissions.exactAlarmSettings(ctx)) }
    }
}

@Composable
private fun PermRow(title: String, sub: String, granted: Boolean, onGrant: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) Icon(Icons.Filled.CheckCircle, "Granted",
                tint = MaterialTheme.colorScheme.primary)
            else TextButton(onClick = onGrant) { Text("Allow", fontWeight = FontWeight.Bold) }
        }
    }
}
