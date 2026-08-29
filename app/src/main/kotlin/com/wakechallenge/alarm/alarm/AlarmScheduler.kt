package com.wakechallenge.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.wakechallenge.alarm.data.AlarmEntity
import java.util.Calendar

object AlarmScheduler {

    const val EXTRA_ALARM_ID = "extra_alarm_id"

    fun schedule(context: Context, alarm: AlarmEntity) {
        if (!alarm.enabled) {
            cancel(context, alarm.id)
            return
        }
        val triggerAt = nextTriggerMillis(alarm)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, alarm.id)

        if (am.canScheduleExactAlarms()) {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pi), pi)
        } else {
            // Fall back to an inexact-but-close alarm if the user hasn't granted exact-alarm permission.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context, alarmId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, alarmId))
    }

    /** Called right when an alarm fires, to line up the next occurrence for repeating alarms. */
    fun rescheduleAfterFiring(context: Context, alarm: AlarmEntity) {
        if (alarm.isRepeating() && alarm.enabled) {
            schedule(context, alarm)
        }
    }

    private fun pendingIntent(context: Context, alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTriggerMillis(alarm: AlarmEntity): Long {
        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (!alarm.isRepeating()) {
            if (candidate.before(now)) candidate.add(Calendar.DAY_OF_YEAR, 1)
            return candidate.timeInMillis
        }

        // Repeating: walk forward day by day (max 7) until we find an enabled day at/after now.
        for (i in 0..7) {
            val check = candidate.clone() as Calendar
            check.add(Calendar.DAY_OF_YEAR, i)
            val dow = check.get(Calendar.DAY_OF_WEEK)
            if (alarm.repeatsOn(dow) && check.after(now)) {
                return check.timeInMillis
            }
        }
        // Shouldn't happen, but fall back to tomorrow same time.
        candidate.add(Calendar.DAY_OF_YEAR, 1)
        return candidate.timeInMillis
    }
}
