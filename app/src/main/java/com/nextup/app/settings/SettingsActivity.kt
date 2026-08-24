package com.nextup.app.settings

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.nextup.app.R

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        settings = SettingsRepository(this)

        findViewById<Button>(R.id.buttonTextColor).setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.text_color), settings.textColor) {
                settings.textColor = it
                recreate()
            }
        }

        findViewById<Button>(R.id.buttonBackgroundColor).setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.background_color), settings.backgroundColor) {
                settings.backgroundColor = it
                recreate()
            }
        }

        findViewById<Button>(R.id.buttonHighColor).setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.high_priority_color), settings.highPriorityColor) {
                settings.highPriorityColor = it
                recreate()
            }
        }

        findViewById<Button>(R.id.buttonMediumColor).setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.medium_priority_color), settings.mediumPriorityColor) {
                settings.mediumPriorityColor = it
                recreate()
            }
        }

        findViewById<Button>(R.id.buttonNormalColor).setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.normal_priority_color), settings.normalPriorityColor) {
                settings.normalPriorityColor = it
                recreate()
            }
        }

        findViewById<Button>(R.id.buttonHighlightColor).setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.highlight_color), settings.highlightColor) {
                settings.highlightColor = it
                recreate()
            }
        }

        val fontSpinner = findViewById<Spinner>(R.id.spinnerFont)
        val fontNames = FontOption.entries.map { it.name }
        fontSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fontNames)
        fontSpinner.setSelection(FontOption.entries.indexOf(settings.fontOption))
        fontSpinner.post {
            fontSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    settings.fontOption = FontOption.entries[position]
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }

        findViewById<Button>(R.id.buttonResetDefaults).setOnClickListener {
            getSharedPreferences("nextup_settings", MODE_PRIVATE).edit().clear().apply()
            recreate()
        }

        findViewById<Button>(R.id.buttonWidgetSettings).setOnClickListener {
            startActivity(android.content.Intent(this, WidgetSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.buttonTeachWords).setOnClickListener {
            startActivity(android.content.Intent(this, com.nextup.app.parser.TeachWordsActivity::class.java))
        }
    }
}
