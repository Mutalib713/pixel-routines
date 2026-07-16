package com.mosman.routines

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/**
 * Home-screen widget listing every routine with a tap-to-toggle switch, so you can turn a
 * routine on or off without opening the app.
 */
class RoutinesWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(ctx, mgr, it) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_TOGGLE) {
            val id = intent.getLongExtra(EXTRA_ROUTINE_ID, -1L)
            if (id > 0) {
                val r = Store.get(ctx, id)
                if (r != null) Store.setEnabled(ctx, id, !r.enabled)
            }
            refresh(ctx)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.mosman.routines.WIDGET_TOGGLE"
        const val EXTRA_ROUTINE_ID = "routine_id"

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, RoutinesWidget::class.java))
            if (ids.isEmpty()) return
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            ids.forEach { render(ctx, mgr, it) }
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
            val routines = Store.load(ctx)
            val active = routines.count { it.enabled }
            val rv = RemoteViews(ctx.packageName, R.layout.widget_routines)
            rv.setTextViewText(R.id.widget_sub,
                if (routines.isEmpty()) "Tap to create one" else "$active of ${routines.size} on")

            // Header opens the app.
            rv.setOnClickPendingIntent(R.id.widget_header, PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

            // The list itself.
            val svc = Intent(ctx, WidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))   // unique per widget
            }
            rv.setRemoteAdapter(R.id.widget_list, svc)
            rv.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // One template; each row fills in its routine id.
            val toggle = Intent(ctx, RoutinesWidget::class.java).setAction(ACTION_TOGGLE)
            rv.setPendingIntentTemplate(R.id.widget_list, PendingIntent.getBroadcast(
                ctx, 2, toggle, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))

            mgr.updateAppWidget(widgetId, rv)
        }
    }
}

/** Feeds the widget's list. */
class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val ctx: Context) : RemoteViewsFactory {
        private var routines: List<Routine> = emptyList()
        private var activeIds: Set<Long> = emptySet()

        override fun onCreate() {}
        override fun onDataSetChanged() {
            routines = Store.load(ctx)
            activeIds = Store.activeIds(ctx)
        }
        override fun onDestroy() {}
        override fun getCount() = routines.size
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = routines.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds() = true

        override fun getViewAt(position: Int): RemoteViews {
            val r = routines[position]
            val rv = RemoteViews(ctx.packageName, R.layout.widget_row)
            rv.setTextViewText(R.id.row_name, r.name.ifBlank { "Untitled" })
            rv.setTextViewText(R.id.row_state, when {
                !r.enabled -> "Off"
                activeIds.contains(r.id) -> "Running"
                else -> "On"
            })
            rv.setOnClickFillInIntent(R.id.row_root,
                Intent().putExtra(RoutinesWidget.EXTRA_ROUTINE_ID, r.id))
            return rv
        }
    }
}
