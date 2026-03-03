# ARI Platform Demo Script

**Duration**: 3-5 minutes
**Target**: Avalanche hackathon judges
**Focus**: On-chain components verified on Fuji testnet

---

## Pre-Recording Checklist

- [ ] Docker running: `docker compose up -d`
- [ ] Fuji L1s accessible: `curl -s -X POST ... TR_RPC` and `EU_RPC`
- [ ] core-banking running on :8080 (dev profile)
- [ ] blockchain-service running on :8081 (fuji profile)
- [ ] Web app running on :3000: `cd web && npm run dev`
- [ ] Demo data set up: `./scripts/setup-demo.sh`
- [ ] Wait 30s for on-chain mint to settle
- [ ] Browser open to `http://localhost:3000`
- [ ] Second browser tab: Fuji Subnet Explorer

## Architecture Overview (Show at start or end)

```
┌─────────────────────────────────────────────────────────────────┐
│                     ARI Fintech Platform                         │
│                                                                  │
│  ┌─────────────┐    ┌──────────────┐    ┌───────────────────┐   │
│  │  Web App     │───▶│ Core Banking  │───▶│ Blockchain Service │  │
│  │  (Next.js)   │    │ (Spring Boot) │    │ (Spring Boot)     │  │
│  └─────────────┘    └──────┬───────┘    └────────┬──────────┘   │
│                            │  Outbox Events       │              │
│                            ▼                      ▼              │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                  Avalanche Fuji Testnet                    │   │
│  │                                                            │   │
│  │  ┌─── TR L1 (Chain 1279) ───┐  ┌─── EU L1 (Chain 1832) ─┐│   │
│  │  │ ariTRY (ERC-20+KYC)     │  │ ariEUR (ERC-20+KYC)     ││   │
│  │  │ AriTokenHome (lock)     │  │ AriTokenHome (lock)      ││   │
│  │  │ AriTokenRemote (wEUR)   │  │ AriTokenRemote (wTRY)    ││   │
│  │  │ AriBridgeAdapter        │  │ AriBridgeAdapter          ││   │
│  │  └──────────┬─────────────┘  └──────────┬────────────────┘│   │
│  │             │       Teleporter/ICM       │                 │   │
│  │             └────────────────────────────┘                 │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Scene 1: User Onboarding (~30s)

**What to show**: Web app signup + instant KYC

**Steps**:
1. Open `http://localhost:3000`
2. Click "Sign Up"
3. Fill form: demo@ari.finance, phone, password, region=TR
4. Submit → auto-redirect to KYC page
5. Click "Verify" (simulated provider = instant approval)
6. Redirect to dashboard

**Talking points**:
- "ARI is a dual-region fintech platform for Turkey and EU"
- "KYC verification is required before any financial operations"
- "In production, this integrates with real identity verification providers"

---

## Scene 2: Deposit & On-Chain Mint (~60s)

**What to show**: TRY deposit triggers ariTRY stablecoin mint on Avalanche L1

**Steps**:
1. Dashboard shows TRY account with 10,000 TRY balance
2. Open a terminal and show the on-chain mint:
   ```bash
   # Show ariTRY balance on TR L1
   cast call $TR_STABLECOIN_ADDRESS "balanceOf(address)" $WALLET_ADDR --rpc-url $TR_RPC
   ```
3. Or show the blockchain-service logs with the mint transaction hash
4. Copy the tx hash and show it on the Fuji Subnet Explorer

**Talking points**:
- "When a user deposits TRY, the system mints ariTRY stablecoins on our Avalanche L1"
- "Each ariTRY is backed 1:1 by the deposited fiat in safeguarding accounts"
- "The stablecoin has a KYC allowlist — only verified users can hold tokens"
- "This is a real transaction on Fuji testnet, verifiable on the explorer"

**Key data to highlight**:
- Contract: `0x44F26f6812694184FC29D7b14FB91523948542a7` (ariTRY on TR L1)
- Transaction hash from blockchain-service logs

---

## Scene 3: Cross-Border Transfer via ICTT Bridge (~90s)

**What to show**: TRY → EUR transfer using Avalanche's Inter-Chain Token Transfer

**Steps**:
1. In web app, go to Transfer → Cross-Border tab
2. Select: From TRY account → To EUR account
3. Enter amount: 1,000 TRY
4. System shows FX quote: ~34.50 EUR (rate + spread)
5. Confirm transfer
6. Show progress:
   - "INITIATED" → "COMPLIANCE_CHECK" → "SETTLING"
   - Blockchain service processes BurnRequested (burns ariTRY on TR L1)
   - Teleporter relays message to EU L1
   - MintRequested (mints ariEUR on EU L1)
7. Show final status: COMPLETED

**Meanwhile in terminal**:
```bash
# Watch blockchain-service logs for bridge operations
# Show: BurnRequested → burn tx → bridge → MintRequested → mint tx
```

**On Fuji Explorer**:
- Show burn transaction on TR L1
- Show mint transaction on EU L1
- Point out Teleporter message relay

**Talking points**:
- "Cross-border transfers use Avalanche's ICTT bridge protocol"
- "ariTRY is burned on the TR L1, and ariEUR is minted on the EU L1"
- "Teleporter handles the cross-chain message relay automatically"
- "The entire flow is atomic — if any step fails, the transaction is rolled back"
- "FX conversion happens at real-time rates with transparent spread"

---

## Scene 4: Architecture Recap (~30s)

**What to show**: Architecture diagram overlay

**Talking points**:
- "ARI runs on two Avalanche L1s — one for Turkey (TRY), one for EU (EUR)"
- "Each L1 has a regulated stablecoin with KYC enforcement"
- "Cross-border transfers use ICTT bridge with Teleporter messaging"
- "The backend uses an outbox pattern for reliable blockchain integration"
- "All transactions are verifiable on-chain on the Fuji testnet"

---

## Fallback Plans

**If mint hasn't settled**:
- Show the MintRequested outbox event in the database
- Show the blockchain-service processing logs
- Explain the async flow

**If bridge takes too long**:
- Pre-execute the bridge before recording
- Show the completed state and on-chain results
- Speed up the waiting section in video editing

**If web app has issues**:
- Use the CLI demo scripts: `./scripts/demo-e2e.sh`
- All API calls work via curl

---

## Contract Addresses (for judges)

| Contract | Chain | Address |
|----------|-------|---------|
| ariTRY Stablecoin | TR L1 (1279) | `0x44F26f6812694184FC29D7b14FB91523948542a7` |
| ariEUR Stablecoin | EU L1 (1832) | `0x053c8E2872434cE438281b75010B6051D2577B77` |
| AriTokenHome (TR) | TR L1 | `0x48Bfa2eC3F2756631C2B3851e6F4BF74a8838D95` |
| AriTokenHome (EU) | EU L1 | `0x3CC7e983d7CAA5923Ab99655b6305cA04DB10a5F` |
| AriTokenRemote (TR) | TR L1 | `0x9490CA23a24EDeFc8Dc1841b25534DA2B8cB8D5b` |
| AriTokenRemote (EU) | EU L1 | `0x91d314bbb8f998ae28a488DF0548Ea5ee5dbe0D5` |
| AriBridgeAdapter (TR) | TR L1 | `0x6DD482b76Eb446E6fB3cE57e711ec021b8114f0e` |
| AriBridgeAdapter (EU) | EU L1 | `0x5b7bA996f044FE43CD9e2e84649dCa725570ac82` |

---

## Quick Start Commands

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Start services (separate terminals)
./scripts/run-fuji.sh core-banking
./scripts/run-fuji.sh blockchain-service

# 3. Start web app
cd web && npm run dev

# 4. Setup demo data
./scripts/setup-demo.sh

# 5. Run E2E demo (CLI version)
./scripts/demo-e2e.sh
```
