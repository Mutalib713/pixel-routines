package com.mosman.routines

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home-screen widget: shows how many routines are active and opens the app. */
class RoutinesWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(ctx, mgr, it) }
    }

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, RoutinesWidget::class.java))
            ids.forEach { render(ctx, mgr, it) }
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val routines = Store.load(ctx)
            val active = routines.count { it.enabled }
            val rv = RemoteViews(ctx.packageName, R.layout.widget_routines)
            rv.setTextViewText(R.id.widget_title, "Pixel Routines")
            rv.setTextViewText(R.id.widget_sub,
                if (routines.isEmpty()) "Tap to create one"
                else "$active active · ${routines.size} total")

            val open = PendingIntent.getActivity(ctx, 1,
                Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            rv.setOnClickPendingIntent(R.id.widget_root, open)
            mgr.updateAppWidget(id, rv)
        }
    }
}
