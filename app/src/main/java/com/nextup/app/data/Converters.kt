package com.nextup.app.data

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromPriority(priority: Priority): String = priority.name

    @TypeConverter
    fun toPriority(value: String): Priority = Priority.valueOf(value)

    @TypeConverter
    fun fromDeadlineType(type: DeadlineType): String = type.name

    @TypeConverter
    fun toDeadlineType(value: String): DeadlineType = DeadlineType.valueOf(value)

    @TypeConverter
    fun fromSourceType(type: SourceType): String = type.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = SourceType.valueOf(value)
}
