package com.nextup.app.widget

import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.nextup.app.R
import com.nextup.app.data.Priority
import com.nextup.app.data.Task
import com.nextup.app.data.TaskDatabase
import com.nextup.app.settings.SettingsRepository
import com.nextup.app.settings.WidgetTaskFilter
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
        val settings = SettingsRepository(context)
        val all = TaskDatabase.getInstance(context).taskDao().getUpcomingTasksSync()

        tasks = when (settings.widgetTaskFilter) {
            WidgetTaskFilter.ALL_UPCOMING -> all
            WidgetTaskFilter.HIGH_PRIORITY_ONLY -> all.filter { it.priority == Priority.HIGH }
            WidgetTaskFilter.TODAY_ONLY -> {
                val zone = ZoneId.systemDefault()
                val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
                val tomorrowStart = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                all.filter { it.dueDate in todayStart until tomorrowStart }
            }
        }
    }

    override fun onDestroy() {
        tasks = emptyList()
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        val task = tasks[position]
        val views = RemoteViews(context.packageName, R.layout.widget_task_item)
        val settings = SettingsRepository(context)

        views.setTextViewText(R.id.widgetItemTitle, task.title)
        views.setTextViewText(R.id.widgetItemSubtitle, dateFormat.format(task.dueDate))

        val dueDate = Instant.ofEpochMilli(task.dueDate).atZone(ZoneId.systemDefault()).toLocalDate()
        val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), dueDate)
        views.setTextViewText(R.id.widgetItemDaysRemaining, daysLeft.toString())

        val fontSize = settings.widgetFontSizeSp
        views.setTextViewTextSize(R.id.widgetItemTitle, TypedValue.COMPLEX_UNIT_SP, fontSize)
        views.setTextViewTextSize(R.id.widgetItemSubtitle, TypedValue.COMPLEX_UNIT_SP, (fontSize - 3f).coerceAtLeast(9f))
        views.setTextViewTextSize(R.id.widgetItemDaysRemaining, TypedValue.COMPLEX_UNIT_SP, fontSize + 2f)

        views.setTextColor(R.id.widgetItemTitle, settings.widgetFontColor)
        views.setTextColor(R.id.widgetItemSubtitle, settings.widgetFontColor)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = tasks[position].id
    override fun hasStableIds(): Boolean = true
}
