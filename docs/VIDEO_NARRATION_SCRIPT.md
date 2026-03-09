# ARI Demo — Full Narration Script

Read this script while recording the screen. Pauses marked with `[pause]` — use these to let the UI catch up or to let a point land. Actions you should be performing on screen are in **[brackets]**.

---

## SCENE 0 — Opening (40 seconds)

**[Show landing page. Let hero animations play. Slowly scroll to features section, then back up.]**

Sending money across borders today still relies on SWIFT — it costs twenty-five to eighty dollars per transfer and takes up to five business days to settle. In Turkey, if you want to transfer foreign currency between two domestic banks, you sometimes have to physically withdraw cash from an ATM and deposit it at the other bank.

And buying a car? You go to a notary, wait in line for hours, and pay hundreds of lira for someone to stamp a piece of paper. Most notaries don't even verify that the buyer actually paid before transferring the title. People lose their money, their cars — and sometimes worse.

ARI fixes both of these problems using Avalanche L1 blockchains. Every jurisdiction gets its own permissioned chain, connected by Avalanche Teleporter for instant cross-chain settlement. Let me show you how it works.

---

## SCENE 1 — Signup & KYC (40 seconds)

**[Click "Open Account" → go to signup page]**

Let's sign up as a new user.

**[Fill in email, phone, password. Select "Turkey (TRY)" region.]**

I'm picking Turkey as my region — this means my custodial wallet will live on the ariTR L1, chain ID twelve-seventy-nine, running on Fuji testnet.

**[Submit form → login → complete KYC if prompted]**

Behind the scenes, the backend derives a wallet address using HMAC-SHA-256 and automatically KYC-allowlists it on the ariTRY stablecoin contract. That KYC check isn't just a database flag — it's enforced in the smart contract's `_update` hook at the EVM level. If an address isn't on the allowlist, it cannot hold or receive tokens. Period.

**[Show dashboard with empty balance, KYC verified badge]**

Here's my dashboard — TRY account created, KYC verified, zero balance. Let's fund it.

---

## SCENE 2 — Deposit & Mint (30 seconds)

**[Show dashboard with funded balance — pre-fund before recording or show balance appearing]**

In production, when a user deposits Turkish Lira via bank transfer, the backend records it in the double-entry ledger and publishes a mint event. The blockchain service picks it up and mints the equivalent ariTRY — our KYC-enforced stablecoin — directly on the Turkey L1.

Every lira deposited is backed one-to-one by minted ariTRY on-chain. Both L1s use the NativeMinter precompile for gas tokens, so users never touch gas — the platform operator pays all fees at one gwei minimum base fee. Completely gasless from the user's perspective.

**[Show balance: e.g., 5,000 TRY]**

I've pre-funded this account so we can jump to the exciting part.

---

## SCENE 3 — Cross-Border Transfer (80 seconds)

**[Navigate to /transfer → click "Cross-Border" tab]**

Now for the flagship feature — cross-border transfers powered by Avalanche Teleporter.

**[Select TRY source account]**

I'll send a thousand Turkish Lira cross-border. [pause]

**[Point to the "Same currency" toggle]**

This toggle switches between two modes. With same-currency enabled, I can send TRY to another TRY account in the EU region — that uses our AriBurnMintBridge, which burns native ariTRY on the Turkey L1, sends a Teleporter message through Avalanche ICM, and mints native ariTRY on the Europe L1. No wrapped tokens — real native stablecoins on both chains.

**[Leave toggle off for FX mode. Enter recipient account, enter amount: 1000]**

Right now I'll do a currency conversion — TRY to EUR. [pause]

**[Click "Get Quote"]**

The backend locks in an exchange rate with a thirty-second time-to-live. You can see the countdown — the user knows exactly what rate they're getting. No hidden fees, no slippage.

**[Show quote details: rate, spread, estimated delivery. Click "Confirm & Send"]**

When I confirm, here's what happens: the backend burns a thousand ariTRY on the Turkey L1 by calling our burn-mint bridge contract. The bridge sends a message through TeleporterMessenger at the canonical Avalanche address. The managed relayer picks up the message via Avalanche Warp Messaging and delivers it to the Europe L1. The partner bridge on the EU side verifies the sender, checks the message hash for replay protection, and mints the equivalent ariEUR.

**[Watch the progress timeline animate — point at each step]**

You can see the settlement happening in real time — burn confirmed, Teleporter relay in transit... and mint confirmed. Seconds, not days.

**[Point to the blockchain details — burn tx hash, mint tx hash]**

Every step is verifiable on-chain.

**[Click the transaction hash → show explorer loading]**

There it is on the Avalanche explorer — a real transaction on our Fuji L1. Not a mock. Real on-chain settlement via Teleporter.

---

## SCENE 4 — Vehicle Escrow (70 seconds)

**[Navigate to /vehicles from sidebar]**

Now let me show you something that solves a real human problem.

**[Click "Register Vehicle"]**

In Turkey, buying a car means trusting a stranger at a notary office. Most notaries don't verify payment before transferring the title. People get scammed. [pause]

**[Fill in VIN, plate, make: "BMW", model: "320i", year: 2024, color, mileage. Note the Avalanche banner.]**

ARI replaces the notary with on-chain vehicle ownership. I enter my car's details — the VIN is hashed with keccak-256 for privacy. The backend mints an ERC-721 NFT on the Turkey L1. Each VIN can only be minted once — the contract enforces uniqueness on-chain.

**[Submit → show vehicle in list with PENDING status → wait or cut to MINTED status]**

**[Click into vehicle detail page → show On-Chain Proof section]**

There it is — minted on Avalanche with a token ID, verified on-chain. And this NFT has a critical restriction: direct transfers are blocked in the smart contract. This vehicle can only change hands through an approved escrow contract.

**[Click "Sell This Vehicle" → enter sale price: 250,000 TRY]**

**[Show the share code and link generated]**

When I sell, the system creates a smart contract escrow. The buyer gets a share code, joins the deal, funds it with ariTRY, and both parties confirm. Then — in a single atomic transaction — the ariTRY goes to the seller, a fifty-lira platform fee goes to the treasury, and the NFT transfers to the buyer.

**[Show escrow detail page with the progress timeline if available]**

Neither party can cheat. Payment and ownership transfer happen atomically in one on-chain transaction. No notary. No trust required. [pause]

This is what blockchain should be used for — not speculation, but protecting people.

---

## SCENE 5 — Closing (30 seconds)

**[Show the README architecture diagram on GitHub, or the explorer showing both L1s]**

To recap: two permissioned Avalanche L1s on Fuji — ariTR and ariEU. Each with its own validator set, KYC-enforced stablecoins, and burn-mint bridges connected by Teleporter. Twenty smart contract instances deployed across both chains. A hundred eighty-three Solidity tests passing. Full-stack integration from Kotlin backend to Next.js frontend to on-chain settlement.

We chose Avalanche because no other platform gives us permissioned EVM chains with sub-second finality, native cross-chain messaging, and precompile-level access control — all in one stack.

ARI is built for the real world — regulated, compliant, and designed to expand. Adding a new jurisdiction means deploying a new L1 and registering it with the bridge network. Turkey and Europe today. The world tomorrow.

Thank you for watching.

---

## Quick Reference — Numbers to Mention

| Stat | Value |
|------|-------|
| Smart contract instances | 20 across 2 L1s |
| Solidity tests | 183 passing |
| ariTR chain ID | 1279 |
| ariEU chain ID | 1832 |
| Stablecoin contracts | ariTRY + ariEUR (UUPS upgradeable, KYC-enforced) |
| Bridge type | AriBurnMintBridge via Teleporter (no wrapped tokens) |
| TeleporterMessenger | Canonical: `0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf` |
| Vehicle escrow fee | 50 TRY (flat) |
| Gas model | 1 gwei min base fee, operator-paid, gasless UX |
| Governance | AriTimelock — 48h delay in production |

---

## Total Script Duration Estimate

| Section | Word Count | Duration (~150 wpm) |
|---------|-----------|---------------------|
| Scene 0 | ~130 words | ~52s |
| Scene 1 | ~120 words | ~48s |
| Scene 2 | ~95 words | ~38s |
| Scene 3 | ~250 words | ~100s |
| Scene 4 | ~210 words | ~84s |
| Scene 5 | ~110 words | ~44s |
| **Total** | **~915 words** | **~6 min reading** |

**Note**: At natural speaking pace with pauses for UI actions, this will compress to ~4:30–5:00. If running long, cut Scene 2 (deposit) shorter — just show the balance and say "pre-funded" in one sentence. You can also trim the technical detail in Scene 3 if the UI speaks for itself.
