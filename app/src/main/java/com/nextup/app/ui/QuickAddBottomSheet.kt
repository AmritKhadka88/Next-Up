package com.nextup.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nextup.app.R
import com.nextup.app.data.SourceType
import com.nextup.app.data.TaskDatabase
import com.nextup.app.parser.TaskParser
import kotlinx.coroutines.launch

/**
 * Compact quick-add sheet: type or speak a single line ("h. Give $100 to X till 10 Sept with alarm")
 * and it is parsed + saved instantly, no confirmation step.
 */
class QuickAddBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_quick_add, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editText = view.findViewById<EditText>(R.id.editTextQuickAdd)
        val sendButton = view.findViewById<ImageButton>(R.id.buttonSend)
        val micButton = view.findViewById<ImageButton>(R.id.buttonMic)

        editText.requestFocus()

        sendButton.setOnClickListener {
            val input = editText.text.toString().trim()
            if (input.isNotBlank()) {
                saveTask(input, SourceType.MANUAL)
            }
        }

        // Speech input hook: wire this to SpeechRecognizer / RecognizerIntent.
        // On final transcript, call saveTask(transcript, SourceType.SPEECH) directly —
        // per the "instant save" decision, no preview step before committing.
        micButton.setOnClickListener {
            Toast.makeText(requireContext(), "Wire SpeechRecognizer here", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveTask(input: String, source: SourceType) {
        val parsed = TaskParser.parse(input)
        val task = parsed.toTask(isDailyTask = false, source = source)

        lifecycleScope.launch {
            TaskDatabase.getInstance(requireContext()).taskDao().insert(task)
            // TODO: if task.hasAlarm, schedule via AlarmManager here using task.dueTime/dueDate.
            dismiss()
        }
    }
}
