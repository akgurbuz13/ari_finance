# Regulatory Compliance Documentation for Ova

## Overview

Ova operates as an e-money institution in two jurisdictions:
- **Turkey**: Under TCMB (Central Bank of Turkey) e-money license
- **European Union**: Under EMI (Electronic Money Institution) license + MiCA (Markets in Crypto-Assets) for EUR stablecoin

This document details the technical requirements for regulatory compliance in both jurisdictions.

---

## Part 1: Turkey (TCMB) Requirements

### 1.1 Legal Framework

| Regulation | Description |
|------------|-------------|
| Law No. 6493 | Payment Services and Electronic Money Issuance Law |
| TCMB Regulations | Implementing regulations for payment/e-money institutions |
| MASAK Laws (5549) | Anti-money laundering and counter-terrorism financing |
| KVKK (6698) | Personal Data Protection Law (Turkish GDPR) |
| SPK Crypto Laws | Capital Markets Board crypto asset regulations |

### 1.2 Capital Requirements

| Requirement | Amount (2025) | Notes |
|-------------|---------------|-------|
| Initial Paid-in Capital | 5,000,000 TL | At application |
| Minimum Equity (Özkaynak) | **80,000,000 TL** | As of June 30, 2025 |
| Guarantee Deposit | 5,000,000 TL | Held at TCMB |
| License Fee | 1,000,000 TL | Upon commencing operations |
| Application Fee | 500,000 TL | Non-refundable |

**Note:** Equity requirements are updated annually by TCMB based on inflation indices.

### 1.3 Corporate Structure

```
Requirements:
- Legal form: Anonim Şirket (Joint Stock Company)
- All shares: Registered (nama yazılı), cash-paid
- 10%+ shareholders: Must meet bank founder qualifications (Law 5411)
- Board: Must include finance, law, and technology experts
- Ownership: Transparent structure allowing regulatory supervision
```

### 1.4 Infrastructure Requirements

#### Data Localization

```
MANDATORY:
- Primary data center: Located in Turkey
- Secondary/DR data center: Located in Turkey
- Customer data: Must remain in Turkey
- Private cloud: Required for sensitive/competitive data

NOT PERMITTED:
- Processing customer data outside Turkey (without safeguards)
- Using public cloud for sensitive data
- Cross-border data transfer without KVKK compliance
```

#### Technical Standards

| Requirement | Standard |
|-------------|----------|
| Security Management | ISO 27001 certification required |
| Penetration Testing | Annual, by independent team |
| IT Audit | Biennial, by TCMB-authorized auditor |
| Personnel | Operations/infosec teams with min. 7 years experience |
| Business Continuity | Documented BCP with regular testing |

### 1.5 MASAK Requirements (AML/CFT)

#### Customer Due Diligence (KYC)

```
Identity Verification Requirements:
- Turkish citizens: TC Kimlik No validation against MERNIS
- Foreigners: Passport + visa/residence permit
- Video KYC: Permitted since November 2023
  - Real-time video conference with operator
  - Biometric facial recognition required
  - AI-based verification permitted with regulations
- Corporate customers: Remote verification permitted (January 2025)
```

#### Transaction Monitoring

| Threshold | Action Required |
|-----------|-----------------|
| 10,000 TL+ | Automatic flagging for analysis |
| 20,000 TL+ | Enhanced monitoring (linked transactions) |
| Suspicious activity | STR submission via MASAK Online 2.0 |

#### STR (Suspicious Transaction Reporting)

```
Technical Implementation:
- System: MASAK Online 2.0 (mandatory since Sept 12, 2024)
- Format: Electronic submission only
- Timeline: Submit within reasonable time of detection
- Penalty: 2x transaction amount (min 5% of transaction)
- Statute of limitations: 8 years from violation

API Integration:
POST https://masak.hmb.gov.tr/api/str
{
  "reportType": "SUSPICIOUS_TRANSACTION",
  "transactionId": "...",
  "amount": 50000,
  "currency": "TRY",
  "suspicionIndicators": ["rapid_turnover", "structured_deposits"],
  "customerDetails": {...},
  "narrative": "..."
}
```

#### Sanctions Screening

```
Required Lists:
1. MASAK Asset Freezing List (primary)
2. UN Security Council Sanctions Lists
3. OFAC SDN List (for USD operations)
4. EU Consolidated List (for EUR operations)

Screening Points:
- Customer onboarding (all names + aliases)
- Before every transaction
- Periodic re-screening of existing customers
- Real-time for high-risk customers

Matching Thresholds:
- Block: ≥70% fuzzy match score
- Flag for review: ≥40% fuzzy match score
- Search: ≥30% fuzzy match score
```

#### Record Retention

| Record Type | Retention Period |
|-------------|------------------|
| KYC documents | 8 years after relationship ends |
| Transaction records | 8 years after transaction |
| STR records | 8 years after submission |
| Sanctions screening logs | 8 years |

### 1.6 Safeguarding Requirements

#### Koruma Hesabı (Protection Account)

```
Requirements:
- Account type: Dedicated protection account
- Bank: Must be licensed under Law No. 5411
- Segregation: Separate from all other funds
- Transfer deadline: By end of next business day
- Interest: NOT permitted (nemalandırma yasak)
- TCMB blocking: End-of-day balance blocked at bank's TCMB account

Reconciliation:
- Frequency: Daily (each business day for previous day)
- Comparison: Internal ledger vs bank statement
- Customer-level tracking: Must enable individual fund tracking
- Coverage: 100% of customer funds must be in protection account
```

#### Technical Implementation

```kotlin
// Daily safeguarding reconciliation job
@Scheduled(cron = "0 0 2 * * *") // 2 AM daily
fun reconcileSafeguarding() {
    val internalBalance = ledgerService.getTotalCustomerFunds("TRY")
    val bankBalance = bankApi.getProtectionAccountBalance()

    if (internalBalance != bankBalance) {
        auditService.log(
            action = "SAFEGUARDING_DISCREPANCY",
            details = mapOf(
                "internal" to internalBalance,
                "bank" to bankBalance,
                "difference" to (internalBalance - bankBalance)
            )
        )
        alertService.sendCriticalAlert("Safeguarding discrepancy detected")
    }

    reconciliationRepository.save(ReconciliationRecord(
        date = LocalDate.now().minusDays(1),
        internalBalance = internalBalance,
        bankBalance = bankBalance,
        status = if (internalBalance == bankBalance) "MATCHED" else "DISCREPANCY"
    ))
}
```

### 1.7 KVKK Requirements

#### VERBİS Registration

```
Mandatory Registration:
- Register in Data Controllers Registry (VERBİS)
- Appoint contact person (irtibat kişisi)
- Declare: data categories, purposes, legal basis, retention periods
- Update within 7 days of any changes
```

#### Technical Controls

| Requirement | Implementation |
|-------------|----------------|
| Consent | Explicit, informed consent before processing |
| Encryption | At rest and in transit |
| Pseudonymization | Where possible |
| Access Control | Role-based, least privilege |
| Audit Trail | Log all data access |
| Data Subject Rights | Access, rectification, deletion portals |
| Breach Notification | 72 hours to KVKK |

#### Cross-Border Transfer

```
As of September 1, 2024:
- Explicit consent alone: Only valid for incidental transfers
- Regular transfers require:
  - Adequacy decision for destination country, OR
  - Appropriate safeguards (standard contractual clauses), OR
  - KVKK Board approval

For EU transfers: Use standard contractual clauses
For other countries: Obtain KVKK Board approval
```

### 1.8 Blockchain Considerations for Turkey

#### TCMB Prohibition

```
CRITICAL: TCMB Regulation on Non-Use of Crypto Assets in Payments (April 2021)

Payment and electronic money institutions CANNOT:
❌ Buy/sell crypto assets
❌ Provide crypto custody
❌ Transfer or issue crypto assets
❌ Intermediate fund transfers to/from crypto platforms

Ova's blockchain settlement is PERMITTED because:
✅ Internal infrastructure (not customer-facing)
✅ Stablecoin backed 1:1 by fiat (e-money, not crypto)
✅ Settlement layer, not trading platform
✅ Permissioned chain (not public DeFi)
```

#### Documentation for TCMB

Prepare explanation document stating:
1. Blockchain is used solely for internal settlement
2. All tokens are fully backed by fiat in safeguarding accounts
3. No customer-facing crypto trading or custody
4. Permissioned validators (not public network)
5. Complies with all e-money regulations

---

## Part 2: European Union Requirements

### 2.1 Legal Framework

| Regulation | Description |
|------------|-------------|
| EMD2 | Electronic Money Directive 2009/110/EC |
| PSD2 | Payment Services Directive (EU) 2015/2366 |
| MiCA | Markets in Crypto-Assets Regulation (EU) 2023/1114 |
| AMLD6 | 6th Anti-Money Laundering Directive |
| DORA | Digital Operational Resilience Act |
| GDPR | General Data Protection Regulation |

### 2.2 EMI License Requirements

#### Capital Requirements

| Requirement | Amount |
|-------------|--------|
| Initial Capital | €350,000 |
| Own Funds | 2% of average outstanding e-money |
| PSD3 (upcoming) | Will increase to €400,000 |

#### Safeguarding

```
Two Options:

Option 1 - Segregation:
- Deposit funds in separate accounts at authorized credit institutions
- OR invest in secure, low-risk assets held by authorized custodians
- Funds protected from EMI's creditors in insolvency

Option 2 - Insurance/Guarantee:
- Insurance policy from authorized insurer, OR
- Comparable guarantee from credit institution
- Coverage must equal outstanding e-money
```

### 2.3 MiCA E-Money Token (EMT) Requirements

Since Ova issues EUR-backed stablecoins, MiCA EMT rules apply.

#### Authorization

```
Timeline:
- Must be authorized EMI first (EMD2)
- Notify competent authority: 40 working days before offering EMTs
- Whitepaper notification: 20 working days before publication

No separate MiCA authorization - EMI license is sufficient for EMT issuance
```

#### Reserve Requirements

```
Mandatory:
- 1:1 backing with referenced fiat currency (EUR)
- At least 30% in bank deposits at credit institutions
- Remaining 70% in secure, low-risk, highly liquid assets
- Regular independent reserve audits
- Transparency reports published

Reserve Asset Custody:
- Must be held by regulated custodians
- Segregated from issuer's own assets
- Subject to independent audit
```

#### Whitepaper Requirements

```
Content (Article 6):
1. Issuer information
2. Technical description of the token
3. Rights and obligations attached to the token
4. Underlying technology description
5. Risk disclosures
6. Reserve composition details
7. Redemption procedures
8. Complaint handling procedures

Filing:
- Submit to competent authority
- Publish on issuer's website
- Keep updated (notify changes within 7 days)
```

#### Redemption Rights

```
Requirements:
- Token holders can redeem at any time
- Redemption at par value (1 token = 1 EUR)
- No arbitrary restrictions on redemption
- Maximum fee: cost of redemption (no profit margin)

Technical Implementation:
POST /api/v1/redemption
{
  "tokenId": "OvaEUR",
  "amount": 1000,
  "destinationIban": "LT..."
}

Response must be processed within 1 business day
```

#### Significant EMT Thresholds

```
Classified as "significant" if meeting ≥3 criteria:
- >10 million token holders
- >€5 billion market cap/reserves
- >2.5 million daily transactions
- >€500 million daily transaction volume
- Significant international scale
- Interconnected with financial system

Additional requirements for significant EMTs:
- Hold ≥60% of reserves in weekly liquid instruments
- Higher proportion as bank deposits in official currency
- Subject to EBA direct supervision
- Enhanced governance requirements
```

### 2.4 PSD2 Strong Customer Authentication (SCA)

#### Requirements

```
Multi-Factor Authentication:
Must use 2+ elements from:
1. Knowledge (something user knows): password, PIN, security questions
2. Possession (something user has): phone, card, OTP device
3. Inherence (something user is): fingerprint, face, voice

Dynamic Linking:
- Authentication code tied to specific transaction
- Must include: amount AND payee identifier
- Changing either invalidates authentication
```

#### Implementation

```kotlin
// SCA implementation for payments
fun initiatePayment(request: PaymentRequest, auth: AuthContext): PaymentResult {
    // Verify 2FA already completed at login (possession + knowledge)
    require(auth.has2FA) { "2FA required" }

    // Dynamic linking - generate transaction-specific code
    val dynamicCode = scaService.generateDynamicCode(
        amount = request.amount,
        payee = request.payeeIdentifier,
        userId = auth.userId
    )

    // User must confirm with the dynamic code
    return PaymentResult.AwaitingConfirmation(
        paymentId = createPendingPayment(request),
        confirmationCode = dynamicCode,
        expiresAt = Instant.now().plusMinutes(5)
    )
}

fun confirmPayment(paymentId: UUID, confirmationCode: String): PaymentResult {
    val payment = paymentRepository.findById(paymentId)

    // Verify dynamic code matches transaction details
    require(scaService.verifyDynamicCode(
        code = confirmationCode,
        amount = payment.amount,
        payee = payment.payeeIdentifier
    )) { "Invalid confirmation code" }

    return executePayment(payment)
}
```

#### SCA Exemptions

| Exemption | Conditions |
|-----------|------------|
| Low value | < €30, max 5 tx or €100 cumulative |
| Recurring | Same amount, same payee, after first SCA |
| Trusted beneficiary | User-created whitelist |
| Merchant-initiated | Card-on-file, with user consent |
| Corporate payments | Dedicated processes, secure protocols |

### 2.5 AMLD6 Requirements

#### Transaction Monitoring

```
Thresholds:
- €10,000: Full CDD required for occasional transactions
- €3,000: Limited CDD for occasional cash transactions
- €10,000: EU-wide max for cash payments

Monitoring Rules to Implement:
1. Single large transaction alerts
2. Velocity checks (many small transactions)
3. Structuring detection (just under thresholds)
4. Geographic risk (high-risk jurisdictions)
5. Peer analysis (unusual beneficiary patterns)
6. Time-of-day anomalies
```

#### Sanctions Screening

```
Required Lists:
1. EU Consolidated Financial Sanctions List (primary)
   - URL: https://webgate.ec.europa.eu/fsd/fsf
   - Format: XML, JSON
   - Update frequency: Daily

2. UN Security Council Consolidated List
3. National lists of operating countries

Screening Points:
- Onboarding (all names + aliases + date of birth)
- Before every transaction (sender + receiver)
- Periodic re-screening (recommended: monthly)
```

#### Record Retention

```
Period: 5 years after end of business relationship

Records to Retain:
- CDD documents (identity, beneficial ownership)
- Transaction records
- Risk assessments
- SAR copies and supporting documentation
- Correspondence related to AML

GDPR Interaction:
- Retention period overrides right to erasure
- Must delete after 5 years unless other legal basis
```

### 2.6 DORA Requirements (Effective Jan 2025)

#### ICT Risk Management Framework

```
Required Elements:
- Documented ICT risk management framework
- Strategies, policies, procedures, and tools
- Annual review (minimum)
- Review after: major incidents, audit findings, supervisory instructions

Framework Contents:
1. ICT risk identification procedures
2. Protection and prevention measures
3. Detection mechanisms
4. Response and recovery plans
5. Learning and evolving processes
```

#### Incident Reporting

| Report Type | Timeline |
|-------------|----------|
| Initial Notification | Within 4 hours of classification, max 24 hours from detection |
| Intermediate Report | Within 72 hours of initial notification |
| Final Report | Within 1 month of incident resolution |

```kotlin
// DORA incident reporting workflow
enum class IncidentSeverity { MAJOR, SIGNIFICANT, MINOR }

data class IctIncident(
    val id: UUID,
    val detectedAt: Instant,
    val classifiedAt: Instant?,
    val severity: IncidentSeverity,
    val description: String,
    val impactAssessment: String,
    val rootCause: String?,
    val remediationSteps: List<String>,
    val status: IncidentStatus
)

fun classifyIncident(incident: IctIncident): IncidentSeverity {
    // DORA criteria for major incident:
    // - Affects critical functions
    // - Causes significant financial loss
    // - Affects large number of customers
    // - Causes reputational damage
    // - Breaches security
}

fun submitInitialNotification(incident: IctIncident) {
    require(incident.classifiedAt != null)
    val deadline = incident.classifiedAt!!.plusHours(4)
    require(Instant.now().isBefore(deadline)) { "Missed 4-hour reporting deadline" }

    regulatoryReportingService.submit(
        type = "DORA_INITIAL_NOTIFICATION",
        incident = incident
    )
}
```

#### Third-Party Risk Management

```
Requirements (effective July 22, 2025):
- Policies for ICT services supporting critical functions
- Due diligence on critical service providers
- Key contractual provisions with ICT third-parties
- Subcontracting approval requirements
- Exit strategies for critical providers

Cloud Considerations:
- Cloud providers are ICT third-parties under DORA
- Must assess concentration risk
- Must have exit strategy
- Contract must allow supervisory access
```

#### Resilience Testing

| Test Type | Frequency |
|-----------|-----------|
| Vulnerability scanning | At least annually |
| Network security testing | At least annually |
| Gap analysis | Annually |
| Scenario-based testing | Annually |
| Threat-led penetration testing (TLPT) | Every 3 years (for significant entities) |

### 2.7 GDPR Requirements

#### Technical Measures (Article 32)

```
Required:
- Encryption of personal data (transit + rest)
- Pseudonymization where appropriate
- Confidentiality, integrity, availability, resilience
- Ability to restore data after incident
- Regular testing of security measures

Implementation:
- TLS 1.3 for all data in transit
- AES-256 for data at rest
- Database-level encryption
- Key rotation procedures
- Backup encryption
```

#### Blockchain & Right to Erasure

```
Challenge:
- GDPR Article 17: Right to erasure ("right to be forgotten")
- Blockchain: Immutable, cannot delete data

Solutions:
1. NEVER store personal data on-chain
   - Only store hashed references
   - Keep all PII off-chain in database

2. Encryption key disposal
   - Encrypt any on-chain data with user-specific key
   - "Delete" by destroying the encryption key
   - Data remains but is unreadable (functional erasure)

3. Pseudonymization
   - Use pseudonymous identifiers on-chain
   - Mapping kept off-chain and can be deleted
```

#### Data Protection Impact Assessment (DPIA)

```
Required When:
- Systematic/extensive profiling
- Large-scale processing of special category data
- Systematic monitoring of public areas
- High risk processing (2+ criteria from EDPB guidelines)

Ova Processes Requiring DPIA:
1. KYC automated verification
2. Transaction monitoring (profiling)
3. Sanctions screening
4. Blockchain settlement processing

DPIA Contents:
- Description of processing operations
- Purpose and legal basis
- Necessity and proportionality assessment
- Risk assessment to data subjects
- Measures to address risks
```

### 2.8 Passporting

#### Process

```
1. Obtain EMI license in home country (recommend: Lithuania)
2. Submit passport notification via IMAS Portal
3. Home country notifies host country competent authority
4. Can operate cross-border once notification complete

Two Options:
A. Freedom of Establishment (branch)
   - Physical presence in host country
   - Submit branch notification template

B. Freedom to Provide Services
   - No local presence
   - Submit declaration template
```

#### Using Agents/Distributors

```
Requirements:
- Notify competent authority of agent arrangements
- Changes notified 1 month before becoming effective
- Agent acts under EMI's responsibility
- EMI must ensure agent compliance
```

### 2.9 Recommended License Jurisdiction

| Country | Timeline | Pros | Cons |
|---------|----------|------|------|
| **Lithuania** | 6-9 months | Fastest, fintech-friendly, low costs | Upfront AML investment |
| Netherlands | 5-6 months | Very fast, strong DNB | Higher costs |
| Ireland | 12-18 months | Strong brand, talent pool | Slow, heavy docs |
| Estonia | 6-9 months | Digital-first | Increased scrutiny |

**Recommendation: Lithuania**
- Bank of Lithuania is supportive of fintech/crypto
- Fastest licensing in EU (6-9 months)
- Lower operational costs than Ireland/Netherlands
- Can passport to all 27 EU + EEA countries
- Good for stablecoin issuers

---

## Part 3: Technical Implementation Mapping

### Compliance Module Enhancements

```kotlin
// compliance/internal/service/ComplianceService.kt

@Service
class ComplianceService(
    private val sanctionsScreeningService: SanctionsScreeningService,
    private val transactionMonitoringService: TransactionMonitoringService,
    private val caseManagementService: CaseManagementService,
    private val regulatoryReportingService: RegulatoryReportingService
) {

    // Screen customer against all required lists
    fun screenCustomer(customer: Customer): ScreeningResult {
        val lists = determineRequiredLists(customer.region)
        // lists = [MASAK, UN, OFAC, EU] based on region and currencies

        return sanctionsScreeningService.screenAgainstLists(
            name = customer.fullName,
            dateOfBirth = customer.dateOfBirth,
            aliases = customer.aliases,
            lists = lists
        )
    }

    // Monitor transaction against regional thresholds
    fun monitorTransaction(tx: Transaction): MonitoringResult {
        val rules = getRegionalRules(tx.region)

        return transactionMonitoringService.evaluate(
            transaction = tx,
            rules = rules,
            // Turkey: 10K TL flag, 20K TL enhanced
            // EU: 10K EUR full CDD
        )
    }

    // Submit STR/SAR to appropriate authority
    fun submitSuspiciousActivityReport(case: ComplianceCase) {
        when (case.region) {
            Region.TR -> regulatoryReportingService.submitToMasak(case)
            Region.EU -> regulatoryReportingService.submitToFiu(case)
        }
    }
}
```

### Identity Module Enhancements

```kotlin
// identity/internal/service/KycService.kt

@Service
class KycService(
    private val videoKycProvider: VideoKycProvider,
    private val biometricProvider: BiometricProvider,
    private val identityVerificationProvider: IdentityVerificationProvider
) {

    // Turkey requires video KYC with biometrics
    fun initiateVideoKyc(userId: UUID, region: Region): VideoKycSession {
        return when (region) {
            Region.TR -> {
                // Turkish video KYC requirements
                videoKycProvider.createSession(
                    userId = userId,
                    requirements = VideoKycRequirements(
                        liveOperator = true,
                        biometricFacialRecognition = true,
                        documentVerification = DocumentType.TC_KIMLIK,
                        aiVerification = true // Permitted since 2023
                    )
                )
            }
            Region.EU -> {
                // EU eIDAS compatible verification
                videoKycProvider.createSession(
                    userId = userId,
                    requirements = VideoKycRequirements(
                        liveOperator = false, // Can be automated
                        biometricFacialRecognition = true,
                        documentVerification = DocumentType.EU_ID_OR_PASSPORT
                    )
                )
            }
        }
    }

    // PSD2 SCA implementation
    fun generateScaChallenge(
        userId: UUID,
        transactionAmount: BigDecimal,
        payeeIdentifier: String
    ): ScaChallenge {
        return ScaChallenge(
            userId = userId,
            dynamicCode = generateDynamicCode(transactionAmount, payeeIdentifier),
            factors = listOf(
                ScaFactor.KNOWLEDGE, // Password at login
                ScaFactor.POSSESSION  // TOTP code or push notification
            ),
            expiresAt = Instant.now().plusMinutes(5)
        )
    }
}
```

### Ledger Module Enhancements

```kotlin
// ledger/internal/service/SafeguardingService.kt

@Service
class SafeguardingService(
    private val ledgerService: LedgerService,
    private val bankApiClient: BankApiClient,
    private val reconciliationRepository: ReconciliationRepository
) {

    // Daily reconciliation (TCMB + EU requirement)
    @Scheduled(cron = "0 0 2 * * *")
    fun performDailyReconciliation() {
        for (currency in listOf("TRY", "EUR")) {
            val internalBalance = ledgerService.getTotalCustomerFunds(currency)
            val bankBalance = bankApiClient.getProtectionAccountBalance(currency)

            val record = ReconciliationRecord(
                date = LocalDate.now().minusDays(1),
                currency = currency,
                internalBalance = internalBalance,
                bankBalance = bankBalance,
                status = determineStatus(internalBalance, bankBalance)
            )

            reconciliationRepository.save(record)

            if (record.status == ReconciliationStatus.DISCREPANCY) {
                alertService.sendCriticalAlert(
                    "Safeguarding discrepancy: $currency internal=$internalBalance bank=$bankBalance"
                )
            }
        }
    }

    // Transfer to protection account by next business day (TCMB requirement)
    @Scheduled(cron = "0 0 18 * * MON-FRI")
    fun transferToProtectionAccount() {
        val pendingFunds = ledgerService.getPendingForSafeguarding()

        for ((currency, amount) in pendingFunds) {
            bankApiClient.transferToProtectionAccount(
                amount = amount,
                currency = currency,
                reference = "SAFEGUARD-${LocalDate.now()}"
            )

            ledgerService.markAsSafeguarded(amount, currency)
        }
    }
}
```

### Regulatory Reporting Module (NEW)

```kotlin
// compliance/internal/service/RegulatoryReportingService.kt

@Service
class RegulatoryReportingService(
    private val masakClient: MasakOnlineClient,
    private val euFiuClient: EuFiuClient,
    private val auditService: AuditService
) {

    // MASAK Online 2.0 STR submission
    fun submitToMasak(case: ComplianceCase) {
        val str = MasakStrReport(
            reportType = "SUSPICIOUS_TRANSACTION",
            transactionId = case.transactionId,
            amount = case.amount,
            currency = case.currency,
            suspicionIndicators = case.indicators,
            customerDetails = mapCustomerDetails(case.customer),
            narrative = case.narrative
        )

        val response = masakClient.submitStr(str)

        auditService.log(
            action = "STR_SUBMITTED",
            details = mapOf(
                "caseId" to case.id,
                "masakReference" to response.referenceNumber,
                "submittedAt" to Instant.now()
            )
        )
    }

    // DORA incident reporting
    fun submitDoraIncident(incident: IctIncident, reportType: DoraReportType) {
        val report = when (reportType) {
            DoraReportType.INITIAL -> DoraInitialNotification(
                incidentId = incident.id,
                detectedAt = incident.detectedAt,
                classifiedAt = incident.classifiedAt,
                severity = incident.severity,
                description = incident.description,
                initialImpact = incident.impactAssessment
            )
            DoraReportType.INTERMEDIATE -> DoraIntermediateReport(
                incidentId = incident.id,
                rootCauseAnalysis = incident.rootCause,
                remediationProgress = incident.remediationSteps,
                updatedImpact = incident.impactAssessment
            )
            DoraReportType.FINAL -> DoraFinalReport(
                incidentId = incident.id,
                rootCause = incident.rootCause!!,
                lessonsLearned = incident.lessonsLearned,
                preventiveMeasures = incident.preventiveMeasures
            )
        }

        euRegulatorClient.submitDoraReport(report)
    }
}
```

---

## Part 4: Compliance Checklist

### Turkey (TCMB) Pre-Launch

- [ ] Establish Anonim Şirket with 5M TL capital
- [ ] VERBİS registration complete
- [ ] ISO 27001 certification obtained
- [ ] Primary data center in Turkey operational
- [ ] Secondary/DR data center in Turkey operational
- [ ] MASAK Online 2.0 integration complete
- [ ] Sanctions screening against MASAK + UN + OFAC + EU lists
- [ ] Video KYC with biometric verification operational
- [ ] Safeguarding account at licensed bank established
- [ ] Daily reconciliation process automated
- [ ] 8-year record retention system in place
- [ ] Business continuity plan documented and tested
- [ ] Penetration test completed (< 1 year old)
- [ ] Information systems audit completed (< 2 years old)

### EU (EMI + MiCA) Pre-Launch

- [ ] EMI license application submitted (recommend Lithuania)
- [ ] €350K initial capital deposited
- [ ] MiCA EMT whitepaper prepared
- [ ] Reserve management: 30% deposits, 70% liquid assets
- [ ] Independent reserve audit completed
- [ ] PSD2 SCA implementation (2FA + dynamic linking)
- [ ] SCA exemption logic implemented
- [ ] EU sanctions list integration (daily updates)
- [ ] 5-year record retention system in place
- [ ] DORA ICT risk framework documented
- [ ] DORA incident reporting workflow implemented
- [ ] GDPR DPIA for KYC and transaction monitoring
- [ ] No personal data stored on blockchain
- [ ] Passporting notifications prepared

### Blockchain-Specific

- [ ] Document: blockchain as internal settlement only
- [ ] Multisig (3/5) for smart contract admin
- [ ] Timelock (48h) for contract upgrades
- [ ] UUPS proxy pattern for upgradeability
- [ ] No customer-facing crypto services
- [ ] Reserve attestation mechanism on-chain
- [ ] Settlement finality mechanism documented

---

## References

### Turkey
- [Law No. 6493 - mevzuat.gov.tr](https://www.mevzuat.gov.tr/mevzuat?MevzuatNo=6493&MevzuatTur=1&MevzuatTertip=5)
- [TCMB Payment Systems Regulations](https://www.tcmb.gov.tr/wps/wcm/connect/TR/TCMB+TR/Main+Menu/Banka+Hakkinda/Mevzuat/Odeme+Sistemleri/)
- [MASAK Official Site](https://masak.hmb.gov.tr/)
- [KVKK Official Site](https://www.kvkk.gov.tr/)
- [VERBİS Portal](https://verbis.kvkk.gov.tr/)
- [TÖDEB Official Site](https://todeb.org.tr/)

### European Union
- [MiCA Regulation](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32023R1114)
- [EMD2 Directive](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32009L0110)
- [PSD2 Directive](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32015L2366)
- [DORA Regulation](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32022R2554)
- [EBA MiCA Guidelines](https://www.eba.europa.eu/regulation-and-policy/asset-referenced-and-e-money-tokens-mica)
- [ESMA MiCA Guidelines](https://www.esma.europa.eu/esmas-activities/digital-finance-and-innovation/markets-crypto-assets-regulation-mica)
- [EU Consolidated Sanctions List](https://webgate.ec.europa.eu/fsd/fsf)
