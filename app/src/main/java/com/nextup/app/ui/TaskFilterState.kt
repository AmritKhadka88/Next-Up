package com.nextup.app.ui

import com.nextup.app.data.Priority
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * App-wide (in-memory only) filter/search state shared between the List, Daily,
 * and Calendar tabs. Not persisted — resets on app restart, which is fine since
 * it's a transient viewing preference rather than data.
 */
object TaskFilterState {
    val showCompleted = MutableStateFlow(true)
    val allowedPriorities = MutableStateFlow(setOf(Priority.HIGH, Priority.MEDIUM, Priority.NORMAL))
    val searchQuery = MutableStateFlow("")

    fun reset() {
        showCompleted.value = true
        allowedPriorities.value = setOf(Priority.HIGH, Priority.MEDIUM, Priority.NORMAL)
        searchQuery.value = ""
    }
}
