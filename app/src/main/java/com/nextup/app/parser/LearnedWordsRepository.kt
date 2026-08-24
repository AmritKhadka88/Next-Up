package com.nextup.app.parser

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ExcludedWord(
    val phrase: String,
    val lastUsed: LocalDate = LocalDate.now(),
    val useCount: Int = 0
)

/**
 * The "library" of words the user double-tapped to say "treat this as plain text, not a
 * keyword." Tracks usage the same way RuleLibraryRepository does, so stale exclusions that
 * haven't mattered in months get cleared out automatically too.
 */
class LearnedWordsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun getExcludedWords(): List<ExcludedWord> {
        return prefs.getStringSet(KEY_EXCLUDED, emptySet())?.mapNotNull { decode(it) } ?: emptyList()
    }

    fun getExcludedPhrases(): Set<String> = getExcludedWords().map { it.phrase }.toSet()

    fun isExcluded(phrase: String): Boolean = normalize(phrase) in getExcludedPhrases()

    private fun saveAll(words: List<ExcludedWord>) {
        prefs.edit().putStringSet(KEY_EXCLUDED, words.map { encode(it) }.toSet()).apply()
    }

    /** Toggles exclusion for a phrase and marks it used. Returns the new excluded state. */
    fun toggle(phrase: String): Boolean {
        val normalized = normalize(phrase)
        val current = getExcludedWords().toMutableList()
        val existingIdx = current.indexOfFirst { it.phrase == normalized }
        val nowExcluded: Boolean
        if (existingIdx >= 0) {
            current.removeAt(existingIdx)
            nowExcluded = false
        } else {
            current.add(ExcludedWord(normalized, LocalDate.now(), 1))
            nowExcluded = true
        }
        saveAll(current)
        return nowExcluded
    }

    fun markUsed(phrase: String) {
        val current = getExcludedWords().toMutableList()
        val idx = current.indexOfFirst { it.phrase == normalize(phrase) }
        if (idx >= 0) {
            val w = current[idx]
            current[idx] = w.copy(lastUsed = LocalDate.now(), useCount = w.useCount + 1)
            saveAll(current)
        }
    }

    fun pruneStale(days: Long = 120): Int {
        val cutoff = LocalDate.now().minusDays(days)
        val current = getExcludedWords()
        val kept = current.filter { it.lastUsed.isAfter(cutoff) }
        val removed = current.size - kept.size
        if (removed > 0) saveAll(kept)
        return removed
    }

    fun clearAll() {
        prefs.edit().remove(KEY_EXCLUDED).apply()
    }

    fun exportAsText(): String {
        val words = getExcludedWords().sortedByDescending { it.useCount }
        val sb = StringBuilder()
        words.forEach { w ->
            sb.appendLine("${w.phrase} [human] used=${w.useCount} last=${w.lastUsed.format(dateFmt)}")
        }
        return sb.toString()
    }

    private fun encode(word: ExcludedWord): String {
        return listOf(word.phrase, word.lastUsed.format(dateFmt), word.useCount.toString()).joinToString(DELIMITER)
    }

    private fun decode(entry: String): ExcludedWord? {
        val parts = entry.split(DELIMITER)
        return when (parts.size) {
            3 -> try {
                ExcludedWord(parts[0], LocalDate.parse(parts[1], dateFmt), parts[2].toIntOrNull() ?: 0)
            } catch (e: Exception) { null }
            1 -> ExcludedWord(parts[0]) // old format before usage-tracking existed
            else -> null
        }
    }

    companion object {
        private const val PREFS_NAME = "nextup_learned_words"
        private const val KEY_EXCLUDED = "excluded_v2"
        private const val DELIMITER = "|||"

        fun normalize(phrase: String): String = phrase.trim().lowercase()
    }
}
