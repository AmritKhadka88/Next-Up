package com.nextup.app.widget

import android.content.Context
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.nextup.app.R
import com.nextup.app.data.Task
import com.nextup.app.data.TaskDatabase
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

class QuickAddRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<Task> = emptyList()
    private val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())

    override fun onCreate() {}

    override fun onDataSetChanged() {
        // Called on this factory's own background thread by the widget host — safe to block here.
        tasks = TaskDatabase.getInstance(context).taskDao().getUpcomingTasksSync()
    }

    override fun onDestroy() {
        tasks = emptyList()
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        val task = tasks[position]
        val views = RemoteViews(context.packageName, R.layout.widget_task_item)

        views.setTextViewText(R.id.widgetItemTitle, task.title)
        views.setTextViewText(R.id.widgetItemSubtitle, dateFormat.format(task.dueDate))

        val dueDate = Instant.ofEpochMilli(task.dueDate).atZone(ZoneId.systemDefault()).toLocalDate()
        val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), dueDate)
        views.setTextViewText(R.id.widgetItemDaysRemaining, daysLeft.toString())

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = tasks[position].id
    override fun hasStableIds(): Boolean = true
}
