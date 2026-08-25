package com.nextup.app.settings

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nextup.app.R
import com.nextup.app.alarm.NotificationHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository
    private lateinit var fontStatusView: TextView
    private lateinit var alarmSoundStatusView: TextView
    private lateinit var voiceStatusView: TextView

    private val fontPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        importFontFile(uri)
    }

    private val ringtonePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        settings.alarmSoundUri = uri?.toString()
        NotificationHelper.createChannel(this) // recreate channel so the new sound actually applies
        updateAlarmSoundStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        settings = SettingsRepository(this)
        fontStatusView = findViewById(R.id.textCustomFontStatus)
        alarmSoundStatusView = findViewById(R.id.textAlarmSoundStatus)
        voiceStatusView = findViewById(R.id.textVoiceStatus)

        setSwatch(R.id.swatchTextColor, settings.textColor)
        setSwatch(R.id.swatchBackgroundColor, settings.backgroundColor)
        setSwatch(R.id.swatchHighColor, settings.highPriorityColor)
        setSwatch(R.id.swatchMediumColor, settings.mediumPriorityColor)
        setSwatch(R.id.swatchNormalColor, settings.normalPriorityColor)
        setSwatch(R.id.swatchHighlightColor, settings.highlightColor)

        findViewById<LinearLayout>(R.id.rowTextColor).setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.text_color), settings.textColor) {
                settings.textColor = it
                recreate()
            }
        }
        findViewById<LinearLayout>(R.id.rowBackgroundColor).setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.background_color), settings.backgroundColor) {
                settings.backgroundColor = it
                recreate()
            }
        }
        findViewById<LinearLayout>(R.id.rowHighColor).setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.high_priority_color), settings.highPriorityColor) {
                settings.highPriorityColor = it
                recreate()
            }
        }
        findViewById<LinearLayout>(R.id.rowMediumColor).setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.medium_priority_color), settings.mediumPriorityColor) {
                settings.mediumPriorityColor = it
                recreate()
            }
        }
        findViewById<LinearLayout>(R.id.rowNormalColor).setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.normal_priority_color), settings.normalPriorityColor) {
                settings.normalPriorityColor = it
                recreate()
            }
        }
        findViewById<LinearLayout>(R.id.rowHighlightColor).setOnClickListener {
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

        findViewById<LinearLayout>(R.id.rowImportFont).setOnClickListener {
            fontPickerLauncher.launch("*/*")
        }
        updateFontStatus()

        findViewById<LinearLayout>(R.id.rowAlarmSound).setOnClickListener {
            val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                val current = settings.alarmSoundUri?.let { Uri.parse(it) }
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
            }
            ringtonePickerLauncher.launch(intent)
        }
        updateAlarmSoundStatus()

        findViewById<LinearLayout>(R.id.rowVoice).setOnClickListener {
            VoicePickerDialog.show(this, settings.ttsVoiceName) { voice ->
                settings.ttsVoiceName = voice.name
                updateVoiceStatus()
            }
        }
        updateVoiceStatus()

        findViewById<LinearLayout>(R.id.rowResetDefaults).setOnClickListener {
            getSharedPreferences("nextup_settings", MODE_PRIVATE).edit().clear().apply()
            settings.customFontPath = null
            NotificationHelper.createChannel(this)
            recreate()
        }

        findViewById<LinearLayout>(R.id.rowWidgetSettings).setOnClickListener {
            startActivity(android.content.Intent(this, WidgetSettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowTeachWords).setOnClickListener {
            startActivity(android.content.Intent(this, com.nextup.app.parser.TeachWordsActivity::class.java))
        }
    }

    private fun setSwatch(viewId: Int, color: Int) {
        val swatch = findViewById<android.view.View>(viewId)
        (swatch.background as? GradientDrawable)?.setColor(color)
    }

    private fun importFontFile(uri: Uri) {
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
            Typeface.createFromFile(destFile) // validates it actually loads before committing

            settings.customFontPath = destFile.absolutePath
            Toast.makeText(this, "Font imported: $name", Toast.LENGTH_SHORT).show()
            updateFontStatus()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't load that as a font file", Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "font"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
        }
        return name
    }

    private fun updateFontStatus() {
        val path = settings.customFontPath
        fontStatusView.text = if (path != null && java.io.File(path).exists()) {
            "Custom font active — overrides the dropdown above"
        } else {
            "No custom font imported"
        }
    }

    private fun updateAlarmSoundStatus() {
        val uriString = settings.alarmSoundUri
        alarmSoundStatusView.text = if (uriString == null) {
            "System default"
        } else {
            try {
                RingtoneManager.getRingtone(this, Uri.parse(uriString))?.getTitle(this) ?: "Custom sound"
            } catch (e: Exception) {
                "Custom sound"
            }
        }
    }

    private fun updateVoiceStatus() {
        val name = settings.ttsVoiceName
        voiceStatusView.text = name?.replace(Regex("""[_\-.]"""), " ")?.trim() ?: "System default"
    }
}
