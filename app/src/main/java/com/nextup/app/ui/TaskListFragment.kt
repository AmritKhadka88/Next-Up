package com.nextup.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nextup.app.R
import com.nextup.app.data.TaskDatabase
import kotlinx.coroutines.flow.collectLatest
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

        adapter = TaskAdapter { task, completed ->
            lifecycleScope.launch {
                TaskDatabase.getInstance(requireContext()).taskDao().setCompleted(task.id, completed)
            }
        }
        recyclerView.adapter = adapter

        val dao = TaskDatabase.getInstance(requireContext()).taskDao()
        val flow = if (isDaily) dao.getDailyTasks() else dao.getMainTasks()

        lifecycleScope.launch {
            flow.collectLatest { tasks ->
                adapter.submitList(tasks)
            }
        }
    }
}
