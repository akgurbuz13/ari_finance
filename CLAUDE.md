# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ARI is a regulated fintech platform (Turkey + EU) with a Kotlin/Spring Boot modular monolith backend, a separate blockchain settlement service, customer web app, admin console, Flutter mobile app, and Solidity smart contracts on Avalanche L1.

**Repository**: https://github.com/akgurbuz13/ari_finance
**Brand name**: ARI (uppercase, no dot, no period)
**Logo CSS**: `.ari-logo` in `web/app/globals.css` must use `text-transform: uppercase`. NEVER set it to lowercase.

### Commit Rules
- **NEVER add Co-Authored-By lines** to commit messages. User does not want Claude listed as co-author.

---

## ⚠️ IMPORTANT: Check Progress First

Before starting any work, **always read** [`PROGRESS.md`](./PROGRESS.md) to understand:
- Current implementation status
- Recently completed work
- Known issues and technical debt
- What has been tested vs what needs testing

This prevents duplicate effort and ensures you build on existing work rather than recreating it.

---

## Implementation Status (as of 2026-03-09)

> **Full details in [PROGRESS.md](./PROGRESS.md)**

| Component | Completion | Status |
|-----------|------------|--------|
| Core Banking Backend | 92% | ✅ Production-Ready |
| Blockchain Service | 97% | ✅ Production-Ready |
| Smart Contracts | 95% | ✅ Tested (183 tests), deployed on Fuji |
| Web App | 95% | ✅ Production-Ready |
| Admin Console | 90% | ✅ Production-Ready |
| Mobile App | 70% | 🔶 Needs Review |
| AWS Infrastructure | 85% | ✅ 2-Validator Ready |
| Payment Rails | 30% | ⚠️ Stubs Only |

### Same-Currency Cross-Border Transfers (2026-03-07)
- `AriBurnMintBridge.sol` — burn/mint via Teleporter, no wrapped tokens
- `SameCurrencyCrossBorderService.kt` — saga orchestrator with transit account pattern
- Region-based accounts: `V020__cross_border_same_currency.sql`
- Web app: same-currency toggle, blockchain settlement progress UI
- 20 new Solidity tests, zero compiler warnings

### Avalanche Hackathon MVP: All 6 phases complete (Phases 0-5)
- Rebrand from Ova to ARI, Fuji L1 deployment, contract deployment, backend integration, demo readiness
- E2E mint verified on Fuji TR L1
- CI fixes: Redis port, config prefix, JVM memory (2026-03-03)
- Cross-border region logic fix: `regionForCurrency()` helper (2026-03-03)

### Cross-Border Settlement Bug Fixes (2026-03-09)
- Account region now derives from user's home region (identity table), not currency
- Core-banking OutboxPoller exclusion list covers all 9 blockchain event types
- burnAndBridge custodial flow: mint-to-operator before burn
- V022 migration for `cross_border_same_ccy` payment type constraint
- Transaction history API path fixed in web app
- First successful on-chain cross-border settlement on Fuji TR L1

### Cross-Border Bridge Bug Fix (2026-03-10)
- `getBurnMintBridge(chainId)` was NOT currency-aware — EU→TR TRY transfers used the EUR bridge
- Fixed: `getBurnMintBridge(chainId, currency)` now uses 4 bridge addresses (one per chain+currency pair)
- Config: added `tr-eur-burn-mint-bridge-address` and `eu-try-burn-mint-bridge-address` to fuji profile
- Manual settlement: 99,950 TRY credited to Mustafa from stuck EU transit account

### Remaining Issues
- Mobile biometric auth incomplete
- Payment rails are intentional stubs (awaiting bank partnerships)

---

## Build & Run Commands

### Prerequisites
- Docker Compose for local PostgreSQL 16 + Redis 7: `docker compose up -d`
- JDK 21 (not 17!), Node.js 20, Flutter SDK 3.2+

### Quick Start (Web Testing)
```bash
# 1. Start infrastructure
docker compose up -d

# 2. Start backend (in one terminal)
./gradlew :core-banking:bootRun

# 3. Install and start web app (in another terminal)
cd web && npm install && npm run dev

# Web app: http://localhost:3000
# API: http://localhost:8080
```

### Backend (Kotlin/Spring Boot)
```bash
./gradlew :core-banking:bootRun          # Start core-banking on :8080
./gradlew :blockchain-service:bootRun    # Start blockchain-service on :8081
./gradlew :core-banking:test             # Run core-banking tests
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :blockchain-service:test --no-daemon  # Java 21 required!
./gradlew bootJar                        # Build JARs for both modules
./gradlew ktlintCheck                    # Lint check
```
Test framework: JUnit 5 + Kotest assertions + MockK + Testcontainers.

### Smart Contracts (Solidity 0.8.24 / Hardhat)
```bash
cd contracts && npm install              # Install dependencies
cd contracts && npx hardhat compile
cd contracts && npx hardhat test         # 183 tests
cd contracts && npx hardhat coverage     # Coverage report
```

### AWS Testing
```bash
./scripts/bootstrap-validators.sh --dry-run  # Preview infrastructure
./scripts/deploy-contracts.sh both           # Deploy to TR + EU L1
./scripts/e2e-bridge-test.sh 1000           # Test 1000 TRY transfer
```

### Avalanche Tooling (IMPORTANT)
**Avalanche-CLI (`avalanche` command) is DEPRECATED.** Use these replacements:
- **P-Chain operations** (keys, transfers, staking, subnets, L1 validators): **Platform CLI** (`platform` command)
- **ICM/Teleporter, node setup, L1 management**: **Builder Console** (https://build.avax.network/console)

```bash
# Platform CLI (replaces avalanche-cli for P-Chain ops)
platform keys generate --name mykey           # Was: avalanche key create
platform subnet create --key-name mykey       # Was: avalanche subnet create
platform chain create --subnet-id <id> --genesis genesis.json  # Was: avalanche blockchain create
platform transfer c-to-p --amount 5 --key-name mykey           # Was: avalanche key transfer
```

> **Note**: Scripts in `scripts/` still reference deprecated `avalanche` commands. Our Fuji L1s are already deployed and working — the old CLI was used one-time. Future re-deployments should use Platform CLI + Builder Console.

---

## Agentic Coding Guidelines

### Before Starting Work

1. **Read PROGRESS.md** — Understand what's done vs what's needed
2. **Read this CLAUDE.md** — Understand conventions and patterns
3. **Check auto memory** — `.claude/projects/.../memory/MEMORY.md` for learnings from previous sessions
4. **Run tests first** — Verify current state before making changes

### Working Patterns

#### Pattern: Incremental Verification
After each significant change:
```bash
# For Kotlin
./gradlew :module-name:compileKotlin

# For Solidity
npx hardhat compile

# For TypeScript
npm run build
```

#### Pattern: Test Before Commit
```bash
# Run relevant tests
./gradlew :core-banking:test
npx hardhat test

# Then commit
git add specific-files && git commit -m "Descriptive message"
```

#### Pattern: Prioritized Implementation
When given multiple tasks, create a priority list (P1, P2, P3...) and tackle them sequentially. Each priority should be:
- Independently verifiable
- Committable on its own
- Documented in PROGRESS.md when complete

### Common Pitfalls to Avoid

| Pitfall | Solution |
|---------|----------|
| Wrong Java version for blockchain-service | Use `JAVA_HOME=$(/usr/libexec/java_home -v 21)` |
| Mock return type mismatch in Kotlin | Use `answers { firstArg<T>().copy(id = 1L) }` not `returns Unit` |
| Ethers address checksum errors | Use `ethers.getAddress()` for EIP-55 checksums |
| Test fails due to KYC | Add `await token.addToAllowlist(address)` in test setup |
| Solidity stack too deep | Enable `viaIR: true` in hardhat.config.ts |
| Account region defaults to currency | Region must derive from user's home region, not `regionForCurrency()` |
| burnAndBridge reverts (balance 0) | Custodial flow: mint tokens to operator BEFORE calling burnAndBridge |
| Cross-border uses wrong bridge | `getBurnMintBridge(chainId, currency)` — bridge is per (chain, currency) pair, NOT per chain alone |
| Core-banking steals blockchain events | Add new event types to `OutboxPoller.kt` exclusion list |
| Wrong chain IDs on Fuji | Set `ARI_TR_CHAIN_ID=1279` and `ARI_EU_CHAIN_ID=1832` |

### Code Patterns

#### Kotlin Repository Mocking
```kotlin
// Correct pattern for mocking save() that returns the entity
every { repository.save(any()) } answers {
    firstArg<Entity>().copy(id = 1L)
}

// For methods that return lists
every { repository.findByX(any()) } returns listOf(entity1, entity2)
```

#### Solidity Test Pattern
```typescript
// Always set up allowlist for KYC-restricted tokens
beforeEach(async () => {
    await token.addToAllowlist(user.address);
    await token.addToAllowlist(admin.address);
});

// Use ethers.getAddress() for address comparisons
expect(await contract.someAddress()).to.equal(ethers.getAddress(expected));
```

### When to Update Documentation

Update **PROGRESS.md** when:
- Completing a significant feature
- Fixing a known issue
- Discovering new technical debt
- Adding new tests

Update **MEMORY.md** when:
- Learning a pattern that will help future sessions
- Discovering a common pitfall
- Finding an important file location

---

## Architecture Quick Reference

### Critical Paths

| Operation | Key Files |
|-----------|-----------|
| User Auth | `AuthController.kt` → `JwtTokenProvider.kt` |
| P2P Transfer | `PaymentController.kt` → `DomesticTransferService.kt` → `LedgerService.kt` |
| Cross-Border (FX) | `CrossBorderTransferService.kt` → FX Quote → Ledger → Outbox → Blockchain |
| Cross-Border (Same-Ccy) | `SameCurrencyCrossBorderService.kt` → Transit Account → Outbox → `AriBurnMintBridge.sol` |
| Mint/Burn | `OutboxPollerService.kt` → `MintService.kt`/`BurnService.kt` → `AriStablecoin.sol` |
| ICTT Bridge | `IcttBridgeService.kt` → `AriTokenHome.sol` ↔ Teleporter ↔ `AriTokenRemote.sol` |
| Burn/Mint Bridge | `OutboxPollerService.kt` → `AriBurnMintBridgeContract.kt` → `AriBurnMintBridge.sol` |

### Module Boundaries

Core Banking modules communicate via:
- **Sync**: Direct service calls within same module
- **Async**: Outbox events for cross-module communication
- **Never**: Direct API calls between modules

Blockchain Service communicates via:
- **Inbound**: Reads `shared.outbox_events` table
- **Outbound**: REST callbacks to `/api/internal/settlement-confirmed`

**Outbox event ownership**: Both core-banking and blockchain-service poll the same `shared.outbox_events` table. Core-banking's `OutboxPoller` MUST exclude all blockchain event types (`MintRequested`, `BurnRequested`, `CrossBorderBurnMintRequested`, etc.) to prevent stealing events from blockchain-service. When adding a new blockchain event type, add it to core-banking's exclusion list in `OutboxPoller.kt`.

### Database Access

**Local**: Database `ari`, user `ari`, container `ari-postgres`.
**Production (Neon)**: Database `neondb`, user `neondb_owner`. Credentials in auto-memory `database-credentials.md`.
```bash
# Local:
docker exec ari-postgres psql -U ari -d ari -c "SELECT ..."
# Production:
export PATH="/opt/homebrew/opt/libpq/bin:$PATH" && PGPASSWORD='...' psql "postgresql://neondb_owner@...neon.tech/neondb?sslmode=require"
# identity.users columns: id, email, phone, password_hash, first_name, last_name (NOT full_name), date_of_birth, nationality, status, region, totp_secret, totp_enabled, created_at, updated_at, role
```
**Note**: Schemas are identical (identity, ledger, payments, shared). `ledger.entries` has a NOT NULL `balance_after` column — always calculate it when inserting manually.

| Schema | Purpose |
|--------|---------|
| `identity` | Users, KYC, 2FA |
| `ledger` | Accounts, transactions, entries |
| `payments` | Payment orders, status history |
| `shared` | Outbox, audit log |
| `blockchain` | Chain transactions, wallets |

---

## Key Conventions

- **Idempotency**: All payment/ledger endpoints require `Idempotency-Key` header
- **Repositories**: Raw `JdbcTemplate` SQL, not JPA
- **Events**: Via `OutboxPublisher`, never direct dispatch
- **Currencies**: TRY and EUR only
- **Amounts**: `BigDecimal` / `NUMERIC(20,8)`
- **Migrations**: `V{NNN}__{description}.sql`
- **Chain IDs**: Configurable via `ari.blockchain.tr-l1-chain-id` / `ari.blockchain.eu-l1-chain-id` (defaults: 99999/99998 for local)

---

## Documentation Map

| Document | Purpose |
|----------|---------|
| `ARCHITECTURE.md` | Original system design and implementation phases |
| `PROGRESS.md` | Current implementation status and recent work |
| `CLAUDE.md` | AI agent guidance (this file) |
| `scripts/README.md` | AWS testing infrastructure |
| `docs/avalanche-docs.md` | Avalanche L1 deployment guide (legacy) |
| `docs/FUJI_L1_SETUP_GUIDE.md` | **Current** Fuji L1 setup guide (Platform CLI + Builder Console) |
| `docs/compliance.md` | TR/EU regulatory requirements |
| `docs/LOCAL_TESTING_GUIDE.md` | Step-by-step local testing guide |
| `docs/adr/001-multi-region-data-residency.md` | Production multi-region architecture (BDDK/GDPR) |

---

## Testing Guide

### Backend Tests
```bash
# Core Banking (any Java version)
./gradlew :core-banking:test

# Blockchain Service (Java 21 required!)
./gradlew --stop  # Stop any daemon with wrong Java
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :blockchain-service:test --no-daemon
```

### Contract Tests
```bash
cd contracts
npx hardhat test                    # All 183 tests
npx hardhat test test/AriTokenHome.test.ts  # Specific test file
npx hardhat coverage               # Coverage report
```

### E2E Tests
```bash
# Requires core-banking + blockchain-service running
./scripts/e2e-bridge-test.sh 1000  # Test 1000 TRY TR→EU transfer
```

### Fuji Testnet Testing
Running against real Fuji L1s requires specific environment variables:

```bash
# Terminal 1: Core Banking
export ARI_JWT_SECRET="any-long-secret-for-dev"
export ARI_INTERNAL_API_KEY="dev-internal-api-key-never-use-in-production"
export ARI_TR_CHAIN_ID=1279
export ARI_EU_CHAIN_ID=1832
./gradlew :core-banking:bootRun

# Terminal 2: Blockchain Service
source .env.fuji   # Loads DEPLOYER_PRIVATE_KEY + chain config
export SPRING_PROFILES_ACTIVE=fuji
./gradlew :blockchain-service:bootRun

# Terminal 3: Web App
cd web && npm run dev
```

**Key notes:**
- `.env.fuji` has the deployer key (`ari-deploy`, address `0xe9ce1Cd8...`) — this key is gitignored
- Default chain IDs (99999/99998) are for **local dev only**. Fuji uses 1279 (TR) / 1832 (EU)
- `ARI_INTERNAL_API_KEY` is required for blockchain-service → core-banking settlement callbacks

---

## GitHub Integration

Claude Code has access to GitHub via MCP plugin:
- **Searching code**: `mcp__plugin_github_github__search_code`
- **Creating issues**: `mcp__plugin_github_github__issue_write`
- **PR operations**: `mcp__plugin_github_github__create_pull_request`
- **Viewing commits**: `mcp__plugin_github_github__list_commits`

---

## Session Workflow Recommendation

1. **Start**: Read PROGRESS.md, CLAUDE.md, MEMORY.md
2. **Plan**: Create prioritized task list if multiple items
3. **Implement**: Make changes, verify incrementally
4. **Test**: Run relevant tests before committing
5. **Commit**: Descriptive commit messages (no Co-Authored-By)
6. **Update**: Update PROGRESS.md with completed work
7. **Push**: Push changes to remote
8. **Document**: Add learnings to MEMORY.md if applicable
