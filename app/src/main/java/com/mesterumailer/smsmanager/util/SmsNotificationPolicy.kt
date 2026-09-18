package com.mesterumailer.smsmanager.util

object SmsNotificationPolicy {
    fun shouldNotify(isBlocked: Boolean, isCategoryEnabled: Boolean): Boolean =
        !isBlocked && isCategoryEnabled
}
