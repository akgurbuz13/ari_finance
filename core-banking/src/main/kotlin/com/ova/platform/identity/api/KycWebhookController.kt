package com.ova.platform.identity.api

import com.ova.platform.identity.internal.service.KycService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/webhooks/kyc")
class KycWebhookController(private val kycService: KycService) {

    private val log = LoggerFactory.getLogger(KycWebhookController::class.java)

    @PostMapping("/{provider}")
    fun handleWebhook(
        @PathVariable provider: String,
        @RequestBody payload: String,
        @RequestHeader("X-Webhook-Signature", required = false) signature: String?
    ): ResponseEntity<Void> {
        log.info("Received KYC webhook from provider: {}", provider)
        kycService.handleWebhookCallback(provider, payload, signature)
        return ResponseEntity.ok().build()
    }
}
