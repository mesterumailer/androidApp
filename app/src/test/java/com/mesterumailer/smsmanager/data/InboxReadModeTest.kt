package com.mesterumailer.smsmanager.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InboxReadModeTest {
    @Test
    fun defaultInboxLimitIs250Messages() {
        assertEquals(250, SmsSettingsRepository.DEFAULT_INBOX_LIMIT)
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
