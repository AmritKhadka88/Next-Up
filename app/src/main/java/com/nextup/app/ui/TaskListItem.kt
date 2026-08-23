package com.nextup.app.ui

import com.nextup.app.data.Task

/**
 * Wraps tasks plus an optional divider so the adapter can render
 * "High priority" tasks in their own block above a divider line,
 * with everything else below (separated only by their priority color tag).
 */
sealed class TaskListItem {
    data class TaskRow(val task: Task) : TaskListItem()
    object Divider : TaskListItem()
}

fun buildGroupedList(tasks: List<Task>): List<TaskListItem> {
    val high = tasks.filter { it.priority == com.nextup.app.data.Priority.HIGH }
    val rest = tasks.filter { it.priority != com.nextup.app.data.Priority.HIGH }

    val result = mutableListOf<TaskListItem>()
    high.forEach { result.add(TaskListItem.TaskRow(it)) }
    if (high.isNotEmpty() && rest.isNotEmpty()) {
        result.add(TaskListItem.Divider)
    }
    rest.forEach { result.add(TaskListItem.TaskRow(it)) }
    return result
}
