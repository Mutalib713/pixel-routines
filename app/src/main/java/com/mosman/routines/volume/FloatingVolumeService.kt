package com.mosman.routines.volume

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.mosman.routines.Permissions
import com.mosman.routines.R
import kotlin.math.abs

/**
 * Foreground / overlay service for the movable Floating Volume Button.
 * Uses TYPE_APPLICATION_OVERLAY to stay accessible across applications,
 * supports dragging, edge snapping, size and opacity configuration,
 * and opens the system volume panel on tap.
 */
class FloatingVolumeService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Permissions.hasOverlay(this)) {
            stopSelf()
            return
        }
        setupFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Permissions.hasOverlay(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        updateAppearance()
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingButton() {
        windowManager = getSystemService(WindowManager::class.java) ?: return
        val settings = VolumePanelStore.load(this)

        val sizePx = dpToPx(settings.floatingButtonSize.dpSize)

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val displayMetrics = resources.displayMetrics
            x = if (settings.floatingButtonX >= 0) settings.floatingButtonX else displayMetrics.widthPixels - sizePx - dpToPx(16)
            y = if (settings.floatingButtonY >= 0) settings.floatingButtonY else displayMetrics.heightPixels / 3
        }
        layoutParams = params

        // Container view
        val container = FrameLayout(this).apply {
            alpha = settings.floatingButtonOpacity
            elevation = dpToPx(6).toFloat()

            // Material pill/circle background
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF2563EB.toInt()) // Modern Material Accent Blue
                setStroke(dpToPx(1), 0x33FFFFFF)
            }
            background = bg
        }

        // Volume Icon
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_stat)
            setColorFilter(0xFFFFFFFF.toInt())
            val pad = dpToPx(12)
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            )
        }
        container.addView(icon)

        // Drag & Click handler
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var touchStartTime = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchStartTime = System.currentTimeMillis()
                        container.scaleX = 0.92f
                        container.scaleY = 0.92f
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        params.x = initialX + dx
                        params.y = initialY + dy
                        clampPosition(params)
                        runCatching { windowManager?.updateViewLayout(container, params) }
                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        container.scaleX = 1.0f
                        container.scaleY = 1.0f
                        val diffX = abs(event.rawX - initialTouchX)
                        val diffY = abs(event.rawY - initialTouchY)
                        val elapsed = System.currentTimeMillis() - touchStartTime

                        if (diffX < dpToPx(8) && diffY < dpToPx(8) && elapsed < 350L) {
                            // Tap event: Show volume panel
                            VolumePanelController.show(this@FloatingVolumeService)
                        } else {
                            // Drag ended: Save last position
                            clampPosition(params)
                            runCatching { windowManager?.updateViewLayout(container, params) }
                            VolumePanelStore.update(this@FloatingVolumeService) {
                                it.copy(floatingButtonX = params.x, floatingButtonY = params.y)
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        floatingView = container
        runCatching { windowManager?.addView(container, params) }
    }

    private fun updateAppearance() {
        val container = floatingView ?: return
        val params = layoutParams ?: return
        val settings = VolumePanelStore.load(this)

        val sizePx = dpToPx(settings.floatingButtonSize.dpSize)
        params.width = sizePx
        params.height = sizePx
        container.alpha = settings.floatingButtonOpacity
        clampPosition(params)

        runCatching { windowManager?.updateViewLayout(container, params) }
    }

    private fun clampPosition(params: WindowManager.LayoutParams) {
        val dm = resources.displayMetrics
        val sizePx = params.width
        val maxX = dm.widthPixels - sizePx
        val maxY = dm.heightPixels - sizePx
        params.x = params.x.coerceIn(0, maxOf(0, maxX))
        params.y = params.y.coerceIn(dpToPx(24), maxOf(dpToPx(24), maxY))
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        floatingView?.let {
            runCatching { windowManager?.removeView(it) }
        }
        floatingView = null
        windowManager = null
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, FloatingVolumeService::class.java)
            runCatching { ctx.startService(i) }
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, FloatingVolumeService::class.java)
            runCatching { ctx.stopService(i) }
        }
    }
}
