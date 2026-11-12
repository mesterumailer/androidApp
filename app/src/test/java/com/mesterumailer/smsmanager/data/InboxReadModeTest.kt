package com.mesterumailer.smsmanager.data

import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.util.SmsClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class InboxReadModeTest {
    @Test
    fun defaultReadyInboxLimitIs500AndCanGrowTo1000() {
        assertEquals(500, SmsSettingsRepository.DEFAULT_INBOX_LIMIT)
        assertEquals(1000, SmsSettingsRepository.MAX_INBOX_LIMIT)
        assertEquals(1, SmsSettingsRepository.MIN_INBOX_LIMIT)
    }

    @Test
    fun inboxLimitIsClampedToOneThroughOneThousand() {
        assertEquals(100, SmsSettingsRepository.clampInboxLimit(100))
        assertEquals(500, SmsSettingsRepository.clampInboxLimit(500))
        assertEquals(1000, SmsSettingsRepository.clampInboxLimit(1000))
        assertEquals(1000, SmsSettingsRepository.clampInboxLimit(5000))
        assertEquals(1, SmsSettingsRepository.clampInboxLimit(0))
        assertEquals(1, SmsSettingsRepository.clampInboxLimit(-5))
        assertEquals(1, SmsSettingsRepository.clampInboxLimit(1))
    }

    @Test
    fun disabledCategoryRulesAreRemovedBeforeClassification() {
        val disabledRule = FilterRule(
            categoryId = "disabled_category",
            displayName = "غیرفعال",
            anyKeywords = listOf("عبارت آزمایشی"),
            keywordWeights = mapOf("عبارت آزمایشی" to 20),
            priority = 100
        )
        val activeRule = FilterRule(
            categoryId = "active_category",
            displayName = "فعال",
            anyKeywords = listOf("عبارت آزمایشی"),
            keywordWeights = mapOf("عبارت آزمایشی" to 8),
            priority = 1
        )
        val activeRules = CategoryActivationRepository.filterActiveRules(
            listOf(disabledRule, activeRule),
            setOf("active_category")
        )

        assertEquals(listOf(activeRule), activeRules)
        assertEquals(
            "active_category",
            SmsClassifier(activeRules).analyze("sender", "عبارت آزمایشی").categoryId
        )
    }

    @Test
    fun allDisabledCategoryRulesLeaveTheMessageUncategorized() {
        val disabledRule = FilterRule(
            categoryId = "disabled_category",
            displayName = "غیرفعال",
            anyKeywords = listOf("عبارت آزمایشی"),
            keywordWeights = mapOf("عبارت آزمایشی" to 20)
        )
        val activeRules = CategoryActivationRepository.filterActiveRules(
            listOf(disabledRule),
            emptySet()
        )

        assertEquals(emptyList<FilterRule>(), activeRules)
        assertEquals(
            "unknown",
            SmsClassifier(activeRules).analyze("sender", "عبارت آزمایشی").categoryId
        )
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
