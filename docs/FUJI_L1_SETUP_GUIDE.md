# ARI Fuji L1 Setup Guide

> **Last Updated:** 2026-03-09
> **Status:** Ready for deployment
> **Tooling:** Platform CLI + Builder Console (avalanche-cli is deprecated)

This guide walks you through creating two Avalanche L1 blockchains on Fuji testnet, deploying ARI smart contracts, and connecting the backend services.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [On-Chain Component Analysis](#2-on-chain-component-analysis)
3. [Prerequisites](#3-prerequisites)
4. [Step 1: Install Platform CLI](#step-1-install-platform-cli)
5. [Step 2: Create & Fund a Deployer Key](#step-2-create--fund-a-deployer-key)
6. [Step 3: Create Subnets on Fuji](#step-3-create-subnets-on-fuji)
7. [Step 4: Prepare Genesis Files](#step-4-prepare-genesis-files)
8. [Step 5: Create Blockchains](#step-5-create-blockchains)
9. [Step 6: Set Up Validator Nodes](#step-6-set-up-validator-nodes)
10. [Step 7: Convert Subnets to L1s](#step-7-convert-subnets-to-l1s)
11. [Step 8: Set Up ICM (Teleporter)](#step-8-set-up-icm-teleporter)
12. [Step 9: Deploy Smart Contracts](#step-9-deploy-smart-contracts)
13. [Step 10: Update Backend Configuration](#step-10-update-backend-configuration)
14. [Step 11: Start Services & Verify](#step-11-start-services--verify)
15. [Preventing Future Validator Outages](#preventing-future-validator-outages)
16. [Troubleshooting](#troubleshooting)

---

## 1. Architecture Overview

ARI uses two dedicated Avalanche L1 blockchains for regulatory isolation:

```
┌─────────────────────────────────────────────────────────────┐
│                    Avalanche Primary Network                 │
│                    (Fuji Testnet)                            │
│                                                              │
│  ┌─────────────────────┐    ┌──────────────────────┐        │
│  │   ariTR L1 (Turkey)  │    │   ariEU L1 (Europe)   │       │
│  │   Chain ID: TBD      │    │   Chain ID: TBD       │       │
│  │                      │◄──►│                       │       │
│  │  - ariTRY Stablecoin │ ICM│  - ariEUR Stablecoin  │       │
│  │  - Vehicle NFT       │Tele│  - AriTokenRemote     │       │
│  │  - Vehicle Escrow    │port│    (wTRY)              │       │
│  │  - AriTokenHome      │er  │  - AriBurnMintBridge  │       │
│  │  - AriBurnMintBridge │    │                       │       │
│  └─────────────────────┘    └──────────────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

**Why two L1s?**
- **TR L1**: BDDK (Turkish banking regulator) requires Turkish financial data to stay in-jurisdiction
- **EU L1**: GDPR/MiCA requires European financial data to stay in EU jurisdiction
- **Cross-chain transfers** use Avalanche Teleporter (ICM) — only economic value crosses, not personal data

---

## 2. On-Chain Component Analysis

### Source of Truth Assessment

These contracts are **genuinely authoritative** — not just mirrors of off-chain data:

| Contract | Classification | Why It's Real Source of Truth |
|---|---|---|
| **AriVehicleNFT** | **SOURCE OF TRUTH** | `ownerOf(tokenId)` is the definitive vehicle ownership record. VIN hash uniqueness prevents double-tokenization. Direct transfers blocked — only escrow can move NFTs. Replaces Turkey's notary system. |
| **AriVehicleEscrow** | **SOURCE OF TRUTH** | Atomic `_executeSwap` — ariTRY payment + NFT transfer in one tx. Irreversible. The on-chain state (CREATED/FUNDED/COMPLETED/CANCELLED) is authoritative. Neither buyer nor seller can cheat. |
| **AriStablecoin** | **Partial SOT** | Token balances are authoritative on-chain. Daily reconciliation verifies totalSupply matches off-chain ledger. But fiat-backing guarantee is off-chain. |
| **AriBurnMintBridge** | **Settlement Rail** | For same-currency cross-border (TRY/TR → TRY/EU): receiver is NOT credited in ledger until on-chain bridge tx confirms. Blockchain IS the settlement authority. |
| **AriTokenHome** | **SOT for locked tokens** | `totalBridgedOut` and contract balance are authoritative. The invariant `lockedBalance == totalBridgedOut` must hold for bridge solvency. |
| **AriTokenRemote** | **SOT for wrapped supply** | Wrapped token supply must equal `totalBridgedOut` on corresponding TokenHome. |

### Contracts That Are NOT Source of Truth

| Contract | Classification | Notes |
|---|---|---|
| **ValidatorManager.sol** | Mirror/registry | Does not control actual Avalanche consensus. P-Chain is real authority. |
| **KycAllowList.sol** | Orphaned | Deployed but never referenced by any other contract. |

### Key Demo Stories

1. **Vehicle Sale via On-Chain Escrow** — atomic swap replaces Turkey's broken notary process. Neither party can cheat. On-chain proof of ownership.
2. **Same-Currency Cross-Border Settlement** — TRY/TR → TRY/EU via burn/mint bridge. Receiver only gets money after blockchain confirms. Teleporter delivers cross-chain message.
3. **Stablecoin Minting** — on-chain supply always matches fiat backing (daily reconciliation proves it).

---

## 3. Prerequisites

### Required Software

| Tool | Purpose | Install |
|------|---------|---------|
| **Core Wallet** | P-Chain transactions (mandatory) | https://core.app (browser extension) |
| **Platform CLI** | P-Chain operations from command line | See Step 1 |
| **Node.js 20+** | Smart contract deployment | https://nodejs.org |
| **JDK 21** | Backend services | `brew install openjdk@21` |

### Important Notes

- **Disable MetaMask** while using Builder Console — Core and MetaMask conflict on the browser injected provider
- **Avalanche-CLI (`avalanche` command) is DEPRECATED** — do NOT use it. Use Platform CLI (`platform` command) instead.
- **Builder Console** (https://build.avax.network/console) handles ICM/Teleporter setup and node management

### Funding Requirements

| Operation | Approximate Cost |
|---|---|
| Create 2 subnets | ~0.2 AVAX |
| Create 2 blockchains | ~0.2 AVAX |
| Validator balances (2 validators, 3+ months) | ~8 AVAX |
| **Total needed on P-Chain** | **~10 AVAX** |

---

## Step 1: Install Platform CLI

```bash
# Install Platform CLI (replacement for deprecated avalanche-cli)
curl -sSfL https://build.avax.network/install/platform-cli | sh

# Verify installation
platform --help
platform version
```

Platform CLI defaults to Fuji testnet (`--network fuji`). For mainnet, add `--network mainnet`.

**Global flags available on all commands:**

| Flag | Description | Default |
|------|-------------|---------|
| `--network` / `-n` | `fuji` or `mainnet` | `fuji` |
| `--key-name` | Load key from keystore | |
| `--ledger` | Use Ledger hardware wallet | `false` |
| `--rpc-url` | Custom RPC URL | |

---

## Step 2: Create & Fund a Deployer Key

### Generate the key

```bash
# Generate an encrypted key (will prompt for password)
platform keys generate --name ari-deployer

# View the addresses
platform keys list --show-addresses
```

Output will show:
```
NAME            ENCRYPTED  DEFAULT  P-CHAIN              EVM                  CREATED
ari-deployer    yes        *        P-fuji1abc123...      0xdef456...          2026-03-09
```

**Save both addresses** — you need:
- **P-Chain address** (`P-fuji1...`) — for funding and P-Chain operations
- **EVM address** (`0x...`) — for contract deployment and genesis allocation

### Export the private key (needed for Hardhat)

```bash
# Export to file (secure)
platform keys export --name ari-deployer --format hex --output-file ./deployer-key.txt

# Or export to stdout (less secure)
platform keys export --name ari-deployer --format hex --unsafe-stdout
```

Save this as your `DEPLOYER_PRIVATE_KEY` for later steps.

### Fund the key with Fuji AVAX

**Option A — Builder Account faucet (easiest, gives P-Chain + C-Chain):**
1. Create a free account at https://build.avax.network
2. Go to https://build.avax.network/console/primary-network/faucet
3. Enter your C-Chain address (`0x...`)
4. AVAX arrives on both C-Chain and P-Chain automatically

**Option B — External faucet + cross-chain transfer:**
1. Get C-Chain AVAX from https://core.app/tools/testnet-faucet (2 AVAX)
2. Transfer C-Chain → P-Chain:
   ```bash
   platform transfer c-to-p --amount 2 --key-name ari-deployer
   ```

**Option C — Someone sends you AVAX:**
```bash
# Check your balance
platform wallet balance --key-name ari-deployer
```

**Target: 10+ AVAX on P-Chain** to cover subnet creation + validator balances.

---

## Step 3: Create Subnets on Fuji

A subnet is the container for your blockchain. You need one subnet per L1.

```bash
# Create the TR subnet
platform subnet create --key-name ari-deployer --network fuji
```

Output:
```
Creating new subnet...
Owner: P-fuji1abc123...
Submitting transaction...
Subnet created successfully!
Subnet ID: <TR_SUBNET_ID>
```

**Save the TR Subnet ID.**

```bash
# Create the EU subnet
platform subnet create --key-name ari-deployer --network fuji
```

**Save the EU Subnet ID.**

---

## Step 4: Prepare Genesis Files

Create genesis configuration files for each chain. These define the initial state of the EVM chain.

### `genesis-ariTR.json`

```json
{
  "config": {
    "chainId": 1279,
    "homesteadBlock": 0,
    "eip150Block": 0,
    "eip155Block": 0,
    "eip158Block": 0,
    "byzantiumBlock": 0,
    "constantinopleBlock": 0,
    "petersburgBlock": 0,
    "istanbulBlock": 0,
    "muirGlacierBlock": 0,
    "subnetEVMTimestamp": 0,
    "feeConfig": {
      "gasLimit": 12000000,
      "targetBlockRate": 2,
      "minBaseFee": 25000000000,
      "targetGas": 15000000,
      "baseFeeChangeDenominator": 36,
      "minBlockGasCost": 0,
      "maxBlockGasCost": 1000000,
      "blockGasCostStep": 200000
    },
    "contractDeployerAllowListConfig": {
      "adminAddresses": ["<YOUR_EVM_ADDRESS>"],
      "enabledAddresses": [],
      "blockTimestamp": 0
    },
    "warpConfig": {
      "blockTimestamp": 0
    }
  },
  "alloc": {
    "<YOUR_EVM_ADDRESS>": {
      "balance": "0x295BE96E64066972000000"
    }
  },
  "nonce": "0x0",
  "timestamp": "0x0",
  "extraData": "0x00",
  "gasLimit": "0xB71B00",
  "difficulty": "0x0",
  "mixHash": "0x0000000000000000000000000000000000000000000000000000000000000000",
  "coinbase": "0x0000000000000000000000000000000000000000",
  "number": "0x0",
  "gasUsed": "0x0",
  "parentHash": "0x0000000000000000000000000000000000000000000000000000000000000000"
}
```

**Replace `<YOUR_EVM_ADDRESS>`** with the deployer's EVM address (lowercase, no checksum).

The `balance` value `0x295BE96E64066972000000` = 50,000,000 AVAX (native gas token, pre-funded to deployer).

### `genesis-ariEU.json`

Same as above but change `"chainId": 1832`.

### Key Genesis Config Choices

| Setting | Value | Why |
|---|---|---|
| `chainId` | 1279 (TR) / 1832 (EU) | Reuse previous IDs for config compatibility |
| `gasLimit` | 12,000,000 | Standard for low-throughput L1 |
| `targetBlockRate` | 2 seconds | Fast enough for fintech |
| `contractDeployerAllowListConfig` | deployer only | Permissioned — only ARI can deploy contracts |
| `warpConfig` | enabled | **Required** for ICM/Teleporter cross-chain messaging |
| Pre-funded balance | 50M tokens | Plenty of native gas for deployer |

---

## Step 5: Create Blockchains

Create a blockchain on each subnet using the genesis files:

```bash
# Create ariTR blockchain
platform chain create \
  --subnet-id <TR_SUBNET_ID> \
  --genesis genesis-ariTR.json \
  --name ariTR \
  --key-name ari-deployer \
  --network fuji

# Create ariEU blockchain
platform chain create \
  --subnet-id <EU_SUBNET_ID> \
  --genesis genesis-ariEU.json \
  --name ariEU \
  --key-name ari-deployer \
  --network fuji
```

**Save the Blockchain IDs** from the output. These are CB58-encoded identifiers like `9x7zHB85vsWaX2Bi...`.

---

## Step 6: Set Up Validator Nodes

Each L1 needs at least one validator node running AvalancheGo and tracking the subnet.

### Option A: Builder Console Managed Nodes (Easiest)

1. Go to https://build.avax.network/console
2. Navigate to your L1
3. Use "Add Node" to spin up a managed validator
4. The console handles AvalancheGo installation, syncing, and subnet tracking

**Note:** Managed testnet nodes may auto-expire. Check the console for expiry times.

### Option B: Self-Hosted Node

Run AvalancheGo with `--track-subnets` pointing to your subnet IDs:

```bash
./avalanchego --network-id=fuji \
  --track-subnets=<TR_SUBNET_ID>,<EU_SUBNET_ID>
```

Get the node's info for validator registration:

```bash
platform node info --ip <NODE_IP>:9650
```

This returns the **NodeID**, **BLS Public Key**, and **BLS Proof of Possession** needed for the next step.

---

## Step 7: Convert Subnets to L1s

This converts permissioned subnets into L1 blockchains with a ValidatorManager contract. **This is irreversible.**

### Auto-Discovery Mode (if node is accessible)

```bash
# Convert ariTR subnet to L1
platform subnet convert-l1 \
  --subnet-id <TR_SUBNET_ID> \
  --chain-id <TR_BLOCKCHAIN_ID> \
  --manager <VALIDATOR_MANAGER_ADDRESS> \
  --validators <NODE_IP>:9650 \
  --validator-balance 5.0 \
  --key-name ari-deployer \
  --network fuji

# Convert ariEU subnet to L1
platform subnet convert-l1 \
  --subnet-id <EU_SUBNET_ID> \
  --chain-id <EU_BLOCKCHAIN_ID> \
  --manager <VALIDATOR_MANAGER_ADDRESS> \
  --validators <NODE_IP>:9650 \
  --validator-balance 5.0 \
  --key-name ari-deployer \
  --network fuji
```

### Mock Validator Mode (for testing)

```bash
platform subnet convert-l1 \
  --subnet-id <TR_SUBNET_ID> \
  --chain-id <TR_BLOCKCHAIN_ID> \
  --mock-validator \
  --key-name ari-deployer \
  --network fuji
```

**Fund validators with 5+ AVAX each** (~3.75 months of operation at ~1.33 AVAX/month).

### Verify the RPC endpoints

After conversion, your RPC URL format is:

```
http://<NODE_IP>:9650/ext/bc/<BLOCKCHAIN_ID_CB58>/rpc
```

Test connectivity:

```bash
# Test TR L1
curl -X POST -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","method":"eth_chainId","id":1}' \
  http://<NODE_IP>:9650/ext/bc/<TR_BLOCKCHAIN_ID>/rpc

# Expected: {"result":"0x4ff"} (1279 in hex)

# Test EU L1
curl -X POST -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","method":"eth_chainId","id":1}' \
  http://<NODE_IP>:9650/ext/bc/<EU_BLOCKCHAIN_ID>/rpc

# Expected: {"result":"0x728"} (1832 in hex)
```

---

## Step 8: Set Up ICM (Teleporter)

ICM (Interchain Messaging) enables cross-chain communication between your two L1s.

### Via Builder Console (Recommended)

1. Go to https://build.avax.network/console/icm/setup
2. Select your ariTR and ariEU chains
3. Deploy TeleporterMessenger on both chains
4. The console deploys the contracts at canonical addresses:
   - **TeleporterMessenger**: `0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf`
   - **TeleporterRegistry**: `0xF86Cb19Ad8405AEFa7d09C778215D2Cb6eBfB228`
5. With a Builder Account, you get a **free managed relayer** that delivers cross-chain messages

### Verify Teleporter Deployment

```bash
# Check Teleporter is deployed on TR L1
curl -X POST -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","method":"eth_getCode","params":["0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf","latest"],"id":1}' \
  <TR_RPC_URL>

# Should return bytecode (not "0x")
```

---

## Step 9: Deploy Smart Contracts

### Set environment variables

```bash
export DEPLOYER_PRIVATE_KEY="<your-hex-private-key>"
export TR_L1_RPC_URL="http://<NODE_IP>:9650/ext/bc/<TR_BLOCKCHAIN_ID>/rpc"
export EU_L1_RPC_URL="http://<NODE_IP>:9650/ext/bc/<EU_BLOCKCHAIN_ID>/rpc"
export TR_L1_CHAIN_ID=1279
export EU_L1_CHAIN_ID=1832
```

### Install dependencies

```bash
cd contracts && npm install
```

### Deploy to TR L1

```bash
# Core contracts: Stablecoin, KYC, Timelock, TokenHome, TokenRemote, BridgeAdapter
npx hardhat run scripts/deploy-fuji-l1s.ts --network ari-tr-testnet
```

Save all addresses from the output.

### Deploy to EU L1

```bash
npx hardhat run scripts/deploy-fuji-l1s.ts --network ari-eu-testnet
```

Save all addresses.

### Deploy AriBurnMintBridge (same-currency cross-border)

```bash
# Deploy bridge on TR L1
npx hardhat run scripts/deploy-burn-mint-bridge.ts --network ari-tr-testnet

# Deploy bridge on EU L1
npx hardhat run scripts/deploy-burn-mint-bridge.ts --network ari-eu-testnet
```

### Deploy Vehicle NFT + Escrow (TR L1 only)

```bash
npx hardhat run scripts/deploy-vehicle-escrow.ts --network ari-tr-testnet
```

### Configure ICTT Bridge Cross-Registration

```bash
npx hardhat run scripts/configure-bridge.ts --network ari-tr-testnet
```

### Deployment Checklist

After all deployments, verify you have addresses for:

- [ ] TR Stablecoin (ariTRY)
- [ ] EU Stablecoin (ariEUR)
- [ ] TR BridgeAdapter
- [ ] EU BridgeAdapter
- [ ] TR TokenHome
- [ ] EU TokenHome
- [ ] TR TokenRemote
- [ ] EU TokenRemote
- [ ] TR BurnMintBridge
- [ ] EU BurnMintBridge
- [ ] Vehicle NFT (TR only)
- [ ] Vehicle Escrow (TR only)
- [ ] TR Timelock
- [ ] EU Timelock
- [ ] Treasury address

---

## Step 10: Update Backend Configuration

### `blockchain-service/src/main/resources/application-fuji.yml`

Update with the new values from deployment:

```yaml
ari:
  core-banking:
    internal-api-key: dev-internal-api-key-never-use-in-production
  blockchain:
    tr-l1:
      rpc-url: <NEW_TR_RPC_URL>
      chain-id: 1279
      stablecoin-address: "<from deploy output>"
      timelock-address: "<from deploy output>"
    eu-l1:
      rpc-url: <NEW_EU_RPC_URL>
      chain-id: 1832
      stablecoin-address: "<from deploy output>"
      timelock-address: "<from deploy output>"
    bridge:
      tr-bridge-adapter-address: "<from deploy output>"
      eu-bridge-adapter-address: "<from deploy output>"
      tr-token-home-address: "<from deploy output>"
      eu-token-home-address: "<from deploy output>"
      tr-token-remote-address: "<from deploy output>"
      eu-token-remote-address: "<from deploy output>"
      tr-burn-mint-bridge-address: "<from deploy output>"
      eu-burn-mint-bridge-address: "<from deploy output>"
      tr-blockchain-id: "<Avalanche bytes32 blockchain ID for TR>"
      eu-blockchain-id: "<Avalanche bytes32 blockchain ID for EU>"
    wallet:
      master-key: ${DEPLOYER_PRIVATE_KEY:}
    relayer:
      private-key: ${DEPLOYER_PRIVATE_KEY:}
    keys:
      minter: ${DEPLOYER_PRIVATE_KEY:}
      admin: ${DEPLOYER_PRIVATE_KEY:}
      bridge-operator: ${DEPLOYER_PRIVATE_KEY:}
```

### `contracts/hardhat.config.ts`

Update the testnet network URLs (already env-var driven, just set the env vars):

```bash
export TR_L1_RPC_URL="<new TR RPC>"
export EU_L1_RPC_URL="<new EU RPC>"
export TR_L1_CHAIN_ID=1279
export EU_L1_CHAIN_ID=1832
```

### Important: Blockchain IDs vs Chain IDs

- **EVM Chain ID** (1279, 1832): used by MetaMask, Hardhat, web3j — the `chainId` in genesis
- **Avalanche Blockchain ID** (CB58 string like `9x7zHB85...`): Avalanche-native identifier, used in RPC URL path
- **Avalanche Blockchain ID as bytes32** (0x-prefixed hex): used by Teleporter for `destinationBlockchainID` in cross-chain messages

To get the bytes32 blockchain ID from the CB58 ID, use the Avalanche SDK or the Builder Console.

---

## Step 11: Start Services & Verify

### Start infrastructure

```bash
docker compose up -d
```

### Start core-banking

```bash
./gradlew :core-banking:bootRun
```

### Start blockchain-service with Fuji profile

```bash
SPRING_PROFILES_ACTIVE=fuji \
DEPLOYER_PRIVATE_KEY=<key> \
./gradlew :blockchain-service:bootRun
```

### Start web app

```bash
cd web && npm run dev
```

### Verify end-to-end

```bash
# Test mint flow
./scripts/e2e-bridge-test.sh 1000

# Test vehicle escrow
./scripts/e2e-vehicle-escrow-test.sh
```

### Quick on-chain verification

```bash
# Check stablecoin is deployed
curl -X POST -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","method":"eth_call","params":[{"to":"<STABLECOIN_ADDRESS>","data":"0x06fdde03"},"latest"],"id":1}' \
  <TR_RPC_URL>
```

---

## Preventing Future Validator Outages

The old ariTR (1279) and ariEU (1832) L1s went offline because validator AVAX balances depleted.

### How the fee model works

- Each L1 validator has a **continuous P-Chain fee** of ~1.33 AVAX/month
- Balance is set at registration time
- When balance hits zero → validator becomes **inactive**
- When < 2/3 stake weight is active → **chain halts entirely**

### Prevention

1. **Fund validators generously** — 12+ AVAX each (≈9 months of operation)
2. **Monitor balance regularly:**
   ```bash
   # Check via Platform CLI
   platform wallet balance --key-name ari-deployer
   ```
3. **Top up when low:**
   ```bash
   platform l1 add-balance \
     --validation-id <VALIDATION_ID> \
     --balance 5.0 \
     --key-name ari-deployer
   ```
   This is permissionless — anyone can top up any validator.
4. **Set a calendar reminder** to check balances monthly

---

## Troubleshooting

### "RPC endpoint not responding"

1. Check if the validator node is running and synced
2. Verify the blockchain ID in the RPC URL is correct
3. Test with: `curl -X POST -H "Content-Type: application/json" --data '{"jsonrpc":"2.0","method":"eth_blockNumber","id":1}' <RPC_URL>`

### "Chain ID mismatch"

The EVM chain ID must match between:
- Genesis file (`config.chainId`)
- Hardhat config (`chainId`)
- Backend config (`chain-id`)
- MetaMask/Core wallet network settings

### "Transaction reverted: allowlist"

If `contractDeployerAllowListConfig` is enabled in genesis, only the admin address can deploy contracts. Ensure the deployer address matches what's in genesis.

### "Teleporter message not delivered"

1. Verify ICM relayer is running (Builder Console manages this)
2. Check both chains have TeleporterMessenger deployed at `0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf`
3. Verify `warpConfig` is enabled in both genesis files

### "Validator went inactive"

```bash
# Top up the validator balance
platform l1 add-balance \
  --validation-id <VALIDATION_ID> \
  --balance 5.0 \
  --key-name ari-deployer
```

---

## Reference: Platform CLI vs Deprecated avalanche-cli

| Operation | Platform CLI (use this) | avalanche-cli (deprecated) |
|---|---|---|
| Generate key | `platform keys generate --name x` | `avalanche key create x` |
| List keys | `platform keys list --show-addresses` | `avalanche key list` |
| Check balance | `platform wallet balance` | `avalanche key list --keys x --ledger 0 --cchain` |
| C→P transfer | `platform transfer c-to-p --amount 5` | `avalanche key transfer` |
| Create subnet | `platform subnet create` | `avalanche subnet create x` |
| Create chain | `platform chain create --subnet-id x --genesis f.json` | `avalanche blockchain create x` |
| Convert to L1 | `platform subnet convert-l1` | `avalanche blockchain deploy x` |
| Add validator | `platform l1 register-validator` | `avalanche blockchain addValidator x` |
| Top up validator | `platform l1 add-balance` | `avalanche validator increaseBalance` |
| Node info | `platform node info --ip x` | N/A |

---

## Reference: Key Addresses & IDs (fill in after deployment)

```
# ─── Chain Infrastructure (deployed 2026-03-09) ───
TR Subnet ID:            2Sw7W5coLCB4EZRADRyfTCuPBF5QqxMxj3jL8cUWPpCdso1MGX
TR Blockchain ID (CB58): 2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM
TR Blockchain ID (hex):  0xb5a82a53e6366b84f980e4d2f13e583ca02f10eaf1ead220e23d036574799345
TR EVM Chain ID:         1279
TR RPC URL:              https://nodes-prod.18.182.4.86.sslip.io/ext/bc/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/rpc

EU Subnet ID:            5KvzdVWjkZu6YuFrWVKhpFq2ZyGqQgHEozgvJFhzJRdBfba6M
EU Blockchain ID (CB58): 7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt
EU Blockchain ID (hex):  0x0ea0530c367859873c37829bdbc918ad3da9f4c7bed68d083275efc310ab03f4
EU EVM Chain ID:         1832
EU RPC URL:              https://nodes-prod.18.182.4.86.sslip.io/ext/bc/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt/rpc

# ─── Deployer ───
EVM Address:             0xe9ce1Cd8179134B162581BEb7988EBD2e2400503
P-Chain Address:         P-fuji1ka380cuwnqtq08g636cmvp3wxz3nl4w3rh4kyg

# ─── TR L1 Contracts ───
ariTRY Stablecoin:       0x63d1a883130feeB9e863A4Ed974Dd1448A43aaa6
ariEUR (cross-currency): 0x78870378c9A1A3458B2188f3F6c96cD406A85DC7
TR Timelock:             0xde2E9ADbd664bA2266300349920c4FC9cAEBeAeE
TR BridgeAdapter:        0xcCf46814bdA0cA12e997bAC9CEc3Dc90B104e0C2
TR TokenHome:            0x1090B43270a8693C111fEe23D81FAcCC8Eee7A76
TR TokenRemote (wEUR):   0xe94BB4716255178e01bf34d1aE6A02edADc117B5
TR BurnMintBridge (TRY): 0x74CDb2b07e6e6441b71348E7812E7208eF909f24
TR BurnMintBridge (EUR): 0xA2Aa53A97A848343F7D399e186D237E905888Df4
Vehicle NFT:             0xF66B3253eBe361D2A3E14B45C82Acd2d5a1C44c1
Vehicle Escrow:          0x2F3e53AfE15263D1bc5f4b3a908628498CcECf55
Treasury:                0xe9ce1Cd8179134B162581BEb7988EBD2e2400503

# ─── EU L1 Contracts ───
ariEUR Stablecoin:       0xd354bb151EAbAd1BfaaE9a36c32e3e2CB16Ae232
ariTRY (cross-currency): 0xcCf46814bdA0cA12e997bAC9CEc3Dc90B104e0C2
EU Timelock:             0x3a6b3CFbC5EC7D61E6BDD57Ba15AEa8155d5798f
EU BridgeAdapter:        0x63d1a883130feeB9e863A4Ed974Dd1448A43aaa6
EU TokenHome:            0xD76af0Ef48d735BAB56302388A44B080B8A313fE
EU TokenRemote (wTRY):   0x444c7316C7DF741ed7bf470c4B0b56c923AB08bB
EU BurnMintBridge (EUR): 0x1C3C34dAe1503E64033Ec99A4f2a61F32AA2Be0E
EU BurnMintBridge (TRY): 0x5EB99416745b310b6D091E7Cb91C3B0297788144

# ─── Teleporter (canonical, same on all chains) ───
TeleporterMessenger:     0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf
TeleporterRegistry:      0xF86Cb19Ad8405AEFa7d09C778215D2Cb6eBfB228

# ─── Relayers (Builder Console managed, expire 2026-03-12) ───
Relayer 1 (C+EU+TR):    0xdca59Abbbb13E1e35C337F4989Bd7b057676663d
Relayer 2 (EU+TR):      0xBE1d4B56A5b62E29883d816c1F5aBce0D61F95a4
```
