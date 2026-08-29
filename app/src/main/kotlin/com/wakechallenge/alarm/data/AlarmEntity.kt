package com.wakechallenge.alarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

/**
 * daysMask bit layout (bit 0 = Sunday ... bit 6 = Saturday), matching Calendar.DAY_OF_WEEK - 1.
 * A daysMask of 0 means "one-off alarm, fires once then disables itself".
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val daysMask: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    val primaryGoalId: Long,
    val secondaryGoalId: Long? = null,
    val vibrate: Boolean = true,
    val soundMode: SoundMode = SoundMode.SYSTEM_RINGTONE,
    val soundUri: String? = null,       // used when soundMode == SYSTEM_RINGTONE; null = device default alarm sound
    val soundDisplayName: String = "Default alarm sound",
    val ringVolume: Int = 100            // 0..100, percentage of max alarm-stream volume
) {
    fun isRepeating(): Boolean = daysMask != 0

    fun repeatsOn(calendarDayOfWeek: Int): Boolean {
        val bit = 1 shl (calendarDayOfWeek - Calendar.SUNDAY)
        return (daysMask and bit) != 0
    }

    companion object {
        fun dayBit(calendarDayOfWeek: Int): Int = 1 shl (calendarDayOfWeek - Calendar.SUNDAY)
    }
}
