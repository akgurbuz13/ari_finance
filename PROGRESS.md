# ARI Fintech — Implementation Progress

> **Last Updated:** 2026-03-09
> **Current Phase:** Fuji L1 Redeployment + Competition Submission
> **Overall Completion:** ~99% of MVP

This document tracks implementation progress against the [ARCHITECTURE.md](./ARCHITECTURE.md) plan. Update this document when completing significant milestones.

---

## Fresh Fuji L1 Deployment (2026-03-09)

Complete from-scratch creation and deployment of both Avalanche L1s on Fuji testnet using Platform CLI + Builder Console. All 13 contracts deployed and cross-registered.

**L1 Creation:**
- Created ariTR subnet (`2Sw7W5coLCB4EZRADRyfTCuPBF5QqxMxj3jL8cUWPpCdso1MGX`) and chain (ID 1279) via Platform CLI
- Created ariEU subnet (`5KvzdVWjkZu6YuFrWVKhpFq2ZyGqQgHEozgvJFhzJRdBfba6M`) and chain (ID 1832) via Platform CLI
- Managed validator nodes provisioned via Builder Console
- ValidatorManager deployed and L1 conversion completed
- ICM/Teleporter set up with 2 managed relayers (bidirectional)

**Contract Deployment (all 13 contracts across both chains):**
- AriStablecoinUpgradeable (ariTRY on TR, ariEUR on EU) via UUPS proxy
- Cross-currency stablecoins (ariEUR on TR, ariTRY on EU)
- AriTokenHome + AriTokenRemote on both chains (ICTT bridge)
- AriBridgeAdapter on both chains
- AriBurnMintBridge ×2 on each chain (TRY bridge + EUR bridge)
- AriVehicleNFT + AriVehicleEscrow on TR L1
- AriTimelock + KycAllowList on both chains

**Cross-Registration:**
- All 4 burn-mint bridges cross-registered as partners via `cross-register-bridges.ts`

**Configuration Updates:**
- Updated `hardhat.config.ts` with new RPC URLs
- Updated `application-fuji.yml` with all contract addresses, chain config, blockchain IDs
- Updated `application.yml` default RPC URLs
- Created deployment records: `ari-tr-testnet.json`, `ari-eu-testnet.json`, `vehicle-escrow-1279.json`
- Updated `FUJI_L1_SETUP_GUIDE.md` with all deployed values

**Verification:**
- 183 Solidity tests passing
- Both Kotlin modules compile clean (zero warnings)
- ariTRY `name()` returns "ARI Turkish Lira" on TR L1
- ariEUR `name()` returns "ARI Euro" on EU L1

---

## Vehicle Securitization & Smart Contract Escrow (2026-03-07)

Full-stack vehicle NFT + escrow system replacing Turkey's broken notary process. On-chain NFT ownership + escrow ensures neither party can cheat in vehicle sales.

**Smart Contracts (Phase 1):**
- `AriVehicleNFT.sol` — ERC-721 with transfer restrictions (escrow/admin only), KYC allowlist, VIN uniqueness
- `AriVehicleEscrow.sol` — On-chain escrow state machine: dual confirmation, 50 TRY fee, atomic swap, cancel with burn
- `deploy-vehicle-escrow.ts` — Deployment script with role setup
- 48 new Solidity tests (183 total), all passing

**Database & Models (Phase 2):**
- `V021__vehicle_escrow_tables.sql` — `vehicle_registrations` + `vehicle_escrows` tables
- `VehicleRegistration`, `VehicleEscrow` entities with `VehicleStatus`, `EscrowState` enums
- JdbcTemplate repositories following existing patterns
- `VEHICLE_ESCROW_HOLDING` account type for transit pattern

**Core-Banking Services (Phase 3):**
- `VehicleRegistrationService.kt` — Register vehicle, hash VIN/plate, publish mint event
- `VehicleEscrowService.kt` — Full lifecycle: create, join, fund, confirm, cancel + settlement callbacks
- `VehicleController.kt` — REST API (9 endpoints) for all vehicle/escrow operations
- 5 new outbox event types (`VehicleMintRequested`, `EscrowSetup/Funding/Confirmation/Cancellation`)
- Vehicle settlement callback endpoint in `InternalSettlementController`

**Blockchain Service (Phase 4):**
- `AriVehicleNFTContract.kt` — Web3j wrapper (mint, approve, ownerOf, allowlist)
- `AriVehicleEscrowContract.kt` — Web3j wrapper (create, fund, confirm, cancel)
- `ContractFactory` extended with `getVehicleNFT()`, `getVehicleEscrow()`
- `OutboxPollerService` extended with 5 vehicle event handlers
- Vehicle settlement callback to core-banking

**Web App (Phase 5):**
- My Vehicles page — list vehicles with status badges
- Register Vehicle page — form with VIN, plate, make/model/year
- Vehicle Detail page — on-chain proof, mint status, sell button
- Create Escrow page — set price, get shareable link/code
- Join Escrow page — lookup by share code, review deal info
- Escrow Detail page — progress timeline, action buttons, on-chain proof links
- Sidebar navigation updated with Vehicles item

**Architecture:**
- NFT = source of truth for vehicle ownership (on-chain)
- Escrow = atomic swap (ariTRY payment + NFT ownership in single transaction)
- Transit account pattern: buyer funds → VEHICLE_ESCROW_HOLDING → seller/fee on completion
- Outbox pattern for all blockchain interactions (same as existing payment flows)

---

## Avalanche Hackathon MVP (2026-03-03)

All 6 phases of the hackathon sprint are complete:

| Phase | Description | Status |
|-------|-------------|--------|
| Phase 0 | Rebrand from Ova to ARI | ✅ Complete |
| Phase 1 | Testnet Infrastructure (Fuji L1s) | ✅ Complete |
| Phase 2 | Contract Deployment & Bridge Setup | ✅ Complete |
| Phase 3 | Backend Integration & Critical Fixes | ✅ Complete |
| Phase 4 | Demo Readiness (scripts, docs, web app) | ✅ Complete |
| Phase 5 | Final Verification & Submission | ✅ Complete |

**Verification results:**
- Solidity: 135/135 tests passing (115 original + 20 AriBurnMintBridge)
- Blockchain-service: all tests passing (Java 21)
- Core-banking: compiles clean (zero warnings)
- Web app: builds successfully
- E2E mint verified on Fuji TR L1 (tx 0x152bbe77...)

### Same-Currency Cross-Border Transfers (2026-03-07)

Full-stack implementation of same-currency cross-border transfers (e.g. TRY/TR → TRY/EU) using a lightweight `AriBurnMintBridge` contract with Avalanche Teleporter. Burns native ariTRY on source chain, Teleporter relays, mints native ariTRY on destination chain — no wrapped tokens.

**Architecture:**
- Blockchain IS the settlement rail — receiver credited only after on-chain confirmation
- Transit account pattern: sender debited to `CROSS_BORDER_TRANSIT`, receiver credited after bridge confirms
- Single bridge call replaces separate burn + mint (Teleporter handles cross-chain delivery)

**Smart Contracts (P2-P3):**
- `AriBurnMintBridge.sol` (~161 lines) — burn/mint bridge with Teleporter, replay protection, partner registration
- `deploy-burn-mint-bridge.ts` — deployment script for both chains
- 20 new tests (deployment, partner registration, burn+bridge, receive+mint, replay protection, E2E cycle)
- Bridge needs `MINTER_ROLE` + `DEFAULT_ADMIN_ROLE` on stablecoin for mint/burn/allowlist

**Database (P1):**
- Migration `V020__cross_border_same_currency.sql` — adds `region` column to accounts, `CROSS_BORDER_TRANSIT` account type, chain tracking on payment orders
- Unique constraint updated: `UNIQUE(user_id, currency, account_type, region)` — enables same-currency accounts in different regions

**Backend (P4-P6):**
- `AriBurnMintBridgeContract.kt` — Web3j wrapper for bridge contract
- `SameCurrencyCrossBorderService.kt` — saga orchestrator (validate → compliance → debit sender to transit → publish outbox event)
- `CrossBorderBurnMintRequested` event + handler in `OutboxPollerService`
- Settlement callback in `InternalSettlementController` creates receiver credit ledger entry
- Multi-chain config: cross-currency stablecoin addresses, burn-mint bridge addresses per chain
- `ContractFactory` extended with `getStablecoin(chainId, currency)` and `getBurnMintBridge(chainId)`
- `MintService`/`BurnService` support explicit `chainId` parameter

**Web App (P7):**
- Same-currency toggle checkbox on cross-border transfer form
- Blockchain settlement progress visualization (burn → Teleporter relay → mint)
- Account cards show region label ("Turkey" / "Europe")
- `region` field added to Account type

**Code Quality:**
- Replaced hand-rolled `hexStringToByteArray` with web3j `Numeric.hexStringToByteArray`
- Consolidated `getOrCreateSystemAccount` methods (removed `WithRegion` variant)
- Eliminated redundant DB re-fetches in settlement handlers
- Fixed pre-existing ReconciliationService warnings (unused var, unnecessary safe call)
- Zero compiler warnings across both backend modules

**Commits:** `e686a89` (P1-P3), `cab581f` (P4-P6), `8a39953` (P7-P8), `3339df6` (simplify)

### MVP Production Deployment (2026-03-04)

Production deployment infrastructure for `arifinance.co` using free-tier services:

**Code Changes:**
- Added password reset endpoints (`POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/reset-password`)
- Migration `V019__password_reset_tokens.sql` for token storage
- `PasswordResetTokenRepository` with auto-invalidation of previous tokens
- Updated `AuthService` with `requestPasswordReset()` and `resetPassword()` methods
- Updated `SecurityConfig` to permit reset endpoints
- Created `application-prod.yml` for core-banking (Neon 5-conn, Upstash TLS, sanctions disabled)
- Created `application-prod.yml` for blockchain-service (matching prod config)
- Updated Dockerfiles for root-context builds and `JAVA_OPTS` support
- Created `render.yaml` deployment blueprint for Render.com
- Created `deploy-fuji-l1s.ts` simplified contract deployment for Builder Console nodes
- Created `DEPLOYMENT.md` comprehensive step-by-step deployment guide

**Target Architecture:** Vercel (web + admin) + Render (backend) + Neon (PostgreSQL) + Upstash (Redis) + Builder Console (Avalanche L1s) = $0/month

**Branch:** `deploy/mvp-live` (separate from `main`)

### Bug Investigation & Fixes (2026-03-03)

Pre-demo investigation and fixes:

**CI Backend Test Fixes:**
- Fixed Redis port mismatch: `BaseIntegrationTest` hardcoded port `16379`, CI Redis runs on `6379`. Now reads from `SPRING_DATA_REDIS_PORT` env var with `16379` fallback for local dev.
- Fixed config prefix: `application-test.yml` used dead old `ova:` prefix instead of `ari:`. Updated to match `application.yml`.
- Added JVM memory limit: `-Xmx1536m` in `GRADLE_OPTS` to prevent OOM in CI.
- Added `SPRING_DATA_REDIS_PORT` env var to CI workflow.

**Cross-Border Transfer Fix:**
- Fixed hardcoded `region = "TR"` / `region = "EU"` in `CrossBorderTransferService`. Now derives region from currency via `regionForCurrency()` helper. EUR→TRY direction was completely broken before this fix.

**Documentation:**
- Created `docs/adr/001-multi-region-data-residency.md` — production multi-region architecture (BDDK/GDPR compliance).
- Added TODO comment in `CrossBorderTransferService` for multi-region ledger postings.

---

## Quick Status

| Component | Status | Completion | Notes |
|-----------|--------|------------|-------|
| Core Banking Backend | ✅ Production-Ready | 92% | All modules + same-ccy cross-border |
| Blockchain Service | ✅ Production-Ready | 97% | Mint/Burn/ICTT Bridge/BurnMint Bridge |
| Smart Contracts | ✅ Production-Ready | 95% | 183 tests, 13 contracts deployed on Fuji |
| Web App | ✅ Production-Ready | 95% | Full user flows |
| Admin Console | ✅ Production-Ready | 90% | All admin features |
| Mobile App | 🔶 Needs Review | 70% | Core flows implemented |
| Infrastructure | ✅ AWS Testing Ready | 85% | 2-validator setup complete |
| Payment Rails | ⚠️ Stubs Only | 30% | Awaiting bank partnerships |

---

## Phase Completion Status

### Phase 1: Foundation ✅ COMPLETE
**Original Timeline:** Weeks 1-6

| Deliverable | Status | Notes |
|-------------|--------|-------|
| Project scaffolding | ✅ | Gradle multi-module, Docker Compose |
| PostgreSQL schema V001-V005 | ✅ | Extended to V012 |
| Spring Security + JWT auth | ✅ | Access (15m) + Refresh (7d) tokens |
| 2FA setup (TOTP) | ✅ | Optional per user |
| User profile CRUD | ✅ | Full CRUD + admin management |
| Account creation (TRY/EUR) | ✅ | With blockchain wallet assignment |
| Balance query endpoint | ✅ | Real-time via ledger |
| OpenAPI documentation | ✅ | springdoc auto-generation |
| CI pipeline | ✅ | GitHub Actions build + test |
| Terraform base setup | ✅ | AWS eu-central-1 |

### Phase 2: Ledger & Domestic Payments ✅ COMPLETE
**Original Timeline:** Weeks 7-12

| Deliverable | Status | Notes |
|-------------|--------|-------|
| Double-entry posting engine | ✅ | Idempotent, balanced, auditable |
| Transaction history API | ✅ | Paginated, filterable |
| Domestic P2P transfers | ✅ | TRY→TRY, EUR→EUR |
| Outbox event publisher | ✅ | With poller service |
| Basic compliance checks | ✅ | Amount limits, status validation |
| Notification module | ✅ | Stubs for SMS/Email/Push |
| Admin console foundation | ✅ | Next.js with auth |

### Phase 3: KYC & Compliance ✅ COMPLETE
**Original Timeline:** Weeks 13-18

| Deliverable | Status | Notes |
|-------------|--------|-------|
| eKYC provider integration | 🔶 | Stub implementation (Veriff/Onfido ready) |
| KYC status flow | ✅ | Full state machine |
| Sanctions screening | ✅ | **Fuzzy matching** with OFAC/UN/EU lists |
| Transaction monitoring | ✅ | Rule-based with thresholds |
| Case management | ✅ | Admin workflow |
| Audit log viewer | ✅ | Full history |
| Freeze/unfreeze | ✅ | Account + user level |
| **MASAK Reporting** | ✅ | Turkish AML authority reports |

### Phase 4: Payment Rail Integration 🔶 PARTIAL
**Original Timeline:** Weeks 19-26

| Deliverable | Status | Notes |
|-------------|--------|-------|
| SEPA integration (EU) | 🔶 | **Stub adapter** — awaiting CENTROlink access |
| IBAN generation | ✅ | Algorithm implemented |
| FAST/EFT integration (TR) | 🔶 | **Stub adapters** — awaiting bank partnership |
| Deposit flow | ✅ | Ledger integration complete |
| Withdrawal flow | ✅ | Ledger integration complete |
| Reconciliation | ✅ | Daily job implemented |
| Webhook handlers | ✅ | Ready for real rails |

**Note:** Rail adapters are intentionally stubs. The orchestration logic is production-ready; only external API calls need real integration.

### Phase 5: Blockchain & Cross-Border ✅ COMPLETE
**Original Timeline:** Weeks 27-36

| Deliverable | Status | Date | Notes |
|-------------|--------|------|-------|
| AriStablecoin contract | ✅ | 2026-02-03 | ERC-20 + mint/burn/freeze/allowlist |
| AriStablecoinUpgradeable | ✅ | 2026-02-03 | UUPS proxy pattern |
| Deploy TR L1 + EU L1 | ✅ | 2026-03-09 | Fresh Fuji L1s via Platform CLI + Builder Console |
| Blockchain service: mint/burn | ✅ | 2026-02-04 | Full implementation with error handling |
| Custodial wallet management | ✅ | 2026-02-04 | HD wallet derivation |
| Gasless relayer | ✅ | 2026-02-04 | ERC-2771 meta-transactions |
| Chain event listener | ✅ | 2026-02-04 | With retry logic |
| **ICTT bridge integration** | ✅ | 2026-02-05 | TokenHome ↔ TokenRemote |
| **AriTokenHome contract** | ✅ | 2026-02-05 | Lock/release native tokens |
| **AriTokenRemote contract** | ✅ | 2026-02-05 | Mint/burn wrapped tokens |
| **AriBridgeAdapter contract** | ✅ | 2026-02-05 | Bridge orchestration |
| FX quote engine | ✅ | 2026-02-03 | 30s TTL, spread calculation |
| Cross-border orchestrator | ✅ | 2026-02-04 | Full saga pattern |
| Daily reconciliation | ✅ | 2026-02-04 | On-chain vs off-chain |
| **AriBurnMintBridge contract** | ✅ | 2026-03-07 | Same-ccy burn/mint via Teleporter |
| **Same-ccy cross-border service** | ✅ | 2026-03-07 | Saga orchestrator + transit pattern |
| **Region-based accounts** | ✅ | 2026-03-07 | V020 migration, region dimension |
| **Test suites** | ✅ | 2026-03-07 | 135 Solidity + 12 Kotlin tests |
| **AWS 2-validator infra** | ✅ | 2026-02-05 | Terraform + bootstrap scripts |

### Phase 6: Web App, Mobile App & Launch Prep 🔶 PARTIAL
**Original Timeline:** Weeks 37-46

| Deliverable | Status | Notes |
|-------------|--------|-------|
| Web landing page | ✅ | Black/white design |
| Web auth flows | ✅ | Login, signup, 2FA |
| Web dashboard | ✅ | Balances, activity |
| Web transfer flows | ✅ | Domestic + cross-border |
| Web account details | ✅ | Statements, history |
| Web KYC onboarding | ✅ | Provider widget ready |
| Web settings | ✅ | Profile, security |
| Mobile auth flows | ✅ | Flutter implemented |
| Mobile dashboard | ✅ | Implemented |
| Mobile transfers | ✅ | Implemented |
| Mobile settings | ✅ | Implemented |
| Push notifications | 🔶 | FCM/APNs stubs |
| Biometric auth | 🔶 | Partial |
| E2E test suite | ✅ | Bash script ready |
| Security audit | ⏳ | Pending external |
| Smart contract audit | ⏳ | Pending external |
| Production deployment | 🔶 | Free-tier deployment ready (deploy/mvp-live branch) |

---

## Recent Session Work (2026-02-15)

### Context
A security review identified 22 findings across the backend. Another AI agent attempted fixes for ~12 of them, but 3 were incorrect/incomplete. This session corrects those mistakes, adds RBAC support, fixes infrastructure gaps, and updates documentation.

### Work Completed

#### Phase 1: Fix Incorrect AI Agent Changes
- **ReconciliationService**: Fixed wrong table name (`ledger.ledger_entries` -> `ledger.entries`), wrong columns (`entry_type` -> `direction`, `reference` -> `reference_id`), added missing JOIN to `ledger.transactions`
- **MasakReportingService**: Changed `LEFT JOIN` to `JOIN` for account lookups (4 places) — completed payment orders always have sender/receiver accounts
- **InternalSettlementController**: Fixed metadata type fragility — store `"true"` (String) instead of `true` (Boolean), use `?.toString() == "true"` for JSONB round-trip safety

#### Phase 2: RBAC / Admin Role System
- New migration `V016__add_user_role.sql` — adds `role` column with `DEFAULT 'USER'` and CHECK constraint
- Updated `User` model with `role` field
- Updated `UserRepository`: role in rowMapper, save(), update(), new `updateRole()` method
- Updated `AuthService.generateTokens()`: uses `user.role` instead of hardcoded `"USER"` for JWT roles

#### Phase 3: Infrastructure & K8s Fixes
- Activated `external-secrets.yaml` in kustomization base
- Added managed service `ipBlock` egress rules (PostgreSQL + Redis) to both core-banking and blockchain-service network policies
- Removed mutable `:latest` tag from deploy workflow (only immutable `${{ github.ref_name }}` remains)
- Added single-replica rationale comment to blockchain-service deployment

#### Phase 4: Documentation Cleanup
- Fixed ARCHITECTURE.md: Kong -> nginx ingress, updated rate limiting description (Bucket4j in-app)
- Annotated V008 sanctions seed data as test fixtures only
- Updated PROGRESS.md with this session's work

---

## Recent Session Work (2026-02-05)

### Context
After initial implementation of Phases 1-5, a comprehensive review revealed that the blockchain service layer had significant gaps between the Solidity contracts and Kotlin service wrappers. A focused effort was undertaken to complete all blockchain-related work.

### Work Completed

#### Priority 1: Bug Fixes ✅
- Fixed `OutboxPollerService` method name mismatch (`initiateCrossChainTransfer` → `initiateBridgeTransfer`)
- Added `getBridgeOperatorCredentials()` to `CustodialWalletService`
- Created `V013__fix_blockchain_operations.sql` migration (operation constraints + metadata column)
- Added `metadata` field to `BlockchainTransaction` entity
- Added `findByTransferId()` and `findPendingBridgeTransfers()` repository methods

#### Priority 2: Kotlin Wrappers ✅
- Rewrote `AriBridgeAdapterContract` with correct Solidity method signatures
- Created `AriTokenHomeContract` wrapper (`bridgeTokens`, `registerRemote`, daily limits)
- Created `AriTokenRemoteContract` wrapper (`bridgeBack`, `registerHomeChain`, allowlist)
- Updated `ContractFactory` with new contract factories
- Updated `BlockchainConfig` with TokenHome/TokenRemote addresses

#### Priority 3: Deployment Scripts ✅
- Enhanced `deploy.ts` for TokenHome/TokenRemote deployment
- Created `deploy-dual-chain.ts` for TR + EU L1 orchestration
- Created `configure-bridge.ts` for cross-chain registration

#### Priority 4: Comprehensive Tests ✅
- Created `MockTeleporter.sol` for testing ICTT bridge without network
- Created `AriTokenHome.test.ts` (37 test cases)
- Created `AriTokenRemote.test.ts` (comprehensive coverage)
- Created `AriBridgeAdapter.test.ts` (full integration tests)
- Created `IcttBridgeServiceTest.kt` (quote, transfer, status tracking)
- Created `MintServiceTest.kt` (mint operations, allowlist, errors)
- Created `BurnServiceTest.kt` (burn operations, errors, network handling)
- **Total: 115 Solidity tests + 12 Kotlin tests passing**

#### Priority 5: AWS Testing Infrastructure ✅
- Created `infra/terraform/environments/dev/validators.tf` (2 validators per L1)
- Created `scripts/bootstrap-validators.sh` (key generation, Secrets Manager, Terraform)
- Created `scripts/deploy-contracts.sh` (dual-chain deployment)
- Created `scripts/e2e-bridge-test.sh` (full TR→EU transfer test)
- Created `.env.aws-test.example` template
- Created `scripts/README.md` documentation

---

## Technical Debt & Known Issues

### High Priority
| Issue | Impact | Effort | Notes |
|-------|--------|--------|-------|
| Payment rails are stubs | Cannot process real deposits/withdrawals | High | Awaiting bank partnerships |
| eKYC provider not integrated | Manual KYC only | Medium | Veriff/Onfido SDKs ready |
| No external security audit | Production risk | High | Schedule before mainnet |
| No smart contract audit | Production risk | High | Schedule before mainnet |

### Medium Priority
| Issue | Impact | Effort | Notes |
|-------|--------|--------|-------|
| Password reset endpoints missing | User friction | Low | ✅ Added (deploy/mvp-live branch) |
| Mobile biometric auth incomplete | User experience | Low | Flutter plugin ready |
| Push notifications are stubs | User engagement | Medium | FCM/APNs integration needed |
| FX rates are hardcoded | Testing only | Low | Partner API integration needed |

### Low Priority
| Issue | Impact | Effort | Notes |
|-------|--------|--------|-------|
| No API rate limiting in prod | Potential abuse | Medium | Kong configuration needed |
| Missing transaction export feature | User feature | Low | PDF/CSV generation |

---

## Critical Files Reference

### Core Banking Entry Points
| File | Purpose |
|------|---------|
| `core-banking/.../AriPlatformApplication.kt` | Spring Boot entry |
| `core-banking/.../identity/api/AuthController.kt` | Auth endpoints |
| `core-banking/.../ledger/internal/service/LedgerService.kt` | Double-entry engine |
| `core-banking/.../payments/internal/service/CrossBorderTransferService.kt` | FX cross-border saga |
| `core-banking/.../payments/internal/service/SameCurrencyCrossBorderService.kt` | Same-ccy cross-border saga |

### Blockchain Service Entry Points
| File | Purpose |
|------|---------|
| `blockchain-service/.../AriBlockchainApplication.kt` | Spring Boot entry |
| `blockchain-service/.../bridge/IcttBridgeService.kt` | ICTT bridge operations |
| `blockchain-service/.../settlement/MintService.kt` | Token minting |
| `blockchain-service/.../settlement/BurnService.kt` | Token burning |
| `blockchain-service/.../outbox/OutboxPollerService.kt` | Event processing |

### Smart Contracts
| File | Purpose |
|------|---------|
| `contracts/contracts/token/AriStablecoin.sol` | Base stablecoin |
| `contracts/contracts/bridge/AriTokenHome.sol` | Native token lock/release |
| `contracts/contracts/bridge/AriTokenRemote.sol` | Wrapped token mint/burn |
| `contracts/contracts/bridge/AriBridgeAdapter.sol` | ICTT bridge orchestration |
| `contracts/contracts/bridge/AriBurnMintBridge.sol` | Same-ccy burn/mint bridge |

### Infrastructure
| File | Purpose |
|------|---------|
| `infra/terraform/environments/dev/validators.tf` | Validator config |
| `scripts/bootstrap-validators.sh` | Validator setup |
| `scripts/deploy-contracts.sh` | Contract deployment |
| `scripts/e2e-bridge-test.sh` | End-to-end test |

### Documentation
| File | Purpose |
|------|---------|
| `docs/LOCAL_TESTING_GUIDE.md` | Step-by-step local testing (with/without blockchain) |
| `scripts/README.md` | AWS validator and E2E testing docs |
| `docs/avalanche-docs.md` | Avalanche L1 reference |
| `docs/compliance.md` | TR/EU regulatory requirements |

---

## Verification Checklist

Before considering a phase complete, verify:

### Backend
- [ ] `./gradlew :core-banking:test` passes
- [ ] `./gradlew :blockchain-service:test` passes (requires Java 21)
- [ ] No critical ktlint warnings

### Contracts
- [ ] `npx hardhat compile` succeeds
- [ ] `npx hardhat test` passes (135 tests)
- [ ] `npx hardhat coverage` shows >80% coverage

### Frontend
- [ ] `cd web && npm run build` succeeds
- [ ] `cd admin-console && npm run build` succeeds
- [ ] `cd mobile && flutter build apk` succeeds

### Infrastructure
- [ ] Terraform validates: `terraform validate`
- [ ] Docker images build: `./gradlew bootJar && docker build`

### Integration
- [ ] E2E test passes: `./scripts/e2e-bridge-test.sh`

---

## Next Steps (Recommended Order)

1. **Deploy validators to AWS** — Run `./scripts/bootstrap-validators.sh`
2. **Create L1 chains** — Platform CLI (`platform subnet create` + `platform chain create`)
3. **Deploy contracts** — Run `./scripts/deploy-contracts.sh both`
4. **Run E2E test** — Verify TR→EU transfer completes
5. **Schedule security audit** — External firm for backend + contracts
6. **Integrate real payment rails** — SEPA (EU) / FAST (TR) when partnerships ready
7. **Production deployment** — Both regions simultaneously

---

## Commit History (Phase 4-5 Completion)

| Commit | Date | Description |
|--------|------|-------------|
| `092f6a2` | 2026-02-05 | Phase 4-5 P5: AWS 2-validator testing infrastructure |
| `267aa84` | 2026-02-05 | Phase 4-5 P4: Comprehensive test suite for ICTT bridge |
| `<prior>` | 2026-02-05 | Phase 4-5 P3: Smart contract deployment scripts |
| `<prior>` | 2026-02-05 | Phase 4-5 P2: Kotlin wrappers aligned with Solidity |
| `<prior>` | 2026-02-05 | Phase 4-5 P1: Bug fixes (methods, migrations, metadata) |

---

## How to Update This Document

When completing significant work:

1. Update the **Quick Status** table
2. Check off completed items in the relevant Phase section
3. Add entries to **Recent Session Work** with date and details
4. Update **Technical Debt** if new issues discovered
5. Update **Commit History** with significant commits
6. Update the **Last Updated** date at the top

For AI agents: This document is the source of truth for implementation status. Check it before starting work to understand current state and avoid duplicating effort.
