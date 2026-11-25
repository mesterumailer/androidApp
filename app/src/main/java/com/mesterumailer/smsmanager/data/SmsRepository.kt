package com.mesterumailer.smsmanager.data

import android.content.ContentResolver
import android.provider.Telephony
import android.os.SystemClock
import android.util.Log
import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsMessage
import com.mesterumailer.smsmanager.util.SmsBlockFilter
import com.mesterumailer.smsmanager.util.SmsClassifier

data class SmsInboxBatch(
    val messages: List<SmsMessage>,
    val scannedCount: Int,
    val nextBeforeTimestamp: Long?,
    val nextBeforeId: Long?,
    val hasMore: Boolean
)

class SmsRepository(
    private val contentResolver: ContentResolver,
    rules: List<FilterRule>,
    private val blockFilter: SmsBlockFilter = SmsBlockFilter(emptyList(), emptyList()),
    senderWeight: Int = SmsClassifier.DEFAULT_SENDER_WEIGHT,
    private val classificationCache: SmsClassificationCache? = null
) {
    private val classifier = SmsClassifier(rules, senderWeight)

    fun getInboxBatch(
        batchSize: Int = DEFAULT_SCAN_BATCH_SIZE,
        untilTimestampExclusive: Long? = null,
        beforeTimestamp: Long? = null,
        beforeId: Long? = null,
        shouldInclude: (SmsMessage) -> Boolean = { true }
    ): SmsInboxBatch {
        val safeBatchSize = batchSize.coerceIn(1, DEFAULT_SCAN_BATCH_SIZE)
        val messages = mutableListOf<SmsMessage>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        untilTimestampExclusive?.let {
            clauses += "${Telephony.Sms.DATE} < ?"
            args += it.toString()
        }
        if (beforeTimestamp != null && beforeId != null) {
            clauses += "(${Telephony.Sms.DATE} < ? OR (${Telephony.Sms.DATE} = ? AND ${Telephony.Sms._ID} < ?))"
            args += beforeTimestamp.toString()
            args += beforeTimestamp.toString()
            args += beforeId.toString()
        }
        val selection = clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
        val selectionArgs = args.takeIf { it.isNotEmpty() }?.toTypedArray()

        val batchStartedAt = SystemClock.elapsedRealtime()
        var cacheHits = 0
        var cacheMisses = 0
        var classificationElapsedMs = 0L
        var scanned = 0
        var lastTimestamp: Long? = null
        var lastId: Long? = null
        var hasMore = false

        contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} DESC, ${Telephony.Sms._ID} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (scanned < safeBatchSize && cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val timestamp = cursor.getLong(dateIndex)
                val body = cursor.getString(bodyIndex).orEmpty()
                val address = cursor.getString(addressIndex).orEmpty()
                scanned += 1
                lastTimestamp = timestamp
                lastId = id

                if (blockFilter.isBlocked(address, body)) continue

                val classificationStartedAt = SystemClock.elapsedRealtime()
                val cachedAnalysis = classificationCache?.get(id, address, body)
                val analysis = if (cachedAnalysis != null) {
                    cacheHits += 1
                    cachedAnalysis
                } else {
                    cacheMisses += 1
                    classifier.analyze(address, body).also { freshAnalysis ->
                        classificationCache?.put(id, address, body, freshAnalysis)
                    }
                }
                classificationElapsedMs += SystemClock.elapsedRealtime() - classificationStartedAt

                val candidate = SmsMessage(
                    id = id,
                    address = address,
                    body = body,
                    timestamp = timestamp,
                    analysis = analysis
                )
                if (shouldInclude(candidate)) messages += candidate
            }
            hasMore = scanned == safeBatchSize && cursor.moveToNext()
        }

        classificationCache?.flush()
        Log.d(
            "SmsInboxPerf",
            "Repository batch: scanned=$scanned, cached=$cacheHits, classified=$cacheMisses, " +
                "classificationMs=$classificationElapsedMs, totalMs=${SystemClock.elapsedRealtime() - batchStartedAt}"
        )
        return SmsInboxBatch(messages, scanned, lastTimestamp, lastId, hasMore)
    }

    fun close() {
        runCatching { classificationCache?.flush() }
        runCatching { classificationCache?.close() }
    }

    companion object {
        const val DEFAULT_SCAN_BATCH_SIZE = 100
    }
}
