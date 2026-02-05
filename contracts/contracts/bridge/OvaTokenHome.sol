// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import "./IcttInterfaces.sol";

/**
 * @title OvaTokenHome
 * @notice Token Home contract for Avalanche ICTT bridge integration.
 *         This contract is deployed on the "home" chain where the native token lives.
 *         It locks tokens when bridging out and releases tokens when bridging in.
 *
 * AVALANCHE ICTT ARCHITECTURE:
 * - TokenHome: Deployed on the chain where the canonical token exists
 * - TokenRemote: Deployed on destination chains, mints/burns wrapped representations
 * - Uses Teleporter for cross-chain messaging with BLS signature verification
 *
 * For Ova:
 * - TR L1 has TokenHome for ovaTRY, TokenRemote for wrapped EUR
 * - EU L1 has TokenHome for ovaEUR, TokenRemote for wrapped TRY
 */
contract OvaTokenHome is AccessControl, ReentrancyGuard, ITeleporterReceiver {
    using SafeERC20 for IERC20;

    bytes32 public constant BRIDGE_ADMIN_ROLE = keccak256("BRIDGE_ADMIN_ROLE");
    bytes32 public constant PAUSE_ROLE = keccak256("PAUSE_ROLE");

    /// @notice The Teleporter messenger contract
    ITeleporterMessenger public teleporterMessenger;

    /// @notice The token that this bridge handles
    IERC20 public immutable token;

    /// @notice This chain's blockchain ID
    bytes32 public immutable blockchainID;

    /// @notice Mapping of destination chain ID to registered TokenRemote address
    mapping(bytes32 => address) public registeredRemotes;

    /// @notice Whether the bridge is paused
    bool public paused;

    /// @notice Total tokens locked in this contract (bridged out)
    uint256 public totalBridgedOut;

    /// @notice Minimum amount for bridge transfers
    uint256 public minBridgeAmount;

    /// @notice Maximum amount for single bridge transfer
    uint256 public maxBridgeAmount;

    /// @notice Daily limit for bridge transfers
    uint256 public dailyLimit;

    /// @notice Track daily bridged amounts
    mapping(uint256 => uint256) public dailyBridgedAmount; // day => amount

    /// @notice Nonce for message deduplication
    mapping(bytes32 => bool) public processedMessages;

    // Events
    event TokensLocked(
        address indexed sender,
        bytes32 indexed destinationChainID,
        address indexed recipient,
        uint256 amount,
        bytes32 messageID
    );

    event TokensReleased(
        bytes32 indexed sourceChainID,
        address indexed recipient,
        uint256 amount,
        bytes32 messageID
    );

    event RemoteRegistered(
        bytes32 indexed chainID,
        address remoteAddress
    );

    event RemoteUnregistered(bytes32 indexed chainID);
    event BridgePaused(address indexed by);
    event BridgeUnpaused(address indexed by);
    event LimitsUpdated(uint256 minAmount, uint256 maxAmount, uint256 dailyLimit);

    modifier whenNotPaused() {
        require(!paused, "Bridge is paused");
        _;
    }

    modifier onlyTeleporter() {
        require(msg.sender == address(teleporterMessenger), "Only Teleporter");
        _;
    }

    constructor(
        address token_,
        address teleporter_,
        bytes32 blockchainID_,
        address admin
    ) {
        require(token_ != address(0), "Invalid token");
        require(teleporter_ != address(0), "Invalid teleporter");
        require(admin != address(0), "Invalid admin");

        token = IERC20(token_);
        teleporterMessenger = ITeleporterMessenger(teleporter_);
        blockchainID = blockchainID_;

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(BRIDGE_ADMIN_ROLE, admin);
        _grantRole(PAUSE_ROLE, admin);

        // Default limits (can be adjusted via governance)
        minBridgeAmount = 1e18; // 1 token minimum
        maxBridgeAmount = 1_000_000e18; // 1M token maximum per tx
        dailyLimit = 10_000_000e18; // 10M daily limit
    }

    /**
     * @notice Register a TokenRemote on a destination chain
     * @param destinationChainID The destination blockchain ID
     * @param remoteAddress The TokenRemote contract address on that chain
     */
    function registerRemote(
        bytes32 destinationChainID,
        address remoteAddress
    ) external onlyRole(BRIDGE_ADMIN_ROLE) {
        require(remoteAddress != address(0), "Invalid remote address");
        require(destinationChainID != blockchainID, "Cannot register self");

        registeredRemotes[destinationChainID] = remoteAddress;
        emit RemoteRegistered(destinationChainID, remoteAddress);
    }

    /**
     * @notice Unregister a TokenRemote
     * @param destinationChainID The chain to unregister
     */
    function unregisterRemote(bytes32 destinationChainID) external onlyRole(BRIDGE_ADMIN_ROLE) {
        delete registeredRemotes[destinationChainID];
        emit RemoteUnregistered(destinationChainID);
    }

    /**
     * @notice Bridge tokens to a destination chain
     * @param destinationChainID The destination blockchain ID
     * @param recipient The recipient address on the destination chain
     * @param amount The amount of tokens to bridge
     * @param feeAmount Fee to pay to relayers (in fee token)
     */
    function bridgeTokens(
        bytes32 destinationChainID,
        address recipient,
        uint256 amount,
        uint256 feeAmount
    ) external nonReentrant whenNotPaused returns (bytes32 messageID) {
        require(amount >= minBridgeAmount, "Amount below minimum");
        require(amount <= maxBridgeAmount, "Amount exceeds maximum");
        require(recipient != address(0), "Invalid recipient");

        address remoteAddress = registeredRemotes[destinationChainID];
        require(remoteAddress != address(0), "Destination not registered");

        // Check daily limit
        uint256 today = block.timestamp / 1 days;
        require(
            dailyBridgedAmount[today] + amount <= dailyLimit,
            "Daily limit exceeded"
        );
        dailyBridgedAmount[today] += amount;

        // Transfer tokens to this contract (lock)
        token.safeTransferFrom(msg.sender, address(this), amount);
        totalBridgedOut += amount;

        // Prepare cross-chain message
        bytes memory payload = abi.encode(
            msg.sender,      // Original sender
            recipient,       // Destination recipient
            amount          // Amount to mint on destination
        );

        // Send via Teleporter
        ITeleporterMessenger.TeleporterMessageInput memory messageInput = ITeleporterMessenger.TeleporterMessageInput({
            destinationBlockchainID: destinationChainID,
            destinationAddress: remoteAddress,
            feeInfo: ITeleporterMessenger.TeleporterFeeInfo({
                feeTokenAddress: address(token),
                amount: feeAmount
            }),
            requiredGasLimit: 300_000, // Gas limit for receiving on destination
            allowedRelayerAddresses: new address[](0), // Any relayer can relay
            message: payload
        });

        // Approve fee if needed
        if (feeAmount > 0) {
            token.safeTransferFrom(msg.sender, address(this), feeAmount);
            token.approve(address(teleporterMessenger), feeAmount);
        }

        messageID = teleporterMessenger.sendCrossChainMessage(messageInput);

        emit TokensLocked(msg.sender, destinationChainID, recipient, amount, messageID);
        return messageID;
    }

    /**
     * @notice Receive tokens back from a remote chain (unlock)
     * @dev Called by Teleporter when a burn message is received from TokenRemote
     */
    function receiveTeleporterMessage(
        bytes32 sourceBlockchainID,
        address originSenderAddress,
        bytes calldata message
    ) external override onlyTeleporter whenNotPaused {
        // Verify the sender is a registered remote
        require(
            registeredRemotes[sourceBlockchainID] == originSenderAddress,
            "Unknown remote"
        );

        // Decode the message
        (
            address originalSender,
            address recipient,
            uint256 amount,
            bytes32 burnMessageID
        ) = abi.decode(message, (address, address, uint256, bytes32));

        // Prevent replay
        require(!processedMessages[burnMessageID], "Already processed");
        processedMessages[burnMessageID] = true;

        // Release tokens
        require(amount <= totalBridgedOut, "Insufficient locked balance");
        totalBridgedOut -= amount;
        token.safeTransfer(recipient, amount);

        emit TokensReleased(sourceBlockchainID, recipient, amount, burnMessageID);
    }

    /**
     * @notice Pause the bridge
     */
    function pause() external onlyRole(PAUSE_ROLE) {
        paused = true;
        emit BridgePaused(msg.sender);
    }

    /**
     * @notice Unpause the bridge
     */
    function unpause() external onlyRole(PAUSE_ROLE) {
        paused = false;
        emit BridgeUnpaused(msg.sender);
    }

    /**
     * @notice Update bridge limits
     */
    function setLimits(
        uint256 minAmount,
        uint256 maxAmount,
        uint256 dailyLimit_
    ) external onlyRole(BRIDGE_ADMIN_ROLE) {
        require(minAmount < maxAmount, "Invalid limits");
        require(maxAmount <= dailyLimit_, "Max exceeds daily");

        minBridgeAmount = minAmount;
        maxBridgeAmount = maxAmount;
        dailyLimit = dailyLimit_;

        emit LimitsUpdated(minAmount, maxAmount, dailyLimit_);
    }

    /**
     * @notice Update the Teleporter address (for upgrades)
     */
    function setTeleporter(address teleporter_) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(teleporter_ != address(0), "Invalid teleporter");
        teleporterMessenger = ITeleporterMessenger(teleporter_);
    }

    /**
     * @notice Emergency withdrawal (only when paused)
     */
    function emergencyWithdraw(
        address to,
        uint256 amount
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(paused, "Must be paused");
        require(to != address(0), "Invalid recipient");
        token.safeTransfer(to, amount);
    }

    /**
     * @notice Get the current day's bridged amount
     */
    function getTodayBridgedAmount() external view returns (uint256) {
        return dailyBridgedAmount[block.timestamp / 1 days];
    }

    /**
     * @notice Get remaining daily limit
     */
    function getRemainingDailyLimit() external view returns (uint256) {
        uint256 today = block.timestamp / 1 days;
        uint256 used = dailyBridgedAmount[today];
        return used >= dailyLimit ? 0 : dailyLimit - used;
    }
}
