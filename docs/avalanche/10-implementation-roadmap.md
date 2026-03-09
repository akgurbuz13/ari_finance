# 10 - Implementation Roadmap for ARI

> **Key Takeaways**
> - The core ARI blockchain infrastructure is production-ready: contracts deployed on Fuji, mint/burn E2E verified, bridge operational
> - The highest-priority gap is the genesis file for ariTR (`requirePrimaryNetworkSigners` bug, high `minBaseFee`, missing precompiles)
> - Medium-priority improvements: upgradeable proxy pattern for stablecoin contracts, pre-deploying TeleporterMessenger in genesis, KMS-backed minter key
> - Long-term items: 3-of-5 validator set for production, Prometheus/Grafana monitoring stack, formal smart contract audit

---

## 1. Current State Assessment (as of 2026-03-09)

### What Is Complete and Deployed

| Component | Status | Evidence |
|-----------|--------|---------|
| AriStablecoin (ariTRY, ariEUR) | Deployed on Fuji | Fuji transactions confirmed |
| AriBurnMintBridge | Deployed on Fuji TR + EU L1 | 20 passing tests |
| AriTokenHome + AriTokenRemote (ICTT) | Deployed on Fuji | ICTT bridge setup complete |
| AriVehicleNFT + AriVehicleEscrow | Deployed on Fuji | 48 passing tests |
| blockchain-service (Kotlin) | Production-ready | All tests passing |
| core-banking (Kotlin) | Production-ready | 92% complete |
| Fuji ariTR L1 | Running on Fuji | Chain ID 1279 |
| Fuji ariEU L1 | Running on Fuji | Chain ID 1832 |
| E2E mint verification | Complete | tx 0x152bbe77... |
| 183 Solidity tests | All passing | `npx hardhat test` |

### What Has Never Been Done

| Gap | Category | Impact |
|-----|----------|--------|
| Mainnet L1 deployment | Infrastructure | Blocks go-live |
| 3+ validator setup | Infrastructure | Currently 2 validators per L1 (f=0) |
| KMS-backed minter key | Security | Minter key currently in config file |
| Prometheus/Grafana monitoring | Operations | No node-level alerting |
| Formal smart contract audit | Compliance | Required before mainnet |
| MiCA authorization | Legal/Compliance | Required before EU ariEUR go-live |
| BDDK PSP license | Legal/Compliance | Required before Turkey ariTRY go-live |
| Daily reconciliation job | Operations | No automated balance verification |
| Genesis file corrections | Infrastructure | See gap analysis below |

---

## 2. Gap Analysis: Genesis File

ARI's current `genesis-ariTR.json` has the following issues that should be corrected before the next L1 deployment:

### Issue 1: requirePrimaryNetworkSigners Should Be False

**Current:**
```json
"warpConfig": {
  "blockTimestamp": 0,
  "requirePrimaryNetworkSigners": true
}
```

**Recommended:**
```json
"warpConfig": {
  "blockTimestamp": 0
}
```

**Reason**: `requirePrimaryNetworkSigners: true` is only needed when the L1 needs to RECEIVE messages from the C-Chain or X-Chain. ARI's L1s only send and receive messages from each other. Keeping it true adds unnecessary consensus overhead (requires Primary Network validators to co-sign Warp messages).

### Issue 2: minBaseFee Is Too High

**Current:** `"minBaseFee": 25000000000` (25 gwei)

**Recommended:** `"minBaseFee": 1` (1 wei)

**Reason**: At 25 gwei, a standard ERC-20 mint (50,000 gas) costs 0.00125 native tokens per transaction. At 1 wei, it is effectively free. The blockchain-service pays all gas, so operational cost scales linearly with transaction volume.

### Issue 3: Missing Recommended Precompiles

The following precompiles are not in the current genesis but should be for production operations:

```json
"txAllowListConfig": {
  "blockTimestamp": 0,
  "adminAddresses": ["<MULTI_SIG_ADDRESS>"]
  // enabledAddresses: [] — no one enabled by default
  // This allows future restriction of who can submit txs
},
"feeManagerConfig": {
  "blockTimestamp": 0,
  "adminAddresses": ["<MULTI_SIG_ADDRESS>"],
  "initialFeeConfig": {
    "gasLimit": 12000000,
    "targetBlockRate": 2,
    "minBaseFee": 1,
    "targetGas": 15000000,
    "baseFeeChangeDenominator": 36,
    "minBlockGasCost": 0,
    "maxBlockGasCost": 1000000,
    "blockGasCostStep": 200000
  }
}
```

### Issue 4: TeleporterMessenger Not Pre-Deployed

The current genesis does not include the TeleporterMessenger contract. This means it must be deployed after genesis using Nick's method (funded via a burner address). Consider pre-deploying in genesis `alloc` for the mainnet deployment:

```json
"alloc": {
  "0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf": {
    "balance": "0x0",
    "code": "0x<TELEPORTER_BYTECODE>"
    // bytecode from: ava-labs/icm-contracts
  }
}
```

---

## 3. Prioritized Improvement List

### Priority 1: Critical (Security + Correctness)

These must be addressed before mainnet deployment:

| Item | Effort | Risk if Skipped |
|------|--------|-----------------|
| P1.1: Move minter key to AWS KMS / Azure Key Vault | 2 days | Critical: key in config file is a security incident waiting to happen |
| P1.2: Make admin key a multi-sig (Gnosis Safe) | 1 day | Critical: single-key admin means one compromised laptop = game over |
| P1.3: Fix genesis `requirePrimaryNetworkSigners` | 1 hour | Medium: incorrect Warp message validation on cross-chain txs |
| P1.4: Fix genesis `minBaseFee` to 1 wei | 1 hour | Low impact now, but operational cost accumulates |
| P1.5: Deploy 3rd validator per L1 | 2 days | Critical: 2 validators = zero Byzantine fault tolerance |

### Priority 2: High (Operational Readiness)

Required before handling real user funds:

| Item | Effort | Benefit |
|------|--------|---------|
| P2.1: Deploy Prometheus + Grafana on all validator nodes | 1 day | Node health visibility |
| P2.2: Implement DailyReconciliationService | 3 days | Detects supply discrepancies |
| P2.3: Add operator wallet balance monitoring + alert | 1 day | Prevent gas depletion |
| P2.4: Enable `feeManagerConfig` precompile in genesis | See P1.3 | Dynamic fee adjustment without hard fork |
| P2.5: Implement rolling upgrade procedure + test it | 1 day | Zero-downtime upgrades |

### Priority 3: Medium (Compliance + Auditability)

Required by regulatory timelines:

| Item | Effort | Requirement |
|------|--------|-------------|
| P3.1: Smart contract security audit | 4-8 weeks (external) | MiCA + BDDK requirement |
| P3.2: MiCA white paper for ariEUR | 2-4 weeks (legal) | Required before EU launch |
| P3.3: BDDK PSP license application | 3-6 months (legal) | Required before Turkey launch |
| P3.4: Implement MicaReserveAttestation generation | 3 days | Monthly regulatory reporting |
| P3.5: MASAK transaction reporting above threshold | 2 days | Required by Turkish AML law |

### Priority 4: Low (Enhancement)

Nice to have for production polish:

| Item | Effort | Benefit |
|------|--------|---------|
| P4.1: Upgrade stablecoin to upgradeable proxy pattern | 3 days | Enables bug fixes without full re-deployment |
| P4.2: Pre-deploy TeleporterMessenger in mainnet genesis | 1 day | Cleaner deployment, avoids Nick's method |
| P4.3: Add txAllowList to further restrict who can submit txs | 1 day | Defense in depth for permissioned chain |
| P4.4: Implement NativeMinter emergency top-up contract | 1 day | Automated gas recovery for operator wallet |
| P4.5: AvalancheGo Ledger hardware wallet support for P-Chain ops | 1 day | Better key security for validator management |

---

## 4. Testing Strategy

### Current Test Coverage

| Test Suite | Count | Status |
|-----------|-------|--------|
| Solidity (Hardhat) | 183 | All passing |
| blockchain-service (Kotlin/JUnit5) | Comprehensive | All passing |
| core-banking (Kotlin/JUnit5) | Comprehensive | All passing |
| E2E bridge test script | 1 scenario | Verified on Fuji |

### Testing Gaps to Address

#### 1. Reconciliation Integration Test

```kotlin
// blockchain-service integration test
@SpringBootTest
@Testcontainers
class ReconciliationIntegrationTest {
    @Test
    fun `reconciliation passes when supply matches ledger`() {
        // Mint 100 TRY on-chain
        // Credit 100 TRY to user in ledger
        // Run reconciliation
        // Assert: discrepancy < 0.00000001
    }

    @Test
    fun `reconciliation fails and alerts when discrepancy exists`() {
        // Mint 100 TRY on-chain
        // Credit only 99.99 TRY to ledger (simulate bug)
        // Run reconciliation
        // Assert: alertService.criticalAlert() was called
    }
}
```

#### 2. Bridge Failure Scenarios (Solidity)

```typescript
// What happens if the bridge message is not delivered?
it("should allow retry if Teleporter delivery fails", async () => {
    // Mock: Teleporter delivery fails
    // Assert: source chain tokens are locked (not lost)
    // Assert: retry mechanism works
});

// What happens if recipient is not on KYC allowlist at destination?
it("should revert at destination if recipient not allowlisted", async () => {
    // Send bridge message to destination where recipient is not KYC'd
    // Assert: destination receiveTeleporterMessage reverts
    // Assert: source tokens are in the bridge's pending state
});
```

#### 3. Node Failure Test

Not automatable via Hardhat, but should be performed manually before mainnet:
1. Stop one validator; verify blocks still produce
2. Stop two validators simultaneously; verify chain halts (expected for 3-of-3)
3. Restart stopped validators; verify chain resumes

#### 4. Key Rotation Test

Before mainnet, manually verify:
1. Generate new minter key in KMS
2. Call `grantRole(MINTER_ROLE, newKey)` on both stablecoin contracts
3. Call `revokeRole(MINTER_ROLE, oldKey)`
4. Update blockchain-service configuration to use new KMS key ID
5. Submit a test mint transaction; verify it succeeds with the new key

---

## 5. Mainnet Migration Strategy

The current Fuji deployment serves as the testnet reference. Mainnet deployment requires:

### Step 1: Mainnet L1 Creation (Platform CLI)

```bash
# Create mainnet subnet
platform subnet create --key-name ari-mainnet-key --network mainnet

# Create mainnet chains (use production genesis files with corrected parameters)
platform chain create \
  --subnet-id <MAINNET_SUBNET_ID> \
  --genesis genesis-ariTR-mainnet.json \
  --key-name ari-mainnet-key \
  --network mainnet

# Deploy ValidatorManager and PoAManager on the new chain
# Convert subnet to L1 with production validators
platform subnet convert-l1 \
  --subnet-id <MAINNET_SUBNET_ID> \
  --validators <VALIDATOR_1_ENDPOINT>,<VALIDATOR_2_ENDPOINT>,<VALIDATOR_3_ENDPOINT> \
  --key-name ari-mainnet-key \
  --network mainnet
```

### Step 2: Contract Deployment

```bash
# Deploy to mainnet L1 (TR)
cd contracts
npx hardhat run scripts/deploy-production.ts --network ariTRMainnet

# This deploys in order:
# 1. AriStablecoin (ariTRY proxy + implementation)
# 2. TeleporterMessenger (if not pre-deployed in genesis)
# 3. AriTokenHome
# 4. AriBurnMintBridge
# 5. AriVehicleNFT
# 6. AriVehicleEscrow

# Repeat for EU L1
npx hardhat run scripts/deploy-production.ts --network ariEUMainnet
```

### Step 3: Bridge Registration

```bash
# Register AriTokenRemote (wTRY on EU) with AriTokenHome (TRY on TR)
npx hardhat run scripts/register-bridges.ts --network ariEUMainnet
```

### Step 4: Validator Fee Funding

```bash
# Each validator needs continuous fee balance on P-Chain
# ~0.004 AVAX/hour per validator at target = ~1.33 AVAX/month
platform transfer c-to-p --amount 10 --key-name ari-mainnet-key --network mainnet
platform l1 add-balance --validation-id <VAL_1_ID> --balance 5.0 --key-name ari-mainnet-key
platform l1 add-balance --validation-id <VAL_2_ID> --balance 5.0 --key-name ari-mainnet-key
platform l1 add-balance --validation-id <VAL_3_ID> --balance 5.0 --key-name ari-mainnet-key
```

### Step 5: Smoke Tests

After mainnet deployment, run:
```bash
./scripts/e2e-mint-test.sh mainnet
./scripts/e2e-bridge-test.sh mainnet 100
```

---

## 6. Performance Benchmarks and Targets

### Throughput Targets

| Metric | Current (Fuji) | Target (Production) | Avalanche L1 Capacity |
|--------|---------------|--------------------|-----------------------|
| Mint transactions/min | Limited by test volume | 100 TPS | ~1,000 TPS |
| Bridge transfers/min | Not tested at scale | 10 TPS | ~100 TPS |
| Event indexing latency | 5s polling | < 10s | N/A |
| Settlement confirmation | ~3-5s | < 10s | ~1-2s (finality) |

### Latency Budget for a Mint Operation

```
User initiates mint on web app
    │ REST call to core-banking
    │ ~50ms
    ▼
core-banking creates outbox event
    │ database write
    │ ~10ms
    ▼
OutboxPollerService picks up event (max 2s polling interval)
    │ ~0-2000ms
    ▼
MintService submits transaction to ARI L1
    │ HTTP call to validator RPC
    │ ~100ms
    ▼
Transaction included in next block
    │ ~1-2s (Snowman consensus)
    ▼
TransactionReceipt returned
    │ web3j polling or waitForReceipt
    │ ~500ms
    ▼
REST callback to core-banking /settlement-confirmed
    │ ~50ms
    ▼
Total end-to-end: 2-5 seconds
```

### Resource Targets for Validator Nodes

| Metric | Warning Alert | Target | Notes |
|--------|--------------|--------|-------|
| CPU utilization | > 60% sustained | < 40% | At 100 TPS |
| RAM usage | > 70% | < 6 GB | Out of 8 GB total |
| Disk IOPS | > 70% | < 50% | Local NVMe |
| Peer count | < 3 | ≥ 2 other validators | Minimum for consensus |
| Last block age | > 5s | < 3s | Should be ~2s target block rate |

---

## 7. Timeline Recommendation

Assuming regulatory approvals proceed in parallel with technical work:

| Milestone | Technical Effort | Dependency |
|-----------|-----------------|------------|
| Genesis file corrections + test on Fuji | 1 week | None |
| KMS key integration + multi-sig deployment | 2 weeks | AWS KMS setup, Gnosis Safe setup |
| Add 3rd validator per L1 | 1 week | Cloud instance provisioning |
| Prometheus/Grafana monitoring | 1 week | None |
| Daily reconciliation service | 2 weeks | None |
| Smart contract audit engagement | 1 week (admin) | Audit firm selection |
| Smart contract audit (external) | 4-8 weeks | Audit firm |
| MiCA white paper and submission | 4 weeks | Legal team |
| Mainnet L1 creation + deployment | 1 week | All above complete |
| Regulatory soft launch (EU) | 1 day | MiCA approval |
| Regulatory full launch (Turkey) | 1 day | BDDK license |

Total technical path to mainnet readiness (excluding regulatory): **8-12 weeks**

---

## 8. Cross-References

- Genesis file issues: `genesis-ariTR.json` + `docs/avalanche/02-permissioned-l1-setup.md`
- KMS key management: `docs/avalanche/08-reconciliation-security.md`
- Monitoring setup: `docs/avalanche/06-node-infrastructure.md`
- Reconciliation service design: `docs/avalanche/08-reconciliation-security.md`
- Smart contract patterns: `docs/avalanche/03-smart-contract-best-practices.md`
- Bridge testing: `docs/avalanche/04-ictt-bridge-integration.md`
- Current Fuji deployment state: `docs/FUJI_L1_SETUP_GUIDE.md`
- Overall project progress: `PROGRESS.md`
