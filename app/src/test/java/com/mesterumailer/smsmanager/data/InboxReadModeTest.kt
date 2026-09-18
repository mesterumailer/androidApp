package com.mesterumailer.smsmanager.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InboxReadModeTest {
    @Test
    fun knownIdsResolveToExpectedModes() {
        assertEquals(InboxReadMode.UNTIL_TODAY, InboxReadMode.fromId("until_today"))
        assertEquals(InboxReadMode.UNTIL_DATE, InboxReadMode.fromId("until_date"))
    }

    @Test
    fun unknownOrMissingModeFallsBackToToday() {
        assertEquals(InboxReadMode.UNTIL_TODAY, InboxReadMode.fromId(null))
        assertEquals(InboxReadMode.UNTIL_TODAY, InboxReadMode.fromId("unknown"))
    }
}
