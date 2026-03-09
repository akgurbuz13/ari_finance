# 09 - SDK and API Reference for ARI

> **Key Takeaways**
> - The blockchain-service uses **web3j** (Java/Kotlin library) for all EVM interactions — this is the primary SDK relevant to ARI
> - Avalanche's official TypeScript SDK (`@avalanche-sdk/chainkit`) is designed for frontend/Node.js applications; ARI does not currently use it but it is useful for tooling scripts
> - The **Subnet-EVM RPC** is fully Ethereum JSON-RPC compatible plus three Avalanche-specific extensions: `eth_feeConfig`, `eth_getChainConfig`, `eth_getActiveRulesAt`
> - The **Avalanche Data API** (hosted, requires API key) supports public chains and registered L1s; for ARI's private L1s, use direct RPC calls
> - The `validators.getCurrentValidators` endpoint on the L1 RPC provides validator health information without needing P-Chain access
> - All amounts must be converted: `BigDecimal` ↔ `BigInteger` (wei, 18 decimals)

---

## 1. Primary SDK: web3j in blockchain-service

ARI's blockchain-service uses web3j (https://github.com/web3j/web3j), the standard Java/Kotlin library for Ethereum-compatible chains.

### Dependency (build.gradle.kts)

```kotlin
dependencies {
    // web3j core
    implementation("org.web3j:core:4.12.x")

    // web3j contract wrappers are generated from ABI by the web3j Gradle plugin
    implementation("org.web3j:contracts:4.12.x")
}
```

### Connecting to ARI L1 Nodes

```kotlin
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.websocket.WebSocketService

// HTTP connection (for transactions, polling)
val web3jHttp = Web3j.build(
    HttpService("http://validator1-tr.ari.internal:9650/ext/bc/$TR_BLOCKCHAIN_ID/rpc")
)

// WebSocket connection (for subscriptions)
val wsService = WebSocketService(
    "ws://validator1-tr.ari.internal:9650/ext/bc/$TR_BLOCKCHAIN_ID/ws",
    false
)
wsService.connect()
val web3jWs = Web3j.build(wsService)
```

### Common RPC Operations in Kotlin

```kotlin
// Get current block number
val blockNumber: BigInteger = web3j.ethBlockNumber().send().blockNumber

// Get block details
val block = web3j.ethGetBlockByNumber(
    DefaultBlockParameter.valueOf(blockNumber),
    true // include transactions
).send().block

// Get transaction receipt
val receipt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt.get()

// Call a view function (no gas, no signature)
val function = Function(
    "totalSupply",
    listOf(),
    listOf(object : TypeReference<Uint256>() {})
)
val encoded = FunctionEncoder.encode(function)
val result = web3j.ethCall(
    Transaction.createEthCallTransaction(
        null,
        stablecoinAddress,
        encoded
    ),
    DefaultBlockParameterName.LATEST
).send()
val totalSupply = FunctionReturnDecoder.decode(result.value, function.outputParameters)
    .first().value as BigInteger

// Get native gas token balance of the operator wallet
val balance: BigInteger = web3j.ethGetBalance(
    operatorAddress,
    DefaultBlockParameterName.LATEST
).send().balance

// Get ERC-20 token balance (via eth_call to balanceOf)
val balanceOf = Function(
    "balanceOf",
    listOf(Address(userAddress)),
    listOf(object : TypeReference<Uint256>() {})
)
```

---

## 2. Contract Wrapper Generation (web3j)

ARI generates type-safe Kotlin wrappers from the compiled Solidity ABIs using the web3j Gradle plugin:

```kotlin
// build.gradle.kts for blockchain-service
plugins {
    id("org.web3j") version "4.12.x"
}

web3j {
    generatedPackageName = "com.ari.blockchain.contracts"
    generatedFilesBaseDir = "$projectDir/src/main/kotlin"
    abiDir = "$rootDir/contracts/artifacts/contracts"
}
```

Generated wrapper usage:

```kotlin
import com.ari.blockchain.contracts.AriStablecoin

// Load existing deployed contract
val stablecoin = AriStablecoin.load(
    contractAddress,
    web3j,
    credentials,
    DefaultGasProvider()
)

// Read total supply (view function — no gas)
val totalSupply: BigInteger = stablecoin.totalSupply().send()

// Mint tokens (transaction — requires gas and signature)
val receipt: TransactionReceipt = stablecoin.mint(
    toAddress,
    amountWei
).send()

// Get mint event from receipt
val mintEvents = AriStablecoin.getTokensMintedEvents(receipt)
mintEvents.forEach { event ->
    println("Minted ${event.amount} to ${event.to}")
}
```

---

## 3. Amount Conversion Reference

All amounts in ARI's Kotlin code use `BigDecimal`, but on-chain amounts are `uint256` in wei (18 decimal places).

```kotlin
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

object AmountConverter {
    private val WEI_FACTOR = BigDecimal.TEN.pow(18)

    /**
     * Convert from ARI ledger amount (NUMERIC(20,8)) to wei (uint256).
     * Example: BigDecimal("1234.56789012") → BigInteger("1234567890120000000000000000")
     */
    fun toWei(amount: BigDecimal): BigInteger =
        amount.multiply(WEI_FACTOR).toBigInteger()

    /**
     * Convert from wei (uint256) to ARI ledger amount (8 decimal places).
     * Example: BigInteger("1234567890120000000000000000") → BigDecimal("1234.56789012")
     *
     * The last 10 decimal places are lost (wei has 18dp, ledger has 8dp).
     * This truncation is < 0.0000000005 per operation — acceptable for TRY/EUR.
     */
    fun fromWei(amountWei: BigInteger): BigDecimal =
        BigDecimal(amountWei)
            .divide(WEI_FACTOR)
            .setScale(8, RoundingMode.HALF_UP)

    /**
     * For reconciliation: compare two 8dp amounts with tolerance.
     */
    fun withinTolerance(a: BigDecimal, b: BigDecimal, tolerance: BigDecimal = BigDecimal("0.00000001")): Boolean =
        (a - b).abs() <= tolerance
}
```

---

## 4. Gas Provider Configuration

```kotlin
import org.web3j.tx.gas.ContractGasProvider
import java.math.BigInteger

/**
 * Static gas provider for ARI's permissioned L1 (minBaseFee=1 wei).
 * Uses explicit gas price to avoid estimation issues with very low base fees.
 */
class AriGasProvider(
    private val gasPrice: BigInteger = BigInteger.ONE,  // 1 wei
    private val gasLimit: BigInteger = BigInteger.valueOf(500_000) // 500k covers all ARI operations
) : ContractGasProvider {
    override fun getGasPrice(contractFunc: String?): BigInteger = gasPrice
    override fun getGasLimit(contractFunc: String?): BigInteger = when (contractFunc) {
        "mint" -> BigInteger.valueOf(80_000)
        "burnFrom" -> BigInteger.valueOf(60_000)
        "burnAndBridge" -> BigInteger.valueOf(200_000)
        "send" -> BigInteger.valueOf(250_000)  // ICTT TokenHome.send()
        else -> gasLimit
    }
}
```

---

## 5. Subnet-EVM Specific RPC Methods

The Subnet-EVM RPC adds three endpoints beyond standard Ethereum JSON-RPC:

### eth_feeConfig

Returns the current fee configuration:

```bash
curl -X POST \
  http://validator1-tr.ari.internal:9650/ext/bc/$TR_BLOCKCHAIN_ID/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "eth_feeConfig",
    "params": ["latest"],
    "id": 1
  }'
```

Response:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "feeConfig": {
      "gasLimit": 12000000,
      "targetBlockRate": 2,
      "minBaseFee": 1,
      "targetGas": 15000000,
      "baseFeeChangeDenominator": 36,
      "minBlockGasCost": 0,
      "maxBlockGasCost": 1000000,
      "blockGasCostStep": 200000
    },
    "lastChangedAt": 0
  }
}
```

### eth_getChainConfig

Returns full chain configuration including precompile status:

```bash
curl -X POST \
  http://validator1-tr.ari.internal:9650/ext/bc/$TR_BLOCKCHAIN_ID/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_getChainConfig","params":[],"id":1}'
```

Useful for verifying that the correct precompiles are active.

### eth_getActiveRulesAt

Returns which EVM and Avalanche upgrade rules are active:

```bash
curl -X POST \
  http://validator1-tr.ari.internal:9650/ext/bc/$TR_BLOCKCHAIN_ID/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_getActiveRulesAt","params":[],"id":1}'
```

### validators.getCurrentValidators (L1 Health)

Query active validators from the chain's `/validators` endpoint:

```bash
curl -X POST \
  http://validator1-tr.ari.internal:9650/ext/bc/$TR_BLOCKCHAIN_ID/validators \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"validators.getCurrentValidators","params":{"nodeIDs":[]},"id":1}'
```

Response includes `isActive`, `isConnected`, and `uptimePercentage` for each validator.

---

## 6. Standard Ethereum JSON-RPC Reference

All standard Ethereum JSON-RPC methods work on ARI's L1 via web3j:

| Method | Purpose | web3j Call |
|--------|---------|-----------|
| `eth_blockNumber` | Current block height | `web3j.ethBlockNumber().send()` |
| `eth_getBalance` | Native token balance | `web3j.ethGetBalance(addr, block).send()` |
| `eth_call` | Read contract state | `web3j.ethCall(tx, block).send()` |
| `eth_sendRawTransaction` | Submit signed tx | `web3j.ethSendRawTransaction(hex).send()` |
| `eth_getTransactionReceipt` | Get tx outcome | `web3j.ethGetTransactionReceipt(hash).send()` |
| `eth_getLogs` | Query event logs | `web3j.ethGetLogs(filter).send()` |
| `eth_estimateGas` | Estimate gas cost | `web3j.ethEstimateGas(tx).send()` |
| `eth_gasPrice` | Current gas price | `web3j.ethGasPrice().send()` |
| `eth_getCode` | Contract bytecode | `web3j.ethGetCode(addr, block).send()` |
| `net_chainId` | Chain ID | `web3j.ethChainId().send()` |

---

## 7. P-Chain RPC Reference

The blockchain-service uses P-Chain RPCs indirectly (via Platform CLI and the Builder Console) for:
- Checking L1 validator set state
- Verifying P-Chain balance for continuous validator fees

Relevant P-Chain endpoints (called against the node's P-Chain endpoint):

```bash
# Get all blockchains (confirms ARI chains are registered)
curl -X POST http://validator1.ari.internal:9650/ext/bc/P \
  -d '{"jsonrpc":"2.0","method":"platform.getBlockchains","params":{},"id":1}'

# Get current supply of AVAX (useful for P-Chain balance monitoring)
curl -X POST http://validator1.ari.internal:9650/ext/bc/P \
  -d '{"jsonrpc":"2.0","method":"platform.getCurrentSupply","params":{"subnetID":"11111111111111111111111111111111LpoYY"},"id":1}'

# Get validators for ARI's L1 (from P-Chain perspective)
curl -X POST http://validator1.ari.internal:9650/ext/bc/P \
  -d '{"jsonrpc":"2.0","method":"platform.getCurrentValidators","params":{"subnetID":"<ARI_SUBNET_ID>"},"id":1}'
```

In Kotlin (using web3j HTTP client directly for P-Chain JSON-RPC):

```kotlin
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

fun getPChainValidators(pChainUrl: String, subnetId: String): String {
    val client = OkHttpClient()
    val body = """
        {"jsonrpc":"2.0","method":"platform.getCurrentValidators",
         "params":{"subnetID":"$subnetId"},"id":1}
    """.trimIndent().toRequestBody()

    val request = Request.Builder()
        .url(pChainUrl)
        .post(body)
        .addHeader("Content-Type", "application/json")
        .build()

    return client.newCall(request).execute().body?.string() ?: ""
}
```

---

## 8. Info API (Node Diagnostics)

The Info API is useful for verifying node connectivity and health:

```kotlin
// Check if ARI L1 is bootstrapped
fun isBootstrapped(nodeUrl: String, blockchainId: String): Boolean {
    val body = """
        {"jsonrpc":"2.0","method":"info.isBootstrapped",
         "params":{"chain":"$blockchainId"},"id":1}
    """.trimIndent().toRequestBody()

    val request = Request.Builder()
        .url("$nodeUrl/ext/info")
        .post(body)
        .addHeader("Content-Type", "application/json")
        .build()

    val response = httpClient.newCall(request).execute()
    val json = ObjectMapper().readTree(response.body?.string())
    return json["result"]["isBootstrapped"].asBoolean()
}

// Get node version (for upgrade verification)
fun getNodeVersion(nodeUrl: String): String {
    // Call info.getNodeVersion via HTTP
    // Returns: {"version": "avalanche/1.12.x", "databaseVersion": "..."}
}
```

---

## 9. Avalanche Data API (Public Chains)

For operations on public Avalanche chains (Fuji/Mainnet C-Chain, P-Chain), the Avalanche Data API provides indexed data without requiring a full node query:

**Base URL**: `https://glacier-api.avax.network`
**Authentication**: `x-glacier-api-key: <YOUR_API_KEY>`

ARI does not require a Data API key for its core operations (all settlement happens on private L1s), but it is useful for:

1. Checking Teleporter/ICM contract state on Fuji C-Chain
2. Monitoring P-Chain validator registrations
3. Administrative tooling and dashboards

Example: Check Teleporter registry on Fuji C-Chain:

```bash
curl "https://glacier-api.avax.network/v1/chains/43113/addresses/0xF86Cb19Ad8405AEFa7d09C778215D2Cb6eBfB228/transactions" \
  -H "x-glacier-api-key: $GLACIER_API_KEY"
```

---

## 10. Error Handling and Retry Patterns

### web3j Error Categories

```kotlin
import org.web3j.protocol.exceptions.TransactionException

fun submitWithRetry(
    txSupplier: () -> RemoteCall<TransactionReceipt>,
    maxRetries: Int = 3,
    delayMs: Long = 2000
): TransactionReceipt {
    repeat(maxRetries) { attempt ->
        try {
            return txSupplier().send()
        } catch (e: TransactionException) {
            // Transaction failed on-chain (revert) — do NOT retry
            throw e
        } catch (e: IOException) {
            // Network error — retry
            if (attempt == maxRetries - 1) throw e
            Thread.sleep(delayMs * (attempt + 1)) // exponential backoff
        } catch (e: RuntimeException) {
            when {
                e.message?.contains("nonce too low") == true -> {
                    // Nonce management issue — refresh nonce and retry
                    refreshNonce()
                    Thread.sleep(delayMs)
                }
                e.message?.contains("replacement transaction underpriced") == true -> {
                    // Gas price too low for replacement — increase and retry
                    Thread.sleep(delayMs)
                }
                else -> throw e
            }
        }
    }
    throw RuntimeException("Max retries exceeded")
}
```

### Nonce Management

In a high-throughput environment, nonce collisions can occur if multiple threads submit transactions simultaneously. ARI's current architecture uses a single OutboxPollerService thread, which naturally serializes transactions:

```kotlin
// Kotlin coroutine-based approach for sequential nonce management
class NonceManager(private val web3j: Web3j, private val address: String) {
    private var currentNonce: BigInteger? = null
    private val lock = Mutex()

    suspend fun getNextNonce(): BigInteger = lock.withLock {
        if (currentNonce == null) {
            // Fetch from chain
            currentNonce = web3j.ethGetTransactionCount(
                address, DefaultBlockParameterName.PENDING
            ).send().transactionCount
        }
        val nonce = currentNonce!!
        currentNonce = nonce + BigInteger.ONE
        nonce
    }

    suspend fun resetNonce() = lock.withLock {
        currentNonce = null // Force re-fetch from chain on next call
    }
}
```

---

## 11. Blockchain ID Conversions

Avalanche uses two representations of blockchain/subnet IDs:

| Format | Example | Used in |
|--------|---------|---------|
| CB58 | `2D7oZm5mVrAH...` | P-Chain RPC, Platform CLI |
| Hex (bytes32) | `0x1234...abcd` | Solidity contracts, Warp messages |

Converting in Kotlin:

```kotlin
import org.web3j.utils.Numeric
import io.ipfs.multibase.Multibase  // or use custom CB58 decoder

// CB58 to hex (for Solidity bytes32 parameters in bridge calls)
fun cb58ToHex(cb58: String): ByteArray {
    // CB58 is Base58Check encoding
    // Use AvalancheGo's CB58 library or implement Base58 + checksum verification
    // In production, validate the 4-byte checksum
    val decoded = Base58.decode(cb58)
    val payload = decoded.dropLast(4).toByteArray() // remove checksum
    return payload
}

// Usage in OutboxPollerService
val destBlockchainIdHex = "0x" + Numeric.toHexStringNoPrefix(cb58ToHex(destBlockchainIdCB58))
bridge.burnAndBridge(destBlockchainIdHex.toByteArray(), recipient, amountWei).send()
```

---

## 12. Health Check Endpoints

blockchain-service exposes Spring Actuator health at `/actuator/health`. Relevant custom indicators:

```kotlin
// Add to blockchain-service for comprehensive health reporting
@Bean
fun blockchainHealthEndpoint(web3jTr: Web3j, web3jEu: Web3j) = HealthEndpointConfiguration(
    mapOf(
        "tr-l1" to { checkChainConnectivity(web3jTr, "TR") },
        "eu-l1" to { checkChainConnectivity(web3jEu, "EU") }
    )
)

fun checkChainConnectivity(web3j: Web3j, chainName: String): Health {
    return try {
        val block = web3j.ethBlockNumber().send()
        if (block.hasError()) {
            Health.down().withDetail("error", block.error.message).build()
        } else {
            Health.up()
                .withDetail("blockNumber", block.blockNumber)
                .withDetail("chain", chainName)
                .build()
        }
    } catch (e: Exception) {
        Health.down(e).withDetail("chain", chainName).build()
    }
}
```

---

## 13. Cross-References

- web3j integration patterns: `blockchain-service/src/main/kotlin/com/ari/blockchain/`
- Gas provider configuration: `docs/avalanche/05-gasless-transactions.md`
- Event indexing with eth_getLogs: `docs/avalanche/07-event-listening-indexing.md`
- Reconciliation using totalSupply(): `docs/avalanche/08-reconciliation-security.md`
- Official Subnet-EVM RPC docs: `https://build.avax.network/docs/rpcs/subnet-evm`
- Official Data API docs: `https://build.avax.network/docs/api-reference/data-api`
- web3j documentation: `https://docs.web3j.io`
