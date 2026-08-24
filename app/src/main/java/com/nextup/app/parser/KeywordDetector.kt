package com.nextup.app.parser

data class KeywordSpan(
    val start: Int,
    val end: Int,
    val matchedText: String,
    val isExcluded: Boolean
)

/**
 * Non-destructive scan of raw input text, returning every span that would currently be
 * treated as a keyword by TaskParser (priority markers, daily/alarm keywords, till/by,
 * dates, times, amounts, recipients) — used to drive the live yellow highlighting while typing.
 */
object KeywordDetector {

    fun detect(text: String, excluded: Set<String>): List<KeywordSpan> {
        val spans = mutableListOf<KeywordSpan>()

        fun addIfPresent(range: IntRange?, matched: String) {
            if (range == null) return
            spans.add(KeywordSpan(range.first, range.last + 1, matched, LearnedWordsRepository.normalize(matched) in excluded))
        }

        ParserPatterns.startPhraseRegex.find(text)?.let {
            if (it.groupValues[1].isNotBlank()) addIfPresent(it.range, it.value)
        }
        ParserPatterns.shortPriorityPrefixRegex.find(text)?.let { addIfPresent(it.range, it.value) }
        ParserPatterns.anywherePhraseRegex.find(text)?.let { addIfPresent(it.range, it.value) }
        ParserPatterns.dailyRegex.find(text)?.let { addIfPresent(it.range, it.value) }
        ParserPatterns.alarmRegex.find(text)?.let { addIfPresent(it.range, it.value.trim()) }
        ParserPatterns.tillRegex.find(text)?.let { addIfPresent(it.range, it.value) }
        ParserPatterns.byRegex.find(text)?.let { addIfPresent(it.range, it.value) }
        ParserPatterns.amountRegex.find(text)?.let { addIfPresent(it.range, it.value) }
        ParserPatterns.recipientRegex.find(text)?.let { addIfPresent(it.range, it.value) }

        // Date/time reuse DateTimeExtractor directly so highlighting always agrees with
        // what the parser will actually pick up — no separate/duplicated date logic here.
        val normalized = DateTimeExtractor.normalizeNumberWords(text)
        DateTimeExtractor.extractDate(normalized)?.let { extracted ->
            val idx = text.indexOf(extracted.matchedText, ignoreCase = true)
            if (idx >= 0) addIfPresent(idx..(idx + extracted.matchedText.length - 1), extracted.matchedText)
        }
        DateTimeExtractor.extractTime(normalized)?.let { extracted ->
            val idx = text.indexOf(extracted.matchedText, ignoreCase = true)
            if (idx >= 0) addIfPresent(idx..(idx + extracted.matchedText.length - 1), extracted.matchedText)
        }

        // Drop overlapping spans (keep the first/longest found) so highlighting doesn't
        // double-apply to the same characters.
        val sorted = spans.sortedBy { it.start }
        val result = mutableListOf<KeywordSpan>()
        for (span in sorted) {
            if (result.none { it.start < span.end && span.start < it.end }) {
                result.add(span)
            }
        }
        return result
    }
}
