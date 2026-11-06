package com.mesterumailer.smsmanager.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InboxReadModeTest {
    @Test
    fun defaultReadyInboxLimitIs100AndCannotExceed100() {
        assertEquals(100, SmsSettingsRepository.DEFAULT_INBOX_LIMIT)
        assertEquals(100, SmsSettingsRepository.MAX_INBOX_LIMIT)
        assertEquals(1, SmsSettingsRepository.MIN_INBOX_LIMIT)
    }

    @Test
    fun defaultReadyInboxCountIs100() {
        assertEquals(100, SmsSettingsRepository.DEFAULT_INBOX_LIMIT)
        assertEquals(100, SmsSettingsRepository.MAX_INBOX_LIMIT)
        assertEquals(1, SmsSettingsRepository.MIN_INBOX_LIMIT)
    }

    @Test
    fun serviceAndDeliveryAreInactiveByDefault() {
        assertEquals(false, CategoryActivationRepository.isActiveByDefault("service"))
        assertEquals(false, CategoryActivationRepository.isActiveByDefault("delivery"))
        assertEquals(true, CategoryActivationRepository.isActiveByDefault("otp"))
        assertEquals(true, CategoryActivationRepository.isActiveByDefault("unknown"))
    }

    @Test
    fun knownIdsResolveToExpectedModes() {
        assertEquals(InboxReadMode.LATEST_MESSAGES, InboxReadMode.fromId("until_today"))
        assertEquals(InboxReadMode.UNTIL_DATE, InboxReadMode.fromId("until_date"))
    }

    @Test
    fun unknownOrMissingModeFallsBackToLatestMessages() {
        assertEquals(InboxReadMode.LATEST_MESSAGES, InboxReadMode.fromId(null))
        assertEquals(InboxReadMode.LATEST_MESSAGES, InboxReadMode.fromId("unknown"))
    }
}
