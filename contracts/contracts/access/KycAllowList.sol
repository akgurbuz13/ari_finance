// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/access/AccessControl.sol";

/**
 * @title KycAllowList
 * @notice Standalone KYC allowlist registry that can be shared across multiple contracts.
 *         The AriStablecoin contract can reference this for KYC status checks.
 */
contract KycAllowList is AccessControl {
    bytes32 public constant KYC_OPERATOR_ROLE = keccak256("KYC_OPERATOR_ROLE");

    mapping(address => bool) public isVerified;
    mapping(address => uint256) public verifiedAt;
    mapping(address => uint256) public expiresAt;

    event KycVerified(address indexed account, uint256 expiresAt);
    event KycRevoked(address indexed account);

    constructor() {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(KYC_OPERATOR_ROLE, msg.sender);
    }

    function verify(address account, uint256 validityPeriod) external onlyRole(KYC_OPERATOR_ROLE) {
        isVerified[account] = true;
        verifiedAt[account] = block.timestamp;
        expiresAt[account] = block.timestamp + validityPeriod;
        emit KycVerified(account, expiresAt[account]);
    }

    function batchVerify(
        address[] calldata accounts,
        uint256 validityPeriod
    ) external onlyRole(KYC_OPERATOR_ROLE) {
        for (uint256 i = 0; i < accounts.length; i++) {
            isVerified[accounts[i]] = true;
            verifiedAt[accounts[i]] = block.timestamp;
            expiresAt[accounts[i]] = block.timestamp + validityPeriod;
            emit KycVerified(accounts[i], expiresAt[accounts[i]]);
        }
    }

    function revoke(address account) external onlyRole(KYC_OPERATOR_ROLE) {
        isVerified[account] = false;
        emit KycRevoked(account);
    }

    function isKycValid(address account) external view returns (bool) {
        return isVerified[account] && block.timestamp < expiresAt[account];
    }
}
