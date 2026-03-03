// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts-upgradeable/token/ERC20/ERC20Upgradeable.sol";
import "@openzeppelin/contracts-upgradeable/access/AccessControlUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/utils/PausableUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";

/**
 * @title AriStablecoinUpgradeable
 * @notice UUPS-upgradeable ERC-20 stablecoin with mint/burn/freeze/allowlist capabilities.
 *         Deployed per L1 chain (TR L1 for TRY, EU L1 for EUR).
 *         Only KYC-verified addresses can hold or transfer tokens.
 *
 * SECURITY:
 * - Upgrades require UPGRADER_ROLE (should be timelock-protected multisig)
 * - Upgrade capability can be permanently disabled by renouncing UPGRADER_ROLE
 * - All role grants should go through timelock for critical roles
 *
 * REGULATORY:
 * - Supports freeze for regulatory compliance (court orders, sanctions)
 * - Supports pause for emergency response
 * - Burn requires explicit authorization (for redemption workflows)
 */
contract AriStablecoinUpgradeable is
    Initializable,
    ERC20Upgradeable,
    AccessControlUpgradeable,
    PausableUpgradeable,
    UUPSUpgradeable
{
    bytes32 public constant MINTER_ROLE = keccak256("MINTER_ROLE");
    bytes32 public constant FREEZER_ROLE = keccak256("FREEZER_ROLE");
    bytes32 public constant UPGRADER_ROLE = keccak256("UPGRADER_ROLE");

    /// @notice Mapping of KYC-verified addresses that can hold/transfer tokens
    mapping(address => bool) public allowlisted;

    /// @notice Mapping of frozen addresses (regulatory holds, sanctions)
    mapping(address => bool) public frozen;

    /// @notice Maximum supply cap (0 = unlimited)
    uint256 public supplyCap;

    /// @notice Version identifier for tracking upgrades
    uint256 public constant VERSION = 1;

    // Events
    event AddressAllowlisted(address indexed account, address indexed addedBy);
    event AddressRemovedFromAllowlist(address indexed account, address indexed removedBy);
    event AddressFrozen(address indexed account, address indexed frozenBy, string reason);
    event AddressUnfrozen(address indexed account, address indexed unfrozenBy);
    event TokensMinted(address indexed to, uint256 amount, address indexed mintedBy);
    event TokensBurned(address indexed from, uint256 amount, address indexed burnedBy);
    event SupplyCapUpdated(uint256 oldCap, uint256 newCap);

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    /**
     * @notice Initialize the stablecoin (replaces constructor for upgradeable contracts)
     * @param name_ Token name (e.g., "ARI Turkish Lira")
     * @param symbol_ Token symbol (e.g., "ariTRY")
     * @param admin Initial admin address (should be multisig or timelock)
     * @param minter Initial minter address (backend service key)
     * @param supplyCap_ Maximum supply cap (0 for unlimited)
     */
    function initialize(
        string memory name_,
        string memory symbol_,
        address admin,
        address minter,
        uint256 supplyCap_
    ) public initializer {
        require(admin != address(0), "Admin cannot be zero address");
        require(minter != address(0), "Minter cannot be zero address");

        __ERC20_init(name_, symbol_);
        __AccessControl_init();
        __Pausable_init();
        __UUPSUpgradeable_init();

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(UPGRADER_ROLE, admin);
        _grantRole(MINTER_ROLE, minter);
        _grantRole(FREEZER_ROLE, admin);

        supplyCap = supplyCap_;

        // Allow the contract itself and zero address for mint/burn operations
        allowlisted[address(this)] = true;
        allowlisted[address(0)] = true;
    }

    /**
     * @notice Mint new tokens to a KYC-verified address
     * @param to Recipient address (must be allowlisted)
     * @param amount Amount to mint
     */
    function mint(address to, uint256 amount) external onlyRole(MINTER_ROLE) whenNotPaused {
        require(allowlisted[to], "AriStablecoin: recipient not KYC verified");
        require(!frozen[to], "AriStablecoin: recipient account frozen");

        if (supplyCap > 0) {
            require(totalSupply() + amount <= supplyCap, "AriStablecoin: supply cap exceeded");
        }

        _mint(to, amount);
        emit TokensMinted(to, amount, msg.sender);
    }

    /**
     * @notice Burn tokens from an address (for redemption)
     * @param from Address to burn from (must be allowlisted)
     * @param amount Amount to burn
     */
    function burn(address from, uint256 amount) external onlyRole(MINTER_ROLE) whenNotPaused {
        require(allowlisted[from], "AriStablecoin: holder not KYC verified");
        _burn(from, amount);
        emit TokensBurned(from, amount, msg.sender);
    }

    /**
     * @notice Freeze an account (regulatory requirement)
     * @param account Address to freeze
     * @param reason Reason for freezing (for audit trail)
     */
    function freeze(address account, string calldata reason) external onlyRole(FREEZER_ROLE) {
        require(account != address(0), "Cannot freeze zero address");
        frozen[account] = true;
        emit AddressFrozen(account, msg.sender, reason);
    }

    /**
     * @notice Legacy freeze function for backwards compatibility
     */
    function freeze(address account) external onlyRole(FREEZER_ROLE) {
        require(account != address(0), "Cannot freeze zero address");
        frozen[account] = true;
        emit AddressFrozen(account, msg.sender, "");
    }

    /**
     * @notice Unfreeze an account
     * @param account Address to unfreeze
     */
    function unfreeze(address account) external onlyRole(FREEZER_ROLE) {
        frozen[account] = false;
        emit AddressUnfrozen(account, msg.sender);
    }

    /**
     * @notice Add an address to the allowlist (KYC verified)
     * @param account Address to allowlist
     */
    function addToAllowlist(address account) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(account != address(0), "Cannot allowlist zero address");
        allowlisted[account] = true;
        emit AddressAllowlisted(account, msg.sender);
    }

    /**
     * @notice Remove an address from the allowlist
     * @param account Address to remove
     */
    function removeFromAllowlist(address account) external onlyRole(DEFAULT_ADMIN_ROLE) {
        allowlisted[account] = false;
        emit AddressRemovedFromAllowlist(account, msg.sender);
    }

    /**
     * @notice Batch add addresses to the allowlist
     * @param accounts Array of addresses to allowlist
     */
    function batchAddToAllowlist(address[] calldata accounts) external onlyRole(DEFAULT_ADMIN_ROLE) {
        for (uint256 i = 0; i < accounts.length; i++) {
            if (accounts[i] != address(0)) {
                allowlisted[accounts[i]] = true;
                emit AddressAllowlisted(accounts[i], msg.sender);
            }
        }
    }

    /**
     * @notice Update the supply cap
     * @param newCap New supply cap (0 for unlimited)
     */
    function setSupplyCap(uint256 newCap) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(newCap == 0 || newCap >= totalSupply(), "New cap below current supply");
        emit SupplyCapUpdated(supplyCap, newCap);
        supplyCap = newCap;
    }

    /**
     * @notice Pause all token transfers and minting
     */
    function pause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _pause();
    }

    /**
     * @notice Unpause token operations
     */
    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _unpause();
    }

    /**
     * @notice Internal transfer hook with KYC/freeze checks
     */
    function _update(
        address from,
        address to,
        uint256 value
    ) internal override whenNotPaused {
        // Skip checks for mint (from == 0) and burn (to == 0)
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

    /**
     * @notice Authorize contract upgrade (UUPS pattern)
     * @dev Only addresses with UPGRADER_ROLE can upgrade
     */
    function _authorizeUpgrade(address newImplementation) internal override onlyRole(UPGRADER_ROLE) {
        // Additional validation can be added here
        // e.g., require newImplementation to be a verified contract
    }

    /**
     * @notice Check if an address can receive tokens
     */
    function canReceive(address account) external view returns (bool) {
        return allowlisted[account] && !frozen[account];
    }

    /**
     * @notice Check if an address can send tokens
     */
    function canSend(address account) external view returns (bool) {
        return allowlisted[account] && !frozen[account];
    }
}
