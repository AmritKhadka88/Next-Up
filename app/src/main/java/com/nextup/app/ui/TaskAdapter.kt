package com.nextup.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nextup.app.R
import com.nextup.app.data.Priority
import com.nextup.app.data.Task
import com.nextup.app.settings.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Locale

private const val TYPE_TASK = 0
private const val TYPE_DIVIDER = 1

class TaskAdapter(
    private val onCompletedChanged: (Task, Boolean) -> Unit,
    private val onTaskLongClick: (Task) -> Unit
) : ListAdapter<TaskListItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TaskListItem.TaskRow -> TYPE_TASK
        is TaskListItem.Divider -> TYPE_DIVIDER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_DIVIDER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_priority_divider, parent, false)
            DividerViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
            TaskViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is TaskViewHolder && item is TaskListItem.TaskRow) {
            holder.bind(item.task, onCompletedChanged, onTaskLongClick)
        }
    }

    class DividerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkboxCompleted)
        private val title: TextView = itemView.findViewById(R.id.textTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.textSubtitle)
        private val priorityIndicator: View = itemView.findViewById(R.id.viewPriorityIndicator)

        private val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        fun bind(task: Task, onCompletedChanged: (Task, Boolean) -> Unit, onTaskLongClick: (Task) -> Unit) {
            val settings = SettingsRepository(itemView.context)

            title.text = task.title
            title.setTextColor(settings.textColor)
            title.typeface = settings.fontOption.toTypeface()

            subtitle.typeface = settings.fontOption.toTypeface()

            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = task.isCompleted
            checkBox.setOnCheckedChangeListener { _, checked -> onCompletedChanged(task, checked) }

            itemView.setOnLongClickListener {
                onTaskLongClick(task)
                true
            }

            val dateStr = dateFormat.format(task.dueDate)
            val timeStr = task.dueTime?.let { " • ${timeFormat.format(it)}" } ?: ""
            val alarmStr = if (task.hasAlarm) " ⏰" else ""
            val dailyStr = if (task.isDailyTask) " 🔁" else ""
            subtitle.text = "$dateStr$timeStr$alarmStr$dailyStr"

            priorityIndicator.setBackgroundColor(
                when (task.priority) {
                    Priority.HIGH -> settings.highPriorityColor
                    Priority.MEDIUM -> settings.mediumPriorityColor
                    Priority.NORMAL -> settings.normalPriorityColor
                }
            )
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TaskListItem>() {
            override fun areItemsTheSame(old: TaskListItem, new: TaskListItem): Boolean {
                if (old is TaskListItem.Divider && new is TaskListItem.Divider) return true
                if (old is TaskListItem.TaskRow && new is TaskListItem.TaskRow) return old.task.id == new.task.id
                return false
            }
            override fun areContentsTheSame(old: TaskListItem, new: TaskListItem): Boolean = old == new
        }
    }
}
