package com.ova.platform.payments.api

import com.ova.platform.ledger.internal.service.AccountService
import com.ova.platform.payments.internal.model.PaymentOrder
import com.ova.platform.payments.internal.model.PaymentStatusHistory
import com.ova.platform.payments.internal.repository.PaymentOrderRepository
import com.ova.platform.payments.internal.repository.PaymentStatusHistoryRepository
import com.ova.platform.payments.internal.service.CrossBorderTransferService
import com.ova.platform.payments.internal.service.DepositService
import com.ova.platform.payments.internal.service.DomesticTransferService
import com.ova.platform.payments.internal.service.SameCurrencyCrossBorderService
import com.ova.platform.payments.internal.service.WithdrawalService
import com.ova.platform.shared.exception.ForbiddenException
import com.ova.platform.shared.exception.NotFoundException
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val domesticTransferService: DomesticTransferService,
    private val crossBorderTransferService: CrossBorderTransferService,
    private val sameCurrencyCrossBorderService: SameCurrencyCrossBorderService,
    private val depositService: DepositService,
    private val withdrawalService: WithdrawalService,
    private val accountService: AccountService,
    private val paymentOrderRepository: PaymentOrderRepository,
    private val statusHistoryRepository: PaymentStatusHistoryRepository
) {

    @PostMapping("/domestic")
    fun initiateDomesticTransfer(
        @Valid @RequestBody request: DomesticTransferRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String
    ): ResponseEntity<PaymentOrderResponse> {
        val userId = currentUserId()

        val order = domesticTransferService.execute(
            idempotencyKey = idempotencyKey,
            senderAccountId = request.senderAccountId,
            receiverAccountId = request.receiverAccountId,
            amount = request.amount,
            currency = request.currency,
            description = request.description,
            initiatorId = userId
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(order.toResponse())
    }

    @PostMapping("/deposit")
    fun initiateDeposit(
        @Valid @RequestBody request: DepositRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String
    ): ResponseEntity<PaymentOrderResponse> {
        val userId = currentUserId()

        val order = depositService.initiateDeposit(
            idempotencyKey = idempotencyKey,
            userId = userId,
            accountId = request.accountId,
            amount = request.amount,
            currency = request.currency,
            sourceIban = request.sourceIban,
            preferredRail = request.preferredRail,
            description = request.description
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(order.toResponse())
    }

    @PostMapping("/withdrawal")
    fun initiateWithdrawal(
        @Valid @RequestBody request: WithdrawalRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String
    ): ResponseEntity<PaymentOrderResponse> {
        val userId = currentUserId()

        val order = withdrawalService.initiateWithdrawal(
            idempotencyKey = idempotencyKey,
            userId = userId,
            accountId = request.accountId,
            amount = request.amount,
            currency = request.currency,
            destinationIban = request.destinationIban,
            preferredRail = request.preferredRail,
            description = request.description
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(order.toResponse())
    }

    @PostMapping("/cross-border")
    fun initiateCrossBorderTransfer(
        @Valid @RequestBody request: CrossBorderTransferRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String
    ): ResponseEntity<PaymentOrderResponse> {
        val userId = currentUserId()

        // Detect same vs different currency to route to the correct service
        val senderAccount = accountService.getAccountById(request.senderAccountId)
        val receiverAccount = accountService.getAccountById(request.receiverAccountId)

        val order = if (senderAccount.currency == receiverAccount.currency) {
            // Same currency, different regions -> burn/mint bridge
            sameCurrencyCrossBorderService.execute(
                idempotencyKey = idempotencyKey,
                senderAccountId = request.senderAccountId,
                receiverAccountId = request.receiverAccountId,
                amount = request.amount
                    ?: throw com.ova.platform.shared.exception.BadRequestException("Amount required for same-currency cross-border"),
                description = request.description,
                initiatorId = userId
            )
        } else {
            // Different currencies -> FX cross-border
            crossBorderTransferService.execute(
                idempotencyKey = idempotencyKey,
                senderAccountId = request.senderAccountId,
                receiverAccountId = request.receiverAccountId,
                fxQuoteId = request.fxQuoteId
                    ?: throw com.ova.platform.shared.exception.BadRequestException("FX quote required for cross-currency transfer"),
                description = request.description,
                initiatorId = userId
            )
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(order.toResponse())
    }

    @GetMapping("/{id}")
    fun getPaymentDetails(@PathVariable id: UUID): ResponseEntity<PaymentDetailResponse> {
        val userId = currentUserId()
        val order = paymentOrderRepository.findById(id)
            ?: throw NotFoundException("PaymentOrder", id.toString())

        val userCanAccess = accountService.isAccountOwnedByUser(order.senderAccountId, userId) ||
            accountService.isAccountOwnedByUser(order.receiverAccountId, userId)
        if (!userCanAccess) {
            throw ForbiddenException("You do not have access to this payment")
        }

        val statusHistory = statusHistoryRepository.findByPaymentOrderId(id)

        return ResponseEntity.ok(
            PaymentDetailResponse(
                payment = order.toResponse(),
                statusHistory = statusHistory.map { it.toResponse() }
            )
        )
    }

    @GetMapping
    fun getPaymentHistory(
        @RequestParam accountId: UUID,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<List<PaymentOrderResponse>> {
        val userId = currentUserId()
        if (!accountService.isAccountOwnedByUser(accountId, userId)) {
            throw ForbiddenException("You do not have access to this account")
        }

        val orders = paymentOrderRepository.findByAccountId(accountId, limit, offset)
        return ResponseEntity.ok(orders.map { it.toResponse() })
    }

    private fun currentUserId(): UUID {
        return UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
    }

    private fun PaymentOrder.toResponse(): PaymentOrderResponse {
        return PaymentOrderResponse(
            id = id.toString(),
            type = type.value,
            status = status.value,
            senderAccountId = senderAccountId.toString(),
            receiverAccountId = receiverAccountId.toString(),
            amount = amount.toPlainString(),
            currency = currency,
            feeAmount = feeAmount.toPlainString(),
            feeCurrency = feeCurrency,
            fxQuoteId = fxQuoteId?.toString(),
            ledgerTransactionId = ledgerTransactionId?.toString(),
            description = description,
            metadata = metadata,
            createdAt = createdAt.toString(),
            updatedAt = updatedAt.toString(),
            completedAt = completedAt?.toString()
        )
    }

    private fun PaymentStatusHistory.toResponse(): StatusHistoryResponse {
        return StatusHistoryResponse(
            fromStatus = fromStatus?.value,
            toStatus = toStatus.value,
            reason = reason,
            createdAt = createdAt.toString()
        )
    }
}

// ---- Request DTOs ----

data class DomesticTransferRequest(
    @field:NotNull
    val senderAccountId: UUID,

    @field:NotNull
    val receiverAccountId: UUID,

    @field:NotNull
    @field:DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    val amount: BigDecimal,

    @field:NotBlank
    @field:Pattern(regexp = "TRY|EUR", message = "Currency must be TRY or EUR")
    val currency: String,

    val description: String? = null
)

data class DepositRequest(
    @field:NotNull
    val accountId: UUID,

    @field:NotNull
    @field:DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    val amount: BigDecimal,

    @field:NotBlank
    @field:Pattern(regexp = "TRY|EUR", message = "Currency must be TRY or EUR")
    val currency: String,

    @field:NotBlank
    val sourceIban: String,

    val preferredRail: String? = null,
    val description: String? = null
)

data class WithdrawalRequest(
    @field:NotNull
    val accountId: UUID,

    @field:NotNull
    @field:DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    val amount: BigDecimal,

    @field:NotBlank
    @field:Pattern(regexp = "TRY|EUR", message = "Currency must be TRY or EUR")
    val currency: String,

    @field:NotBlank
    val destinationIban: String,

    val preferredRail: String? = null,
    val description: String? = null
)

data class CrossBorderTransferRequest(
    @field:NotNull
    val senderAccountId: UUID,

    @field:NotNull
    val receiverAccountId: UUID,

    val fxQuoteId: UUID? = null,

    @field:DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    val amount: BigDecimal? = null,

    val description: String? = null
)

// ---- Response DTOs ----

data class PaymentOrderResponse(
    val id: String,
    val type: String,
    val status: String,
    val senderAccountId: String,
    val receiverAccountId: String,
    val amount: String,
    val currency: String,
    val feeAmount: String,
    val feeCurrency: String?,
    val fxQuoteId: String?,
    val ledgerTransactionId: String?,
    val description: String?,
    val metadata: Map<String, Any>?,
    val createdAt: String,
    val updatedAt: String,
    val completedAt: String?
)

data class PaymentDetailResponse(
    val payment: PaymentOrderResponse,
    val statusHistory: List<StatusHistoryResponse>
)

data class StatusHistoryResponse(
    val fromStatus: String?,
    val toStatus: String,
    val reason: String?,
    val createdAt: String
)
