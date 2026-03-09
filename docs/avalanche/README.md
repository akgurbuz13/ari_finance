# Avalanche Documentation for ARI

This directory contains exhaustive research and implementation guidance on Avalanche Network's architecture, best practices, and APIs, synthesized specifically for the ARI regulated fintech platform.

ARI operates two permissioned Avalanche L1s: **ariTR** (Turkey, BDDK/SPK/MASAK regulated) and **ariEU** (EU, MiCA/GDPR/PSD2 regulated). These L1s host TRY and EUR stablecoins and are connected via the Avalanche Teleporter cross-chain messaging protocol.

---

## Document Index

| Document | Description | Key Audience |
|----------|-------------|--------------|
| [01 - Architecture Overview](./01-architecture-overview.md) | Avalanche network stack, L1 architecture, two-chain topology, Snowman consensus | All engineers |
| [02 - Permissioned L1 Setup](./02-permissioned-l1-setup.md) | Step-by-step L1 creation, genesis configuration, precompiles, validator setup | Infrastructure |
| [03 - Smart Contract Best Practices](./03-smart-contract-best-practices.md) | Stablecoin patterns, access control, upgradability, security, testing | Solidity / Kotlin |
| [04 - ICTT Bridge Integration](./04-ictt-bridge-integration.md) | Warp messaging, Teleporter, ICTT token bridges, AriBurnMintBridge | Solidity / Kotlin |
| [05 - Gasless Transactions](./05-gasless-transactions.md) | Fee configuration, minBaseFee=1, FeeManager precompile, operator wallet gas model | Kotlin / DevOps |
| [06 - Node Infrastructure](./06-node-infrastructure.md) | AvalancheGo setup, hardware requirements, monitoring, multi-region deployment | DevOps / SRE |
| [07 - Event Listening and Indexing](./07-event-listening-indexing.md) | eth_getLogs polling, WebSocket subscriptions, outbox pattern integration | Kotlin |
| [08 - Reconciliation and Security](./08-reconciliation-security.md) | Daily reconciliation, key management, compliance (MiCA/BDDK), incident response | Kotlin / Security |
| [09 - SDK and API Reference](./09-sdk-api-reference.md) | web3j patterns, Subnet-EVM RPC methods, amount conversion, error handling | Kotlin |
| [10 - Implementation Roadmap](./10-implementation-roadmap.md) | Gap analysis, prioritized improvements, mainnet migration, performance benchmarks | All engineers |

---

## Executive Summary

### What ARI Has (Current State)

ARI's blockchain infrastructure is substantially complete at the MVP level:

- **Two live L1 chains on Fuji testnet**: ariTR (Chain ID 1279) and ariEU (Chain ID 1832), each running Subnet-EVM with `contractDeployerAllowListConfig` and `warpConfig` precompiles
- **Deployed contracts**: AriStablecoin (ariTRY + ariEUR), AriBurnMintBridge, AriTokenHome, AriTokenRemote, AriVehicleNFT, AriVehicleEscrow — all on Fuji
- **183 passing Solidity tests** covering all contract functionality including KYC allowlist, bridge operations, and vehicle escrow
- **E2E verified mint flow**: From outbox event → blockchain-service → AriStablecoin.mint() → confirmed on Fuji TR L1
- **Spring Boot blockchain-service** with full web3j integration: OutboxPollerService, ChainEventListener, MintService, BurnService, IcttBridgeService, AriBurnMintBridgeContract

### What ARI Still Needs (Critical Gaps)

Three categories of work remain before production:

**1. Genesis File Corrections (1 week)**
The current `genesis-ariTR.json` has:
- `requirePrimaryNetworkSigners: true` — incorrect for L1-to-L1 messaging; should be omitted
- `minBaseFee: 25000000000` (25 gwei) — should be `1` (1 wei) for a permissioned chain where the operator pays gas
- Missing `feeManagerConfig` precompile — needed for on-chain fee adjustment without a hard fork

**2. Security Hardening (2-4 weeks)**
- Minter key must move from config file to AWS KMS / Azure Key Vault
- Contract admin role must become a multi-sig (Gnosis Safe, 2-of-3 threshold)
- 3rd validator must be added per L1 (current 2-validator setup has zero Byzantine fault tolerance)

**3. Operational Readiness (2-4 weeks)**
- Prometheus + Grafana monitoring stack on all validator nodes
- Daily reconciliation service: `AriStablecoin.totalSupply()` vs `SUM(ledger.accounts.balance)`
- Smart contract security audit (required by both MiCA and BDDK before real funds)

---

## Key Technical Facts

### Addresses and Chain IDs

| Chain | Chain ID (Fuji) | Network |
|-------|----------------|---------|
| ariTR L1 | 1279 | Fuji testnet |
| ariEU L1 | 1832 | Fuji testnet |
| Fuji C-Chain | 43113 | Fuji testnet |
| Mainnet C-Chain | 43114 | Avalanche Mainnet |

| Contract | Address (Fuji) | Chain |
|----------|---------------|-------|
| TeleporterMessenger | `0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf` | All chains |
| TeleporterRegistry | `0xF86Cb19Ad8405AEFa7d09C778215D2Cb6eBfB228` | Fuji C-Chain |

### Precompile Addresses (Subnet-EVM)

| Precompile | Address |
|-----------|---------|
| NativeMinter | `0x0200000000000000000000000000000000000001` |
| ContractDeployerAllowList | `0x0200000000000000000000000000000000000000` |
| TxAllowList | `0x0200000000000000000000000000000000000002` |
| FeeManager | `0x0200000000000000000000000000000000000003` |
| RewardManager | `0x0200000000000000000000000000000000000004` |
| WarpMessenger | `0x0200000000000000000000000000000000000005` |

### Critical Conversions

| From | To | Formula |
|------|-----|---------|
| `BigDecimal` (ledger, 8dp) | `BigInteger` (wei, 18dp) | `amount × 10^18` |
| `BigInteger` (wei, 18dp) | `BigDecimal` (ledger, 8dp) | `amount ÷ 10^18`, round to 8dp |
| CB58 blockchain ID | bytes32 hex | Base58Check decode → strip 4-byte checksum |
| Reconciliation tolerance | | `0.00000001` (8th decimal place) |

---

## Avalanche Tooling Reference

| Tool | Purpose | Status |
|------|---------|--------|
| `avalanche-cli` | Legacy L1 creation tool | DEPRECATED — do not use |
| `platform` (Platform CLI) | P-Chain ops: keys, transfers, subnets, L1 validators | Current |
| Builder Console (build.avax.network/console) | Web GUI for L1 management, Teleporter, node setup | Current |
| Prometheus + Grafana | Node monitoring | Deploy via monitoring-installer.sh |
| Hardhat | Solidity contract development, testing, deployment | Current |
| web3j | Java/Kotlin EVM client library | Current (blockchain-service) |

---

## Important References

### Internal Documents

- `PROGRESS.md` — Current implementation status (read before starting any work)
- `CLAUDE.md` — Engineering conventions and patterns
- `docs/FUJI_L1_SETUP_GUIDE.md` — Operational guide for Fuji L1 setup (Platform CLI + Builder Console)
- `docs/adr/001-multi-region-data-residency.md` — Multi-region architecture decision record
- `docs/compliance.md` — TR/EU regulatory requirements

### Official Avalanche Sources

- Primary docs: `https://build.avax.network/docs`
- Subnet-EVM precompiles: `https://build.avax.network/docs/avalanche-l1s/precompiles`
- Platform CLI: `https://build.avax.network/docs/tooling/platform-cli`
- Subnet-EVM RPC: `https://build.avax.network/docs/rpcs/subnet-evm`
- Node infrastructure: `https://build.avax.network/docs/nodes`
- Data API: `https://build.avax.network/docs/api-reference/data-api`
- ICM Contracts (Teleporter): `https://github.com/ava-labs/icm-contracts`
- ICM Services (Relayer): `https://github.com/ava-labs/icm-services`

### ACP Documents (Background Reading)

- ACP-77 (Reinventing Subnets): `https://build.avax.network/docs/acps/77-reinventing-subnets`
- ACP-191 (Seamless L1 Creation): `https://build.avax.network/docs/acps/191-seamless-l1-creation`
- ACP-103 (Dynamic Fees): `https://build.avax.network/docs/acps/103-dynamic-fees`
