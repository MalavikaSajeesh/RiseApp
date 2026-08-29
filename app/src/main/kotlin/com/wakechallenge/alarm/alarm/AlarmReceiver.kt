package com.wakechallenge.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.wakechallenge.alarm.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (alarmId == -1L) return

        // Briefly wake the CPU so we reliably get the foreground service started
        // even if the device was in deep sleep when the alarm fired.
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wakechallenge:alarm_receiver")
        wakeLock.acquire(15_000)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.get(context)
                val alarm = db.alarmDao().getById(alarmId)
                if (alarm != null && alarm.enabled) {
                    val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
                        putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                    AlarmScheduler.rescheduleAfterFiring(context, alarm)
                }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }
}
