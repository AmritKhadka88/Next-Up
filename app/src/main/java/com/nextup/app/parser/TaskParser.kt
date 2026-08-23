package com.nextup.app.parser

import com.nextup.app.data.DeadlineType
import com.nextup.app.data.Priority
import com.nextup.app.data.SourceType
import com.nextup.app.data.Task
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ParsedTaskResult(
    val title: String,
    val priority: Priority,
    val deadlineType: DeadlineType,
    val date: LocalDate?,
    val time: LocalTime?,
    val hasAlarm: Boolean,
    val amount: Double?,
    val recipient: String?,
    val isDailyTask: Boolean,
    /** Non-null when a priority phrase was found mid-sentence rather than at the very start,
     *  meaning the UI should ask the user whether they meant it literally or as a priority tag. */
    val ambiguousPriorityPhrase: String? = null,
    val ambiguousSuggestedPriority: Priority? = null
) {
    /** Converts the parsed result into a Room-ready Task entity. */
    fun toTask(source: SourceType = SourceType.MANUAL, overrideDate: LocalDate? = null): Task {
        val zone = ZoneId.systemDefault()
        val resolvedDate = overrideDate ?: date ?: LocalDate.now(zone)
        val dueDateMillis = resolvedDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val dueTimeMillis = time?.let {
            resolvedDate.atTime(it).atZone(zone).toInstant().toEpochMilli()
        }
        val startHighlight = if (deadlineType == DeadlineType.TILL) {
            LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        } else null

        return Task(
            title = title,
            amount = amount,
            recipient = recipient,
            priority = priority,
            deadlineType = deadlineType,
            dueDate = dueDateMillis,
            dueTime = dueTimeMillis,
            startHighlightDate = startHighlight,
            hasAlarm = hasAlarm,
            isDailyTask = isDailyTask,
            sourceType = source
        )
    }

    /** Returns a copy with the ambiguous phrase resolved: either applied as a priority tag,
     *  or dismissed (kept as literal text, ambiguity cleared). */
    fun resolveAmbiguousPriority(applyAsPriority: Boolean): ParsedTaskResult {
        if (ambiguousPriorityPhrase == null) return this
        return if (applyAsPriority) {
            copy(
                priority = ambiguousSuggestedPriority ?: priority,
                title = title.replace(ambiguousPriorityPhrase, "", ignoreCase = true)
                    .replace(Regex("""\s{2,}"""), " ").trim(' ', ',', '.', '-'),
                ambiguousPriorityPhrase = null,
                ambiguousSuggestedPriority = null
            )
        } else {
            copy(ambiguousPriorityPhrase = null, ambiguousSuggestedPriority = null)
        }
    }
}

object TaskParser {

    private val alarmRegex = Regex("""\s*,?\s*with alarm\b""", RegexOption.IGNORE_CASE)
    private val tillRegex = Regex("""\b(till|until)\b""", RegexOption.IGNORE_CASE)
    private val byRegex = Regex("""\bby\b""", RegexOption.IGNORE_CASE)
    private val amountRegex = Regex(
        """\$\s?(\d+(?:\.\d{1,2})?)""" +
            """|(\d+(?:\.\d{1,2})?)\s?\$""" +
            """|(\d+(?:\.\d{1,2})?)\s?(?:dollars|bucks)""",
        RegexOption.IGNORE_CASE
    )
    private val recipientRegex = Regex("""\bto\s+([A-Z][a-zA-Z]*|\p{Ll}+)\b""")

    // Short-form prefix: "h.", "h:", "m.", "m:", "n.", "n:" at the very start
    private val shortPriorityPrefixRegex = Regex("""^\s*(h|m|n)[.:]\s*""", RegexOption.IGNORE_CASE)

    // Long-form phrase at the very start: "high priority task:", "medium priority:", "low priority", etc.
    private val startPhraseRegex = Regex(
        """^\s*(high|medium|normal|low)\s*(?:priority)?\s*(?:task)?\s*[:.\-]?\s*""",
        RegexOption.IGNORE_CASE
    )

    // Same phrase family, but used to scan anywhere in the text (for the ambiguous mid-sentence case)
    private val anywherePhraseRegex = Regex(
        """\b(high|medium|normal|low)\s*priority(?:\s*task)?\b""",
        RegexOption.IGNORE_CASE
    )

    private val dailyRegex = Regex(
        """\b(d\s*:|daily\s*:|do this daily|daily task|repeat daily|every day)\b""",
        RegexOption.IGNORE_CASE
    )

    private fun wordToPriority(word: String): Priority = when (word.lowercase()) {
        "high" -> Priority.HIGH
        "medium" -> Priority.MEDIUM
        "normal", "low" -> Priority.NORMAL
        else -> Priority.NORMAL
    }

    fun parse(rawInput: String): ParsedTaskResult {
        var text = rawInput.trim()

        // 1. priority — try long-form phrase at the start first, then short-form prefix
        var priority = Priority.NORMAL
        var explicitPriorityMatched = false

        val startPhraseMatch = startPhraseRegex.find(text)
        if (startPhraseMatch != null && startPhraseMatch.groupValues[1].isNotBlank()) {
            priority = wordToPriority(startPhraseMatch.groupValues[1])
            text = text.removeRange(startPhraseMatch.range)
            explicitPriorityMatched = true
        } else {
            val shortMatch = shortPriorityPrefixRegex.find(text)
            if (shortMatch != null) {
                priority = when (shortMatch.groupValues[1].lowercase()) {
                    "h" -> Priority.HIGH
                    "m" -> Priority.MEDIUM
                    else -> Priority.NORMAL
                }
                text = text.removeRange(shortMatch.range)
                explicitPriorityMatched = true
            }
        }

        // 2. daily task keywords — can appear anywhere in the sentence
        val dailyMatch = dailyRegex.find(text)
        val isDailyTask = dailyMatch != null
        if (dailyMatch != null) text = text.removeRange(dailyMatch.range)

        // 3. alarm keyword
        val hasAlarm = alarmRegex.containsMatchIn(text)
        text = alarmRegex.replace(text, "")

        // 4. deadline type (check TILL before BY, since "until" implies range)
        val deadlineType = when {
            tillRegex.containsMatchIn(text) -> DeadlineType.TILL
            byRegex.containsMatchIn(text) -> DeadlineType.BY
            else -> DeadlineType.ON
        }

        // 5. amount
        val amountMatch = amountRegex.find(text)
        val amount = amountMatch?.let {
            listOf(it.groupValues[1], it.groupValues[2], it.groupValues[3])
                .firstOrNull { g -> g.isNotBlank() }
                ?.toDoubleOrNull()
        }
        if (amountMatch != null) text = text.removeRange(amountMatch.range)

        // 6. recipient ("to X") — only meaningful when there's an amount context
        val recipientMatch = if (amount != null) recipientRegex.find(text) else null
        val recipient = recipientMatch?.groupValues?.get(1)
        if (recipientMatch != null) text = text.removeRange(recipientMatch.range)

        // 7. ambiguous mid-sentence priority phrase (e.g. "...keep this as high priority task")
        //    Only checked if we didn't already consume an explicit priority at the very start.
        var ambiguousPhrase: String? = null
        var ambiguousPriority: Priority? = null
        if (!explicitPriorityMatched) {
            val midMatch = anywherePhraseRegex.find(text)
            if (midMatch != null) {
                ambiguousPhrase = midMatch.value
                ambiguousPriority = wordToPriority(midMatch.groupValues[1])
                // Note: text is left untouched here — resolution happens later via resolveAmbiguousPriority()
            }
        }

        // 8. normalize number words, then extract date + time from normalized text
        val normalized = DateTimeExtractor.normalizeNumberWords(text)
        val extractedDate = DateTimeExtractor.extractDate(normalized)
        val extractedTime = DateTimeExtractor.extractTime(normalized)

        // 9. strip matched date/time/deadline-keyword substrings from title
        var title = text
        title = Regex("""\b(till|until|by|on)\b""", RegexOption.IGNORE_CASE).replace(title, "")
        if (extractedDate != null) {
            title = title.replace(Regex(Regex.escape(extractedDate.matchedText), RegexOption.IGNORE_CASE), "")
        }
        if (extractedTime != null) {
            title = title.replace(Regex(Regex.escape(extractedTime.matchedText), RegexOption.IGNORE_CASE), "")
        }
        title = title.replace(Regex("""\s{2,}"""), " ").trim(' ', ',', '.', '-')

        if (title.isBlank()) title = rawInput.trim()

        return ParsedTaskResult(
            title = title,
            priority = priority,
            deadlineType = deadlineType,
            date = extractedDate?.date,
            time = extractedTime?.time,
            hasAlarm = hasAlarm,
            amount = amount,
            recipient = recipient,
            isDailyTask = isDailyTask,
            ambiguousPriorityPhrase = ambiguousPhrase,
            ambiguousSuggestedPriority = ambiguousPriority
        )
    }
}
