package com.nextup.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.nextup.app.R
import com.nextup.app.settings.SettingsActivity
import com.nextup.app.settings.SettingsRepository

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        com.nextup.app.alarm.NotificationHelper.createChannel(this)
        requestAlarmPermissionsIfNeeded()

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        viewPager.adapter = MainPagerAdapter(this)

        val tabTitles = listOf("List", "Daily", "Calendar")
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        findViewById<FloatingActionButton>(R.id.fabAdd).apply {
            setOnClickListener {
                QuickAddBottomSheet().show(supportFragmentManager, "quick_add")
            }
            setOnLongClickListener {
                startActivity(Intent(this@MainActivity, com.nextup.app.voice.VoiceNoteActivity::class.java))
                true
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* result not critical to act on here — alarms still schedule either way */ }

    private fun requestAlarmPermissionsIfNeeded() {
        // Android 13+ requires explicit runtime permission to post notifications at all.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Android 12+ requires a separate special permission for exact alarms — this can't
        // be requested inline, it has to send the user to a system settings screen.
        if (!com.nextup.app.alarm.AlarmScheduler.canScheduleExactAlarms(this)) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Allow exact alarms")
                .setMessage("For task alarms to fire at the right time, Next-Up needs the \"Alarms & reminders\" permission.")
                .setPositiveButton("Open settings") { _, _ ->
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                }
                .setNegativeButton("Not now", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-applying here so a color/font change in Settings reflects immediately on return.
        val settings = SettingsRepository(this)
        findViewById<android.view.View>(android.R.id.content).setBackgroundColor(settings.backgroundColor)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
            R.id.action_filter -> {
                FilterDialog.show(this)
                return true
            }
            R.id.action_search -> {
                SearchDialog.show(this)
                return true
            }
            R.id.action_clear_completed -> {
                confirmClearCompleted()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun confirmClearCompleted() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete completed tasks?")
            .setMessage("This removes every task currently marked done. This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val dao = com.nextup.app.data.TaskDatabase.getInstance(this@MainActivity).taskDao()
                    dao.deleteCompleted()
                    com.nextup.app.widget.WidgetUpdater.refreshAll(this@MainActivity)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> TaskListFragment.newInstance(daily = false)
                1 -> TaskListFragment.newInstance(daily = true)
                else -> CalendarFragment()
            }
        }
    }
}
