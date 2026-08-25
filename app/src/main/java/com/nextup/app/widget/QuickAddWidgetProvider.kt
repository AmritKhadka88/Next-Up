package com.nextup.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews
import com.nextup.app.R
import com.nextup.app.settings.SettingsRepository

class QuickAddWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        // Roughly the footprint of a single home screen icon cell — below this in BOTH
        // dimensions, the widget is treated as "just the add button". Anything resized
        // bigger than this (wider and/or taller) switches to the scrollable task list.
        private const val COMPACT_THRESHOLD_DP = 90

        private fun addButtonPendingIntent(context: Context): PendingIntent {
            val launchIntent = Intent(context, QuickAddWidgetActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, COMPACT_THRESHOLD_DP)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, COMPACT_THRESHOLD_DP)

            val isCompact = minWidth < COMPACT_THRESHOLD_DP && minHeight < COMPACT_THRESHOLD_DP

            val views = if (isCompact) {
                buildCompactViews(context)
            } else {
                buildExpandedViews(context, widgetId)
            }

            appWidgetManager.updateAppWidget(widgetId, views)

            if (!isCompact) {
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, android.R.id.list)
            }
        }

        private fun buildCompactViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)
            views.setOnClickPendingIntent(R.id.widgetAddButton, addButtonPendingIntent(context))
            return views
        }

        private fun buildExpandedViews(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_task_list)
            val settings = SettingsRepository(context)

            // Left-side add button stays present at every expanded size, same as the compact widget.
            views.setOnClickPendingIntent(R.id.widgetAddButtonExpanded, addButtonPendingIntent(context))

            // Tapping anywhere on the task list (a row, the empty-state text, or the
            // background behind them) opens the app itself — separate from the add button.
            val openAppIntent = Intent(context, com.nextup.app.ui.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 1, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(android.R.id.list, openAppPendingIntent)
            views.setOnClickPendingIntent(android.R.id.empty, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widgetCapsuleBackground, openAppPendingIntent)

            // Tint the capsule background image to the user's chosen color + transparency.
            val bgColor = settings.widgetBackgroundColor
            val alpha = settings.widgetBackgroundAlpha
            val tinted = Color.argb(
                alpha,
                Color.red(bgColor),
                Color.green(bgColor),
                Color.blue(bgColor)
            )
            views.setInt(R.id.widgetCapsuleBackground, "setColorFilter", tinted)

            val serviceIntent = Intent(context, QuickAddWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(android.R.id.list, serviceIntent)
            views.setEmptyView(android.R.id.list, android.R.id.empty)

            return views
        }
    }
}
