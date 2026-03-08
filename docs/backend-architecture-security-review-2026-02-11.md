# Backend Tech Stack, Security, and Architecture Review

Date: 2026-02-11
Scope: Entire repository backend stack (`core-banking`, `blockchain-service`, DB migrations, infra, CI/CD, and architecture/compliance docs)
Constraint followed: No code/config behavior changes were made; only this findings document was added.

## Executive Verdict

Current backend architecture is a strong prototype and a good modular-monolith foundation, but **not yet launch-ready for a high-security/high-uptime fintech**.

The top blockers are not stylistic; they are concrete runtime and security defects:
- Broken authorization boundaries on user-facing read APIs
- Multiple schema/code drifts that can break core payment/settlement flows
- Service-to-service auth/config mismatches that can prevent settlement callbacks
- Compliance query/constraint mismatches
- Production-hardening gaps in secrets, webhook validation, and SDLC controls

Global scale outlook: **partially scalable by design direction (modular + outbox + region split), but not yet operationally or data-architecturally scalable to global neobank volume without major hardening and data evolution.**

## Severity Legend

- Critical: Direct security, funds integrity, or production-break risk; launch blocker
- High: Serious reliability/compliance/security weakness; should be fixed before scale-up
- Medium: Important for scale/performance/operational excellence; plan into roadmap

## Findings

### Critical

1. Horizontal authorization gaps on account/payment/transaction reads

Evidence:
- `core-banking/src/main/kotlin/com/ari/platform/payments/api/PaymentController.kt:116`
- `core-banking/src/main/kotlin/com/ari/platform/payments/api/PaymentController.kt:132`
- `core-banking/src/main/kotlin/com/ari/platform/ledger/api/AccountController.kt:51`
- `core-banking/src/main/kotlin/com/ari/platform/ledger/api/TransactionController.kt:90`

Why this is critical:
- Authenticated users can request resources by ID without ownership checks.
- This is a direct confidentiality breach and likely regulatory violation (PII + financial data exposure).

Required change:
- Enforce ownership checks on every read path using account ownership or transaction-account joins.
- Add negative tests for cross-user access attempts.

2. Authentication route policy is over-broad (`/api/v1/auth/**` is public)

Evidence:
- `core-banking/src/main/kotlin/com/ari/platform/shared/security/SecurityConfig.kt:39`
- `core-banking/src/main/kotlin/com/ari/platform/identity/api/AuthController.kt:88`
- `core-banking/src/main/kotlin/com/ari/platform/identity/api/AuthController.kt:106`

Why this is critical:
- `logout`, `2fa/setup`, and `2fa/enable` live under `/api/v1/auth/**` and rely on authenticated principal.
- Broad permit-all can cause undefined security behavior and accidental unauthenticated access paths.

Required change:
- Permit only explicit public endpoints (`signup`, `login`, `refresh`, password-reset flows), require auth for the rest.

3. Service-to-service settlement callback path is internally inconsistent

Evidence:
- Core requires internal key header: `core-banking/src/main/kotlin/com/ari/platform/shared/security/InternalApiKeyFilter.kt:26`
- Internal path protected by `ROLE_SYSTEM`: `core-banking/src/main/kotlin/com/ari/platform/shared/security/SecurityConfig.kt:53`
- Blockchain callback does not send internal key header: `blockchain-service/src/main/kotlin/com/ari/blockchain/outbox/OutboxPollerService.kt:149`
- Callback target is internal endpoint: `core-banking/src/main/kotlin/com/ari/platform/shared/api/InternalSettlementController.kt:32`

Why this is critical:
- Settlement confirmations can fail authentication, leaving payment lifecycle stuck/inconsistent.

Required change:
- Include `X-Internal-Api-Key` on callback requests and validate end-to-end.
- Add integration test for successful internal settlement callback auth.

4. Production env variable naming is inconsistent and can break startup/runtime

Evidence:
- App expects `JWT_SECRET`: `core-banking/src/main/resources/application.yml:51`
- K8s sets `OVA_JWT_SECRET`: `infra/k8s/base/core-banking.yaml:97`
- App expects `BLOCKCHAIN_SERVICE_URL`: `core-banking/src/main/resources/application.yml:58`
- Compose sets `OVA_BLOCKCHAIN_SERVICE_URL`: `docker-compose.prod.yml:21`
- Blockchain app expects `CORE_BANKING_URL`: `blockchain-service/src/main/resources/application.yml:24`
- Compose sets `OVA_BLOCKCHAIN_CORE_BANKING_URL`: `docker-compose.prod.yml:44`
- App expects `INTERNAL_API_KEY`: `core-banking/src/main/resources/application.yml:62`
- K8s sets `OVA_INTERNAL_API_KEY`: `infra/k8s/base/core-banking.yaml:102`

Why this is critical:
- Misbound secrets/URLs can cause startup failure or service misrouting (`localhost` fallbacks).

Required change:
- Standardize env naming convention and remove ambiguous variants.
- Add startup configuration validation tests in CI.

5. Blockchain outbox/schema drift: non-existent columns referenced

Evidence:
- Outbox update writes `published_at`: `blockchain-service/src/main/kotlin/com/ari/blockchain/outbox/OutboxPollerService.kt:65`
- Outbox table has no `published_at`: `core-banking/src/main/resources/db/migration/V005__shared_tables.sql:3`
- Account owner query uses `owner_id`: `blockchain-service/src/main/kotlin/com/ari/blockchain/outbox/OutboxPollerService.kt:140`
- Ledger accounts schema uses `user_id`: `core-banking/src/main/resources/db/migration/V003__ledger_tables.sql:5`

Why this is critical:
- Poller can fail at runtime; settlement path dependent on this poller becomes unreliable.

Required change:
- Align SQL in service with current schema (or add backward-compatible migration with explicit intent).

6. Blockchain transaction status/model drift causes likely insert failures

Evidence:
- Service writes `status = "initiated"`: `blockchain-service/src/main/kotlin/com/ari/blockchain/bridge/IcttBridgeService.kt:177`
- Also writes initiated in bridge-back: `blockchain-service/src/main/kotlin/com/ari/blockchain/bridge/IcttBridgeService.kt:286`
- DB status constraint excludes `initiated`: `core-banking/src/main/resources/db/migration/V013__fix_blockchain_operations.sql:38`

Why this is critical:
- On-chain transaction persistence can fail under normal bridge operations.

Required change:
- Normalize status model between code and DB constraints.

7. Bridge transfer tracking likely broken due metadata format mismatch

Evidence:
- Metadata stored via map `.toString()`: `blockchain-service/src/main/kotlin/com/ari/blockchain/bridge/IcttBridgeService.kt:181`
- Query expects JSON-like pattern in `LIKE`: `blockchain-service/src/main/kotlin/com/ari/blockchain/repository/BlockchainTransactionRepository.kt:122`

Why this is critical:
- Bridge status lookup by transferId can fail or become nondeterministic.

Required change:
- Persist metadata as actual JSONB structure and query with JSON operators.

8. FX schema/application model is inconsistent across modules

Evidence:
- `payments.fx_quotes` schema columns: `rate`, `inverse_rate`, `used`: `core-banking/src/main/resources/db/migration/V004__payments_tables.sql:32`
- FX service expects `mid_market_rate`, `customer_rate`, `status`, `consumed_at`: `core-banking/src/main/kotlin/com/ari/platform/fx/internal/service/QuoteService.kt:85`
- Payments repo expects `exchange_rate`, `fee_amount`, `fee_currency`: `core-banking/src/main/kotlin/com/ari/platform/payments/internal/repository/FxQuoteRepository.kt:18`
- Conversion writes to `payments.fx_conversions` table: `core-banking/src/main/kotlin/com/ari/platform/fx/internal/service/ConversionService.kt:129`
- No migration defines `payments.fx_conversions`.

Why this is critical:
- Cross-border and FX flows can fail at SQL runtime; this affects core product capability.

Required change:
- Finalize one FX canonical schema and refactor both modules to it; add migration/test coverage.

9. Compliance/threshold logic references non-existent table and invalid enum value

Evidence:
- MASAK checks query `payments.payments`: `core-banking/src/main/kotlin/com/ari/platform/compliance/internal/service/MasakReportingService.kt:310`
- and: `core-banking/src/main/kotlin/com/ari/platform/compliance/internal/service/MasakReportingService.kt:335`
- Actual payment table is `payments.payment_orders`: `core-banking/src/main/resources/db/migration/V004__payments_tables.sql:3`
- Reconciliation inserts alert type `SAFEGUARDING_DISCREPANCY`: `core-banking/src/main/kotlin/com/ari/platform/payments/internal/service/ReconciliationService.kt:313`
- Constraint does not allow it: `core-banking/src/main/resources/db/migration/V011__masak_and_enhanced_sanctions.sql:86`

Why this is critical:
- AML thresholding can silently fail or error; safeguarding discrepancy escalation can fail insertion.

Required change:
- Align compliance SQL with actual schema; update allowed alert taxonomy.

10. Cross-border accounting and settlement state transitions are inconsistent

Evidence:
- FX leg debits and credits same source float account: `core-banking/src/main/kotlin/com/ari/platform/payments/internal/service/CrossBorderTransferService.kt:184`
- Payment marked `COMPLETED` immediately after publishing mint/burn requests: `core-banking/src/main/kotlin/com/ari/platform/payments/internal/service/CrossBorderTransferService.kt:292`
- Internal settlement callback prepares metadata but does not persist metadata update: `core-banking/src/main/kotlin/com/ari/platform/shared/api/InternalSettlementController.kt:112`

Why this is critical:
- Ledger semantics and settlement truth can diverge from real on-chain finality.

Required change:
- Correct FX leg postings, persist settlement leg state, and complete only after both legs are confirmed.

### High

11. Ledger posting path has potential race condition for concurrent debits

Evidence:
- Reads latest balance then writes new entry without explicit account lock: `core-banking/src/main/kotlin/com/ari/platform/ledger/internal/service/LedgerService.kt:75`
- Latest balance query is a plain read: `core-banking/src/main/kotlin/com/ari/platform/ledger/internal/repository/EntryRepository.kt:57`

Risk:
- Under concurrent load, user-wallet non-negative checks can be bypassed by races.

Recommended:
- Use account-level locking (`SELECT ... FOR UPDATE`) or serialized balance table with atomic updates.

12. Webhook signature verification is stubbed

Evidence:
- Rail webhook signature validator always returns true: `core-banking/src/main/kotlin/com/ari/platform/rails/api/RailWebhookController.kt:139`
- KYC webhook accepts signature header but simulated parser does not validate it: `core-banking/src/main/kotlin/com/ari/platform/identity/api/KycWebhookController.kt:18`, `core-banking/src/main/kotlin/com/ari/platform/identity/internal/provider/SimulatedKycProvider.kt:44`

Risk:
- Forged callback payloads can manipulate payment/KYC states.

Recommended:
- Provider-specific HMAC/signature verification + replay protection + timestamp tolerance.

13. RBAC/admin path is likely incomplete

Evidence:
- Admin endpoints require `ROLE_ADMIN`: `core-banking/src/main/kotlin/com/ari/platform/shared/security/SecurityConfig.kt:55`
- Access tokens generated with default empty role list: `core-banking/src/main/kotlin/com/ari/platform/identity/internal/service/AuthService.kt:156`
- JWT filter only maps roles from claim: `core-banking/src/main/kotlin/com/ari/platform/shared/security/JwtAuthenticationFilter.kt:28`
- User schema has no role column: `core-banking/src/main/resources/db/migration/V002__identity_tables.sql:3`

Risk:
- Admin authorization is either unreachable or handled out-of-band without clear governance.

Recommended:
- Implement explicit admin identity/role model and auditable role assignment workflow.

14. Network policy model may block managed DB/Redis connectivity

Evidence:
- Global default deny: `infra/k8s/base/network-policies.yaml:8`
- Egress rules allow DB/Redis by pod selectors (`app:postgres`, `app:redis`): `infra/k8s/base/network-policies.yaml:71`, `infra/k8s/base/network-policies.yaml:79`, `infra/k8s/base/network-policies.yaml:166`, `infra/k8s/base/network-policies.yaml:174`
- Infra uses managed RDS/Azure PG and managed Redis, not in-cluster pods: `infra/terraform/modules/aws-eu/main.tf:163`, `infra/terraform/modules/azure-tr/main.tf:102`

Risk:
- Production pods may fail to reach state stores depending on CNI/policy semantics.

Recommended:
- Replace podSelector DB/Redis egress with explicit CIDR/FQDN strategies per environment.

15. Secrets management posture is incomplete for fintech-grade production

Evidence:
- External secrets resource is commented out by default: `infra/k8s/base/kustomization.yaml:16`
- Terraform includes placeholder DB admin passwords in code: `infra/terraform/modules/aws-eu/main.tf:175`, `infra/terraform/modules/azure-tr/main.tf:111`

Risk:
- Higher chance of secret mismanagement and deployment drift.

Recommended:
- Make secret managers/Key Vault mandatory in deploy pipelines and ban inline credentials.

16. CI/CD lacks core security and supply-chain controls

Evidence:
- Build pipeline focuses lint/test/build only: `.github/workflows/build.yml:56`
- Deploy pipeline pushes mutable `latest` tags: `.github/workflows/deploy.yml:62`

Risk:
- Missing SAST/SCA/container scanning/SBOM/signing/policy gates for regulated environment.

Recommended:
- Add mandatory security gates and immutable digest-based deploys.

17. Architecture docs and runtime implementation diverge on security plane

Evidence:
- Architecture shows Kong API gateway and Kong rate limiting: `ARCHITECTURE.md:45`, `ARCHITECTURE.md:887`
- Runtime ingress routes directly via nginx ingress: `infra/k8s/base/ingress.yaml:11`

Risk:
- Security assumptions in docs may not match real controls.

Recommended:
- Align documentation and implementation; declare authoritative runtime architecture source.

18. Blockchain service HA is weak in base manifests

Evidence:
- Base blockchain deployment has one replica: `infra/k8s/base/blockchain-service.yaml:9`
- PDB requires one available pod: `infra/k8s/base/pod-disruption-budgets.yaml:22`

Risk:
- Single-pod dependency for settlement path reduces uptime margin.

Recommended:
- Run >=2 replicas with leader election/consumer coordination and test failover.

19. Test sanctions seed data shipped in migration path

Evidence:
- Fictional sanctions entries inserted in migration: `core-banking/src/main/resources/db/migration/V008__sanctions_screening.sql:24`

Risk:
- Production compliance datasets can be polluted if this migration is used unchanged.

Recommended:
- Remove seed data from production migrations; move to non-prod fixtures.

### Medium

20. Domain constraints are hardcoded to TR/EU and TRY/EUR

Evidence:
- Ledger currency check only TRY/EUR: `core-banking/src/main/resources/db/migration/V003__ledger_tables.sql:11`
- User region check only TR/EU: `core-banking/src/main/resources/db/migration/V002__identity_tables.sql:19`
- Safeguarding region/currency constrained to TR/EU + TRY/EUR: `core-banking/src/main/resources/db/migration/V009__iban_rail_references_reconciliation.sql:59`

Impact:
- Future expansion into additional corridors/currencies requires repeated schema migrations.

Recommended:
- Move to reference tables/config-driven jurisdiction+currency model.

21. Rate limiting is in-memory and non-distributed

Evidence:
- Uses local `ConcurrentHashMap` buckets: `core-banking/src/main/kotlin/com/ari/platform/shared/security/RateLimitingFilter.kt:35`
- File itself notes Redis-backed model for production: `core-banking/src/main/kotlin/com/ari/platform/shared/security/RateLimitingFilter.kt:169`

Impact:
- Limits are per-instance, can be bypassed across replicas.

Recommended:
- Use shared distributed rate-limit state (Redis or gateway-native globally consistent limits).

22. Database design lacks long-horizon scale patterns for append-heavy fintech workloads

Evidence:
- Ledger/outbox/event tables are unpartitioned in migrations (`V003`, `V005`, `V010`), and no partition strategy appears in migration set.

Impact:
- At global volume, index bloat, long vacuum cycles, and history query degradation are likely.

Recommended:
- Introduce partitioning/archival plan by time and region; add read-models and retention tiers.

## Database Scalability Assessment for Global Fintech

Short answer: **Not yet globally scalable in its current form, but evolvable.**

What is good:
- PostgreSQL + double-entry ledger is a valid fintech core foundation.
- Region-separated deployment direction (TR/EU split) is aligned with data-residency constraints.
- Outbox pattern is a strong start for event-driven evolution.

What will fail first at global scale:
- Unpartitioned ledger entries / outbox / chain events
- Cross-module schema drift and mixed ownership of FX/payment models
- Lack of explicit hot-path locking strategy for monetary debits
- Non-distributed rate limits and callback fragility

Scale path (recommended):
- Stage 1 (now): Fix critical correctness/security defects; lock money movement paths.
- Stage 2: Partition large append tables and introduce async read models for statements/history.
- Stage 3: Introduce durable event backbone (Kafka/Pulsar) for high-fanout flows and replay.
- Stage 4: Region-by-region domain decomposition with strict bounded contexts and contract tests.

## Competitor Benchmark (Architecture/Tech Stack Signals)

From public engineering materials and hiring signals:
- Nubank: heavy event-driven architecture with Kafka and strict domain/event discipline.
- Monzo: event-driven core patterns, strong resilience and “degraded mode” operational design.
- Wise: Java-based service ecosystem, strong reliability and observability focus for global transfers.
- N26: Java/Kotlin cloud-native microservice backend operating under strict EU regulatory controls.

Implication for ARI:
- Your direction (modular monolith + outbox + later extraction) is reasonable for current team size.
- But competitors at scale are differentiated by operational rigor: strict service contracts, resilient event systems, production security controls, and mature reliability engineering.
- To compete, near-term priority is correctness/security hardening, then platform reliability and data scalability.

## Prioritized Remediation Roadmap

### Phase 0 (Blockers, next 7-14 days)
- Fix all authorization boundary gaps on read endpoints.
- Fix security matcher scoping under `/api/v1/auth/**`.
- Resolve env/config key mismatches and enforce startup validation.
- Align schema/code drifts in blockchain outbox and FX tables.
- Repair compliance SQL/table references and alert type constraints.

### Phase 1 (30-45 days)
- Implement real webhook signature verification and replay protection.
- Make settlement callback auth explicit and tested (header + key rotation policy).
- Add ledger concurrency hardening with locking/atomic balance strategy.
- Remove production seed sanctions data from migrations.

### Phase 2 (60-90 days)
- Add SAST/SCA/container scan/SBOM/signing to CI/CD.
- Switch to immutable image digests for deployment.
- Align runtime architecture docs and controls (gateway, mTLS, secrets).
- Increase blockchain-service HA and validate failover behavior.

### Phase 3 (90+ days, scale prep)
- Partition high-growth tables and implement archival/read-model patterns.
- Introduce distributed rate limiting and globally consistent abuse controls.
- Implement a formal reliability program: SLOs, error budgets, chaos/failover tests.

## Suggested Target SLO/Security Baseline (Fintech-Grade)

- Core payment API availability: 99.95% minimum (target 99.99%)
- Settlement callback success (P99): >= 99.99%
- RPO: <= 5 minutes; RTO: <= 30 minutes for critical payment paths
- Mandatory controls before launch: external security audit, smart-contract audit, load/failure testing

## External References (Competitor/Industry Context)

- Nubank Engineering: https://building.nubank.com.br/the-beauty-of-kafka-canonicity/
- Nubank Risk/Real-time systems: https://building.nubank.com.br/scaling-to-fight-financial-crimes-how-we-defend-nubank-users-in-real-time/
- Monzo engineering (core payments orchestration): https://monzo.com/blog/2018/08/09/why-we-orchestrate-our-crowdfunding-payments-in-our-core-system
- Monzo resilience posture: https://monzo.com/help/current-accounts/what-happens-if-mastercard-goes-down
- Monzo security overview: https://monzo.com/help/security-fraud/how-we-protect-you/
- N26 backend hiring signal (Java/Kotlin): https://n26.com/en-eu/careers/7074025
- Wise engineering stack article: https://medium.com/wise-engineering/our-tech-stack-and-how-we-evolved-it-13613a30bf04
