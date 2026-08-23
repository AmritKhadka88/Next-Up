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

        fun bind(day: CalendarDay, onDayClick: (CalendarDay) -> Unit, onDayDoubleTap: (CalendarDay) -> Unit) {
            if (day.isPlaceholder || day.date == null) {
                dayNumber.text = ""
                itemView.setBackgroundColor(Color.TRANSPARENT)
                itemView.isClickable = false
                itemView.setOnTouchListener(null)
                return
            }

            dayNumber.text = day.date.dayOfMonth.toString()
            itemView.isClickable = true

            val settings = SettingsRepository(itemView.context)
            val today = LocalDate.now()
            when {
                day.hasTask -> itemView.setBackgroundColor(Color.parseColor("#FFCDD2"))
                day.isInTillRange -> itemView.setBackgroundColor(settings.highlightColor)
                day.date == today -> itemView.setBackgroundColor(Color.parseColor("#E3F2FD"))
                else -> itemView.setBackgroundColor(Color.TRANSPARENT)
            }

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
