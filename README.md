# ARI Fintech Platform

**Cross-border fintech on Avalanche L1s — real compliance, real on-chain settlement, global ambition**

ARI is a regulated cross-border payments and asset tokenization platform currently focused on Turkey and the EU, with a vision for global expansion. We are building a real enterprise fintech solution from the ground up with blockchain at its core — not retrofitting crypto onto legacy infrastructure, but designing every layer (ledger, compliance, settlement, custody) to work natively with on-chain enforcement. Each jurisdiction gets its own permissioned Avalanche L1 blockchain, connected by Avalanche ICM (Teleporter) for cross-chain messaging. The MVP launches with two L1s (ariTR for Turkey, ariEU for Europe) — the architecture is designed so that adding new jurisdictions means deploying a new L1 and registering it with the existing bridge network.

> **Live on Fuji testnet. 20 smart contract instances deployed across 2 L1s. 183 Solidity tests passing. Full-stack integration: Kotlin backend + Next.js frontend + on-chain settlement.**

> **Live demo**: [arifinance.co](https://arifinance.co) — The frontend is deployed and browsable. The backend (Kotlin/Spring Boot + PostgreSQL + Redis + blockchain service with Fuji RPC connectivity) requires infrastructure that goes beyond static hosting, so the live site showcases the UI and flows without live API connectivity. For the full working stack, run locally with `docker compose up -d` and `./gradlew :core-banking:bootRun` (see [Run Locally](#run-locally)).

---

## The Problem

There are fundamental inefficiencies in how people move money and transfer ownership of valuable assets, and these problems hit hardest in emerging markets like Turkey.

**Cross-border payments are broken.** Sending foreign currency across borders still relies on the SWIFT network, which charges $25-80 per transfer and takes 1-5 business days to settle. Even within Turkey, transferring foreign currency between two domestic banks often requires physically withdrawing cash from an ATM and depositing it at the other bank — because direct inter-bank FX transfers either don't exist or carry prohibitive fees.

**Vehicle ownership transfer is broken.** Buying or selling a car in Turkey requires going to a notary, waiting in line for hours, and paying hundreds or sometimes thousands of Turkish lira for what is essentially someone stamping a piece of paper. Worse, most notaries do not verify that funds have actually arrived from the buyer to the seller before completing the title transfer — leading to fraud, scams, and in some cases, people committing crimes in desperation. A small number of notaries offer secure payment verification, but these services cost thousands of lira on top of already high notary fees, making them inaccessible to most people.

**Our mission is not just about saving money — it's about protecting people.**

---

## Why Avalanche L1s?

Turkey (BDDK/MASAK) and the EU (PSD2/MiCA) have incompatible regulatory frameworks. Assets, user data, and compliance rules cannot be mixed. Existing solutions either ignore this (risky) or use a single centralized database (no transparency).

**Our solution**: One L1 per jurisdiction. Each chain has its own validator set, governance, and compliance rules. Cross-border transfers go through explicit bridges with full audit trails. On-chain KYC enforcement means compliance isn't just a backend check — it's cryptographically enforced at the EVM level.

**Why two chains instead of one?** Regulatory isolation. Turkey's BDDK requires transaction processing within Turkish jurisdiction. EU's GDPR/MiCA has its own data residency requirements. Two L1s keep these boundaries clean while enabling instant cross-border settlement via Teleporter — replacing the days-long SWIFT process with seconds-long on-chain finality.

**Why Avalanche specifically?** Avalanche L1s give us permissioned EVM chains with sub-second finality, native cross-chain messaging (Teleporter/ICM), and the ability to customize every aspect of the chain — from fee structure (gasless UX via 1 gwei min base fee) to access control (transaction and contract deployment allowlists at the precompile level). No other platform offers this combination of permissioning, interoperability, and EVM compatibility.

---

## Live Chains on Fuji Testnet

### ariTR L1 (Turkey) — [Explorer](https://build.avax.network/explorer/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM)

| Property | Value |
|----------|-------|
| Chain ID | `1279` |
| Blockchain ID | `2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM` |
| Blockchain ID (bytes32) | `0xb5a82a53e6366b84f980e4d2f13e583ca02f10eaf1ead220e23d036574799345` |
| RPC URL | `https://nodes-prod.18.182.4.86.sslip.io/ext/bc/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/rpc` |
| Subnet ID | `2Sw7W5coLCB4EZRADRyfTCuPBF5QqxMxj3jL8cUWPpCdso1MGX` |
| Consensus | Proof of Authority (managed validator via Builder Console) |
| Native Token | ARI (gas token, 1 gwei min base fee) |

### ariEU L1 (Europe) — [Explorer](https://build.avax.network/explorer/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt)

| Property | Value |
|----------|-------|
| Chain ID | `1832` |
| Blockchain ID | `7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt` |
| Blockchain ID (bytes32) | `0x0ea0530c367859873c37829bdbc918ad3da9f4c7bed68d083275efc310ab03f4` |
| RPC URL | `https://nodes-prod.18.182.4.86.sslip.io/ext/bc/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt/rpc` |
| Subnet ID | `5KvzdVWjkZu6YuFrWVKhpFq2ZyGqQgHEozgvJFhzJRdBfba6M` |
| Consensus | Proof of Authority (managed validator via Builder Console) |
| Native Token | ARI (gas token, 1 gwei min base fee) |

### Chain Configuration (Both L1s)

Both chains are fully permissioned enterprise L1s created via [Avalanche Builder Console](https://build.avax.network/console):

- **Contract Deployer Allowlist**: ON — only whitelisted addresses can deploy contracts
- **Transaction Allowlist**: ON — only whitelisted addresses can submit transactions
- **NativeMinter Precompile**: ON — admin can mint gas tokens without external funding
- **Warp Messaging**: Enabled for Avalanche ICM / Teleporter cross-chain communication
- **ICM/Teleporter**: Fully set up with managed relayers for bidirectional messaging
- **Gasless UX**: Users never touch gas tokens. The platform operator pays all gas at 1 gwei min base fee

Genesis configurations: [`ariTR_genesis.json`](./ariTR_genesis.json) | [`ariEU_genesis.json`](./ariEU_genesis.json)

---

## Deployed Smart Contracts

All contracts deployed on 2026-03-09. Deployer: `0xe9ce1Cd8179134B162581BEb7988EBD2e2400503`

### ariTR L1 — Turkey Jurisdiction (Chain ID 1279) — [Explorer](https://build.avax.network/explorer/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM)

| Contract | Address | Purpose |
|----------|---------|---------|
| **ariTRY Stablecoin** (Proxy) | [`0x63d1a883130feeB9e863A4Ed974Dd1448A43aaa6`](https://build.avax.network/explorer/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/address/0x63d1a883130feeB9e863A4Ed974Dd1448A43aaa6) | KYC-enforced Turkish Lira stablecoin (UUPS upgradeable) |
| **ariEUR Stablecoin** (Proxy) | [`0x78870378c9A1A3458B2188f3F6c96cD406A85DC7`](https://build.avax.network/explorer/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/address/0x78870378c9A1A3458B2188f3F6c96cD406A85DC7) | Cross-currency: ariEUR deployed natively on TR L1 |
| AriTokenHome | [`0x1090B43270a8693C111fEe23D81FAcCC8Eee7A76`](https://build.avax.network/explorer/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/address/0x1090B43270a8693C111fEe23D81FAcCC8Eee7A76) | ICTT: locks ariTRY for cross-chain bridging |
| AriTokenRemote | [`0xe94BB4716255178e01bf34d1aE6A02edADc117B5`](https://build.avax.network/explorer/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/address/0xe94BB4716255178e01bf34d1aE6A02edADc117B5) | ICTT: receives cross-chain tokens from EU |
| AriBridgeAdapter | [`0xcCf46814bdA0cA12e997bAC9CEc3Dc90B104e0C2`](https://build.avax.network/explorer/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/address/0xcCf46814bdA0cA12e997bAC9CEc3Dc90B104e0C2) | Orchestrates ICTT bridge operations |
| AriBurnMintBridge (TRY) | [`0x74CDb2b07e6e6441b71348E7812E7208eF909f24`](https://build.avax.network/explorer/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/address/0x74CDb2b07e6e6441b71348E7812E7208eF909f24) | Same-currency cross-border: burn ariTRY on TR, mint ariTRY on EU |
| AriBurnMintBridge (EUR) | [`0xA2Aa53A97A848343F7D399e186D237E905888Df4`](https://build.avax.network/explorer/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/address/0xA2Aa53A97A848343F7D399e186D237E905888Df4) | Same-currency cross-border: burn ariEUR on TR, mint ariEUR on EU |
| AriVehicleNFT | [`0xF66B3253eBe361D2A3E14B45C82Acd2d5a1C44c1`](https://build.avax.network/explorer/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/address/0xF66B3253eBe361D2A3E14B45C82Acd2d5a1C44c1) | ERC-721 vehicle ownership NFT |
| AriVehicleEscrow | [`0xb38f33015ED35E73b141f1169D78BEF379A57E2F`](https://build.avax.network/explorer/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/address/0xb38f33015ED35E73b141f1169D78BEF379A57E2F) | Atomic swap: ariTRY payment + NFT transfer (v2, no approve required) |
| AriTimelock | [`0xde2E9ADbd664bA2266300349920c4FC9cAEBeAeE`](https://build.avax.network/explorer/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/address/0xde2E9ADbd664bA2266300349920c4FC9cAEBeAeE) | Governance timelock (1h delay on testnet) |
| KycAllowList | [`0xD76af0Ef48d735BAB56302388A44B080B8A313fE`](https://build.avax.network/explorer/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/address/0xD76af0Ef48d735BAB56302388A44B080B8A313fE) | On-chain KYC verification registry |

### ariEU L1 — Europe Jurisdiction (Chain ID 1832) — [Explorer](https://build.avax.network/explorer/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt)

| Contract | Address | Purpose |
|----------|---------|---------|
| **ariEUR Stablecoin** (Proxy) | [`0xd354bb151EAbAd1BfaaE9a36c32e3e2CB16Ae232`](https://build.avax.network/explorer/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt/address/0xd354bb151EAbAd1BfaaE9a36c32e3e2CB16Ae232) | KYC-enforced Euro stablecoin (UUPS upgradeable) |
| **ariTRY Stablecoin** (Proxy) | [`0xcCf46814bdA0cA12e997bAC9CEc3Dc90B104e0C2`](https://build.avax.network/explorer/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt/address/0xcCf46814bdA0cA12e997bAC9CEc3Dc90B104e0C2) | Cross-currency: ariTRY deployed natively on EU L1 |
| AriTokenHome | [`0xD76af0Ef48d735BAB56302388A44B080B8A313fE`](https://build.avax.network/explorer/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt/address/0xD76af0Ef48d735BAB56302388A44B080B8A313fE) | ICTT: locks ariEUR for cross-chain bridging |
| AriTokenRemote | [`0x444c7316C7DF741ed7bf470c4B0b56c923AB08bB`](https://build.avax.network/explorer/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt/address/0x444c7316C7DF741ed7bf470c4B0b56c923AB08bB) | ICTT: receives cross-chain tokens from TR |
| AriBridgeAdapter | [`0x63d1a883130feeB9e863A4Ed974Dd1448A43aaa6`](https://build.avax.network/explorer/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt/address/0x63d1a883130feeB9e863A4Ed974Dd1448A43aaa6) | Orchestrates ICTT bridge operations |
| AriBurnMintBridge (EUR) | [`0x1C3C34dAe1503E64033Ec99A4f2a61F32AA2Be0E`](https://build.avax.network/explorer/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt/address/0x1C3C34dAe1503E64033Ec99A4f2a61F32AA2Be0E) | Same-currency cross-border: burn ariEUR on EU, mint ariEUR on TR |
| AriBurnMintBridge (TRY) | [`0x5EB99416745b310b6D091E7Cb91C3B0297788144`](https://build.avax.network/explorer/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt/address/0x5EB99416745b310b6D091E7Cb91C3B0297788144) | Same-currency cross-border: burn ariTRY on EU, mint ariTRY on TR |
| AriTimelock | [`0x3a6b3CFbC5EC7D61E6BDD57Ba15AEa8155d5798f`](https://build.avax.network/explorer/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt/address/0x3a6b3CFbC5EC7D61E6BDD57Ba15AEa8155d5798f) | Governance timelock (1h delay on testnet) |
| KycAllowList | [`0xA4Fc63413DDd3696ea8E295f73e4F52195101a35`](https://build.avax.network/explorer/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt/address/0xA4Fc63413DDd3696ea8E295f73e4F52195101a35) | On-chain KYC verification registry |

### Cross-Chain Infrastructure

| Component | Value |
|-----------|-------|
| TeleporterMessenger | `0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf` (canonical, on both L1s) |
| ICM Relayers | 2 managed relayers (ariTR ↔ ariEU, bidirectional) |
| Bridge Cross-Registration | All 4 burn-mint bridges cross-registered as partners |

---

## On-Chain Features

### 1. KYC-Enforced Stablecoins (ariTRY + ariEUR)

`AriStablecoinUpgradeable` — not a typical ERC-20. Compliance is enforced at the EVM level, not just in the backend:

- **KYC Allowlist**: Every `transfer()`, `mint()`, and `burn()` checks that both sender and recipient are on the allowlist. Unverified addresses **cannot hold or receive tokens**. This is enforced in the `_update()` hook — there is no way to bypass it.
- **Freeze/Unfreeze**: Sanctions screening can immediately freeze any address. Tokens remain but cannot move.
- **MINTER_ROLE**: Only the platform's operational key can create or destroy tokens (backing fiat deposits/withdrawals).
- **UUPS Upgradeable**: ERC-1967 proxy pattern. Contract logic can be upgraded without migrating balances.
- **Emergency Pause**: Circuit breaker that halts all token operations platform-wide.
- **Supply Cap**: Optional cap to limit total token issuance.

```solidity
// From AriStablecoinUpgradeable.sol — every transfer checks KYC
function _update(address from, address to, uint256 value) internal override whenNotPaused {
    if (from != address(0)) {
        require(allowlisted[from], "AriStablecoin: sender not KYC verified");
        require(!frozen[from], "AriStablecoin: sender account frozen");
    }
    if (to != address(0)) {
        require(allowlisted[to], "AriStablecoin: recipient not KYC verified");
        require(!frozen[to], "AriStablecoin: recipient account frozen");
    }
    super._update(from, to, value);
}
```

**Verify on-chain** (requires [Foundry `cast`](https://book.getfoundry.sh/getting-started/installation)):
```bash
# Check ariTRY token name on TR L1
cast call 0x63d1a883130feeB9e863A4Ed974Dd1448A43aaa6 "name()(string)" \
  --rpc-url https://nodes-prod.18.182.4.86.sslip.io/ext/bc/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/rpc
# Returns: "ARI Turkish Lira"

# Check ariEUR token name on EU L1
cast call 0xd354bb151EAbAd1BfaaE9a36c32e3e2CB16Ae232 "name()(string)" \
  --rpc-url https://nodes-prod.18.182.4.86.sslip.io/ext/bc/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt/rpc
# Returns: "ARI Euro"

# Check if the deployer is KYC-allowlisted
cast call 0x63d1a883130feeB9e863A4Ed974Dd1448A43aaa6 "allowlisted(address)(bool)" \
  0xe9ce1Cd8179134B162581BEb7988EBD2e2400503 \
  --rpc-url https://nodes-prod.18.182.4.86.sslip.io/ext/bc/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/rpc

# Check total supply
cast call 0x63d1a883130feeB9e863A4Ed974Dd1448A43aaa6 "totalSupply()(uint256)" \
  --rpc-url https://nodes-prod.18.182.4.86.sslip.io/ext/bc/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/rpc
```

### 2. Same-Currency Cross-Border Settlement (AriBurnMintBridge)

The flagship cross-border feature. Burns native ariTRY on the source chain, sends a Teleporter message, and mints native ariTRY on the destination chain. **No wrapped tokens** — the receiver gets real ariTRY/ariEUR on both chains. This replaces SWIFT's 1-5 day settlement with seconds-long on-chain finality.

```
Source Chain (TR L1):                         Dest Chain (EU L1):
┌──────────────────────┐    Teleporter    ┌──────────────────────┐
│ AriBurnMintBridge    │───── ICM/AWM ───>│ AriBurnMintBridge    │
│   burn(ariTRY, 1000) │                  │   mint(ariTRY, 1000) │
│   emit BurnAndBridge │                  │   emit MintFromBridge│
└──────────────────────┘                  └──────────────────────┘
```

- **Partner Registration**: Each bridge explicitly registers its partner on the other chain. Only messages from registered partners are accepted.
- **Replay Protection**: Defense-in-depth — message hashes are tracked even though Teleporter has its own replay protection.
- **Bidirectional**: 4 bridges total — TRY bridges and EUR bridges on both chains, all cross-registered.

**Verify on-chain:**
```bash
# Check registered partner for TR TRY bridge → EU TRY bridge
cast call 0x74CDb2b07e6e6441b71348E7812E7208eF909f24 \
  "registeredPartners(bytes32)(address)" \
  0x0ea0530c367859873c37829bdbc918ad3da9f4c7bed68d083275efc310ab03f4 \
  --rpc-url https://nodes-prod.18.182.4.86.sslip.io/ext/bc/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/rpc
# Returns: 0x5EB99416745b310b6D091E7Cb91C3B0297788144 (EU TRY bridge)
```

### 3. Cross-Currency FX Settlement

For FX transfers (TRY to EUR), the backend handles currency conversion off-chain via the double-entry ledger, then settles on-chain with native stablecoins on each respective chain. **No wrapped tokens are involved** — users only ever hold native ariTRY and ariEUR.

```
User: "Transfer 1,000 TRY → EUR"
  │
  ├─ Backend: FX quote (1,000 TRY → 34.50 EUR at market rate)
  │           Double-entry ledger postings across both currencies
  │
  ├─ TR L1: Burn 1,000 ariTRY from sender's custodial wallet
  │
  └─ EU L1: Mint 34.50 ariEUR to receiver's custodial wallet
```

Both ariTRY and ariEUR are deployed natively on both chains (see contract tables above), so each chain can settle either currency independently. The FX rate is locked at quote time with a 30-second TTL, protecting users from slippage.

### ICTT Bridge Infrastructure (AriTokenHome + AriTokenRemote)

In addition to the burn/mint bridges used for current settlement, ARI has deployed the full Avalanche ICTT (Inter-Chain Token Transfer) bridge stack on both chains. These contracts enable token locking on a home chain and minting of wrapped representations on a remote chain — infrastructure designed for future features like decentralized cross-chain liquidity pools and atomic FX swaps.

- **AriTokenHome**: Locks native tokens, sends Teleporter messages. Daily limits (default 10M/day), emergency pause, emergency withdrawal.
- **AriTokenRemote**: Receives Teleporter messages, mints wrapped tokens with full KYC/freeze enforcement.
- **AriBridgeAdapter**: Orchestrates the lock → message → confirm flow with fee handling.

These contracts are deployed, tested (183 tests), and production-ready — representing additional on-chain infrastructure beyond the current settlement flow.

### 4. Vehicle Securitization (AriVehicleNFT + AriVehicleEscrow)

This is the feature that addresses a real human problem. In Turkey, buying a car means going to a notary, paying steep fees, and trusting that the other party won't cheat you. Most notaries don't verify payment before transferring the title. People have lost their cars, their money, and worse.

ARI replaces this with on-chain vehicle ownership NFTs and atomic escrow swaps — making it impossible for either party to cheat.

**AriVehicleNFT** (ERC-721):
- Each token represents a registered vehicle with hashed VIN and plate number
- **VIN Uniqueness**: Same vehicle cannot be registered twice (`vinHashUsed` mapping)
- **Transfer Restrictions**: Direct `transfer()` is blocked. Vehicles can **only** change hands through approved escrow contracts — preventing unauthorized ownership changes
- **KYC Allowlist**: Only verified addresses can hold vehicle NFTs

**AriVehicleEscrow** — atomic swap state machine:
1. **Create**: Operator creates escrow with seller, buyer, vehicle NFT, and price. No explicit ERC-721 `approve()` needed — the NFT contract's `_isAuthorized` override allows approved escrow contracts to transfer directly.
2. **Fund**: Buyer's ariTRY is minted to the escrow contract
3. **Dual Confirm**: Both seller and buyer must confirm
4. **Execute**: In a single atomic transaction: ariTRY goes to seller, 50 TRY fee goes to treasury, NFT transfers to buyer. **Neither party can cheat — payment and ownership transfer happen atomically.**
5. **Cancel**: If either party backs out, escrowed ariTRY is burned and refunded off-chain

```
┌─────────────┐     ┌──────────────────────┐     ┌──────────────┐
│   Seller     │     │   AriVehicleEscrow   │     │    Buyer     │
│ (owns NFT)  │     │  (holds ariTRY funds) │     │ (has ariTRY) │
└──────┬───────┘     └──────────┬───────────┘     └──────┬───────┘
       │                        │<── fund(ariTRY) ────────┤
       │  sellerConfirm()       │                         │
       ├───────────────────────>│                         │
       │                        │<── buyerConfirm() ──────┤
       │                        │                         │
       │    ╔═══ ATOMIC SWAP ═══╗                        │
       │    ║ ariTRY → seller   ║                        │
       │    ║ 50 TRY → treasury ║                        │
       │    ║ NFT → buyer       ║                        │
       │    ╚═══════════════════╝                        │
```

**Verify on-chain:**
```bash
# Check AriVehicleNFT contract name
cast call 0xF66B3253eBe361D2A3E14B45C82Acd2d5a1C44c1 "name()(string)" \
  --rpc-url https://nodes-prod.18.182.4.86.sslip.io/ext/bc/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/rpc
# Returns: "ARI Vehicle"

# Check AriVehicleEscrow fee amount (50 TRY in wei)
cast call 0xb38f33015ED35E73b141f1169D78BEF379A57E2F "FEE_AMOUNT()(uint256)" \
  --rpc-url https://nodes-prod.18.182.4.86.sslip.io/ext/bc/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/rpc
# Returns: 50000000000000000000 (50 * 10^18)
```

### 5. Governance (AriTimelock)

All critical admin operations go through `AriTimelock` (OpenZeppelin `TimelockController`):
- 48-hour delay in production, 1-hour on testnet
- Prevents rogue admin actions — community/regulators can review proposed changes
- Contract upgrades, role changes, and parameter modifications must be timelocked

### 6. Permissioned Chain Architecture

Both L1s use Subnet-EVM precompiles for enterprise-grade permissioning:

| Precompile | Purpose |
|-----------|---------|
| **ContractDeployerAllowList** | Only authorized addresses can deploy contracts |
| **TransactionAllowList** | Only authorized addresses can submit transactions |
| **NativeMinter** | Admin can mint gas tokens — no external gas token needed |
| **FeeManager** | Adjust fee parameters without hard fork |
| **Warp** | Enables ICM/Teleporter cross-chain messaging |

---

## Architecture

```
                          ARI Platform
  ┌──────────────────────────────────────────────────────┐
  │                                                      │
  │   Web App (Next.js)  ──>  Core Banking (Spring Boot) │
  │   Admin Console          │ Auth, KYC, Ledger,       │
  │   Mobile (Flutter)       │ Payments, FX, Compliance │
  │                          │                           │
  │                    Outbox Events (PostgreSQL)         │
  │                          │                           │
  │                 Blockchain Service (Spring Boot)      │
  │                  │ Mint, Burn, Bridge, Wallet,       │
  │                  │ Event Listener, Settlement        │
  └──────────────────┼───────────────────────────────────┘
                     │
    ┌────────────────┴────────────────────┐
    ▼                                     ▼
┌── TR L1 (1279) ────────┐    ┌── EU L1 (1832) ────────┐
│                         │    │                         │
│  ariTRY  (KYC ERC-20)  │    │  ariEUR  (KYC ERC-20)  │
│  ariEUR  (cross-ccy)   │    │  ariTRY  (cross-ccy)   │
│  BurnMintBridge ×2      │◄──►│  BurnMintBridge ×2      │
│  ICTT TokenHome/Remote  │ ICM│  ICTT TokenHome/Remote  │
│  VehicleNFT + Escrow    │    │                         │
│  Timelock + KycAllowList│    │  Timelock + KycAllowList│
│                         │    │                         │
└─────────────────────────┘    └─────────────────────────┘
          Teleporter / AWM Relayer (managed)
```

**Key design decisions:**
- **Outbox pattern**: Core banking publishes events to a PostgreSQL outbox table. Blockchain service polls and processes them. Guarantees exactly-once delivery and crash recovery.
- **Custodial wallets**: Users don't manage keys. Wallets are deterministically derived (HMAC-SHA256) from a master key + userId. Auto-KYC-allowlisted on creation.
- **Double-entry ledger**: Every financial operation creates balanced debit/credit entries. On-chain settlement is a confirmation step, not the source of truth for balances.

---

## Smart Contracts

11 deployable Solidity contracts (0.8.24) + interfaces and test mocks, **183 tests passing**, OpenZeppelin 5.x:

| Contract | Lines | Purpose |
|----------|-------|---------|
| `AriStablecoinUpgradeable` | 253 | UUPS ERC-20 with KYC allowlist, freeze, pause, mint/burn, supply cap |
| `AriStablecoin` | ~200 | Non-upgradeable stablecoin for testing |
| `AriTokenHome` | 317 | ICTT home: lock tokens, Teleporter message, daily limits, emergency withdraw |
| `AriTokenRemote` | 308 | ICTT remote: receive cross-chain tokens, KYC-enforced, freeze capability |
| `AriBridgeAdapter` | ~250 | Orchestrates ICTT bridge: approve → lock → message → confirm |
| `AriBurnMintBridge` | 161 | Same-currency bridge: burn → Teleporter → mint, replay protection |
| `AriVehicleNFT` | 145 | ERC-721 vehicle ownership, VIN uniqueness, escrow-only transfers |
| `AriVehicleEscrow` | 299 | Atomic swap: dual confirmation, 50 TRY fee, cancel with burn |
| `AriTimelock` | 62 | OpenZeppelin TimelockController (48h prod, 1h testnet) |
| `KycAllowList` | 53 | Standalone KYC registry with expiration |
| `ValidatorManager` | ~100 | L1 validator set management |
| `IcttInterfaces` | ~50 | Teleporter interface definitions |
| `MockTeleporter` | ~100 | Test mock for cross-chain messaging |

Source code: [`contracts/contracts/`](./contracts/contracts/)

```bash
cd contracts && npx hardhat test       # 183 passing
cd contracts && npx hardhat coverage   # Coverage report
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Smart Contracts | Solidity 0.8.24, Hardhat, OpenZeppelin 5.x, UUPS Proxy |
| Backend | Kotlin, Spring Boot 3.2, JdbcTemplate, PostgreSQL 16, Redis 7 |
| Blockchain Integration | Web3j 4.10, outbox pattern, receipt polling, event listening |
| Frontend | Next.js 14, React 18, Tailwind CSS, Framer Motion |
| Mobile | Flutter 3.2+ (Dart) |
| Cross-Chain | Avalanche ICM (Teleporter), AWM Relayer, ICTT Protocol |
| Infrastructure | Docker Compose, Platform CLI, Builder Console, Fuji testnet |

---

## Run Locally

### Prerequisites
- Docker (for PostgreSQL + Redis)
- JDK 21
- Node.js 20
- [Foundry](https://book.getfoundry.sh/getting-started/installation) (optional, for `cast` on-chain verification commands)

### Quick Start
```bash
# 1. Clone and start infrastructure
git clone https://github.com/akgurbuz13/ari_finance.git
cd ari_finance
docker compose up -d   # PostgreSQL 16 + Redis 7

# 2. Start core banking (terminal 1)
./gradlew :core-banking:bootRun
# API available at http://localhost:8080

# 3. Start blockchain service with Fuji profile (terminal 2)
# Set DEPLOYER_PRIVATE_KEY in .env.fuji or environment
./gradlew :blockchain-service:bootRun --args='--spring.profiles.active=fuji'
# Blockchain service at http://localhost:8081

# 4. Start web app (terminal 3)
cd web && npm install && npm run dev
# Web app at http://localhost:3000
```

### Run Smart Contract Tests
```bash
cd contracts
npm install
npx hardhat test          # 183 tests
npx hardhat coverage      # Coverage report
npx hardhat compile       # Compile all contracts
```

### Run Backend Tests
```bash
# Core banking tests
./gradlew :core-banking:test

# Blockchain service tests (requires Java 21)
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :blockchain-service:test --no-daemon
```

---

## Repository Structure

```
ari/
├── contracts/                 # Solidity smart contracts + Hardhat (183 tests)
│   ├── contracts/
│   │   ├── token/             # AriStablecoin, AriStablecoinUpgradeable
│   │   ├── bridge/            # AriTokenHome, AriTokenRemote, AriBridgeAdapter,
│   │   │                      # AriBurnMintBridge, IcttInterfaces
│   │   ├── vehicle/           # AriVehicleNFT, AriVehicleEscrow
│   │   ├── governance/        # AriTimelock, ValidatorManager
│   │   ├── access/            # KycAllowList
│   │   └── mocks/             # MockTeleporter (for testing)
│   ├── scripts/               # Deploy, configure-bridge, cross-register
│   ├── test/                  # Full test suite
│   └── deployments/           # Deployment records (addresses, timestamps)
├── core-banking/              # Kotlin/Spring Boot modular monolith
│   ├── identity/              # Auth, KYC, 2FA, RBAC
│   ├── ledger/                # Double-entry accounting engine
│   ├── payments/              # Domestic, cross-border (FX + same-currency)
│   ├── fx/                    # FX rates + quotes (30s TTL)
│   ├── vehicle/               # Vehicle registration + escrow services
│   └── shared/                # Outbox, security, audit, compliance
├── blockchain-service/        # Blockchain integration service
│   ├── contract/              # Web3j contract wrappers
│   ├── bridge/                # ICTT + BurnMint bridge orchestration
│   ├── settlement/            # Mint/Burn services
│   ├── wallet/                # Custodial wallet management (HD derivation)
│   └── outbox/                # Event processing from core-banking
├── web/                       # Next.js customer web app
├── admin-console/             # Next.js admin dashboard
├── mobile/                    # Flutter mobile app
├── scripts/                   # Run, demo, deployment scripts
├── infra/                     # K8s manifests, Terraform
├── docs/                      # Architecture, compliance, Avalanche docs
│   └── avalanche/             # 10-part Avalanche integration guide
├── ariTR_genesis.json         # Genesis config for TR L1
└── ariEU_genesis.json         # Genesis config for EU L1
```

---

## Security Model

- **On-chain KYC enforcement**: Only allowlisted addresses can hold ariTRY/ariEUR — enforced in EVM, not just API
- **Role-based access**: Separate `MINTER_ROLE`, `BRIDGE_OPERATOR_ROLE`, `FREEZER_ROLE`, `DEFAULT_ADMIN_ROLE`
- **Freeze capability**: Compliance can freeze individual wallets instantly (sanctions, court orders)
- **Timelock governance**: Admin operations go through `AriTimelock` with mandatory delay
- **Transfer restrictions**: Vehicle NFTs can only change hands through approved escrow contracts
- **Replay protection**: All bridge contracts track processed message hashes
- **Permissioned chains**: Transaction and contract deployment allowlists at the EVM precompile level
- **Double-entry ledger**: Every debit has a matching credit, fully auditable
- **Outbox pattern**: Exactly-once event delivery between core banking and blockchain service
- **Idempotent payments**: All payment endpoints require `Idempotency-Key` headers

---

## Documentation

| Document | Description |
|----------|-------------|
| [`docs/avalanche/`](./docs/avalanche/) | 10-part Avalanche integration guide (architecture, L1 setup, ICTT, gasless txs, etc.) |
| [`docs/FUJI_L1_SETUP_GUIDE.md`](./docs/FUJI_L1_SETUP_GUIDE.md) | Step-by-step guide: Platform CLI + Builder Console L1 setup |
| [`docs/compliance.md`](./docs/compliance.md) | TR (BDDK/MASAK) and EU (MiCA/PSD2/GDPR) regulatory requirements |
| [`docs/adr/001-multi-region-data-residency.md`](./docs/adr/001-multi-region-data-residency.md) | Architecture decision: multi-region data residency for production |
| [`PROGRESS.md`](./PROGRESS.md) | Detailed implementation progress and session history |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | Original system design and phase plan |
