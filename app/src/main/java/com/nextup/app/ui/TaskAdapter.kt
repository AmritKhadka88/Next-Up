package com.nextup.app.ui

import android.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.Locale

class TaskAdapter(
    private val onCompletedChanged: (Task, Boolean) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position), onCompletedChanged)
    }

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkboxCompleted)
        private val title: TextView = itemView.findViewById(R.id.textTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.textSubtitle)
        private val priorityIndicator: View = itemView.findViewById(R.id.viewPriorityIndicator)

        private val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        fun bind(task: Task, onCompletedChanged: (Task, Boolean) -> Unit) {
            title.text = task.title
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = task.isCompleted
            checkBox.setOnCheckedChangeListener { _, checked -> onCompletedChanged(task, checked) }

            val dateStr = dateFormat.format(task.dueDate)
            val timeStr = task.dueTime?.let { " • ${timeFormat.format(it)}" } ?: ""
            val alarmStr = if (task.hasAlarm) " ⏰" else ""
            subtitle.text = "$dateStr$timeStr$alarmStr"

            priorityIndicator.setBackgroundColor(
                when (task.priority) {
                    Priority.HIGH -> Color.parseColor("#E53935")
                    Priority.MEDIUM -> Color.parseColor("#FB8C00")
                    Priority.NORMAL -> Color.parseColor("#BDBDBD")
                }
            )
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Task>() {
            override fun areItemsTheSame(old: Task, new: Task) = old.id == new.id
            override fun areContentsTheSame(old: Task, new: Task) = old == new
        }
    }
}
