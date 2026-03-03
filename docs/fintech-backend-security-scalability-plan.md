# Fintech Backend Security, Compliance, and Scalability Plan

Date: 2026-02-11
Scope: `core-banking`, `blockchain-service`, Postgres migrations, K8s/Terraform manifests, CI/CD

## Goals

1. Eliminate launch-blocking security and correctness defects in payments and settlement.
2. Align with fintech/banking expectations from OWASP API Security, NIST, DORA/PSD2-aligned controls.
3. Build a path to global scale (data, reliability, and operational controls).

## Phase A: Critical Blockers (Immediate)

1. Authorization and data isolation
- Enforce ownership checks for account/payment/transaction read APIs.
- Remove broad auth route exemptions; allow only explicit public auth endpoints.

2. Settlement trust boundary
- Enforce authenticated service-to-service callbacks on internal settlement endpoints.
- Ensure blockchain callback sends internal auth header consistently.

3. Schema/runtime drift removal
- Align outbox schema and poller behavior.
- Align blockchain tx status values with DB constraints.
- Align FX quote/conversion schema with service expectations.
- Fix compliance query/table mismatches.

4. Cross-border correctness
- Fix incorrect FX leg bookkeeping.
- Keep cross-border payments in `settling` until both burn/mint confirmations arrive.
- Persist settlement metadata and complete only on final confirmation.

## Phase B: Security Hardening

1. Webhook authenticity
- Replace webhook signature stubs with HMAC validation for rail and KYC webhooks.
- Add replay-resistant signature handling and strict provider secret configuration.

2. Identity/session controls
- Ensure access tokens carry explicit roles.
- Keep admin role paths separated from user role paths.

3. Secrets and config hygiene
- Normalize env var keys and add compatibility fallbacks during migration.
- Move runtime secrets to managed secret stores and remove plaintext placeholders.

## Phase C: Reliability and Uptime

1. Ledger concurrency
- Add deterministic account-level locking before balance mutation to prevent race-condition overdrafts.

2. Availability controls
- Increase settlement-path service resilience (replicas/coordination, failure-safe retries).
- Add SLO/SLI instrumentation and alerting for settlement and payment APIs.

3. DR/BCP
- Validate backup/restore and failover against RPO/RTO targets.

## Phase D: Scale Foundation

1. Database scalability
- Partition append-heavy tables (`ledger.entries`, `shared.outbox_events`, blockchain events).
- Implement archival/read-model strategy for long-term history and reporting workloads.

2. Event architecture
- Keep outbox as baseline, add robust consumer idempotency/dead-letter handling.
- Introduce event backbone (Kafka/Pulsar) once throughput/coupling thresholds are met.

## Validation and Acceptance

1. Security tests
- BOLA/BFLA regressions for all object-ID endpoints.
- Internal callback auth negative/positive tests.
- Webhook signature tampering tests.

2. Correctness tests
- Cross-border flow remains `settling` until both legs confirmed.
- FX quote + conversion lifecycle is fully persisted and replay-safe.
- Compliance threshold jobs execute against real schema.

3. Operational checks
- Compile + integration tests + migration checks in CI.
- Security scan gates and immutable deploy artifacts.

## Assumptions

1. Off-chain ledger remains legal source of truth; blockchain remains settlement rail.
2. Data residency constraints (TR/EU) remain strict and region boundaries are preserved.
3. Team executes in staged rollout with production gating between phases.
