package com.nextup.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViewsService

class QuickAddWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return QuickAddRemoteViewsFactory(applicationContext, widgetId)
    }
}
