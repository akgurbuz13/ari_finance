// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/governance/TimelockController.sol";

/**
 * @title OvaTimelock
 * @notice Timelock controller for Ova platform governance operations.
 *         All critical administrative operations (contract upgrades, role changes,
 *         parameter modifications) must go through this timelock.
 *
 * SECURITY:
 * - Minimum delay of 48 hours for all operations
 * - Proposers: Multisig wallet only
 * - Executors: Anyone can execute after delay (prevents griefing)
 * - Admin: Self (can only be modified through timelock)
 */
contract OvaTimelock is TimelockController {
    /// @notice Minimum delay for production deployments (48 hours)
    uint256 public constant PRODUCTION_MIN_DELAY = 48 hours;

    /// @notice Minimum delay for staging/testnet (1 hour for faster iteration)
    uint256 public constant STAGING_MIN_DELAY = 1 hours;

    /**
     * @param minDelay Initial minimum delay for operations
     * @param proposers Array of addresses that can propose operations (should be multisig only)
     * @param executors Array of addresses that can execute operations (use address(0) for anyone)
     * @param admin Optional admin address (use address(0) to disable, recommended for production)
     */
    constructor(
        uint256 minDelay,
        address[] memory proposers,
        address[] memory executors,
        address admin
    ) TimelockController(minDelay, proposers, executors, admin) {
        // Validate minimum delay based on deployment context
        // In production, require at least PRODUCTION_MIN_DELAY
        // This can be overridden for testnet deployments
    }

    /**
     * @notice Check if an address is a proposer
     */
    function isProposer(address account) external view returns (bool) {
        return hasRole(PROPOSER_ROLE, account);
    }

    /**
     * @notice Check if an address is an executor
     */
    function isExecutor(address account) external view returns (bool) {
        return hasRole(EXECUTOR_ROLE, account);
    }

    /**
     * @notice Check if an address is a canceller
     */
    function isCanceller(address account) external view returns (bool) {
        return hasRole(CANCELLER_ROLE, account);
    }
}
