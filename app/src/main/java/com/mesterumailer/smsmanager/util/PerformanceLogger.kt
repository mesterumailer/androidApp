package com.mesterumailer.smsmanager.util

import android.os.SystemClock
import android.util.Log
import com.mesterumailer.smsmanager.BuildConfig
import java.util.Locale

/**
 * Debug-only performance diagnostics.
 * Never logs SMS body, sender, or other message content.
 */
object PerformanceLogger {
    private const val TAG = "SmsManagerPerf"

    fun logDuration(stage: String, startNanos: Long) {
        if (!BuildConfig.DEBUG) return
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0
        Log.d(TAG, stage + "=" + String.format(Locale.US, "%.2f", elapsedMs) + "ms")
    }

    fun log(stage: String, message: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, stage + " " + message)
    }
}
