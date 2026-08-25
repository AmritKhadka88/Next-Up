package com.nextup.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nextup.app.data.TaskDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Alarms registered with AlarmManager are wiped on reboot, so every task that still
        // has an active alarm needs to be re-scheduled from what's in the database.
        CoroutineScope(Dispatchers.IO).launch {
            val tasks = TaskDatabase.getInstance(context).taskDao().getUpcomingTasksSync()
            tasks.filter { it.hasAlarm && !it.isCompleted }.forEach { task ->
                AlarmScheduler.schedule(context, task)
            }
        }
    }
}
