package com.nextup.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
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
    private var selectedDate: LocalDate = LocalDate.now()

    private lateinit var gridAdapter: CalendarDayAdapter
    private lateinit var dayTaskAdapter: TaskAdapter
    private lateinit var monthLabel: TextView
    private lateinit var selectedDayLabel: TextView
    private var tasksInMonth: List<Task> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        monthLabel = view.findViewById(R.id.textMonthLabel)
        selectedDayLabel = view.findViewById(R.id.textSelectedDayLabel)

        val gridRecycler = view.findViewById<RecyclerView>(R.id.recyclerViewCalendar)
        gridRecycler.layoutManager = GridLayoutManager(requireContext(), 7)
        gridRecycler.isNestedScrollingEnabled = false

        val dayTaskRecycler = view.findViewById<RecyclerView>(R.id.recyclerViewDayTasks)
        dayTaskRecycler.layoutManager = LinearLayoutManager(requireContext())
        dayTaskRecycler.isNestedScrollingEnabled = false

        val dao = TaskDatabase.getInstance(requireContext()).taskDao()

        dayTaskAdapter = TaskAdapter(
            onCompletedChanged = { task, completed ->
                lifecycleScope.launch { dao.setCompleted(task.id, completed) }
            },
            onTaskLongClick = { task ->
                EditTaskDialog.newInstance(task.id).show(parentFragmentManager, "edit_task")
            }
        )
        dayTaskRecycler.adapter = dayTaskAdapter

        gridAdapter = CalendarDayAdapter(
            onDayClick = { day -> if (day.date != null) selectDay(day.date) },
            onDayDoubleTap = { day ->
                if (day.date != null) {
                    QuickAddBottomSheet.newInstanceForDate(day.date).show(parentFragmentManager, "quick_add")
                }
            }
        )
        gridRecycler.adapter = gridAdapter

        view.findViewById<Button>(R.id.buttonPrevMonth).setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            loadMonth()
        }
        view.findViewById<Button>(R.id.buttonNextMonth).setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            loadMonth()
        }

        loadMonth()
        loadTasksForSelectedDay()
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

        gridAdapter.submitList(days)
    }

    private fun selectDay(date: LocalDate) {
        selectedDate = date
        loadTasksForSelectedDay()
    }

    private var dayTaskJob: kotlinx.coroutines.Job? = null

    private fun loadTasksForSelectedDay() {
        selectedDayLabel.text = "Tasks on $selectedDate"
        val zone = ZoneId.systemDefault()
        val dayStart = selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()

        val dao = TaskDatabase.getInstance(requireContext()).taskDao()
        dayTaskJob?.cancel()
        dayTaskJob = lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                dao.getTasksForDay(dayStart),
                TaskFilterState.showCompleted,
                TaskFilterState.allowedPriorities,
                TaskFilterState.searchQuery
            ) { tasks, showCompleted, allowedPriorities, query ->
                tasks.filter { task ->
                    (showCompleted || !task.isCompleted) &&
                        task.priority in allowedPriorities &&
                        (query.isBlank() || task.title.contains(query, ignoreCase = true))
                }
            }.collect { filtered ->
                dayTaskAdapter.submitList(buildGroupedList(filtered))
            }
        }
    }
}

data class CalendarDay(
    val date: LocalDate?,
    val hasTask: Boolean,
    val isInTillRange: Boolean,
    val isPlaceholder: Boolean = false
)
