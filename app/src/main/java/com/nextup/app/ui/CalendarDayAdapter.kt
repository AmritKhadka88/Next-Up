package com.nextup.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.nextup.app.R
import java.time.LocalDate

class CalendarDayAdapter(
    private val onDayClick: (CalendarDay) -> Unit
) : ListAdapter<CalendarDay?, CalendarDayAdapter.DayViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(getItem(position), onDayClick)
    }

    class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dayNumber: TextView = itemView.findViewById(R.id.textDayNumber)

        fun bind(day: CalendarDay?, onDayClick: (CalendarDay) -> Unit) {
            if (day == null) {
                dayNumber.text = ""
                itemView.setBackgroundColor(Color.TRANSPARENT)
                itemView.isClickable = false
                return
            }

            dayNumber.text = day.date.dayOfMonth.toString()
            itemView.isClickable = true

            val today = LocalDate.now()
            when {
                day.hasTask -> itemView.setBackgroundColor(Color.parseColor("#FFCDD2")) // task on this exact day
                day.isInTillRange -> itemView.setBackgroundColor(Color.parseColor("#FFF9C4")) // within a TILL deadline range
                day.date == today -> itemView.setBackgroundColor(Color.parseColor("#E3F2FD")) // today marker
                else -> itemView.setBackgroundColor(Color.TRANSPARENT)
            }

            itemView.setOnClickListener { onDayClick(day) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CalendarDay?>() {
            override fun areItemsTheSame(old: CalendarDay?, new: CalendarDay?) = old?.date == new?.date
            override fun areContentsTheSame(old: CalendarDay?, new: CalendarDay?) = old == new
        }
    }
}
