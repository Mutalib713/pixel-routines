package com.mosman.routines.volume

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
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
 * Edge-swipe zones drawn over other apps.
 *
 * One thin strip per edge that something needs: the volume-panel edge from Volume Panel
 * Triggers, plus any edge a routine uses as a "Swipe from edge" trigger.
 *
 * Why it is built this way (each of these was a reason swipes silently did nothing):
 *  - With gesture navigation every edge already belongs to the system (Back on the sides,
 *    Home at the bottom, the notification shade at the top). The side strips ask Android to
 *    leave them alone with systemGestureExclusionRects (honoured for up to 200dp per side,
 *    so the strips are 180dp tall), and the top/bottom strips sit just inside the status bar
 *    and the gesture handle instead of on top of them.
 *  - When the system still takes a gesture, the strip gets ACTION_CANCEL rather than
 *    ACTION_UP, so the swipe is recognised during ACTION_MOVE as soon as it is far enough.
 */
class SwipeTriggerService : Service() {

    private var windowManager: WindowManager? = null
    private val edgeViews = mutableListOf<View>()
    private var lastSwipeAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Permissions.hasOverlay(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Rebuild every time: the set of edges may have changed.
        teardownViews()
        val edges = VolumePanelStore.swipeEdges(this)
        if (edges.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        windowManager = getSystemService(WindowManager::class.java)
        edges.forEach { addEdgeZone(it) }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addEdgeZone(dir: SwipeDirection) {
        val wm = windowManager ?: return
        val dm = resources.displayMetrics
        val isSideEdge = dir == SwipeDirection.LEFT || dir == SwipeDirection.RIGHT
        val thickness = dpToPx(24)

        val params = WindowManager.LayoutParams(
            if (isSideEdge) thickness else (dm.widthPixels * 0.40f).toInt(),
            if (isSideEdge) minOf(dpToPx(180), (dm.heightPixels * 0.35f).toInt()) else thickness,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = when (dir) {
                SwipeDirection.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
                SwipeDirection.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
                SwipeDirection.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                SwipeDirection.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            // Stay clear of the status bar (top) and the home-gesture area (bottom).
            y = when (dir) {
                SwipeDirection.TOP -> systemDimen("status_bar_height", 32)
                SwipeDirection.BOTTOM -> systemDimen("navigation_bar_height", 48) + dpToPx(8)
                else -> 0
            }
        }

        val zone = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(0x332563EB)
                setStroke(dpToPx(1), 0x55FFFFFF)
            }
            // Claim the whole strip from the system Back gesture once it has a size.
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                v.systemGestureExclusionRects = listOf(Rect(0, 0, v.width, v.height))
            }
        }

        zone.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var fired = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        fired = false
                    }
                    MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                        if (!fired && isInwardSwipe(dir, event.rawX - startX, event.rawY - startY)) {
                            fired = true
                            onSwipe(dir)
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> fired = false
                }
                return true
            }
        })

        runCatching {
            wm.addView(zone, params)
            edgeViews += zone
        }
    }

    private fun isInwardSwipe(dir: SwipeDirection, dx: Float, dy: Float): Boolean {
        val min = dpToPx(28)
        return when (dir) {
            SwipeDirection.LEFT -> dx > min && abs(dx) > abs(dy)
            SwipeDirection.RIGHT -> dx < -min && abs(dx) > abs(dy)
            SwipeDirection.TOP -> dy > min && abs(dy) > abs(dx)
            SwipeDirection.BOTTOM -> dy < -min && abs(dy) > abs(dx)
        }
    }

    private fun onSwipe(dir: SwipeDirection) {
        val now = System.currentTimeMillis()
        if (now - lastSwipeAt < SWIPE_GAP_MS) return
        lastSwipeAt = now

        val settings = VolumePanelStore.load(this)
        if (settings.swipeEnabled && settings.swipeDirection == dir) {
            VolumePanelController.show(this)
        }
        // Routines fire on their own, whether or not the volume panel is set to this edge.
        Engine.handleEvent(this) { t -> t is Trigger.Swipe && t.direction == dir }
    }

    private fun teardownViews() {
        edgeViews.forEach { v -> runCatching { windowManager?.removeView(v) } }
        edgeViews.clear()
    }

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun systemDimen(name: String, fallbackDp: Int): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dpToPx(fallbackDp)
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        teardownViews()
        windowManager = null
        super.onDestroy()
    }

    companion object {
        /** One swipe = one fire, even if the finger wobbles back over the threshold. */
        private const val SWIPE_GAP_MS = 800L

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
