package com.wakechallenge.alarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromGoalType(type: GoalType): String = type.name

    @TypeConverter
    fun toGoalType(value: String): GoalType = GoalType.valueOf(value)

    @TypeConverter
    fun fromSoundMode(mode: SoundMode): String = mode.name

    @TypeConverter
    fun toSoundMode(value: String): SoundMode = SoundMode.valueOf(value)
}

@Database(
    entities = [AlarmEntity::class, GoalEntity::class, MusicPoolEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun goalDao(): GoalDao
    abstract fun musicPoolDao(): MusicPoolDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wake_challenge.db"
                )
                    // Simple hobby-app migration strategy: wipes local data on schema
                    // changes rather than shipping a full Migration. Fine pre-release;
                    // replace with real Migrations if you need to preserve alarms across updates.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
