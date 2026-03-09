# 08 - Reconciliation and Security for ARI

> **Key Takeaways**
> - Daily reconciliation compares `AriStablecoin.totalSupply()` (on-chain) against the sum of all user stablecoin balances in the ledger (off-chain); any discrepancy above 0.00000001 TRY/EUR must trigger an alert
> - The minter key (blockchain-service operational key) and the admin/governance key (multi-sig) must NEVER be the same key
> - Store the minter key in AWS KMS (eu-central-1) or Azure Key Vault (Turkey Central) — never in a `.env` file or Kubernetes Secret in plaintext
> - MiCA (EU) requires stablecoin issuers to maintain reserves 1:1 with circulating supply; `totalSupply()` provides the on-chain proof
> - BDDK/SPK (Turkey) require transaction audit trails with cryptographic integrity; the combination of `blockchain.chain_events` + Avalanche's immutable block history satisfies this
> - The `NUMERIC(20,8)` ↔ `uint256` conversion introduces a 10-decimal-place truncation; reconciliation must use a tolerance of 0.00000001 (the 8th decimal place)

---

## 1. Daily Reconciliation Architecture

### What to Reconcile

ARI maintains two independent views of stablecoin supply:

| Source | What it contains | How to query |
|--------|-----------------|--------------|
| On-chain: `AriStablecoin.totalSupply()` | Total minted minus total burned; authoritative | `eth_call` to stablecoin contract |
| Off-chain: `ledger.accounts` sum | Sum of all user and system account balances for a given currency | `SELECT SUM(balance) FROM ledger.accounts WHERE currency = 'TRY'` |

In a correctly operating system, `totalSupply / 10^18 = SUM(balance)` (within rounding tolerance).

### Reconciliation Flow

```
Daily Reconciliation (scheduled at 02:00 UTC)
───────────────────────────────────────────────────────────

1. Query on-chain:
   ariTRY.totalSupply() → onChainSupply (BigInteger, 18 decimals)
   ariEUR.totalSupply() → onChainSupply (BigInteger, 18 decimals)

2. Query off-chain (PostgreSQL):
   SELECT SUM(balance) FROM ledger.accounts
   WHERE currency = 'TRY' AND account_type != 'CROSS_BORDER_TRANSIT'
   → offChainSupply (NUMERIC(20,8))

3. Convert on-chain to 8 decimals:
   onChainSupply8dp = onChainSupply / 10^18, rounded to 8dp

4. Compute discrepancy:
   discrepancy = ABS(onChainSupply8dp - offChainSupply)

5. If discrepancy > 0.00000001:
   - Alert: PagerDuty/OpsGenie CRITICAL
   - Log: full trace to shared.audit_log
   - Escalate: compliance officer notification
   Else:
   - Log: reconciliation PASSED with timestamp and both amounts
   - Store in blockchain.reconciliation_reports
```

### Kotlin Implementation Sketch

```kotlin
@Component
class DailyReconciliationService(
    private val web3jTr: Web3j,
    private val ariTryContract: AriStablecoin,
    private val jdbcTemplate: JdbcTemplate,
    private val alertService: AlertService
) {
    @Scheduled(cron = "0 0 2 * * *") // 02:00 UTC daily
    fun reconcile() {
        reconcileChain("TRY", ariTryContract)
        reconcileChain("EUR", ariEurContract)
    }

    private fun reconcileChain(currency: String, contract: AriStablecoin) {
        // On-chain supply
        val onChainWei: BigInteger = contract.totalSupply().send()
        val onChain8dp: BigDecimal = BigDecimal(onChainWei)
            .divide(BigDecimal.TEN.pow(18))
            .setScale(8, RoundingMode.HALF_UP)

        // Off-chain sum (exclude transit accounts — they are in-flight between chains)
        val offChain8dp: BigDecimal = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(balance), 0)
            FROM ledger.accounts
            WHERE currency = ?
              AND account_type != 'CROSS_BORDER_TRANSIT'
              AND account_type != 'FEES'
            """.trimIndent(),
            BigDecimal::class.java,
            currency
        )!!

        val discrepancy = (onChain8dp - offChain8dp).abs()
        val tolerance = BigDecimal("0.00000001") // 1 at the 8th decimal place

        if (discrepancy > tolerance) {
            alertService.criticalAlert(
                """
                RECONCILIATION FAILURE: $currency
                On-chain totalSupply: $onChain8dp
                Off-chain sum: $offChain8dp
                Discrepancy: $discrepancy
                """.trimIndent()
            )
        }

        // Always persist the reconciliation report (pass or fail)
        jdbcTemplate.update(
            """
            INSERT INTO blockchain.reconciliation_reports
            (currency, on_chain_supply, off_chain_sum, discrepancy, passed, reconciled_at)
            VALUES (?, ?, ?, ?, ?, NOW())
            """.trimIndent(),
            currency, onChain8dp, offChain8dp, discrepancy, discrepancy <= tolerance
        )
    }
}
```

### Precision Notes

- `NUMERIC(20,8)` stores at most 8 decimal places
- EVM `uint256` has 18 decimal places (wei)
- Converting 18dp → 8dp truncates the last 10 digits
- The maximum rounding error per operation is `0.000000005` (half of the smallest stored unit)
- Reconciliation tolerance is `0.00000001` which accommodates the rounding while rejecting real discrepancies

---

## 2. Cryptographic Proofs for Regulatory Audits

### On-Chain Proof: Block Hash as Root of Trust

Every transaction on ARI's L1 is part of a block, and every block has:
- A `blockHash` — immutable once finalized
- A `receiptsRoot` — Merkle root of all transaction receipts in the block
- A transaction index within the block

Together, these form a cryptographic audit trail: a regulator can independently verify any transaction by fetching the block and re-computing the Merkle proof.

```kotlin
// Generating an audit proof for a specific mint transaction
fun generateAuditProof(txHash: String, web3j: Web3j): AuditProof {
    val receipt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt.get()
    val block = web3j.ethGetBlockByNumber(
        DefaultBlockParameter.valueOf(receipt.blockNumber), false
    ).send().block

    return AuditProof(
        transactionHash = txHash,
        blockNumber = receipt.blockNumber.toLong(),
        blockHash = block.hash,
        blockTimestamp = block.timestamp.toLong(),
        transactionIndex = receipt.transactionIndex.toInt(),
        receiptsRoot = block.receiptsRoot,
        from = receipt.from,
        to = receipt.to,
        gasUsed = receipt.gasUsed.toLong(),
        logs = receipt.logs.map { log ->
            AuditLog(
                logIndex = log.logIndex.toInt(),
                contractAddress = log.address,
                topics = log.topics,
                data = log.data
            )
        }
    )
}
```

### MiCA Compliance: Reserve Proof

EU's Markets in Crypto-Assets Regulation (MiCA) requires stablecoin issuers (ART/EMT categories) to:
1. Maintain 1:1 reserves for each token in circulation
2. Publish regular attestations of reserve adequacy
3. Allow competent authority (e.g., BaFin in Germany) to verify reserves

The on-chain `totalSupply()` provides the circulation figure. The reconciliation report provides the attestation. For a production MiCA compliance package, generate a signed report:

```kotlin
data class MicaReserveAttestation(
    val reportDate: LocalDate,
    val currency: String,
    val circulatingSupply: BigDecimal,  // from totalSupply()
    val reserveBalance: BigDecimal,      // from off-chain bank account
    val ratio: BigDecimal,               // must be >= 1.0
    val blockNumber: Long,               // block at which totalSupply was read
    val blockHash: String,               // immutable proof
    val signerAddress: String,           // compliance officer's key
    val signature: String                // EIP-191 personal_sign
)
```

### BDDK/SPK Compliance: Transaction Audit Trail

Turkish banking regulations require:
- Transaction records retained for 10 years
- Records must be tamper-evident
- Real-time reporting to MASAK for suspicious transactions above thresholds

ARI's `blockchain.chain_events` table combined with the on-chain block history satisfies the tamper-evidence requirement — any modification to the database can be detected by re-querying the blockchain.

---

## 3. Key Management

### Key Inventory

ARI's system uses three distinct categories of cryptographic keys:

| Key | Purpose | Storage | Rotation |
|-----|---------|---------|----------|
| Minter key (EVM) | Signs mint/burn/bridge transactions | AWS KMS / Azure Key Vault | Annual or on compromise |
| Admin/governance key (EVM) | Multi-sig for contract upgrades, role grants | Hardware wallet (Ledger) | On personnel change |
| P-Chain SubnetAuth key | Manages validator set (pre-ACP-77 subnets) | Secure cold storage | Rarely (subnet lifecycle) |
| Node staker key | Identifies validator node on P-Chain | File on validator; backup in KMS | On node replacement |
| Internal API key | blockchain-service ↔ core-banking auth | AWS Secrets Manager | Quarterly |

### Minter Key: AWS KMS Integration

The minter key should be a Customer Managed Key (CMK) in AWS KMS. The blockchain-service signs transactions using KMS:

```kotlin
// Conceptual: KMS-backed transaction signing
// In practice, use a KMS-backed Signer that implements web3j's Credentials interface

import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.SignRequest
import software.amazon.awssdk.core.SdkBytes

class KmsCredentials(
    private val kmsClient: KmsClient,
    private val keyId: String,
    override val address: String
) : Credentials {

    override fun sign(transactionHash: ByteArray): Sign.SignatureData {
        val signRequest = SignRequest.builder()
            .keyId(keyId)
            .message(SdkBytes.fromByteArray(transactionHash))
            .messageType(MessageType.DIGEST)
            .signingAlgorithm(SigningAlgorithmSpec.ECDSA_SHA_256)
            .build()

        val response = kmsClient.sign(signRequest)
        val derSignature = response.signature().asByteArray()

        // Parse DER-encoded signature to r, s, v components
        return parseDerSignature(derSignature, transactionHash, address)
    }
}
```

### Multi-Sig for Governance Keys

The `DEFAULT_ADMIN_ROLE` holder and the deployer AllowList admin should be a multi-sig wallet. Recommended setup:

- **Tool**: Gnosis Safe (now known as "Safe") — supports Avalanche L1s
- **Threshold**: 2-of-3 or 3-of-5 signers
- **Signers**: ARI CTO + Head of Compliance + External Security Council member

All contract upgrades, role changes, and emergency pauses require multi-sig confirmation. This satisfies the separation of duties required by both BDDK and MiCA.

### Platform CLI Key Security

For operational keys used with Platform CLI (P-Chain operations, validator management):

```bash
# Keys are stored encrypted by default
platform keys generate --name ari-prod-validator

# Export only to secure storage (never to source control)
platform keys export --name ari-prod-validator --output-file /tmp/validator-key.txt
# Immediately move to secure storage:
aws secretsmanager create-secret \
  --name "ari/validator/p-chain-key" \
  --secret-string "$(cat /tmp/validator-key.txt)"
rm /tmp/validator-key.txt
```

---

## 4. Security Hardening

### Smart Contract Security

| Risk | Mitigation |
|------|-----------|
| Unauthorized minting | `MINTER_ROLE` restricted to blockchain-service address only; stored in KMS |
| KYC allowlist bypass | `_update()` hook enforces allowlist on all transfer paths, not just `transfer()` |
| Contract upgrade without authorization | `DEFAULT_ADMIN_ROLE` is a multi-sig, upgrades require 2-of-N signatures |
| Emergency: stolen minter key | `PAUSER_ROLE` holder can pause all transfers immediately |
| Bridge replay attack | TeleporterMessenger includes nonce-based replay protection |
| Large single mint | Consider adding per-transaction mint cap in the stablecoin contract |

### Node Security

| Risk | Mitigation |
|------|-----------|
| RPC endpoint exposed | UFW rules restrict port 9650 to blockchain-service VPC CIDR only |
| Validator key compromise | Keys stored in KMS; node communicates with KMS via IAM role |
| Malicious validator | PoA: only pre-approved validators can join; PoAManager contract managed by multi-sig |
| DoS on validator | Staking port 9651 restricted to known validator IPs only |
| Software vulnerability | Subscribe to AvalancheGo release notifications; patch within 72h of critical CVEs |

### Network Security

```bash
# Minimal UFW ruleset for ARI validator
ufw default deny incoming
ufw default allow outgoing

# Consensus traffic (between validators only)
ufw allow from <VALIDATOR_2_IP> to any port 9651/tcp

# RPC (blockchain-service only, internal VPC)
ufw allow from 10.0.0.0/16 to any port 9650/tcp

# Monitoring (Prometheus, internal only)
ufw allow from 10.0.0.0/16 to any port 9095/tcp

# SSH (bastion host only)
ufw allow from <BASTION_IP> to any port 22/tcp

ufw enable
```

---

## 5. Incident Response

### Severity Classification

| Severity | Definition | Examples | Response Time |
|----------|-----------|---------|--------------|
| P1 - Critical | Stablecoins being minted/burned incorrectly; minter key compromise | Unauthorized mint, reconciliation failure > 1% | Immediate (< 15 min) |
| P2 - High | Validator down, bridge stuck, RPC unreachable | Single validator failure, missed blocks | < 1 hour |
| P3 - Medium | Reconciliation warning, slow confirmation | Small discrepancy, high gas usage | < 4 hours |
| P4 - Low | Non-critical monitoring alert | Disk at 80%, certificate expiry warning | < 24 hours |

### P1 Response: Unauthorized Mint Detected

```
1. Pause the stablecoin contract IMMEDIATELY
   → Multi-sig holders sign `pause()` transaction
   → Target: < 5 minutes from detection to pause

2. Freeze the minter key in KMS
   → AWS KMS: Disable the key in Console or via CLI
   → This prevents any further transactions signed by the compromised key

3. Notify regulators (as required)
   → MiCA: Notify competent authority within 2 hours
   → BDDK: Report to TCMB/BDDK per incident reporting protocol

4. Root cause analysis
   → Examine blockchain.transactions for unauthorized mint transactions
   → Examine chain_events for corresponding on-chain events
   → Verify off-chain ledger for corresponding credits
   → Identify entry point (KMS policy breach? blockchain-service vulnerability?)

5. Recovery
   → Generate new minter key in KMS
   → Update blockchain-service configuration to use new key
   → Grant MINTER_ROLE to new key address
   → Unpause the stablecoin contract after confirming system integrity
   → Burn any illegitimately minted tokens

6. Post-incident
   → Regulatory disclosure report
   → Update incident response runbook
   → Security audit of the affected component
```

### P2 Response: Validator Down

```
1. Check validator health:
   curl http://validator1-tr.ari.internal:9650/ext/health

2. If unreachable, check cloud console for instance status

3. Start spare validator (pre-provisioned but not active):
   sudo systemctl start avalanchego

4. Wait for bootstrap:
   watch curl -s http://spare-validator:9650/ext/health | jq '.checks.bootstrapped.message'

5. Once bootstrapped, update blockchain-service config to include new RPC endpoint
   (or verify load balancer automatically detects health)

6. Total expected recovery time: 30-60 minutes
```

---

## 6. Compliance Considerations

### EU: MiCA (Markets in Crypto-Assets Regulation)

MiCA applies to ARI's ariEUR stablecoin as an "e-money token" (EMT):

| MiCA Requirement | ARI Implementation |
|-----------------|-------------------|
| Issuer authorization | ARI must be authorized as an EMT issuer by an EU competent authority (e.g., CSSF Luxembourg) |
| 1:1 reserve with bank deposits | Bank balance ≥ ariEUR.totalSupply() at all times; daily reconciliation verifies this |
| Redemption right | Users must be able to redeem ariEUR for EUR at par; burn path in smart contract provides this |
| White paper | Must be published and notified to competent authority |
| Transaction reporting | All mints/burns reported via the standard chain_events pipeline |
| Data residency | Personal data (user identity) stays in EU region; only token amounts cross-chain |

### Turkey: BDDK/SPK/MASAK

| Requirement | ARI Implementation |
|-------------|-------------------|
| PSP license | ARI must hold a Payment Service Provider license from BDDK |
| KYC/AML | Users on-boarded via Turkish KYC process; wallet addresses on ariTRY allowlist only after KYC |
| MASAK reporting | Transactions above statutory threshold (30,000 TRY) reported to MASAK |
| Data localization | Turkish user data stored in Azure Turkey Central; ariTRY chain nodes hosted in Turkey |
| 10-year retention | `blockchain.chain_events` and Avalanche block history both immutable and long-term |

### Key Regulatory Events and Blockchain Responses

| Event | Blockchain Action | Who Initiates |
|-------|------------------|---------------|
| User account frozen (court order) | Remove from KYC allowlist via `removeFromAllowlist()` | Admin multi-sig |
| Regulatory audit of a specific account | Pull all chain_events for that wallet address | Compliance team |
| Stablecoin supply attestation | Read `totalSupply()` at a specific block height | Automated daily |
| Emergency: market manipulation detected | Pause stablecoin contract | Pauser multi-sig |
| License revoked | Pause all operations, freeze all balances | Admin multi-sig |

---

## 7. Reconciliation Database Schema

```sql
-- Add to blockchain-service migrations

CREATE TABLE blockchain.reconciliation_reports (
    id              BIGSERIAL PRIMARY KEY,
    currency        VARCHAR(3) NOT NULL,         -- 'TRY' or 'EUR'
    on_chain_supply NUMERIC(30, 8) NOT NULL,     -- from totalSupply() / 10^18
    off_chain_sum   NUMERIC(30, 8) NOT NULL,     -- sum of ledger.accounts
    discrepancy     NUMERIC(30, 8) NOT NULL,
    passed          BOOLEAN NOT NULL,
    block_number    BIGINT,                      -- block at which on-chain was read
    block_hash      VARCHAR(66),
    reconciled_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reconciliation_currency_date
    ON blockchain.reconciliation_reports(currency, reconciled_at DESC);
```

---

## 8. Cross-References

- Smart contract roles and access control: `docs/avalanche/03-smart-contract-best-practices.md`
- Node staker key management: `docs/avalanche/06-node-infrastructure.md`
- Event indexing for audit trail: `docs/avalanche/07-event-listening-indexing.md`
- Compliance regulatory docs: `docs/compliance.md`
- Multi-region data residency ADR: `docs/adr/001-multi-region-data-residency.md`
