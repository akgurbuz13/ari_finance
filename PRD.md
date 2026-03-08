# PRD.md — ARI Fintech MVP (Turkey + EU, Avalanche-native, user-abstracted)

**Status:** Draft for build

This PRD defines a compliant, scalable MVP for **ARI**, a multi-region fintech that starts as a regulated **e-money + payment account** provider and expands into a fully digital bank.

---

## 1) Product vision

### 1.1 What we’re building
ARI is a **global-first money platform** designed for:
- **Commissionless user transfers** (user-facing fee = 0)
- **Instant cross-border value movement** (Turkey ↔ EU first; expandable to other regions)
- **Multi-currency accounts** with bank connectivity (IBAN-based deposits/withdrawals)
- A foundation for **smart money** (conditional payments, automation) and later **tokenized assets** (NFT rails for car/home transfer and collateralized loans) once legal/registry integrations exist.

### 1.2 The “press a button” future
Our long-term product goal is that transferring *ownership* or *rights* (home, car, rental contract, escrow conditions) becomes as easy as sending money today.
- **Today (MVP):** we solve the high friction / high cost of FX + cross-border transfers.
- **Tomorrow:** we add programmable money and NFT-based asset rails to enable instant settlement workflows under regulation.

### 1.3 Non-negotiable UX principles
- **Users should not know blockchain exists.** No gas, no wallet jargon, no chain IDs.
- The app feels like a modern bank: fast, predictable, recoverable.
- Every user-visible action has a clear status: `Processing → Completed`.

---

## 2) Operating model & requirements

### 2.1 Regions from day one
- Turkey and EU are equal targets.
- Architecture must support adding regional “pods” later (UK/Asia): new rails + new regional Avalanche L1.

### 2.2 Reliability and uptime
Fintech must behave like critical infrastructure:
- **24/7 availability** (no “maintenance windows” that stop transfers).
- Design for **high uptime** (target ≥ 99.9% MVP; architect toward 99.99%+).
- Define and enforce **RTO/RPO** targets (e.g., RTO < 30–60 min MVP; RPO near-zero for ledger).

### 2.3 Modern stack and scalability (no language lock-in)
We do not lock into a single language; we choose technologies that are:
- Cloud-native and operationally mature
- Easy to audit and secure
- Scalable across regions
- Supported by strong talent pools (TR + EU)

---

## 3) MVP scope

### 3.1 Must-have user features
**Onboarding & security**
- Signup + login
- eKYC flow + verification status (`pending/approved/rejected`)
- Step-up verification for risky actions (2FA)

**Accounts (payments + e-money)**
- Wallet accounts per currency (TRY, EUR)
- IBAN connectivity per region (via partner/sponsor bank in MVP)
- Balance + statement + transaction history

**Deposits & withdrawals**
- Turkey: EFT/FAST deposit/withdraw
- EU: SEPA (and ideally SEPA Instant) deposit/withdraw

**Transfers (commissionless UX)**
- Domestic P2P transfers (TRY within TR; EUR within EU)
- Cross-border transfers TR ↔ EU (recipient gets local currency)
- FX conversion with transparent quote, final amount shown before send

### 3.2 Must-have compliance features
- KYC/AML hooks + sanctions/PEP screening
- Transaction monitoring + alerts (rules-based MVP)
- Case management (review/approve/flag)
- Funds safeguarding model + reconciliation reporting
- Full audit logs (admin actions, ledger changes, access logs)
- Admin console (RBAC): KYC decisions, freeze/unfreeze, limits, overrides (dual control)

### 3.3 Explicit non-goals (MVP)
- Lending, credit, investments
- Cards (prepare for later, not required to ship MVP)
- RWA/NFT registry integrations (design for later)
- Public blockchain usage
- Open DeFi pools or user-controlled bridging

---

## 4) Compliance-by-design (Turkey + EU)

> This is a system design checklist, not legal advice.

### 4.1 Core regulatory themes (both regions)
- **Safeguarding:** customer funds held segregated; e-money issued 1:1 backed.
- **KYC/AML:** identity verification, ongoing monitoring, suspicious activity workflow.
- **Security:** strong auth, encryption, least privilege, auditability.
- **Operational resilience:** incident response, DR, monitoring, vendor risk.
- **Privacy:** KVKK + GDPR requirements (data minimization, retention, user rights).

### 4.2 Turkey emphasis (TCMB/CBRT + MASAK)
- Treat TRY tokenization as **regulated e-money ledger**, not public crypto.
- Strong audit trails and reporting readiness.
- Integration to local rails (FAST/EFT) and compliance reporting processes.

### 4.3 EU emphasis (EMI + PSD2/SCA + GDPR + resilience)
- Strong customer authentication patterns and risk-based step-up.
- Strict logging, access control, data protection.
- Design for operational resilience expectations (testing, DR, vendor controls).

---

## 5) System architecture (hybrid model)

### 5.1 First principle: hybrid ledger
**Canonical truth is off-chain** (for customer experience and regulatory reporting), while **settlement + portability is on-chain**.

- **Off-chain (Core Fintech Layer)**
  - Customer profiles, KYC status
  - Accounts and limits
  - Double-entry ledger (statements, disputes, reversals)
  - Rail integrations (FAST/EFT, SEPA)
  - Compliance monitoring and reporting

- **On-chain (Avalanche Layer)**
  - Regional stablecoins representing issued e-money (TRY, EUR)
  - Fast settlement + immutable transaction trail
  - Inter-region movement via Avalanche interchain transfers

**Key rule:** the app does not query chain directly for “truth”. The backend publishes a clean banking view.

### 5.2 Logical architecture
```
Mobile/Web App
   |
API Gateway (auth, rate limits)
   |
+---------------------------+------------------------------+
| Core Fintech Services     | Avalanche Services           |
|                           |                              |
| Identity/KYC              | Node/RPC Access              |
| Account & Ledger          | Token Contracts (TRY/EUR)    |
| Rails Integrations        | Bridge (ICM/ICTT)            |
| FX/Treasury               | Relayer / Fee Sponsorship    |
| Compliance Monitoring     | Chain Event Listener         |
+---------------------------+------------------------------+
   |
Data Stores (ledger DB, user DB, audit logs, analytics)
```

### 5.3 Ledger design options (no lock-in)
**Option A — Relational double-entry ledger (recommended MVP)**
- Append-only `LedgerEntries` (debit/credit)
- Derived balances via materialized views / projections
- Strong idempotency + constraints

**Option B — Event-sourced ledger**
- Immutable event log + projections (more complex)
- Great for scale and audits, but heavier for MVP

**Option C — Distributed SQL**
- Global-write capability, higher ops cost

**Recommendation:** Start with A and implement an outbox pattern so moving toward B is straightforward.

### 5.4 Service boundaries (minimum viable)
- Identity/KYC
- Account & Ledger
- Payments Rails Adapters (TR rails, EU rails)
- FX/Treasury (quotes, conversion, liquidity)
- Blockchain Settlement (mint/burn, transfers, events)
- Compliance Monitoring (rules, alerts, cases)
- Admin Console

---

## 6) Avalanche integration (must be in MVP)

### 6.1 Regional permissioned L1s
- **TR L1:** TRY e-money token
- **EU L1:** EUR e-money token

Each region runs its own **permissioned** validator set. This supports:
- regional operational control
- region-specific policy (limits, compliance)
- clearer regulatory perimeter

### 6.2 Stablecoin = issued e-money representation
For each currency token:
- Mint when fiat deposit is confirmed
- Burn when withdrawal is executed
- Total supply must match safeguarded funds (reconciled)

### 6.3 Cross-region transfers (Avalanche interchain)
We connect regional L1s using Avalanche interchain token transfer mechanisms.
- Origin chain: lock/burn
- Destination chain: mint/release

Policy layer on top:
- KYC-only participants
- amount/velocity limits
- enhanced review thresholds

### 6.4 Gasless + blockchain-abstracted UX
Users should never manage gas or keys.

Two acceptable MVP patterns:
1) **Custodial signing + relayer:** backend signs and submits transactions (keys stored in HSM/KMS). User sees bank-style confirmations.
2) **User signatures + relayer:** user signs a standard approval in-app; backend pays fees and submits.

Preferred for full abstraction: **custodial signing** in MVP, with strict controls and audit.

### 6.5 Permissioning and control (regulator-friendly)
- Allowlist: only KYC-approved addresses transact.
- Deployer restrictions: only Ova-approved contracts.
- Emergency controls: pause/freeze with multi-approval governance.

### 6.6 Validator hosting (cloud allowed, hardened)
- Cloud VMs are acceptable with:
  - network isolation
  - hardened images
  - continuous monitoring
  - keys in HSM / secure enclave
- Distribute validators across providers/regions for resilience.

---

## 7) Money lifecycle (end-to-end)

### 7.1 Deposit (fiat → token)
1. User deposits to assigned IBAN.
2. Rails adapter confirms settlement.
3. Ledger credits user.
4. Blockchain service mints tokens to user address.
5. Reconciliation checks safeguarding balance == supply.

### 7.2 Domestic transfer
1. User initiates send.
2. Compliance checks (KYC, limits, sanctions, fraud rules).
3. Submit on-chain transfer (gasless).
4. Update DB from receipt/event.

### 7.3 Cross-border (TR ↔ EU)
1. FX/Treasury quotes rate and locks liquidity.
2. Execute conversion + bridge.
3. Recipient is credited in local currency.
4. Full audit trail (FX quote, approvals, chain tx hashes).

### 7.4 Withdraw (token → fiat)
1. User requests payout.
2. Ledger debits; burn tokens.
3. Execute payout via rails.

---

## 8) Security, monitoring, and operations

### 8.1 Security baseline
- Strong auth + 2FA
- RBAC for admins + dual control for sensitive actions
- Secrets in vault/KMS
- Encryption at rest + in transit
- Audit logging for every privileged action

### 8.2 Key management
- HSM/KMS-backed keys for:
  - validators
  - mint/burn admin
  - relayer
- Rotation, backup, separation of duties

### 8.3 Monitoring and incident response
- Metrics: API, DB, message queue, rails latency, chain health
- Alerts: abnormal transaction patterns, reconciliation mismatch
- Runbooks: chain degradation, rail outage, suspicious activity
- DR: backups, restore drills, regional failover plan

---

## 9) MVP definition of done

### Functional
- Onboarding + KYC decisioning
- TRY & EUR accounts + statements
- IBAN deposit/withdraw in TR and EU (partner integration acceptable)
- Domestic P2P + cross-border TR↔EU
- FX quote + execution
- Gasless UX (no gas token exposure)
- Admin console (KYC/freeze/limits/audit)

### Compliance/ops
- Safeguarding accounts mapped + daily reconciliation report
- AML monitoring rules + case workflow + reporting exports
- Full audit trails + access logs
- Incident response + DR plan (MVP level)

### Blockchain
- TR permissioned L1 live + TRY token
- EU permissioned L1 live + EUR token
- Interchain transfers working (test + staging)
- Allowlist + deploy restrictions enabled

---

## 10) Roadmap (post-MVP)
- Phase 2: cards, merchant payments, more currencies, stronger automation.
- Phase 3: smart money + tokenized assets (cars/homes), registry integrations, collateralized lending rails.

---

## 11) Open questions
- Licensing sequencing (TR vs EU vs parallel)
- Custody model (custodial vs MPC vs user-held)
- FX liquidity strategy (in-house treasury vs partners)
- Data residency constraints per jurisdiction
- Validator composition (including potential observer nodes)
