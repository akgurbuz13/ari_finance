# ARI — Demo Video Walkthrough Plan

**Target**: Avalanche Builder Competition Judges
**Duration**: 4–5 minutes
**Format**: Screen recording with voiceover narration
**Setup**: Local build (web app on :3000, core-banking on :8080, blockchain-service on :8081 with `--spring.profiles.active=fuji` connecting to live Fuji L1s)

---

## Pre-Recording Checklist

Before hitting record:

1. `docker compose up -d` (PostgreSQL + Redis)
2. `./gradlew :core-banking:bootRun` (terminal 1)
3. `SPRING_PROFILES_ACTIVE=fuji ./gradlew :blockchain-service:bootRun` (terminal 2)
4. `cd web && npm run dev` (terminal 3)
5. Clear browser history/cookies for a fresh signup experience
6. Have a second browser tab ready for the admin console (optional)
7. Open the Avalanche Builder Hub explorer in a tab: `https://build.avax.network/explorer/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM`
8. Pre-register a second "buyer" user if showing vehicle escrow buyer side (or use admin API)

**Resolution**: 1920x1080, browser zoom 100-110%

---

## Scene Breakdown

### Scene 0 — Opening & Vision (0:00–0:40)

**What's on screen**: ARI landing page at `localhost:3000` — the hero section with animated floating cards (balance card, transfer card, vehicle escrow card).

**Pacing**: Let the landing page load and the card animations play. Slowly scroll down to show the features section and vehicle comparison table ("Traditional Way vs ARI Way"), then scroll back up.

**Script**:

> Sending money across borders today still relies on SWIFT — it costs $25 to $80 per transfer and takes up to five business days. In Turkey, if you want to transfer foreign currency between two domestic banks, you sometimes have to physically withdraw cash from an ATM and deposit it at the other bank.
>
> And buying a car? You go to a notary, wait in line for hours, and pay hundreds of lira — and most notaries don't even verify that the buyer actually paid before transferring the title. People lose their money, their cars, and sometimes worse.
>
> ARI is a regulated fintech platform that fixes both of these problems using Avalanche L1 blockchains. Every jurisdiction gets its own permissioned L1 — Turkey gets one, Europe gets one — connected by Avalanche Teleporter for instant cross-chain settlement. Let me show you how it works.

---

### Scene 1 — User Signup & KYC (0:40–1:20)

**What's on screen**: Click "Open Account" on the landing page → navigate to `/signup`.

**Actions (do on screen while narrating)**:
1. Fill in email, phone, password
2. Select region: **Turkey (TRY)** — point out that this determines which L1 the user's wallet lives on
3. Submit → redirected to dashboard or login
4. Login with credentials
5. **(If KYC prompt appears)**: Click through KYC verification (it auto-approves in dev)
6. Create a TRY account from the dashboard or accounts page
7. Show the dashboard — empty balance, account health showing KYC verified

**Script**:

> Let's sign up as a new user. I'll pick Turkey as my region — this means my custodial wallet will be created on the ariTR L1, chain ID 1279, deployed on Fuji testnet.
>
> *(filling form)* When I create my account, the backend deterministically derives a wallet address using HMAC-SHA256 from a master key plus my user ID, and automatically KYC-allowlists it on the ariTRY stablecoin contract. That means this wallet address is now approved at the EVM level — not just in a database, but enforced in the smart contract's `_update` hook. No KYC, no tokens. Period.
>
> *(showing dashboard)* Here's my dashboard. I have a TRY account, verified KYC, and zero balance. Let's fund it and do something interesting.

---

### Scene 2 — Deposit & On-Chain Mint (1:20–1:50)

**What's on screen**: Dashboard showing the TRY account.

**Actions**:
1. The deposit happens via the backend (in production, a bank transfer triggers a webhook; for demo, use the setup script or admin API to pre-fund)
2. Show the balance updating on the dashboard
3. Briefly mention that the deposit triggers an `ariTRY` mint on the TR L1

**Script**:

> In production, when a user deposits Turkish Lira via bank transfer, our backend records the deposit in the double-entry ledger and publishes a `MintRequested` event to the outbox table. The blockchain service picks it up and mints the equivalent ariTRY — our KYC-enforced stablecoin — on the Turkey L1. Every lira deposited is backed one-to-one by minted ariTRY on-chain.
>
> I've pre-funded this account so we can jump straight to the exciting part — cross-border transfers.

---

### Scene 3 — Cross-Border Transfer via Teleporter (1:50–3:10) **[KEY SCENE]**

**What's on screen**: Navigate to `/transfer` → click the "Cross-Border" tab.

**Actions (step by step)**:
1. Select source account (TRY, Turkey)
2. **Toggle "Same currency"** checkbox — explain what this does
3. Enter recipient account ID (a EUR account on the EU L1, or another TRY account in EU region)
4. Enter amount (e.g., 1,000 TRY)
5. *(If FX)* Click "Get Quote" → show the **countdown timer** with exchange rate, spread, and estimated delivery time
6. Click "Confirm & Send"
7. **Watch the transfer progress timeline animate** — this is the money shot:
   - Payment initiated (checkmark)
   - Compliance check (checkmark)
   - Blockchain settlement: **show the chain visualization** with animated dots flowing from Turkey L1 to Europe L1
   - Expandable blockchain details: burn tx hash, bridge relay status, mint tx hash
8. Success screen with "Settled on Avalanche" badge
9. **Click the burn tx hash** → opens Avalanche explorer showing the on-chain transaction

**Script**:

> Now for the flagship feature — cross-border transfers. I'll send 1,000 Turkish Lira to a Euro account in Europe.
>
> *(selecting options)* I can toggle "same currency" if I want to send TRY to another TRY account in the EU region — that uses our AriBurnMintBridge, which burns native ariTRY on the Turkey L1, sends a Teleporter message via Avalanche ICM, and mints native ariTRY on the Europe L1. No wrapped tokens — real native stablecoins on both chains.
>
> But right now I'll do an FX conversion — TRY to EUR. *(clicking Get Quote)* The backend locks in an exchange rate with a 30-second time-to-live. You can see the countdown — the user knows exactly what rate they're getting, no slippage.
>
> *(confirming)* When I confirm, here's what happens on-chain: the backend burns 1,000 ariTRY on the Turkey L1 — that's chain ID 1279 — then the blockchain service calls our AriBurnMintBridge contract, which sends a message through TeleporterMessenger at the canonical address. The Avalanche relayer picks up the message via Avalanche Warp Messaging and delivers it to the Europe L1 — chain ID 1832. The bridge contract on the EU side receives the Teleporter message, verifies the sender is a registered partner, and mints the equivalent ariEUR to the recipient's wallet.
>
> *(watching progress)* You can see it settling in real time — burn confirmed, Teleporter relay in transit, mint confirmed. The whole thing takes seconds instead of days. And every step is verifiable on-chain.
>
> *(clicking tx hash)* Here's the transaction on the Avalanche explorer. That's a real burn on the Fuji TR L1 — not a testnet mock, not a simulation. Real on-chain settlement via Teleporter.

**Tip**: If the transfer takes a few seconds to settle, fill the silence by talking about the transit account pattern — the receiver is only credited after on-chain confirmation.

---

### Scene 4 — Vehicle Escrow: NFT Mint + Atomic Swap (3:10–4:20) **[KEY SCENE]**

**What's on screen**: Navigate to `/vehicles` from the sidebar.

**Actions (step by step)**:
1. Click "Register Vehicle"
2. Fill in VIN, plate number, make (e.g., "BMW"), model ("320i"), year (2024), color, mileage
3. Note the Avalanche banner: "Your vehicle will be minted as an NFT on Avalanche L1"
4. Submit → show the vehicle appearing in "My Vehicles" with PENDING status
5. *(Wait a few seconds or cut)* → Status changes to MINTED with Avalanche NFT badge and Token ID
6. Click into the vehicle detail page → show **On-Chain Proof** section with tx hash and "Verified on Avalanche" badge
7. Click "Sell This Vehicle" → Create escrow with a sale price (e.g., 250,000 TRY)
8. Show the **share code** and link generated for the buyer
9. *(If time)* Show the escrow detail page with the 6-step progress timeline
10. Explain the atomic swap — in a single transaction, ariTRY goes to seller, NFT goes to buyer, fee goes to treasury

**Script**:

> Now let me show you something that solves a real human problem. In Turkey, buying a car means going to a notary and trusting a stranger. Most notaries don't verify payment before transferring the title. People get scammed.
>
> ARI replaces this with on-chain vehicle ownership. *(registering vehicle)* I enter my car's VIN and details — the backend hashes the VIN with keccak-256 for privacy, then mints an ERC-721 NFT on the Turkey L1 using our AriVehicleNFT contract. Each VIN can only be minted once — the contract enforces uniqueness.
>
> *(showing minted vehicle)* There it is — minted on Avalanche with a token ID. I can verify it on the explorer. The NFT has a critical restriction: direct transfers are blocked. This vehicle can only change hands through an approved escrow contract.
>
> *(creating escrow)* When I sell, I set a price and the system creates a smart contract escrow. The buyer gets a share code, joins the deal, and funds it with ariTRY. Both parties confirm. Then — in a single atomic transaction on-chain — the ariTRY goes to the seller, a 50 TRY platform fee goes to the treasury, and the NFT transfers to the buyer. Neither party can cheat. Payment and ownership transfer happen atomically. No notary. No trust required.
>
> This is what blockchain should be used for — not speculation, but protecting people.

---

### Scene 5 — Closing & Architecture (4:20–4:50)

**What's on screen**: Briefly show the README's architecture diagram on GitHub, or show the chain explorer with both L1s.

**Script**:

> To recap what's running here: two permissioned Avalanche L1s on Fuji — ariTR and ariEU — each with its own validator set, KYC-enforced stablecoins, and burn-mint bridges connected by Teleporter. 20 smart contracts deployed, 183 tests passing, full-stack integration with a Kotlin backend and Next.js frontend.
>
> We chose Avalanche because no other platform gives us permissioned EVM chains with sub-second finality, native cross-chain messaging, and precompile-level access control — all in one stack. Every transaction allowlist, every contract deployment restriction, every KYC check is enforced at the chain level, not just in our API.
>
> ARI is built for the real world — regulated, compliant, and designed to expand. Adding a new jurisdiction means deploying a new L1 and registering it with the bridge network. Turkey and Europe today. The world tomorrow.
>
> Thank you for watching.

---

## Timing Summary

| Scene | Duration | Content |
|-------|----------|---------|
| 0 — Vision | 0:00–0:40 (40s) | Landing page + problem statement |
| 1 — Signup | 0:40–1:20 (40s) | User registration, KYC, wallet creation |
| 2 — Deposit | 1:20–1:50 (30s) | Funding + mint explanation |
| 3 — Cross-Border | 1:50–3:10 (80s) | FX quote, Teleporter settlement, explorer proof |
| 4 — Vehicle Escrow | 3:10–4:20 (70s) | NFT mint, escrow creation, atomic swap |
| 5 — Closing | 4:20–4:50 (30s) | Architecture recap, why Avalanche, vision |
| **Total** | | **~4 min 50 sec** |

---

## Editing Tips

- **Cut waiting time**: If blockchain operations take a few seconds, cut in post. Or narrate the technical flow while waiting.
- **Zoom in** on blockchain-specific UI: the chain visualization with animated dots, the tx hash links, the "Verified on Avalanche" badges, the countdown timer.
- **Show the explorer**: When clicking a transaction hash, briefly show the Avalanche explorer page loading — judges want to see it's real.
- **Keep mouse movements smooth**: Use a tool like ScreenFlow or OBS. Avoid frantic clicking.
- **Background music**: Light, professional ambient track at low volume. Nothing distracting.
- **No face cam needed**: Screen recording with voiceover is fine for a builder competition.

---

## Fallback Plans

| Issue | Fallback |
|-------|----------|
| Blockchain settlement slow/stuck | Pre-record the settlement segment. Explain "this was recorded live on Fuji testnet." |
| RPC node down | Use pre-recorded segments for on-chain interactions. Check node health before recording. |
| KYC auto-approve fails | Use `curl` to manually approve via admin API: `POST /api/v1/kyc/{id}/approve` |
| Balance not showing | Pre-fund via setup-demo.sh before recording |
| Vehicle mint takes too long | Pre-register and pre-mint a vehicle. Show the already-minted vehicle detail page. |

---

## Key Technical Points to Mention (for judges)

These are the details Avalanche judges will care about. Work them into the narration naturally:

- **Two permissioned L1s** with ContractDeployerAllowList + TransactionAllowList precompiles
- **TeleporterMessenger** at canonical address `0x253b...` for cross-chain messaging
- **AriBurnMintBridge**: burn on source → Teleporter ICM/AWM → mint on destination. No wrapped tokens.
- **KYC enforced in `_update()` hook** — not a backend check, EVM-level enforcement
- **Custodial wallets**: deterministic derivation (HMAC-SHA256), auto-KYC-allowlisted
- **NativeMinter precompile**: gas tokens minted by admin, users never touch gas (1 gwei min base fee)
- **Outbox pattern**: exactly-once event delivery between core banking and blockchain service
- **Transit account pattern**: receiver credited only after on-chain confirmation
- **ERC-721 with transfer restrictions**: vehicles can only change hands through approved escrow contracts
- **Atomic swap**: payment + NFT + fee in a single on-chain transaction
- **Partner registration**: bridges only accept messages from registered partner contracts on the other chain
- **UUPS upgradeable proxies**: stablecoins can be upgraded without migrating balances
- **AriTimelock**: 48h governance delay in production for admin operations

---

## What NOT to Show

- Mobile app (70% complete, not polished enough)
- Payment rail stubs (deposit/withdrawal are intentionally stubbed awaiting bank partnerships)
- Admin console (judges care about on-chain, not admin CRUD — mention it exists but don't demo)
- Raw API calls or terminal commands (keep it visual — the web app tells the story better)
- Code editor or IDE (this is a product demo, not a code review — the README links to source code)
