package com.nextup.app.parser

import android.app.AlertDialog
import android.graphics.Color
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.StrikethroughSpan
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.EditText

/**
 * Highlights detected keywords (dates, times, priority markers, "till", amounts, etc.)
 * in a muted yellow while the user types.
 *
 * - Double-tap a highlighted word: toggles it to plain text for THIS input only — nothing
 *   is saved, it doesn't affect the permanent dictionary, and reopening the sheet resets it.
 * - Long-press (tap and hold) a highlighted word: offers to remove it from the permanent
 *   learned-exceptions dictionary, with a confirmation dialog — this is the only way to
 *   make an exclusion stick for future tasks too.
 */
class KeywordHighlighter(private val editText: EditText) {

    private val learnedWords = LearnedWordsRepository(editText.context)
    private var currentSpans: List<KeywordSpan> = emptyList()
    private var isApplyingSpans = false

    // Session-only exclusions from double-tap — cleared whenever this highlighter is
    // recreated (i.e. next time the quick-add sheet is opened), never persisted.
    private val sessionExclusions = mutableSetOf<String>()

    private val highlightColor = Color.parseColor("#33C9A227") // light, muted dark-yellow

    private val gestureDetector = GestureDetector(editText.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val offset = editText.getOffsetForPosition(e.x, e.y)
            val tapped = currentSpans.firstOrNull { offset in it.start until it.end } ?: return false
            val normalized = LearnedWordsRepository.normalize(tapped.matchedText)
            if (normalized in sessionExclusions) sessionExclusions.remove(normalized) else sessionExclusions.add(normalized)
            refreshHighlights()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val offset = editText.getOffsetForPosition(e.x, e.y)
            val tapped = currentSpans.firstOrNull { offset in it.start until it.end } ?: return
            showRemoveFromDictionaryDialog(tapped.matchedText)
        }
    })

    fun attach() {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isApplyingSpans) return
                refreshHighlights()
            }
        })

        editText.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false // let normal text editing / cursor placement continue as usual
        }

        refreshHighlights()
    }

    /** Session-only exclusions (from double-tap), to be merged with the permanent dictionary
     *  right before parsing — so "just this once" actually takes effect on save. */
    fun getSessionExclusions(): Set<String> = sessionExclusions.toSet()

    private fun showRemoveFromDictionaryDialog(phrase: String) {
        val alreadyExcluded = learnedWords.isExcluded(phrase)
        val title = if (alreadyExcluded) "Restore \"$phrase\" as a keyword?" else "Remove \"$phrase\" from the dictionary?"
        val message = if (alreadyExcluded) {
            "This will go back to being treated as a keyword everywhere, permanently."
        } else {
            "This will be treated as plain text everywhere from now on, permanently — not just in this task."
        }

        AlertDialog.Builder(editText.context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(if (alreadyExcluded) "Restore" else "Remove") { _, _ ->
                learnedWords.toggle(phrase)
                refreshHighlights()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshHighlights() {
        val text = editText.text ?: return
        val excluded = learnedWords.getExcludedPhrases() + sessionExclusions
        currentSpans = KeywordDetector.detect(text.toString(), excluded)

        isApplyingSpans = true
        text.getSpans(0, text.length, BackgroundColorSpan::class.java).forEach { text.removeSpan(it) }
        text.getSpans(0, text.length, StrikethroughSpan::class.java).forEach { text.removeSpan(it) }

        for (span in currentSpans) {
            if (span.isExcluded) {
                text.setSpan(StrikethroughSpan(), span.start, span.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                text.setSpan(BackgroundColorSpan(highlightColor), span.start, span.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        isApplyingSpans = false
    }
}
