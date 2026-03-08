# ARI Testing Scripts

This directory contains scripts for deploying and testing the ARI platform, particularly the cross-border transfer functionality using Avalanche ICTT bridges.

## Overview

| Script | Purpose |
|--------|---------|
| `bootstrap-validators.sh` | Sets up Avalanche L1 validators on AWS |
| `deploy-contracts.sh` | Deploys smart contracts to both TR and EU L1 chains |
| `e2e-bridge-test.sh` | Runs end-to-end cross-border transfer test |

## Prerequisites

### Software Requirements
- AWS CLI v2 configured with appropriate credentials
- Terraform >= 1.6
- Node.js 20+
- Java 21 (for blockchain-service)
- jq
- curl

### Optional (for full L1 creation)
- Platform CLI (`platform subnet create`) — Install: `curl -sSfL https://build.avax.network/install/platform-cli | sh`
- Builder Console access (https://build.avax.network/console) — for ICM/Teleporter setup

## AWS 2-Validator Testing Setup (P5)

### Step 1: Bootstrap Validators

```bash
# Review what will be created (dry-run)
./scripts/bootstrap-validators.sh --dry-run

# Actually deploy validators
export ENVIRONMENT=dev
export AWS_REGION=eu-central-1
./scripts/bootstrap-validators.sh
```

This will:
1. Generate validator staking keys (RSA + BLS)
2. Upload keys to AWS Secrets Manager
3. Deploy 2 validators for TR L1 and 2 for EU L1 via Terraform
4. Provide instructions for manual L1 chain creation

### Step 2: Create L1 Chains (Manual)

After validators are running, create the L1 chains using Platform CLI and Builder Console:

```bash
# Install Platform CLI if not present
curl -sSfL https://build.avax.network/install/platform-cli | sh

# Import or generate deployer key
platform keys generate --name ari-deployer
# Fund with testnet AVAX via Fuji faucet, then transfer to P-Chain:
platform transfer c-to-p --amount 5 --key-name ari-deployer

# Create TR L1 subnet + chain
platform subnet create --key-name ari-deployer --network fuji
# Note the Subnet ID from output, then:
platform chain create --subnet-id <TR_SUBNET_ID> \
  --genesis genesis-tr.json --name ari-tr --key-name ari-deployer

# Convert subnet to L1 with validators
platform subnet convert-l1 --subnet-id <TR_SUBNET_ID> \
  --chain-id <TR_CHAIN_ID> --manager <VALIDATOR_MANAGER_ADDR> \
  --validators <VALIDATOR_IP>:9650 --key-name ari-deployer

# Repeat for EU L1
platform subnet create --key-name ari-deployer --network fuji
platform chain create --subnet-id <EU_SUBNET_ID> \
  --genesis genesis-eu.json --name ari-eu --key-name ari-deployer
platform subnet convert-l1 --subnet-id <EU_SUBNET_ID> \
  --chain-id <EU_CHAIN_ID> --manager <VALIDATOR_MANAGER_ADDR> \
  --validators <VALIDATOR_IP>:9652 --key-name ari-deployer

# Enable Teleporter messaging via Builder Console
# Go to https://build.avax.network/console and enable ICM for both L1s
```

### Step 3: Configure Environment

Copy and edit the environment file:

```bash
cp .env.aws-test.example .env.aws-test
# Edit with your deployed addresses and keys
```

Required variables:
- `DEPLOYER_PRIVATE_KEY` - Wallet with funds for deployment
- `MINTER_PRIVATE_KEY` - For mint/burn operations
- `BRIDGE_OPERATOR_PRIVATE_KEY` - For cross-chain operations
- `TREASURY_ADDRESS` - Fee collection address
- `TR_L1_RPC_URL` / `EU_L1_RPC_URL` - Validator endpoints

### Step 4: Deploy Contracts

```bash
# Deploy to both chains
./scripts/deploy-contracts.sh both

# Or deploy to specific chain
./scripts/deploy-contracts.sh tr
./scripts/deploy-contracts.sh eu
```

This will:
1. Compile all Solidity contracts
2. Deploy ariTRY stablecoin + TokenHome + TokenRemote to TR L1
3. Deploy ariEUR stablecoin + TokenHome + TokenRemote to EU L1
4. Configure cross-chain bridge connections
5. Grant operational roles
6. Update `.env.aws-test` with deployed addresses

### Step 5: Start Services

```bash
# Terminal 1: Start PostgreSQL and Redis
docker compose up -d

# Terminal 2: Start core-banking (loads .env.aws-test)
./gradlew :core-banking:bootRun

# Terminal 3: Start blockchain-service
./gradlew :blockchain-service:bootRun

# Terminal 4: Start web app
cd web && npm run dev
```

### Step 6: Run End-to-End Test

```bash
# Run with default amount (1000 TRY)
./scripts/e2e-bridge-test.sh

# Run with custom amount
./scripts/e2e-bridge-test.sh 5000
```

The test will:
1. Create TR user and EU user
2. Create TRY and EUR accounts
3. Fund TR user with TRY
4. Get FX quote for TRY→EUR
5. Initiate cross-border transfer
6. Wait for transfer completion
7. Verify balances on both sides
8. Verify blockchain settlement transactions
9. Run reconciliation check

## Architecture

```
┌─────────────────┐                      ┌─────────────────┐
│    TR L1        │                      │    EU L1        │
│  (Chain 99999)  │                      │  (Chain 99998)  │
├─────────────────┤                      ├─────────────────┤
│ • ariTRY        │                      │ • ariEUR        │
│ • TokenHome     │◄─── Teleporter ────►│ • TokenHome     │
│ • TokenRemote   │     Messages        │ • TokenRemote   │
│ • BridgeAdapter │                      │ • BridgeAdapter │
└────────┬────────┘                      └────────┬────────┘
         │                                        │
         │         ┌──────────────────┐          │
         └────────►│ Blockchain Svc   │◄─────────┘
                   │ (Java/Kotlin)    │
                   └────────┬─────────┘
                            │
                   ┌────────▼─────────┐
                   │  Core Banking    │
                   │ (Spring Modulith)│
                   └────────┬─────────┘
                            │
                   ┌────────▼─────────┐
                   │   Web/Mobile     │
                   │   Clients        │
                   └──────────────────┘
```

## Transfer Flow (TR→EU)

1. **User initiates transfer** via web app
2. **Core Banking** validates, creates payment order, debits TRY account
3. **Outbox event** triggers blockchain-service
4. **Blockchain Service**:
   - Burns TRY on TR L1
   - Calls TokenHome.bridgeTokens()
5. **Teleporter** relays message to EU L1
6. **EU L1 TokenRemote** mints wrapped TRY (wTRY)
7. **Chain Event Listener** detects completion
8. **Core Banking** credits EUR to recipient (after FX conversion)

## Troubleshooting

### Validators not starting
```bash
# Check validator logs
ssh -i ari-dev.pem ubuntu@<validator-ip> \
  "journalctl -u avalanchego -f"
```

### Contract deployment fails
```bash
# Check RPC connectivity
curl -X POST -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' \
  $TR_L1_RPC_URL
```

### Bridge transfer stuck
```bash
# Check blockchain transaction status
curl http://localhost:8081/api/v1/transactions?paymentOrderId=$TRANSFER_ID

# Check Teleporter relay status
# (requires access to relayer logs)
```

### Reconciliation fails
```bash
# Manual reconciliation trigger
curl -X POST http://localhost:8081/api/internal/reconciliation/run \
  -H "X-Internal-Api-Key: $INTERNAL_API_KEY"
```

## Cost Estimation

For development testing with 2 validators per L1:

| Resource | Specification | Monthly Cost (est.) |
|----------|---------------|---------------------|
| EC2 (4x t3.large) | 2 vCPU, 8GB RAM | ~$240 |
| EBS (4x 100GB gp3) | Validator storage | ~$40 |
| Data Transfer | ~50GB | ~$5 |
| Secrets Manager | 4 secrets | ~$2 |
| **Total** | | **~$290/month** |

For production, use m5.xlarge with 500GB storage and 3+ validators per L1.
