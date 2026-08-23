package com.nextup.app.ui

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog

object SearchDialog {

    fun show(context: Context) {
        val input = EditText(context).apply {
            hint = "Search task titles..."
            setText(TaskFilterState.searchQuery.value)
            setSelection(text.length)
        }

        AlertDialog.Builder(context)
            .setTitle("Search tasks")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                TaskFilterState.searchQuery.value = input.text.toString().trim()
            }
            .setNeutralButton("Clear") { _, _ ->
                TaskFilterState.searchQuery.value = ""
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
