package com.nextup.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    // Main list tab: non-daily tasks, ordered by priority then due date
    @Query("""
        SELECT * FROM tasks
        WHERE isDailyTask = 0
        ORDER BY
            CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END,
            dueDate ASC
    """)
    fun getMainTasks(): Flow<List<Task>>

    // Daily tasks tab
    @Query("""
        SELECT * FROM tasks
        WHERE isDailyTask = 1
        ORDER BY
            CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END,
            dueTime ASC
    """)
    fun getDailyTasks(): Flow<List<Task>>

    // Tasks for a specific calendar day, including TILL-range tasks that span over it
    @Query("""
        SELECT * FROM tasks
        WHERE (dueDate = :dayStart)
           OR (deadlineType = 'TILL' AND startHighlightDate <= :dayStart AND dueDate >= :dayStart)
        ORDER BY dueTime ASC
    """)
    fun getTasksForDay(dayStart: Long): Flow<List<Task>>

    // All tasks within a month range, used to compute which days get a dot/highlight
    @Query("""
        SELECT * FROM tasks
        WHERE (dueDate BETWEEN :monthStart AND :monthEnd)
           OR (deadlineType = 'TILL' AND startHighlightDate <= :monthEnd AND dueDate >= :monthStart)
    """)
    fun getTasksInRange(monthStart: Long, monthEnd: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Query("UPDATE tasks SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    // Synchronous (non-suspend) on purpose: RemoteViewsFactory methods are called by the
    // widget host on its own background thread and cannot use coroutines, so a direct
    // blocking Room query is the correct approach here.
    @Query("""
        SELECT * FROM tasks
        WHERE isCompleted = 0 AND isDailyTask = 0
        ORDER BY dueDate ASC, dueTime ASC
        LIMIT 50
    """)
    fun getUpcomingTasksSync(): List<Task>
}
