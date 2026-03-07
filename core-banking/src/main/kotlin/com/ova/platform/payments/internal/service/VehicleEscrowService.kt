package com.ova.platform.payments.internal.service

import com.ova.platform.ledger.internal.model.AccountType
import com.ova.platform.ledger.internal.model.EntryDirection
import com.ova.platform.ledger.internal.model.PostingInstruction
import com.ova.platform.ledger.internal.model.TransactionType
import com.ova.platform.ledger.internal.service.AccountService
import com.ova.platform.ledger.internal.service.LedgerService
import com.ova.platform.payments.event.EscrowCancellationRequested
import com.ova.platform.payments.event.EscrowConfirmationRequested
import com.ova.platform.payments.event.EscrowFundingRequested
import com.ova.platform.payments.event.EscrowSetupRequested
import com.ova.platform.payments.internal.model.EscrowState
import com.ova.platform.payments.internal.model.VehicleEscrow
import com.ova.platform.payments.internal.model.VehicleStatus
import com.ova.platform.payments.internal.repository.VehicleEscrowRepository
import com.ova.platform.payments.internal.repository.VehicleRegistrationRepository
import com.ova.platform.shared.event.OutboxPublisher
import com.ova.platform.shared.exception.BadRequestException
import com.ova.platform.shared.exception.ForbiddenException
import com.ova.platform.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class VehicleEscrowService(
    private val vehicleEscrowRepository: VehicleEscrowRepository,
    private val vehicleRegistrationRepository: VehicleRegistrationRepository,
    private val ledgerService: LedgerService,
    private val accountService: AccountService,
    private val outboxPublisher: OutboxPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val FEE_AMOUNT = BigDecimal("50.00000000")

    @Transactional
    fun createEscrow(sellerUserId: UUID, vehicleRegistrationId: UUID, saleAmount: BigDecimal): VehicleEscrow {
        val vehicle = vehicleRegistrationRepository.findById(vehicleRegistrationId)
            ?: throw NotFoundException("Vehicle", vehicleRegistrationId.toString())

        if (vehicle.ownerUserId != sellerUserId) {
            throw ForbiddenException("You do not own this vehicle")
        }
        if (vehicle.status != VehicleStatus.MINTED) {
            throw BadRequestException("Vehicle is not available for escrow (status: ${vehicle.status.value})")
        }
        if (saleAmount <= BigDecimal.ZERO) {
            throw BadRequestException("Sale amount must be greater than zero")
        }

        // Check vehicle is not already in an active escrow
        val existingEscrows = vehicleEscrowRepository.findByVehicleRegistrationId(vehicleRegistrationId)
        val activeEscrow = existingEscrows.find { it.state !in listOf(EscrowState.COMPLETED, EscrowState.CANCELLED) }
        if (activeEscrow != null) {
            throw BadRequestException("Vehicle already has an active escrow")
        }

        val shareCode = generateShareCode()

        val escrow = vehicleEscrowRepository.save(
            VehicleEscrow(
                vehicleRegistrationId = vehicleRegistrationId,
                sellerUserId = sellerUserId,
                saleAmount = saleAmount,
                feeAmount = FEE_AMOUNT,
                shareCode = shareCode
            )
        )

        vehicleRegistrationRepository.updateStatus(vehicleRegistrationId, VehicleStatus.IN_ESCROW)

        log.info("Escrow created: id={}, vehicle={}, amount={}, shareCode={}",
            escrow.id, vehicleRegistrationId, saleAmount, shareCode)
        return escrow
    }

    @Transactional
    fun joinEscrow(buyerUserId: UUID, shareCode: String): VehicleEscrow {
        val escrow = vehicleEscrowRepository.findByShareCode(shareCode)
            ?: throw NotFoundException("Escrow", shareCode)

        if (escrow.state != EscrowState.CREATED) {
            throw BadRequestException("Escrow is not available for joining (state: ${escrow.state.value})")
        }
        if (escrow.sellerUserId == buyerUserId) {
            throw BadRequestException("Seller cannot join as buyer")
        }
        if (escrow.buyerUserId != null) {
            throw BadRequestException("Escrow already has a buyer")
        }

        val vehicle = vehicleRegistrationRepository.findById(escrow.vehicleRegistrationId)
            ?: throw NotFoundException("Vehicle", escrow.vehicleRegistrationId.toString())

        vehicleEscrowRepository.updateBuyer(escrow.id, buyerUserId)
        vehicleEscrowRepository.updateState(escrow.id, EscrowState.JOINING)

        outboxPublisher.publish(
            EscrowSetupRequested(
                escrowId = escrow.id,
                vehicleTokenId = vehicle.tokenId
                    ?: throw BadRequestException("Vehicle NFT not yet minted"),
                sellerWalletUserId = escrow.sellerUserId,
                buyerWalletUserId = buyerUserId,
                saleAmount = escrow.saleAmount,
                feeAmount = escrow.feeAmount,
                currency = escrow.currency
            )
        )

        log.info("Buyer {} joined escrow {}", buyerUserId, escrow.id)
        return escrow.copy(buyerUserId = buyerUserId, state = EscrowState.JOINING)
    }

    @Transactional
    fun fundEscrow(buyerUserId: UUID, escrowId: UUID): VehicleEscrow {
        val escrow = vehicleEscrowRepository.findById(escrowId)
            ?: throw NotFoundException("Escrow", escrowId.toString())

        if (escrow.buyerUserId != buyerUserId) {
            throw ForbiddenException("Only the buyer can fund this escrow")
        }
        if (escrow.state != EscrowState.SETUP_COMPLETE) {
            throw BadRequestException("Escrow is not ready for funding (state: ${escrow.state.value})")
        }
        if (escrow.onChainEscrowId == null) {
            throw BadRequestException("On-chain escrow not yet created")
        }

        val totalAmount = escrow.saleAmount.add(escrow.feeAmount)

        // Debit buyer, credit escrow holding
        val buyerAccount = accountService.getUserAccounts(buyerUserId)
            .find { it.account.currency == escrow.currency }
            ?: throw BadRequestException("Buyer has no ${escrow.currency} account")

        val holdingAccount = accountService.getOrCreateSystemAccount(
            escrow.currency, AccountType.VEHICLE_ESCROW_HOLDING
        )

        ledgerService.postEntries(
            idempotencyKey = "vehicle_escrow:${escrowId}:fund",
            type = TransactionType.FEE,
            postings = listOf(
                PostingInstruction(
                    accountId = buyerAccount.account.id,
                    direction = EntryDirection.DEBIT,
                    amount = totalAmount,
                    currency = escrow.currency
                ),
                PostingInstruction(
                    accountId = holdingAccount.id,
                    direction = EntryDirection.CREDIT,
                    amount = totalAmount,
                    currency = escrow.currency
                )
            ),
            referenceId = escrowId.toString(),
            metadata = mapOf("type" to "vehicle_escrow_fund")
        )

        vehicleEscrowRepository.updateState(escrowId, EscrowState.FUNDING)

        outboxPublisher.publish(
            EscrowFundingRequested(
                escrowId = escrowId,
                onChainEscrowId = escrow.onChainEscrowId,
                totalAmount = totalAmount,
                currency = escrow.currency
            )
        )

        log.info("Escrow {} funded by buyer {}, amount={}", escrowId, buyerUserId, totalAmount)
        return escrow.copy(state = EscrowState.FUNDING)
    }

    @Transactional
    fun confirmEscrow(userId: UUID, escrowId: UUID): VehicleEscrow {
        val escrow = vehicleEscrowRepository.findById(escrowId)
            ?: throw NotFoundException("Escrow", escrowId.toString())

        val role = when (userId) {
            escrow.sellerUserId -> "SELLER"
            escrow.buyerUserId -> "BUYER"
            else -> throw ForbiddenException("You are not a party to this escrow")
        }

        if (escrow.state !in listOf(EscrowState.FUNDED, EscrowState.SELLER_CONFIRMED, EscrowState.BUYER_CONFIRMED)) {
            throw BadRequestException("Escrow is not ready for confirmation (state: ${escrow.state.value})")
        }
        if (role == "SELLER" && escrow.sellerConfirmed) {
            throw BadRequestException("Seller already confirmed")
        }
        if (role == "BUYER" && escrow.buyerConfirmed) {
            throw BadRequestException("Buyer already confirmed")
        }
        if (escrow.onChainEscrowId == null) {
            throw BadRequestException("On-chain escrow not yet created")
        }

        vehicleEscrowRepository.updateConfirmation(escrowId, role, true)

        outboxPublisher.publish(
            EscrowConfirmationRequested(
                escrowId = escrowId,
                onChainEscrowId = escrow.onChainEscrowId,
                role = role
            )
        )

        log.info("Escrow {} confirmed by {} ({})", escrowId, userId, role)
        return escrow.copy(
            sellerConfirmed = if (role == "SELLER") true else escrow.sellerConfirmed,
            buyerConfirmed = if (role == "BUYER") true else escrow.buyerConfirmed
        )
    }

    @Transactional
    fun cancelEscrow(userId: UUID, escrowId: UUID): VehicleEscrow {
        val escrow = vehicleEscrowRepository.findById(escrowId)
            ?: throw NotFoundException("Escrow", escrowId.toString())

        if (userId != escrow.sellerUserId && userId != escrow.buyerUserId) {
            throw ForbiddenException("You are not a party to this escrow")
        }
        if (escrow.state in listOf(EscrowState.COMPLETED, EscrowState.CANCELLED)) {
            throw BadRequestException("Escrow is already ${escrow.state.value}")
        }
        if (escrow.sellerConfirmed && escrow.buyerConfirmed) {
            throw BadRequestException("Both parties have confirmed, cannot cancel")
        }

        val wasFunded = escrow.state in listOf(
            EscrowState.FUNDED, EscrowState.SELLER_CONFIRMED, EscrowState.BUYER_CONFIRMED
        )

        vehicleEscrowRepository.updateState(escrowId, EscrowState.CANCELLING)

        outboxPublisher.publish(
            EscrowCancellationRequested(
                escrowId = escrowId,
                onChainEscrowId = escrow.onChainEscrowId,
                wasFunded = wasFunded
            )
        )

        log.info("Escrow {} cancellation requested by {}", escrowId, userId)
        return escrow.copy(state = EscrowState.CANCELLING)
    }

    // Settlement callbacks from blockchain-service
    @Transactional
    fun onVehicleMinted(vehicleRegistrationId: UUID, tokenId: Long, txHash: String) {
        vehicleRegistrationRepository.updateTokenIdAndTxHash(vehicleRegistrationId, tokenId, txHash)
        log.info("Vehicle minted: id={}, tokenId={}, tx={}", vehicleRegistrationId, tokenId, txHash)
    }

    @Transactional
    fun onEscrowSetupConfirmed(escrowId: UUID, onChainEscrowId: Long, txHash: String) {
        vehicleEscrowRepository.updateOnChainEscrowId(escrowId, onChainEscrowId, txHash)
        log.info("Escrow setup confirmed: id={}, onChainId={}", escrowId, onChainEscrowId)
    }

    @Transactional
    fun onEscrowFunded(escrowId: UUID, txHash: String) {
        vehicleEscrowRepository.updateFundTxHash(escrowId, txHash)
        log.info("Escrow funded on-chain: id={}", escrowId)
    }

    @Transactional
    fun onEscrowConfirmed(escrowId: UUID, role: String, completed: Boolean, txHash: String) {
        if (completed) {
            onEscrowCompleted(escrowId, txHash)
        } else {
            vehicleEscrowRepository.updateConfirmation(escrowId, role, true)
            log.info("Escrow {} {} confirmed on-chain", escrowId, role)
        }
    }

    @Transactional
    fun onEscrowCompleted(escrowId: UUID, txHash: String) {
        val escrow = vehicleEscrowRepository.findById(escrowId)
            ?: throw NotFoundException("Escrow", escrowId.toString())

        val holdingAccount = accountService.getOrCreateSystemAccount(
            escrow.currency, AccountType.VEHICLE_ESCROW_HOLDING
        )

        // Credit seller: holding -> seller
        val sellerAccounts = accountService.getUserAccounts(escrow.sellerUserId)
        val sellerAccount = sellerAccounts.find { it.account.currency == escrow.currency }?.account
            ?: throw BadRequestException("Seller has no ${escrow.currency} account")

        // Credit seller sale amount
        ledgerService.postEntries(
            idempotencyKey = "vehicle_escrow:${escrowId}:seller_credit",
            type = TransactionType.P2P_TRANSFER,
            postings = listOf(
                PostingInstruction(
                    accountId = holdingAccount.id,
                    direction = EntryDirection.DEBIT,
                    amount = escrow.saleAmount,
                    currency = escrow.currency
                ),
                PostingInstruction(
                    accountId = sellerAccount.id,
                    direction = EntryDirection.CREDIT,
                    amount = escrow.saleAmount,
                    currency = escrow.currency
                )
            ),
            referenceId = escrowId.toString(),
            metadata = mapOf("type" to "vehicle_escrow_seller_payment")
        )

        // Fee: holding -> revenue
        val revenueAccount = accountService.getOrCreateSystemAccount(
            escrow.currency, AccountType.FEE_REVENUE
        )
        ledgerService.postEntries(
            idempotencyKey = "vehicle_escrow:${escrowId}:fee",
            type = TransactionType.FEE,
            postings = listOf(
                PostingInstruction(
                    accountId = holdingAccount.id,
                    direction = EntryDirection.DEBIT,
                    amount = escrow.feeAmount,
                    currency = escrow.currency
                ),
                PostingInstruction(
                    accountId = revenueAccount.id,
                    direction = EntryDirection.CREDIT,
                    amount = escrow.feeAmount,
                    currency = escrow.currency
                )
            ),
            referenceId = escrowId.toString(),
            metadata = mapOf("type" to "vehicle_escrow_fee")
        )

        // Transfer vehicle ownership
        vehicleRegistrationRepository.updateOwner(escrow.vehicleRegistrationId, escrow.buyerUserId!!)
        vehicleRegistrationRepository.updateStatus(escrow.vehicleRegistrationId, VehicleStatus.MINTED)

        vehicleEscrowRepository.updateCompleteTxHash(escrowId, txHash)

        log.info("Escrow {} completed: seller paid {}, fee {}", escrowId, escrow.saleAmount, escrow.feeAmount)
    }

    @Transactional
    fun onEscrowCancelled(escrowId: UUID, txHash: String) {
        val escrow = vehicleEscrowRepository.findById(escrowId)
            ?: throw NotFoundException("Escrow", escrowId.toString())

        val wasFunded = escrow.state in listOf(
            EscrowState.FUNDED, EscrowState.SELLER_CONFIRMED, EscrowState.BUYER_CONFIRMED,
            EscrowState.FUNDING, EscrowState.CANCELLING
        ) && escrow.fundTxHash != null

        if (wasFunded && escrow.buyerUserId != null) {
            // Refund buyer: holding -> buyer
            val totalAmount = escrow.saleAmount.add(escrow.feeAmount)
            val holdingAccount = accountService.getOrCreateSystemAccount(
                escrow.currency, AccountType.VEHICLE_ESCROW_HOLDING
            )
            val buyerAccounts = accountService.getUserAccounts(escrow.buyerUserId)
            val buyerAccount = buyerAccounts.find { it.account.currency == escrow.currency }?.account
                ?: throw BadRequestException("Buyer has no ${escrow.currency} account")

            ledgerService.postEntries(
                idempotencyKey = "vehicle_escrow:${escrowId}:refund",
                type = TransactionType.P2P_TRANSFER,
                postings = listOf(
                    PostingInstruction(
                        accountId = holdingAccount.id,
                        direction = EntryDirection.DEBIT,
                        amount = totalAmount,
                        currency = escrow.currency
                    ),
                    PostingInstruction(
                        accountId = buyerAccount.id,
                        direction = EntryDirection.CREDIT,
                        amount = totalAmount,
                        currency = escrow.currency
                    )
                ),
                referenceId = escrowId.toString(),
                metadata = mapOf("type" to "vehicle_escrow_refund")
            )
        }

        // Reset vehicle status
        vehicleRegistrationRepository.updateStatus(escrow.vehicleRegistrationId, VehicleStatus.MINTED)

        vehicleEscrowRepository.updateCancelTxHash(escrowId, txHash)

        log.info("Escrow {} cancelled, refunded={}", escrowId, wasFunded)
    }

    fun getMyEscrows(userId: UUID): List<VehicleEscrow> {
        return vehicleEscrowRepository.findBySellerOrBuyer(userId)
    }

    fun getEscrow(userId: UUID, escrowId: UUID): VehicleEscrow {
        val escrow = vehicleEscrowRepository.findById(escrowId)
            ?: throw NotFoundException("Escrow", escrowId.toString())
        if (escrow.sellerUserId != userId && escrow.buyerUserId != userId) {
            throw ForbiddenException("You are not a party to this escrow")
        }
        return escrow
    }

    fun getEscrowByShareCode(shareCode: String): VehicleEscrow {
        return vehicleEscrowRepository.findByShareCode(shareCode)
            ?: throw NotFoundException("Escrow", shareCode)
    }

    private fun generateShareCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars.random() }.joinToString("")
    }
}
