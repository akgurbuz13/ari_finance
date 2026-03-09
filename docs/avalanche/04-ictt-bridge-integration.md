# 04 - ICTT Bridge Integration

> **Key Takeaways**
> - ARI uses TWO distinct bridge systems: ICTT (lock/mint for FX cross-border TRY→EUR) and AriBurnMintBridge (burn/mint for same-currency cross-border TRY/TR→TRY/EU)
> - Both systems use Teleporter (ICM Contracts) as the underlying messaging layer
> - TeleporterMessenger is deployed at the canonical address `0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf` on ALL Avalanche chains
> - ICM Relayer (formerly AWM Relayer) is the off-chain process that aggregates BLS signatures and delivers messages; Builder Console provides a managed relayer for free
> - Warp messaging uses BLS multi-signatures verified against P-Chain validator records — no trusted third party
> - Pre-deploying TeleporterMessenger in genesis is the cleanest approach for new L1 deployments

---

## 1. Two Bridge Systems in ARI

```
FX Cross-Border (TRY → EUR):          Same-Currency Cross-Border (TRY/TR → TRY/EU):

User deposits TRY                       User sends TRY from TR region
        │                                       │
        ▼                                       ▼
AriTokenHome (TR L1)              AriBurnMintBridge (TR L1)
  locks ariTRY                       burns ariTRY
        │                                       │
        │ ICM message via Teleporter            │ ICM message via Teleporter
        │                                       │
        ▼                                       ▼
AriTokenRemote (EU L1)            AriBurnMintBridge (EU L1)
  mints wTRY (wrapped)               mints ariTRY (native)
        │                                       │
        ▼                                       ▼
Core-banking FX conversion          Receiver credited in EU ledger
User holds wTRY until converted       (only after on-chain confirmation)
```

Key difference: ICTT creates wrapped tokens (`wTRY`); AriBurnMintBridge uses the native stablecoin on both ends (no wrapped tokens). ARI's same-currency bridge is therefore simpler for users (they always hold native ariTRY) but requires the stablecoin to grant MINTER_ROLE to the bridge on both chains.

---

## 2. Avalanche Warp Messaging (AWM) — The Foundation

All cross-chain communication in Avalanche is built on **Avalanche Warp Messaging (AWM)**. Understanding this helps debug bridge issues.

### How AWM Works

```
Chain A (Source)                           Chain B (Destination)
     │                                            │
     │ 1. Contract calls                          │
     │    sendWarpMessage(payload)                │
     │    via Warp precompile                     │
     │    (0x0200...0005)                         │
     │                                            │
     │ 2. Warp precompile emits                   │
     │    SendWarpMessage log                     │
     │                                            │
     │ 3. Off-chain ICM Relayer                   │
     │    queries each validator                  │
     │    for their BLS signature                 │
     │                                            │
     │ 4. Relayer aggregates                      │
     │    signatures into one                     │
     │    BLS multi-signature                     │
     │                                            │
     │ 5. Relayer submits tx to Chain B           │
     │    with aggregated sig in Access List      │
     │                                            │
     │                              6. Chain B EVM verifies
     │                                 BLS sig against P-Chain
     │                                 validator set
     │                                            │
     │                              7. Warp precompile makes
     │                                 verified message available
     │                                 via getVerifiedWarpMessage()
     │                                            │
     │                              8. TeleporterMessenger reads
     │                                 the verified message and
     │                                 calls receiveTeleporterMessage()
     │                                 on the destination contract
```

### Quorum Requirements

By default (and in ARI's genesis), a Warp message is valid if validators representing ≥67% of the L1's stake weight have signed it. For ARI's 2-validator setup:
- Both validators must sign (100% weight, 100% stake)
- If one validator goes offline, cross-chain messages will stall
- For mainnet: plan for ≥4 validators so one offline doesn't block cross-chain transfers

---

## 3. TeleporterMessenger — Developer Interface

`TeleporterMessenger` is an ICM Contract (a smart contract that uses the Warp precompile) that adds:
- Replay protection (each message ID is unique and tracked)
- Retry support (messages can be resent if validator set changes invalidated the signature)
- Fee incentivization (optional ERC-20 fees for relayers)
- Allowed relayer lists

### Sending a Message

```solidity
// Contracts that use Teleporter implement this interface
interface ITeleporterMessenger {
    function sendCrossChainMessage(
        TeleporterMessageInput calldata messageInput
    ) external returns (bytes32 messageID);
}

struct TeleporterMessageInput {
    bytes32 destinationBlockchainID;  // 32-byte Avalanche blockchain ID of destination
    address destinationAddress;       // Contract address on destination chain
    TeleporterFeeInfo feeInfo;        // Optional relayer fee (use 0 for managed relayer)
    uint256 requiredGasLimit;         // Gas limit for the receiveTeleporterMessage call
    address[] allowedRelayerAddresses; // Empty = any relayer allowed
    bytes message;                    // ABI-encoded payload
}
```

### Receiving a Message

```solidity
interface ITeleporterReceiver {
    function receiveTeleporterMessage(
        bytes32 sourceBlockchainID,
        address originSenderAddress,
        bytes calldata message
    ) external;
}
```

ARI's `AriBurnMintBridge.sol` implements `ITeleporterReceiver` and is called by TeleporterMessenger when a cross-chain burn message arrives.

### Canonical Addresses

These addresses are the same on every Avalanche chain (C-Chain, Fuji, and any custom L1):

| Contract | Address | Note |
|----------|---------|------|
| TeleporterMessenger | `0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf` | Deployed via Nick's method |
| TeleporterRegistry (Fuji C-Chain) | `0xF86Cb19Ad8405AEFa7d09C778215D2Cb6eBfB228` | Chain-specific |

---

## 4. ICTT (Interchain Token Transfer) — FX Bridge

The ICTT framework (`ava-labs/icm-contracts/contracts/ictt/`) is the token bridge used for ARI's FX cross-border transfers (TRY → EUR).

### Architecture

```
TR L1                              EU L1
  │                                  │
AriTokenHome                   AriTokenRemote
(ERC20TokenHome)               (ERC20TokenRemote)
  │                                  │
  │ 1. User approves TokenHome       │
  │    to spend ariTRY               │
  │                                  │
  │ 2. TokenHome.send() locks        │
  │    ariTRY as collateral          │
  │    and emits ICM message         │
  │                                  │
  │ ──── ICM message via ──────────► │
  │      Teleporter                  │
  │                                  │ 3. TokenRemote mints wTRY
  │                                  │    (ERC20 representation)
  │                                  │    to recipient
  │                                  │
  │ ◄─── ICM message (return) ────── │ 4. When returning: TokenRemote
  │                                  │    burns wTRY, TokenHome unlocks
  │                                  │    ariTRY
```

### Token Scaling

`ERC20TokenRemote` supports scaling when the home and remote asset have different denominations. ARI's stablecoins both use 18 decimals, so no scaling is needed (`tokenMultiplier = 1`, `multiplyOnRemote = false` or default).

### Registration Flow

After deploying `AriTokenRemote`, it must register with `AriTokenHome`:

```typescript
// Call on the remote chain
await tokenRemote.registerWithHome({ feeTokenAddress: ethers.ZeroAddress, amount: 0n });
// This sends an ICM message to TokenHome, which then tracks this remote
```

ARI's `scripts/configure-bridge.ts` handles this registration.

### IcttBridgeService.kt Key Points

The `IcttBridgeService` in `blockchain-service` handles FX bridge initiation:

```kotlin
// From IcttBridgeService.kt (conceptual)
fun initiateBridgeTransfer(
    fromChainId: Long, toChainId: Long,
    fromAddress: String, toAddress: String,
    amount: BigDecimal, currency: String
): BridgeResult {
    val amountWei = amount.multiply(BigDecimal.TEN.pow(18)).toBigInteger()
    val destBlockchainId = blockchainConfig.getBlockchainId(toChainId)

    // Uses AriTokenHome or AriTokenRemote depending on direction
    val tokenHome = contractFactory.getTokenHome(fromChainId, credentials)
    tokenHome.send(
        destBlockchainId,    // destination blockchain ID (bytes32)
        toAddress,           // recipient on destination
        amountWei,           // amount to transfer
        BigInteger.ZERO,     // primary fee (0 = managed relayer)
        BigInteger.ZERO      // secondary fee
    )
}
```

---

## 5. AriBurnMintBridge — Same-Currency Bridge

ARI's custom `AriBurnMintBridge.sol` is simpler than ICTT because there are no wrapped tokens. The same ariTRY stablecoin exists natively on both TR L1 and EU L1.

### Flow

```
TR L1 (source)                     EU L1 (destination)
     │                                    │
     │ 1. AriBurnMintBridge.burnAndBridge()
     │    - burns ariTRY from sender      │
     │    - sends ICM message to dest     │
     │                                    │
     │ ──── Teleporter ICM ─────────────► │
     │                                    │
     │                      2. AriBurnMintBridge.receiveTeleporterMessage()
     │                         - validates source chain
     │                         - calls stablecoin.addToAllowlist(recipient)
     │                           (requires DEFAULT_ADMIN_ROLE)
     │                         - calls stablecoin.mint(recipient, amount)
     │                           (requires MINTER_ROLE)
```

### Roles Required on Destination Stablecoin

```solidity
// The bridge contract address must have BOTH roles on the stablecoin
stablecoin.grantRole(MINTER_ROLE, bridgeAddress);
stablecoin.grantRole(DEFAULT_ADMIN_ROLE, bridgeAddress);  // ethers.ZeroHash
```

This is the most common deployment mistake (see MEMORY.md). Forgetting `DEFAULT_ADMIN_ROLE` causes `addToAllowlist` to revert.

### OutboxPollerService Integration

```kotlin
// From OutboxPollerService.kt
private fun handleCrossBorderBurnMint(payload: JsonNode) {
    val destBlockchainIdHex = blockchainConfig.getBlockchainId(targetChainId)
    val destBlockchainIdBytes = Numeric.hexStringToByteArray(destBlockchainIdHex)

    val receipt = bridge.burnAndBridge(destBlockchainIdBytes, targetWalletAddress, amountWei)
    // On success, notify core-banking. Core-banking credits the receiver's EU account.
}
```

The receiver is NOT credited in the ledger until the `burnAndBridge` transaction is confirmed on-chain. This is the "transit account pattern" — the receiver's account stays in a pending state until blockchain confirmation.

---

## 6. Pre-Deploying TeleporterMessenger in Genesis

The recommended approach for new L1s is to include TeleporterMessenger directly in the genesis file's `alloc` section. This avoids the Nick's method funding step and ensures Teleporter is available immediately at block 0.

```json
"alloc": {
  "<DEPLOYER_ADDRESS>": {
    "balance": "0x295BE96E64066972000000"
  },
  "0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf": {
    "balance": "0x0",
    "code": "<TELEPORTER_BYTECODE>",
    "storage": {
      "0x0000000000000000000000000000000000000000000000000000000000000000": "0x0000000000000000000000000000000000000000000000000000000000000001",
      "0x0000000000000000000000000000000000000000000000000000000000000001": "0x0000000000000000000000000000000000000000000000000000000000000001"
    },
    "nonce": 1
  }
}
```

The TeleporterMessenger bytecode is available in the ICM Contracts release artifacts at `https://github.com/ava-labs/icm-contracts/releases`. Download the bytecode for the version you intend to use (currently v1.x).

Also pre-deploy the TeleporterRegistry (optional but recommended):

```
"0x618FEdD9A45a8C456812ecAAE70C671c6249DfaC": {
  "balance": "0x0",
  "nonce": 1
}
```

ARI's current genesis file (`genesis-ariTR.json`) does NOT pre-deploy TeleporterMessenger. It must be deployed separately via `scripts/deploy_teleporter.sh`. This is fine for the current Fuji setup but should be addressed for future L1 deployments.

---

## 7. ICM Relayer — The Off-Chain Component

The ICM Relayer (GitHub: `ava-labs/icm-services`) is the off-chain process that:
1. Watches source chains for Warp message events
2. Collects BLS signatures from source chain validators via P2P
3. Aggregates signatures
4. Submits the signed message to the destination chain

### Options for ARI

| Option | Pros | Cons |
|--------|------|------|
| Builder Console managed relayer | Free, no ops work, handles retry | Requires Builder Account, less control |
| Self-hosted icm-relayer | Full control, can customize routing | Ops burden, must manage keys |
| Both (redundant) | Highest reliability | More complexity |

### Self-Hosted Relayer Configuration

```json
{
  "log-level": "info",
  "p-chain-api": {
    "base-url": "https://api.avax-test.network"
  },
  "info-api": {
    "base-url": "https://api.avax-test.network"
  },
  "source-blockchains": [
    {
      "subnet-id": "<TR_SUBNET_ID>",
      "blockchain-id": "<TR_BLOCKCHAIN_ID_CB58>",
      "rpc-endpoint": { "base-url": "<TR_RPC_URL>" },
      "ws-endpoint": { "base-url": "<TR_WS_URL>" },
      "message-contracts": {
        "0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf": {
          "message-format": "teleporter",
          "settings": {}
        }
      }
    },
    {
      "subnet-id": "<EU_SUBNET_ID>",
      "blockchain-id": "<EU_BLOCKCHAIN_ID_CB58>",
      "rpc-endpoint": { "base-url": "<EU_RPC_URL>" },
      "ws-endpoint": { "base-url": "<EU_WS_URL>" },
      "message-contracts": {
        "0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf": {
          "message-format": "teleporter",
          "settings": {}
        }
      }
    }
  ],
  "destination-blockchains": [
    {
      "subnet-id": "<TR_SUBNET_ID>",
      "blockchain-id": "<TR_BLOCKCHAIN_ID_CB58>",
      "rpc-endpoint": { "base-url": "<TR_RPC_URL>" },
      "account-private-key": "${RELAYER_PRIVATE_KEY}"
    },
    {
      "subnet-id": "<EU_SUBNET_ID>",
      "blockchain-id": "<EU_BLOCKCHAIN_ID_CB58>",
      "rpc-endpoint": { "base-url": "<EU_RPC_URL>" },
      "account-private-key": "${RELAYER_PRIVATE_KEY}"
    }
  ],
  "storage-location": "./icm-relayer-storage",
  "process-missed-blocks": true
}
```

The relayer private key must be allowed to transact on both chains. If `txAllowListConfig` is enabled, add the relayer address to the enabled list.

### Relayer Reliability

- Set `process-missed-blocks: true` (default) so the relayer catches up after restarts
- Use Redis (`redis-url`) instead of local file storage for the relayer database in production
- The relayer is stateless except for the "last processed block" tracking; restarting it is safe
- Monitor the relayer's `/health` endpoint (port 8080 by default)

---

## 8. Blockchain IDs: CB58 vs Hex

ARI uses two formats for blockchain IDs:

| Format | Example | Used By |
|--------|---------|---------|
| CB58 | `9x7zHB85vsWaX2Bi...` | RPC URL path, Platform CLI, P-Chain |
| Hex (bytes32) | `0x1234...abcd` | Solidity, Teleporter `destinationBlockchainID` |

Converting between them in Kotlin:

```kotlin
import org.web3j.utils.Numeric
import org.apache.commons.codec.binary.Base58  // or use web3j's Numeric

// CB58 → hex bytes32
fun cb58ToHex(cb58: String): String {
    val decoded = Base58.decodeBase58(cb58)
    // Strip 4-byte checksum
    val withoutChecksum = decoded.copyOfRange(0, decoded.size - 4)
    return "0x" + Numeric.toHexStringNoPrefix(withoutChecksum).padStart(64, '0')
}
```

This conversion is needed when setting `ari.blockchain.bridge.tr-blockchain-id` in application config. The Builder Console shows both formats.

---

## 9. Monitoring Bridge Operations

Key metrics to monitor for bridge health:

| Metric | How to Check | Alert Threshold |
|--------|-------------|-----------------|
| ICM relayer health | `GET http://relayer-host:8080/health` | Any `down` status |
| Pending Warp messages | Count `SendWarpMessage` events not yet delivered | > 10 pending for > 5 minutes |
| Bridge contract balance (ICTT) | `tokenHome.totalBridgedOut()` vs `tokenHome.totalBalance()` | Any mismatch |
| BurnMintBridge events | Count `TokensBurned` on source vs `TokensMinted` on dest | Mismatch > 0 after 5 minutes |
| Validator online status | `platform wallet balance` (checks P-Chain balance) | Balance < 1 AVAX (30 days remaining) |

---

## 10. Cross-References

- Architecture overview: `docs/avalanche/01-architecture-overview.md`
- Genesis with Teleporter pre-deploy: `docs/avalanche/02-permissioned-l1-setup.md`
- Event listening for bridge events: `docs/avalanche/07-event-listening-indexing.md`
- Node infrastructure for relayer: `docs/avalanche/06-node-infrastructure.md`
- Bridge contracts: `contracts/AriBurnMintBridge.sol`, `contracts/AriTokenHome.sol`, `contracts/AriTokenRemote.sol`
- Bridge services: `blockchain-service/src/main/kotlin/com/ari/blockchain/bridge/IcttBridgeService.kt`
