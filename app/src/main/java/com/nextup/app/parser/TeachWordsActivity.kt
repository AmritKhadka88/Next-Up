package com.nextup.app.parser

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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

        findViewById<Button>(R.id.buttonClearAll).setOnClickListener {
            ruleLibrary.clearAll()
            learnedWords.clearAll()
            refreshLists()
        }

        refreshLists()
    }

    private fun refreshLists() {
        val rules = ruleLibrary.getRules()
        rulesListView.text = if (rules.isEmpty()) {
            getString(R.string.none_yet)
        } else {
            rules.joinToString("\n") { "${it.phrase} = ${it.meaning}" }
        }

        val excluded = learnedWords.getExcludedPhrases()
        excludedListView.text = if (excluded.isEmpty()) {
            getString(R.string.none_yet)
        } else {
            excluded.joinToString(", ")
        }
    }
}
