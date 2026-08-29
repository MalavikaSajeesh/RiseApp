package com.wakechallenge.alarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.wakechallenge.alarm.data.AppDatabase
import com.wakechallenge.alarm.ring.AlarmRingActivity
import com.wakechallenge.alarm.util.MotionStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the alarm sound + vibration + motion-based volume ducking
 * for as long as an alarm is ringing. AlarmRingActivity binds to this to know when the
 * user has completed a challenge and the alarm should actually stop.
 */
class AlarmRingingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var sensorManager: SensorManager? = null
    private var motionMonitor: MotionStateMonitor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var alarmId: Long = -1
    private val binder = LocalBinder()

    // Volume levels the ducking logic switches between (0f..1f), set from the alarm's
    // chosen ringVolume once it starts ringing; these defaults are just a fallback.
    private var fullVolume = 1.0f
    private var duckedVolume = 0.3f

    inner class LocalBinder : Binder() {
        fun getService(): AlarmRingingService = this@AlarmRingingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        alarmId = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
        if (alarmId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification())
        startRinging()
        startMotionMonitor()
        return START_STICKY
    }

    fun getAlarmId(): Long = alarmId

    /** Called by AlarmRingActivity once the user has completed a challenge. */
    fun completeChallenge() {
        stopRingingAndCleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "wakechallenge:ringing_service"
        ).apply { acquire(10 * 60 * 1000L) } // safety cap: 10 minutes
    }

    private fun startRinging() {
        scope.launch {
            val db = AppDatabase.get(this@AlarmRingingService)
            val alarm = db.alarmDao().getById(alarmId)
            val volumeFraction = ((alarm?.ringVolume ?: 100).coerceIn(5, 100)) / 100f

            val soundUri = resolveSoundUri(db, alarm)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                try {
                    setDataSource(this@AlarmRingingService, soundUri)
                } catch (e: Exception) {
                    // Track might have been deleted/moved since it was added to the pool —
                    // fall back to the device's default alarm sound so the alarm still rings.
                    reset()
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmRingingService, defaultAlarmUri())
                }
                isLooping = true
                setVolume(volumeFraction, volumeFraction)
                prepare()
                start()
            }
            this@AlarmRingingService.fullVolume = volumeFraction
            this@AlarmRingingService.duckedVolume = volumeFraction * 0.3f

            if (alarm?.vibrate == true) startVibration()
        }
    }

    private suspend fun resolveSoundUri(db: AppDatabase, alarm: com.wakechallenge.alarm.data.AlarmEntity?): android.net.Uri {
        if (alarm?.soundMode == com.wakechallenge.alarm.data.SoundMode.RANDOM_FROM_POOL) {
            val pool = db.musicPoolDao().getAllOnce()
            if (pool.isNotEmpty()) {
                return android.net.Uri.parse(pool.random().uri)
            }
            // Empty pool — fall through to default/system ringtone below.
        }
        val specific = alarm?.soundUri
        return if (!specific.isNullOrEmpty()) android.net.Uri.parse(specific) else defaultAlarmUri()
    }

    private fun defaultAlarmUri(): android.net.Uri =
        RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    private fun startVibration() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun startMotionMonitor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        motionMonitor = MotionStateMonitor { moving ->
            val target = if (moving) duckedVolume else fullVolume
            mediaPlayer?.setVolume(target, target)
            if (moving) vibrator?.cancel() else if (mediaPlayer != null) restartVibrationIfNeeded()
        }
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel != null) {
            sensorManager?.registerListener(motionMonitor, accel, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun restartVibrationIfNeeded() {
        scope.launch {
            val alarm = AppDatabase.get(this@AlarmRingingService).alarmDao().getById(alarmId)
            if (alarm?.vibrate == true) startVibration()
        }
    }

    private fun stopRingingAndCleanup() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        motionMonitor?.let { sensorManager?.unregisterListener(it) }
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    override fun onDestroy() {
        stopRingingAndCleanup()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Alarm", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm ringing notifications"
                setSound(null, null) // the MediaPlayer handles alarm audio, not the notification
            }
            nm.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(this, AlarmRingActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, alarmId.toInt(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alarm")
            .setContentText("Complete your wake-up challenge to dismiss")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "alarm_ringing"
        private const val NOTIFICATION_ID = 42
    }
}
