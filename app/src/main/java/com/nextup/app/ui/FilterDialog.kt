package com.nextup.app.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.nextup.app.data.Priority

object FilterDialog {

    fun show(context: Context) {
        val options = arrayOf("High priority", "Medium priority", "Normal priority", "Show completed tasks")
        val currentPriorities = TaskFilterState.allowedPriorities.value
        val checked = booleanArrayOf(
            Priority.HIGH in currentPriorities,
            Priority.MEDIUM in currentPriorities,
            Priority.NORMAL in currentPriorities,
            TaskFilterState.showCompleted.value
        )

        AlertDialog.Builder(context)
            .setTitle("Filter tasks")
            .setMultiChoiceItems(options, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Apply") { _, _ ->
                val newPriorities = mutableSetOf<Priority>()
                if (checked[0]) newPriorities.add(Priority.HIGH)
                if (checked[1]) newPriorities.add(Priority.MEDIUM)
                if (checked[2]) newPriorities.add(Priority.NORMAL)
                TaskFilterState.allowedPriorities.value = newPriorities
                TaskFilterState.showCompleted.value = checked[3]
            }
            .setNeutralButton("Reset") { _, _ ->
                TaskFilterState.reset()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
