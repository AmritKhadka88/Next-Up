package com.nextup.app.ui

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nextup.app.R
import com.nextup.app.data.SourceType
import com.nextup.app.data.TaskDatabase
import com.nextup.app.parser.ParsedTaskResult
import com.nextup.app.parser.TaskParser
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * Compact quick-add sheet: type, speak, or write a single line and it is parsed + saved.
 * Can be pre-targeted at a specific date (double-tap on a calendar day), or launched
 * from the home screen widget (in which case dismissing it also finishes the transparent
 * host activity).
 */
class QuickAddBottomSheet : BottomSheetDialogFragment() {

    private var prefillDate: LocalDate? = null
    private lateinit var editTextRef: EditText
    private lateinit var keywordHighlighter: com.nextup.app.parser.KeywordHighlighter

    // Delegates to the system's speech recognizer app (Google app, typically) — this avoids
    // needing our own RECORD_AUDIO runtime permission, since the recognition itself happens
    // in that app, and we only receive back the transcribed text.
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val transcript = matches?.firstOrNull()
            if (!transcript.isNullOrBlank()) {
                val existing = editTextRef.text.toString()
                val combined = if (existing.isBlank()) transcript else "$existing $transcript"
                editTextRef.setText(combined)
                editTextRef.setSelection(combined.length)
            }
        }
    }

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
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.getString(ARG_PREFILL_DATE)?.let {
            prefillDate = LocalDate.parse(it)
        }

        val editText = view.findViewById<EditText>(R.id.editTextQuickAdd)
        editTextRef = editText
        val sendButton = view.findViewById<ImageButton>(R.id.buttonSend)
        val micButton = view.findViewById<ImageButton>(R.id.buttonMic)

        editText.requestFocus()
        editText.post {
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }

        keywordHighlighter = com.nextup.app.parser.KeywordHighlighter(editText)
        keywordHighlighter.attach()

        sendButton.setOnClickListener {
            val input = editText.text.toString().trim()
            if (input.isNotBlank()) {
                handleParsedInput(input, SourceType.MANUAL)
            }
        }

        micButton.setOnClickListener { startVoiceInput() }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your task...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No speech recognizer available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleParsedInput(input: String, source: SourceType) {
        val excluded = com.nextup.app.parser.LearnedWordsRepository(requireContext()).getExcludedPhrases() +
            keywordHighlighter.getSessionExclusions()
        val rules = com.nextup.app.parser.RuleLibraryRepository(requireContext()).getRules()
        val fillers = com.nextup.app.parser.FillerPhraseRepository(requireContext()).getLearnedPhrases().map { it.phrase }
        val parsed = TaskParser.parse(input, excluded, rules, fillers)

        if (parsed.possiblyMissingDateTime) {
            showTeachPrompt(input, parsed, source)
        } else if (parsed.ambiguousPriorityPhrase != null) {
            showAmbiguousPriorityDialog(parsed, source)
        } else {
            saveTask(parsed, source)
        }
    }

    /**
     * The wording had temporal-sounding words ("now", "hour", "after", etc.) but nothing
     * actually resolved to a date/time — this is the "ask the user" learning path: offer to
     * teach a rule right here rather than silently saving a task that's probably missing
     * the deadline the person meant to give it.
     */
    private fun showTeachPrompt(originalInput: String, parsed: ParsedTaskResult, source: SourceType) {
        val ruleInput = android.widget.EditText(requireContext()).apply {
            hint = "e.g. two days from now = today + 2"
            setPadding(40, 24, 40, 24)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Couldn't find a date/time")
            .setMessage("This doesn't look like it has a date or time I recognize. If it does, teach me what it means (phrase = meaning), or just save it as-is.")
            .setView(ruleInput)
            .setPositiveButton("Teach & save") { _, _ ->
                val ruleText = ruleInput.text.toString()
                val parts = ruleText.split("=", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    val repo = com.nextup.app.parser.RuleLibraryRepository(requireContext())
                    repo.addRule(parts[0].trim(), parts[1].trim())
                    val excluded = com.nextup.app.parser.LearnedWordsRepository(requireContext()).getExcludedPhrases() +
                        keywordHighlighter.getSessionExclusions()
                    val fillers = com.nextup.app.parser.FillerPhraseRepository(requireContext()).getLearnedPhrases().map { it.phrase }
                    val reparsed = TaskParser.parse(originalInput, excluded, repo.getRules(), fillers)
                    saveTask(reparsed, source)
                } else {
                    saveTask(parsed, source)
                }
            }
            .setNegativeButton("Just save it") { _, _ ->
                saveTask(parsed, source)
            }
            .setCancelable(false)
            .show()
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

            parsed.usedRulePhrase?.let {
                com.nextup.app.parser.RuleLibraryRepository(requireContext()).markUsed(it)
            }
            maybeRunStalenessPrune()

            // TODO: if task.hasAlarm, schedule via AlarmManager here using task.dueTime/dueDate.
            dismiss()
        }
    }

    /** Runs the 4-month staleness prune at most once per day, not on every single save. */
    private fun maybeRunStalenessPrune() {
        val prefs = requireContext().getSharedPreferences("nextup_maintenance", android.content.Context.MODE_PRIVATE)
        val lastRun = prefs.getLong("last_prune_check", 0L)
        val now = System.currentTimeMillis()
        if (now - lastRun < 24L * 60 * 60 * 1000) return

        com.nextup.app.parser.RuleLibraryRepository(requireContext()).pruneStale()
        com.nextup.app.parser.LearnedWordsRepository(requireContext()).pruneStale()
        prefs.edit().putLong("last_prune_check", now).apply()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (arguments?.getBoolean(ARG_FINISH_HOST_ON_DISMISS) == true) {
            activity?.finish()
        }
    }
}
