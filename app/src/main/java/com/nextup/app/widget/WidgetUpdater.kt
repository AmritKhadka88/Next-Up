package com.nextup.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetUpdater {

    /** Call this after any insert/update/delete/complete-toggle so the home screen widget stays in sync. */
    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, QuickAddWidgetProvider::class.java))
        for (id in ids) {
            QuickAddWidgetProvider.updateWidget(context, manager, id)
        }
    }
}
