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
    val recipient: String?
) {
    /** Converts the parsed result into a Room-ready Task entity. */
    fun toTask(isDailyTask: Boolean = false, source: SourceType = SourceType.MANUAL): Task {
        val zone = ZoneId.systemDefault()
        val resolvedDate = date ?: LocalDate.now(zone)
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
    private val priorityPrefixRegex = Regex("""^\s*(h|m)\.\s*""", RegexOption.IGNORE_CASE)

    fun parse(rawInput: String): ParsedTaskResult {
        var text = rawInput.trim()

        // 1. priority prefix
        var priority = Priority.NORMAL
        val prefixMatch = priorityPrefixRegex.find(text)
        if (prefixMatch != null) {
            priority = when (prefixMatch.groupValues[1].lowercase()) {
                "h" -> Priority.HIGH
                "m" -> Priority.MEDIUM
                else -> Priority.NORMAL
            }
            text = text.removeRange(prefixMatch.range)
        }

        // 2. alarm keyword
        val hasAlarm = alarmRegex.containsMatchIn(text)
        text = alarmRegex.replace(text, "")

        // 3. deadline type (check TILL before BY, since "until" implies range)
        val deadlineType = when {
            tillRegex.containsMatchIn(text) -> DeadlineType.TILL
            byRegex.containsMatchIn(text) -> DeadlineType.BY
            else -> DeadlineType.ON
        }

        // 4. amount
        val amountMatch = amountRegex.find(text)
        val amount = amountMatch?.let {
            listOf(it.groupValues[1], it.groupValues[2], it.groupValues[3])
                .firstOrNull { g -> g.isNotBlank() }
                ?.toDoubleOrNull()
        }
        if (amountMatch != null) text = text.removeRange(amountMatch.range)

        // 5. recipient ("to X") — only meaningful when there's an amount context, avoids
        //    misfiring on ordinary phrases like "go to gym"
        val recipientMatch = if (amount != null) recipientRegex.find(text) else null
        val recipient = recipientMatch?.groupValues?.get(1)
        if (recipientMatch != null) text = text.removeRange(recipientMatch.range)

        // 6. normalize number words, then extract date + time from normalized text
        val normalized = DateTimeExtractor.normalizeNumberWords(text)
        val extractedDate = DateTimeExtractor.extractDate(normalized)
        val extractedTime = DateTimeExtractor.extractTime(normalized)

        // 7. strip matched date/time/deadline-keyword substrings from title, using the ORIGINAL text
        //    so we don't leave behind normalized digits the user didn't type.
        var title = text
        title = Regex("""\b(till|until|by|on)\b""", RegexOption.IGNORE_CASE).replace(title, "")
        if (extractedDate != null) {
            title = title.replace(Regex(Regex.escape(extractedDate.matchedText), RegexOption.IGNORE_CASE), "")
        }
        if (extractedTime != null) {
            title = title.replace(Regex(Regex.escape(extractedTime.matchedText), RegexOption.IGNORE_CASE), "")
        }
        // also strip word-number forms of any digits we consumed (best-effort cleanup)
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
            recipient = recipient
        )
    }
}
