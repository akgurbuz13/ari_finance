// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/Pausable.sol";

/**
 * @title OvaStablecoin
 * @notice ERC-20 stablecoin with mint/burn/freeze/allowlist capabilities.
 *         Deployed per L1 chain (TR L1 for TRY, EU L1 for EUR).
 *         Only KYC-verified addresses can hold or transfer tokens.
 */
contract OvaStablecoin is ERC20, AccessControl, Pausable {
    bytes32 public constant MINTER_ROLE = keccak256("MINTER_ROLE");
    bytes32 public constant FREEZER_ROLE = keccak256("FREEZER_ROLE");

    mapping(address => bool) public allowlisted;
    mapping(address => bool) public frozen;

    event AddressAllowlisted(address indexed account);
    event AddressRemovedFromAllowlist(address indexed account);
    event AddressFrozen(address indexed account);
    event AddressUnfrozen(address indexed account);
    event TokensMinted(address indexed to, uint256 amount);
    event TokensBurned(address indexed from, uint256 amount);

    constructor(
        string memory name_,
        string memory symbol_
    ) ERC20(name_, symbol_) {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(MINTER_ROLE, msg.sender);
        _grantRole(FREEZER_ROLE, msg.sender);

        // Allow the contract itself and zero address for mint/burn
        allowlisted[address(this)] = true;
        allowlisted[address(0)] = true;
    }

    function mint(address to, uint256 amount) external onlyRole(MINTER_ROLE) whenNotPaused {
        require(allowlisted[to], "OvaStablecoin: recipient not KYC verified");
        require(!frozen[to], "OvaStablecoin: recipient account frozen");
        _mint(to, amount);
        emit TokensMinted(to, amount);
    }

    function burn(address from, uint256 amount) external onlyRole(MINTER_ROLE) whenNotPaused {
        require(allowlisted[from], "OvaStablecoin: holder not KYC verified");
        _burn(from, amount);
        emit TokensBurned(from, amount);
    }

    function freeze(address account) external onlyRole(FREEZER_ROLE) {
        frozen[account] = true;
        emit AddressFrozen(account);
    }

    function unfreeze(address account) external onlyRole(FREEZER_ROLE) {
        frozen[account] = false;
        emit AddressUnfrozen(account);
    }

    function addToAllowlist(address account) external onlyRole(DEFAULT_ADMIN_ROLE) {
        allowlisted[account] = true;
        emit AddressAllowlisted(account);
    }

    function removeFromAllowlist(address account) external onlyRole(DEFAULT_ADMIN_ROLE) {
        allowlisted[account] = false;
        emit AddressRemovedFromAllowlist(account);
    }

    function batchAddToAllowlist(address[] calldata accounts) external onlyRole(DEFAULT_ADMIN_ROLE) {
        for (uint256 i = 0; i < accounts.length; i++) {
            allowlisted[accounts[i]] = true;
            emit AddressAllowlisted(accounts[i]);
        }
    }

    function pause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _pause();
    }

    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _unpause();
    }

    function _update(
        address from,
        address to,
        uint256 value
    ) internal override whenNotPaused {
        // Skip checks for mint (from == 0) and burn (to == 0)
        if (from != address(0)) {
            require(allowlisted[from], "OvaStablecoin: sender not KYC verified");
            require(!frozen[from], "OvaStablecoin: sender account frozen");
        }
        if (to != address(0)) {
            require(allowlisted[to], "OvaStablecoin: recipient not KYC verified");
            require(!frozen[to], "OvaStablecoin: recipient account frozen");
        }
        super._update(from, to, value);
    }
}
