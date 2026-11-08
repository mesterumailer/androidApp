package com.mesterumailer.smsmanager.data

import android.content.ContentResolver
import android.provider.Telephony
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
    senderWeight: Int = SmsClassifier.DEFAULT_SENDER_WEIGHT
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

                val candidate = SmsMessage(
                    id = id,
                    address = address,
                    body = body,
                    timestamp = timestamp,
                    analysis = classifier.analyze(address, body)
                )
                if (shouldInclude(candidate)) messages += candidate
            }
            hasMore = scanned == safeBatchSize && cursor.moveToNext()
        }

        return SmsInboxBatch(messages, scanned, lastTimestamp, lastId, hasMore)
    }

    companion object {
        const val DEFAULT_SCAN_BATCH_SIZE = 100
    }
}
