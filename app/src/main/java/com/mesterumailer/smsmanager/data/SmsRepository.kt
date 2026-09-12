package com.mesterumailer.smsmanager.data

import android.content.ContentResolver
import android.provider.Telephony
import com.mesterumailer.smsmanager.model.SmsMessage
import com.mesterumailer.smsmanager.util.SmsClassifier

class SmsRepository(
    private val contentResolver: ContentResolver,
    private val classifier: SmsClassifier = SmsClassifier()
) {
    fun getInbox(limit: Int = 200): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (cursor.moveToNext() && messages.size < limit) {
                val body = cursor.getString(bodyIndex).orEmpty()
                messages += SmsMessage(
                    id = cursor.getLong(idIndex),
                    address = cursor.getString(addressIndex).orEmpty(),
                    body = body,
                    timestamp = cursor.getLong(dateIndex),
                    analysis = classifier.analyze(body)
                )
            }
        }

        return messages
    }
}
