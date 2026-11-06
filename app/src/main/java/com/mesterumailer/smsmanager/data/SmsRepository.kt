package com.mesterumailer.smsmanager.data

import android.content.ContentResolver
import android.provider.Telephony
import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsMessage
import com.mesterumailer.smsmanager.util.SmsBlockFilter
import com.mesterumailer.smsmanager.util.SmsClassifier

class SmsRepository(
    private val contentResolver: ContentResolver,
    rules: List<FilterRule>,
    private val blockFilter: SmsBlockFilter = SmsBlockFilter(emptyList(), emptyList()),
    senderWeight: Int = SmsClassifier.DEFAULT_SENDER_WEIGHT
) {
    private val classifier = SmsClassifier(rules, senderWeight)

    fun getInbox(
        limit: Int = SmsSettingsRepository.DEFAULT_INBOX_LIMIT,
        untilTimestampExclusive: Long? = null,
        shouldInclude: (SmsMessage) -> Boolean = { true }
    ): List<SmsMessage> {
        val targetCount = SmsSettingsRepository.clampInboxLimit(limit)
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

            while (cursor.moveToNext() && messages.size < targetCount) {
                val body = cursor.getString(bodyIndex).orEmpty()
                val address = cursor.getString(addressIndex).orEmpty()

                // Blocked messages are discarded before classification, extraction or UI creation.
                if (blockFilter.isBlocked(address, body)) continue

                val candidate = SmsMessage(
                    id = cursor.getLong(idIndex),
                    address = address,
                    body = body,
                    timestamp = cursor.getLong(dateIndex),
                    analysis = classifier.analyze(address, body)
                )
                // Keep scanning older messages until the requested number of final,
                // display-ready Inbox entries has been collected.
                if (shouldInclude(candidate)) messages += candidate
            }
        }

        return messages
    }
}
