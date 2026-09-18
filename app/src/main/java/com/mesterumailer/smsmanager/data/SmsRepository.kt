package com.mesterumailer.smsmanager.data

import android.content.ContentResolver
import android.provider.Telephony
import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsMessage
import com.mesterumailer.smsmanager.util.SmsClassifier

class SmsRepository(
    private val contentResolver: ContentResolver,
    rules: List<FilterRule>
) {
    private val classifier = SmsClassifier(rules)

    fun getInbox(
        limit: Int = SmsSettingsRepository.DEFAULT_INBOX_LIMIT,
        untilTimestampExclusive: Long? = null
    ): List<SmsMessage> {
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
                messages += SmsMessage(
                    id = cursor.getLong(idIndex),
                    address = address,
                    body = body,
                    timestamp = cursor.getLong(dateIndex),
                    analysis = classifier.analyze(address, body)
                )
            }
        }

        return messages
    }
}
