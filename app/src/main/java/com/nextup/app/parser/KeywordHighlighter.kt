package com.nextup.app.parser

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
 * in a muted yellow while the user types. Double-tapping a highlighted word toggles it
 * in/out of the learned-exceptions library: excluded words show with a strikethrough
 * instead of the yellow fill, and the parser will then treat that exact phrase as plain
 * text everywhere, not just in this one sentence.
 */
class KeywordHighlighter(private val editText: EditText) {

    private val learnedWords = LearnedWordsRepository(editText.context)
    private var currentSpans: List<KeywordSpan> = emptyList()
    private var isApplyingSpans = false

    private val highlightColor = Color.parseColor("#33C9A227") // light, muted dark-yellow

    private val gestureDetector = GestureDetector(editText.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val offset = editText.getOffsetForPosition(e.x, e.y)
            val tapped = currentSpans.firstOrNull { offset in it.start until it.end } ?: return false
            learnedWords.toggle(tapped.matchedText)
            refreshHighlights()
            return true
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

    private fun refreshHighlights() {
        val text = editText.text ?: return
        val excluded = learnedWords.getExcludedPhrases()
        currentSpans = KeywordDetector.detect(text.toString(), excluded)

        isApplyingSpans = true
        // Clear only our own spans before reapplying, so we don't disturb anything else.
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
