package com.mosman.routines.volume

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.mosman.routines.Engine
import com.mosman.routines.Permissions
import com.mosman.routines.Trigger
import kotlin.math.abs

/**
 * Edge Swipe Gesture Service.
 * Anchors a non-intrusive edge detector overlay along the user's chosen screen edge
 * (Left, Right, Top, or Bottom). Detects inward swipe gestures to open the system volume panel
 * without obstructing normal screen interactions.
 */
class SwipeTriggerService : Service() {

    private var windowManager: WindowManager? = null
    private var edgeView: FrameLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Permissions.hasOverlay(this)) {
            stopSelf()
            return
        }
        setupEdgeGestureZone()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Permissions.hasOverlay(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Re-setup if direction changed
        teardownView()
        setupEdgeGestureZone()
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupEdgeGestureZone() {
        windowManager = getSystemService(WindowManager::class.java) ?: return
        val settings = VolumePanelStore.load(this)
        val dir = settings.swipeDirection
        val dm = resources.displayMetrics

        val isHorizontalEdge = (dir == SwipeDirection.LEFT || dir == SwipeDirection.RIGHT)
        val edgeThickness = dpToPx(24)
        val edgeLength = if (isHorizontalEdge) (dm.heightPixels * 0.35f).toInt() else (dm.widthPixels * 0.40f).toInt()

        val gravity = when (dir) {
            SwipeDirection.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
            SwipeDirection.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
            SwipeDirection.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            SwipeDirection.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        val width = if (isHorizontalEdge) edgeThickness else edgeLength
        val height = if (isHorizontalEdge) edgeLength else edgeThickness

        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
        }

        val container = FrameLayout(this).apply {
            // Subtle translucent handle so user knows where edge gesture is active
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(0x332563EB) // Translucent theme tint
                setStroke(dpToPx(1), 0x55FFFFFF)
            }
            background = bg
        }

        container.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        val dx = event.rawX - startX
                        val dy = event.rawY - startY
                        val swipeDistance = dpToPx(28)

                        val isTriggered = when (dir) {
                            SwipeDirection.LEFT -> dx > swipeDistance && abs(dx) > abs(dy)
                            SwipeDirection.RIGHT -> dx < -swipeDistance && abs(dx) > abs(dy)
                            SwipeDirection.TOP -> dy > swipeDistance && abs(dy) > abs(dx)
                            SwipeDirection.BOTTOM -> dy < -swipeDistance && abs(dy) > abs(dx)
                        }

                        if (isTriggered) {
                            val shown = VolumePanelController.show(this@SwipeTriggerService)
                            if (shown) {
                                Engine.handleEvent(this@SwipeTriggerService) { t ->
                                    t is Trigger.Swipe && t.direction == dir
                                }
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        edgeView = container
        runCatching { windowManager?.addView(container, params) }
    }

    private fun teardownView() {
        edgeView?.let {
            runCatching { windowManager?.removeView(it) }
        }
        edgeView = null
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        teardownView()
        windowManager = null
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, SwipeTriggerService::class.java)
            runCatching { ctx.startService(i) }
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, SwipeTriggerService::class.java)
            runCatching { ctx.stopService(i) }
        }
    }
}
