package com.nextup.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nextup.app.R
import com.nextup.app.data.Task
import com.nextup.app.data.TaskDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class CalendarFragment : Fragment(R.layout.fragment_calendar) {

    private var currentMonth: YearMonth = YearMonth.now()
    private lateinit var adapter: CalendarDayAdapter
    private lateinit var monthLabel: TextView
    private var tasksInMonth: List<Task> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        monthLabel = view.findViewById(R.id.textMonthLabel)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewCalendar)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 7)

        adapter = CalendarDayAdapter { day -> onDayClicked(day) }
        recyclerView.adapter = adapter

        view.findViewById<Button>(R.id.buttonPrevMonth).setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            loadMonth()
        }
        view.findViewById<Button>(R.id.buttonNextMonth).setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            loadMonth()
        }

        loadMonth()
    }

    private fun loadMonth() {
        monthLabel.text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}"

        val zone = ZoneId.systemDefault()
        val monthStart = currentMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = currentMonth.atEndOfMonth().atStartOfDay(zone).toInstant().toEpochMilli()

        val dao = TaskDatabase.getInstance(requireContext()).taskDao()
        lifecycleScope.launch {
            dao.getTasksInRange(monthStart, monthEnd).collectLatest { tasks ->
                tasksInMonth = tasks
                buildGrid()
            }
        }
    }

    private fun buildGrid() {
        val zone = ZoneId.systemDefault()
        val firstDay = currentMonth.atDay(1)
        val daysInMonth = currentMonth.lengthOfMonth()
        // Sunday = 0 offset convention, matching DateTimeExtractor's weekday indexing
        val startOffset = firstDay.dayOfWeek.value % 7

        val days = mutableListOf<CalendarDay>()
        repeat(startOffset) { days.add(CalendarDay(date = null, hasTask = false, isInTillRange = false, isPlaceholder = true)) }

        for (dayNum in 1..daysInMonth) {
            val date = currentMonth.atDay(dayNum)
            val dayStartMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()

            val hasTaskOnDay = tasksInMonth.any { it.dueDate == dayStartMillis }
            val isInTillRange = tasksInMonth.any { task ->
                task.deadlineType == com.nextup.app.data.DeadlineType.TILL &&
                    task.startHighlightDate != null &&
                    dayStartMillis in task.startHighlightDate!!..task.dueDate
            }

            days.add(CalendarDay(date, hasTaskOnDay, isInTillRange))
        }

        adapter.submitList(days)
    }

    private fun onDayClicked(day: CalendarDay) {
        // Hook point: open a bottom sheet or dialog listing tasks for this day.
        // Left as an extension point — wire to TaskDao.getTasksForDay(day.date millis).
    }
}

data class CalendarDay(
    val date: LocalDate?,
    val hasTask: Boolean,
    val isInTillRange: Boolean,
    val isPlaceholder: Boolean = false
)
