package com.mesterumailer.smsmanager.data

import android.content.ContentResolver
import android.provider.Telephony
import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsMessage
import com.mesterumailer.smsmanager.util.SmsBlockFilter
import com.mesterumailer.smsmanager.util.SmsClassifier
import com.mesterumailer.smsmanager.util.PerformanceLogger

class SmsRepository(
    private val contentResolver: ContentResolver,
    rules: List<FilterRule>,
    private val blockFilter: SmsBlockFilter = SmsBlockFilter(emptyList(), emptyList())
) {
    private val classifier = SmsClassifier(rules)

    fun getInbox(
        limit: Int = SmsSettingsRepository.DEFAULT_INBOX_LIMIT,
        untilTimestampExclusive: Long? = null
    ): List<SmsMessage> {
        val totalStart = if (android.util.Log.isLoggable("SmsManagerPerf", android.util.Log.DEBUG)) {
            android.os.SystemClock.elapsedRealtimeNanos()
        } else {
            0L
        }
        var classifyNanos = 0L
        var blockedCount = 0
        val messages = mutableListOf<SmsMessage>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val selection = untilTimestampExclusive?.let { "${Telephony.Sms.DATE} < ?" }
        val selectionArgs = untilTimestampExclusive?.let { arrayOf(it.toString()) }

        contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (cursor.moveToNext() && messages.size < limit) {
                val body = cursor.getString(bodyIndex).orEmpty()
                val address = cursor.getString(addressIndex).orEmpty()

                // Blocked messages are discarded before classification, extraction or UI creation.
                if (blockFilter.isBlocked(address, body)) {
                    blockedCount += 1
                    continue
                }

                val classifyStart = if (totalStart != 0L) {
                    android.os.SystemClock.elapsedRealtimeNanos()
                } else {
                    0L
                }
                val analysis = classifier.analyze(address, body)
                if (classifyStart != 0L) {
                    classifyNanos += android.os.SystemClock.elapsedRealtimeNanos() - classifyStart
                }

                messages += SmsMessage(
                    id = cursor.getLong(idIndex),
                    address = address,
                    body = body,
                    timestamp = cursor.getLong(dateIndex),
                    analysis = analysis
                )
            }
        }

        if (totalStart != 0L) {
            PerformanceLogger.logDuration("inbox_query_and_read", totalStart)
            val classifyMs = classifyNanos / 1_000_000.0
            PerformanceLogger.log(
                "inbox_query_breakdown",
                "accepted=" + messages.size +
                    " blocked=" + blockedCount +
                    " classify=" + String.format(java.util.Locale.US, "%.2f", classifyMs) +
                    "ms limit=" + limit
            )
        }

        return messages
    }
}
