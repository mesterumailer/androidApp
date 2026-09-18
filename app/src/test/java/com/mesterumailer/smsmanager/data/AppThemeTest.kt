package com.mesterumailer.smsmanager.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeTest {
    @Test
    fun knownIdsResolveToExpectedThemes() {
        assertEquals(AppTheme.LIGHT, AppTheme.fromId("light"))
        assertEquals(AppTheme.DARK, AppTheme.fromId("dark"))
    }

    @Test
    fun unknownOrMissingIdFallsBackToLight() {
        assertEquals(AppTheme.LIGHT, AppTheme.fromId(null))
        assertEquals(AppTheme.LIGHT, AppTheme.fromId("unknown"))
    }
}
