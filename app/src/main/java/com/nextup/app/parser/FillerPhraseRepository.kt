package com.nextup.app.parser

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class FillerPhrase(
    val phrase: String,
    val source: RuleSource = RuleSource.HUMAN,
    val lastUsed: LocalDate = LocalDate.now(),
    val useCount: Int = 0
)

/**
 * Conversational wrapper phrases stripped from the start of what's typed, before any other
 * parsing happens — "I need to give coffee to Paarth" becomes "give coffee to Paarth" first,
 * so date/time/priority extraction then run on the actual task content, not the politeness
 * wrapper around it.
 *
 * A sensible default set ships built-in and can't be removed (they're near-universal English
 * phrasing). Anything beyond that is teachable the same way as the rule library — typed
 * directly, or via the same export/AI-update/import loop, with the same usage tracking and
 * 4-month staleness pruning.
 */
class FillerPhraseRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun getLearnedPhrases(): List<FillerPhrase> {
        return prefs.getStringSet(KEY_PHRASES, emptySet())?.mapNotNull { decode(it) } ?: emptyList()
    }

    /** Built-in defaults plus anything taught, longest phrase first so multi-word fillers
     *  match before a shorter phrase they contain (e.g. "please remind me to" before "remind me to"). */
    fun getAllPhrases(): List<String> {
        val learned = getLearnedPhrases().map { it.phrase }
        return (DEFAULT_FILLERS + learned).distinct().sortedByDescending { it.length }
    }

    private fun saveAll(phrases: List<FillerPhrase>) {
        prefs.edit().putStringSet(KEY_PHRASES, phrases.map { encode(it) }.toSet()).apply()
    }

    fun addPhrase(phrase: String, source: RuleSource = RuleSource.HUMAN) {
        val current = getLearnedPhrases().toMutableList()
        current.removeAll { normalize(it.phrase) == normalize(phrase) }
        current.add(FillerPhrase(normalize(phrase), source, LocalDate.now(), 0))
        saveAll(current)
    }

    fun removePhrase(phrase: String) {
        saveAll(getLearnedPhrases().filterNot { normalize(it.phrase) == normalize(phrase) })
    }

    fun markUsed(phrase: String) {
        val current = getLearnedPhrases().toMutableList()
        val idx = current.indexOfFirst { normalize(it.phrase) == normalize(phrase) }
        if (idx >= 0) {
            val p = current[idx]
            current[idx] = p.copy(lastUsed = LocalDate.now(), useCount = p.useCount + 1)
            saveAll(current)
        }
    }

    fun pruneStale(days: Long = 120): Int {
        val cutoff = LocalDate.now().minusDays(days)
        val current = getLearnedPhrases()
        val kept = current.filter { it.lastUsed.isAfter(cutoff) }
        val removed = current.size - kept.size
        if (removed > 0) saveAll(kept)
        return removed
    }

    fun clearLearned() {
        prefs.edit().remove(KEY_PHRASES).apply()
    }

    fun importBulkText(text: String): Int {
        var count = 0
        text.lines().forEach { line ->
            val phrase = line.trim().trimEnd(':').trim()
            if (phrase.isNotBlank() && !phrase.startsWith("#")) {
                addPhrase(phrase, RuleSource.HUMAN)
                count++
            }
        }
        return count
    }

    fun importAiUpdate(text: String): Pair<Int, Int> {
        var added = 0
        var skippedHuman = 0
        val current = getLearnedPhrases().toMutableList()
        val humanPhrases = current.filter { it.source == RuleSource.HUMAN }.map { normalize(it.phrase) }.toSet()

        text.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            val annotated = annotatedLineRegex.find(line)
            val phrase: String
            var lastUsed = LocalDate.now()
            var useCount = 0

            if (annotated != null) {
                phrase = annotated.groupValues[1].trim()
                useCount = annotated.groupValues[3].toIntOrNull() ?: 0
                lastUsed = try { LocalDate.parse(annotated.groupValues[4], dateFmt) } catch (e: Exception) { LocalDate.now() }
            } else {
                phrase = line.trimEnd(':').trim()
            }
            if (phrase.isBlank()) return@forEach

            if (normalize(phrase) in humanPhrases) {
                skippedHuman++
                return@forEach
            }
            current.removeAll { normalize(it.phrase) == normalize(phrase) }
            current.add(FillerPhrase(normalize(phrase), RuleSource.AI, lastUsed, useCount))
            added++
        }
        saveAll(current)
        return added to skippedHuman
    }

    fun exportAsText(): String {
        val phrases = getLearnedPhrases().sortedByDescending { it.useCount }
        val sb = StringBuilder()
        phrases.forEach { p ->
            val tag = if (p.source == RuleSource.HUMAN) "human" else "ai"
            sb.appendLine("${p.phrase} [$tag] used=${p.useCount} last=${p.lastUsed.format(dateFmt)}")
        }
        return sb.toString()
    }

    /** Repeatedly strips matching filler phrases from the START of the text (handles
     *  chained fillers like "please can you remind me to..."), then trims connector
     *  leftovers ("to", "that") that commonly follow a stripped filler. */
    fun strip(text: String, allPhrases: List<String> = getAllPhrases()): String {
        return stripFillers(text, allPhrases)
    }

    private fun encode(p: FillerPhrase): String {
        return listOf(p.phrase, p.source.name, p.lastUsed.format(dateFmt), p.useCount.toString()).joinToString(DELIMITER)
    }

    private fun decode(entry: String): FillerPhrase? {
        val parts = entry.split(DELIMITER)
        return if (parts.size == 4) {
            try {
                FillerPhrase(parts[0], RuleSource.valueOf(parts[1]), LocalDate.parse(parts[2], dateFmt), parts[3].toIntOrNull() ?: 0)
            } catch (e: Exception) { null }
        } else null
    }

    companion object {
        private const val PREFS_NAME = "nextup_filler_phrases"
        private const val KEY_PHRASES = "phrases"
        private const val DELIMITER = "|||"

        fun normalize(phrase: String): String = phrase.trim().lowercase()

        private val annotatedLineRegex = Regex(
            """^(.+?)\s*\[(human|ai)]\s*used=(\d+)\s*last=(\d{4}-\d{2}-\d{2})\s*$""",
            RegexOption.IGNORE_CASE
        )

        val DEFAULT_FILLERS = listOf(
            "i need to", "i have to", "i want to", "i must", "i should", "i gotta", "i got to",
            "please remind me to", "remind me to", "don't forget to", "do not forget to",
            "make sure to", "make sure you", "can you", "could you", "would you",
            "i need you to", "please", "kindly", "just need to", "just have to",
            "note to self", "reminder to", "reminder that", "note that", "fyi"
        )

        /** Pure, Context-free version so it can be called from places (like TaskParser)
         *  that don't have a repository instance handy. */
        fun stripFillers(text: String, allPhrases: List<String>): String {
            var result = text.trim()
            var strippedSomething = true
            var guard = 0
            while (strippedSomething && guard < 5) {
                strippedSomething = false
                guard++
                for (phrase in allPhrases) {
                    if (result.startsWith(phrase, ignoreCase = true)) {
                        result = result.substring(phrase.length).trimStart(' ', ',', ':', '-')
                        strippedSomething = true
                        break
                    }
                }
            }
            return result.ifBlank { text.trim() }
        }
    }
}
