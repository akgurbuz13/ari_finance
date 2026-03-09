# 03 - Smart Contract Best Practices for ARI

> **Key Takeaways**
> - ARI stablecoins should be OpenZeppelin ERC-20 + AccessControl + Pausable — a well-established, audited pattern
> - The `contractDeployerAllowListConfig` precompile restricts who can deploy contracts; the admin key managing this must be a multi-sig
> - On a permissioned Subnet-EVM, `viaIR: true` in hardhat.config resolves "stack too deep" on complex contracts
> - All amounts in ARI are `NUMERIC(20,8)` in PostgreSQL; on-chain amounts are always 18-decimal wei (`uint256`). Always multiply by 10^18 when going on-chain and divide by 10^18 when coming back.
> - Solidity 0.8.24 (used by ARI) already includes checked arithmetic; no SafeMath needed
> - Upgradeable proxy pattern (OpenZeppelin TransparentUpgradeableProxy or UUPS) is recommended for stablecoin and bridge contracts in production

---

## 1. Stablecoin Contract Pattern (AriStablecoin)

ARI's stablecoin (`AriStablecoin.sol`) implements the following pattern:

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Burnable.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Pausable.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";

contract AriStablecoin is ERC20, ERC20Burnable, ERC20Pausable, AccessControl {
    bytes32 public constant MINTER_ROLE = keccak256("MINTER_ROLE");
    bytes32 public constant PAUSER_ROLE = keccak256("PAUSER_ROLE");

    // KYC allowlist: only approved addresses may hold/transfer tokens
    mapping(address => bool) private _allowlist;

    // Events for auditability
    event TokensMinted(address indexed to, uint256 amount);
    event TokensBurned(address indexed from, uint256 amount);
    event AddedToAllowlist(address indexed account);
    event RemovedFromAllowlist(address indexed account);

    constructor(
        string memory name,
        string memory symbol,
        address admin
    ) ERC20(name, symbol) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(MINTER_ROLE, admin);
        _grantRole(PAUSER_ROLE, admin);
    }

    function mint(address to, uint256 amount) external onlyRole(MINTER_ROLE) {
        require(_allowlist[to], "AriStablecoin: recipient not on KYC allowlist");
        _mint(to, amount);
        emit TokensMinted(to, amount);
    }

    function burnFrom(address account, uint256 amount) public override onlyRole(MINTER_ROLE) {
        _burn(account, amount);
        emit TokensBurned(account, amount);
    }

    function pause() external onlyRole(PAUSER_ROLE) { _pause(); }
    function unpause() external onlyRole(PAUSER_ROLE) { _unpause(); }

    function addToAllowlist(address account) external onlyRole(DEFAULT_ADMIN_ROLE) {
        _allowlist[account] = true;
        emit AddedToAllowlist(account);
    }

    function removeFromAllowlist(address account) external onlyRole(DEFAULT_ADMIN_ROLE) {
        _allowlist[account] = false;
        emit RemovedFromAllowlist(account);
    }

    function allowlisted(address account) external view returns (bool) {
        return _allowlist[account];
    }

    // ERC-20 transfer hooks enforce allowlist on all transfers
    function _update(address from, address to, uint256 value)
        internal override(ERC20, ERC20Pausable)
    {
        // Allow minting (from == address(0)) and burning (to == address(0))
        if (from != address(0) && to != address(0)) {
            require(_allowlist[from], "AriStablecoin: sender not on allowlist");
            require(_allowlist[to], "AriStablecoin: recipient not on allowlist");
        }
        super._update(from, to, value);
    }
}
```

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| `AccessControl` over `Ownable` | Supports multiple roles (MINTER, PAUSER, ADMIN) independently |
| `ERC20Pausable` | Emergency pause for regulatory/security incidents |
| KYC allowlist in `_update` | Enforces at the transfer level, not just mint/burn — no allowlist bypass |
| `NUMERIC(20,8)` ↔ `uint256` with 18 decimals | Standard EVM denomination; BigDecimal conversion in blockchain-service |
| `emit` on every mint/burn | Required for `ChainEventListener` to index events |

---

## 2. Amount Conversion: BigDecimal to Wei

ARI uses `BigDecimal` in Kotlin and `NUMERIC(20,8)` in PostgreSQL. On-chain, all amounts are `uint256` in wei (18 decimal places).

### In blockchain-service (Kotlin)

```kotlin
import java.math.BigDecimal
import java.math.BigInteger

// BigDecimal (e.g., "1234.56789012") → wei BigInteger
fun toWei(amount: BigDecimal): BigInteger =
    amount.multiply(BigDecimal.TEN.pow(18)).toBigInteger()

// wei BigInteger → BigDecimal
fun fromWei(amountWei: BigInteger): BigDecimal =
    BigDecimal(amountWei).divide(BigDecimal.TEN.pow(18))

// Example usage in MintService.kt
val amountWei = toWei(amount)  // amount is BigDecimal from the outbox event
stablecoin.mint(toAddress, amountWei)
```

### Precision Warning

`NUMERIC(20,8)` supports 8 decimal places. EVM uses 18 decimal places. When converting from wei back to the ledger:

```kotlin
// Wei has 18 decimals; ledger has 8. The last 10 decimal places will be truncated.
// This is acceptable for TRY (Lira) and EUR where sub-satoshi precision is irrelevant.
val ledgerAmount = fromWei(amountWei).setScale(8, java.math.RoundingMode.HALF_UP)
```

Reconciliation (see `docs/avalanche/08-reconciliation-security.md`) must account for this truncation when comparing on-chain `totalSupply()` to the off-chain ledger sum.

---

## 3. Upgradability Pattern

For a regulated fintech platform, the ability to fix bugs in deployed contracts is essential. Two patterns are suitable:

### Option A: OpenZeppelin Transparent Proxy (Recommended for ARI)

```
ProxyAdmin (owned by multi-sig)
    |
    | controls upgrades of
    ↓
TransparentUpgradeableProxy
    |
    | delegates calls to
    ↓
AriStablecoin (implementation contract, upgradeable version)
```

Benefits:
- Multi-sig controls all upgrades (BDDK/MiCA auditability)
- Implementation contract is replaceable without changing the contract address
- Proxy address stays constant — no need to re-register with Teleporter

Deployment with Hardhat + OpenZeppelin Upgrades plugin:

```typescript
import { ethers, upgrades } from "hardhat";

const AriStablecoin = await ethers.getContractFactory("AriStablecoinUpgradeable");
const proxy = await upgrades.deployProxy(AriStablecoin, [
  "ARI Turkish Lira", "ariTRY", adminAddress
], { initializer: "initialize" });
await proxy.waitForDeployment();
console.log("Proxy deployed at:", await proxy.getAddress());
```

### Option B: UUPS (Universal Upgradeable Proxy Standard)

UUPS puts upgrade logic in the implementation rather than the proxy. Slightly more gas efficient but more error-prone (a faulty upgrade can brick the contract).

For ARI's risk profile, the Transparent Proxy pattern is safer.

### The ValidatorManager Is Already Upgradeable

The Ava Labs ValidatorManager contracts (`ValidatorManager`, `PoAManager`) already use the OpenZeppelin upgradeable pattern. This is a requirement since they need to be deployed before `ConvertSubnetToL1Tx` but initialized after it.

---

## 4. Access Control Roles for ARI

| Role | Holder | Purpose |
|------|--------|---------|
| `DEFAULT_ADMIN_ROLE` | Multi-sig wallet | Grant/revoke all roles, emergency control |
| `MINTER_ROLE` | blockchain-service minter wallet | Mint and burn stablecoins |
| `PAUSER_ROLE` | Multi-sig wallet or automated circuit breaker | Pause all transfers in emergency |
| Deployer AllowList Admin | Multi-sig wallet (via precompile) | Authorize new contract deployments |

The blockchain-service uses a single "minter" key (configured via `ari.blockchain.keys.minter`) that holds `MINTER_ROLE`. This key should be stored in AWS KMS or Azure Key Vault, not in a `.env` file.

### Role Separation for Compliance

BDDK and MiCA both require separation of duties:
- The minting key (operational) should not be able to upgrade the contract (governance)
- The admin key (governance) should be a multi-sig, not a hot wallet
- The pauser key should be able to act quickly — consider a 1-of-N multi-sig for it

---

## 5. Security Patterns

### Reentrancy Guards

`AriBurnMintBridge.sol` and `AriVehicleEscrow.sol` should use `ReentrancyGuard`:

```solidity
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract AriBurnMintBridge is ReentrancyGuard {
    function burnAndBridge(
        bytes32 destBlockchainID,
        address recipient,
        uint256 amount
    ) external nonReentrant {
        // ...
    }
}
```

The `TeleporterMessenger` already uses `ReentrancyGuard` internally.

### Integer Overflow

Solidity 0.8.24 (used by ARI) includes built-in checked arithmetic. Overflow/underflow will revert automatically. No SafeMath needed.

### Access Control on Sensitive Functions

```solidity
// All mint/burn functions must be restricted
function mint(address to, uint256 amount) external onlyRole(MINTER_ROLE) { ... }

// Admin functions should use a time-lock for production
// Consider OpenZeppelin TimelockController
function upgradeContract(...) external onlyRole(DEFAULT_ADMIN_ROLE) { ... }
```

### Allowlist Bypass Prevention

The KYC allowlist in `_update` (not just `transfer`) ensures no bypass:

```solidity
// BAD: Only checking in transfer() — transferFrom() can bypass it
function transfer(address to, uint256 amount) override public {
    require(allowlist[to], "not on allowlist");
    super.transfer(to, amount);
}

// GOOD: Override _update which is called by all transfer paths
function _update(address from, address to, uint256 value) internal override {
    if (from != address(0) && to != address(0)) {
        require(_allowlist[to], "not on allowlist");
    }
    super._update(from, to, value);
}
```

---

## 6. Gas Optimization for Permissioned L1

Since ARI's blockchain-service pays all gas fees (users never interact directly), gas optimization is about reducing ARI's operational costs rather than user experience.

### Relevant Optimizations

1. **Batch operations**: When provisioning many user wallets, batch allowlist additions in one transaction (requires a batcher contract or multicall)

2. **`viaIR: true` in Hardhat**: Required for ARI's contracts which are complex enough to hit the "stack too deep" error:
   ```typescript
   // hardhat.config.ts
   solidity: {
     version: "0.8.24",
     settings: {
       optimizer: { enabled: true, runs: 200 },
       viaIR: true
     }
   }
   ```

3. **Event indexing**: Use `indexed` parameters for fields that will be filtered in `ChainEventListener`. Non-indexed data is cheaper to store.

4. **Low gas price**: Set `minBaseFee: 1` in genesis (see `02-permissioned-l1-setup.md`). At 1 wei base fee, even complex transactions cost fractions of a cent.

---

## 7. Testing Strategy

ARI uses Hardhat with TypeScript for contract testing. Current test count: 183 tests.

### Test Setup Pattern for KYC Allowlist

```typescript
// Always required before any token operation
beforeEach(async () => {
    const [admin, user, bridgeOperator] = await ethers.getSigners();
    await stablecoin.addToAllowlist(user.address);
    await stablecoin.addToAllowlist(bridgeOperator.address);
    await stablecoin.addToAllowlist(await stablecoin.getAddress()); // if contract receives tokens
});
```

### AriBurnMintBridge: DEFAULT_ADMIN_ROLE Requirement

The bridge calls `stablecoin.addToAllowlist()` in `receiveTeleporterMessage()`, which requires `DEFAULT_ADMIN_ROLE`. Grant both `MINTER_ROLE` AND `DEFAULT_ADMIN_ROLE` (`ethers.ZeroHash`) to the bridge contract address:

```typescript
await stablecoin.grantRole(await stablecoin.MINTER_ROLE(), bridge.target);
await stablecoin.grantRole(ethers.ZeroHash, bridge.target); // DEFAULT_ADMIN_ROLE
```

### Coverage Target

For a regulated fintech platform, aim for 100% line coverage on:
- `AriStablecoin.sol`
- `AriBurnMintBridge.sol`
- `AriVehicleEscrow.sol`

Run coverage:
```bash
cd contracts && npx hardhat coverage
```

---

## 8. Auditing Recommendations

Before mainnet deployment:

1. **Internal review**: All contracts reviewed by at least 2 engineers
2. **Static analysis**: Run Slither (`pip install slither-analyzer && slither contracts/`) before external audit
3. **External audit**: Engage a recognized auditor (Trail of Bits, OpenZeppelin, Certik, etc.) with regulatory fintech experience
4. **MiCA compliance check**: EU stablecoin regulations (MiCA Article 36) require specific reserve and audit mechanisms — the smart contract audit should cover this
5. **Audit the validator manager**: The PoAManager/ValidatorManager contracts from Ava Labs are not ARI's own code but should be verified against the published audits in `ava-labs/icm-contracts/audits/`

---

## 9. Cross-References

- L1 permissioning setup: `docs/avalanche/02-permissioned-l1-setup.md`
- Bridge contracts: `docs/avalanche/04-ictt-bridge-integration.md`
- Reconciliation and on-chain verification: `docs/avalanche/08-reconciliation-security.md`
- Contract source code: `contracts/` directory
- Deployed contracts: configured in `blockchain-service/src/main/resources/application-fuji.yml`
