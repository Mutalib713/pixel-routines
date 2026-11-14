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

    // --- Gestures (flip / shake / back-tap) ---
    private var sensors: android.hardware.SensorManager? = null
    // What the sensor callback needs, cached by syncWatchers(). The callback runs up to 50 times
    // a second; it used to re-read and re-parse every routine plus the volume settings from
    // storage on each reading. syncWatchers runs on every service start, and Engine.rearmAll
    // restarts the service whenever routines or volume settings change, so this stays current.
    @Volatile private var volSettings = com.mosman.routines.volume.VolumePanelSettings()
    @Volatile private var wantShake = false
    @Volatile private var wantBackTap = false
    @Volatile private var wantFlip = false

    private val motionDetector = com.mosman.routines.volume.VolumeMotionDetector(
        onShakeDetected = {
            if (volSettings.shakeEnabled) {
                com.mosman.routines.volume.VolumePanelController.show(this@EventService)
            }
            Engine.handleEvent(this@EventService) {
                it is Trigger.Gesture && it.type == GestureType.SHAKE
            }
        },
        onBackTapDetected = {
            if (volSettings.backTapEnabled) {
                com.mosman.routines.volume.VolumePanelController.show(this@EventService)
            }
            Engine.handleEvent(this@EventService) {
                it is Trigger.Gesture && it.type == GestureType.BACK_TAP
            }
        },
        onFlipDetected = { isDown ->
            Engine.handleEvent(this@EventService) {
                it is Trigger.Gesture && it.type == if (isDown) GestureType.FLIP_DOWN else GestureType.FLIP_UP
            }
        },
    )

    private val sensorListener = object : android.hardware.SensorEventListener {
        override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        override fun onSensorChanged(e: android.hardware.SensorEvent) {
            val s = volSettings
            motionDetector.onSensorChanged(
                x = e.values[0],
                y = e.values[1],
                z = e.values[2],
                shakeSensitivity = s.shakeSensitivity,
                backTapSensitivity = s.backTapSensitivity,
                cooldownMs = s.cooldownDurationMs,
                enableShake = wantShake,
                enableBackTap = wantBackTap,
                enableFlip = wantFlip,
            )
        }
    }

    // --- App-open watching (polls only while the screen is on) ---
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastForeground = ""
    private val appPoll = object : Runnable {
        override fun run() {
            if (State.screenOn(this@EventService)) {
                foregroundApp()?.let { pkg ->
                    if (pkg != lastForeground) {
                        lastForeground = pkg
                        Engine.handleEvent(this@EventService) {
                            it is Trigger.AppOpened && it.pkg == pkg
                        }
                    }
                }
            }
            handler.postDelayed(this, 2000)
        }
    }

    private fun foregroundApp(): String? = runCatching {
        val usage = getSystemService(android.app.usage.UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - 10_000, now)
        var latest: String? = null
        val e = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND)
                latest = e.packageName
        }
        latest
    }.getOrNull()

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

    /**
     * Re-reads which watchers are needed. Runs on every start (not just onCreate) because
     * the service is usually already alive when a routine gains a gesture/app trigger.
     * The accelerometer and usage poller both cost battery, so they stay off unless used.
     */
    private fun syncWatchers() {
        val triggers = Store.load(this).filter { it.enabled }.flatMap { it.triggers + it.endTriggers }
        val gestures = triggers.filterIsInstance<Trigger.Gesture>().map { it.type }.toSet()
        val vol = com.mosman.routines.volume.VolumePanelStore.load(this)
        volSettings = vol
        // Each detector runs only if something actually uses it (a back-tap detector running
        // for a flip-only routine was just a source of false fires).
        wantShake = vol.shakeEnabled || GestureType.SHAKE in gestures
        wantBackTap = vol.backTapEnabled || GestureType.BACK_TAP in gestures
        wantFlip = GestureType.FLIP_DOWN in gestures || GestureType.FLIP_UP in gestures

        runCatching { sensors?.unregisterListener(sensorListener) }
        sensors = null
        if (wantShake || wantBackTap || wantFlip) {
            motionDetector.reset()
            sensors = getSystemService(android.hardware.SensorManager::class.java)
            // A knuckle tap lasts only a few milliseconds. At SENSOR_DELAY_UI (~15 readings a
            // second) most taps fell between readings, so back-tap gets ~50 a second (GAME).
            // Shake and flip are slow movements and keep the cheaper rate.
            val rate = if (wantBackTap) android.hardware.SensorManager.SENSOR_DELAY_GAME
                else android.hardware.SensorManager.SENSOR_DELAY_UI
            sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)?.let {
                sensors?.registerListener(sensorListener, it, rate)
            }
        }

        handler.removeCallbacks(appPoll)
        if (triggers.any { it is Trigger.AppOpened } && Permissions.hasUsageAccess(this)) {
            handler.post(appPoll)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        syncWatchers()
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        runCatching { sensors?.unregisterListener(sensorListener) }
        handler.removeCallbacks(appPoll)
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

        /** Minimum gap between face-down/face-up transitions. */
        private const val FLIP_GAP_MS = 700L

        fun start(ctx: Context) {
            val i = Intent(ctx, EventService::class.java)
            runCatching { ctx.startForegroundService(i) }
        }
        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, EventService::class.java)) }
        }
    }
}
