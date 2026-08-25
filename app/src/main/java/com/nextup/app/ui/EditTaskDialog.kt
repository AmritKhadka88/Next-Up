package com.nextup.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nextup.app.R
import com.nextup.app.data.Priority
import com.nextup.app.data.Task
import com.nextup.app.data.TaskDatabase
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Long-press on any task opens this to edit its fields or delete it.
 */
class EditTaskDialog : BottomSheetDialogFragment() {

    private var task: Task? = null
    private var pickedDate: LocalDate = LocalDate.now()
    private var pickedTime: LocalTime? = null

    companion object {
        private const val ARG_TASK_ID = "arg_task_id"

        fun newInstance(taskId: Long): EditTaskDialog {
            val dialog = EditTaskDialog()
            dialog.arguments = Bundle().apply { putLong(ARG_TASK_ID, taskId) }
            return dialog
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_edit_task, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val taskId = arguments?.getLong(ARG_TASK_ID) ?: return
        val zone = ZoneId.systemDefault()

        val titleField = view.findViewById<EditText>(R.id.editTextEditTitle)
        val prioritySpinner = view.findViewById<Spinner>(R.id.spinnerEditPriority)
        val dateButton = view.findViewById<Button>(R.id.buttonEditDate)
        val timeButton = view.findViewById<Button>(R.id.buttonEditTime)
        val dailyCheckbox = view.findViewById<CheckBox>(R.id.checkboxEditDaily)
        val alarmCheckbox = view.findViewById<CheckBox>(R.id.checkboxEditAlarm)
        val deleteButton = view.findViewById<Button>(R.id.buttonDeleteTask)
        val saveButton = view.findViewById<Button>(R.id.buttonSaveTask)

        val priorities = Priority.entries.map { it.name }
        prioritySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, priorities)

        val dao = TaskDatabase.getInstance(requireContext()).taskDao()

        lifecycleScope.launch {
            val loaded = dao.getById(taskId) ?: return@launch
            task = loaded
            titleField.setText(loaded.title)
            prioritySpinner.setSelection(priorities.indexOf(loaded.priority.name))
            dailyCheckbox.isChecked = loaded.isDailyTask
            alarmCheckbox.isChecked = loaded.hasAlarm

            pickedDate = Instant.ofEpochMilli(loaded.dueDate).atZone(zone).toLocalDate()
            pickedTime = loaded.dueTime?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
            updateDateButtonText(dateButton)
            updateTimeButtonText(timeButton)
        }

        dateButton.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                pickedDate = LocalDate.of(y, m + 1, d)
                updateDateButtonText(dateButton)
            }, pickedDate.year, pickedDate.monthValue - 1, pickedDate.dayOfMonth).show()
        }

        timeButton.setOnClickListener {
            val initial = pickedTime ?: LocalTime.now()
            TimePickerDialog(requireContext(), { _, h, min ->
                pickedTime = LocalTime.of(h, min)
                updateTimeButtonText(timeButton)
            }, initial.hour, initial.minute, false).show()
        }

        deleteButton.setOnClickListener {
            val current = task ?: return@setOnClickListener
            lifecycleScope.launch {
                com.nextup.app.alarm.AlarmScheduler.cancel(requireContext(), current)
                dao.delete(current)
                com.nextup.app.widget.WidgetUpdater.refreshAll(requireContext())
                dismiss()
            }
        }

        saveButton.setOnClickListener {
            val current = task ?: return@setOnClickListener
            val newPriority = Priority.valueOf(priorities[prioritySpinner.selectedItemPosition])
            val dueDateMillis = pickedDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val dueTimeMillis = pickedTime?.let { pickedDate.atTime(it).atZone(zone).toInstant().toEpochMilli() }

            val updated = current.copy(
                title = titleField.text.toString().trim().ifBlank { current.title },
                priority = newPriority,
                dueDate = dueDateMillis,
                dueTime = dueTimeMillis,
                isDailyTask = dailyCheckbox.isChecked,
                hasAlarm = alarmCheckbox.isChecked
            )

            lifecycleScope.launch {
                com.nextup.app.alarm.AlarmScheduler.cancel(requireContext(), current)
                dao.update(updated)
                if (updated.hasAlarm) {
                    com.nextup.app.alarm.AlarmScheduler.schedule(requireContext(), updated)
                }
                com.nextup.app.widget.WidgetUpdater.refreshAll(requireContext())
                dismiss()
            }
        }
    }

    private fun updateDateButtonText(button: Button) {
        button.text = "Date: $pickedDate"
    }

    private fun updateTimeButtonText(button: Button) {
        button.text = if (pickedTime != null) "Time: $pickedTime" else "Time: none"
    }
}
