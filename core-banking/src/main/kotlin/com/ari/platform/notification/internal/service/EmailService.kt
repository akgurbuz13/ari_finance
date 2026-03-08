package com.ari.platform.notification.internal.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EmailService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun send(to: String, subject: String, body: String) {
        // TODO: Integrate with email provider (SendGrid, SES, etc.)
        log.info("Email to {}: subject={}", to, subject)
    }
}
