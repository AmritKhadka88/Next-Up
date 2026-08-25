package com.nextup.app.settings

import android.graphics.Typeface
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nextup.app.R

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository
    private lateinit var fontStatusView: TextView

    private val fontPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        importFontFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        settings = SettingsRepository(this)
        fontStatusView = findViewById(R.id.textCustomFontStatus)

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

        findViewById<Button>(R.id.buttonImportFont).setOnClickListener {
            fontPickerLauncher.launch("*/*")
        }
        updateFontStatus()

        findViewById<Button>(R.id.buttonResetDefaults).setOnClickListener {
            getSharedPreferences("nextup_settings", MODE_PRIVATE).edit().clear().apply()
            settings.customFontPath = null
            recreate()
        }

        findViewById<Button>(R.id.buttonWidgetSettings).setOnClickListener {
            startActivity(android.content.Intent(this, WidgetSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.buttonTeachWords).setOnClickListener {
            startActivity(android.content.Intent(this, com.nextup.app.parser.TeachWordsActivity::class.java))
        }
    }

    private fun importFontFile(uri: android.net.Uri) {
        val name = getFileName(uri)
        if (!name.endsWith(".ttf", ignoreCase = true) && !name.endsWith(".otf", ignoreCase = true)) {
            Toast.makeText(this, "Please pick a .ttf or .otf font file", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val fontsDir = java.io.File(filesDir, "fonts").apply { mkdirs() }
            val destFile = java.io.File(fontsDir, "custom_font.${name.substringAfterLast('.')}")
            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }

            // Validate it actually loads as a typeface before committing to it.
            Typeface.createFromFile(destFile)

            settings.customFontPath = destFile.absolutePath
            Toast.makeText(this, "Font imported: $name", Toast.LENGTH_SHORT).show()
            updateFontStatus()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't load that as a font file", Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileName(uri: android.net.Uri): String {
        var name = "font"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
        }
        return name
    }

    private fun updateFontStatus() {
        val path = settings.customFontPath
        if (path != null && java.io.File(path).exists()) {
            fontStatusView.text = "Custom font active — overrides the dropdown above. Reset to defaults to remove it."
        } else {
            fontStatusView.text = "No custom font imported — using the dropdown selection above."
        }
    }
}
