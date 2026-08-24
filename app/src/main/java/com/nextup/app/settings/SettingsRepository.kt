package com.nextup.app.settings

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface

enum class FontOption { DEFAULT, SERIF, SANS_SERIF, MONOSPACE;

    fun toTypeface(): Typeface = when (this) {
        DEFAULT -> Typeface.DEFAULT
        SERIF -> Typeface.SERIF
        SANS_SERIF -> Typeface.SANS_SERIF
        MONOSPACE -> Typeface.MONOSPACE
    }
}

enum class WidgetTaskFilter { ALL_UPCOMING, HIGH_PRIORITY_ONLY, TODAY_ONLY }

/**
 * Central place for all user-customizable appearance settings.
 * Backed by SharedPreferences so it persists across app restarts, on-device only.
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var textColor: Int
        get() = prefs.getInt(KEY_TEXT_COLOR, Color.parseColor("#212121"))
        set(value) = prefs.edit().putInt(KEY_TEXT_COLOR, value).apply()

    var backgroundColor: Int
        get() = prefs.getInt(KEY_BACKGROUND_COLOR, Color.WHITE)
        set(value) = prefs.edit().putInt(KEY_BACKGROUND_COLOR, value).apply()

    var highPriorityColor: Int
        get() = prefs.getInt(KEY_HIGH_COLOR, Color.parseColor("#E53935"))
        set(value) = prefs.edit().putInt(KEY_HIGH_COLOR, value).apply()

    var mediumPriorityColor: Int
        get() = prefs.getInt(KEY_MEDIUM_COLOR, Color.parseColor("#FB8C00"))
        set(value) = prefs.edit().putInt(KEY_MEDIUM_COLOR, value).apply()

    var normalPriorityColor: Int
        get() = prefs.getInt(KEY_NORMAL_COLOR, Color.parseColor("#BDBDBD"))
        set(value) = prefs.edit().putInt(KEY_NORMAL_COLOR, value).apply()

    /** Used for the calendar's TILL-range day highlighting. */
    var highlightColor: Int
        get() = prefs.getInt(KEY_HIGHLIGHT_COLOR, Color.parseColor("#FFF9C4"))
        set(value) = prefs.edit().putInt(KEY_HIGHLIGHT_COLOR, value).apply()

    var fontOption: FontOption
        get() = FontOption.valueOf(prefs.getString(KEY_FONT, FontOption.DEFAULT.name) ?: FontOption.DEFAULT.name)
        set(value) = prefs.edit().putString(KEY_FONT, value.name).apply()

    // --- Widget-specific appearance/content settings ---

    var widgetTaskFilter: WidgetTaskFilter
        get() = WidgetTaskFilter.valueOf(
            prefs.getString(KEY_WIDGET_FILTER, WidgetTaskFilter.ALL_UPCOMING.name) ?: WidgetTaskFilter.ALL_UPCOMING.name
        )
        set(value) = prefs.edit().putString(KEY_WIDGET_FILTER, value.name).apply()

    /** Font size in SP for widget task rows. */
    var widgetFontSizeSp: Float
        get() = prefs.getFloat(KEY_WIDGET_FONT_SIZE, 14f)
        set(value) = prefs.edit().putFloat(KEY_WIDGET_FONT_SIZE, value).apply()

    var widgetFontColor: Int
        get() = prefs.getInt(KEY_WIDGET_FONT_COLOR, Color.parseColor("#212121"))
        set(value) = prefs.edit().putInt(KEY_WIDGET_FONT_COLOR, value).apply()

    var widgetBackgroundColor: Int
        get() = prefs.getInt(KEY_WIDGET_BG_COLOR, Color.WHITE)
        set(value) = prefs.edit().putInt(KEY_WIDGET_BG_COLOR, value).apply()

    /** 0 = fully transparent, 255 = fully opaque. */
    var widgetBackgroundAlpha: Int
        get() = prefs.getInt(KEY_WIDGET_BG_ALPHA, 230)
        set(value) = prefs.edit().putInt(KEY_WIDGET_BG_ALPHA, value.coerceIn(0, 255)).apply()

    companion object {
        private const val PREFS_NAME = "nextup_settings"
        private const val KEY_TEXT_COLOR = "text_color"
        private const val KEY_BACKGROUND_COLOR = "background_color"
        private const val KEY_HIGH_COLOR = "high_priority_color"
        private const val KEY_MEDIUM_COLOR = "medium_priority_color"
        private const val KEY_NORMAL_COLOR = "normal_priority_color"
        private const val KEY_HIGHLIGHT_COLOR = "highlight_color"
        private const val KEY_FONT = "font_option"
        private const val KEY_WIDGET_FILTER = "widget_task_filter"
        private const val KEY_WIDGET_FONT_SIZE = "widget_font_size"
        private const val KEY_WIDGET_FONT_COLOR = "widget_font_color"
        private const val KEY_WIDGET_BG_COLOR = "widget_bg_color"
        private const val KEY_WIDGET_BG_ALPHA = "widget_bg_alpha"

        /** A small curated palette for the color picker dialog — keeps the UI simple on mobile. */
        val PRESET_COLORS = listOf(
            "#212121", "#FFFFFF", "#E53935", "#FB8C00", "#FDD835",
            "#43A047", "#1E88E5", "#8E24AA", "#6D4C41", "#BDBDBD",
            "#FFF9C4", "#FFCDD2", "#C8E6C9", "#BBDEFB", "#000000"
        ).map { Color.parseColor(it) }
    }
}
