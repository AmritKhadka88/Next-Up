package com.nextup.app.widget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nextup.app.ui.QuickAddBottomSheet

/**
 * Launched only from the home screen widget's + button. It has a fully transparent theme
 * so tapping the widget appears to open just the quick-add sheet with the keyboard,
 * not the whole app. Finishes itself as soon as the sheet is dismissed.
 */
class QuickAddWidgetActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        QuickAddBottomSheet.newInstanceForWidget().show(supportFragmentManager, "quick_add_widget")
    }
}
