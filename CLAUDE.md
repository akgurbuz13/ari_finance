# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ova is a regulated fintech platform (Turkey + EU) with a Kotlin/Spring Boot modular monolith backend, a separate blockchain settlement service, customer web app, admin console, Flutter mobile app, and Solidity smart contracts on Avalanche L1.

## Build & Run Commands

### Prerequisites
- Docker Compose for local PostgreSQL 16 + Redis 7: `docker compose up -d`
- JDK 21, Node.js 20, Flutter SDK 3.2+

### Backend (Kotlin/Spring Boot)
```
./gradlew :core-banking:bootRun          # Start core-banking on :8080
./gradlew :blockchain-service:bootRun    # Start blockchain-service on :8081
./gradlew :core-banking:test             # Run core-banking tests
./gradlew :blockchain-service:test       # Run blockchain-service tests
./gradlew bootJar                        # Build JARs for both modules
./gradlew ktlintCheck                    # Lint check
```
Test framework: JUnit 5 + Kotest assertions + MockK + Testcontainers (PostgreSQL/Redis).

### Web App (Next.js)
```
cd web && npm run dev                    # Dev server on :3000
cd web && npm run build                  # Production build
cd web && npm run lint                   # Lint
```

### Admin Console (Next.js)
```
cd admin-console && npm run dev          # Dev server on :3001
cd admin-console && npm run build
cd admin-console && npm run lint
```

### Mobile (Flutter)
```
cd mobile && flutter run                 # Run on connected device/emulator
cd mobile && flutter test                # Run tests
```

### Smart Contracts (Solidity 0.8.24 / Hardhat)
```
cd contracts && npx hardhat compile
cd contracts && npx hardhat test
cd contracts && npx hardhat run scripts/deploy.ts --network testnet
```

## Architecture

### Core Banking — Modular Monolith (Spring Modulith)

The `core-banking` module is a single Spring Boot app with enforced module boundaries via `@Modulithic`. All modules live under `core-banking/src/main/kotlin/com/ova/platform/`:

| Module | Purpose |
|--------|---------|
| `identity` | Auth (JWT + TOTP 2FA), user CRUD, KYC verification |
| `ledger` | Double-entry accounting engine, accounts, balances |
| `payments` | Payment orchestration (domestic P2P, cross-border saga) |
| `rails` | Banking rail adapters (FAST/EFT for Turkey, SEPA for EU) |
| `fx` | FX rate provider, quote engine, currency conversion |
| `compliance` | Sanctions screening, transaction monitoring, case management |
| `notification` | SMS/Email/Push notification stubs |
| `shared` | Cross-cutting: security, events, config, exceptions, value objects |

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

PostgreSQL with 4 schemas: `identity`, `ledger`, `payments`, `shared`. Migrations in `core-banking/src/main/resources/db/migration/` (Flyway, V001-V005). All repositories use `JdbcTemplate` directly (not JPA). The `shared.outbox_events` and `shared.audit_log` tables are used across all modules.

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
- Terraform: `infra/terraform/modules/aws-eu/` and `azure-tr/`, environments in `environments/{dev,staging,prod}/`
- CI: `.github/workflows/build.yml` (lint, test, build on PR), `deploy.yml` (tag-triggered deploy to both regions)

## Key Conventions

- All payment and ledger endpoints require an `Idempotency-Key` header
- Repositories use raw `JdbcTemplate` SQL, not Spring Data JPA or jOOQ generated code
- Domain events extend `DomainEvent` abstract class and are published through `OutboxPublisher`, never directly
- Supported currencies: TRY and EUR only
- All monetary amounts use `BigDecimal` with `NUMERIC(20,8)` in the database
- Flyway migration naming: `V{NNN}__{description}.sql`
