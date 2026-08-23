package com.nextup.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Priority { HIGH, MEDIUM, NORMAL }
enum class DeadlineType { ON, BY, TILL }
enum class SourceType { MANUAL, HANDWRITING, SPEECH }

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val title: String,
    val category: String? = null,
    val amount: Double? = null,
    val recipient: String? = null,

    val priority: Priority = Priority.NORMAL,
    val deadlineType: DeadlineType = DeadlineType.ON,

    val dueDate: Long,                     // epoch millis, midnight of due day
    val dueTime: Long? = null,             // epoch millis representing time-of-day, nullable

    val startHighlightDate: Long? = null,  // used for TILL range highlighting in calendar

    val hasAlarm: Boolean = false,
    val alarmId: Int? = null,              // AlarmManager request code, for cancel/update

    val isDailyTask: Boolean = false,      // true -> shows in "Daily" tab instead of main list

    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),

    val sourceType: SourceType = SourceType.MANUAL
)
