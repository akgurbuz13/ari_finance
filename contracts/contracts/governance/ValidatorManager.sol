// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

/**
 * @title ValidatorManager
 * @notice Manages the validator set for ARI's Proof-of-Authority L1 chains.
 *         This contract handles validator registration, rotation, and removal.
 *
 * AVALANCHE POST-ETNA (Dec 2024):
 * - No 2000 AVAX stake requirement for permissioned L1s
 * - Validators managed through this contract
 * - Uses BLS signature aggregation via ICM relayer
 *
 * SECURITY:
 * - Minimum 3 validators required for consensus
 * - Maximum 10 validators to maintain performance
 * - Validator changes require quorum approval
 * - 24-hour delay for validator removals (prevents attack scenarios)
 */
contract ValidatorManager is AccessControl, ReentrancyGuard {
    bytes32 public constant VALIDATOR_ADMIN_ROLE = keccak256("VALIDATOR_ADMIN_ROLE");

    struct Validator {
        address addr;           // Validator address (for rewards/slashing)
        bytes32 nodeId;         // Avalanche Node ID
        bytes blsPublicKey;     // BLS public key for ICM signing
        uint256 addedAt;        // Timestamp when validator was added
        uint256 removalRequestedAt; // Timestamp when removal was requested (0 if not requested)
        bool isActive;          // Whether validator is currently active
    }

    /// @notice Minimum number of validators required
    uint256 public constant MIN_VALIDATORS = 3;

    /// @notice Maximum number of validators allowed
    uint256 public constant MAX_VALIDATORS = 10;

    /// @notice Delay before a validator can be removed (24 hours)
    uint256 public constant REMOVAL_DELAY = 24 hours;

    /// @notice Array of all validator addresses
    address[] public validatorAddresses;

    /// @notice Mapping from address to validator info
    mapping(address => Validator) public validators;

    /// @notice Mapping from node ID to validator address
    mapping(bytes32 => address) public nodeIdToAddress;

    /// @notice Current number of active validators
    uint256 public activeValidatorCount;

    // Events
    event ValidatorAdded(
        address indexed validator,
        bytes32 indexed nodeId,
        bytes blsPublicKey
    );
    event ValidatorRemovalRequested(
        address indexed validator,
        uint256 removalTime
    );
    event ValidatorRemoved(
        address indexed validator,
        bytes32 indexed nodeId
    );
    event ValidatorReactivated(
        address indexed validator
    );

    constructor(address admin) {
        require(admin != address(0), "Admin cannot be zero address");
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(VALIDATOR_ADMIN_ROLE, admin);
    }

    /**
     * @notice Add a new validator to the set
     * @param validatorAddr Validator's reward address
     * @param nodeId Avalanche Node ID (20 bytes, typically hashed)
     * @param blsPublicKey BLS public key for ICM message signing
     */
    function addValidator(
        address validatorAddr,
        bytes32 nodeId,
        bytes calldata blsPublicKey
    ) external onlyRole(VALIDATOR_ADMIN_ROLE) {
        require(validatorAddr != address(0), "Invalid validator address");
        require(nodeId != bytes32(0), "Invalid node ID");
        require(blsPublicKey.length == 48, "Invalid BLS public key length");
        require(!validators[validatorAddr].isActive, "Validator already active");
        require(nodeIdToAddress[nodeId] == address(0), "Node ID already registered");
        require(activeValidatorCount < MAX_VALIDATORS, "Maximum validators reached");

        validators[validatorAddr] = Validator({
            addr: validatorAddr,
            nodeId: nodeId,
            blsPublicKey: blsPublicKey,
            addedAt: block.timestamp,
            removalRequestedAt: 0,
            isActive: true
        });

        nodeIdToAddress[nodeId] = validatorAddr;
        validatorAddresses.push(validatorAddr);
        activeValidatorCount++;

        emit ValidatorAdded(validatorAddr, nodeId, blsPublicKey);
    }

    /**
     * @notice Request removal of a validator (starts delay period)
     * @param validatorAddr Validator address to remove
     */
    function requestRemoveValidator(address validatorAddr) external onlyRole(VALIDATOR_ADMIN_ROLE) {
        require(validators[validatorAddr].isActive, "Validator not active");
        require(validators[validatorAddr].removalRequestedAt == 0, "Removal already requested");
        require(activeValidatorCount > MIN_VALIDATORS, "Cannot go below minimum validators");

        validators[validatorAddr].removalRequestedAt = block.timestamp;

        emit ValidatorRemovalRequested(
            validatorAddr,
            block.timestamp + REMOVAL_DELAY
        );
    }

    /**
     * @notice Complete validator removal after delay period
     * @param validatorAddr Validator address to remove
     */
    function completeRemoveValidator(address validatorAddr) external onlyRole(VALIDATOR_ADMIN_ROLE) nonReentrant {
        Validator storage validator = validators[validatorAddr];
        require(validator.isActive, "Validator not active");
        require(validator.removalRequestedAt > 0, "Removal not requested");
        require(
            block.timestamp >= validator.removalRequestedAt + REMOVAL_DELAY,
            "Removal delay not passed"
        );
        require(activeValidatorCount > MIN_VALIDATORS, "Cannot go below minimum validators");

        validator.isActive = false;
        activeValidatorCount--;

        bytes32 nodeId = validator.nodeId;
        delete nodeIdToAddress[nodeId];

        emit ValidatorRemoved(validatorAddr, nodeId);
    }

    /**
     * @notice Cancel a pending removal request
     * @param validatorAddr Validator address
     */
    function cancelRemoval(address validatorAddr) external onlyRole(VALIDATOR_ADMIN_ROLE) {
        require(validators[validatorAddr].removalRequestedAt > 0, "No pending removal");
        validators[validatorAddr].removalRequestedAt = 0;
        emit ValidatorReactivated(validatorAddr);
    }

    /**
     * @notice Emergency removal of a validator (bypasses delay)
     * @dev Only for compromised validators - requires DEFAULT_ADMIN_ROLE
     * @param validatorAddr Validator address to remove
     */
    function emergencyRemoveValidator(address validatorAddr) external onlyRole(DEFAULT_ADMIN_ROLE) nonReentrant {
        Validator storage validator = validators[validatorAddr];
        require(validator.isActive, "Validator not active");
        require(activeValidatorCount > MIN_VALIDATORS, "Cannot go below minimum validators");

        validator.isActive = false;
        activeValidatorCount--;

        bytes32 nodeId = validator.nodeId;
        delete nodeIdToAddress[nodeId];

        emit ValidatorRemoved(validatorAddr, nodeId);
    }

    /**
     * @notice Update a validator's BLS public key (key rotation)
     * @param validatorAddr Validator address
     * @param newBlsPublicKey New BLS public key
     */
    function updateValidatorKey(
        address validatorAddr,
        bytes calldata newBlsPublicKey
    ) external onlyRole(VALIDATOR_ADMIN_ROLE) {
        require(validators[validatorAddr].isActive, "Validator not active");
        require(newBlsPublicKey.length == 48, "Invalid BLS public key length");
        validators[validatorAddr].blsPublicKey = newBlsPublicKey;
    }

    /**
     * @notice Get all active validators
     * @return addresses Array of active validator addresses
     * @return nodeIds Array of corresponding node IDs
     * @return blsKeys Array of corresponding BLS public keys
     */
    function getActiveValidators()
        external
        view
        returns (
            address[] memory addresses,
            bytes32[] memory nodeIds,
            bytes[] memory blsKeys
        )
    {
        addresses = new address[](activeValidatorCount);
        nodeIds = new bytes32[](activeValidatorCount);
        blsKeys = new bytes[](activeValidatorCount);

        uint256 index = 0;
        for (uint256 i = 0; i < validatorAddresses.length; i++) {
            address addr = validatorAddresses[i];
            if (validators[addr].isActive) {
                addresses[index] = addr;
                nodeIds[index] = validators[addr].nodeId;
                blsKeys[index] = validators[addr].blsPublicKey;
                index++;
            }
        }

        return (addresses, nodeIds, blsKeys);
    }

    /**
     * @notice Get validator info
     */
    function getValidator(address validatorAddr)
        external
        view
        returns (
            bytes32 nodeId,
            bytes memory blsPublicKey,
            uint256 addedAt,
            uint256 removalRequestedAt,
            bool isActive
        )
    {
        Validator memory v = validators[validatorAddr];
        return (v.nodeId, v.blsPublicKey, v.addedAt, v.removalRequestedAt, v.isActive);
    }

    /**
     * @notice Check if an address is an active validator
     */
    function isActiveValidator(address validatorAddr) external view returns (bool) {
        return validators[validatorAddr].isActive;
    }

    /**
     * @notice Get the quorum required for consensus (2/3 + 1)
     */
    function getQuorum() external view returns (uint256) {
        return (activeValidatorCount * 2) / 3 + 1;
    }
}
