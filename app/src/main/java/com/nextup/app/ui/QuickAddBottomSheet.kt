package com.nextup.app.ui

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nextup.app.data.SourceType
import com.nextup.app.data.TaskDatabase
import com.nextup.app.R
import com.nextup.app.parser.ParsedTaskResult
import com.nextup.app.parser.TaskParser
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Compact quick-add sheet: type or speak a single line and it is parsed + saved instantly.
 * Can be pre-targeted at a specific date (double-tap on a calendar day), or launched
 * from the home screen widget (in which case dismissing it also finishes the transparent
 * host activity so the widget-launch doesn't leave a lingering blank activity behind).
 */
class QuickAddBottomSheet : BottomSheetDialogFragment() {

    private var prefillDate: LocalDate? = null

    companion object {
        private const val ARG_PREFILL_DATE = "arg_prefill_date"
        private const val ARG_FINISH_HOST_ON_DISMISS = "arg_finish_host_on_dismiss"

        fun newInstanceForDate(date: LocalDate): QuickAddBottomSheet {
            val sheet = QuickAddBottomSheet()
            sheet.arguments = Bundle().apply { putString(ARG_PREFILL_DATE, date.toString()) }
            return sheet
        }

        fun newInstanceForWidget(): QuickAddBottomSheet {
            val sheet = QuickAddBottomSheet()
            sheet.arguments = Bundle().apply { putBoolean(ARG_FINISH_HOST_ON_DISMISS, true) }
            return sheet
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_quick_add, container, false)
    }

    override fun onStart() {
        super.onStart()
        // Ensures the keyboard is forced open even when this sheet is the very first
        // thing shown in a freshly-launched (widget) activity, where a plain requestFocus()
        // is not always enough to trigger the IME.
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.getString(ARG_PREFILL_DATE)?.let {
            prefillDate = LocalDate.parse(it)
        }

        val editText = view.findViewById<EditText>(R.id.editTextQuickAdd)
        val sendButton = view.findViewById<ImageButton>(R.id.buttonSend)
        val micButton = view.findViewById<ImageButton>(R.id.buttonMic)

        editText.requestFocus()
        editText.post {
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }

        sendButton.setOnClickListener {
            val input = editText.text.toString().trim()
            if (input.isNotBlank()) {
                handleParsedInput(input, SourceType.MANUAL)
            }
        }

        // Speech input hook: wire this to SpeechRecognizer / RecognizerIntent.
        micButton.setOnClickListener {
            Toast.makeText(requireContext(), "Wire SpeechRecognizer here", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleParsedInput(input: String, source: SourceType) {
        val parsed = TaskParser.parse(input)

        if (parsed.ambiguousPriorityPhrase != null) {
            showAmbiguousPriorityDialog(parsed, source)
        } else {
            saveTask(parsed, source)
        }
    }

    private fun showAmbiguousPriorityDialog(parsed: ParsedTaskResult, source: SourceType) {
        val phrase = parsed.ambiguousPriorityPhrase ?: return
        val suggested = parsed.ambiguousSuggestedPriority?.name ?: "priority"

        AlertDialog.Builder(requireContext())
            .setTitle("Priority or plain text?")
            .setMessage("Found \"$phrase\" in your task. Should this set it as $suggested priority, or keep it as regular text?")
            .setPositiveButton("Set as $suggested") { _, _ ->
                saveTask(parsed.resolveAmbiguousPriority(applyAsPriority = true), source)
            }
            .setNegativeButton("Keep as text") { _, _ ->
                saveTask(parsed.resolveAmbiguousPriority(applyAsPriority = false), source)
            }
            .setCancelable(false)
            .show()
    }

    private fun saveTask(parsed: ParsedTaskResult, source: SourceType) {
        val task = parsed.toTask(source = source, overrideDate = prefillDate)

        lifecycleScope.launch {
            TaskDatabase.getInstance(requireContext()).taskDao().insert(task)
            com.nextup.app.widget.WidgetUpdater.refreshAll(requireContext())
            // TODO: if task.hasAlarm, schedule via AlarmManager here using task.dueTime/dueDate.
            dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (arguments?.getBoolean(ARG_FINISH_HOST_ON_DISMISS) == true) {
            activity?.finish()
        }
    }
}
