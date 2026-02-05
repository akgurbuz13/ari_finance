package com.ova.platform.ledger.internal.repository

import com.ova.platform.ledger.internal.model.Account
import com.ova.platform.ledger.internal.model.AccountStatus
import com.ova.platform.ledger.internal.model.AccountType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.util.UUID

@Repository
class AccountRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        Account(
            id = UUID.fromString(rs.getString("id")),
            userId = UUID.fromString(rs.getString("user_id")),
            currency = rs.getString("currency"),
            accountType = AccountType.fromValue(rs.getString("account_type")),
            status = AccountStatus.fromValue(rs.getString("status")),
            iban = rs.getString("iban"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    fun save(account: Account): Account {
        jdbcTemplate.update(
            """
            INSERT INTO ledger.accounts (id, user_id, currency, account_type, status, iban)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            account.id, account.userId, account.currency,
            account.accountType.value, account.status.value, account.iban
        )
        return account
    }

    fun findByIban(iban: String): Account? {
        return jdbcTemplate.query(
            "SELECT * FROM ledger.accounts WHERE iban = ?", rowMapper, iban
        ).firstOrNull()
    }

    fun assignIban(accountId: UUID, iban: String) {
        jdbcTemplate.update(
            "UPDATE ledger.accounts SET iban = ? WHERE id = ? AND iban IS NULL",
            iban, accountId
        )
    }

    fun findById(id: UUID): Account? {
        return jdbcTemplate.query(
            "SELECT * FROM ledger.accounts WHERE id = ?", rowMapper, id
        ).firstOrNull()
    }

    fun findByUserIdAndCurrency(userId: UUID, currency: String): Account? {
        return jdbcTemplate.query(
            "SELECT * FROM ledger.accounts WHERE user_id = ? AND currency = ? AND account_type = 'user_wallet'",
            rowMapper, userId, currency
        ).firstOrNull()
    }

    fun findAllByUserId(userId: UUID): List<Account> {
        return jdbcTemplate.query(
            "SELECT * FROM ledger.accounts WHERE user_id = ? AND account_type = 'user_wallet' ORDER BY currency",
            rowMapper, userId
        )
    }

    fun getBalance(accountId: UUID): BigDecimal {
        val results = jdbcTemplate.query(
            """
            SELECT COALESCE(balance_after, 0) as balance
            FROM ledger.entries
            WHERE account_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """
        , { rs, _ -> rs.getBigDecimal("balance") }, accountId)
        return results.firstOrNull() ?: BigDecimal.ZERO
    }

    fun updateStatus(accountId: UUID, status: AccountStatus) {
        jdbcTemplate.update(
            "UPDATE ledger.accounts SET status = ? WHERE id = ?",
            status.value, accountId
        )
    }

    fun findSystemAccount(currency: String, accountType: AccountType): Account? {
        return jdbcTemplate.query(
            """
            SELECT * FROM ledger.accounts
            WHERE currency = ? AND account_type = ?
            AND user_id = '00000000-0000-0000-0000-000000000000'
            """,
            rowMapper, currency, accountType.value
        ).firstOrNull()
    }
}
