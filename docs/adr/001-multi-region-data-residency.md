# ADR-001: Multi-Region Data Residency

## Status: Accepted (for production roadmap)

## Context

Turkish banking law (BDDK Article 11, Regulation on Internal Systems of Banks) requires financial data to reside in Turkey. EU GDPR requires EU citizen data protection with adequate safeguards. Turkey lacks an EU adequacy decision under GDPR, meaning data cannot freely flow between regions.

A single database holding both TR and EU user data violates both regulatory frameworks.

**MVP approach**: Single DB, single backend — acceptable for demo and hackathon submission.

## Decision

Deploy separate core-banking instances per regulatory jurisdiction for production.

### Production Architecture

```
┌─────────────────────────┐              ┌─────────────────────────┐
│ TR Region (Istanbul)     │              │ EU Region (Frankfurt)    │
│ ├─ core-banking (TR)     │  ◄── API ──►│ ├─ core-banking (EU)     │
│ ├─ postgres (TR users)   │              │ ├─ postgres (EU users)   │
│ ├─ redis (TR sessions)   │              │ ├─ redis (EU sessions)   │
│ └─ blockchain-svc (TR)   │              │ └─ blockchain-svc (EU)   │
└────────────┬────────────┘              └────────────┬────────────┘
             │                                        │
             ▼                                        ▼
       TR L1 (ariTRY)  ◄── Teleporter ──►  EU L1 (ariEUR)
```

### Key Design Decisions

1. **Same codebase, regional deployments** — one Spring Boot app deployed per region
2. **Region routing** — DNS/load balancer routes users to their regional instance
3. **Cross-region payment API** — for cross-border transfers, TR backend calls EU backend's `/api/internal/cross-region/*` endpoints (replaces direct DB write)
4. **Shared blockchain layer** — blockchain data is inherently public; blockchain-service can be per-region or centralized with multi-chain support
5. **Outbox pattern already supports this** — outbox events are regional; cross-region events become API calls instead of DB inserts

### What Changes from MVP to Production

| Component | MVP | Production |
|-----------|-----|------------|
| Database | Single PostgreSQL | One per region (TR: Istanbul, EU: Frankfurt) |
| Backend | Single instance | One per region, same codebase |
| User routing | N/A | Region field -> DNS routing |
| Cross-border | Direct DB postings | TR instance -> API call -> EU instance |
| Blockchain service | Single instance | Per-region or shared |
| Web app | Single deployment | CDN with region-aware API base URL |

### Migration Path

1. Add `/api/internal/cross-region/` endpoints for inter-region operations
2. Extract shared schema (outbox events) into API contracts
3. Deploy regional instances with regional databases
4. Add cross-region API authentication (mTLS or shared secret)
5. Update `CrossBorderTransferService` to call remote region API instead of writing to local DB for foreign-region accounts

## Regulatory References

- Turkey BDDK: Article 11, Regulation on Internal Systems of Banks
- Turkey KVKK: Law No. 6698, cross-border transfer mechanisms (SCCs/BCRs)
- EU GDPR: Articles 44-49, international data transfers
- Turkey Banking Law No. 5411: Article 73, banking secrecy
