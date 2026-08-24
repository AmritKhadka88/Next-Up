package com.nextup.app.ui

import android.graphics.Color
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.nextup.app.R
import com.nextup.app.settings.SettingsRepository
import java.time.LocalDate

class CalendarDayAdapter(
    private val onDayClick: (CalendarDay) -> Unit,
    private val onDayDoubleTap: (CalendarDay) -> Unit
) : ListAdapter<CalendarDay, CalendarDayAdapter.DayViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(getItem(position), onDayClick, onDayDoubleTap)
    }

    class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dayNumber: TextView = itemView.findViewById(R.id.textDayNumber)
        private val highPriorityCircle: View = itemView.findViewById(R.id.imageHighPriorityCircle)
        private val todayCircle: View = itemView.findViewById(R.id.viewTodayCircle)
        private val taskDot: View = itemView.findViewById(R.id.viewTaskDot)

        fun bind(day: CalendarDay, onDayClick: (CalendarDay) -> Unit, onDayDoubleTap: (CalendarDay) -> Unit) {
            if (day.isPlaceholder || day.date == null) {
                dayNumber.text = ""
                highPriorityCircle.visibility = View.GONE
                todayCircle.visibility = View.GONE
                taskDot.visibility = View.GONE
                itemView.setBackgroundColor(Color.TRANSPARENT)
                itemView.isClickable = false
                itemView.setOnTouchListener(null)
                return
            }

            dayNumber.text = day.date.dayOfMonth.toString()
            itemView.isClickable = true

            val settings = SettingsRepository(itemView.context)
            val today = LocalDate.now()
            val isToday = day.date == today

            // Selected day gets a clear grey highlight so it's obvious which date the task
            // list below belongs to. It takes visual priority over the softer TILL-range tint.
            itemView.setBackgroundColor(
                when {
                    day.isSelected -> Color.parseColor("#BDBDBD")
                    day.isInTillRange -> settings.highlightColor
                    else -> Color.TRANSPARENT
                }
            )

            // The hand-drawn circle takes priority visually over the plain today-ring,
            // since a high-priority date is the more important signal.
            highPriorityCircle.visibility = if (day.hasHighPriorityTask) View.VISIBLE else View.GONE
            todayCircle.visibility = if (isToday && !day.hasHighPriorityTask) View.VISIBLE else View.GONE

            // Small dot for an ordinary (non-high-priority) task on this day.
            taskDot.visibility = if (day.hasTask && !day.hasHighPriorityTask) View.VISIBLE else View.GONE

            val gestureDetector = GestureDetector(itemView.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onDayClick(day)
                    return true
                }
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    onDayDoubleTap(day)
                    return true
                }
            })

            itemView.setOnTouchListener { v, event ->
                gestureDetector.onTouchEvent(event)
                v.performClick()
                true
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CalendarDay>() {
            override fun areItemsTheSame(oldItem: CalendarDay, newItem: CalendarDay) =
                oldItem.date == newItem.date && oldItem.isPlaceholder == newItem.isPlaceholder
            override fun areContentsTheSame(oldItem: CalendarDay, newItem: CalendarDay) =
                oldItem == newItem
        }
    }
}
