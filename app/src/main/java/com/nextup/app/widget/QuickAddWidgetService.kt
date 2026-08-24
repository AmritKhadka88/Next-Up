package com.nextup.app.widget

import android.content.Intent
import android.widget.RemoteViewsService

class QuickAddWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return QuickAddRemoteViewsFactory(applicationContext)
    }
}
