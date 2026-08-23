package com.nextup.app.settings

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog

/**
 * Shows a simple grid of preset colors for the user to pick from.
 * Kept intentionally simple (no HSV wheel) so it stays usable and legible on a phone screen.
 */
object ColorPickerDialog {

    fun show(context: Context, title: String, currentColor: Int, onColorChosen: (Int) -> Unit) {
        val grid = GridLayout(context).apply {
            columnCount = 5
            setPadding(32, 32, 32, 32)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(grid)
            .setNegativeButton("Cancel", null)
            .create()

        for (color in SettingsRepository.PRESET_COLORS) {
            val swatch = ImageView(context).apply {
                val size = 120
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(12, 12, 12, 12)
                }
                setBackgroundColor(color)
                if (color == currentColor) {
                    elevation = 12f
                }
            }
            swatch.setOnClickListener {
                onColorChosen(color)
                dialog.dismiss()
            }
            grid.addView(swatch)
        }

        dialog.window?.setGravity(Gravity.CENTER)
        dialog.show()
    }
}
