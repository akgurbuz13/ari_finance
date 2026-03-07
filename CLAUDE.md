# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ARI is a regulated fintech platform (Turkey + EU) with a Kotlin/Spring Boot modular monolith backend, a separate blockchain settlement service, customer web app, admin console, Flutter mobile app, and Solidity smart contracts on Avalanche L1. (Codebase still uses `Ova` prefix in class/file names from before the Phase 0 rebrand.)

**Repository**: https://github.com/akgurbuz13/ova_finance
**Brand name**: ARI (uppercase, no dot, no period)
**Logo CSS**: `.ova-logo` in `web/app/globals.css` must use `text-transform: uppercase`. NEVER set it to lowercase.

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

## Implementation Status (as of 2026-03-07)

> **Full details in [PROGRESS.md](./PROGRESS.md)**

| Component | Completion | Status |
|-----------|------------|--------|
| Core Banking Backend | 92% | ✅ Production-Ready |
| Blockchain Service | 97% | ✅ Production-Ready |
| Smart Contracts | 92% | ✅ Tested (135 tests), deployed on Fuji |
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
- Rebrand Ova → ARI, Fuji L1 deployment, contract deployment, backend integration, demo readiness
- E2E mint verified on Fuji TR L1
- CI fixes: Redis port, config prefix, JVM memory (2026-03-03)
- Cross-border region logic fix: `regionForCurrency()` helper (2026-03-03)

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
cd contracts && npx hardhat test         # 135 tests
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
| Mint/Burn | `OutboxPollerService.kt` → `MintService.kt`/`BurnService.kt` → `OvaStablecoin.sol` |
| ICTT Bridge | `IcttBridgeService.kt` → `OvaTokenHome.sol` ↔ Teleporter ↔ `OvaTokenRemote.sol` |
| Burn/Mint Bridge | `OutboxPollerService.kt` → `AriBurnMintBridgeContract.kt` → `AriBurnMintBridge.sol` |

### Module Boundaries

Core Banking modules communicate via:
- **Sync**: Direct service calls within same module
- **Async**: Outbox events for cross-module communication
- **Never**: Direct API calls between modules

Blockchain Service communicates via:
- **Inbound**: Reads `shared.outbox_events` table
- **Outbound**: REST callbacks to `/api/internal/settlement-confirmed`

### Database Schemas

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
| `docs/avalanche-docs.md` | Avalanche L1 deployment guide |
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
npx hardhat test                    # All 115 tests
npx hardhat test test/OvaTokenHome.test.ts  # Specific test file
npx hardhat coverage               # Coverage report
```

### E2E Tests
```bash
# Requires core-banking + blockchain-service running
./scripts/e2e-bridge-test.sh 1000  # Test 1000 TRY TR→EU transfer
```

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
5. **Commit**: Descriptive commit messages with Co-Author
6. **Update**: Update PROGRESS.md with completed work
7. **Push**: Push changes to remote
8. **Document**: Add learnings to MEMORY.md if applicable
