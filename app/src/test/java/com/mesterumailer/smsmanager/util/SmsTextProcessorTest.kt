package com.mesterumailer.smsmanager.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTextProcessorTest {

    @Test
    fun detectsHttpsUrl() {
        assertTrue(SmsTextProcessor.mayContainWebUrl("برای مشاهده https://example.com/123 کلیک کنید"))
    }

    @Test
    fun detectsWwwUrl() {
        assertTrue(SmsTextProcessor.mayContainWebUrl("www.example.com را ببینید"))
    }

    @Test
    fun detectsBareDomain() {
        assertTrue(SmsTextProcessor.mayContainWebUrl("example.com"))
    }

    @Test
    fun ignoresNormalPersianText() {
        assertFalse(SmsTextProcessor.mayContainWebUrl("این یک پیام معمولی بدون لینک است"))
    }

    @Test
    fun ignoresStandaloneEmailLikeText() {
        assertFalse(SmsTextProcessor.mayContainWebUrl("test@example"))
    }
}
