package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.model.SmsCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SmsClassifierTest {
    private val classifier = SmsClassifier()

    @Test
    fun classifiesPersianOtp() {
        val result = classifier.analyze("کد تأیید شما 583214 است")

        assertEquals(SmsCategory.OTP, result.category)
        assertEquals("583214", result.otpCode)
    }

    @Test
    fun classifiesTransactionAndExtractsAmount() {
        val result = classifier.analyze("خرید به مبلغ 125,000 تومان با کارت انجام شد")

        assertEquals(SmsCategory.TRANSACTION, result.category)
        assertNotNull(result.amount)
    }

    @Test
    fun keepsUnknownMessagesUnknown() {
        val result = classifier.analyze("سلام، امروز ساعت 8 حرکت می‌کنیم")

        assertEquals(SmsCategory.UNKNOWN, result.category)
    }
}
