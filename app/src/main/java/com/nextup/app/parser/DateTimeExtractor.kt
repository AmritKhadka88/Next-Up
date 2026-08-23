package com.nextup.app.parser

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class ExtractedDate(val date: LocalDate, val matchedText: String)
data class ExtractedTime(val time: LocalTime, val matchedText: String)

/**
 * Handles messy natural language date/time extraction:
 * - digits and number words ("5", "five")
 * - month names with misspelling tolerance ("feb", "feburary", "february")
 * - relative dates ("today", "tomorrow", "day after tomorrow", weekday names)
 * - time formats: "5:30", "5.30", "5 30", "5,30", "five thirty", "2am", "2 am"
 */
object DateTimeExtractor {

    private val numberWords = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
        "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40,
        "fifty" to 50, "sixty" to 60
    )

    private val months = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december"
    )

    private val weekdays = listOf(
        "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"
    )

    /** Converts number words (including compound like "twenty five") into digits within a string. */
    fun normalizeNumberWords(input: String): String {
        var text = input.lowercase()
        // handle compound tens+units e.g. "twenty five" -> "25"
        val compoundRegex = Regex("""\b(twenty|thirty|forty|fifty|sixty)\s+(one|two|three|four|five|six|seven|eight|nine)\b""")
        text = compoundRegex.replace(text) { m ->
            val tens = numberWords[m.groupValues[1]] ?: 0
            val units = numberWords[m.groupValues[2]] ?: 0
            (tens + units).toString()
        }
        // then single words
        for ((word, num) in numberWords.entries.sortedByDescending { it.key.length }) {
            text = Regex("\\b$word\\b").replace(text, num.toString())
        }
        return text
    }

    /** Fuzzy-matches a token against month names, tolerating small misspellings. */
    fun matchMonth(token: String): Int? {
        val clean = token.lowercase().trimEnd('.', ',')
        // exact or prefix match first (e.g. "sept", "sep")
        months.forEachIndexed { idx, name ->
            if (name == clean || (clean.length >= 3 && name.startsWith(clean))) return idx + 1
        }
        // fuzzy match with edit distance tolerance for misspellings e.g. "feburary"
        months.forEachIndexed { idx, name ->
            if (clean.length >= 3 && levenshtein(clean, name) <= 2) return idx + 1
        }
        return null
    }

    private fun matchWeekday(token: String): Int? {
        val clean = token.lowercase().trimEnd('.', ',')
        weekdays.forEachIndexed { idx, name ->
            if (name == clean || (clean.length >= 3 && name.startsWith(clean))) return idx
        }
        return null
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }

    /**
     * Extracts the first date reference found in text.
     * Text should already have number words normalized via normalizeNumberWords().
     */
    fun extractDate(text: String, today: LocalDate = LocalDate.now(ZoneId.systemDefault())): ExtractedDate? {
        val lower = text.lowercase()

        // "day after tomorrow"
        if (Regex("""\bday after tomorrow\b""").containsMatchIn(lower)) {
            return ExtractedDate(today.plusDays(2), "day after tomorrow")
        }
        if (Regex("""\btomorrow\b""").containsMatchIn(lower)) {
            return ExtractedDate(today.plusDays(1), "tomorrow")
        }
        if (Regex("""\btoday\b""").containsMatchIn(lower)) {
            return ExtractedDate(today, "today")
        }

        // "next <weekday>" or bare "<weekday>"
        val weekdayRegex = Regex("""\b(next\s+)?([a-z]{3,9})\b""")
        for (match in weekdayRegex.findAll(lower)) {
            val dayIdx = matchWeekday(match.groupValues[2])
            if (dayIdx != null) {
                val isNext = match.groupValues[1].isNotBlank()
                var result = today.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(if (dayIdx == 0) 7 else dayIdx)))
                if (isNext && result == today) result = result.plusWeeks(1)
                if (!isNext && result == today) { /* keep today if same day, "friday" said on friday */ }
                return ExtractedDate(result, match.value)
            }
        }

        // "10 sept", "sept 10", "10th september", plus optional trailing year: "10 sept 2030"
        val dateRegex = Regex(
            """\b(\d{1,2})(?:st|nd|rd|th)?\s+([a-z]{3,9})(?:\s+(\d{4}))?\b""" +
                """|\b([a-z]{3,9})\s+(\d{1,2})(?:st|nd|rd|th)?(?:\s+(\d{4}))?\b"""
        )
        val match = dateRegex.find(lower)
        if (match != null) {
            val day: Int
            val monthToken: String
            val yearToken: String
            if (match.groupValues[1].isNotBlank()) {
                day = match.groupValues[1].toInt()
                monthToken = match.groupValues[2]
                yearToken = match.groupValues[3]
            } else {
                monthToken = match.groupValues[4]
                day = match.groupValues[5].toInt()
                yearToken = match.groupValues[6]
            }
            val month = matchMonth(monthToken)
            if (month != null && day in 1..31) {
                val explicitYear = yearToken.toIntOrNull()
                var year = explicitYear ?: today.year
                var candidate = try { LocalDate.of(year, month, day) } catch (e: Exception) { null }
                // Only roll forward to next year when no explicit year was given and the date has already passed
                if (explicitYear == null && candidate != null && candidate.isBefore(today)) {
                    candidate = LocalDate.of(year + 1, month, day)
                }
                if (candidate != null) return ExtractedDate(candidate, match.value)
            }
        }

        // numeric date "10/9" or "10-9" (day/month, Australian convention)
        val numericRegex = Regex("""\b(\d{1,2})[/\-](\d{1,2})\b""")
        val numericMatch = numericRegex.find(lower)
        if (numericMatch != null) {
            val day = numericMatch.groupValues[1].toInt()
            val month = numericMatch.groupValues[2].toInt()
            if (day in 1..31 && month in 1..12) {
                var candidate = try { LocalDate.of(today.year, month, day) } catch (e: Exception) { null }
                if (candidate != null && candidate.isBefore(today)) {
                    candidate = LocalDate.of(today.year + 1, month, day)
                }
                if (candidate != null) return ExtractedDate(candidate, numericMatch.value)
            }
        }

        return null
    }

    /**
     * Extracts time-of-day. Handles "5:30", "5.30", "5 30", "5,30", "2am", "2 am", "14:00".
     * Assumes number words already normalized to digits.
     */
    fun extractTime(text: String): ExtractedTime? {
        val lower = text.lowercase()

        // e.g. "5:30 am", "5.30pm", "5 30 am", "5,30"
        val timeRegex = Regex("""\b(\d{1,2})[:.,\s](\d{2})\s?(am|pm)?\b""")
        val match = timeRegex.find(lower)
        if (match != null) {
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val meridiem = match.groupValues[3]
            if (meridiem == "pm" && hour < 12) hour += 12
            if (meridiem == "am" && hour == 12) hour = 0
            if (hour in 0..23 && minute in 0..59) {
                return ExtractedTime(LocalTime.of(hour, minute), match.value)
            }
        }

        // e.g. "2am", "2 am", "2pm"
        val simpleRegex = Regex("""\b(\d{1,2})\s?(am|pm)\b""")
        val simpleMatch = simpleRegex.find(lower)
        if (simpleMatch != null) {
            var hour = simpleMatch.groupValues[1].toInt()
            val meridiem = simpleMatch.groupValues[2]
            if (meridiem == "pm" && hour < 12) hour += 12
            if (meridiem == "am" && hour == 12) hour = 0
            if (hour in 0..23) {
                return ExtractedTime(LocalTime.of(hour, 0), simpleMatch.value)
            }
        }

        return null
    }
}
