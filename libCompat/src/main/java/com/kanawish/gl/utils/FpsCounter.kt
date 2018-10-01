package com.kanawish.gl.utils

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class FpsCounter(private val reporter: (Double)->Unit) {

    private val mainThread = Handler(Looper.getMainLooper())

    private var measureStart: Long = 0
    private var frameCount = FRAME_SAMPLE_SIZE

    fun log() {
        if (frameCount < FRAME_SAMPLE_SIZE) {
            frameCount++
        } else {
            report(SystemClock.elapsedRealtimeNanos())
            frameCount = 1
            measureStart = SystemClock.elapsedRealtimeNanos()
        }
    }

    private fun report(measureEnd: Long) {
        val ms: Double
        val elapsed = measureEnd - measureStart

        if (elapsed > 0) {
            ms = elapsed / FRAME_SAMPLE_SIZE.toDouble() / 1000000.0
        } else {
            ms = -1.0
        }

        mainThread.post { reporter(ms) }
    }

    companion object {
        private const val FRAME_SAMPLE_SIZE = 60
    }
}