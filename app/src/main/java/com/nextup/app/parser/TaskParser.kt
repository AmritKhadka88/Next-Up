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
    val ambiguousSuggestedPriority: Priority? = null,
    /** True when nothing in the text looked like a date/time even though the wording
     *  suggests the user meant to specify one — a signal the UI can use to offer teaching a rule. */
    val possiblyMissingDateTime: Boolean = false
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

    // Heuristic trigger words: if any of these show up but no date/time got extracted,
    // the sentence probably meant to specify a time in wording the app doesn't know yet.
    private val temporalHintRegex = Regex(
        """\b(now|today|tomorrow|tonight|later|next|after|before|hour|minute|second|day|week|month|year|morning|evening|night|noon|midnight|am|pm)\b""",
        RegexOption.IGNORE_CASE
    )

    private fun wordToPriority(word: String): Priority = when (word.lowercase()) {
        "high" -> Priority.HIGH
        "medium" -> Priority.MEDIUM
        "normal", "low" -> Priority.NORMAL
        else -> Priority.NORMAL
    }

    private fun findAccepted(regex: Regex, text: String, excluded: Set<String>): MatchResult? {
        return regex.findAll(text).firstOrNull { LearnedWordsRepository.normalize(it.value) !in excluded }
    }

    /**
     * @param excludedPhrases phrases double-tap-taught to be treated as plain text.
     * @param rules user-taught phrase-to-meaning equivalences (the "library").
     */
    fun parse(
        rawInput: String,
        excludedPhrases: Set<String> = emptySet(),
        rules: List<Rule> = emptyList()
    ): ParsedTaskResult {
        var text = rawInput.trim()

        // 1. priority
        var priority = Priority.NORMAL
        var explicitPriorityMatched = false

        val startPhraseMatch = findAccepted(ParserPatterns.startPhraseRegex, text, excludedPhrases)
        if (startPhraseMatch != null && startPhraseMatch.range.first == 0 && startPhraseMatch.groupValues[1].isNotBlank()) {
            priority = wordToPriority(startPhraseMatch.groupValues[1])
            text = text.removeRange(startPhraseMatch.range)
            explicitPriorityMatched = true
        } else {
            val shortMatch = findAccepted(ParserPatterns.shortPriorityPrefixRegex, text, excludedPhrases)
            if (shortMatch != null && shortMatch.range.first == 0) {
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
        val dailyMatch = findAccepted(ParserPatterns.dailyRegex, text, excludedPhrases)
        val isDailyTask = dailyMatch != null
        if (dailyMatch != null) text = text.removeRange(dailyMatch.range)

        // 3. alarm keyword
        val alarmMatch = findAccepted(ParserPatterns.alarmRegex, text, excludedPhrases)
        val hasAlarm = alarmMatch != null
        if (alarmMatch != null) text = text.removeRange(alarmMatch.range)

        // 4. amount
        val amountMatch = findAccepted(ParserPatterns.amountRegex, text, excludedPhrases)
        val amount = amountMatch?.let {
            listOf(it.groupValues[1], it.groupValues[2], it.groupValues[3])
                .firstOrNull { g -> g.isNotBlank() }
                ?.replace(",", "")
                ?.toDoubleOrNull()
        }

        // 5. recipient — data only, not stripped from title
        val recipientMatch = if (amount != null) findAccepted(ParserPatterns.recipientRegex, text, excludedPhrases) else null
        val recipient = recipientMatch?.groupValues?.get(1)

        // 6. ambiguous mid-sentence priority phrase
        var ambiguousPhrase: String? = null
        var ambiguousPriority: Priority? = null
        if (!explicitPriorityMatched) {
            val midMatch = findAccepted(ParserPatterns.anywherePhraseRegex, text, excludedPhrases)
            if (midMatch != null) {
                ambiguousPhrase = midMatch.value
                ambiguousPriority = wordToPriority(midMatch.groupValues[1])
            }
        }

        // 7. till/by presence
        val hasTillWord = findAccepted(ParserPatterns.tillRegex, text, excludedPhrases) != null
        val hasByWord = findAccepted(ParserPatterns.byRegex, text, excludedPhrases) != null

        // 8. date/time resolution, in priority order:
        //    a) learned rules (user-taught phrase equivalences)
        //    b) relative durations ("5 seconds from now", "in 3 hours")
        //    c) the standard absolute date/time patterns
        val normalized = DateTimeExtractor.normalizeNumberWords(text)

        var extractedDate: ExtractedDate? = null
        var extractedTime: ExtractedTime? = null

        for (rule in rules) {
            val idx = text.indexOf(rule.phrase, ignoreCase = true)
            if (idx < 0) continue
            val (ruleDate, ruleTime) = RuleLibraryRepository.resolveMeaning(rule.meaning)
            if (ruleDate == null && ruleTime == null) continue
            val matchedSubstring = text.substring(idx, idx + rule.phrase.length)
            if (ruleDate != null) extractedDate = ExtractedDate(ruleDate, matchedSubstring)
            if (ruleTime != null) extractedTime = ExtractedTime(ruleTime, matchedSubstring)
            break
        }

        if (extractedDate == null && extractedTime == null) {
            val duration = RelativeDurationExtractor.extract(normalized)
            if (duration != null) {
                extractedDate = ExtractedDate(duration.date, duration.matchedText)
                if (duration.time != null) {
                    extractedTime = ExtractedTime(duration.time, duration.matchedText)
                }
            }
        }

        if (extractedDate == null) {
            val d = DateTimeExtractor.extractDate(normalized)
            extractedDate = if (d != null && LearnedWordsRepository.normalize(d.matchedText) !in excludedPhrases) d else null
        }
        if (extractedTime == null) {
            val t = DateTimeExtractor.extractTime(normalized)
            extractedTime = if (t != null && LearnedWordsRepository.normalize(t.matchedText) !in excludedPhrases) t else null
        }

        // 9. decide the real deadline type
        val hasDateOrTime = extractedDate != null || extractedTime != null
        val deadlineType = when {
            hasTillWord && hasDateOrTime -> DeadlineType.TILL
            hasByWord -> DeadlineType.BY
            else -> DeadlineType.ON
        }

        // 10. strip matched substrings from the title
        var title = text
        title = Regex("""\b(by|on)\b""", RegexOption.IGNORE_CASE).replace(title, "")
        if (deadlineType == DeadlineType.TILL) {
            title = Regex("""\b(till|until)\b""", RegexOption.IGNORE_CASE).replace(title, "")
        }
        if (extractedDate != null) {
            title = title.replace(
                Regex("""(?:\b(?:at|on|in)\s+)?${Regex.escape(extractedDate.matchedText)}""", RegexOption.IGNORE_CASE),
                ""
            )
        }
        if (extractedTime != null && extractedTime.matchedText != extractedDate?.matchedText) {
            title = title.replace(
                Regex("""(?:\b(?:at|in)\s+)?${Regex.escape(extractedTime.matchedText)}""", RegexOption.IGNORE_CASE),
                ""
            )
        }
        title = title.replace(Regex("""\s{2,}"""), " ").trim(' ', ',', '.', '-')

        if (title.isBlank()) title = rawInput.trim()

        val possiblyMissing = !hasDateOrTime && temporalHintRegex.containsMatchIn(rawInput)

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
            ambiguousSuggestedPriority = ambiguousPriority,
            possiblyMissingDateTime = possiblyMissing
        )
    }
}
