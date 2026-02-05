# Avalanche L1 Architecture Documentation for Ova

## Overview

Ova uses two permissioned Avalanche L1 blockchains for stablecoin settlement:
- **TR L1** (Chain ID: 99999) - Turkish Lira (TRY) stablecoin operations
- **EU L1** (Chain ID: 99998) - Euro (EUR) stablecoin operations

This document covers the architecture, deployment, and operational procedures for self-hosted Avalanche validators.

---

## 1. Architecture Overview

### Why Avalanche L1 for Fintech?

| Feature | Benefit for Ova |
|---------|-----------------|
| Sub-second finality | Instant settlement confirmation |
| Permissioned validators | KYC'd validators only (regulatory compliance) |
| Customizable consensus | Can tune for high-value, low-volume transactions |
| Native cross-chain (ICTT) | TR↔EU transfers without trusted bridges |
| Gas customization | Zero-cost transactions for allowlisted users |
| Horizontal scaling | Each L1 independent, no C-Chain congestion |

### Network Topology

```
                    ┌─────────────────────────────────────┐
                    │         Avalanche P-Chain           │
                    │    (Validator Set Management)       │
                    └───────────────┬─────────────────────┘
                                    │
              ┌─────────────────────┴─────────────────────┐
              │                                           │
    ┌─────────▼─────────┐                     ┌──────────▼──────────┐
    │      TR L1        │◄───── ICM ─────────►│       EU L1         │
    │  (Chain 99999)    │    Cross-chain      │   (Chain 99998)     │
    │                   │     Messaging       │                     │
    │ - OvaTRY Token    │                     │ - OvaEUR Token      │
    │ - TokenHome (TRY) │                     │ - TokenHome (EUR)   │
    │ - TokenRemote(EUR)│                     │ - TokenRemote(TRY)  │
    │ - ValidatorMgr    │                     │ - ValidatorMgr      │
    └─────────┬─────────┘                     └──────────┬──────────┘
              │                                          │
    ┌─────────▼─────────┐                     ┌──────────▼──────────┐
    │   3 Validators    │                     │    2 Validators     │
    │  (AWS eu-central) │                     │ (Azure Turkey)      │
    └───────────────────┘                     └─────────────────────┘
```

---

## 2. Validator Infrastructure

### Hardware Requirements

| Component | Minimum Spec | Recommended |
|-----------|--------------|-------------|
| CPU | 8 vCPUs | 16 vCPUs |
| RAM | 16 GB | 32 GB |
| Storage | 1 TB SSD | 2 TB NVMe SSD |
| Network | 100 Mbps | 1 Gbps |
| OS | Ubuntu 22.04 LTS | Ubuntu 22.04 LTS |

### AWS Instance Types
- **Recommended**: c5.2xlarge (8 vCPU, 16 GB RAM) - ~$250/month
- **Alternative**: c6i.2xlarge (newer generation)
- **Storage**: gp3 EBS, 3000 IOPS minimum

### Azure Instance Types (Turkey)
- **Recommended**: Standard_F8s_v2 (8 vCPU, 16 GB RAM)
- **Storage**: Premium SSD P30 minimum

### Network Configuration

| Port | Protocol | Purpose |
|------|----------|---------|
| 9650 | TCP | HTTP API |
| 9651 | TCP | P2P (staking) |
| 9653 | TCP | State sync |

```bash
# AWS Security Group rules
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxx \
  --protocol tcp \
  --port 9651 \
  --cidr 0.0.0.0/0

# Restrict API to internal only
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxx \
  --protocol tcp \
  --port 9650 \
  --source-group sg-internal
```

---

## 3. Validator Deployment

### Prerequisites

```bash
# Install avalanche-cli
curl -sSfL https://raw.githubusercontent.com/ava-labs/avalanche-cli/main/scripts/install.sh | sh -s

# Verify installation
avalanche --version
```

### Create L1 Configuration

```bash
# Create TR L1
avalanche blockchain create ova-tr \
  --evm \
  --genesis-file genesis-tr.json \
  --chain-id 99999 \
  --token-symbol OVA-TR

# Create EU L1
avalanche blockchain create ova-eu \
  --evm \
  --genesis-file genesis-eu.json \
  --chain-id 99998 \
  --token-symbol OVA-EU
```

### Genesis Configuration

```json
{
  "config": {
    "chainId": 99999,
    "feeConfig": {
      "gasLimit": 15000000,
      "targetBlockRate": 2,
      "minBaseFee": 1000000000,
      "targetGas": 15000000,
      "baseFeeChangeDenominator": 48,
      "minBlockGasCost": 0,
      "maxBlockGasCost": 1000000,
      "blockGasCostStep": 200000
    },
    "allowFeeRecipients": true,
    "contractDeployerAllowListConfig": {
      "adminAddresses": ["0xADMIN_MULTISIG_ADDRESS"],
      "enabledAddresses": []
    },
    "txAllowListConfig": {
      "adminAddresses": ["0xADMIN_MULTISIG_ADDRESS"],
      "enabledAddresses": ["0xBLOCKCHAIN_SERVICE_ADDRESS"]
    }
  },
  "alloc": {
    "0xADMIN_MULTISIG_ADDRESS": {
      "balance": "0x0"
    }
  },
  "timestamp": "0x0",
  "gasLimit": "0xE4E1C0",
  "difficulty": "0x0"
}
```

### Deploy to AWS

```bash
# One-command deployment
avalanche node create ova-validator-1 \
  --aws \
  --aws-profile ova-prod \
  --region eu-central-1 \
  --num-validators 3 \
  --node-type c5.2xlarge

# Check status
avalanche node status ova-validator-1
```

### Deploy Blockchain to Validators

```bash
# Deploy TR L1
avalanche blockchain deploy ova-tr \
  --cluster ova-validator-1 \
  --endpoint https://api.avax.network

# Deploy EU L1
avalanche blockchain deploy ova-eu \
  --cluster ova-validator-2 \
  --endpoint https://api.avax.network
```

---

## 4. ValidatorManager (Proof of Authority)

### Why PoA for Regulated Fintech?

- **KYC'd validators only**: All validators must pass identity verification
- **Controlled membership**: Multisig approves new validators
- **No token staking**: Validators don't need to hold tokens
- **Regulatory compliance**: Clear accountability for all validators

### Contract Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    PoAValidatorManager                       │
│  (Ownable by Gnosis Safe Multisig)                          │
├─────────────────────────────────────────────────────────────┤
│  + initiateValidatorRegistration(nodeID, blsPublicKey)      │
│  + completeValidatorRegistration(pChainSignature)           │
│  + initiateValidatorRemoval(nodeID)                         │
│  + completeValidatorRemoval(pChainSignature)                │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ ICM Message
                              ▼
                    ┌───────────────────┐
                    │    P-Chain        │
                    │ (Validator State) │
                    └───────────────────┘
```

### Deployment

```solidity
// contracts/contracts/validators/PoAValidatorManager.sol
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@avalabs/icm-contracts/validator-manager/PoAValidatorManager.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

contract OvaValidatorManager is PoAValidatorManager, Ownable {
    constructor(address _multisig) Ownable(_multisig) {}

    function initiateValidatorRegistration(
        bytes32 nodeID,
        bytes memory blsPublicKey,
        uint64 weight
    ) external onlyOwner returns (bytes32 validationID) {
        return _initiateValidatorRegistration(nodeID, blsPublicKey, weight);
    }

    function initiateValidatorRemoval(
        bytes32 validationID
    ) external onlyOwner {
        _initiateValidatorRemoval(validationID);
    }
}
```

### Adding a New Validator

```bash
# 1. KYC the validator operator (off-chain)

# 2. Initiate registration via multisig
cast send $VALIDATOR_MANAGER \
  "initiateValidatorRegistration(bytes32,bytes,uint64)" \
  $NODE_ID \
  $BLS_PUBLIC_KEY \
  100 \
  --private-key $MULTISIG_EXECUTOR

# 3. Wait for P-Chain message

# 4. Complete registration (anyone can call)
cast send $VALIDATOR_MANAGER \
  "completeValidatorRegistration(bytes)" \
  $P_CHAIN_SIGNATURE
```

---

## 5. ICTT Bridge (Interchain Token Transfer)

### Architecture

ICTT enables trustless cross-chain token transfers using Avalanche Warp Messaging (AWM).

```
TR L1                                              EU L1
┌────────────────────┐                  ┌────────────────────┐
│   TokenHome (TRY)  │                  │  TokenRemote (TRY) │
│                    │                  │                    │
│ - Locks TRY tokens │ ───── ICM ─────► │ - Mints wrapped    │
│ - Holds collateral │ ◄───── ICM ───── │ - Burns on redeem  │
└────────────────────┘                  └────────────────────┘

┌────────────────────┐                  ┌────────────────────┐
│ TokenRemote (EUR)  │                  │   TokenHome (EUR)  │
│                    │                  │                    │
│ - Mints wrapped    │ ◄───── ICM ───── │ - Locks EUR tokens │
│ - Burns on redeem  │ ───── ICM ─────► │ - Holds collateral │
└────────────────────┘                  └────────────────────┘
```

### Key Contracts (from ava-labs/icm-contracts)

| Contract | Purpose |
|----------|---------|
| `TokenHome` | Deployed on chain where token originates. Locks tokens as collateral. |
| `TokenRemote` | Deployed on remote chains. Mints/burns wrapped representations. |
| `TeleporterMessenger` | Core ICM messaging contract (already deployed by Avalanche). |

### Deployment Steps

```bash
# 1. Deploy TokenHome on TR L1 for OvaTRY
npx hardhat run scripts/deploy-token-home.ts --network tr-l1

# 2. Deploy TokenRemote on EU L1 for wrapped TRY
npx hardhat run scripts/deploy-token-remote.ts --network eu-l1

# 3. Register TokenRemote with TokenHome
# This sends an ICM message to establish the relationship
cast send $TOKEN_REMOTE \
  "registerWithHome()" \
  --rpc-url $EU_L1_RPC

# 4. Repeat for EUR (TokenHome on EU, TokenRemote on TR)
```

### Cross-Chain Transfer Flow

```
User wants to send 1000 TRY from TR L1 to EU L1:

1. User calls TokenHome.send(1000, EU_CHAIN_ID, recipient)
2. TokenHome locks 1000 TRY
3. TokenHome emits ICM message with transfer details
4. ICM Relayer picks up message
5. Relayer aggregates BLS signatures from TR L1 validators
6. Relayer submits signed message to EU L1
7. TokenRemote.receiveTokens() verifies signatures
8. TokenRemote mints 1000 wrapped TRY to recipient
```

### ICM Relayer Setup

```bash
# Clone relayer repository
git clone https://github.com/ava-labs/awm-relayer.git
cd awm-relayer

# Configure for Ova chains
cat > config.json << EOF
{
  "source-chains": {
    "99999": {
      "rpc-endpoint": "http://tr-l1-node:9650/ext/bc/tr/rpc",
      "warp-api-endpoint": "http://tr-l1-node:9650/ext/bc/tr/warp",
      "subnet-id": "TR_SUBNET_ID"
    },
    "99998": {
      "rpc-endpoint": "http://eu-l1-node:9650/ext/bc/eu/rpc",
      "warp-api-endpoint": "http://eu-l1-node:9650/ext/bc/eu/warp",
      "subnet-id": "EU_SUBNET_ID"
    }
  },
  "destination-chains": ["99999", "99998"],
  "relayer-key": "RELAYER_PRIVATE_KEY"
}
EOF

# Run relayer
./awm-relayer --config config.json
```

### Warp Message Verification

ICM uses BLS multi-signatures for trustless verification:

1. **Message Creation**: Source chain creates `WarpMessage` with payload hash
2. **Validator Signing**: Source chain validators sign message with BLS keys
3. **Signature Aggregation**: Relayer aggregates signatures into single BLS multi-sig
4. **Verification**: Destination chain verifies aggregate signature against source chain's validator set (from P-Chain)
5. **Threshold**: Default 67% of validator stake weight required

```solidity
// Verification happens automatically in Warp precompile
// 0x0200000000000000000000000000000000000005

// The ICM contracts call this internally:
(bool success, bytes memory result) = WARP_PRECOMPILE.call(
    abi.encodeWithSelector(
        IWarpMessenger.getVerifiedWarpMessage.selector,
        index
    )
);
```

---

## 6. Native Gas Sponsorship

### Why Not ERC-2771?

| ERC-2771 Approach | Native Sponsorship |
|-------------------|-------------------|
| Requires Trusted Forwarder contract | Built into L1 config |
| Complex signature verification | No signatures needed |
| Extra gas for meta-tx overhead | Zero overhead |
| Smart contracts must inherit ERC2771Context | No contract changes |

### Configuration

In genesis file, enable `allowFeeRecipients`:

```json
{
  "config": {
    "allowFeeRecipients": true,
    "feeConfig": {
      "minBaseFee": 0
    }
  }
}
```

### Fee Recipient Configuration

Validators can set a fee recipient that pays for gas:

```bash
# Validator config
avalanche node config set \
  --fee-recipient 0xOVA_GAS_SPONSOR_ADDRESS \
  --node ova-validator-1
```

### Transaction Allowlist

Only allowlisted addresses can submit transactions:

```solidity
// Call via admin multisig
IAllowList(TX_ALLOW_LIST_ADDRESS).setEnabled(
    userCustodialWallet,
    AllowList.Role.Enabled
);
```

This way:
- Only KYC'd users (with custodial wallets) can transact
- Gas is paid by the fee recipient (Ova's address)
- Users never need native tokens

---

## 7. Key Management

### Current State (UNSAFE)

```yaml
# DO NOT USE IN PRODUCTION
wallet:
  master-key: ${WALLET_MASTER_KEY:dev-master-key-do-not-use-in-production}
```

### Required Architecture

```
                    ┌──────────────────────┐
                    │   Gnosis Safe 3/5    │
                    │   (Admin Multisig)   │
                    └──────────┬───────────┘
                               │
       ┌───────────────────────┼───────────────────────┐
       │                       │                       │
       ▼                       ▼                       ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  AWS KMS     │      │  AWS KMS     │      │  AWS KMS     │
│ Minter Key   │      │ Bridge Key   │      │ Relayer Key  │
└──────────────┘      └──────────────┘      └──────────────┘
       │                       │                       │
       ▼                       ▼                       ▼
  OvaStablecoin          OvaBridgeAdapter         ICM Relayer
  (MINTER_ROLE)        (BRIDGE_OPERATOR)        (gas sponsor)
```

### AWS KMS Integration

```kotlin
// blockchain-service KMS signer
class KmsSigner(
    private val kmsClient: KmsClient,
    private val keyId: String
) : TransactionSigner {

    override fun signTransaction(tx: RawTransaction): ByteArray {
        val hash = TransactionEncoder.encode(tx)

        val signRequest = SignRequest.builder()
            .keyId(keyId)
            .message(SdkBytes.fromByteArray(hash))
            .messageType(MessageType.DIGEST)
            .signingAlgorithm(SigningAlgorithmSpec.ECDSA_SHA_256)
            .build()

        val response = kmsClient.sign(signRequest)
        return derToEthSignature(response.signature().asByteArray())
    }
}
```

### Gnosis Safe Deployment

```bash
# Deploy Safe on TR L1
npx hardhat run scripts/deploy-safe.ts --network tr-l1

# Configure with 5 signers, 3 threshold
# Signers should be hardware wallets held by different team members
```

---

## 8. Monitoring & Alerting

### Prometheus Metrics

AvalancheGo exposes metrics at `/ext/metrics`:

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'avalanchego'
    static_configs:
      - targets: ['validator-1:9650', 'validator-2:9650', 'validator-3:9650']
    metrics_path: '/ext/metrics'
```

### Key Metrics to Monitor

| Metric | Alert Threshold | Severity |
|--------|-----------------|----------|
| `avalanche_network_peers` | < 10 | Critical |
| `avalanche_consensus_polls_failed` | > 0 for 5m | Warning |
| `avalanche_consensus_blks_accepted` | 0 for 2m | Critical |
| `avalanche_db_get_count` | Sudden spike | Warning |
| `avalanche_health_checks_failing` | > 0 | Critical |

### Grafana Dashboards

Use pre-built dashboards from Ava Labs:

```bash
# Clone monitoring repo
git clone https://github.com/ava-labs/avalanche-monitoring.git

# Import dashboards to Grafana
# Main Dashboard, C-Chain Dashboard, Network Dashboard
```

### Alerting Rules

```yaml
# alertmanager rules
groups:
  - name: avalanche
    rules:
      - alert: ValidatorOffline
        expr: up{job="avalanchego"} == 0
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Validator {{ $labels.instance }} is offline"

      - alert: NoBlocksAccepted
        expr: rate(avalanche_consensus_blks_accepted[5m]) == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "No blocks accepted on {{ $labels.instance }}"

      - alert: ICMRelayerDown
        expr: up{job="icm-relayer"} == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "ICM Relayer is offline - cross-chain transfers will fail"
```

---

## 9. Disaster Recovery

### Validator Backup Strategy

```bash
# Daily snapshot of validator data
aws ec2 create-snapshot \
  --volume-id vol-xxx \
  --description "Validator daily backup $(date +%Y%m%d)"

# Retain 7 days of snapshots
aws ec2 describe-snapshots --filters "Name=description,Values=Validator*" \
  | jq -r '.Snapshots | sort_by(.StartTime) | .[:-7] | .[].SnapshotId' \
  | xargs -I {} aws ec2 delete-snapshot --snapshot-id {}
```

### Recovery Procedure

1. **Single Validator Failure**
   - Other validators continue consensus
   - Spin up replacement from latest snapshot
   - Register new validator via ValidatorManager

2. **Majority Validator Failure**
   - Pause contract operations (PAUSER_ROLE)
   - Investigate root cause
   - Restore validators from backups
   - Resume operations

3. **Complete Chain Failure**
   - Restore all validators from backups
   - Bootstrap from genesis if needed
   - Reconcile on-chain state with off-chain ledger

### Runbook

```markdown
## Validator Recovery Runbook

### Detection
1. Prometheus alert fires: ValidatorOffline or NoBlocksAccepted
2. Verify alert in Grafana dashboard
3. Check AWS/Azure console for instance status

### Diagnosis
1. SSH to validator: `ssh ubuntu@validator-ip`
2. Check logs: `journalctl -u avalanchego -f`
3. Check disk space: `df -h`
4. Check memory: `free -m`

### Recovery
1. If OOM: Increase instance size
2. If disk full: Prune old data or expand volume
3. If config issue: Restore from known-good config
4. If hardware failure: Launch new instance from snapshot

### Post-Recovery
1. Verify validator is synced
2. Verify validator is producing blocks
3. Close incident ticket
```

---

## 10. Security Considerations

### Smart Contract Security

| Control | Implementation |
|---------|----------------|
| Admin Multisig | 3-of-5 Gnosis Safe |
| Timelock | 48-hour delay for upgrades |
| Pausable | Emergency pause by admin |
| Upgradeable | UUPS proxy pattern |
| Access Control | OpenZeppelin AccessControl |

### Validator Security

| Control | Implementation |
|---------|----------------|
| SSH Access | Key-based only, no passwords |
| Firewall | Only ports 9651, 9653 public |
| Updates | Automated security patches |
| Keys | Stored in AWS KMS, not on disk |
| Monitoring | 24/7 alerting |

### Operational Security

| Control | Implementation |
|---------|----------------|
| Separation of Duties | Different keys for different roles |
| Audit Trail | All admin actions logged |
| Code Review | Required for all changes |
| Incident Response | Documented runbooks |

---

## 11. Future Considerations

### Custom VM

Avalanche supports custom Virtual Machines. For future optimization:

```
Potential Custom VM for Banking:
- Optimized for high-value, low-volume transactions
- Native double-entry accounting primitives
- Built-in compliance checks
- Faster finality for known validators
```

### Simplex Consensus

Alternative to Snowball consensus for permissioned environments:

```
Simplex Benefits:
- Even faster finality (single round)
- Deterministic validator selection
- Lower message complexity
- Better suited for small validator sets
```

### Multi-Region Expansion

```
Current:  TR L1 + EU L1
Future:   + US L1 (when expanding to US market)
          + APAC L1 (when expanding to Asia)

All connected via ICTT for global settlement
```

---

## References

- [Avalanche Builder Hub](https://build.avax.network)
- [Run Validators on AWS](https://build.avax.network/docs/tooling/create-avalanche-nodes/run-validators-aws)
- [ICTT Overview](https://build.avax.network/docs/cross-chain/interchain-token-transfer/overview)
- [ICM Contracts GitHub](https://github.com/ava-labs/icm-contracts)
- [ValidatorManager Contracts](https://build.avax.network/docs/avalanche-l1s/validator-manager/contract)
- [Node Monitoring](https://build.avax.network/docs/nodes/maintain/monitoring)
- [Proof of Authority](https://build.avax.network/academy/l1-validator-management/03-deploy-validator-manager/00-proof-of-authority)
