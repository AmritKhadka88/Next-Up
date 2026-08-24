package com.nextup.app.parser

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class Rule(val phrase: String, val meaning: String)

/**
 * The teachable "library" of custom phrase equivalences. Two ways to add a rule:
 *
 * 1. Alias to something the app already understands:
 *    "two days from now = day after tomorrow"
 *
 * 2. An explicit formula, "today"/"now" plus/minus an offset:
 *    "the day after tomorrow = today + 2"        (days is the default unit)
 *    "in a bit = now + 30 minutes"
 *
 * Rules are stored as "phrase|||meaning" strings in a SharedPreferences StringSet —
 * simple and avoids pulling in a JSON dependency for what's just pairs of short strings.
 */
class RuleLibraryRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRules(): List<Rule> {
        return prefs.getStringSet(KEY_RULES, emptySet())?.mapNotNull { entry ->
            val parts = entry.split(DELIMITER)
            if (parts.size == 2) Rule(parts[0], parts[1]) else null
        } ?: emptyList()
    }

    fun addRule(phrase: String, meaning: String) {
        val current = prefs.getStringSet(KEY_RULES, emptySet())?.toMutableSet() ?: mutableSetOf()
        // Replace any existing rule for the same phrase rather than duplicating it.
        current.removeAll { it.startsWith("${normalize(phrase)}$DELIMITER") }
        current.add("${normalize(phrase)}$DELIMITER${meaning.trim()}")
        prefs.edit().putStringSet(KEY_RULES, current).apply()
    }

    fun removeRule(phrase: String) {
        val current = prefs.getStringSet(KEY_RULES, emptySet())?.toMutableSet() ?: return
        current.removeAll { it.startsWith("${normalize(phrase)}$DELIMITER") }
        prefs.edit().putStringSet(KEY_RULES, current).apply()
    }

    fun clearAll() {
        prefs.edit().remove(KEY_RULES).apply()
    }

    /**
     * Parses bulk-pasted rules, one per line, in "phrase = meaning" format.
     * Returns how many rules were successfully imported.
     */
    fun importBulkText(text: String): Int {
        var count = 0
        text.lines().forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                val phrase = parts[0].trim()
                val meaning = parts[1].trim()
                if (phrase.isNotBlank() && meaning.isNotBlank()) {
                    addRule(phrase, meaning)
                    count++
                }
            }
        }
        return count
    }

    companion object {
        private const val PREFS_NAME = "nextup_rule_library"
        private const val KEY_RULES = "rules"
        private const val DELIMITER = "|||"

        fun normalize(phrase: String): String = phrase.trim().lowercase()

        private val formulaRegex = Regex(
            """\b(today|now)\s*([+-])\s*(\d+)\s*(day|hour|minute|second)?s?\b""",
            RegexOption.IGNORE_CASE
        )

        /**
         * Resolves a rule's right-hand side to a concrete date/time. Tries the explicit
         * "today/now +/- N unit" formula first; if that doesn't match, treats the meaning
         * as an ordinary phrase and re-runs it through the normal parsing pipeline
         * (relative durations, then absolute date/time patterns) — which lets one rule's
         * meaning point at "day after tomorrow" or any other phrase the app already understands.
         */
        fun resolveMeaning(meaning: String, now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault())): Pair<LocalDate?, LocalTime?> {
            val formulaMatch = formulaRegex.find(meaning)
            if (formulaMatch != null) {
                val base = formulaMatch.groupValues[1].lowercase()
                val sign = if (formulaMatch.groupValues[2] == "-") -1L else 1L
                val amount = formulaMatch.groupValues[3].toLongOrNull() ?: 0L
                val unit = formulaMatch.groupValues[4].ifBlank { "day" }.lowercase()

                val offsetAmount = sign * amount
                val result = when (unit) {
                    "day" -> now.plusDays(offsetAmount)
                    "hour" -> now.plusHours(offsetAmount)
                    "minute" -> now.plusMinutes(offsetAmount)
                    "second" -> now.plusSeconds(offsetAmount)
                    else -> now.plusDays(offsetAmount)
                }
                val impliesTime = unit != "day" || base == "now"
                return result.toLocalDate() to (if (impliesTime) result.toLocalTime() else null)
            }

            // Not a formula — treat it as an ordinary phrase and reuse the standard pipeline.
            val normalized = DateTimeExtractor.normalizeNumberWords(meaning)
            val duration = RelativeDurationExtractor.extract(normalized, now)
            if (duration != null) return duration.date to duration.time

            val date = DateTimeExtractor.extractDate(normalized, now.toLocalDate())?.date
            val time = DateTimeExtractor.extractTime(normalized)?.time
            return date to time
        }
    }
}
