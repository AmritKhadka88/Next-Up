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
    val ambiguousPriorityPhrase: String? = null,
    val ambiguousSuggestedPriority: Priority? = null
) {
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
        """\$\s?(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)""" +
            """|(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)\s?\$""" +
            """|(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)\s?(?:dollars|bucks)""",
        RegexOption.IGNORE_CASE
    )
    private val recipientRegex = Regex("""\bto\s+([A-Z][a-zA-Z]*|\p{Ll}+)\b""")

    private val shortPriorityPrefixRegex = Regex("""^\s*(h|m|n)[.:]\s*""", RegexOption.IGNORE_CASE)
    private val startPhraseRegex = Regex(
        """^\s*(high|medium|normal|low)\s*(?:priority)?\s*(?:task)?\s*[:.\-]?\s*""",
        RegexOption.IGNORE_CASE
    )
    private val anywherePhraseRegex = Regex(
        """\b(high|medium|normal|low)\s*priority(?:\s*task)?\b""",
        RegexOption.IGNORE_CASE
    )

    // NOTE: no trailing \b here — patterns ending in ":" never satisfy a following \b
    // since ":" and whitespace are both non-word characters, so the old version silently
    // never matched "daily:" / "d:" at all.
    private val dailyRegex = Regex(
        """\b(?:daily\s*:|d\s*:|do this daily|daily task|repeat daily|every day)""",
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

        // 1. priority
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

        // 2. daily task keywords
        val dailyMatch = dailyRegex.find(text)
        val isDailyTask = dailyMatch != null
        if (dailyMatch != null) text = text.removeRange(dailyMatch.range)

        // 3. alarm keyword
        val hasAlarm = alarmRegex.containsMatchIn(text)
        text = alarmRegex.replace(text, "")

        // 4. amount — detected for the Task's data field, but deliberately NOT removed from
        //    the visible title anymore. Silently stripping "$1000" out of what the user typed
        //    made it look like the amount had vanished from the task.
        val amountMatch = amountRegex.find(text)
        val amount = amountMatch?.let {
            listOf(it.groupValues[1], it.groupValues[2], it.groupValues[3])
                .firstOrNull { g -> g.isNotBlank() }
                ?.replace(",", "")
                ?.toDoubleOrNull()
        }

        // 5. recipient — still removed from the title, since "to Amrit" reads as redundant
        //    once it's captured as structured data.
        val recipientMatch = if (amount != null) recipientRegex.find(text) else null
        val recipient = recipientMatch?.groupValues?.get(1)
        if (recipientMatch != null) text = text.removeRange(recipientMatch.range)

        // 6. ambiguous mid-sentence priority phrase
        var ambiguousPhrase: String? = null
        var ambiguousPriority: Priority? = null
        if (!explicitPriorityMatched) {
            val midMatch = anywherePhraseRegex.find(text)
            if (midMatch != null) {
                ambiguousPhrase = midMatch.value
                ambiguousPriority = wordToPriority(midMatch.groupValues[1])
            }
        }

        // 7. check for till/by keywords BEFORE deciding what they mean —
        //    they only count as a deadline signal if a real date or time follows.
        val hasTillWord = tillRegex.containsMatchIn(text)
        val hasByWord = byRegex.containsMatchIn(text)

        // 8. normalize number words, then extract date + time
        val normalized = DateTimeExtractor.normalizeNumberWords(text)
        val extractedDate = DateTimeExtractor.extractDate(normalized)
        val extractedTime = DateTimeExtractor.extractTime(normalized)

        // 9. now decide the real deadline type: "till" only counts if a date/time was actually found,
        //    otherwise it was just the ordinary word "till" and should stay as plain text.
        val hasDateOrTime = extractedDate != null || extractedTime != null
        val deadlineType = when {
            hasTillWord && hasDateOrTime -> DeadlineType.TILL
            hasByWord -> DeadlineType.BY
            else -> DeadlineType.ON
        }

        // 10. strip matched substrings from the title
        var title = text
        // "by"/"on" are harmless filler words either way, safe to always strip
        title = Regex("""\b(by|on)\b""", RegexOption.IGNORE_CASE).replace(title, "")
        // "till"/"until" only stripped when they genuinely meant something — otherwise keep as literal text
        if (deadlineType == DeadlineType.TILL) {
            title = Regex("""\b(till|until)\b""", RegexOption.IGNORE_CASE).replace(title, "")
        }
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
