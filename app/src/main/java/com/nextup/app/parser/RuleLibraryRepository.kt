package com.nextup.app.parser

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class RuleSource { HUMAN, AI }

data class Rule(
    val phrase: String,
    val meaning: String,
    val source: RuleSource = RuleSource.HUMAN,
    val lastUsed: LocalDate = LocalDate.now(),
    val useCount: Int = 0
)

/**
 * The teachable "library" of custom phrase equivalences, now with usage tracking so it can
 * grow and prune itself over time:
 *  - Every time a rule actually fires during parsing, its use count and last-used date update.
 *  - Rules untouched for 4+ months get automatically cleared out (pruneStale()).
 *  - Rules are tagged HUMAN (typed directly in the app) or AI (came from an export/import
 *    round-trip with an AI). Re-importing an AI-generated update never overwrites a HUMAN
 *    rule for the same phrase — your hand-written ones are protected by default.
 */
class RuleLibraryRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun getRules(): List<Rule> {
        return prefs.getStringSet(KEY_RULES, emptySet())?.mapNotNull { decode(it) } ?: emptyList()
    }

    private fun saveAll(rules: List<Rule>) {
        val encoded = rules.map { encode(it) }.toSet()
        prefs.edit().putStringSet(KEY_RULES, encoded).apply()
    }

    fun addRule(phrase: String, meaning: String, source: RuleSource = RuleSource.HUMAN) {
        val current = getRules().toMutableList()
        current.removeAll { normalize(it.phrase) == normalize(phrase) }
        current.add(Rule(normalize(phrase), meaning.trim(), source, LocalDate.now(), 0))
        saveAll(current)
    }

    fun removeRule(phrase: String) {
        saveAll(getRules().filterNot { normalize(it.phrase) == normalize(phrase) })
    }

    fun clearAll() {
        prefs.edit().remove(KEY_RULES).apply()
    }

    /** Call whenever a rule actually fires while parsing a task, to keep its usage stats current. */
    fun markUsed(phrase: String) {
        val current = getRules().toMutableList()
        val idx = current.indexOfFirst { normalize(it.phrase) == normalize(phrase) }
        if (idx >= 0) {
            val r = current[idx]
            current[idx] = r.copy(lastUsed = LocalDate.now(), useCount = r.useCount + 1)
            saveAll(current)
        }
    }

    /** Removes any rule that hasn't fired in [days] days. Returns how many were removed. */
    fun pruneStale(days: Long = 120): Int {
        val cutoff = LocalDate.now().minusDays(days)
        val current = getRules()
        val kept = current.filter { it.lastUsed.isAfter(cutoff) }
        val removedCount = current.size - kept.size
        if (removedCount > 0) saveAll(kept)
        return removedCount
    }

    /** Bulk import typed/pasted directly in the app — always tagged HUMAN, always applied. */
    fun importBulkText(text: String): Int {
        var count = 0
        text.lines().forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                addRule(parts[0].trim(), parts[1].trim(), RuleSource.HUMAN)
                count++
            }
        }
        return count
    }

    /**
     * Imports a pasted AI-updated export. Understands both the full annotated export format
     * and plain "phrase = meaning" lines (e.g. brand-new rules an AI just generated with no
     * tags yet). HUMAN rules already stored locally are never overwritten by this — only
     * new phrases or existing AI-tagged phrases get applied. Returns (added, skippedHuman).
     */
    fun importAiUpdate(text: String): Pair<Int, Int> {
        var added = 0
        var skippedHuman = 0
        val current = getRules().toMutableList()
        val humanPhrases = current.filter { it.source == RuleSource.HUMAN }.map { normalize(it.phrase) }.toSet()

        text.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            val annotated = annotatedLineRegex.find(line)
            val phrase: String
            val meaning: String
            var lastUsed = LocalDate.now()
            var useCount = 0

            if (annotated != null) {
                phrase = annotated.groupValues[1].trim()
                meaning = annotated.groupValues[2].trim()
                lastUsed = try { LocalDate.parse(annotated.groupValues[5], dateFmt) } catch (e: Exception) { LocalDate.now() }
                useCount = annotated.groupValues[4].toIntOrNull() ?: 0
            } else {
                val simple = line.split("=", limit = 2)
                if (simple.size != 2 || simple[0].isBlank() || simple[1].isBlank()) return@forEach
                phrase = simple[0].trim()
                meaning = simple[1].trim()
            }

            if (normalize(phrase) in humanPhrases) {
                skippedHuman++
                return@forEach
            }

            current.removeAll { normalize(it.phrase) == normalize(phrase) }
            current.add(Rule(normalize(phrase), meaning, RuleSource.AI, lastUsed, useCount))
            added++
        }

        saveAll(current)
        return added to skippedHuman
    }

    fun exportAsText(): String {
        val rules = getRules().sortedByDescending { it.useCount }
        val sb = StringBuilder()
        sb.appendLine("# NextUp rule library export — ${LocalDate.now()}")
        sb.appendLine("# RULES: phrase = meaning [human/ai] used=N last=yyyy-MM-dd")
        sb.appendLine("# Lines tagged [human] were typed directly in the app — please don't change")
        sb.appendLine("# or remove them unless specifically asked to. Lines tagged [ai] came from a")
        sb.appendLine("# previous AI update and are safe to refine or replace. New rules can just be")
        sb.appendLine("# plain \"phrase = meaning\" lines with no tag.")
        sb.appendLine()
        rules.forEach { r ->
            val tag = if (r.source == RuleSource.HUMAN) "human" else "ai"
            sb.appendLine("${r.phrase} = ${r.meaning} [$tag] used=${r.useCount} last=${r.lastUsed.format(dateFmt)}")
        }
        return sb.toString()
    }

    private fun encode(rule: Rule): String {
        return listOf(
            rule.phrase, rule.meaning, rule.source.name,
            rule.lastUsed.format(dateFmt), rule.useCount.toString()
        ).joinToString(DELIMITER)
    }

    private fun decode(entry: String): Rule? {
        val parts = entry.split(DELIMITER)
        return when (parts.size) {
            5 -> try {
                Rule(
                    parts[0], parts[1],
                    RuleSource.valueOf(parts[2]),
                    LocalDate.parse(parts[3], dateFmt),
                    parts[4].toIntOrNull() ?: 0
                )
            } catch (e: Exception) { null }
            2 -> Rule(parts[0], parts[1]) // old format before usage-tracking existed
            else -> null
        }
    }

    companion object {
        private const val PREFS_NAME = "nextup_rule_library"
        private const val KEY_RULES = "rules_v2"
        private const val DELIMITER = "|||"

        private val annotatedLineRegex = Regex(
            """^(.+?)\s*=\s*(.+?)\s*\[(human|ai)]\s*used=(\d+)\s*last=(\d{4}-\d{2}-\d{2})\s*$""",
            RegexOption.IGNORE_CASE
        )

        fun normalize(phrase: String): String = phrase.trim().lowercase()

        private val formulaRegex = Regex(
            """\b(today|now)\s*([+-])\s*(\d+)\s*(day|hour|minute|second)?s?\b""",
            RegexOption.IGNORE_CASE
        )

        fun resolveMeaning(meaning: String, now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault())): Pair<LocalDate?, java.time.LocalTime?> {
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

            val normalized = DateTimeExtractor.normalizeNumberWords(meaning)
            val duration = RelativeDurationExtractor.extract(normalized, now)
            if (duration != null) return duration.date to duration.time

            val date = DateTimeExtractor.extractDate(normalized, now.toLocalDate())?.date
            val time = DateTimeExtractor.extractTime(normalized)?.time
            return date to time
        }
    }
}
