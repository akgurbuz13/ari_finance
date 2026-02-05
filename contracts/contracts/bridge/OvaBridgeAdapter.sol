// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

interface IOvaTokenHome {
    function bridgeTokens(
        bytes32 destinationChainID,
        address recipient,
        uint256 amount,
        uint256 feeAmount
    ) external returns (bytes32 messageID);
}

interface IOvaTokenRemote {
    function bridgeBack(
        address recipient,
        uint256 amount,
        uint256 feeAmount
    ) external returns (bytes32 messageID);
}

/**
 * @title OvaBridgeAdapter
 * @notice High-level adapter for Ova cross-chain transfers using ICTT.
 *         Provides a simplified interface for the blockchain-service to interact with.
 *         Handles both outbound (to other L1) and inbound (from other L1) transfers.
 *
 * ARCHITECTURE:
 * - This adapter sits between the backend service and the underlying ICTT contracts
 * - Supports both TokenHome (for native tokens) and TokenRemote (for wrapped tokens)
 * - Provides unified interface regardless of which side of the bridge we're on
 *
 * For TR L1 (TRY is native):
 *   - Outbound TRY: Use TokenHome to lock and bridge
 *   - Inbound wEUR: Already have wrapped tokens, use TokenRemote to burn and bridge back
 *
 * For EU L1 (EUR is native):
 *   - Outbound EUR: Use TokenHome to lock and bridge
 *   - Inbound wTRY: Already have wrapped tokens, use TokenRemote to burn and bridge back
 */
contract OvaBridgeAdapter is AccessControl, ReentrancyGuard {
    using SafeERC20 for IERC20;

    bytes32 public constant BRIDGE_OPERATOR_ROLE = keccak256("BRIDGE_OPERATOR_ROLE");

    /// @notice Native token on this chain (ovaTRY on TR L1, ovaEUR on EU L1)
    IERC20 public nativeToken;

    /// @notice Wrapped token on this chain (wEUR on TR L1, wTRY on EU L1)
    IERC20 public wrappedToken;

    /// @notice TokenHome contract for bridging native tokens out
    IOvaTokenHome public tokenHome;

    /// @notice TokenRemote contract for bridging wrapped tokens back
    IOvaTokenRemote public tokenRemote;

    /// @notice This chain's blockchain ID
    bytes32 public blockchainID;

    /// @notice Partner chain's blockchain ID
    bytes32 public partnerChainID;

    /// @notice Whether the adapter is paused
    bool public paused;

    /// @notice Transfer tracking for reconciliation
    mapping(bytes32 => TransferRecord) public transfers;

    struct TransferRecord {
        address sender;
        address recipient;
        uint256 amount;
        bytes32 destinationChainID;
        uint256 timestamp;
        TransferStatus status;
    }

    enum TransferStatus {
        NONE,
        INITIATED,
        COMPLETED,
        FAILED
    }

    // Events
    event BridgeTransferInitiated(
        bytes32 indexed transferId,
        address indexed sender,
        uint256 amount,
        bytes32 indexed destinationChainID,
        address recipient,
        bool isNativeToken
    );

    event BridgeTransferCompleted(
        bytes32 indexed transferId,
        address indexed recipient,
        uint256 amount
    );

    event AdapterPaused(address indexed by);
    event AdapterUnpaused(address indexed by);
    event ContractsUpdated(
        address nativeToken,
        address wrappedToken,
        address tokenHome,
        address tokenRemote
    );

    modifier whenNotPaused() {
        require(!paused, "Adapter paused");
        _;
    }

    constructor(
        address nativeToken_,
        address admin
    ) {
        require(nativeToken_ != address(0), "Invalid native token");
        require(admin != address(0), "Invalid admin");

        nativeToken = IERC20(nativeToken_);
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(BRIDGE_OPERATOR_ROLE, admin);
    }

    /**
     * @notice Configure the adapter with ICTT contract addresses
     * @param wrappedToken_ The wrapped token contract (TokenRemote's ERC20)
     * @param tokenHome_ The TokenHome contract for native token
     * @param tokenRemote_ The TokenRemote contract for wrapped token
     * @param blockchainID_ This chain's blockchain ID
     * @param partnerChainID_ The partner chain's blockchain ID
     */
    function configure(
        address wrappedToken_,
        address tokenHome_,
        address tokenRemote_,
        bytes32 blockchainID_,
        bytes32 partnerChainID_
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        wrappedToken = IERC20(wrappedToken_);
        tokenHome = IOvaTokenHome(tokenHome_);
        tokenRemote = IOvaTokenRemote(tokenRemote_);
        blockchainID = blockchainID_;
        partnerChainID = partnerChainID_;

        emit ContractsUpdated(
            address(nativeToken),
            wrappedToken_,
            tokenHome_,
            tokenRemote_
        );
    }

    /**
     * @notice Bridge native tokens to the partner chain
     * @param recipient Recipient address on the partner chain
     * @param amount Amount of native tokens to bridge
     * @param feeAmount Fee amount for relayers
     * @return transferId Unique transfer identifier
     */
    function bridgeNativeTokens(
        address recipient,
        uint256 amount,
        uint256 feeAmount
    ) external nonReentrant whenNotPaused returns (bytes32 transferId) {
        require(address(tokenHome) != address(0), "TokenHome not configured");
        require(recipient != address(0), "Invalid recipient");
        require(amount > 0, "Amount must be positive");

        // Transfer tokens from user to this adapter
        nativeToken.safeTransferFrom(msg.sender, address(this), amount + feeAmount);

        // Approve TokenHome to spend
        nativeToken.approve(address(tokenHome), amount + feeAmount);

        // Bridge via TokenHome (locks tokens, sends cross-chain message)
        bytes32 messageId = tokenHome.bridgeTokens(
            partnerChainID,
            recipient,
            amount,
            feeAmount
        );

        // Record the transfer
        transferId = keccak256(
            abi.encodePacked(blockchainID, msg.sender, recipient, amount, block.timestamp)
        );

        transfers[transferId] = TransferRecord({
            sender: msg.sender,
            recipient: recipient,
            amount: amount,
            destinationChainID: partnerChainID,
            timestamp: block.timestamp,
            status: TransferStatus.INITIATED
        });

        emit BridgeTransferInitiated(
            transferId,
            msg.sender,
            amount,
            partnerChainID,
            recipient,
            true // isNativeToken
        );

        return transferId;
    }

    /**
     * @notice Bridge wrapped tokens back to their home chain
     * @param recipient Recipient address on the home chain
     * @param amount Amount of wrapped tokens to bridge back
     * @param feeAmount Fee amount for relayers
     * @return transferId Unique transfer identifier
     */
    function bridgeWrappedTokensBack(
        address recipient,
        uint256 amount,
        uint256 feeAmount
    ) external nonReentrant whenNotPaused returns (bytes32 transferId) {
        require(address(tokenRemote) != address(0), "TokenRemote not configured");
        require(address(wrappedToken) != address(0), "Wrapped token not configured");
        require(recipient != address(0), "Invalid recipient");
        require(amount > 0, "Amount must be positive");

        // Transfer wrapped tokens from user (they will be burned by TokenRemote)
        wrappedToken.safeTransferFrom(msg.sender, address(this), amount + feeAmount);

        // Approve TokenRemote
        wrappedToken.approve(address(tokenRemote), amount + feeAmount);

        // Bridge back via TokenRemote (burns tokens, sends cross-chain message)
        bytes32 messageId = tokenRemote.bridgeBack(
            recipient,
            amount,
            feeAmount
        );

        // Record the transfer
        transferId = keccak256(
            abi.encodePacked(blockchainID, msg.sender, recipient, amount, block.timestamp, "wrapped")
        );

        transfers[transferId] = TransferRecord({
            sender: msg.sender,
            recipient: recipient,
            amount: amount,
            destinationChainID: partnerChainID,
            timestamp: block.timestamp,
            status: TransferStatus.INITIATED
        });

        emit BridgeTransferInitiated(
            transferId,
            msg.sender,
            amount,
            partnerChainID,
            recipient,
            false // isNativeToken
        );

        return transferId;
    }

    /**
     * @notice Mark a transfer as completed (called by operator after confirmation)
     * @param transferId The transfer ID to mark as completed
     */
    function markTransferCompleted(bytes32 transferId) external onlyRole(BRIDGE_OPERATOR_ROLE) {
        require(transfers[transferId].status == TransferStatus.INITIATED, "Invalid transfer status");
        transfers[transferId].status = TransferStatus.COMPLETED;

        emit BridgeTransferCompleted(
            transferId,
            transfers[transferId].recipient,
            transfers[transferId].amount
        );
    }

    /**
     * @notice Mark a transfer as failed (for reconciliation)
     */
    function markTransferFailed(bytes32 transferId) external onlyRole(BRIDGE_OPERATOR_ROLE) {
        require(transfers[transferId].status == TransferStatus.INITIATED, "Invalid transfer status");
        transfers[transferId].status = TransferStatus.FAILED;
    }

    /**
     * @notice Get transfer status
     */
    function getTransfer(bytes32 transferId) external view returns (TransferRecord memory) {
        return transfers[transferId];
    }

    /**
     * @notice Pause the adapter
     */
    function pause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        paused = true;
        emit AdapterPaused(msg.sender);
    }

    /**
     * @notice Unpause the adapter
     */
    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        paused = false;
        emit AdapterUnpaused(msg.sender);
    }

    /**
     * @notice Emergency withdrawal (only when paused)
     */
    function emergencyWithdraw(
        address token,
        address to,
        uint256 amount
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(paused, "Must be paused");
        require(to != address(0), "Invalid recipient");
        IERC20(token).safeTransfer(to, amount);
    }
}
