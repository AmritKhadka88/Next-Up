package com.nextup.app.parser

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class ExtractedDuration(
    val date: LocalDate,
    val time: LocalTime?,
    val matchedText: String
)

/**
 * Handles phrasing DateTimeExtractor doesn't cover: relative offsets measured in seconds,
 * minutes, hours, days, or weeks from the current moment — "5 seconds from now",
 * "two days from now", "in 3 hours", etc. Number words should already be normalized to
 * digits (via DateTimeExtractor.normalizeNumberWords) before calling this.
 */
object RelativeDurationExtractor {

    private val fromNowRegex = Regex(
        """\b(\d+)\s+(second|minute|hour|day|week)s?\s+from\s+now\b""",
        RegexOption.IGNORE_CASE
    )

    private val inUnitRegex = Regex(
        """\bin\s+(\d+)\s+(second|minute|hour|day|week)s?\b""",
        RegexOption.IGNORE_CASE
    )

    fun extract(normalizedText: String, now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault())): ExtractedDuration? {
        val match = fromNowRegex.find(normalizedText) ?: inUnitRegex.find(normalizedText) ?: return null

        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val unit = match.groupValues[2].lowercase()

        val result = when (unit) {
            "second" -> now.plusSeconds(amount)
            "minute" -> now.plusMinutes(amount)
            "hour" -> now.plusHours(amount)
            "day" -> now.plusDays(amount)
            "week" -> now.plusWeeks(amount)
            else -> return null
        }

        // Seconds/minutes/hours imply a precise moment; days/weeks are date-granularity only,
        // consistent with how the rest of the app treats day-level due dates.
        val impliesTime = unit == "second" || unit == "minute" || unit == "hour"

        return ExtractedDuration(
            date = result.toLocalDate(),
            time = if (impliesTime) result.toLocalTime() else null,
            matchedText = match.value
        )
    }
}
