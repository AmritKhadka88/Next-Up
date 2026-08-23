package com.nextup.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
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

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        viewPager.adapter = MainPagerAdapter(this)

        val tabTitles = listOf("List", "Daily", "Calendar")
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            QuickAddBottomSheet().show(supportFragmentManager, "quick_add")
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
        }
        return super.onOptionsItemSelected(item)
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
