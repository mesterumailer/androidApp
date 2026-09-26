package com.mesterumailer.smsmanager.util

import android.os.SystemClock
import android.util.Log
import com.mesterumailer.smsmanager.BuildConfig
import java.util.Locale

/**
 * Debug-only performance diagnostics.
 *
 * Logs timing and aggregate counters only; never logs SMS body, sender, or other message content.
 */
object PerformanceLogger {
    private const val TAG = "SmsManagerPerf"

    inline fun <T> measure(stage: String, block: () -> T): T {
        if (!BuildConfig.DEBUG) return block()

        val start = SystemClock.elapsedRealtimeNanos()
        return try {
            block()
        } finally {
            logDuration(stage, start)
        }
    }

    fun logDuration(stage: String, startNanos: Long) {
        if (!BuildConfig.DEBUG) return
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0
        val formatted = String.format(Locale.US, "%.2f", elapsedMs)
        Log.d(TAG, stage + "=" + formatted + "ms")
    }

    fun log(stage: String, message: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, stage + " " + message)
    }
}
