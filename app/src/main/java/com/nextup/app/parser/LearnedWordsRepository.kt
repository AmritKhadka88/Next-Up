package com.nextup.app.parser

import android.content.Context

/**
 * The "library" of learned exceptions. Whenever the user double-taps a highlighted
 * keyword to say "no, treat this as a normal word," that exact phrase gets remembered
 * here — globally, from then on — so the parser stops treating it as a keyword
 * anywhere it appears in future input, not just this one sentence.
 *
 * Double-tapping an already-excluded word un-teaches it (returns it to normal keyword behavior).
 */
class LearnedWordsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getExcludedPhrases(): Set<String> {
        return prefs.getStringSet(KEY_EXCLUDED, emptySet()) ?: emptySet()
    }

    fun isExcluded(phrase: String): Boolean {
        return normalize(phrase) in getExcludedPhrases()
    }

    /** Toggles whether a phrase is treated as plain text. Returns the new excluded state. */
    fun toggle(phrase: String): Boolean {
        val normalized = normalize(phrase)
        val current = getExcludedPhrases().toMutableSet()
        val nowExcluded = if (normalized in current) {
            current.remove(normalized)
            false
        } else {
            current.add(normalized)
            true
        }
        prefs.edit().putStringSet(KEY_EXCLUDED, current).apply()
        return nowExcluded
    }

    fun clearAll() {
        prefs.edit().remove(KEY_EXCLUDED).apply()
    }

    companion object {
        private const val PREFS_NAME = "nextup_learned_words"
        private const val KEY_EXCLUDED = "excluded_phrases"

        fun normalize(phrase: String): String = phrase.trim().lowercase()
    }
}
