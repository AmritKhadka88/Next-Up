package com.nextup.app.settings

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nextup.app.R
import com.nextup.app.widget.WidgetUpdater

class WidgetSettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_settings)
        settings = SettingsRepository(this)

        val filterOptions = listOf("All upcoming", "High priority only", "Today only")
        val filterSpinner = findViewById<Spinner>(R.id.spinnerWidgetFilter)
        filterSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filterOptions)
        filterSpinner.setSelection(WidgetTaskFilter.entries.indexOf(settings.widgetTaskFilter))

        val taskCountSeekBar = findViewById<SeekBar>(R.id.seekBarTaskCount)
        val taskCountPreview = findViewById<TextView>(R.id.textTaskCountPreview)
        // SeekBar range 0-19 maps to 1-20 tasks
        taskCountSeekBar.progress = (settings.widgetTaskCount - 1).coerceIn(0, 19)
        taskCountPreview.text = "${settings.widgetTaskCount} task(s)"
        taskCountSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                taskCountPreview.text = "${progress + 1} task(s)"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val fontSizeSeekBar = findViewById<SeekBar>(R.id.seekBarFontSize)
        val fontSizePreview = findViewById<TextView>(R.id.textFontSizePreview)
        // SeekBar range 0-20 maps to 10sp-30sp font size
        fontSizeSeekBar.progress = (settings.widgetFontSizeSp - 10f).toInt().coerceIn(0, 20)
        fontSizePreview.text = "${settings.widgetFontSizeSp.toInt()}sp"
        fontSizePreview.textSize = settings.widgetFontSizeSp
        fontSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sp = (10 + progress).toFloat()
                fontSizePreview.text = "${sp.toInt()}sp"
                fontSizePreview.textSize = sp
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val transparencySeekBar = findViewById<SeekBar>(R.id.seekBarTransparency)
        transparencySeekBar.progress = settings.widgetBackgroundAlpha

        findViewById<Button>(R.id.buttonWidgetFontColor).setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.widget_font_color), settings.widgetFontColor) {
                settings.widgetFontColor = it
            }
        }

        findViewById<Button>(R.id.buttonWidgetBackgroundColor).setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.widget_background_color), settings.widgetBackgroundColor) {
                settings.widgetBackgroundColor = it
            }
        }

        findViewById<Button>(R.id.buttonApplyWidgetSettings).setOnClickListener {
            settings.widgetTaskFilter = WidgetTaskFilter.entries[filterSpinner.selectedItemPosition]
            settings.widgetFontSizeSp = (10 + fontSizeSeekBar.progress).toFloat()
            settings.widgetBackgroundAlpha = transparencySeekBar.progress
            settings.widgetTaskCount = taskCountSeekBar.progress + 1
            WidgetUpdater.refreshAll(this)
            finish()
        }
    }
}
