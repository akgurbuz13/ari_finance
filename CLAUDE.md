# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ova is a regulated fintech platform (Turkey + EU) with a Kotlin/Spring Boot modular monolith backend, a separate blockchain settlement service, customer web app, admin console, Flutter mobile app, and Solidity smart contracts on Avalanche L1.

**Repository**: https://github.com/akgurbuz13/ova_finance

## Implementation Status (as of Feb 2026)

### Completed (Phases 1-3 + Partial 4-5)
- **Core Banking**: 85-90% complete
  - Identity module: Auth, 2FA, KYC flows fully implemented
  - Ledger module: Double-entry engine production-ready with idempotency
  - Payments module: Domestic P2P and cross-border sagas complete
  - Compliance module: Sanctions screening with fuzzy matching, MASAK reporting
  - FX module: Quote engine with 30s TTL, spread calculation
  - Rails module: FAST/EFT/SEPA adapters (stubs - need real API integration)
  - Notification module: SMS/Email/Push (stubs)

- **Blockchain Service**: 70% complete (has bugs to fix)
  - Settlement services: Mint/Burn/Transfer implemented
  - ICTT Bridge: Architecture complete, method naming issues
  - Gasless relayer: Basic implementation, ERC-2771 encoding needs fix
  - Reconciliation: Daily on-chain vs off-chain checks

- **Smart Contracts**: 60% complete
  - OvaStablecoin + OvaStablecoinUpgradeable: Production-ready
  - Bridge contracts: TokenHome/TokenRemote implemented but deployment script incomplete
  - Governance: ValidatorManager + Timelock ready

- **Web App**: 95% complete (just needs `npm install`)
  - All auth flows, dashboard, transfers, accounts, history, settings

- **Admin Console**: 90% complete
  - Users, KYC, Compliance cases, Audit log, Settings

- **Infrastructure**: 80% complete
  - Terraform modules for AWS validators ready
  - K8s manifests with Kustomize overlays
  - Secrets management with External Secrets

### Known Issues to Fix
1. **Blockchain Service**: Method name mismatches (`initiateCrossChainTransfer` vs `initiateBridgeTransfer`)
2. **Blockchain Service**: Missing `metadata` column in `blockchain.transactions` table
3. **Blockchain Service**: Internal API key header missing in callbacks
4. **Smart Contracts**: Deploy script doesn't deploy TokenHome/TokenRemote
5. **Core Banking**: Missing password reset endpoints in AuthController

## Build & Run Commands

### Prerequisites
- Docker Compose for local PostgreSQL 16 + Redis 7: `docker compose up -d`
- JDK 21, Node.js 20, Flutter SDK 3.2+

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
./gradlew :blockchain-service:test       # Run blockchain-service tests
./gradlew bootJar                        # Build JARs for both modules
./gradlew ktlintCheck                    # Lint check
```
Test framework: JUnit 5 + Kotest assertions + MockK + Testcontainers (PostgreSQL/Redis).

### Web App (Next.js)
```bash
cd web && npm install                    # Install dependencies (required first time)
cd web && npm run dev                    # Dev server on :3000
cd web && npm run build                  # Production build
cd web && npm run lint                   # Lint
```

### Admin Console (Next.js)
```bash
cd admin-console && npm install          # Install dependencies
cd admin-console && npm run dev          # Dev server on :3001
cd admin-console && npm run build
cd admin-console && npm run lint
```

### Mobile (Flutter)
```bash
cd mobile && flutter pub get             # Install dependencies
cd mobile && flutter run                 # Run on connected device/emulator
cd mobile && flutter test                # Run tests
```

### Smart Contracts (Solidity 0.8.24 / Hardhat)
```bash
cd contracts && npm install              # Install dependencies
cd contracts && npx hardhat compile
cd contracts && npx hardhat test
cd contracts && npx hardhat run scripts/deploy.ts --network testnet
```

## GitHub Integration

Claude Code has access to GitHub via MCP plugin. Use these capabilities:

### Common Operations
```bash
# View repository info
gh repo view akgurbuz13/ova_finance

# Create issues for bugs found
gh issue create --title "Bug: ..." --body "..."

# List open PRs
gh pr list

# Create PR after changes
gh pr create --title "..." --body "..."
```

### When to Use GitHub Tools
- **Searching code**: Use `mcp__plugin_github_github__search_code` for cross-repo searches
- **Creating issues**: Track bugs and improvements with `mcp__plugin_github_github__issue_write`
- **PR operations**: Create, update, review PRs with GitHub tools
- **Viewing commits**: Use `mcp__plugin_github_github__list_commits` for history

## Architecture

### Core Banking — Modular Monolith (Spring Modulith)

The `core-banking` module is a single Spring Boot app with enforced module boundaries via `@Modulithic`. All modules live under `core-banking/src/main/kotlin/com/ova/platform/`:

| Module | Purpose | Status |
|--------|---------|--------|
| `identity` | Auth (JWT + TOTP 2FA), user CRUD, KYC verification | Complete |
| `ledger` | Double-entry accounting engine, accounts, balances | Complete |
| `payments` | Payment orchestration (domestic P2P, cross-border saga) | Complete |
| `rails` | Banking rail adapters (FAST/EFT for Turkey, SEPA for EU) | Stubs |
| `fx` | FX rate provider, quote engine, currency conversion | Complete (hardcoded rates) |
| `compliance` | Sanctions screening, transaction monitoring, case management | Complete |
| `notification` | SMS/Email/Push notification stubs | Stubs |
| `shared` | Cross-cutting: security, events, config, exceptions, value objects | Complete |

Each module follows the same internal layout:
- `api/` — REST controllers (the public surface, prefixed `/api/v1/`)
- `internal/` — `model/`, `repository/`, `service/` (not accessible by other modules)
- `event/` — Domain events published through the outbox

### Double-Entry Ledger (Critical Path)

`LedgerService.postEntries()` is the heart of the system. Every financial operation (P2P, FX, cross-border) ultimately calls this method. Rules enforced:
1. Sum of debits == sum of credits per currency (within a single `@Transactional`)
2. Non-negative balance constraint on `user_wallet` accounts
3. Idempotency key prevents double-posting
4. Balance snapshots stored in `balance_after` on each entry

Account types: `user_wallet`, `system_float`, `fee_revenue`, `safeguarding`.

### Event Flow (Outbox Pattern)

Modules do NOT call each other's APIs for async workflows. Instead:
1. Service writes a domain event to `shared.outbox_events` table (same DB transaction as the business operation) via `OutboxPublisher`
2. `OutboxPoller` (1s interval) reads unpublished events with `SELECT FOR UPDATE SKIP LOCKED`
3. Events dispatched via Spring `ApplicationEventPublisher`
4. Blockchain service has its own `OutboxPollerService` that reads events like `MintRequested`/`BurnRequested` and settles on-chain

### Payment Orchestration

`DomesticTransferService` and `CrossBorderTransferService` follow a status-machine saga:
`INITIATED → COMPLIANCE_CHECK → PROCESSING → SETTLING → COMPLETED` (or `FAILED` at any step).
Each transition is recorded in `payments.payment_status_history`. Cross-border adds FX quote validation and multi-leg ledger postings through system float accounts.

### Blockchain Service (Separate Deployable)

`blockchain-service` is a standalone Spring Boot app that handles Avalanche L1 chain interaction: mint/burn stablecoins, ICTT bridge cross-chain transfers, gasless relay (ERC-2771), chain event listening, and daily reconciliation (on-chain vs off-chain balances). Communicates with core-banking via REST callbacks and the shared outbox table.

### Database

PostgreSQL with 4 schemas: `identity`, `ledger`, `payments`, `shared`. Migrations in `core-banking/src/main/resources/db/migration/` (Flyway, V001-V012). All repositories use `JdbcTemplate` directly (not JPA). The `shared.outbox_events` and `shared.audit_log` tables are used across all modules.

### Security

JWT-based stateless auth. Access tokens (15min) + refresh tokens (7d). TOTP 2FA optional. Routes: `/api/v1/auth/**` is public, `/api/v1/admin/**` requires ADMIN role, `/api/internal/**` requires SYSTEM role, everything else requires authentication. BCrypt password hashing.

### Multi-Region

`ova.region` config (`TR` or `EU`) determines which rails adapters load and which blockchain L1 chains to use. Same codebase deployed to both AWS (eu-central-1) and Azure (Turkey Central) with Kustomize overlays.

### Frontend Architecture

- **Web** (`web/`): Next.js 14 + Tailwind CSS, black/white design. Axios client with JWT interceptor and auto-refresh.
- **Admin** (`admin-console/`): Same stack, runs on port 3001. RBAC-protected.
- **Mobile** (`mobile/`): Flutter with Riverpod state management, GoRouter navigation, Dio HTTP client, flutter_secure_storage for tokens.
- All three share the same API contract from the core-banking backend.

### Infrastructure

- Dockerfiles for all 4 services in their respective directories
- K8s manifests: `infra/k8s/base/` (Kustomize) with `overlays/eu/` and `overlays/tr/`
- Terraform: `infra/terraform/modules/avalanche-validators/` for L1 validators on AWS
- CI: `.github/workflows/build.yml` (lint, test, build on PR), `deploy.yml` (tag-triggered deploy to both regions)

### Avalanche L1 Validators

For testing with 2 validators on AWS Europe:
```hcl
module "avalanche_validators" {
  source              = "./infra/terraform/modules/avalanche-validators"
  environment         = "dev"
  region              = "EU"
  l1_name             = "ova-eu"
  l1_chain_id         = 99998
  validator_count     = 2
  validator_instance_type = "m5.xlarge"
}
```

Prerequisites:
1. Generate staking keys and upload to AWS Secrets Manager
2. Deploy ValidatorManager contract on C-Chain
3. Configure VPC and subnets

## Key Conventions

- All payment and ledger endpoints require an `Idempotency-Key` header
- Repositories use raw `JdbcTemplate` SQL, not Spring Data JPA or jOOQ generated code
- Domain events extend `DomainEvent` abstract class and are published through `OutboxPublisher`, never directly
- Supported currencies: TRY and EUR only
- All monetary amounts use `BigDecimal` with `NUMERIC(20,8)` in the database
- Flyway migration naming: `V{NNN}__{description}.sql`

## Testing Guide

### Web App Test Flow
1. Visit http://localhost:3000
2. Sign up with email/phone/password and region (TR or EU)
3. Log in with credentials
4. Create TRY and EUR accounts
5. Perform domestic transfer (same currency)
6. Request FX quote and perform cross-border transfer
7. View transaction history
8. Update settings and 2FA

### Admin Console Test Flow
1. Visit http://localhost:3001
2. Log in with admin credentials
3. View dashboard metrics
4. Manage users (suspend/freeze)
5. Review KYC verifications
6. Handle compliance cases
7. View audit logs

## Documentation

- `ARCHITECTURE.md` - Full system design and implementation phases
- `docs/avalanche-docs.md` - Avalanche L1 deployment and operations
- `docs/compliance.md` - TR/EU regulatory requirements
