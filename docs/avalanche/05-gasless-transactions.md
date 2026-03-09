# 05 - Gasless Transactions on ARI's Permissioned L1

> **Key Takeaways**
> - The simplest "gasless" approach for ARI is to set `minBaseFee` very low (1 wei) and pre-fund every user wallet with a small native gas balance from the blockchain-service operator wallet
> - ERC-2771 meta-transactions are supported on Subnet-EVM but add significant contract complexity; they are not necessary when the blockchain-service can pay gas directly
> - The FeeManager precompile (`0x0200...0003`) allows on-chain fee adjustments without a hard fork
> - True zero-fee chains (`minBaseFee: 0`) are technically possible but complicate Subnet-EVM's EIP-1559 fee algorithm; 1 wei is the recommended minimum
> - For ARI's model where the blockchain-service controls all wallets, gas sponsorship is trivially solved by the operator wallet pattern — the blockchain-service pays all gas

---

## 1. ARI's Gas Model: Who Pays?

On a public EVM like Ethereum, every user pays their own gas. ARI's permissioned L1 has a fundamentally different model:

- **Users never interact with the blockchain directly.** All on-chain operations (mint, burn, bridge) are triggered by the blockchain-service on behalf of users.
- **The blockchain-service operator wallet pays all gas.** This wallet is the `MINTER_ROLE` holder configured via `ari.blockchain.keys.minter`.
- **Users hold native gas tokens only if they call contracts themselves** — which ARI's current architecture does not support.

This means "gasless" for ARI primarily means: ensure the operator wallet never runs out of gas, and ensure per-transaction gas costs are negligible.

---

## 2. Near-Zero Fee Configuration (Recommended for ARI)

### Current vs. Recommended Genesis Fee Config

ARI's current `genesis-ariTR.json` has:
```json
"feeConfig": {
  "minBaseFee": 25000000000
}
```

This is 25 gwei — the same as Ethereum's historical base fee. On a permissioned chain with no competition for blockspace, this is unnecessarily high and costs the operator real money at scale.

### Recommended Production Fee Config

```json
"feeConfig": {
  "gasLimit": 12000000,
  "targetBlockRate": 2,
  "minBaseFee": 1,
  "targetGas": 15000000,
  "baseFeeChangeDenominator": 36,
  "minBlockGasCost": 0,
  "maxBlockGasCost": 1000000,
  "blockGasCostStep": 200000
}
```

With `minBaseFee: 1` (1 wei), a complex ERC-20 mint transaction costs roughly:
- Gas used: ~50,000 gas (standard ERC-20 mint with event)
- Cost: 50,000 * 1 wei = 50,000 wei = 0.00000000005 native tokens

This is effectively free. The operator wallet needs only a small initial allocation and will almost never deplete.

### Can minBaseFee Be Zero?

Technically, Subnet-EVM supports `minBaseFee: 0`. However:
- The EIP-1559 algorithm divides by `baseFeeChangeDenominator` to compute fee adjustments, which can cause division-by-zero edge cases when base fee is 0
- Some Web3 libraries (including web3j used by blockchain-service) behave unexpectedly when `gasPrice` is 0
- The recommended minimum is `1` (1 wei), which is effectively gasless for all practical purposes

---

## 3. FeeManager Precompile for Dynamic Adjustment

The FeeManager precompile (`0x0200000000000000000000000000000000000003`) allows changing fee parameters on-chain without a hard fork. This is important for production operations.

### Adding FeeManager to Genesis

```json
"feeManagerConfig": {
  "blockTimestamp": 0,
  "adminAddresses": ["<MULTI_SIG_ADDRESS>"],
  "initialFeeConfig": {
    "gasLimit": 12000000,
    "targetBlockRate": 2,
    "minBaseFee": 1,
    "targetGas": 15000000,
    "baseFeeChangeDenominator": 36,
    "minBlockGasCost": 0,
    "maxBlockGasCost": 1000000,
    "blockGasCostStep": 200000
  }
}
```

### Adjusting Fees On-Chain

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

// IFeeManager interface at 0x0200000000000000000000000000000000000003
interface IFeeManager {
    function setFeeConfig(
        uint256 gasLimit,
        uint256 targetBlockRate,
        uint256 minBaseFee,
        uint256 targetGas,
        uint256 baseFeeChangeDenominator,
        uint256 minBlockGasCost,
        uint256 maxBlockGasCost,
        uint256 blockGasCostStep
    ) external;

    function getFeeConfig() external view returns (
        uint256 gasLimit,
        uint256 targetBlockRate,
        uint256 minBaseFee,
        uint256 targetGas,
        uint256 baseFeeChangeDenominator,
        uint256 minBlockGasCost,
        uint256 maxBlockGasCost,
        uint256 blockGasCostStep
    );
}
```

### When to Use FeeManager

| Scenario | Action |
|----------|--------|
| Chain under unexpected load | Increase `minBaseFee` to rate-limit |
| Cost reduction initiative | Decrease `minBaseFee` to 1 |
| Block size optimization | Adjust `gasLimit` |
| Emergency spam prevention | Temporarily increase fees |

---

## 4. Operator Wallet Gas Management

The blockchain-service's operator (minter) wallet must always have sufficient native gas tokens. This is the primary operational concern.

### Gas Estimation per Operation

| Operation | Approximate Gas | Cost at 1 wei/gas |
|-----------|-----------------|-------------------|
| ERC-20 mint | ~50,000 | negligible |
| ERC-20 burn | ~35,000 | negligible |
| AriBurnMintBridge.burnAndBridge() | ~150,000 | negligible |
| AriTokenHome.send() (ICTT) | ~200,000 | negligible |
| addToAllowlist() | ~45,000 | negligible |
| Contract deployment | ~1,000,000–3,000,000 | negligible |

At 1 wei gas price, 1,000,000 transactions would consume 0.05 native tokens from the genesis allocation. The standard 50M native token genesis allocation (see `02-permissioned-l1-setup.md`) is effectively inexhaustible.

### Kotlin: Setting Gas in web3j

When `minBaseFee` is very low, web3j transactions should specify gas explicitly rather than relying on estimation to avoid any library edge cases:

```kotlin
import org.web3j.tx.gas.StaticGasProvider
import java.math.BigInteger

// For permissioned L1 with minBaseFee=1
val gasProvider = StaticGasProvider(
    BigInteger.ONE,           // gasPrice: 1 wei
    BigInteger.valueOf(500_000) // gasLimit: 500k for complex txs
)

// Use with contract wrapper
val stablecoin = AriStablecoin.load(
    contractAddress,
    web3j,
    credentials,
    gasProvider
)
```

For more accurate gas limits per operation, use estimation with a safety multiplier:

```kotlin
fun estimateGasWithSafety(web3j: Web3j, transaction: Transaction): BigInteger {
    val estimated = web3j.ethEstimateGas(transaction).send()
        .amountUsed
    return estimated.multiply(BigInteger.valueOf(130)).divide(BigInteger.valueOf(100)) // 30% safety margin
}
```

---

## 5. ERC-2771 Meta-Transactions (Reference Only)

ERC-2771 allows a "forwarder" contract to relay transactions on behalf of users, paying gas while the original signer's address appears as `msg.sender`. This is the standard "gasless" pattern on public chains.

**ARI does not need ERC-2771** because the blockchain-service already acts as the relayer — it initiates all transactions from the operator wallet and applies user context at the application layer. However, the pattern is documented here for completeness in case a future feature requires user-signed transactions.

### ERC-2771 Architecture

```
User (signs message, no ETH)
    |
    | signed EIP-712 message
    ↓
Forwarder Contract
    |
    | verifies signature, pays gas
    | injects original sender into calldata
    ↓
Target Contract (reads _msgSender() for original user)
```

### Forwarder Contract Example

A minimal ERC-2771-compatible contract uses OpenZeppelin's `ERC2771Context`:

```solidity
import "@openzeppelin/contracts/metatx/ERC2771Context.sol";

contract AriStablecoinV2 is ERC2771Context, ERC20, AccessControl {
    constructor(address trustedForwarder, string memory name, string memory symbol)
        ERC2771Context(trustedForwarder)
        ERC20(name, symbol)
    {}

    // _msgSender() returns original user address (not forwarder)
    function mint(address to, uint256 amount) external onlyRole(MINTER_ROLE) {
        // _msgSender() here correctly returns the user, not the forwarder
        _mint(to, amount);
    }
}
```

### Why ARI Does NOT Use ERC-2771

| Factor | ERC-2771 | ARI's Operator Wallet Pattern |
|--------|----------|-------------------------------|
| Complexity | High (forwarder deployment, signature verification) | Low (single operator key) |
| Security surface | Larger (forwarder is an attack vector) | Smaller |
| Compliance auditability | Harder (meta-tx obscures initiator) | Easier (blockchain-service is always the initiator) |
| User experience | Better for decentralized dApps | Same (users never interact with chain directly) |
| Applicable to ARI | No | Yes |

---

## 6. Account Abstraction (ZeroDev Integration)

For future use cases where ARI users might need to sign transactions directly (e.g., a decentralized DeFi extension), the ZeroDev account abstraction toolkit (listed in Avalanche integrations) supports:

- Gasless transactions via a paymaster
- Smart accounts (ERC-4337 compatible)
- Session keys for scoped permissions

ZeroDev works with any EVM chain, including Subnet-EVM. However, deploying a full ERC-4337 infrastructure (EntryPoint contract, paymaster, bundler) on a permissioned L1 is significant overhead and is not recommended for the current ARI architecture.

---

## 7. Native Minter Precompile for Gas Tokens

If the operator wallet ever runs low on native gas tokens, the NativeMinter precompile (`0x0200000000000000000000000000000000000001`) can mint additional tokens programmatically:

```solidity
interface INativeMinter {
    function mintNativeCoin(address addr, uint256 amount) external;
}

contract AriGasRefiller {
    address constant NATIVE_MINTER = 0x0200000000000000000000000000000000000001;

    // Only callable by addresses with NativeMinter MINTER role
    function refillOperatorWallet(address operatorWallet) external {
        INativeMinter(NATIVE_MINTER).mintNativeCoin(
            operatorWallet,
            1_000_000 ether // mint 1M native tokens
        );
    }
}
```

This requires granting the calling contract (or EOA) `MINTER` role on the NativeMinter precompile in genesis:

```json
"nativeMinterConfig": {
  "blockTimestamp": 0,
  "adminAddresses": ["<MULTI_SIG_ADDRESS>"],
  "enabledAddresses": ["<REFILLER_CONTRACT_OR_OPERATOR_ADDRESS>"]
}
```

For ARI, the simpler approach is to allocate a very large initial balance in genesis (50M native tokens) and never need to top up.

---

## 8. Operational Checklist for Gas Management

Before production deployment:

- [ ] Genesis `alloc` for operator wallet has at least 50,000,000 native tokens (see `02-permissioned-l1-setup.md`)
- [ ] Genesis `minBaseFee` is set to 1 wei (not 25 gwei)
- [ ] `feeManagerConfig` is enabled with the multi-sig as admin (allows future adjustments)
- [ ] blockchain-service uses `StaticGasProvider` with explicit `gasPrice = 1 wei` to avoid library issues
- [ ] Monitoring alert set at 10% of initial operator wallet balance
- [ ] `NativeMinterConfig` enabled (with multi-sig admin) as emergency top-up mechanism
- [ ] Per-transaction gas limits are generous enough for complex operations (500,000 gas covers all ARI operations)

---

## 9. Cross-References

- Fee configuration in genesis: `docs/avalanche/02-permissioned-l1-setup.md`
- Node infrastructure and operator key management: `docs/avalanche/06-node-infrastructure.md`
- Security considerations for operator key: `docs/avalanche/08-reconciliation-security.md`
- FeeManager precompile official docs: `https://build.avax.network/docs/avalanche-l1s/precompiles/fee-manager`
