package com.nextup.app.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nextup.app.R
import com.nextup.app.data.TaskDatabase
import com.nextup.app.settings.SettingsRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TaskListFragment : Fragment(R.layout.fragment_task_list) {

    private var isDaily = false
    private lateinit var adapter: TaskAdapter

    companion object {
        private const val ARG_DAILY = "arg_daily"

        fun newInstance(daily: Boolean): TaskListFragment {
            val fragment = TaskListFragment()
            fragment.arguments = Bundle().apply { putBoolean(ARG_DAILY, daily) }
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isDaily = arguments?.getBoolean(ARG_DAILY) ?: false

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewTasks)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val dao = TaskDatabase.getInstance(requireContext()).taskDao()

        adapter = TaskAdapter(
            onCompletedChanged = { task, completed ->
                lifecycleScope.launch {
                    dao.setCompleted(task.id, completed)
                    if (completed && task.hasAlarm) {
                        com.nextup.app.alarm.AlarmScheduler.cancel(requireContext(), task)
                    }
                    com.nextup.app.widget.WidgetUpdater.refreshAll(requireContext())
                }
            },
            onTaskLongClick = { task ->
                EditTaskDialog.newInstance(task.id).show(parentFragmentManager, "edit_task")
            }
        )
        recyclerView.adapter = adapter

        val taskFlow = if (isDaily) dao.getDailyTasks() else dao.getMainTasks()

        lifecycleScope.launch {
            combine(
                taskFlow,
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
                adapter.submitList(buildGroupedList(filtered))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-applied on every resume (not just once at creation) so a color/font change made
        // in Settings shows up immediately when returning to this tab, instead of only
        // taking effect the next time the fragment is recreated from scratch.
        val settings = SettingsRepository(requireContext())
        view?.setBackgroundColor(settings.backgroundColor)
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }
}
