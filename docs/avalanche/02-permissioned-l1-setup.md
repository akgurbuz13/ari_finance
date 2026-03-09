# 02 - Permissioned L1 Setup

> **Key Takeaways**
> - Creating an Avalanche L1 requires three P-Chain transactions: CreateSubnetTx → CreateChainTx → ConvertSubnetToL1Tx (or the upcoming single-step CreateL1Tx from ACP-191)
> - The `contractDeployerAllowListConfig` precompile restricts contract deployment to authorized addresses — critical for regulatory control
> - The `txAllowListConfig` precompile can restrict who submits transactions at all — use with care in production (see caveats)
> - The `warpConfig` must be present in genesis for Teleporter/ICM to function
> - The ValidatorManager contract (PoAManager pattern) is required for L1 sovereignty; the P-Chain no longer manages validator membership directly after `ConvertSubnetToL1Tx`
> - ARI's current genesis file (`genesis-ariTR.json`) is functional but is missing several recommended precompiles

---

## 1. The Three-Step L1 Creation Process

Creating an Avalanche L1 currently requires three separate P-Chain transactions issued in sequence:

```
Step 1: CreateSubnetTx
  → Creates a Subnet record on the P-Chain
  → Sets a SubnetAuth key (temporarily used for Steps 2-3)
  → Returns: SubnetID

Step 2: CreateChainTx
  → Registers a blockchain on the Subnet
  → Embeds the genesis file (max 1 MB)
  → Returns: BlockchainID (CB58)

Step 3: ConvertSubnetToL1Tx
  → Converts the permissioned Subnet into a sovereign L1
  → Sets the ValidatorManager contract address
  → Registers initial validators with their BLS keys
  → IRREVERSIBLE
  → Returns: validation IDs for each initial validator
```

A future proposal (ACP-191) would collapse these into a single `CreateL1Tx`. As of March 2026 it is in "Proposed" status and not yet active.

### Using Platform CLI

```bash
# Step 1: Create subnet
platform subnet create --key-name ari-deployer --network fuji

# Step 2: Create blockchain on the subnet
platform chain create \
  --subnet-id <SUBNET_ID> \
  --genesis genesis-ariTR.json \
  --name ariTR \
  --key-name ari-deployer \
  --network fuji

# Step 3: Convert to L1 (irreversible)
platform subnet convert-l1 \
  --subnet-id <SUBNET_ID> \
  --chain-id <BLOCKCHAIN_ID_CB58> \
  --manager <VALIDATOR_MANAGER_CONTRACT_ADDRESS> \
  --validators <NODE_IP>:9650 \
  --validator-balance 5.0 \
  --key-name ari-deployer \
  --network fuji
```

Note: `--validator-balance` is denominated in AVAX and funds the continuous P-Chain fee. At ~1.33 AVAX/month, 5 AVAX provides approximately 3.75 months. For production, fund with 12+ AVAX per validator.

---

## 2. The Genesis File in Depth

The genesis file defines the initial state of the EVM chain. It is embedded in `CreateChainTx` and cannot be changed after chain creation.

### Annotated Genesis File for ARI

```json
{
  "config": {
    // EVM chain ID — used by MetaMask, web3j, Hardhat, and in EIP-155 tx signing
    // Must be unique globally. ARI uses 1279 (TR) and 1832 (EU).
    // Check https://chainlist.org to avoid collisions if going public.
    "chainId": 1279,

    // All EVM fork activation blocks set to 0 = all forks active at genesis
    "homesteadBlock": 0,
    "eip150Block": 0,
    "eip155Block": 0,
    "eip158Block": 0,
    "byzantiumBlock": 0,
    "constantinopleBlock": 0,
    "petersburgBlock": 0,
    "istanbulBlock": 0,
    "muirGlacierBlock": 0,
    "subnetEVMTimestamp": 0,

    // Fee configuration — embedded directly in genesis.
    // Can be updated post-genesis via the FeeManager precompile (if enabled).
    "feeConfig": {
      "gasLimit": 12000000,       // Max gas per block. 12M is reasonable for fintech.
      "targetBlockRate": 2,       // Target seconds between blocks (2 = ~0.5 TPS at base load)
      "minBaseFee": 25000000000,  // 25 gwei minimum base fee
      "targetGas": 15000000,      // Target gas spending per 10s window
      "baseFeeChangeDenominator": 36,  // How quickly base fee adjusts (higher = slower)
      "minBlockGasCost": 0,       // Deprecated by Granite upgrade
      "maxBlockGasCost": 1000000, // Deprecated by Granite upgrade
      "blockGasCostStep": 200000  // Deprecated by Granite upgrade
    },

    // CONTRACT DEPLOYER ALLOWLIST
    // Only adminAddresses can deploy new contracts. Critical for permissioning.
    // Address added here can also call setEnabled() to grant deploy rights to others.
    "contractDeployerAllowListConfig": {
      "blockTimestamp": 0,
      "adminAddresses": ["0xe9ce1Cd8179134B162581BEb7988EBD2e2400503"],
      "enabledAddresses": []
    },

    // WARP CONFIG — REQUIRED for Teleporter/ICM cross-chain messaging
    // quorumNumerator: percentage of stake that must sign a Warp message (default 67)
    // requirePrimaryNetworkSigners: should be false for L1-to-L1 messages;
    //   set true only if receiving messages FROM the C-Chain or X-Chain
    "warpConfig": {
      "blockTimestamp": 0,
      "quorumNumerator": 67
    }
  },

  // Initial token allocation
  // Pre-fund the deployer address with 50M native gas tokens
  // Balance is in wei (hex). 0x295BE96E64066972000000 = 50,000,000 * 10^18
  "alloc": {
    "0xe9ce1Cd8179134B162581BEb7988EBD2e2400503": {
      "balance": "0x295BE96E64066972000000"
    }
  },

  // Standard EVM genesis fields — do not change these
  "nonce": "0x0",
  "timestamp": "0x0",
  "extraData": "0x00",
  "gasLimit": "0xB71B00",
  "difficulty": "0x0",
  "mixHash": "0x0000000000000000000000000000000000000000000000000000000000000000",
  "coinbase": "0x0000000000000000000000000000000000000000",
  "number": "0x0",
  "gasUsed": "0x0",
  "parentHash": "0x0000000000000000000000000000000000000000000000000000000000000000"
}
```

### Recommended Additions for Production

The current genesis file is missing several precompiles that should be enabled for a production regulated L1:

```json
{
  "config": {
    "chainId": 1279,

    // ... (all EVM fork blocks as above) ...

    "feeConfig": { ... },

    // DEPLOYER ALLOWLIST (already present — keep)
    "contractDeployerAllowListConfig": {
      "blockTimestamp": 0,
      "adminAddresses": ["<MULTI_SIG_ADMIN_ADDRESS>"],
      "enabledAddresses": []
    },

    // TRANSACTION ALLOWLIST (not in current genesis — add for full permissioning)
    // Warning: if enabled, ALL addresses except those listed will be blocked.
    // ARI's relayer, minter, and custodial wallets must all be pre-enabled.
    // The bridge operator address used by blockchain-service must also be enabled.
    "txAllowListConfig": {
      "blockTimestamp": 0,
      "adminAddresses": ["<MULTI_SIG_ADMIN_ADDRESS>"],
      "enabledAddresses": [
        "<MINTER_ADDRESS>",
        "<BRIDGE_OPERATOR_ADDRESS>",
        "<GASLESS_RELAYER_ADDRESS>",
        "<TELEPORTER_DEPLOYER_ADDRESS>"
      ]
    },

    // FEE MANAGER (not in current genesis — add for post-genesis fee updates)
    "feeManagerConfig": {
      "blockTimestamp": 0,
      "adminAddresses": ["<MULTI_SIG_ADMIN_ADDRESS>"],
      "initialFeeConfig": {
        "gasLimit": 12000000,
        "targetBlockRate": 2,
        "minBaseFee": 25000000000,
        "targetGas": 15000000,
        "baseFeeChangeDenominator": 36,
        "minBlockGasCost": 0,
        "maxBlockGasCost": 1000000,
        "blockGasCostStep": 200000
      }
    },

    // REWARD MANAGER (not in current genesis — add for fee destination control)
    // For a permissioned fintech L1, burning fees is simplest.
    // Alternatively: set rewardAddress to a treasury contract.
    "rewardManagerConfig": {
      "blockTimestamp": 0,
      "adminAddresses": ["<MULTI_SIG_ADMIN_ADDRESS>"]
      // No initialRewardConfig = fees are burned by default
    },

    // WARP CONFIG (already present — keep, update requirePrimaryNetworkSigners)
    "warpConfig": {
      "blockTimestamp": 0,
      "quorumNumerator": 67
      // Do NOT set requirePrimaryNetworkSigners: true unless you are receiving
      // messages from C-Chain/X-Chain. For L1-to-L1 messages only, omit it.
    }
  },

  // ALLOC: Pre-deploy TeleporterMessenger in genesis (recommended)
  // This avoids the Nick's method funding step after chain creation.
  "alloc": {
    "<DEPLOYER_ADDRESS>": {
      "balance": "0x295BE96E64066972000000"
    },
    "0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf": {
      "balance": "0x0",
      "code": "<TELEPORTER_BYTECODE_FROM_RELEASE_ARTIFACTS>",
      "storage": {
        "0x0000000000000000000000000000000000000000000000000000000000000000": "0x0000000000000000000000000000000000000000000000000000000000000001",
        "0x0000000000000000000000000000000000000000000000000000000000000001": "0x0000000000000000000000000000000000000000000000000000000000000001"
      },
      "nonce": 1
    }
  }
}
```

---

## 3. Precompile Reference

Precompiles are smart contracts at fixed addresses that control chain-level behavior. They are part of Subnet-EVM, not deployed via the usual transaction flow.

| Precompile | Address | Config Key | Purpose for ARI |
|-----------|---------|-----------|-----------------|
| Contract Deployer AllowList | `0x0200000000000000000000000000000000000000` | `contractDeployerAllowListConfig` | Restrict who can deploy contracts |
| Transaction AllowList | `0x0200000000000000000000000000000000000002` | `txAllowListConfig` | Restrict who can transact at all |
| Native Minter | `0x0200000000000000000000000000000000000001` | `contractNativeMinterConfig` | Mint native gas token programmatically |
| Fee Manager | `0x0200000000000000000000000000000000000003` | `feeManagerConfig` | Update gas prices post-genesis |
| Reward Manager | `0x0200000000000000000000000000000000000004` | `rewardManagerConfig` | Control where tx fees go |
| Warp Messenger | `0x0200000000000000000000000000000000000005` | `warpConfig` | Cross-chain BLS messaging |

### AllowList Role Hierarchy

All allowlist precompiles share the same role model:

| Role | Value | Can Do |
|------|-------|--------|
| Admin | 2 | Add/remove any role (Admin, Manager, Enabled) |
| Manager | 3 | Add/remove only Enabled addresses |
| Enabled | 1 | Use the precompile's functionality |
| None | 0 | Blocked |

In Solidity, roles are read via `readAllowList(address)` which returns the numeric value.

### Interacting with Precompiles in Solidity

```solidity
// Example: granting deploy rights to a new contract deployer
interface IAllowList {
    function setAdmin(address addr) external;
    function setEnabled(address addr) external;
    function setManager(address addr) external;
    function setNone(address addr) external;
    function readAllowList(address addr) external view returns (uint256 role);
}

IAllowList constant DEPLOYER_LIST = IAllowList(0x0200000000000000000000000000000000000000);
IAllowList constant TX_LIST = IAllowList(0x0200000000000000000000000000000000000002);

// Grant deploy rights
DEPLOYER_LIST.setEnabled(newDeployerAddress);

// Revoke transaction rights
TX_LIST.setNone(bannedAddress);
```

---

## 4. Transaction AllowList — Production Considerations

The `txAllowListConfig` is the most powerful and most dangerous precompile for ARI. If enabled:

- Addresses not on the list cannot submit ANY transaction
- This includes custodial wallet addresses created after genesis
- Every new user wallet provisioned by `CustodialWalletService.kt` must be added to the list before that user can transact

### ARI's Transaction AllowList Strategy

Option A (current approach, no txAllowList): No transaction-level restriction. Any address with native gas can transact. Simpler to operate.

Option B (full permissioning): Enable `txAllowListConfig`. The blockchain-service must call `TX_LIST.setEnabled(userWalletAddress)` every time a new custodial wallet is created. The bridge operator (admin role) must call this.

Option B is more compliant (KYC-gated at the chain level) but adds operational complexity. If pursuing Option B:

1. Add the FeeManager admin address as `txAllowListConfig` admin in genesis
2. Modify `CustodialWalletService.kt` to call `setEnabled` after wallet creation
3. Ensure the blockchain-service relayer address is pre-enabled in genesis
4. Pre-enable the TeleporterMessenger deployer address (needed for ICM setup)

---

## 5. ValidatorManager Contract

The `ConvertSubnetToL1Tx` requires specifying a ValidatorManager contract address on the L1. This contract is the on-chain authority for adding and removing validators. After conversion, the P-Chain only accepts validator set changes that come with a Warp message signed by the ValidatorManager's chain.

### For ARI: PoAManager Pattern

ARI should use the `PoAManager` pattern (Proof of Authority):

```
ValidatorManager (core logic, Ownable)
      |
      | owner =
      ↓
  PoAManager (restricts add/remove to owner address)
      |
      | owner =
      ↓
  Multi-sig wallet (e.g., Safe) owned by ARI operations team
```

This means:
- Only the ARI multi-sig can add or remove validators
- The multi-sig provides key security (e.g., 2-of-3 signers required)
- The P-Chain enforces this: it will not accept validator changes without a valid Warp message from the PoAManager's chain

### Deployment Sequence

The ValidatorManager must be deployed BEFORE `ConvertSubnetToL1Tx` because its address must be provided in that transaction. However, `initializeValidatorSet` must be called AFTER the conversion (because it needs the `SubnetToL1ConversionMessage` from the P-Chain).

```
1. Deploy ValidatorManager implementation contract
2. Deploy proxy contract pointing to ValidatorManager
3. Deploy PoAManager pointing to ValidatorManager proxy
4. Call ConvertSubnetToL1Tx with PoAManager address
5. Receive SubnetToL1ConversionMessage from P-Chain
6. Call ValidatorManager.initializeValidatorSet() with the message
```

The contracts are available in the `ava-labs/icm-contracts` repository. They are deployed as upgradeable proxies (ERC-1967 proxy pattern).

### Validator Registration Flow (Post-Conversion)

To add a new validator:

```
1. Get node's BLS public key + proof-of-possession (platform node info --ip <addr>)
2. Call PoAManager.initiateValidatorRegistration(nodeID, blsKey, weight, expiry, ...)
3. The PoAManager emits a Warp message (RegisterL1ValidatorMessage)
4. Submit RegisterL1ValidatorTx to P-Chain containing the Warp message
5. P-Chain signs L1ValidatorRegistrationMessage
6. Call ValidatorManager.completeValidatorRegistration() with P-Chain's message
```

The `platform l1 register-validator` command handles steps 4-6.

---

## 6. Genesis Token Allocation and Native Gas Token

The `alloc` section pre-funds addresses at genesis. For ARI:

- The deployer address gets 50M native gas tokens for paying transaction fees
- The native gas token is not AVAX — it is the L1's own token (nameless by default)
- For a permissioned chain with a relayer paying all fees, users never need this token
- Consider also pre-deploying TeleporterMessenger bytecode in `alloc` (see Section 7 in `04-ictt-bridge-integration.md`)

Hex conversion for alloc balances:

```
50,000,000 tokens = 50,000,000 * 10^18 wei = 0x295BE96E64066972000000
 1,000,000 tokens =  1,000,000 * 10^18 wei = 0xD3C21BCECCEDA1000000
```

---

## 7. Fee Configuration for a Permissioned L1

For ARI, all transactions are submitted by the blockchain-service (the relayer/minter wallet). End users never submit transactions directly. This means:

- Gas fees are a cost center for ARI, not end users
- Setting `minBaseFee` to a low value (or even 1 wei) reduces ARI's operational costs
- Setting `minBaseFee: 0` is technically possible but may cause issues with some tooling that rejects zero-fee transactions

### Recommended Minimal Fee Config for ARI

```json
"feeConfig": {
  "gasLimit": 12000000,
  "targetBlockRate": 2,
  "minBaseFee": 1,
  "targetGas": 15000000,
  "baseFeeChangeDenominator": 48,
  "minBlockGasCost": 0,
  "maxBlockGasCost": 0,
  "blockGasCostStep": 0
}
```

With `minBaseFee: 1` (1 wei), transaction costs are negligible. The deployer wallet pre-funded with 50M tokens will last effectively forever at this fee level.

If the FeeManager precompile is enabled, fees can be adjusted post-genesis without redeploying. The current genesis does not include FeeManager, which means the fee config is locked at genesis values.

---

## 8. Cross-References

- Architecture rationale: `docs/avalanche/01-architecture-overview.md`
- Teleporter pre-deploy in genesis: `docs/avalanche/04-ictt-bridge-integration.md`
- Validator node setup: `docs/avalanche/06-node-infrastructure.md`
- Current deployed genesis: `genesis-ariTR.json` (project root)
- Current setup guide: `docs/FUJI_L1_SETUP_GUIDE.md`
