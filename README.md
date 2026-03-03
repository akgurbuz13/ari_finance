# ARI Platform

**Regulated dual-region fintech on Avalanche L1s**

ARI is a cross-border payments platform built for Turkey and the EU. It uses two permissioned Avalanche L1 blockchains — one per regulatory jurisdiction — connected by Avalanche's ICTT (Inter-Chain Token Transfer) bridge with Teleporter messaging.

> Live on Fuji testnet. All contracts deployed and verified.

---

## Architecture

```
                          ARI Platform
  ┌──────────────────────────────────────────────────────┐
  │                                                      │
  │   Web App (Next.js)  ──>  Core Banking (Spring Boot) │
  │                                  │                   │
  │                           Outbox Events              │
  │                                  │                   │
  │                       Blockchain Service              │
  │                         (Spring Boot)                │
  │                           │       │                  │
  └───────────────────────────┼───────┼──────────────────┘
                              │       │
       ┌──────────────────────┘       └───────────────────────┐
       ▼                                                      ▼
  ┌─── TR L1 (Chain 1279) ────┐       ┌─── EU L1 (Chain 1832) ────┐
  │                            │       │                            │
  │  ariTRY    (ERC-20 + KYC) │       │  ariEUR    (ERC-20 + KYC) │
  │  TokenHome (lock/release)  │◄─────►│  TokenHome (lock/release)  │
  │  TokenRemote (mint wEUR)   │  ICM  │  TokenRemote (mint wTRY)   │
  │  BridgeAdapter             │       │  BridgeAdapter             │
  │                            │       │                            │
  └────────────────────────────┘       └────────────────────────────┘
              Teleporter / AWM Relayer
```

**Key design decisions:**
- **One L1 per jurisdiction** — TR and EU have independent regulatory requirements. Separate chains keep compliance boundaries clean.
- **KYC-enforced stablecoins** — ariTRY and ariEUR are ERC-20 tokens with allowlist-gated transfers. Only verified users can hold or transfer tokens.
- **ICTT bridge for cross-border** — Cross-border transfers burn on the source chain and mint on the destination chain via Teleporter. No wrapped tokens leave their jurisdiction.
- **Outbox pattern** — Core banking and blockchain service communicate via a shared outbox table, ensuring exactly-once delivery and crash recovery.

---

## Live on Fuji Testnet

| Contract | Chain | Address |
|----------|-------|---------|
| ariTRY Stablecoin | TR L1 (1279) | `0x44F26f6812694184FC29D7b14FB91523948542a7` |
| ariEUR Stablecoin | EU L1 (1832) | `0x053c8E2872434cE438281b75010B6051D2577B77` |
| AriTokenHome | TR L1 | `0x48Bfa2eC3F2756631C2B3851e6F4BF74a8838D95` |
| AriTokenHome | EU L1 | `0x3CC7e983d7CAA5923Ab99655b6305cA04DB10a5F` |
| AriTokenRemote | TR L1 | `0x9490CA23a24EDeFc8Dc1841b25534DA2B8cB8D5b` |
| AriTokenRemote | EU L1 | `0x91d314bbb8f998ae28a488DF0548Ea5ee5dbe0D5` |
| AriBridgeAdapter | TR L1 | `0x6DD482b76Eb446E6fB3cE57e711ec021b8114f0e` |
| AriBridgeAdapter | EU L1 | `0x5b7bA996f044FE43CD9e2e84649dCa725570ac82` |
| AriTimelock | TR L1 | `0x17E33D877b93bbBe3b2246f57D5aAB01E75BE1e1` |
| AriTimelock | EU L1 | `0xb1b15b4f25cE23637f81B1C0db2DDEBb8b477a8A` |

Teleporter: `0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf` (standard across all Avalanche chains)

---

## How It Works

### Deposit (Fiat to On-Chain)
1. User deposits TRY via banking rail
2. Core banking credits the user's ledger account
3. Outbox event `MintRequested` is published
4. Blockchain service creates a custodial wallet, adds to KYC allowlist, and mints ariTRY on-chain

### Cross-Border Transfer (TRY to EUR)
1. User requests transfer with a time-limited FX quote (30s expiry)
2. Core banking executes double-entry ledger postings across both currencies
3. Outbox publishes `BurnRequested` (TR side) + `MintRequested` (EU side)
4. Blockchain service burns ariTRY via AriTokenHome on TR L1
5. Teleporter relays the message to EU L1
6. AriTokenRemote mints wrapped ariTRY on EU L1
7. Settlement callback confirms completion to core banking

### ICTT Bridge Flow
```
TR L1:  ariTRY locked in AriTokenHome
           │
           ▼  Teleporter message
EU L1:  wTRY minted by AriTokenRemote
```

---

## Smart Contracts

10 Solidity contracts (0.8.24), 115 tests passing:

| Contract | Purpose |
|----------|---------|
| `AriStablecoinUpgradeable` | UUPS-upgradeable ERC-20 with KYC allowlist, freeze, pause, mint/burn roles |
| `AriStablecoin` | Non-upgradeable version for testing |
| `AriTokenHome` | ICTT home contract — locks native tokens, sends Teleporter messages |
| `AriTokenRemote` | ICTT remote contract — receives messages, mints wrapped tokens |
| `AriBridgeAdapter` | Orchestrates bridge operations with fee handling |
| `AriTimelock` | Governance timelock for admin operations |
| `KycAllowList` | On-chain KYC verification registry |
| `ValidatorManager` | L1 validator set management |

```bash
cd contracts && npx hardhat test    # 115 passing
cd contracts && npx hardhat coverage
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Smart Contracts | Solidity 0.8.24, Hardhat, OpenZeppelin 5.x |
| Backend | Kotlin, Spring Boot 3.2, JdbcTemplate, PostgreSQL 16 |
| Blockchain Integration | Web3j 4.10, outbox pattern, receipt polling |
| Frontend | Next.js 14, React 18, Tailwind CSS, Framer Motion |
| Infrastructure | Docker Compose, Avalanche CLI, Fuji testnet |
| Cross-Chain | ICTT (Teleporter/ICM), AWM Relayer |

---

## Run Locally

### Prerequisites
- Docker, JDK 21, Node.js 20
- Avalanche CLI (`avalanche network start` for local L1s)

### Quick Start
```bash
# 1. Infrastructure
docker compose up -d

# 2. Backend (two terminals)
./scripts/run-fuji.sh core-banking
./scripts/run-fuji.sh blockchain-service

# 3. Frontend
cd web && npm install && npm run dev

# 4. Demo
./scripts/run-fuji.sh demo-setup   # Create user, accounts, fund on-chain
./scripts/run-fuji.sh demo         # Full E2E: mint + cross-border + bridge
```

Web app: http://localhost:3000 | API: http://localhost:8080

---

## Repository Structure

```
ova_v1/
├── contracts/              # Solidity contracts + Hardhat tests (115 tests)
│   ├── contracts/          # AriStablecoin, TokenHome, TokenRemote, BridgeAdapter
│   ├── scripts/            # Deploy, configure-bridge, test-bridge
│   └── test/               # Full test suite
├── core-banking/           # Kotlin/Spring Boot modular monolith
│   ├── identity/           # Auth, KYC, 2FA
│   ├── ledger/             # Double-entry accounting
│   ├── payments/           # Domestic, cross-border, deposit/withdrawal
│   ├── fx/                 # FX rates + quotes
│   └── shared/             # Outbox, security, audit
├── blockchain-service/     # Blockchain integration service
│   ├── contract/           # Web3j contract wrappers
│   ├── bridge/             # ICTT bridge orchestration
│   ├── settlement/         # Mint/Burn services
│   └── wallet/             # Custodial wallet management
├── web/                    # Next.js customer web app
├── scripts/                # Run, demo, deployment scripts
├── infra/                  # K8s manifests, Terraform
└── docs/                   # Architecture, demo script, compliance
```

---

## Security Model

- **KYC-gated tokens**: Only allowlisted addresses can hold ariTRY/ariEUR
- **Role-based access**: Separate MINTER_ROLE, BRIDGE_OPERATOR_ROLE, DEFAULT_ADMIN_ROLE
- **Freeze capability**: Compliance team can freeze individual wallets
- **Timelock governance**: Admin operations go through AriTimelock
- **Idempotent payments**: All payment endpoints require Idempotency-Key headers
- **Double-entry ledger**: Every debit has a matching credit, fully auditable
- **Outbox pattern**: Exactly-once event delivery between services
