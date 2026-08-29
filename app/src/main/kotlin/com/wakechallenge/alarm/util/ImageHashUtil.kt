package com.wakechallenge.alarm.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlin.math.abs

/**
 * Lightweight, dependency-free image comparison so the photo challenge doesn't need
 * ML/OpenCV: an 8x8 average-hash (aHash) for "does this look roughly like the reference",
 * plus a simple gradient-variance check for "is this photo actually in focus".
 */
object ImageHashUtil {

    /** Computes a 64-bit average hash: each bit is 1 if that cell is brighter than the mean. */
    fun averageHash(bitmap: Bitmap): Long {
        val size = 8
        val small = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val gray = IntArray(size * size)
        var sum = 0L
        for (y in 0 until size) {
            for (x in 0 until size) {
                val pixel = small.getPixel(x, y)
                val luminance = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                gray[y * size + x] = luminance
                sum += luminance
            }
        }
        val mean = sum / (size * size)
        var hash = 0L
        for (i in gray.indices) {
            if (gray[i] >= mean) hash = hash or (1L shl i)
        }
        return hash
    }

    /** Number of differing bits between two hashes (0 = identical, 64 = completely different). */
    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * Rough focus/clarity score using average absolute difference between horizontally
     * adjacent pixels on a downsampled grayscale copy. Blurry/out-of-focus photos have
     * much lower edge energy than sharp ones.
     */
    fun sharpnessScore(bitmap: Bitmap): Double {
        val w = 200
        val h = (bitmap.height.toDouble() / bitmap.width * w).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)

        val gray = Array(h) { y -> IntArray(w) { x ->
            val p = small.getPixel(x, y)
            (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
        } }

        var total = 0L
        var count = 0
        for (y in 0 until h) {
            for (x in 0 until w - 1) {
                total += abs(gray[y][x] - gray[y][x + 1])
                count++
            }
        }
        return if (count == 0) 0.0 else total.toDouble() / count
    }

    fun decodeSampled(path: String, reqWidth: Int = 800): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (opts.outWidth / (sample * 2) >= reqWidth) sample *= 2
        val realOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, realOpts)
    }
}
