// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import "./IcttInterfaces.sol";

/**
 * @title IAriStablecoin
 * @notice Minimal interface for AriStablecoin mint/burn used by the bridge
 */
interface IAriStablecoin {
    function mint(address to, uint256 amount) external;
    function burn(address from, uint256 amount) external;
    function addToAllowlist(address account) external;
    function allowlisted(address account) external view returns (bool);
}

/**
 * @title AriBurnMintBridge
 * @notice Lightweight cross-chain bridge using Teleporter for same-currency transfers.
 *         Burns native ariTRY/ariEUR on source chain, sends Teleporter message,
 *         and mints native ariTRY/ariEUR on destination chain.
 *         No wrapped tokens — native tokens on both chains.
 */
contract AriBurnMintBridge is AccessControl, ReentrancyGuard, ITeleporterReceiver {
    bytes32 public constant BRIDGE_OPERATOR_ROLE = keccak256("BRIDGE_OPERATOR_ROLE");

    ITeleporterMessenger public immutable teleporterMessenger;
    IAriStablecoin public immutable stablecoin;

    // Registered partner bridges: partnerChainID -> partnerBridgeAddress
    mapping(bytes32 => address) public registeredPartners;

    // Replay protection (defense-in-depth, Teleporter also has its own)
    mapping(bytes32 => bool) public processedMessages;

    event BurnAndBridge(
        address indexed sender,
        address indexed recipient,
        uint256 amount,
        bytes32 destChainID,
        bytes32 messageID
    );

    event MintFromBridge(
        address indexed recipient,
        uint256 amount,
        bytes32 sourceChainID,
        bytes32 messageHash
    );

    event PartnerRegistered(bytes32 indexed chainID, address bridge);

    constructor(
        address _teleporterMessenger,
        address _stablecoin
    ) {
        require(_teleporterMessenger != address(0), "Invalid teleporter");
        require(_stablecoin != address(0), "Invalid stablecoin");

        teleporterMessenger = ITeleporterMessenger(_teleporterMessenger);
        stablecoin = IAriStablecoin(_stablecoin);

        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(BRIDGE_OPERATOR_ROLE, msg.sender);
    }

    /**
     * @notice Burn tokens on this chain and send Teleporter message to mint on dest chain
     * @param destChainID Avalanche blockchain ID of the destination chain
     * @param recipient Address to receive minted tokens on destination chain
     * @param amount Amount of tokens to bridge (in wei)
     * @return messageID The Teleporter message ID
     */
    function burnAndBridge(
        bytes32 destChainID,
        address recipient,
        uint256 amount
    ) external nonReentrant onlyRole(BRIDGE_OPERATOR_ROLE) returns (bytes32) {
        require(registeredPartners[destChainID] != address(0), "Dest not registered");
        require(recipient != address(0), "Invalid recipient");
        require(amount > 0, "Amount must be > 0");

        // 1. Burn tokens on this chain (caller must have MINTER_ROLE on stablecoin)
        stablecoin.burn(msg.sender, amount);

        // 2. Send Teleporter message to partner bridge on dest chain
        bytes memory payload = abi.encode(recipient, amount);

        address[] memory allowedRelayers = new address[](0);

        bytes32 messageID = teleporterMessenger.sendCrossChainMessage(
            ITeleporterMessenger.TeleporterMessageInput({
                destinationBlockchainID: destChainID,
                destinationAddress: registeredPartners[destChainID],
                feeInfo: ITeleporterMessenger.TeleporterFeeInfo({
                    feeTokenAddress: address(0),
                    amount: 0
                }),
                requiredGasLimit: 200_000,
                allowedRelayerAddresses: allowedRelayers,
                message: payload
            })
        );

        emit BurnAndBridge(msg.sender, recipient, amount, destChainID, messageID);
        return messageID;
    }

    /**
     * @notice Receive Teleporter message and mint tokens on this chain
     * @dev Called by Teleporter messenger when a cross-chain message arrives
     */
    function receiveTeleporterMessage(
        bytes32 sourceBlockchainID,
        address originSenderAddress,
        bytes calldata message
    ) external override {
        require(msg.sender == address(teleporterMessenger), "Only Teleporter");
        require(
            registeredPartners[sourceBlockchainID] == originSenderAddress,
            "Unknown partner"
        );

        (address recipient, uint256 amount) = abi.decode(message, (address, uint256));
        require(recipient != address(0), "Invalid recipient in message");
        require(amount > 0, "Invalid amount in message");

        // Defense-in-depth replay protection
        bytes32 msgHash = keccak256(
            abi.encodePacked(sourceBlockchainID, originSenderAddress, message)
        );
        require(!processedMessages[msgHash], "Already processed");
        processedMessages[msgHash] = true;

        // Ensure recipient is allowlisted before minting
        if (!stablecoin.allowlisted(recipient)) {
            stablecoin.addToAllowlist(recipient);
        }

        // Mint native tokens on this chain
        stablecoin.mint(recipient, amount);

        emit MintFromBridge(recipient, amount, sourceBlockchainID, msgHash);
    }

    /**
     * @notice Register a partner bridge on another chain
     * @param chainID The Avalanche blockchain ID of the partner chain
     * @param bridge The address of the AriBurnMintBridge on the partner chain
     */
    function registerPartner(
        bytes32 chainID,
        address bridge
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(bridge != address(0), "Invalid bridge address");
        registeredPartners[chainID] = bridge;
        emit PartnerRegistered(chainID, bridge);
    }
}
