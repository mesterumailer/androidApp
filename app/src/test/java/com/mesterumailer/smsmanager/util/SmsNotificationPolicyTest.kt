package com.mesterumailer.smsmanager.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsNotificationPolicyTest {
    @Test
    fun blockedMessage_neverNotifies() {
        assertFalse(SmsNotificationPolicy.shouldNotify(true, true))
    }

    @Test
    fun disabledCategory_doesNotNotify() {
        assertFalse(SmsNotificationPolicy.shouldNotify(false, false))
    }

    @Test
    fun enabledNonBlockedCategory_notifies() {
        assertTrue(SmsNotificationPolicy.shouldNotify(false, true))
    }
}
