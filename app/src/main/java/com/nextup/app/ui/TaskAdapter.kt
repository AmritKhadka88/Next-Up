package com.nextup.app.ui

import android.graphics.Paint
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
        private val daysRemaining: TextView = itemView.findViewById(R.id.textDaysRemaining)

        private val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        fun bind(task: Task, onCompletedChanged: (Task, Boolean) -> Unit, onTaskLongClick: (Task) -> Unit) {
            val settings = SettingsRepository(itemView.context)

            title.text = task.title
            title.typeface = settings.fontOption.toTypeface()
            subtitle.typeface = settings.fontOption.toTypeface()

            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = task.isCompleted
            checkBox.setOnCheckedChangeListener { _, checked -> onCompletedChanged(task, checked) }

            // Single tap anywhere on the row toggles completion, same as tapping the checkbox directly.
            itemView.setOnClickListener {
                checkBox.isChecked = !checkBox.isChecked
            }

            itemView.setOnLongClickListener {
                onTaskLongClick(task)
                true
            }

            if (task.isCompleted) {
                // Dim + strike through completed tasks so they visually recede.
                itemView.alpha = 0.5f
                title.setTextColor(android.graphics.Color.GRAY)
                title.paintFlags = title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                itemView.alpha = 1f
                title.setTextColor(settings.textColor)
                title.paintFlags = title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            val dateStr = dateFormat.format(task.dueDate)
            val timeStr = task.dueTime?.let { " • ${timeFormat.format(it)}" } ?: ""
            val alarmStr = if (task.hasAlarm) " ⏰" else ""
            val dailyStr = if (task.isDailyTask) " 🔁" else ""
            subtitle.text = "$dateStr$timeStr$alarmStr$dailyStr"

            val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.now(),
                java.time.Instant.ofEpochMilli(task.dueDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            )
            daysRemaining.text = daysLeft.toString()
            daysRemaining.setTextColor(
                when {
                    task.isCompleted -> android.graphics.Color.GRAY
                    daysLeft < 0 -> android.graphics.Color.parseColor("#E53935")
                    daysLeft == 0L -> android.graphics.Color.parseColor("#FB8C00")
                    else -> settings.textColor
                }
            )

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
