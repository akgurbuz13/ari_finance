package com.ova.blockchain.listener

import com.ova.blockchain.config.BlockchainConfig
import com.ova.blockchain.config.Web3jProvider
import com.ova.blockchain.repository.ChainEvent
import com.ova.blockchain.repository.ChainEventRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.web3j.abi.EventEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.DefaultBlockParameterNumber
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.Log
import java.math.BigDecimal
import java.math.BigInteger

@Service
class ChainEventListener(
    private val config: BlockchainConfig,
    private val web3jProvider: Web3jProvider,
    private val chainEventRepository: ChainEventRepository,
    @Value("\${ova.core-banking.url}") private val coreBankingUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()

    // ERC-20 Transfer event: Transfer(address indexed from, address indexed to, uint256 value)
    private val transferEvent = Event(
        "Transfer",
        listOf(
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<Uint256>() {}
        )
    )

    // OvaStablecoin custom events
    private val tokensMintedEvent = Event(
        "TokensMinted",
        listOf(
            object : TypeReference<Address>(true) {},
            object : TypeReference<Uint256>() {}
        )
    )

    private val tokensBurnedEvent = Event(
        "TokensBurned",
        listOf(
            object : TypeReference<Address>(true) {},
            object : TypeReference<Uint256>() {}
        )
    )

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    fun pollChainEvents() {
        val chainId = config.getChainId()
        try {
            val web3j = web3jProvider.getWeb3jForRegion()
            val contractAddress = config.getStablecoinAddress()

            if (contractAddress.isBlank()) {
                return
            }

            val lastBlock = chainEventRepository.getLastProcessedBlock(chainId)
            val latestBlock = web3j.ethBlockNumber().send().blockNumber.toLong()

            if (latestBlock <= lastBlock) return

            // Process in batches of 1000 blocks to avoid RPC limits
            val fromBlock = lastBlock + 1
            val toBlock = minOf(latestBlock, fromBlock + 999)

            val filter = EthFilter(
                DefaultBlockParameterNumber(BigInteger.valueOf(fromBlock)),
                DefaultBlockParameterNumber(BigInteger.valueOf(toBlock)),
                contractAddress
            )

            val logs = web3j.ethGetLogs(filter).send()

            if (logs.hasError()) {
                log.error("Error fetching logs for chain {}: {}", chainId, logs.error.message)
                return
            }

            var eventsProcessed = 0
            for (logResult in logs.logs) {
                val ethLog = logResult.get() as Log
                try {
                    processLog(ethLog, chainId, contractAddress)
                    eventsProcessed++
                } catch (e: Exception) {
                    log.error("Failed to process log tx={}, index={}: {}",
                        ethLog.transactionHash, ethLog.logIndex, e.message)
                }
            }

            chainEventRepository.updateLastProcessedBlock(chainId, toBlock)

            if (eventsProcessed > 0) {
                log.info("Processed {} chain events, blocks {}-{} on chain {}",
                    eventsProcessed, fromBlock, toBlock, chainId)
            }

        } catch (e: Exception) {
            log.error("Chain event polling failed for chain {}: {}", chainId, e.message)
        }
    }

    private fun processLog(ethLog: Log, chainId: Long, contractAddress: String) {
        if (ethLog.topics.isNullOrEmpty()) return

        val topicHash = ethLog.topics[0]
        val transferSig = EventEncoder.encode(transferEvent)
        val mintSig = EventEncoder.encode(tokensMintedEvent)
        val burnSig = EventEncoder.encode(tokensBurnedEvent)

        val eventType: String
        val fromAddress: String?
        val toAddress: String?
        val amount: BigDecimal

        when (topicHash) {
            transferSig -> {
                eventType = "Transfer"
                fromAddress = decodeAddress(ethLog.topics[1])
                toAddress = decodeAddress(ethLog.topics[2])
                amount = decodeAmount(ethLog.data)
            }
            mintSig -> {
                eventType = "TokensMinted"
                toAddress = decodeAddress(ethLog.topics[1])
                fromAddress = null
                amount = decodeAmount(ethLog.data)
            }
            burnSig -> {
                eventType = "TokensBurned"
                fromAddress = decodeAddress(ethLog.topics[1])
                toAddress = null
                amount = decodeAmount(ethLog.data)
            }
            else -> return
        }

        val saved = chainEventRepository.save(
            ChainEvent(
                chainId = chainId,
                blockNumber = ethLog.blockNumber.toLong(),
                txHash = ethLog.transactionHash,
                logIndex = ethLog.logIndex.toInt(),
                eventType = eventType,
                contractAddress = contractAddress,
                fromAddress = fromAddress,
                toAddress = toAddress,
                amount = amount,
                rawData = """{"topics":${ethLog.topics},"data":"${ethLog.data}"}"""
            )
        )

        if (saved) {
            log.debug("Chain event: type={}, tx={}, amount={}",
                eventType, ethLog.transactionHash, amount)
        }
    }

    private fun decodeAddress(topic: String): String {
        // Topics are 32-byte hex, address is last 20 bytes
        return "0x${topic.substring(26)}"
    }

    private fun decodeAmount(data: String): BigDecimal {
        if (data.isBlank() || data == "0x") return BigDecimal.ZERO
        val amountWei = BigInteger(data.removePrefix("0x"), 16)
        return BigDecimal(amountWei).divide(BigDecimal.TEN.pow(18))
    }
}
