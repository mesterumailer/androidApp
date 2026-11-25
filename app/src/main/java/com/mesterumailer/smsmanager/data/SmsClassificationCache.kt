package com.mesterumailer.smsmanager.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteException
import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsAnalysis
import com.mesterumailer.smsmanager.model.SmsCategory
import java.security.MessageDigest

/**
 * On-device cache for deterministic SMS classifications.
 *
 * Only hashes of message text/sender are persisted; message bodies and sender addresses
 * are never copied into this cache. Results are reused only while the classifier rules,
 * sender weight, message body and sender are unchanged.
 */
class SmsClassificationCache(
    context: Context,
    rules: List<FilterRule>,
    senderWeight: Int
) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    private data class CacheEntry(
        val messageId: Long,
        val bodyHash: String,
        val addressHash: String,
        val rulesFingerprint: String,
        val categoryId: String,
        val otpCode: String?,
        val amount: String?,
        val confidence: Float,
        val lastAccessed: Long
    )

    private val lock = Any()
    private val rulesFingerprint = sha256(
        "classifier-engine-v2|senderWeight=$senderWeight|" +
            rules.joinToString(separator = "\u001e") { it.toJson().toString() }
    )
    private val entries = LinkedHashMap<Long, CacheEntry>(256, 0.75f, true)
    private val pendingWrites = LinkedHashMap<Long, CacheEntry>()

    init {
        loadRecentEntries()
    }

    fun get(messageId: Long, address: String, body: String): SmsAnalysis? {
        val bodyHash = sha256(body)
        val addressHash = sha256(address)
        synchronized(lock) {
            val entry = entries[messageId] ?: return null
            if (entry.bodyHash != bodyHash ||
                entry.addressHash != addressHash ||
                entry.rulesFingerprint != rulesFingerprint
            ) return null

            val touched = entry.copy(lastAccessed = System.currentTimeMillis())
            entries[messageId] = touched
            pendingWrites[messageId] = touched
            return SmsAnalysis(
                category = SmsCategory.fromId(entry.categoryId),
                categoryId = entry.categoryId,
                otpCode = entry.otpCode,
                amount = entry.amount,
                confidence = entry.confidence
            )
        }
    }

    fun put(messageId: Long, address: String, body: String, analysis: SmsAnalysis) {
        val entry = CacheEntry(
            messageId = messageId,
            bodyHash = sha256(body),
            addressHash = sha256(address),
            rulesFingerprint = rulesFingerprint,
            categoryId = analysis.categoryId,
            otpCode = analysis.otpCode,
            amount = analysis.amount,
            confidence = analysis.confidence,
            lastAccessed = System.currentTimeMillis()
        )
        synchronized(lock) {
            entries[messageId] = entry
            pendingWrites[messageId] = entry
            while (entries.size > MAX_ENTRIES) {
                val oldestId = entries.keys.firstOrNull() ?: break
                entries.remove(oldestId)
                pendingWrites.remove(oldestId)
            }
        }
    }

    /** Writes all misses/touches in one transaction instead of doing one disk write per SMS. */
    fun flush() {
        val snapshot = synchronized(lock) { pendingWrites.values.toList() }
        if (snapshot.isEmpty()) return

        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                snapshot.forEach { entry ->
                    val values = ContentValues().apply {
                        put(COLUMN_ID, entry.messageId)
                        put(COLUMN_BODY_HASH, entry.bodyHash)
                        put(COLUMN_ADDRESS_HASH, entry.addressHash)
                        put(COLUMN_RULES_FINGERPRINT, entry.rulesFingerprint)
                        put(COLUMN_CATEGORY_ID, entry.categoryId)
                        put(COLUMN_OTP_CODE, entry.otpCode)
                        put(COLUMN_AMOUNT, entry.amount)
                        put(COLUMN_CONFIDENCE, entry.confidence)
                        put(COLUMN_LAST_ACCESSED, entry.lastAccessed)
                    }
                    db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.execSQL(
                    "DELETE FROM $TABLE_NAME WHERE $COLUMN_ID NOT IN " +
                        "(SELECT $COLUMN_ID FROM $TABLE_NAME ORDER BY $COLUMN_LAST_ACCESSED DESC LIMIT $MAX_ENTRIES)"
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            synchronized(lock) {
                snapshot.forEach { entry ->
                    if (pendingWrites[entry.messageId] == entry) {
                        pendingWrites.remove(entry.messageId)
                    }
                }
            }
        } catch (_: SQLiteException) {
            // Classification still works without caching; a storage issue must not hide messages.
        }
    }

    private fun loadRecentEntries() {
        try {
            readableDatabase.rawQuery(
                """
                SELECT $COLUMN_ID, $COLUMN_BODY_HASH, $COLUMN_ADDRESS_HASH,
                       $COLUMN_RULES_FINGERPRINT, $COLUMN_CATEGORY_ID, $COLUMN_OTP_CODE,
                       $COLUMN_AMOUNT, $COLUMN_CONFIDENCE, $COLUMN_LAST_ACCESSED
                FROM $TABLE_NAME
                WHERE $COLUMN_ID IN (
                    SELECT $COLUMN_ID FROM $TABLE_NAME
                    ORDER BY $COLUMN_LAST_ACCESSED DESC
                    LIMIT $MAX_ENTRIES
                )
                ORDER BY $COLUMN_LAST_ACCESSED ASC
                """.trimIndent(),
                null
            ).use { cursor ->
                val id = cursor.getColumnIndexOrThrow(COLUMN_ID)
                val bodyHash = cursor.getColumnIndexOrThrow(COLUMN_BODY_HASH)
                val addressHash = cursor.getColumnIndexOrThrow(COLUMN_ADDRESS_HASH)
                val fingerprint = cursor.getColumnIndexOrThrow(COLUMN_RULES_FINGERPRINT)
                val category = cursor.getColumnIndexOrThrow(COLUMN_CATEGORY_ID)
                val otp = cursor.getColumnIndexOrThrow(COLUMN_OTP_CODE)
                val amount = cursor.getColumnIndexOrThrow(COLUMN_AMOUNT)
                val confidence = cursor.getColumnIndexOrThrow(COLUMN_CONFIDENCE)
                val accessed = cursor.getColumnIndexOrThrow(COLUMN_LAST_ACCESSED)

                synchronized(lock) {
                    while (cursor.moveToNext()) {
                        val entry = CacheEntry(
                            messageId = cursor.getLong(id),
                            bodyHash = cursor.getString(bodyHash),
                            addressHash = cursor.getString(addressHash),
                            rulesFingerprint = cursor.getString(fingerprint),
                            categoryId = cursor.getString(category),
                            otpCode = if (cursor.isNull(otp)) null else cursor.getString(otp),
                            amount = if (cursor.isNull(amount)) null else cursor.getString(amount),
                            confidence = cursor.getFloat(confidence),
                            lastAccessed = cursor.getLong(accessed)
                        )
                        entries[entry.messageId] = entry
                    }
                }
            }
        } catch (_: SQLiteException) {
            // A missing/corrupt optional cache is treated as an empty cache.
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY,
                $COLUMN_BODY_HASH TEXT NOT NULL,
                $COLUMN_ADDRESS_HASH TEXT NOT NULL,
                $COLUMN_RULES_FINGERPRINT TEXT NOT NULL,
                $COLUMN_CATEGORY_ID TEXT NOT NULL,
                $COLUMN_OTP_CODE TEXT,
                $COLUMN_AMOUNT TEXT,
                $COLUMN_CONFIDENCE REAL NOT NULL,
                $COLUMN_LAST_ACCESSED INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX idx_sms_classification_cache_accessed " +
                "ON $TABLE_NAME ($COLUMN_LAST_ACCESSED DESC)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    companion object {
        private const val DATABASE_NAME = "sms_classification_cache.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "sms_classification_cache"
        private const val MAX_ENTRIES = 1200

        private const val COLUMN_ID = "message_id"
        private const val COLUMN_BODY_HASH = "body_hash"
        private const val COLUMN_ADDRESS_HASH = "address_hash"
        private const val COLUMN_RULES_FINGERPRINT = "rules_fingerprint"
        private const val COLUMN_CATEGORY_ID = "category_id"
        private const val COLUMN_OTP_CODE = "otp_code"
        private const val COLUMN_AMOUNT = "amount"
        private const val COLUMN_CONFIDENCE = "confidence"
        private const val COLUMN_LAST_ACCESSED = "last_accessed"
    }
}
