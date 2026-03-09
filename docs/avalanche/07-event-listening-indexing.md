# 07 - Event Listening and Indexing for ARI

> **Key Takeaways**
> - ARI's `ChainEventListener` uses polling (`eth_getLogs` with block ranges) — this is the correct and production-safe approach for Avalanche L1s
> - WebSocket subscriptions (`eth_subscribe`) are a valid alternative for lower latency but require connection management; polling is more resilient
> - Avalanche's Webhooks API (hosted) provides event-driven notifications without polling infrastructure — suitable for supplementary alerting but NOT for primary settlement logic on a private L1
> - Avalanche's Data API provides rich indexed data (balances, transfers, history) for public chains; for a private permissioned L1, ARI must self-index via `eth_getLogs`
> - The outbox-driven settlement pattern (blockchain-service reads outbox, submits tx, confirms event) already follows best practices; the ChainEventListener provides idempotent confirmation indexing

---

## 1. ARI's Current Event Architecture

ARI processes blockchain events through two independent flows:

```
Flow 1: Outbox → Blockchain (Initiation)
──────────────────────────────────────────
core-banking
  └─► outbox_events table (MintRequested, BurnRequested, etc.)
        └─► OutboxPollerService (polls every 2 seconds)
              └─► MintService / BurnService / IcttBridgeService
                    └─► AriStablecoin.sol / AriBurnMintBridge.sol
                          └─► Transaction submitted → Receipt received
                                └─► blockchain.transactions table updated
                                      └─► REST callback to core-banking

Flow 2: Blockchain → Events (Confirmation Indexing)
─────────────────────────────────────────────────────
AriStablecoin.sol emits events (Transfer, TokensMinted, TokensBurned)
  └─► ChainEventListener (polls every 5 seconds using eth_getLogs)
        └─► Decodes indexed topics + data
              └─► ChainEventRepository.save(ChainEvent(...))
                    └─► blockchain.chain_events table
```

Flow 1 is the primary settlement path. Flow 2 provides an independent confirmation and audit trail that can catch any discrepancy between what was submitted and what was recorded on-chain.

---

## 2. eth_getLogs: Block Range Polling

ARI's `ChainEventListener` uses `ethGetLogs` with a sliding block window. This is the standard pattern for EVM event indexing.

### Current Implementation Pattern

```kotlin
// Conceptual representation of ChainEventListener behavior

@Scheduled(initialDelay = 10_000, fixedDelay = 5_000)
fun pollEvents() {
    val lastProcessed = chainEventRepository.getLastProcessedBlock(chainId)
    val currentBlock = web3j.ethBlockNumber().send().blockNumber

    // Batch in chunks of 1000 blocks to avoid RPC timeout
    var fromBlock = lastProcessed + BigInteger.ONE
    while (fromBlock <= currentBlock) {
        val toBlock = minOf(fromBlock + BigInteger.valueOf(999), currentBlock)

        val filter = EthFilter(
            DefaultBlockParameter.valueOf(fromBlock),
            DefaultBlockParameter.valueOf(toBlock),
            stablecoinAddress
        )
        // Add event topics to filter
        filter.addOptionalTopics(
            EventEncoder.encode(AriStablecoin.TOKENSMINTED_EVENT),
            EventEncoder.encode(AriStablecoin.TOKENSBURNED_EVENT),
            EventEncoder.encode(AriStablecoin.TRANSFER_EVENT)
        )

        val logs = web3j.ethGetLogs(filter).send().logs
        logs.forEach { log -> processLog(log) }

        chainEventRepository.updateLastProcessedBlock(chainId, toBlock)
        fromBlock = toBlock + BigInteger.ONE
    }
}
```

### Why Block Range Polling is the Right Choice for ARI

| Factor | Block Range Polling | WebSocket Subscription |
|--------|--------------------|-----------------------|
| Resilience to disconnects | High (stateless, retryable) | Low (connection must be re-established) |
| Historical backfill | Native (just extend the block range) | Requires separate historical query |
| Missed event recovery | Trivial (re-scan the block range) | Complex (must track gaps) |
| Infrastructure complexity | Low | Medium |
| Latency | 5-10s (acceptable for settlement) | Near real-time (< 2s) |
| Suitable for ARI settlement | Yes | Yes, but adds complexity |

---

## 3. Event Topics: Keccak-256 Hashes

Every EVM event is identified by its topic[0] — the keccak-256 hash of the event signature. ARI's ChainEventListener filters on these topics:

| Event | Signature | Topic[0] (keccak-256) |
|-------|-----------|----------------------|
| `Transfer(address,address,uint256)` | ERC-20 standard | `0xddf252ad...` |
| `TokensMinted(address indexed to, uint256 amount)` | AriStablecoin custom | computed at runtime |
| `TokensBurned(address indexed from, uint256 amount)` | AriStablecoin custom | computed at runtime |
| `MessageReceived(bytes32,address,bytes)` | TeleporterMessenger | varies by version |

Computing topics in web3j (Kotlin):

```kotlin
import org.web3j.crypto.Hash

// Compute topic for a specific event
val mintedTopic = Hash.sha3String("TokensMinted(address,uint256)")
// Returns "0x..." — the topic[0] to filter on
```

### Indexed Parameters in Topics

EVM events can have up to 3 indexed parameters, stored as 32-byte topics (topics[1], [2], [3]).

For `TokensMinted(address indexed to, uint256 amount)`:
- `topics[0]` = event signature hash
- `topics[1]` = `to` address (padded to 32 bytes)
- `data` = `amount` (uint256, non-indexed)

Decoding indexed address from topic:

```kotlin
fun extractAddress(topic: String): String {
    // topics are 32 bytes; address is the last 20 bytes
    // topic = "0x000000000000000000000000<20-byte-address>"
    return "0x${topic.substring(26)}" // skip prefix + 12 zero bytes
}

fun extractAmount(data: String): BigInteger {
    // data field contains the non-indexed uint256
    return BigInteger(data.removePrefix("0x"), 16)
}
```

---

## 4. WebSocket Alternative

For lower latency event processing, WebSocket subscriptions via `eth_subscribe` provide real-time log delivery:

```kotlin
import org.web3j.protocol.websocket.WebSocketService

val wsService = WebSocketService("ws://validator1-tr.ari.internal:9650/ext/bc/<BLOCKCHAIN_ID>/ws", false)
wsService.connect()
val web3jWs = Web3j.build(wsService)

// Subscribe to logs from the stablecoin contract
val subscription = web3jWs.ethLogFlowable(
    EthFilter(
        DefaultBlockParameterName.LATEST,
        DefaultBlockParameterName.LATEST,
        stablecoinAddress
    )
).subscribe { log ->
    processLog(log)
}
```

### WebSocket Reliability Pattern

WebSocket connections can drop. Implement reconnection with backfill:

```kotlin
class ReliableWsListener(
    private val wsUrl: String,
    private val lastProcessedBlockRepo: ChainEventRepository
) {
    private var subscription: Disposable? = null

    fun start() {
        connect()
    }

    private fun connect() {
        val wsService = WebSocketService(wsUrl, false)
        try {
            wsService.connect()
            val web3jWs = Web3j.build(wsService)

            // First: backfill any missed blocks since last disconnect
            val lastBlock = lastProcessedBlockRepo.getLastProcessedBlock(chainId)
            val currentBlock = web3jWs.ethBlockNumber().send().blockNumber
            if (currentBlock > lastBlock + BigInteger.ONE) {
                backfillRange(lastBlock + BigInteger.ONE, currentBlock)
            }

            // Then: subscribe to new logs
            subscription = web3jWs.ethLogFlowable(/* filter */)
                .subscribe(
                    { log -> processLog(log) },
                    { error ->
                        log.error("WebSocket error, reconnecting in 5s", error)
                        Thread.sleep(5000)
                        connect() // Reconnect
                    }
                )
        } catch (e: Exception) {
            Thread.sleep(5000)
            connect()
        }
    }
}
```

**Recommendation for ARI**: Keep polling as the primary mechanism (current `ChainEventListener`). If latency becomes critical (e.g., for real-time settlement confirmation UI), add WebSocket as a supplementary "fast path" while polling remains the authoritative source.

---

## 5. Missed Event Recovery

ARI's polling approach is inherently recoverable. If the blockchain-service restarts:

1. `ChainEventRepository` stores `lastProcessedBlock` per chain
2. On restart, polling resumes from `lastProcessedBlock + 1`
3. All missed events are processed in order before catching up to the chain head

This is a significant advantage over WebSocket-only approaches.

### Reorg Handling

Avalanche L1s using Snowman consensus have probabilistic finality within 1-2 blocks. In practice, reorgs on a permissioned L1 with 3-5 trusted validators are effectively impossible.

However, for defense-in-depth, process logs only after they are at least 1 block below the chain head:

```kotlin
val safeBlock = currentBlock - BigInteger.ONE // 1-block safety margin
val toBlock = minOf(fromBlock + BigInteger.valueOf(999), safeBlock)
```

This adds ~2 seconds of latency (one block) but ensures events will not be rolled back.

---

## 6. Event Storage Schema

The `blockchain.chain_events` table stores all indexed events:

```sql
-- Existing schema (from blockchain-service migrations)
CREATE TABLE blockchain.chain_events (
    id              BIGSERIAL PRIMARY KEY,
    chain_id        BIGINT NOT NULL,
    block_number    BIGINT NOT NULL,
    tx_hash         VARCHAR(66) NOT NULL,
    log_index       INT NOT NULL,
    event_type      VARCHAR(50) NOT NULL,  -- 'TokensMinted', 'TokensBurned', 'Transfer'
    from_address    VARCHAR(42),
    to_address      VARCHAR(42),
    amount          NUMERIC(20, 8),
    contract_address VARCHAR(42) NOT NULL,
    raw_topics      TEXT,                  -- JSON array of topic strings
    raw_data        TEXT,                  -- hex-encoded data field
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(chain_id, tx_hash, log_index)  -- idempotency key
);

-- Index for reconciliation queries
CREATE INDEX idx_chain_events_block ON blockchain.chain_events(chain_id, block_number);
CREATE INDEX idx_chain_events_address ON blockchain.chain_events(contract_address, event_type);
```

The `UNIQUE(chain_id, tx_hash, log_index)` constraint ensures that replaying a block range never creates duplicate events — critical for the reconciliation service.

---

## 7. Avalanche Data API for Public Chain Access

The Avalanche Data API (`https://glacier-api.avax.network`) provides pre-indexed chain data for supported chains. This is relevant for:

- Querying the Fuji C-Chain or Mainnet C-Chain (where TeleporterRegistry is deployed)
- Checking P-Chain validator state
- Looking up transaction history without running your own indexer

The Data API does NOT support private/permissioned L1s that are not registered on the public explorer. ARI's ariTR and ariEU L1s must self-index.

### When to Use the Data API

| Use Case | Data API | Self-Indexed (eth_getLogs) |
|----------|----------|--------------------------|
| Check if an address has AVAX on C-Chain | Yes | No (unnecessary complexity) |
| Index AriStablecoin mint events | No | Yes (required) |
| Look up Teleporter tx on Fuji | Yes | Optional |
| Regulatory audit trail for ariTRY | No | Yes (must store in ARI DB) |

### Example: Data API for Balance Check

```kotlin
// For public chain queries (not for ARI L1)
// The Avalanche SDK can be used from TypeScript/JavaScript
// For Kotlin, use standard HTTP client to call the Data API REST endpoints

val dataApiBase = "https://glacier-api.avax.network"
val response = httpClient.get("$dataApiBase/v1/chains/43113/addresses/$address/balances:listErc20Balances")
```

---

## 8. Avalanche Webhooks API

The Avalanche Webhooks API provides HTTP push notifications for on-chain events. Key characteristics:

- **Coverage**: C-Chain mainnet and testnet; some supported L1s from the public explorer
- **Events supported**: `address_activity` (wallet interactions), ERC transfers, P-Chain validator events
- **Limitations**: Private permissioned L1s are NOT supported unless they are indexed by Avalanche's infrastructure

For ARI's private L1s, webhooks are not directly applicable for settlement logic. However, they can be used for:
- Monitoring the Teleporter contract on Fuji C-Chain
- Getting notifications if the operator wallet on C-Chain is used for P-Chain funding

### Webhook Verification (for any webhook endpoint)

```kotlin
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

// Verify that an incoming webhook request is from Avalanche
fun verifyWebhookSignature(
    payload: String,
    signature: String,
    secret: String
): Boolean {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    val computed = Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    return computed == signature
}
```

---

## 9. Integration with the Outbox Pattern

ARI's outbox pattern is the source of truth for blockchain operations. The event listening flow must integrate correctly:

```
┌─────────────────────────────────────────────────────────────────┐
│                    ARI Settlement State Machine                  │
│                                                                  │
│  outbox_event                                                   │
│  status: PENDING                                                │
│      │                                                          │
│      │ OutboxPollerService picks up                             │
│      ▼                                                          │
│  status: PROCESSING                                             │
│      │                                                          │
│      │ MintService submits tx                                   │
│      ▼                                                          │
│  blockchain.transactions                                        │
│  status: PENDING                                                │
│      │                                                          │
│      │ tx confirmed (TransactionReceipt received)               │
│      ▼                                                          │
│  blockchain.transactions                                        │
│  status: CONFIRMED                                              │
│      │                                                          │
│      │ REST callback to core-banking                            │
│      ▼                                                          │
│  outbox_event status: PROCESSED                                 │
│                                                                  │
│  SEPARATELY (ChainEventListener):                               │
│  blockchain.chain_events ← indexed from eth_getLogs             │
│  (TokensMinted event, same tx_hash as above)                    │
│                                                                  │
│  RECONCILIATION (daily):                                        │
│  blockchain.chain_events ↔ outbox_events ↔ ledger.accounts     │
└─────────────────────────────────────────────────────────────────┘
```

### Critical: Idempotency in Event Processing

When processing events from `eth_getLogs`, always check for existing records before inserting:

```kotlin
fun processLog(log: Log) {
    val txHash = log.transactionHash
    val logIndex = log.logIndex.toInt()
    val chainId = currentChainId

    // Skip if already processed (idempotency)
    if (chainEventRepository.existsByChainIdAndTxHashAndLogIndex(chainId, txHash, logIndex)) {
        return
    }

    val event = ChainEvent(
        chainId = chainId,
        blockNumber = log.blockNumber.toLong(),
        txHash = txHash,
        logIndex = logIndex,
        eventType = decodeEventType(log),
        fromAddress = extractFromAddress(log),
        toAddress = extractToAddress(log),
        amount = extractAmount(log),
        contractAddress = log.address,
        rawTopics = log.topics.joinToString(","),
        rawData = log.data
    )

    chainEventRepository.save(event)
}
```

---

## 10. Cross-References

- ChainEventListener source: `blockchain-service/src/main/kotlin/com/ari/blockchain/listener/ChainEventListener.kt`
- OutboxPollerService source: `blockchain-service/src/main/kotlin/com/ari/blockchain/outbox/OutboxPollerService.kt`
- Reconciliation using chain events: `docs/avalanche/08-reconciliation-security.md`
- Avalanche Data API reference: `docs/avalanche/09-sdk-api-reference.md`
- Official Data API docs: `https://build.avax.network/docs/api-reference/data-api`
- Official Webhooks API docs: `https://build.avax.network/docs/api-reference/webhook-api`
