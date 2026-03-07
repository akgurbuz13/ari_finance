package com.ova.platform.ledger.internal.service

import com.ova.platform.ledger.internal.model.Account
import com.ova.platform.ledger.internal.model.AccountStatus
import com.ova.platform.ledger.internal.model.AccountType
import com.ova.platform.ledger.internal.repository.AccountRepository
import com.ova.platform.shared.exception.ConflictException
import com.ova.platform.shared.exception.NotFoundException
import com.ova.platform.shared.security.AuditService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val ibanGeneratorService: IbanGeneratorService,
    private val auditService: AuditService
) {

    @Transactional
    fun createUserWallet(userId: UUID, currency: String, region: String? = null): Account {
        val accountRegion = region ?: regionForCurrency(currency)

        val existing = accountRepository.findByUserIdCurrencyAndRegion(userId, currency, accountRegion)
        if (existing != null) {
            throw ConflictException("Account already exists for currency $currency in region $accountRegion")
        }

        val iban = ibanGeneratorService.generateIban()

        val account = accountRepository.save(
            Account(
                userId = userId,
                currency = currency,
                accountType = AccountType.USER_WALLET,
                iban = iban,
                region = accountRegion
            )
        )

        auditService.log(userId, "user", "create_account", "account", account.id.toString(),
            details = mapOf("currency" to currency, "iban" to iban, "region" to accountRegion))

        return account
    }

    private fun regionForCurrency(currency: String): String = when (currency) {
        "TRY" -> "TR"
        "EUR" -> "EU"
        else -> "TR"
    }

    fun findByIban(iban: String): Account? {
        return accountRepository.findByIban(iban)
    }

    fun getUserAccounts(userId: UUID): List<AccountWithBalance> {
        return accountRepository.findAllByUserId(userId).map { account ->
            AccountWithBalance(
                account = account,
                balance = accountRepository.getBalance(account.id)
            )
        }
    }

    fun getAccountById(accountId: UUID): Account {
        return accountRepository.findById(accountId)
            ?: throw NotFoundException("Account", accountId.toString())
    }

    fun getBalance(accountId: UUID): BigDecimal {
        return accountRepository.getBalance(accountId)
    }

    fun isAccountOwnedByUser(accountId: UUID, userId: UUID): Boolean {
        return accountRepository.findById(accountId)?.userId == userId
    }

    @Transactional
    fun freezeAccount(accountId: UUID, adminId: UUID) {
        accountRepository.updateStatus(accountId, AccountStatus.FROZEN)
        auditService.log(adminId, "admin", "freeze_account", "account", accountId.toString())
    }

    @Transactional
    fun unfreezeAccount(accountId: UUID, adminId: UUID) {
        accountRepository.updateStatus(accountId, AccountStatus.ACTIVE)
        auditService.log(adminId, "admin", "unfreeze_account", "account", accountId.toString())
    }

    fun getOrCreateSystemAccount(currency: String, accountType: AccountType): Account {
        val systemUserId = UUID.fromString("00000000-0000-0000-0000-000000000000")
        return accountRepository.findSystemAccount(currency, accountType)
            ?: accountRepository.save(
                Account(
                    userId = systemUserId,
                    currency = currency,
                    accountType = accountType,
                    region = regionForCurrency(currency)
                )
            )
    }

    fun getOrCreateSystemAccountWithRegion(currency: String, accountType: AccountType, region: String): Account {
        val systemUserId = UUID.fromString("00000000-0000-0000-0000-000000000000")
        return accountRepository.findSystemAccountByRegion(currency, accountType, region)
            ?: accountRepository.save(
                Account(
                    userId = systemUserId,
                    currency = currency,
                    accountType = accountType,
                    region = region
                )
            )
    }
}

data class AccountWithBalance(
    val account: Account,
    val balance: BigDecimal
)
