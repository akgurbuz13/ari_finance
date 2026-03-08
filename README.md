# ARI Fintech Platform

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

## On-Chain Components (Avalanche L1)

ARI's core value proposition is built on-chain. Every financial operation — stablecoin issuance, cross-border settlement, compliance enforcement — is backed by smart contracts on Avalanche L1 blockchains.

### Why Avalanche L1s?

Traditional cross-border fintech relies on correspondent banking networks (SWIFT, SEPA) with 1-3 day settlement and opaque fee structures. ARI replaces this with two purpose-built Avalanche L1s that settle in seconds with full transparency.

**Regulatory isolation**: Turkey (BDDK/MASAK) and the EU (PSD2/MiCA) have different regulatory frameworks. By deploying a separate L1 per jurisdiction, each chain's validator set, governance, and compliance rules can be configured independently. Assets don't leak across regulatory boundaries — cross-border transfers go through an explicit bridge with audit trails.

**Permissioned by design**: Both L1s use Proof of Authority consensus. Validators are known entities (the platform operator for MVP, licensed financial institutions in production). This satisfies regulators who require identifiable transaction processors, while still getting the immutability and transparency benefits of blockchain settlement.

### AriStablecoin — KYC-Enforced Regulated Stablecoin

`AriStablecoinUpgradeable` is not a typical ERC-20. It enforces compliance at the token level:

- **KYC Allowlist**: Every `transfer()`, `mint()`, and `burn()` checks that both sender and recipient are on the KYC allowlist. Unverified addresses cannot hold or receive tokens. This is enforced in the smart contract itself — not just at the API layer.
- **Freeze/Unfreeze**: If a sanctions screening flags an address, compliance can call `freeze(address)` to immediately block all transfers to/from that address. The tokens remain in the wallet but cannot move.
- **Mint/Burn with Role Control**: Only addresses with `MINTER_ROLE` can create new tokens (backing new fiat deposits) or destroy them (processing withdrawals). This role is held by the blockchain service's operational key.
- **UUPS Upgradeable**: Deployed behind an ERC-1967 proxy so contract logic can be upgraded (e.g., adding new compliance checks) without migrating token balances.
- **Pause**: Emergency circuit breaker — `pause()` halts all token operations platform-wide.

```solidity
// Every transfer checks KYC status on-chain
function _update(address from, address to, uint256 value) internal override {
    require(!frozen[from], "AriStablecoin: sender frozen");
    require(!frozen[to], "AriStablecoin: recipient frozen");
    if (from != address(0)) require(kycAllowList.isAllowed(from), "AriStablecoin: sender not KYC");
    if (to != address(0)) require(kycAllowList.isAllowed(to), "AriStablecoin: recipient not KYC");
    super._update(from, to, value);
}
```

### ICTT Bridge — Cross-Chain Token Transfer via Teleporter

Cross-border transfers between TR and EU use Avalanche's native Inter-Chain Token Transfer (ICTT) protocol, powered by Teleporter/ICM messaging.

**How it works:**

```
User: "Transfer 1,000 TRY to EUR account"
         │
         ▼
┌─── Core Banking ────────────────────────────────────────────┐
│  1. Get FX quote (1,000 TRY → 34.50 EUR at market rate)    │
│  2. Debit sender's TRY ledger, credit receiver's EUR ledger │
│  3. Publish BurnRequested + MintRequested to outbox          │
└──────────────────────────────────────────┬──────────────────┘
                                           │
         ┌─────────────────────────────────┘
         ▼
┌─── Blockchain Service ─────────────────────────────────────┐
│  4. Process BurnRequested:                                  │
│     → AriTokenHome.bridgeTokens(amount, EU_BLOCKCHAIN_ID)  │
│     → ariTRY locked in TokenHome on TR L1                   │
│                                                             │
│  5. Teleporter sends cross-chain message (automatic via AWM)│
│                                                             │
│  6. AriTokenRemote on EU L1 receives message:              │
│     → Mints wrapped ariTRY (wTRY) on EU L1                 │
│                                                             │
│  7. Process MintRequested:                                  │
│     → Mint ariEUR to receiver's custodial wallet on EU L1   │
│                                                             │
│  8. Settlement callback → core-banking confirms completion  │
└─────────────────────────────────────────────────────────────┘
```

**The bridge contracts:**

| Contract | Role | Deployed On |
|----------|------|-------------|
| `AriTokenHome` | Locks native tokens, initiates Teleporter message to remote chain | Both L1s |
| `AriTokenRemote` | Receives Teleporter message, mints wrapped representation | Both L1s |
| `AriBridgeAdapter` | Orchestrates bridge flow: approve → lock → message → confirm | Both L1s |

Each L1 has both a TokenHome (for its native stablecoin) and a TokenRemote (for wrapped tokens from the other chain). This enables bidirectional transfers: TRY→EUR and EUR→TRY.

### Custodial Wallet Management

Users don't manage private keys. The platform creates and controls wallets on their behalf:

1. **Deterministic derivation**: Wallets are derived from a master key + userId + index via HMAC-SHA256. The same user always gets the same wallet address.
2. **Automatic KYC allowlisting**: When a wallet is created, the blockchain service calls `AriStablecoin.addToAllowlist(walletAddress)` so the wallet can receive tokens.
3. **Per-chain wallets**: Each user gets separate wallets on TR L1 and EU L1 (different regulatory jurisdictions = different on-chain identities).

### Verifying On-Chain

All contracts are deployed on Fuji testnet and can be queried directly:

```bash
# Check ariTRY balance of any address
cast call 0x44F26f6812694184FC29D7b14FB91523948542a7 \
  "balanceOf(address)(uint256)" <wallet_address> \
  --rpc-url http://127.0.0.1:9650/ext/bc/9x7zHB85.../rpc

# Check if an address is KYC-allowlisted
cast call 0x44F26f6812694184FC29D7b14FB91523948542a7 \
  "allowlisted(address)(bool)" <wallet_address> \
  --rpc-url http://127.0.0.1:9650/ext/bc/9x7zHB85.../rpc

# Check total ariTRY supply
cast call 0x44F26f6812694184FC29D7b14FB91523948542a7 \
  "totalSupply()(uint256)" \
  --rpc-url http://127.0.0.1:9650/ext/bc/9x7zHB85.../rpc
```

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
| Infrastructure | Docker Compose, Platform CLI, Builder Console, Fuji testnet |
| Cross-Chain | ICTT (Teleporter/ICM), AWM Relayer |

---

## Run Locally

### Prerequisites
- Docker, JDK 21, Node.js 20
- Platform CLI (`platform` command for P-Chain ops) — Install: `curl -sSfL https://build.avax.network/install/platform-cli | sh`

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
