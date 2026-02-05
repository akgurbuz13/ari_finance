package com.ova.blockchain.bridge

import com.ova.blockchain.config.BlockchainConfig
import com.ova.blockchain.config.Web3jProvider
import com.ova.blockchain.contract.ContractFactory
import com.ova.blockchain.contract.OvaBridgeAdapterContract
import com.ova.blockchain.repository.BlockchainTransaction
import com.ova.blockchain.repository.BlockchainTransactionRepository
import com.ova.blockchain.wallet.CustodialWalletService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.web3j.crypto.Credentials
import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.math.BigDecimal
import java.math.BigInteger

class IcttBridgeServiceTest {

    private lateinit var config: BlockchainConfig
    private lateinit var web3jProvider: Web3jProvider
    private lateinit var contractFactory: ContractFactory
    private lateinit var walletService: CustodialWalletService
    private lateinit var txRepository: BlockchainTransactionRepository
    private lateinit var bridgeService: IcttBridgeService

    private lateinit var mockBridgeAdapter: OvaBridgeAdapterContract
    private lateinit var mockCredentials: Credentials

    @BeforeEach
    fun setup() {
        config = mockk(relaxed = true)
        web3jProvider = mockk(relaxed = true)
        contractFactory = mockk(relaxed = true)
        walletService = mockk(relaxed = true)
        txRepository = mockk(relaxed = true)

        mockBridgeAdapter = mockk(relaxed = true)
        mockCredentials = mockk(relaxed = true)

        every { walletService.getBridgeOperatorCredentials() } returns mockCredentials
        every { contractFactory.getBridgeAdapter(any(), any()) } returns mockBridgeAdapter
        every { txRepository.save(any()) } answers { firstArg<BlockchainTransaction>().copy(id = 1L) }

        bridgeService = IcttBridgeService(
            config, web3jProvider, contractFactory, walletService, txRepository
        )
    }

    @Test
    fun `getBridgeQuote should calculate fees correctly`() {
        val quote = bridgeService.getBridgeQuote(
            sourceChainId = IcttBridgeService.TR_L1_CHAIN_ID,
            destinationChainId = IcttBridgeService.EU_L1_CHAIN_ID,
            amount = BigDecimal("1000"),
            currency = "TRY"
        )

        quote.inputAmount shouldBe BigDecimal("1000")
        quote.bridgeFee shouldBe BigDecimal("1.000") // 0.1%
        quote.relayerFee shouldBe BigDecimal("0.01")
        quote.outputAmount shouldBe BigDecimal("998.990") // 1000 - 1.0 - 0.01
        quote.sourceChainId shouldBe IcttBridgeService.TR_L1_CHAIN_ID
        quote.destinationChainId shouldBe IcttBridgeService.EU_L1_CHAIN_ID
    }

    @Test
    fun `getBridgeQuote should reject amount below minimum`() {
        assertThrows<IllegalArgumentException> {
            bridgeService.getBridgeQuote(
                sourceChainId = IcttBridgeService.TR_L1_CHAIN_ID,
                destinationChainId = IcttBridgeService.EU_L1_CHAIN_ID,
                amount = BigDecimal("0.5"),
                currency = "TRY"
            )
        }
    }

    @Test
    fun `getBridgeQuote should reject amount above maximum`() {
        assertThrows<IllegalArgumentException> {
            bridgeService.getBridgeQuote(
                sourceChainId = IcttBridgeService.TR_L1_CHAIN_ID,
                destinationChainId = IcttBridgeService.EU_L1_CHAIN_ID,
                amount = BigDecimal("2000000"),
                currency = "TRY"
            )
        }
    }

    @Test
    fun `getBridgeQuote should estimate time for TR to EU route`() {
        val quote = bridgeService.getBridgeQuote(
            sourceChainId = IcttBridgeService.TR_L1_CHAIN_ID,
            destinationChainId = IcttBridgeService.EU_L1_CHAIN_ID,
            amount = BigDecimal("100"),
            currency = "TRY"
        )

        quote.estimatedTimeSeconds shouldBe 120
    }

    @Test
    fun `initiateBridgeTransfer should call bridge adapter and save transaction`() {
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xabc123"
            every { blockNumber } returns BigInteger.valueOf(12345)
            every { gasUsed } returns BigInteger.valueOf(21000)
        }

        every { mockBridgeAdapter.bridgeNativeTokens(any(), any(), any()) } returns mockReceipt

        val result = bridgeService.initiateBridgeTransfer(
            sourceChainId = IcttBridgeService.TR_L1_CHAIN_ID,
            destinationChainId = IcttBridgeService.EU_L1_CHAIN_ID,
            fromAddress = "0xfrom",
            toAddress = "0xto",
            amount = BigDecimal("1000"),
            currency = "TRY"
        )

        result.status shouldBe IcttBridgeService.BridgeStatus.INITIATED
        result.sourceTxHash shouldBe "0xabc123"
        result.sourceChainId shouldBe IcttBridgeService.TR_L1_CHAIN_ID
        result.destinationChainId shouldBe IcttBridgeService.EU_L1_CHAIN_ID
        result.amount shouldBe BigDecimal("1000")
        result.transferId.shouldNotBeEmpty()

        verify { walletService.getBridgeOperatorCredentials() }
        verify { contractFactory.getBridgeAdapter(IcttBridgeService.TR_L1_CHAIN_ID, mockCredentials) }
        verify { mockBridgeAdapter.bridgeNativeTokens(eq("0xto"), any(), any()) }
        verify { txRepository.save(any()) }
    }

    @Test
    fun `initiateBridgeTransfer should return FAILED status on exception`() {
        every { mockBridgeAdapter.bridgeNativeTokens(any(), any(), any()) } throws RuntimeException("RPC error")

        val result = bridgeService.initiateBridgeTransfer(
            sourceChainId = IcttBridgeService.TR_L1_CHAIN_ID,
            destinationChainId = IcttBridgeService.EU_L1_CHAIN_ID,
            fromAddress = "0xfrom",
            toAddress = "0xto",
            amount = BigDecimal("1000"),
            currency = "TRY"
        )

        result.status shouldBe IcttBridgeService.BridgeStatus.FAILED
        result.sourceTxHash shouldBe ""
        result.error shouldBe "RPC error"

        // Should still save failed transaction
        verify { txRepository.save(match { it.status == "failed" }) }
    }

    @Test
    fun `bridgeWrappedTokensBack should call bridge adapter correctly`() {
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xdef456"
            every { blockNumber } returns BigInteger.valueOf(12346)
            every { gasUsed } returns BigInteger.valueOf(25000)
        }

        every { mockBridgeAdapter.bridgeWrappedTokensBack(any(), any(), any()) } returns mockReceipt

        val result = bridgeService.bridgeWrappedTokensBack(
            sourceChainId = IcttBridgeService.EU_L1_CHAIN_ID,
            destinationChainId = IcttBridgeService.TR_L1_CHAIN_ID,
            fromAddress = "0xfrom",
            toAddress = "0xto",
            amount = BigDecimal("500"),
            currency = "wTRY"
        )

        result.status shouldBe IcttBridgeService.BridgeStatus.INITIATED
        result.sourceTxHash shouldBe "0xdef456"

        verify { mockBridgeAdapter.bridgeWrappedTokensBack(eq("0xto"), any(), any()) }
    }

    @Test
    fun `getBridgeTransferStatus should return null for unknown transfer`() {
        every { txRepository.findByTransferId(any()) } returns emptyList()

        val result = bridgeService.getBridgeTransferStatus("unknown-id")

        result shouldBe null
    }

    @Test
    fun `getBridgeTransferStatus should return COMPLETED when complete transaction exists`() {
        val initTx = BlockchainTransaction(
            txHash = "0xinit",
            chainId = IcttBridgeService.TR_L1_CHAIN_ID,
            operation = "bridge_initiate",
            fromAddress = "0xfrom",
            toAddress = "0xto",
            amount = BigDecimal("1000"),
            currency = "TRY",
            status = "initiated"
        )
        val completeTx = BlockchainTransaction(
            txHash = "0xcomplete",
            chainId = IcttBridgeService.EU_L1_CHAIN_ID,
            operation = "bridge_complete",
            toAddress = "0xto",
            amount = BigDecimal("1000"),
            currency = "TRY",
            status = "confirmed"
        )

        every { txRepository.findByTransferId("test-id") } returns listOf(initTx, completeTx)

        val result = bridgeService.getBridgeTransferStatus("test-id")

        result shouldNotBe null
        result?.status shouldBe IcttBridgeService.BridgeStatus.COMPLETED
        result?.sourceTxHash shouldBe "0xinit"
        result?.destinationTxHash shouldBe "0xcomplete"
    }

    @Test
    fun `getBridgeTransferStatus should return PENDING_RELAY when only init exists`() {
        val initTx = BlockchainTransaction(
            txHash = "0xinit",
            chainId = IcttBridgeService.TR_L1_CHAIN_ID,
            operation = "bridge_initiate",
            fromAddress = "0xfrom",
            toAddress = "0xto",
            amount = BigDecimal("1000"),
            currency = "TRY",
            status = "initiated"
        )

        every { txRepository.findByTransferId("test-id") } returns listOf(initTx)

        val result = bridgeService.getBridgeTransferStatus("test-id")

        result shouldNotBe null
        result?.status shouldBe IcttBridgeService.BridgeStatus.PENDING_RELAY
    }

    @Test
    fun `getBridgeTransferStatus should return FAILED for failed init`() {
        val initTx = BlockchainTransaction(
            txHash = "0xfailed",
            chainId = IcttBridgeService.TR_L1_CHAIN_ID,
            operation = "bridge_initiate",
            fromAddress = "0xfrom",
            toAddress = "0xto",
            amount = BigDecimal("1000"),
            currency = "TRY",
            status = "failed",
            errorMessage = "Gas estimation failed"
        )

        every { txRepository.findByTransferId("test-id") } returns listOf(initTx)

        val result = bridgeService.getBridgeTransferStatus("test-id")

        result shouldNotBe null
        result?.status shouldBe IcttBridgeService.BridgeStatus.FAILED
        result?.error shouldBe "Gas estimation failed"
    }

    @Test
    fun `markBridgeTransferCompleted should save completion transaction`() {
        val capturedTx = slot<BlockchainTransaction>()
        every { txRepository.save(capture(capturedTx)) } answers { capturedTx.captured.copy(id = 1L) }

        bridgeService.markBridgeTransferCompleted(
            transferId = "transfer-123",
            destinationChainId = IcttBridgeService.EU_L1_CHAIN_ID,
            destinationTxHash = "0xcompletehash",
            recipient = "0xrecipient",
            amount = BigDecimal("1000")
        )

        capturedTx.captured.txHash shouldBe "0xcompletehash"
        capturedTx.captured.chainId shouldBe IcttBridgeService.EU_L1_CHAIN_ID
        capturedTx.captured.operation shouldBe "bridge_complete"
        capturedTx.captured.status shouldBe "confirmed"
        capturedTx.captured.toAddress shouldBe "0xrecipient"
        capturedTx.captured.amount shouldBe BigDecimal("1000")
    }

    @Test
    fun `getPendingBridgeTransfers should delegate to repository`() {
        val pendingTxs = listOf(
            BlockchainTransaction(
                txHash = "0xpending1",
                chainId = IcttBridgeService.TR_L1_CHAIN_ID,
                operation = "bridge_initiate",
                amount = BigDecimal("100"),
                currency = "TRY",
                status = "initiated"
            )
        )

        every { txRepository.findPendingBridgeTransfers(IcttBridgeService.TR_L1_CHAIN_ID) } returns pendingTxs

        val result = bridgeService.getPendingBridgeTransfers(IcttBridgeService.TR_L1_CHAIN_ID)

        result shouldBe pendingTxs
        verify { txRepository.findPendingBridgeTransfers(IcttBridgeService.TR_L1_CHAIN_ID) }
    }

    @Test
    fun `initiateBridgeTransfer should convert amount to Wei correctly`() {
        val amountCaptor = slot<BigInteger>()
        val mockReceipt = mockk<TransactionReceipt> {
            every { transactionHash } returns "0xabc"
            every { blockNumber } returns BigInteger.valueOf(100)
            every { gasUsed } returns BigInteger.valueOf(21000)
        }

        every { mockBridgeAdapter.bridgeNativeTokens(any(), capture(amountCaptor), any()) } returns mockReceipt

        bridgeService.initiateBridgeTransfer(
            sourceChainId = IcttBridgeService.TR_L1_CHAIN_ID,
            destinationChainId = IcttBridgeService.EU_L1_CHAIN_ID,
            fromAddress = "0xfrom",
            toAddress = "0xto",
            amount = BigDecimal("123.456"),
            currency = "TRY"
        )

        // 123.456 * 10^18 = 123456000000000000000
        amountCaptor.captured shouldBe BigInteger("123456000000000000000")
    }
}
