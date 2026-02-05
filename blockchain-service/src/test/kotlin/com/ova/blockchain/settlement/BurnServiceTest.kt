package com.ova.blockchain.settlement

import com.ova.blockchain.config.BlockchainConfig
import com.ova.blockchain.config.Web3jProvider
import com.ova.blockchain.contract.ContractFactory
import com.ova.blockchain.contract.OvaStablecoinContract
import com.ova.blockchain.repository.BlockchainTransaction
import com.ova.blockchain.repository.BlockchainTransactionRepository
import com.ova.blockchain.wallet.CustodialWalletService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.web3j.crypto.Credentials
import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID

class BurnServiceTest {

    private lateinit var config: BlockchainConfig
    private lateinit var web3jProvider: Web3jProvider
    private lateinit var contractFactory: ContractFactory
    private lateinit var walletService: CustodialWalletService
    private lateinit var txRepository: BlockchainTransactionRepository
    private lateinit var burnService: BurnService

    private lateinit var mockStablecoin: OvaStablecoinContract
    private lateinit var mockCredentials: Credentials

    private val TR_CHAIN_ID = 99999L
    private val EU_CHAIN_ID = 99998L

    @BeforeEach
    fun setup() {
        config = mockk(relaxed = true)
        web3jProvider = mockk(relaxed = true)
        contractFactory = mockk(relaxed = true)
        walletService = mockk(relaxed = true)
        txRepository = mockk(relaxed = true)

        mockStablecoin = mockk(relaxed = true)
        mockCredentials = mockk(relaxed = true)

        every { walletService.getMinterCredentials() } returns mockCredentials
        every { web3jProvider.getChainIdForCurrency("TRY") } returns TR_CHAIN_ID
        every { web3jProvider.getChainIdForCurrency("EUR") } returns EU_CHAIN_ID
        every { contractFactory.getStablecoin(any(), any()) } returns mockStablecoin
        every { txRepository.save(any()) } answers { firstArg<BlockchainTransaction>().copy(id = 1L) }

        burnService = BurnService(
            config, web3jProvider, contractFactory, walletService, txRepository
        )
    }

    @Test
    fun `burn should successfully burn tokens and save transaction`() {
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xburn123"
            every { blockNumber } returns BigInteger.valueOf(12345)
            every { gasUsed } returns BigInteger.valueOf(45000)
        }

        every { mockStablecoin.burn(any(), any()) } returns mockReceipt

        val result = burnService.burn(
            fromAddress = "0xsender",
            amount = BigDecimal("500"),
            currency = "TRY"
        )

        result.success shouldBe true
        result.txHash shouldBe "0xburn123"
        result.blockNumber shouldBe BigInteger.valueOf(12345)
        result.error shouldBe null

        verify { web3jProvider.getChainIdForCurrency("TRY") }
        verify { walletService.getMinterCredentials() }
        verify { contractFactory.getStablecoin(TR_CHAIN_ID, mockCredentials) }
        verify { mockStablecoin.burn(eq("0xsender"), any()) }
    }

    @Test
    fun `burn should save transaction record on success`() {
        val txCaptor = slot<BlockchainTransaction>()
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xburnabc"
            every { blockNumber } returns BigInteger.valueOf(12348)
            every { gasUsed } returns BigInteger.valueOf(40000)
        }
        val paymentOrderId = UUID.randomUUID()

        every { mockStablecoin.burn(any(), any()) } returns mockReceipt
        every { txRepository.save(capture(txCaptor)) } answers { txCaptor.captured.copy(id = 1L) }

        burnService.burn(
            fromAddress = "0xsender",
            amount = BigDecimal("100.25"),
            currency = "EUR",
            paymentOrderId = paymentOrderId
        )

        txCaptor.captured.txHash shouldBe "0xburnabc"
        txCaptor.captured.chainId shouldBe EU_CHAIN_ID
        txCaptor.captured.operation shouldBe "burn"
        txCaptor.captured.fromAddress shouldBe "0xsender"
        txCaptor.captured.amount shouldBe BigDecimal("100.25")
        txCaptor.captured.currency shouldBe "EUR"
        txCaptor.captured.status shouldBe "confirmed"
        txCaptor.captured.blockNumber shouldBe 12348L
        txCaptor.captured.gasUsed shouldBe 40000L
        txCaptor.captured.paymentOrderId shouldBe paymentOrderId
        txCaptor.captured.confirmedAt shouldNotBe null
    }

    @Test
    fun `burn should return failure on exception`() {
        every { mockStablecoin.burn(any(), any()) } throws RuntimeException("ERC20: burn amount exceeds balance")

        val result = burnService.burn(
            fromAddress = "0xsender",
            amount = BigDecimal("1000000"),
            currency = "TRY"
        )

        result.success shouldBe false
        result.txHash shouldBe ""
        result.blockNumber shouldBe null
        result.error shouldBe "ERC20: burn amount exceeds balance"
    }

    @Test
    fun `burn should save failed transaction record on exception`() {
        val txCaptor = slot<BlockchainTransaction>()
        every { mockStablecoin.burn(any(), any()) } throws RuntimeException("Account frozen")
        every { txRepository.save(capture(txCaptor)) } answers { txCaptor.captured.copy(id = 1L) }

        burnService.burn(
            fromAddress = "0xfrozen",
            amount = BigDecimal("100"),
            currency = "TRY"
        )

        txCaptor.captured.status shouldBe "failed"
        txCaptor.captured.errorMessage shouldBe "Account frozen"
        txCaptor.captured.txHash shouldStartWith "failed-"
        txCaptor.captured.fromAddress shouldBe "0xfrozen"
    }

    @Test
    fun `burn should convert amount to Wei correctly`() {
        val amountCaptor = slot<BigInteger>()
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0x123"
            every { blockNumber } returns BigInteger.valueOf(100)
            every { gasUsed } returns BigInteger.valueOf(21000)
        }

        every { mockStablecoin.burn(any(), capture(amountCaptor)) } returns mockReceipt

        burnService.burn(
            fromAddress = "0xsender",
            amount = BigDecimal("999.123456789"),
            currency = "TRY"
        )

        // 999.123456789 * 10^18 = 999123456789000000000
        amountCaptor.captured shouldBe BigInteger("999123456789000000000")
    }

    @Test
    fun `burn should use correct chain for EUR currency`() {
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xeur"
            every { blockNumber } returns BigInteger.valueOf(200)
            every { gasUsed } returns BigInteger.valueOf(21000)
        }

        every { mockStablecoin.burn(any(), any()) } returns mockReceipt

        burnService.burn(
            fromAddress = "0xsender",
            amount = BigDecimal("100"),
            currency = "EUR"
        )

        verify { web3jProvider.getChainIdForCurrency("EUR") }
        verify { contractFactory.getStablecoin(EU_CHAIN_ID, mockCredentials) }
    }

    @Test
    fun `burn should use correct chain for TRY currency`() {
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xtry"
            every { blockNumber } returns BigInteger.valueOf(200)
            every { gasUsed } returns BigInteger.valueOf(21000)
        }

        every { mockStablecoin.burn(any(), any()) } returns mockReceipt

        burnService.burn(
            fromAddress = "0xsender",
            amount = BigDecimal("100"),
            currency = "TRY"
        )

        verify { web3jProvider.getChainIdForCurrency("TRY") }
        verify { contractFactory.getStablecoin(TR_CHAIN_ID, mockCredentials) }
    }

    @Test
    fun `burn should handle null paymentOrderId`() {
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xnullorder"
            every { blockNumber } returns BigInteger.valueOf(300)
            every { gasUsed } returns BigInteger.valueOf(21000)
        }

        every { mockStablecoin.burn(any(), any()) } returns mockReceipt

        val result = burnService.burn(
            fromAddress = "0xsender",
            amount = BigDecimal("50"),
            currency = "TRY",
            paymentOrderId = null
        )

        result.success shouldBe true
    }

    @Test
    fun `burn should handle zero amount edge case`() {
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xzero"
            every { blockNumber } returns BigInteger.valueOf(400)
            every { gasUsed } returns BigInteger.valueOf(21000)
        }

        every { mockStablecoin.burn(any(), any()) } returns mockReceipt

        val result = burnService.burn(
            fromAddress = "0xsender",
            amount = BigDecimal.ZERO,
            currency = "TRY"
        )

        result.success shouldBe true
        verify { mockStablecoin.burn(any(), eq(BigInteger.ZERO)) }
    }

    @Test
    fun `burn should handle large amounts`() {
        val amountCaptor = slot<BigInteger>()
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xlarge"
            every { blockNumber } returns BigInteger.valueOf(500)
            every { gasUsed } returns BigInteger.valueOf(21000)
        }

        every { mockStablecoin.burn(any(), capture(amountCaptor)) } returns mockReceipt

        burnService.burn(
            fromAddress = "0xsender",
            amount = BigDecimal("1000000"),
            currency = "TRY"
        )

        // 1000000 * 10^18 = 1000000000000000000000000
        amountCaptor.captured shouldBe BigInteger("1000000000000000000000000")
    }

    @Test
    fun `burn should handle contract revert errors`() {
        every { mockStablecoin.burn(any(), any()) } throws RuntimeException("execution reverted: caller is not the minter")

        val result = burnService.burn(
            fromAddress = "0xsender",
            amount = BigDecimal("100"),
            currency = "TRY"
        )

        result.success shouldBe false
        result.error shouldBe "execution reverted: caller is not the minter"
    }

    @Test
    fun `burn should handle network errors`() {
        every { mockStablecoin.burn(any(), any()) } throws RuntimeException("java.net.SocketTimeoutException: Read timed out")

        val result = burnService.burn(
            fromAddress = "0xsender",
            amount = BigDecimal("100"),
            currency = "TRY"
        )

        result.success shouldBe false
        result.error shouldBe "java.net.SocketTimeoutException: Read timed out"
    }
}
