package com.ari.platform.notification.internal.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PushService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun send(userId: String, title: String, body: String, data: Map<String, String>? = null) {
        // TODO: Integrate with FCM / APNs
        log.info("Push to user {}: title={}", userId, title)
    }
}
