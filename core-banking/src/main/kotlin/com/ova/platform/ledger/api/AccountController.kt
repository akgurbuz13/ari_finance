package com.ova.platform.ledger.api

import com.ova.platform.ledger.internal.service.AccountService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController(private val accountService: AccountService) {

    @PostMapping
    fun createAccount(@Valid @RequestBody request: CreateAccountRequest): ResponseEntity<AccountResponse> {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        val account = accountService.createUserWallet(userId, request.currency)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            AccountResponse(
                id = account.id.toString(),
                currency = account.currency,
                accountType = account.accountType.value,
                status = account.status.value,
                balance = "0",
                createdAt = account.createdAt.toString()
            )
        )
    }

    @GetMapping
    fun getAccounts(): ResponseEntity<List<AccountResponse>> {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        val accounts = accountService.getUserAccounts(userId)
        return ResponseEntity.ok(
            accounts.map {
                AccountResponse(
                    id = it.account.id.toString(),
                    currency = it.account.currency,
                    accountType = it.account.accountType.value,
                    status = it.account.status.value,
                    balance = it.balance.toPlainString(),
                    createdAt = it.account.createdAt.toString()
                )
            }
        )
    }

    @GetMapping("/{accountId}/balance")
    fun getBalance(@PathVariable accountId: UUID): ResponseEntity<BalanceResponse> {
        val balance = accountService.getBalance(accountId)
        val account = accountService.getAccountById(accountId)
        return ResponseEntity.ok(
            BalanceResponse(
                accountId = accountId.toString(),
                currency = account.currency,
                balance = balance.toPlainString()
            )
        )
    }
}

data class CreateAccountRequest(
    @field:NotBlank @field:Pattern(regexp = "TRY|EUR") val currency: String
)

data class AccountResponse(
    val id: String,
    val currency: String,
    val accountType: String,
    val status: String,
    val balance: String,
    val createdAt: String
)

data class BalanceResponse(
    val accountId: String,
    val currency: String,
    val balance: String
)
