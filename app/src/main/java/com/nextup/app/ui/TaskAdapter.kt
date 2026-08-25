package com.nextup.app.ui

import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

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
        private val daysCaption: TextView = itemView.findViewById(R.id.textDaysCaption)

        private val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        fun bind(task: Task, onCompletedChanged: (Task, Boolean) -> Unit, onTaskLongClick: (Task) -> Unit) {
            val settings = SettingsRepository(itemView.context)
            val zone = ZoneId.systemDefault()

            title.text = task.title
            title.typeface = settings.getTypeface(itemView.context)
            subtitle.typeface = settings.getTypeface(itemView.context)

            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = task.isCompleted
            checkBox.setOnCheckedChangeListener { _, checked -> onCompletedChanged(task, checked) }

            itemView.setOnClickListener {
                checkBox.isChecked = !checkBox.isChecked
            }

            itemView.setOnLongClickListener {
                onTaskLongClick(task)
                true
            }

            if (task.isCompleted) {
                itemView.alpha = 0.5f
                title.setTextColor(Color.GRAY)
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

            val dueLocalDate = Instant.ofEpochMilli(task.dueDate).atZone(zone).toLocalDate()
            val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(zone), dueLocalDate)

            var numberText: String
            var captionText: String
            var isOverdue = false
            var isTodayNoTime = false

            when {
                daysLeft != 0L -> {
                    numberText = daysLeft.toString()
                    captionText = "days remaining"
                    isOverdue = daysLeft < 0
                }
                task.dueTime != null -> {
                    // Today, and a specific time was given — count down in hours, then minutes.
                    val dueDateTime = Instant.ofEpochMilli(task.dueTime).atZone(zone).toLocalDateTime()
                    val now = LocalDateTime.now(zone)
                    val hoursLeft = ChronoUnit.HOURS.between(now, dueDateTime)
                    if (abs(hoursLeft) >= 1) {
                        numberText = hoursLeft.toString()
                        captionText = "hours remaining"
                        isOverdue = hoursLeft < 0
                    } else {
                        val minutesLeft = ChronoUnit.MINUTES.between(now, dueDateTime)
                        numberText = minutesLeft.toString()
                        captionText = "minutes remaining"
                        isOverdue = minutesLeft < 0
                    }
                }
                else -> {
                    // Today, no time attached — just say "Today" instead of a meaningless "0".
                    numberText = "Today"
                    captionText = ""
                    isTodayNoTime = true
                }
            }

            daysRemaining.text = numberText
            daysCaption.text = captionText

            val priorityColor = when (task.priority) {
                Priority.HIGH -> settings.highPriorityColor
                Priority.MEDIUM -> settings.mediumPriorityColor
                Priority.NORMAL -> settings.normalPriorityColor
            }

            daysRemaining.setTextColor(
                when {
                    task.isCompleted -> Color.GRAY
                    isOverdue -> Color.parseColor("#E53935")
                    isTodayNoTime -> priorityColor
                    else -> settings.textColor
                }
            )

            // "Today, date-only" tasks get a priority-colored border around the whole row,
            // since there's no time countdown to visually signal urgency instead.
            if (isTodayNoTime && !task.isCompleted) {
                val density = itemView.resources.displayMetrics.density
                val border = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke((2 * density).toInt(), priorityColor)
                    cornerRadius = 10 * density
                }
                itemView.background = border
            } else {
                itemView.background = null
            }

            priorityIndicator.setBackgroundColor(priorityColor)
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
