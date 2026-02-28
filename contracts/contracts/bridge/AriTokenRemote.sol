// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import "./IcttInterfaces.sol";

/**
 * @title AriTokenRemote
 * @notice Token Remote contract for Avalanche ICTT bridge integration.
 *         This contract is deployed on "remote" chains where wrapped tokens are minted.
 *         It mints wrapped tokens when receiving from home chain and burns when sending back.
 *
 * For ARI:
 * - On TR L1: This wraps EUR tokens from EU L1 as "wEUR"
 * - On EU L1: This wraps TRY tokens from TR L1 as "wTRY"
 *
 * WRAPPED TOKEN PROPERTIES:
 * - Fully backed 1:1 by locked tokens on the home chain
 * - Can only be minted by valid cross-chain messages
 * - Burning sends tokens back to home chain
 * - Inherits KYC requirements from the platform
 */
contract AriTokenRemote is ERC20, AccessControl, ReentrancyGuard, ITeleporterReceiver {
    bytes32 public constant BRIDGE_ADMIN_ROLE = keccak256("BRIDGE_ADMIN_ROLE");
    bytes32 public constant PAUSE_ROLE = keccak256("PAUSE_ROLE");
    bytes32 public constant FREEZER_ROLE = keccak256("FREEZER_ROLE");

    /// @notice The Teleporter messenger contract
    ITeleporterMessenger public teleporterMessenger;

    /// @notice This chain's blockchain ID
    bytes32 public immutable blockchainID;

    /// @notice The home chain's blockchain ID
    bytes32 public homeChainID;

    /// @notice The TokenHome contract address on the home chain
    address public tokenHomeAddress;

    /// @notice Whether the bridge is paused
    bool public paused;

    /// @notice KYC allowlist (addresses that can hold wrapped tokens)
    mapping(address => bool) public allowlisted;

    /// @notice Frozen addresses
    mapping(address => bool) public frozen;

    /// @notice Processed message IDs to prevent replay
    mapping(bytes32 => bool) public processedMessages;

    /// @notice Nonce for burn messages
    uint256 public burnNonce;

    // Events
    event TokensMinted(
        bytes32 indexed sourceChainID,
        address indexed recipient,
        uint256 amount,
        bytes32 messageID
    );

    event TokensBurned(
        address indexed sender,
        bytes32 indexed destinationChainID,
        address indexed recipient,
        uint256 amount,
        bytes32 messageID
    );

    event HomeChainRegistered(bytes32 chainID, address homeAddress);
    event AddressAllowlisted(address indexed account);
    event AddressRemovedFromAllowlist(address indexed account);
    event AddressFrozen(address indexed account);
    event AddressUnfrozen(address indexed account);
    event BridgePaused(address indexed by);
    event BridgeUnpaused(address indexed by);

    modifier whenNotPaused() {
        require(!paused, "Bridge is paused");
        _;
    }

    modifier onlyTeleporter() {
        require(msg.sender == address(teleporterMessenger), "Only Teleporter");
        _;
    }

    modifier onlyAllowlisted(address account) {
        require(allowlisted[account], "Not KYC verified");
        require(!frozen[account], "Account frozen");
        _;
    }

    constructor(
        string memory name_,
        string memory symbol_,
        address teleporter_,
        bytes32 blockchainID_,
        address admin
    ) ERC20(name_, symbol_) {
        require(teleporter_ != address(0), "Invalid teleporter");
        require(admin != address(0), "Invalid admin");

        teleporterMessenger = ITeleporterMessenger(teleporter_);
        blockchainID = blockchainID_;

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(BRIDGE_ADMIN_ROLE, admin);
        _grantRole(PAUSE_ROLE, admin);
        _grantRole(FREEZER_ROLE, admin);

        // Allow zero address and this contract for mint/burn
        allowlisted[address(0)] = true;
        allowlisted[address(this)] = true;
    }

    /**
     * @notice Register the home chain and TokenHome address
     * @param homeChainID_ The home chain's blockchain ID
     * @param tokenHomeAddress_ The TokenHome contract address
     */
    function registerHomeChain(
        bytes32 homeChainID_,
        address tokenHomeAddress_
    ) external onlyRole(BRIDGE_ADMIN_ROLE) {
        require(tokenHomeAddress_ != address(0), "Invalid home address");
        require(homeChainID_ != blockchainID, "Cannot be same chain");

        homeChainID = homeChainID_;
        tokenHomeAddress = tokenHomeAddress_;

        emit HomeChainRegistered(homeChainID_, tokenHomeAddress_);
    }

    /**
     * @notice Receive tokens from the home chain (mint wrapped tokens)
     * @dev Called by Teleporter when a lock message is received from TokenHome
     */
    function receiveTeleporterMessage(
        bytes32 sourceBlockchainID,
        address originSenderAddress,
        bytes calldata message
    ) external override onlyTeleporter whenNotPaused {
        // Verify the message is from our registered home chain
        require(sourceBlockchainID == homeChainID, "Unknown source chain");
        require(originSenderAddress == tokenHomeAddress, "Unknown sender");

        // Decode the message
        (
            address originalSender,
            address recipient,
            uint256 amount
        ) = abi.decode(message, (address, address, uint256));

        // Generate unique message ID for tracking
        bytes32 messageID = keccak256(
            abi.encodePacked(sourceBlockchainID, originSenderAddress, burnNonce++)
        );

        // Prevent replay
        require(!processedMessages[messageID], "Already processed");
        processedMessages[messageID] = true;

        // Ensure recipient is KYC verified
        require(allowlisted[recipient], "Recipient not KYC verified");
        require(!frozen[recipient], "Recipient frozen");

        // Mint wrapped tokens
        _mint(recipient, amount);

        emit TokensMinted(sourceBlockchainID, recipient, amount, messageID);
    }

    /**
     * @notice Burn wrapped tokens and send back to home chain
     * @param recipient The recipient address on the home chain
     * @param amount The amount to burn and bridge back
     * @param feeAmount Fee to pay to relayers
     */
    function bridgeBack(
        address recipient,
        uint256 amount,
        uint256 feeAmount
    ) external nonReentrant whenNotPaused onlyAllowlisted(msg.sender) returns (bytes32 messageID) {
        require(recipient != address(0), "Invalid recipient");
        require(amount > 0, "Amount must be positive");
        require(homeChainID != bytes32(0), "Home chain not set");

        // Burn the wrapped tokens
        _burn(msg.sender, amount);

        // Generate unique message ID
        messageID = keccak256(
            abi.encodePacked(blockchainID, msg.sender, recipient, amount, burnNonce++)
        );

        // Prepare cross-chain message
        bytes memory payload = abi.encode(
            msg.sender,      // Original burner
            recipient,       // Recipient on home chain
            amount,          // Amount to release
            messageID        // For tracking
        );

        // Send via Teleporter
        ITeleporterMessenger.TeleporterMessageInput memory messageInput = ITeleporterMessenger.TeleporterMessageInput({
            destinationBlockchainID: homeChainID,
            destinationAddress: tokenHomeAddress,
            feeInfo: ITeleporterMessenger.TeleporterFeeInfo({
                feeTokenAddress: address(this), // Use wrapped token for fees
                amount: feeAmount
            }),
            requiredGasLimit: 200_000,
            allowedRelayerAddresses: new address[](0),
            message: payload
        });

        // Handle fee
        if (feeAmount > 0) {
            _burn(msg.sender, feeAmount); // Burn additional for fee
            _mint(address(this), feeAmount);
            _approve(address(this), address(teleporterMessenger), feeAmount);
        }

        teleporterMessenger.sendCrossChainMessage(messageInput);

        emit TokensBurned(msg.sender, homeChainID, recipient, amount, messageID);
        return messageID;
    }

    // ============ KYC Management ============

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

    function freeze(address account) external onlyRole(FREEZER_ROLE) {
        frozen[account] = true;
        emit AddressFrozen(account);
    }

    function unfreeze(address account) external onlyRole(FREEZER_ROLE) {
        frozen[account] = false;
        emit AddressUnfrozen(account);
    }

    // ============ Bridge Control ============

    function pause() external onlyRole(PAUSE_ROLE) {
        paused = true;
        emit BridgePaused(msg.sender);
    }

    function unpause() external onlyRole(PAUSE_ROLE) {
        paused = false;
        emit BridgeUnpaused(msg.sender);
    }

    function setTeleporter(address teleporter_) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(teleporter_ != address(0), "Invalid teleporter");
        teleporterMessenger = ITeleporterMessenger(teleporter_);
    }

    // ============ Transfer Hooks ============

    /**
     * @notice Override transfer to enforce KYC requirements
     */
    function _update(
        address from,
        address to,
        uint256 value
    ) internal override {
        // Skip checks for mint (from == 0) and burn (to == 0)
        if (from != address(0)) {
            require(allowlisted[from], "Sender not KYC verified");
            require(!frozen[from], "Sender frozen");
        }
        if (to != address(0)) {
            require(allowlisted[to], "Recipient not KYC verified");
            require(!frozen[to], "Recipient frozen");
        }
        super._update(from, to, value);
    }

    /**
     * @notice Check if an address can hold tokens
     */
    function canHold(address account) external view returns (bool) {
        return allowlisted[account] && !frozen[account];
    }
}
