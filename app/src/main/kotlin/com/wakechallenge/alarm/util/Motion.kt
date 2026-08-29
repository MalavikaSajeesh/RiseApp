package com.wakechallenge.alarm.util

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Counts discrete movements (a step, a jumping jack) from accelerometer peaks.
 * Works by watching the magnitude of acceleration with gravity removed, and counting
 * every time it crosses above `thresholdG` after having settled back down, with a
 * minimum gap between counts so a single motion isn't counted twice.
 */
class MotionRepCounter(
    private val thresholdG: Float,
    private val minIntervalMs: Long,
    private val onRep: () -> Unit
) : SensorEventListener {

    private var armed = true
    private var lastRepTime = 0L
    private val gravity = FloatArray(3)

    override fun onSensorChanged(event: SensorEvent) {
        // Simple low-pass filter to isolate gravity, then subtract it to get linear acceleration.
        val alpha = 0.8f
        for (i in 0..2) {
            gravity[i] = alpha * gravity[i] + (1 - alpha) * event.values[i]
        }
        val lx = event.values[0] - gravity[0]
        val ly = event.values[1] - gravity[1]
        val lz = event.values[2] - gravity[2]
        val magnitude = sqrt((lx * lx + ly * ly + lz * lz).toDouble()).toFloat() / SensorManager.GRAVITY_EARTH

        val now = System.currentTimeMillis()
        if (armed && magnitude > thresholdG && now - lastRepTime > minIntervalMs) {
            armed = false
            lastRepTime = now
            onRep()
        } else if (magnitude < thresholdG * 0.4f) {
            armed = true
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        fun forSteps(onRep: () -> Unit) = MotionRepCounter(thresholdG = 0.35f, minIntervalMs = 300, onRep = onRep)
        fun forJumpingJacks(onRep: () -> Unit) = MotionRepCounter(thresholdG = 0.9f, minIntervalMs = 500, onRep = onRep)
    }
}

/**
 * Classifies the phone as MOVING or STILL based on rolling variance of acceleration
 * magnitude, so the ringing service can duck the volume once you visibly pick the
 * phone up / sit up, and bring it back up if you set it back down.
 */
class MotionStateMonitor(
    private val onStateChanged: (moving: Boolean) -> Unit
) : SensorEventListener {

    private val window = ArrayDeque<Float>()
    private val windowSizeMs = 2000L
    private val timestamps = ArrayDeque<Long>()
    private var currentlyMoving = false
    private var lastFlipTime = 0L
    private val debounceMs = 3000L // require the new state to hold for this long before flipping

    override fun onSensorChanged(event: SensorEvent) {
        val magnitude = sqrt(
            (event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2]).toDouble()
        ).toFloat()

        val now = System.currentTimeMillis()
        window.addLast(magnitude)
        timestamps.addLast(now)
        while (timestamps.isNotEmpty() && now - timestamps.first() > windowSizeMs) {
            timestamps.removeFirst()
            window.removeFirst()
        }
        if (window.size < 5) return

        val mean = window.average()
        val variance = window.sumOf { (it - mean) * (it - mean) } / window.size
        val moving = variance > MOVING_VARIANCE_THRESHOLD

        if (moving != currentlyMoving) {
            if (lastFlipTime == 0L) lastFlipTime = now
            if (now - lastFlipTime > debounceMs) {
                currentlyMoving = moving
                lastFlipTime = 0L
                onStateChanged(currentlyMoving)
            }
        } else {
            lastFlipTime = 0L
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val MOVING_VARIANCE_THRESHOLD = 1.6f
    }
}
