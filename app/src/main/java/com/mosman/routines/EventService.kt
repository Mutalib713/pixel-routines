package com.mosman.routines

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * A lightweight foreground service that listens for live device events and asks the
 * Engine to fire any routine whose trigger matches. Runs only while at least one enabled
 * routine has an event trigger (managed by Engine.rearmAll).
 */
class EventService : Service() {

    private var lastBattery = -1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED ->
                    Engine.handleEvent(ctx) { it is Trigger.Power && it.connected }
                Intent.ACTION_POWER_DISCONNECTED ->
                    Engine.handleEvent(ctx) { it is Trigger.Power && !it.connected }
                Intent.ACTION_HEADSET_PLUG -> {
                    val plugged = intent.getIntExtra("state", 0) == 1
                    Engine.handleEvent(ctx) { it is Trigger.Headset && it.connected == plugged }
                }
                Intent.ACTION_SCREEN_ON ->
                    Engine.handleEvent(ctx) { it is Trigger.Screen && it.on }
                Intent.ACTION_SCREEN_OFF ->
                    Engine.handleEvent(ctx) { it is Trigger.Screen && !it.on }
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                    val on = State.airplane(ctx)
                    Engine.handleEvent(ctx) { it is Trigger.Airplane && it.on == on }
                }
                Intent.ACTION_BATTERY_CHANGED -> onBattery(ctx, intent)
                BluetoothDevice.ACTION_ACL_CONNECTED -> onBt(ctx, intent, true)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> onBt(ctx, intent, false)
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> onWifi(ctx)
            }
        }
    }

    private fun onBattery(ctx: Context, intent: Intent) {
        val level = intent.getIntExtra("level", -1)
        val scale = intent.getIntExtra("scale", 100)
        if (level < 0) return
        val pct = level * 100 / scale
        val prev = lastBattery
        lastBattery = pct
        if (prev < 0) return
        Engine.handleEvent(ctx) { t ->
            t is Trigger.Battery && if (t.below) prev >= t.level && pct < t.level
            else prev <= t.level && pct > t.level
        }
    }

    private fun onBt(ctx: Context, intent: Intent, connected: Boolean) {
        val name = runCatching {
            @Suppress("DEPRECATION")
            val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            dev?.name
        }.getOrNull()
        Engine.handleEvent(ctx) { t ->
            t is Trigger.Bluetooth && t.connected == connected &&
                (t.deviceName == null || t.deviceName.equals(name, ignoreCase = true))
        }
    }

    private fun onWifi(ctx: Context) {
        val ssid = Permissions.currentSsid(ctx)
        val connected = ssid != null
        Engine.handleEvent(ctx) { t ->
            t is Trigger.Wifi && t.connected == connected &&
                (t.ssid == null || t.ssid.equals(ssid, ignoreCase = true))
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(SERVICE_ID, notification())
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Automation running", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Pixel Routines is active")
            .setContentText("Watching for your triggers")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "service"
        private const val SERVICE_ID = 7

        fun start(ctx: Context) {
            val i = Intent(ctx, EventService::class.java)
            runCatching { ctx.startForegroundService(i) }
        }
        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, EventService::class.java)) }
        }
    }
}
