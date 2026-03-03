package com.ova.blockchain.settlement

import com.ova.blockchain.config.BlockchainConfig
import com.ova.blockchain.config.Web3jProvider
import com.ova.blockchain.contract.ContractFactory
import com.ova.blockchain.contract.AriStablecoinContract
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

class MintServiceTest {

    private lateinit var config: BlockchainConfig
    private lateinit var web3jProvider: Web3jProvider
    private lateinit var contractFactory: ContractFactory
    private lateinit var walletService: CustodialWalletService
    private lateinit var txRepository: BlockchainTransactionRepository
    private lateinit var mintService: MintService

    private lateinit var mockStablecoin: AriStablecoinContract
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

        mintService = MintService(
            config, web3jProvider, contractFactory, walletService, txRepository
        )
    }

    @Test
    fun `mint should successfully mint tokens and save transaction`() {
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xmint123"
            every { blockNumber } returns BigInteger.valueOf(12345)
            every { gasUsed } returns BigInteger.valueOf(65000)
        }

        every { mockStablecoin.allowlisted(any()) } returns true
        every { mockStablecoin.mint(any(), any()) } returns mockReceipt

        val result = mintService.mint(
            toAddress = "0xrecipient",
            amount = BigDecimal("1000"),
            currency = "TRY"
        )

        result.success shouldBe true
        result.txHash shouldBe "0xmint123"
        result.blockNumber shouldBe BigInteger.valueOf(12345)
        result.error shouldBe null

        verify { web3jProvider.getChainIdForCurrency("TRY") }
        verify { walletService.getMinterCredentials() }
        verify { contractFactory.getStablecoin(TR_CHAIN_ID, mockCredentials) }
        verify { mockStablecoin.mint(eq("0xrecipient"), any()) }
    }

    @Test
    fun `mint should add to allowlist if recipient not allowlisted`() {
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xmint456"
            every { blockNumber } returns BigInteger.valueOf(12346)
            every { gasUsed } returns BigInteger.valueOf(70000)
        }
        val mockAllowlistReceipt = mockk<TransactionReceipt>()

        every { mockStablecoin.allowlisted("0xnewuser") } returns false
        every { mockStablecoin.addToAllowlist("0xnewuser") } returns mockAllowlistReceipt
        every { mockStablecoin.mint(any(), any()) } returns mockReceipt

        val result = mintService.mint(
            toAddress = "0xnewuser",
            amount = BigDecimal("500"),
            currency = "TRY"
        )

        result.success shouldBe true

        verify { mockStablecoin.allowlisted("0xnewuser") }
        verify { mockStablecoin.addToAllowlist("0xnewuser") }
        verify { mockStablecoin.mint("0xnewuser", any()) }
    }

    @Test
    fun `mint should not add to allowlist if already allowlisted`() {
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xmint789"
            every { blockNumber } returns BigInteger.valueOf(12347)
            every { gasUsed } returns BigInteger.valueOf(60000)
        }

        every { mockStablecoin.allowlisted("0xexisting") } returns true
        every { mockStablecoin.mint(any(), any()) } returns mockReceipt

        mintService.mint(
            toAddress = "0xexisting",
            amount = BigDecimal("100"),
            currency = "TRY"
        )

        verify { mockStablecoin.allowlisted("0xexisting") }
        verify(exactly = 0) { mockStablecoin.addToAllowlist(any()) }
    }

    @Test
    fun `mint should save transaction record on success`() {
        val txCaptor = slot<BlockchainTransaction>()
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xmintabc"
            every { blockNumber } returns BigInteger.valueOf(12348)
            every { gasUsed } returns BigInteger.valueOf(55000)
        }
        val paymentOrderId = UUID.randomUUID()

        every { mockStablecoin.allowlisted(any()) } returns true
        every { mockStablecoin.mint(any(), any()) } returns mockReceipt
        every { txRepository.save(capture(txCaptor)) } answers { txCaptor.captured.copy(id = 1L) }

        mintService.mint(
            toAddress = "0xrecipient",
            amount = BigDecimal("250.50"),
            currency = "EUR",
            paymentOrderId = paymentOrderId
        )

        txCaptor.captured.txHash shouldBe "0xmintabc"
        txCaptor.captured.chainId shouldBe EU_CHAIN_ID
        txCaptor.captured.operation shouldBe "mint"
        txCaptor.captured.toAddress shouldBe "0xrecipient"
        txCaptor.captured.amount shouldBe BigDecimal("250.50")
        txCaptor.captured.currency shouldBe "EUR"
        txCaptor.captured.status shouldBe "confirmed"
        txCaptor.captured.blockNumber shouldBe 12348L
        txCaptor.captured.gasUsed shouldBe 55000L
        txCaptor.captured.paymentOrderId shouldBe paymentOrderId
        txCaptor.captured.confirmedAt shouldNotBe null
    }

    @Test
    fun `mint should return failure on exception`() {
        every { mockStablecoin.allowlisted(any()) } returns true
        every { mockStablecoin.mint(any(), any()) } throws RuntimeException("Contract reverted: out of gas")

        val result = mintService.mint(
            toAddress = "0xrecipient",
            amount = BigDecimal("1000"),
            currency = "TRY"
        )

        result.success shouldBe false
        result.txHash shouldBe ""
        result.blockNumber shouldBe null
        result.error shouldBe "Contract reverted: out of gas"
    }

    @Test
    fun `mint should save failed transaction record on exception`() {
        val txCaptor = slot<BlockchainTransaction>()
        every { mockStablecoin.allowlisted(any()) } returns true
        every { mockStablecoin.mint(any(), any()) } throws RuntimeException("Insufficient funds")
        every { txRepository.save(capture(txCaptor)) } answers { txCaptor.captured.copy(id = 1L) }

        mintService.mint(
            toAddress = "0xrecipient",
            amount = BigDecimal("1000"),
            currency = "TRY"
        )

        txCaptor.captured.status shouldBe "failed"
        txCaptor.captured.errorMessage shouldBe "Insufficient funds"
        txCaptor.captured.txHash shouldStartWith "failed-"
    }

    @Test
    fun `mint should convert amount to Wei correctly`() {
        val amountCaptor = slot<BigInteger>()
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0x123"
            every { blockNumber } returns BigInteger.valueOf(100)
            every { gasUsed } returns BigInteger.valueOf(21000)
        }

        every { mockStablecoin.allowlisted(any()) } returns true
        every { mockStablecoin.mint(any(), capture(amountCaptor)) } returns mockReceipt

        mintService.mint(
            toAddress = "0xrecipient",
            amount = BigDecimal("123.456789"),
            currency = "TRY"
        )

        // 123.456789 * 10^18 = 123456789000000000000
        amountCaptor.captured shouldBe BigInteger("123456789000000000000")
    }

    @Test
    fun `mint should use correct chain for EUR currency`() {
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xeur"
            every { blockNumber } returns BigInteger.valueOf(200)
            every { gasUsed } returns BigInteger.valueOf(21000)
        }

        every { mockStablecoin.allowlisted(any()) } returns true
        every { mockStablecoin.mint(any(), any()) } returns mockReceipt

        mintService.mint(
            toAddress = "0xrecipient",
            amount = BigDecimal("100"),
            currency = "EUR"
        )

        verify { web3jProvider.getChainIdForCurrency("EUR") }
        verify { contractFactory.getStablecoin(EU_CHAIN_ID, mockCredentials) }
    }

    @Test
    fun `mint should handle null paymentOrderId`() {
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xnullorder"
            every { blockNumber } returns BigInteger.valueOf(300)
            every { gasUsed } returns BigInteger.valueOf(21000)
        }

        every { mockStablecoin.allowlisted(any()) } returns true
        every { mockStablecoin.mint(any(), any()) } returns mockReceipt

        val result = mintService.mint(
            toAddress = "0xrecipient",
            amount = BigDecimal("50"),
            currency = "TRY",
            paymentOrderId = null
        )

        result.success shouldBe true
    }
}
