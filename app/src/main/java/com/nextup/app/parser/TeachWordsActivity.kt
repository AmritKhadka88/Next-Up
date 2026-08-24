package com.nextup.app.parser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nextup.app.R

class TeachWordsActivity : AppCompatActivity() {

    private lateinit var ruleLibrary: RuleLibraryRepository
    private lateinit var learnedWords: LearnedWordsRepository
    private lateinit var rulesListView: TextView
    private lateinit var excludedListView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teach_words)

        ruleLibrary = RuleLibraryRepository(this)
        learnedWords = LearnedWordsRepository(this)
        rulesListView = findViewById(R.id.textRulesList)
        excludedListView = findViewById(R.id.textExcludedList)

        val bulkInput = findViewById<EditText>(R.id.editTextBulkRules)
        findViewById<Button>(R.id.buttonImportRules).setOnClickListener {
            val count = ruleLibrary.importBulkText(bulkInput.text.toString())
            Toast.makeText(this, "Learned $count rule(s)", Toast.LENGTH_SHORT).show()
            bulkInput.text.clear()
            refreshLists()
        }

        findViewById<Button>(R.id.buttonExportLibrary).setOnClickListener {
            showExportDialog()
        }

        val aiUpdateInput = findViewById<EditText>(R.id.editTextAiUpdate)
        findViewById<Button>(R.id.buttonImportAiUpdate).setOnClickListener {
            val (added, skipped) = ruleLibrary.importAiUpdate(aiUpdateInput.text.toString())
            val message = if (skipped > 0) {
                "Added/updated $added rule(s). Skipped $skipped (protected your hand-typed rules)."
            } else {
                "Added/updated $added rule(s)."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            aiUpdateInput.text.clear()
            refreshLists()
        }

        findViewById<Button>(R.id.buttonCleanupNow).setOnClickListener {
            val removedRules = ruleLibrary.pruneStale()
            val removedWords = learnedWords.pruneStale()
            Toast.makeText(this, "Removed ${removedRules + removedWords} unused entry/entries (4+ months untouched)", Toast.LENGTH_SHORT).show()
            refreshLists()
        }

        findViewById<Button>(R.id.buttonClearAll).setOnClickListener {
            ruleLibrary.clearAll()
            learnedWords.clearAll()
            refreshLists()
        }

        refreshLists()
    }

    private fun showExportDialog() {
        val exportText = ruleLibrary.exportAsText() + "\n# EXCLUDED WORDS (treated as plain text)\n" + learnedWords.exportAsText()

        val textView = TextView(this).apply {
            text = exportText
            setPadding(40, 24, 40, 24)
            setTextIsSelectable(true)
            textSize = 12f
        }
        val scroll = android.widget.ScrollView(this).apply { addView(textView) }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.export_library))
            .setView(scroll)
            .setPositiveButton(R.string.copy_to_clipboard) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("NextUp rule library", exportText))
                Toast.makeText(this, "Copied — paste it wherever you're sending it", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.share) { _, _ ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, exportText)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.export_library)))
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun refreshLists() {
        val rules = ruleLibrary.getRules()
        rulesListView.text = if (rules.isEmpty()) {
            getString(R.string.none_yet)
        } else {
            rules.sortedByDescending { it.useCount }.joinToString("\n") {
                val tag = if (it.source == RuleSource.HUMAN) "human" else "ai"
                "${it.phrase} = ${it.meaning}  (${it.useCount}× · $tag)"
            }
        }

        val excluded = learnedWords.getExcludedWords()
        excludedListView.text = if (excluded.isEmpty()) {
            getString(R.string.none_yet)
        } else {
            excluded.sortedByDescending { it.useCount }.joinToString("\n") { "${it.phrase} (${it.useCount}×)" }
        }
    }
}
