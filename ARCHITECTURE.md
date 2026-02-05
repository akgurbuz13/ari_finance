# Ova Fintech MVP — System Architecture & Implementation Plan

## Summary

Build a regulated fintech platform (Ova) launching in Turkey + EU simultaneously. Core banking is a **Kotlin/Spring Boot modular monolith** with **PostgreSQL double-entry ledger**, and blockchain settlement runs as a **separate Avalanche L1 service**. Cross-border transfers use Avalanche ICTT bridge. Architecture is designed from day 1 to support future agentic payments (x402/AP2/ACP).

---

## 1. Tech Stack

| Layer | Choice | Rationale |
|-------|--------|-----------|
| **Backend** | Kotlin + Spring Boot 3 + Spring Modulith | Null safety, immutability, proven at Revolut/N26. Spring Modulith gives explicit module boundaries for future extraction |
| **Database** | PostgreSQL 16 | ACID compliance, append-only double-entry ledger, outbox pattern. jOOQ for type-safe SQL |
| **Cache** | Redis 7 | Sessions, rate limits, OTP, FX rate cache, idempotency keys |
| **Migrations** | Flyway | Used by Revolut, version-controlled schema evolution |
| **Mobile** | Flutter (Dart) | Single codebase, proven at Nubank (200M+ users), excellent custom rendering |
| **Web App** | Next.js 14 (React) + Tailwind CSS | Customer-facing web app. SSR for SEO/performance. Black & white design system, minimalist "serious banking" aesthetic. Brand: "Ova" text wordmark (no logo). Reference: Revolut web |
| **Admin Console** | Next.js 14 (React) | Internal admin tool. SSR for security, RBAC-protected. Separate deployment from web app |
| **Blockchain** | Solidity + Hardhat, Avalanche L1 via AvaCloud | ERC-20 stablecoins, ICTT bridge, permissioned validators |
| **Cloud (EU)** | AWS (eu-central-1 Frankfurt) | Most mature fintech compliance (PCI-DSS, SOC2) |
| **Cloud (Turkey)** | Azure (Turkey Central) | Only major cloud provider with Turkey region. KVKK data residency compliance |
| **IaC** | Terraform + Helm | Multi-cloud, reproducible, auditible infrastructure |
| **Container** | Docker + Kubernetes (EKS/AKS) | Industry standard orchestration |
| **CI/CD** | GitHub Actions | Simple, integrated, sufficient for small team |
| **Testing** | JUnit 5 + Kotest + Testcontainers | Testcontainers for real PostgreSQL/Redis in tests |
| **API Docs** | OpenAPI 3.1 (springdoc) | Machine-readable API spec (critical for future agentic payment compatibility) |
| **Observability** | Grafana + Prometheus + Loki (or Datadog) | Metrics, logs, traces. Critical for fintech SLA |

---

## 2. System Architecture

```
              ┌─────────────────────┐     ┌─────────────────────┐
              │   Flutter Mobile App│     │   Web App (Next.js) │
              │   (iOS + Android)   │     │   ova.com / ova.app │
              └─────────┬───────────┘     └─────────┬───────────┘
                        │ HTTPS                     │ HTTPS
                        └───────────┬───────────────┘
                          ┌─────────▼────────────┐
                          │   API Gateway (Kong)  │
                          │   Auth, Rate Limit,   │
                          │   TLS Termination     │
                          └─────────┬────────────┘
                                    │
              ┌─────────────────────┼────────────────────┐
              │                     │                     │
    ┌─────────▼──────────┐  ┌──────▼─────────┐  ┌───────▼─────────┐
    │  OVA CORE BANKING  │  │ BLOCKCHAIN SVC │  │  ADMIN CONSOLE  │
    │  (Modular Monolith)│  │ (Separate)     │  │  (Next.js)      │
    │                    │  │                │  │                 │
    │ ┌────────────────┐ │  │ Settlement     │  │ KYC Decisions   │
    │ │ Identity Module│ │  │ Mint/Burn      │  │ Freeze/Unfreeze │
    │ │ (Auth, KYC)    │ │  │ Relayer        │  │ Compliance Cases│
    │ ├────────────────┤ │  │ Event Listener │  │ Audit Logs      │
    │ │ Ledger Module  │ │  │ Key Mgmt       │  │ Limits/Config   │
    │ │ (Accounts,     │ │  └───────┬────────┘  └─────────────────┘
    │ │  Entries,      │ │          │
    │ │  Balances)     │ │          │ Outbox Events
    │ ├────────────────┤ │          │ + REST API
    │ │ Payments Module│ │  ┌───────▼─────────────────────────┐
    │ │ (Orchestration)│ │  │     AVALANCHE L1 CHAINS         │
    │ ├────────────────┤ │  │                                 │
    │ │ Rails Module   │ │  │  ┌──────────┐  ┌──────────┐    │
    │ │ (FAST/EFT,     │ │  │  │  TR L1   │  │  EU L1   │    │
    │ │  SEPA)         │ │  │  │ TRY Token│  │ EUR Token│    │
    │ ├────────────────┤ │  │  └────┬─────┘  └────┬─────┘    │
    │ │ FX Module      │ │  │       │    ICTT     │           │
    │ │ (Quotes, Conv.)│ │  │       └─────Bridge──┘           │
    │ ├────────────────┤ │  └─────────────────────────────────┘
    │ │ Compliance Mod.│ │
    │ │ (AML, Screen.) │ │
    │ ├────────────────┤ │
    │ │ Notification   │ │
    │ │ (SMS, Email,   │ │
    │ │  Push)         │ │
    │ └────────────────┘ │
    └────────┬───────────┘
             │
    ┌────────▼───────────┐    ┌──────────────┐
    │   PostgreSQL       │    │    Redis      │
    │   (Ledger, Users,  │    │  (Sessions,   │
    │    Compliance,     │    │   Cache, OTP,  │
    │    Outbox, Audit)  │    │   Rate Limit)  │
    └────────────────────┘    └──────────────┘
```

### Key Architecture Decisions

1. **Modular monolith, not microservices**: For a 5-8 person team, microservices infrastructure overhead (service mesh, distributed tracing, per-service databases) would consume too much of the budget. Spring Modulith enforces module boundaries at compile time, making future extraction straightforward.

2. **Blockchain as separate service**: Different security profile (HSM keys), different deployment cadence, different scaling characteristics. Must be independently deployable.

3. **PostgreSQL outbox pattern**: Modules publish domain events to an outbox table within the same DB transaction. A poller reads outbox and dispatches events. This guarantees at-least-once delivery without needing Kafka from day 1. When extracting microservices later, replace the poller with Kafka consumers.

4. **Off-chain canonical for regulated features**: The PostgreSQL double-entry ledger is the legal source of truth for balances, statements, and regulatory reporting. Blockchain is the settlement and portability layer. On-chain state is reconciled daily against the off-chain ledger.

---

## 3. Multi-Cloud Architecture (Data Residency)

```
┌─────────────────────────────────────┐  ┌────────────────────────────────────┐
│         AWS eu-central-1            │  │      Azure Turkey Central          │
│         (Frankfurt)                 │  │      (Ankara)                      │
│                                     │  │                                    │
│  ┌──────────────┐ ┌──────────────┐  │  │  ┌──────────────┐ ┌────────────┐  │
│  │ EKS Cluster  │ │ RDS Postgres │  │  │  │ AKS Cluster  │ │ Azure DB   │  │
│  │ (Core + BC)  │ │ (EU data)    │  │  │  │ (Core + BC)  │ │ Postgres   │  │
│  ├──────────────┤ ├──────────────┤  │  │  ├──────────────┤ │ (TR data)  │  │
│  │ ElastiCache  │ │ S3 (docs,    │  │  │  │ Azure Cache  │ ├────────────┤  │
│  │ (Redis)      │ │  backups)    │  │  │  │ for Redis    │ │ Blob Store │  │
│  ├──────────────┤ ├──────────────┤  │  │  ├──────────────┤ │ (docs)     │  │
│  │ AWS KMS      │ │ CloudWatch   │  │  │  │ Key Vault    │ │            │  │
│  │ (EU keys)    │ │ (EU logs)    │  │  │  │ (TR keys)    │ │            │  │
│  └──────────────┘ └──────────────┘  │  │  └──────────────┘ └────────────┘  │
│                                     │  │                                    │
│  EU L1 Validators (2-3 nodes)       │  │  TR L1 Validators (2-3 nodes)     │
└─────────────────────────────────────┘  └────────────────────────────────────┘
```

**Critical rule**: Turkish user data (PII, KYC documents, transaction records) NEVER leaves Azure Turkey Central. EU user data stays in AWS Frankfurt. Cross-border transfers exchange only the minimum data needed (amount, anonymized IDs) via encrypted API calls between regions.

**Same codebase, separate deployments**: The same Docker images are deployed to both clusters, configured via environment variables for region-specific behavior (which rails adapters to load, which DB to connect to, which L1 chain to use).

---

## 4. Database Schema (Core Tables)

### Identity Schema
```sql
-- Users
CREATE TABLE identity.users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL UNIQUE,
    phone           TEXT NOT NULL UNIQUE,
    password_hash   TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'pending_kyc', -- pending_kyc, active, suspended, closed
    region          TEXT NOT NULL, -- TR, EU
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- KYC Verifications
CREATE TABLE identity.kyc_verifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES identity.users(id),
    provider        TEXT NOT NULL, -- e.g., 'onfido', 'veriff'
    provider_ref    TEXT NOT NULL,
    status          TEXT NOT NULL, -- pending, approved, rejected, expired
    level           TEXT NOT NULL, -- basic, enhanced
    decision_by     UUID, -- admin user who made decision
    decision_at     TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Ledger Schema (Double-Entry — The Heart of the System)
```sql
-- Accounts (one per user per currency)
CREATE TABLE ledger.accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES identity.users(id),
    currency        TEXT NOT NULL, -- TRY, EUR
    account_type    TEXT NOT NULL, -- user_wallet, system_float, fee_revenue, safeguarding
    status          TEXT NOT NULL DEFAULT 'active', -- active, frozen, closed
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, currency, account_type)
);

-- Transactions (business-level grouping of entries)
CREATE TABLE ledger.transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key TEXT NOT NULL UNIQUE, -- prevents double-posting
    type            TEXT NOT NULL, -- deposit, withdrawal, p2p_transfer, fx_conversion, cross_border, mint, burn, fee
    status          TEXT NOT NULL, -- pending, completed, failed, reversed
    reference_id    TEXT, -- external ref (payment order ID, chain tx hash)
    metadata        JSONB, -- flexible context (FX rate, partner ref, etc.)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

-- Ledger Entries (append-only, immutable, always balanced debit = credit per transaction)
CREATE TABLE ledger.entries (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  UUID NOT NULL REFERENCES ledger.transactions(id),
    account_id      UUID NOT NULL REFERENCES ledger.accounts(id),
    direction       TEXT NOT NULL CHECK (direction IN ('debit', 'credit')),
    amount          NUMERIC(20, 8) NOT NULL CHECK (amount > 0),
    currency        TEXT NOT NULL,
    balance_after   NUMERIC(20, 8) NOT NULL, -- snapshot for fast reads
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Constraint: sum of debits = sum of credits per transaction (enforced in application + periodic audit)
-- Index for balance queries
CREATE INDEX idx_entries_account_created ON ledger.entries(account_id, created_at DESC);
```

### Payments Schema
```sql
CREATE TABLE payments.payment_orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key TEXT NOT NULL UNIQUE,
    type            TEXT NOT NULL, -- deposit, withdrawal, domestic_p2p, cross_border
    status          TEXT NOT NULL, -- initiated, compliance_check, processing, settling, completed, failed, reversed
    sender_account_id   UUID REFERENCES ledger.accounts(id),
    receiver_account_id UUID REFERENCES ledger.accounts(id),
    amount          NUMERIC(20, 8) NOT NULL,
    currency        TEXT NOT NULL,
    fx_quote_id     UUID, -- if cross-border
    chain_tx_hash   TEXT, -- blockchain settlement tx
    rail            TEXT, -- fast, eft, sepa, sepa_instant, blockchain
    error_code      TEXT,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payments.payment_status_history (
    id              BIGSERIAL PRIMARY KEY,
    payment_order_id UUID NOT NULL REFERENCES payments.payment_orders(id),
    status          TEXT NOT NULL,
    reason          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Outbox (for cross-module/cross-service events)
```sql
CREATE TABLE shared.outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  TEXT NOT NULL, -- 'payment', 'kyc', 'ledger'
    aggregate_id    TEXT NOT NULL,
    event_type      TEXT NOT NULL, -- 'PaymentCompleted', 'KycApproved', 'MintRequested'
    payload         JSONB NOT NULL,
    published       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_unpublished ON shared.outbox_events(published, created_at) WHERE NOT published;

-- Audit log (append-only)
CREATE TABLE shared.audit_log (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        UUID, -- user or system
    actor_type      TEXT NOT NULL, -- user, admin, system
    action          TEXT NOT NULL,
    resource_type   TEXT NOT NULL,
    resource_id     TEXT NOT NULL,
    details         JSONB,
    ip_address      INET,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 5. Kotlin Module Structure

```
ova/
├── build.gradle.kts                     # Root build config
├── settings.gradle.kts                  # Module declarations
├── docker-compose.yml                   # Local dev: Postgres, Redis
├── docker-compose.prod.yml              # Production compose reference
│
├── core-banking/                        # MODULAR MONOLITH
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/ova/platform/
│       ├── OvaPlatformApplication.kt    # Spring Boot entry point
│       │
│       ├── identity/                    # Module: Identity & KYC
│       │   ├── api/                     # REST controllers
│       │   │   ├── AuthController.kt
│       │   │   ├── UserController.kt
│       │   │   └── KycController.kt
│       │   ├── internal/               # Module internals (not exposed)
│       │   │   ├── service/
│       │   │   ├── repository/
│       │   │   └── model/
│       │   └── event/                  # Published events
│       │       ├── UserCreated.kt
│       │       └── KycApproved.kt
│       │
│       ├── ledger/                      # Module: Accounts & Ledger
│       │   ├── api/
│       │   │   ├── AccountController.kt
│       │   │   └── TransactionController.kt
│       │   ├── internal/
│       │   │   ├── service/
│       │   │   │   └── LedgerService.kt  # Double-entry posting engine
│       │   │   ├── repository/
│       │   │   └── model/
│       │   └── event/
│       │       └── EntryPosted.kt
│       │
│       ├── payments/                    # Module: Payment Orchestration
│       │   ├── api/
│       │   │   └── PaymentController.kt
│       │   ├── internal/
│       │   │   ├── service/
│       │   │   │   ├── DomesticTransferService.kt
│       │   │   │   └── CrossBorderTransferService.kt  # Saga orchestrator
│       │   │   └── model/
│       │   └── event/
│       │       ├── PaymentInitiated.kt
│       │       └── PaymentCompleted.kt
│       │
│       ├── rails/                       # Module: Banking Rail Adapters
│       │   ├── api/                     # Webhook endpoints for partner banks
│       │   ├── internal/
│       │   │   ├── adapter/
│       │   │   │   ├── FastAdapter.kt   # Turkey FAST
│       │   │   │   ├── EftAdapter.kt    # Turkey EFT
│       │   │   │   └── SepaAdapter.kt   # EU SEPA
│       │   │   └── service/
│       │   └── event/
│       │
│       ├── fx/                          # Module: FX & Treasury
│       │   ├── api/
│       │   │   └── FxController.kt
│       │   ├── internal/
│       │   │   ├── service/
│       │   │   │   ├── QuoteService.kt
│       │   │   │   └── ConversionService.kt
│       │   │   └── provider/
│       │   │       └── FxRateProvider.kt  # Partner API adapter
│       │   └── event/
│       │
│       ├── compliance/                  # Module: AML/Screening/Monitoring
│       │   ├── api/
│       │   │   └── ComplianceAdminController.kt
│       │   ├── internal/
│       │   │   ├── service/
│       │   │   │   ├── SanctionsScreeningService.kt
│       │   │   │   ├── TransactionMonitoringService.kt
│       │   │   │   └── CaseManagementService.kt
│       │   │   └── provider/
│       │   │       └── SanctionsListProvider.kt  # External API
│       │   └── event/
│       │
│       ├── notification/                # Module: Notifications
│       │   ├── internal/
│       │   │   ├── service/
│       │   │   │   ├── SmsService.kt
│       │   │   │   ├── EmailService.kt
│       │   │   │   └── PushService.kt
│       │   │   └── template/
│       │   └── event/
│       │
│       └── shared/                      # Shared kernel
│           ├── event/
│           │   ├── DomainEvent.kt
│           │   └── OutboxPublisher.kt
│           ├── security/
│           │   ├── SecurityConfig.kt
│           │   └── JwtTokenProvider.kt
│           ├── config/
│           │   └── RegionConfig.kt      # TR vs EU behavior
│           └── model/
│               ├── Money.kt             # Value object (amount + currency)
│               └── Region.kt
│
├── blockchain-service/                  # SEPARATE DEPLOYABLE
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/ova/blockchain/
│       ├── OvaBlockchainApplication.kt
│       ├── settlement/
│       │   ├── MintService.kt
│       │   ├── BurnService.kt
│       │   └── TransferService.kt
│       ├── bridge/
│       │   └── IcttBridgeService.kt     # Cross-chain transfers
│       ├── relayer/
│       │   └── GaslessRelayerService.kt # ERC-2771 meta-transactions
│       ├── listener/
│       │   └── ChainEventListener.kt    # On-chain event monitoring
│       ├── wallet/
│       │   └── CustodialWalletService.kt # KMS-backed key management
│       ├── outbox/
│       │   └── OutboxPollerService.kt   # Reads core-banking outbox
│       └── reconciliation/
│           └── ReconciliationService.kt # Daily on-chain vs off-chain check
│
├── contracts/                           # SOLIDITY SMART CONTRACTS
│   ├── package.json
│   ├── hardhat.config.ts
│   ├── contracts/
│   │   ├── token/
│   │   │   └── OvaStablecoin.sol        # ERC-20 + mint/burn/freeze/allowlist
│   │   ├── access/
│   │   │   └── KycAllowList.sol         # KYC-verified addresses only
│   │   └── bridge/
│   │       └── OvaBridgeAdapter.sol     # ICTT bridge integration
│   ├── test/
│   └── scripts/
│       └── deploy.ts
│
├── web/                                 # CUSTOMER-FACING WEB APP (Next.js)
│   ├── package.json
│   ├── tailwind.config.ts               # Black/white design system
│   ├── app/
│   │   ├── layout.tsx                   # Root layout with "Ova" wordmark
│   │   ├── page.tsx                     # Landing / marketing page
│   │   ├── (auth)/
│   │   │   ├── login/
│   │   │   └── signup/
│   │   ├── (dashboard)/                 # Authenticated area
│   │   │   ├── home/                    # Account overview, balances
│   │   │   ├── transfer/               # Send money (domestic + cross-border)
│   │   │   ├── accounts/               # Account details, statements
│   │   │   ├── history/                # Transaction history
│   │   │   └── settings/               # Profile, security, preferences
│   │   └── (onboarding)/
│   │       └── kyc/                     # KYC verification flow
│   ├── components/
│   │   ├── ui/                          # Design system (buttons, inputs, cards)
│   │   ├── layout/                      # Header, sidebar, footer
│   │   └── features/                    # Feature-specific components
│   └── lib/
│       ├── api/                         # API client (shared types with backend)
│       └── hooks/                       # React hooks
│
├── admin-console/                       # INTERNAL ADMIN TOOL (Next.js)
│   ├── package.json
│   ├── app/
│   │   ├── dashboard/
│   │   ├── kyc/
│   │   ├── compliance/
│   │   ├── users/
│   │   └── settings/
│   └── components/
│
├── mobile/                              # FLUTTER APP
│   ├── pubspec.yaml
│   ├── lib/
│   │   ├── main.dart
│   │   ├── features/
│   │   │   ├── auth/
│   │   │   ├── onboarding/
│   │   │   ├── home/
│   │   │   ├── transfer/
│   │   │   ├── accounts/
│   │   │   └── settings/
│   │   ├── core/
│   │   │   ├── api/
│   │   │   ├── models/
│   │   │   └── theme/
│   │   └── shared/
│   └── test/
│
└── infra/                               # INFRASTRUCTURE
    ├── terraform/
    │   ├── modules/
    │   │   ├── aws-eu/                  # EKS, RDS, ElastiCache, KMS
    │   │   ├── azure-tr/               # AKS, Azure DB, Cache, Key Vault
    │   │   └── shared/                 # DNS, CDN
    │   ├── environments/
    │   │   ├── dev/
    │   │   ├── staging/
    │   │   └── prod/
    │   └── main.tf
    ├── k8s/
    │   ├── base/                       # Kustomize base manifests
    │   ├── overlays/
    │   │   ├── eu/
    │   │   └── tr/
    │   └── helm/
    └── ci/
        └── .github/workflows/
            ├── build.yml
            ├── test.yml
            └── deploy.yml
```

---

## 6. Cross-Border Transfer Flow (TR → EU Example)

```
User A (Turkey, TRY) sends 10,000 TRY to User B (EU, EUR)

1. POST /api/v1/payments/cross-border
   Body: { sender_account_id, receiver_account_id, amount: 10000, currency: "TRY" }

2. [Payments Module] Create payment_order (status: initiated)

3. [Compliance Module] Run checks:
   a. Sender KYC status = approved
   b. Receiver KYC status = approved
   c. Sanctions/PEP screening (both parties)
   d. Amount within daily/monthly limits
   e. Rule-based monitoring (velocity, pattern)
   → If any fail: reject, notify user, log to audit

4. [FX Module] Get quote:
   a. Fetch TRY/EUR rate from FX partner API
   b. Apply Ova spread (e.g., 0.3-0.5% better than banks)
   c. Lock quote for 30 seconds
   d. Return: 10,000 TRY → ~260 EUR (example)
   → User confirms

5. [Ledger Module] Execute double-entry postings (single DB transaction):
   a. DEBIT  User A TRY account     10,000 TRY
   b. CREDIT System Float TRY       10,000 TRY
   c. DEBIT  System Float TRY       10,000 TRY  (FX conversion)
   d. CREDIT System Float EUR          260 EUR  (at locked rate)
   e. DEBIT  System Float EUR          260 EUR
   f. CREDIT User B EUR account        260 EUR
   g. DEBIT  System Float EUR          0.78 EUR  (Ova fee from spread)
   h. CREDIT Revenue EUR               0.78 EUR
   → All entries in same transaction, idempotency_key prevents double-post

6. [Outbox] Publish events: MintRequested(EUR, 260, user_b_chain_addr), BurnRequested(TRY, 10000, user_a_chain_addr)

7. [Blockchain Service] Picks up outbox events:
   a. Burn 10,000 TRY tokens on TR L1 (from User A's custodial address)
   b. ICTT bridge: initiate cross-chain transfer TR L1 → EU L1
   c. Mint 260 EUR tokens on EU L1 (to User B's custodial address)
   d. Confirm chain tx hashes

8. [Blockchain Service] Callback to core-banking: settlement confirmed
   → Update payment_order status to completed
   → Record chain tx hashes

9. [Notification Module] Notify both users (push + in-app)

10. [Reconciliation] Daily: verify on-chain balances match off-chain ledger
```

---

## 7. Blockchain Integration Design

### Stablecoin Contract (OvaStablecoin.sol)
```solidity
// Simplified — production contract needs full audit
contract OvaStablecoin is ERC20, AccessControl {
    bytes32 public constant MINTER_ROLE = keccak256("MINTER_ROLE");
    bytes32 public constant FREEZER_ROLE = keccak256("FREEZER_ROLE");

    mapping(address => bool) public allowlisted; // KYC-verified only
    mapping(address => bool) public frozen;

    function mint(address to, uint256 amount) external onlyRole(MINTER_ROLE) { ... }
    function burn(address from, uint256 amount) external onlyRole(MINTER_ROLE) { ... }
    function freeze(address account) external onlyRole(FREEZER_ROLE) { ... }
    function unfreeze(address account) external onlyRole(FREEZER_ROLE) { ... }
    function addToAllowlist(address account) external onlyRole(DEFAULT_ADMIN_ROLE) { ... }

    // Override transfer to check allowlist + frozen status
    function _beforeTokenTransfer(address from, address to, uint256 amount) internal override {
        require(allowlisted[from] && allowlisted[to], "Not KYC verified");
        require(!frozen[from] && !frozen[to], "Account frozen");
    }
}
```

### Key Management
- **Minter key**: AWS KMS (EU) / Azure Key Vault (TR) — signs mint/burn transactions
- **Relayer key**: Separate KMS key — pays gas fees for gasless meta-transactions
- **Admin key**: HSM-backed, multi-sig required for contract upgrades and emergency actions
- **User custodial addresses**: Derived from master key using HD wallet (BIP-44), per-user addresses stored in DB

### Communication Pattern (Core Banking ↔ Blockchain Service)
```
Core Banking                              Blockchain Service
     │                                           │
     │  1. Post to outbox table (same DB tx)      │
     │──────────────────────────────────────────▶│
     │                                           │  2. Poller reads outbox
     │                                           │  3. Execute on-chain tx
     │                                           │  4. Wait for confirmation
     │  5. REST callback: /api/internal/          │
     │     settlement-confirmed                   │
     │◀──────────────────────────────────────────│
     │  6. Update payment status                  │
```

---

## 8. Agentic Payments Architecture (Designed for Future)

The system is designed from day 1 to support machine-to-machine payments:

1. **OpenAPI-first API design**: Every endpoint has a machine-readable spec. AI agents can discover and use the API programmatically.

2. **Idempotency keys on all payment endpoints**: Agents need guaranteed exactly-once semantics.

3. **Programmatic authentication**: OAuth2 client_credentials flow for machine-to-machine auth (in addition to user auth).

4. **Webhook-based notifications**: Agents need async callbacks, not polling.

5. **Payment intent pattern**: `POST /api/v1/payments/intents` creates a payment intent that can be confirmed separately — matching the x402 and ACP patterns.

6. **Future endpoints to build**:
   - `POST /api/v1/agent/payments` — x402-compatible payment endpoint
   - `GET /.well-known/ova-payments.json` — machine-discoverable payment capabilities
   - WebSocket for real-time payment status streaming

7. **Blockchain-native agent payments**: Agents can transact directly on the Avalanche L1 via smart contract APIs — no need for traditional rails for agent-to-agent micropayments.

---

## 9. Security Architecture

| Layer | Implementation |
|-------|---------------|
| **Authentication** | JWT (access token 15min + refresh token 7d), 2FA via TOTP |
| **Authorization** | RBAC for admin, attribute-based for user operations |
| **API Security** | Rate limiting (Kong), request signing, CORS, CSRF protection |
| **Encryption at rest** | AES-256 via KMS (AWS KMS / Azure Key Vault) |
| **Encryption in transit** | TLS 1.3 everywhere, mTLS for internal service communication |
| **Secrets** | AWS Secrets Manager / Azure Key Vault, never in code or env vars |
| **PII handling** | Encrypted columns for sensitive fields, tokenization for card data (future) |
| **Admin actions** | Dual control for sensitive operations (freeze, limit changes, large reversals) |
| **Audit** | Append-only audit_log table, every state change recorded with actor + IP |
| **SCA (EU)** | Step-up auth for payments: 2FA + biometric for amounts above threshold |
| **Blockchain keys** | HSM/KMS-backed, separate keys per function, rotation policy |

---

## 10. MVP Implementation Phases

### Phase 1: Foundation (Weeks 1-6)
**Goal**: Bootable backend, auth, basic account management

**Deliverables**:
- Project scaffolding (Gradle multi-module, Docker Compose for local dev)
- PostgreSQL schema V001-V005 (identity + ledger base tables)
- Spring Security + JWT authentication (signup, login, refresh)
- 2FA setup (TOTP)
- User profile CRUD
- Account creation (TRY/EUR wallets)
- Balance query endpoint
- OpenAPI documentation auto-generation
- CI pipeline (build + test on PR)
- Terraform base setup (dev environment on AWS)

**Key files**:
- `core-banking/src/.../OvaPlatformApplication.kt`
- `core-banking/src/.../identity/api/AuthController.kt`
- `core-banking/src/.../ledger/internal/service/LedgerService.kt`
- `core-banking/src/.../shared/security/SecurityConfig.kt`
- Flyway migrations V001-V005

### Phase 2: Ledger & Domestic Payments (Weeks 7-12)
**Goal**: Working double-entry ledger, P2P domestic transfers

**Deliverables**:
- Double-entry posting engine with idempotency
- Transaction history + statements API
- Domestic P2P transfer flow (TRY→TRY, EUR→EUR)
- Outbox event publisher
- Basic compliance checks (amount limits, account status)
- Notification module (email + push stubs)
- Admin console foundation (Next.js with auth)

### Phase 3: KYC & Compliance (Weeks 13-18)
**Goal**: eKYC flow, sanctions screening, monitoring

**Deliverables**:
- eKYC provider integration (Veriff, Onfido, or similar)
- KYC status flow (pending → approved/rejected)
- Sanctions/PEP screening (ComplyAdvantage, Refinitiv, or similar)
- Rule-based transaction monitoring (velocity, amount thresholds)
- Alert + case management (admin console)
- Audit log viewer (admin console)
- Freeze/unfreeze user functionality

### Phase 4: Payment Rail Integration (Weeks 19-26)
**Goal**: Deposits and withdrawals via payment system connections

**How rail access works with an EMI license:**
- **EU (Lithuania)**: With EMI license, Ova gets **direct SEPA access** via CENTROlink (Bank of Lithuania gateway) — no sponsor bank needed for payment system access (as of April 2025 Instant Payments Regulation). Ova can also **issue IBANs directly**. A bank is still needed for **fund safeguarding accounts** (holding segregated customer funds).
- **Turkey**: Whether CBRT EMI license grants direct FAST/EFT access is not publicly documented. Plan for direct access (preferred) with partner bank as fallback. Confirm with CBRT during licensing process. A bank is needed for **safeguarding accounts** regardless.

**Deliverables**:
- **EU**: CENTROlink integration for SEPA Credit Transfers and SEPA Instant
- **EU**: IBAN generation and assignment to customer accounts
- **Turkey**: FAST/EFT integration (direct if CBRT permits, otherwise via partner bank API)
- **Turkey**: IBAN assignment (direct or via partner bank)
- Deposit flow (bank transfer → ledger credit)
- Withdrawal flow (ledger debit → bank payout)
- Reconciliation between rail settlement statements and internal ledger
- Webhook/callback handlers for payment notifications
- Safeguarding account setup with credit institutions (both regions — required for fund segregation)

**Critical dependencies**:
- EMI license applications must be filed early (EU ~2-3 months, Turkey ~6+ months). Business team should start in Week 1.
- Safeguarding bank relationships needed in both regions (for holding customer funds, NOT for payment access in EU).
- For Turkey: CBRT consultation needed to confirm direct FAST participation eligibility.

### Phase 5: Blockchain & Cross-Border (Weeks 27-36)
**Goal**: Avalanche L1 deployment, stablecoins, cross-border transfers

**Deliverables**:
- Solidity contracts: OvaStablecoin, KycAllowList (with full test suite)
- Deploy TR L1 + EU L1 on Avalanche testnet (via AvaCloud)
- Blockchain service: mint/burn/transfer
- Custodial wallet management (KMS-backed HD wallet)
- Gasless relayer (ERC-2771)
- Chain event listener
- ICTT bridge integration (TR L1 ↔ EU L1)
- FX module: quote engine with partner API
- Cross-border transfer orchestrator (full saga)
- Daily reconciliation job (on-chain vs off-chain)

### Phase 6: Web App, Mobile App & Launch Prep (Weeks 37-46)
**Goal**: Customer-facing web + mobile apps, end-to-end testing, production readiness

**Web App Deliverables** (Next.js + Tailwind):
- Landing page with "Ova" wordmark branding (black/white, minimalist, serious banking aesthetic — reference: Revolut web)
- Auth flows (login, signup, password reset)
- Dashboard: account overview, balances, recent activity
- Transfer flows: domestic P2P, cross-border with FX quote
- Account details: statements, transaction history with search/filter
- KYC onboarding flow (eKYC provider widget integration)
- Settings: profile, security (2FA), preferences
- Responsive design (desktop + tablet + mobile web)

**Mobile App Deliverables** (Flutter):
- All flows matching web app (onboarding, KYC, home, transfers, accounts, settings)
- Push notification integration (FCM / APNs)
- Biometric authentication (fingerprint / face)
- Same black/white design system as web, adapted for mobile patterns

**Launch Prep Deliverables**:
- End-to-end test suite (web + mobile + API)
- Security audit (external)
- Smart contract audit (external — mandatory before mainnet)
- Performance / load testing
- Staging environment (both regions)
- Production deployment (both regions)
- Monitoring + alerting setup (Grafana/Prometheus)
- Incident response runbook

---

## 11. CI/CD Pipeline

```
PR Created → GitHub Actions:
  1. Lint (ktlint)
  2. Unit tests (JUnit + Kotest)
  3. Integration tests (Testcontainers: Postgres + Redis)
  4. Solidity tests (Hardhat)
  5. Docker image build
  6. OpenAPI spec validation

Merge to main → GitHub Actions:
  1. All above +
  2. Push Docker images to ECR/ACR
  3. Deploy to staging (both regions)
  4. Run smoke tests

Release tag → GitHub Actions:
  1. Deploy to production (both regions)
  2. Run health checks
  3. Notify team
```

---

## 12. Key Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| **No safeguarding bank** | Blocks fund segregation (regulatory requirement) | EU: direct SEPA access via CENTROlink doesn't need a bank, but safeguarding does. Start outreach in Week 1 for safeguarding relationships. Turkey: confirm FAST access model with CBRT during license application |
| **License delays** | Can't launch production | Build on testnet/staging while licenses process. Both regions in parallel reduces sequential risk |
| **Avalanche L1 complexity** | Delays blockchain phase | Use AvaCloud managed service. Deploy testnet in Phase 1 for early learning |
| **Multi-cloud complexity** | Ops overhead for small team | Same K8s manifests (Kustomize overlays), same codebase. Terraform modules abstract provider differences |
| **FX partner dependency** | Blocks cross-border | Start with manual FX rate management if partner not ready. Partner can be swapped later |
| **Small team bandwidth** | Can't parallelize enough | Leverage AI tooling (Claude Code). Prioritize ruthlessly. Phase 6 mobile can overlap with Phase 5 |

---

## 13. What We Are NOT Building in MVP

- Lending, credit, investments
- Card issuance
- RWA/NFT registry integrations
- Public blockchain usage
- Open DeFi pools
- Agentic payment endpoints (designed for, not built)
- Smart contract automation for businesses (post-MVP)
- Multi-currency beyond TRY/EUR
- Merchant payment acceptance

---

## Verification & Testing Plan

1. **Unit tests**: Every module has >80% coverage on business logic (especially LedgerService)
2. **Integration tests**: Testcontainers-based tests for all DB operations and cross-module flows
3. **Contract tests**: Full Hardhat test suite for all Solidity contracts
4. **E2E tests**: Full transfer flows (deposit → P2P → cross-border → withdrawal) on staging
5. **Reconciliation tests**: Verify on-chain balances match off-chain ledger after every test run
6. **Security audit**: External firm reviews auth, crypto, and smart contracts before production
7. **Load testing**: Simulate expected transaction volumes per region
8. **Regulatory readiness**: Compliance team reviews all flows against CBRT/MASAK (TR) and EMI/PSD2 (EU) requirements
