# 01 - Avalanche Architecture Overview for ARI

> **Key Takeaways**
> - ARI uses two permissioned Avalanche L1s (formerly called Subnets), one per regulatory jurisdiction
> - L1s are sovereign chains that share only P-Chain state, not block execution
> - Consensus finality is sub-second (~1-2 s) with Snowman consensus, making it suitable for fintech settlement
> - Warp messaging (BLS multi-sig) is the native cross-chain communication layer; Teleporter (ICM Contracts) is the developer-facing abstraction built on top of it
> - ARI's two-chain design directly maps to the BDDK (Turkey) and GDPR/MiCA (EU) data residency requirements documented in `docs/adr/001-multi-region-data-residency.md`

---

## 1. The Avalanche Network Stack

Avalanche is composed of three layers that are important to understand for operating ARI:

```
┌────────────────────────────────────────────────┐
│                 Avalanche L1s                   │
│   (ARI ariTR L1, ARI ariEU L1, other L1s)      │
│   - Run their own EVM (Subnet-EVM)              │
│   - Independent consensus and block production  │
│   - Connected to P-Chain for validator set sync │
├────────────────────────────────────────────────┤
│               Primary Network                   │
│   - P-Chain: validator management, staking      │
│   - C-Chain: Ethereum-compatible smart contracts│
│   - X-Chain: AVAX native token transfers (UTXO) │
├────────────────────────────────────────────────┤
│         Avalanche Consensus (Snowman++)          │
│   - Probabilistic finality in ~1-2 s           │
│   - Byzantine fault tolerant (BFT)             │
│   - Does NOT require PoW                       │
└────────────────────────────────────────────────┘
```

### P-Chain (Platform Chain)

The P-Chain is the coordination layer for all of Avalanche. Relevant to ARI:

- Stores every L1's validator set (as BLS public keys + weights)
- Accepts `ConvertSubnetToL1Tx` to register ARI's L1s
- Accepts `RegisterL1ValidatorTx` / `SetL1ValidatorWeightTx` to manage validator membership
- Charges a continuous fee (~512 nAVAX/s per validator, ~1.33 AVAX/month at target capacity) for maintaining each L1 validator in memory
- Serves as the source of truth for cross-chain Warp message verification

ARI validators only need to sync the P-Chain; they do NOT need to validate the C-Chain or X-Chain. This is a major change from the old Subnet model (pre-ACP-77) where validators were required to also validate the Primary Network.

### C-Chain (Contract Chain)

The C-Chain is Avalanche's public Ethereum-compatible chain. ARI does NOT use the C-Chain directly for stablecoin operations. However:

- Teleporter's canonical address (`0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf`) is deployed there
- The ICTT registry is on the Fuji C-Chain (`0xF86Cb19Ad8405AEFa7d09C778215D2Cb6eBfB228`)
- The Builder Console uses the C-Chain for some tooling operations

---

## 2. What is an Avalanche L1?

An Avalanche L1 (previously called a "Subnet") is an independent blockchain that:

1. Has its own validator set (no overlap with Primary Network required since ACP-77)
2. Runs any Virtual Machine (ARI uses Subnet-EVM, an EVM fork)
3. Produces its own blocks with its own consensus
4. Communicates with other L1s via Avalanche Warp Messaging (AWM)
5. Is registered on the P-Chain through a `ConvertSubnetToL1Tx`
6. Manages its validator set through a **ValidatorManager** smart contract deployed on the L1 itself

Key difference from the old Subnet model:

| Feature | Old Subnet (pre-ACP-77) | Avalanche L1 (ACP-77+) |
|---------|------------------------|------------------------|
| Validators must validate Primary Network | Yes (required) | No (optional) |
| Minimum stake requirement | 2,000 AVAX per validator | None (only ~1.33 AVAX/month P-Chain fee) |
| Validator set management | P-Chain transaction (SubnetAuth key) | ValidatorManager smart contract on L1 |
| Regulated entities | Blocked (had to validate C-Chain) | Allowed |
| Sovereignty | Low | High |

This makes ARI's regulated-entity use case (BDDK/MiCA compliance) possible: ARI validators never touch the public C-Chain.

---

## 3. ARI's Two-L1 Network Topology

```
┌──────────────────────────────────────────────────────────────┐
│                   Avalanche Fuji Testnet                      │
│                   (→ Mainnet at launch)                       │
│                                                              │
│  ┌──────────────────────┐     ┌───────────────────────────┐  │
│  │    ariTR L1           │     │       ariEU L1             │  │
│  │  Chain ID: 1279       │     │   Chain ID: 1832           │  │
│  │  Region: Turkey       │     │   Region: EU               │  │
│  │                       │     │                            │  │
│  │  Contracts:           │ ICM │  Contracts:                │  │
│  │  - ariTRY stablecoin  │◄───►│  - ariEUR stablecoin       │  │
│  │  - AriTokenHome       │     │  - AriTokenHome            │  │
│  │  - AriTokenRemote     │Tele │  - AriTokenRemote          │  │
│  │    (wEUR)             │port │    (wTRY)                  │  │
│  │  - AriBurnMintBridge  │ er  │  - AriBurnMintBridge       │  │
│  │  - AriVehicleNFT      │     │                            │  │
│  │  - AriVehicleEscrow   │     │                            │  │
│  │  - TeleporterMessenger│     │  - TeleporterMessenger     │  │
│  └──────────────────────┘     └───────────────────────────┘  │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐   │
│  │                 Avalanche P-Chain                     │   │
│  │  - Stores ariTR validator set (BLS keys + weights)   │   │
│  │  - Stores ariEU validator set                        │   │
│  │  - Charges continuous validator fee (~1.33 AVAX/mo)  │   │
│  └───────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

### Why Two Chains?

| Requirement | ariTR L1 | ariEU L1 |
|-------------|----------|----------|
| Regulatory framework | BDDK, SPK, MASAK (Turkey) | MiCA, GDPR, PSD2 (EU) |
| Data residency | Turkish jurisdiction | EU jurisdiction |
| Primary stablecoin | ariTRY (Turkish Lira) | ariEUR (Euro) |
| Deployed region | AWS eu-central-1 (Frankfurt) nodes possible; BDDK may require Turkey-hosted nodes | Azure Turkey Central or EU nodes |
| KYC/AML enforcement | Turkish address space | EU address space |

The cross-chain bridge (Teleporter/ICTT) moves only economic value — token amounts and contract state — between chains. Personal data (user identity, KYC records) never crosses the chain boundary and stays in the region-specific database as documented in `docs/adr/001-multi-region-data-residency.md`.

---

## 4. Consensus Mechanism and Finality

Avalanche uses **Snowman consensus** (a DAG-to-chain linearization of the original Avalanche consensus paper) for L1 blockchains.

### Key Properties for ARI

| Property | Value | Significance |
|----------|-------|--------------|
| Time to finality | ~1-2 seconds | Mint/burn settlement can be confirmed quickly |
| BFT tolerance | f < n/5 (Byzantine fault tolerant) | 1 of 5 validators can be malicious safely |
| Block production | Leader-based (Snowman++) | Deterministic block ordering |
| Quorum for Warp messages | ≥67% stake weight | Cross-chain messages require super-majority |
| Reorg probability | Negligible (probabilistic finality) | Mint/burn events are safe to act on immediately |

### Finality vs Confirmations

Unlike Ethereum (which needs 12-64 confirmations for safety), Avalanche achieves probabilistic finality in the first block. For ARI's settlement flow:

- A mint transaction confirmed by the RPC node is effectively final
- The `ChainEventListener` in ARI can act on block 1 confirmations safely
- No need to wait for N blocks as in Ethereum-style chains

---

## 5. Subnet-EVM: The Virtual Machine

ARI's L1s run **Subnet-EVM**, Avalanche's fork of the Ethereum EVM. It is fully Ethereum-compatible with the following additions:

- **Stateful precompiles**: contract-like objects at fixed addresses that control chain behavior (allowlists, native minting, fee management). These are the primary mechanism for permissioning ARI's chains.
- **Avalanche Warp Messaging precompile** (`0x0200000000000000000000000000000000000005`): built-in cross-chain messaging
- **Configurable fee model**: `feeConfig` in genesis sets gas prices independently of the broader Avalanche network
- **Network upgrades via config file**: precompiles can be enabled/disabled at specific block timestamps without hard forks

ARI smart contracts (`AriStablecoin.sol`, `AriBurnMintBridge.sol`, etc.) are standard Solidity 0.8.24 code. They do not need to know they are running on Subnet-EVM except for:

1. Calling Teleporter at its canonical address (`0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf`) for cross-chain messages
2. Optionally calling the Native Minter precompile (`0x0200000000000000000000000000000000000001`) if minting native gas tokens programmatically

---

## 6. Cross-References

- L1 creation process: `docs/avalanche/02-permissioned-l1-setup.md`
- Genesis file configuration: `docs/avalanche/02-permissioned-l1-setup.md`
- ICTT and Teleporter bridge: `docs/avalanche/04-ictt-bridge-integration.md`
- Warp messaging internals: `docs/avalanche/04-ictt-bridge-integration.md`
- Node infrastructure: `docs/avalanche/06-node-infrastructure.md`
- Current Fuji deployment guide: `docs/FUJI_L1_SETUP_GUIDE.md`
- Multi-region data residency ADR: `docs/adr/001-multi-region-data-residency.md`
