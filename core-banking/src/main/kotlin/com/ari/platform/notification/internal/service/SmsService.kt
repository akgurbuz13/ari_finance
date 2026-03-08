package com.ari.platform.notification.internal.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SmsService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun send(phoneNumber: String, message: String) {
        // TODO: Integrate with SMS provider (Twilio, etc.)
        log.info("SMS to {}: {}", phoneNumber, message)
    }
}
