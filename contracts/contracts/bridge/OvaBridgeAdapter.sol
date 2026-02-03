// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";

/**
 * @title OvaBridgeAdapter
 * @notice Adapter for Avalanche ICTT (Interchain Token Transfer) bridge.
 *         Handles token locking on source chain and minting on destination chain.
 *         This contract is deployed on each L1 and interacts with the ICTT bridge contracts.
 */
contract OvaBridgeAdapter is AccessControl {
    bytes32 public constant BRIDGE_OPERATOR_ROLE = keccak256("BRIDGE_OPERATOR_ROLE");

    IERC20 public token;
    address public icttBridge; // The Avalanche ICTT bridge contract address

    mapping(bytes32 => bool) public processedMessages;

    event BridgeTransferInitiated(
        address indexed sender,
        uint256 amount,
        bytes32 indexed destinationChainId,
        address indexed recipient,
        bytes32 messageId
    );

    event BridgeTransferCompleted(
        bytes32 indexed messageId,
        address indexed recipient,
        uint256 amount
    );

    constructor(address token_, address icttBridge_) {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(BRIDGE_OPERATOR_ROLE, msg.sender);
        token = IERC20(token_);
        icttBridge = icttBridge_;
    }

    /**
     * @notice Initiate a cross-chain transfer by locking tokens on this chain.
     *         The ICTT bridge will handle the cross-chain message delivery.
     */
    function sendTokens(
        uint256 amount,
        bytes32 destinationChainId,
        address recipient
    ) external {
        require(amount > 0, "Amount must be greater than 0");

        // Transfer tokens to this contract (lock)
        token.transferFrom(msg.sender, address(this), amount);

        bytes32 messageId = keccak256(
            abi.encodePacked(
                block.chainid,
                msg.sender,
                recipient,
                amount,
                block.timestamp
            )
        );

        // TODO: Call ICTT bridge contract to send cross-chain message
        // icttBridge.sendCrossChainMessage(destinationChainId, abi.encode(recipient, amount));

        emit BridgeTransferInitiated(msg.sender, amount, destinationChainId, recipient, messageId);
    }

    /**
     * @notice Complete a cross-chain transfer by releasing tokens on this chain.
     *         Called by the bridge operator after verifying the cross-chain message.
     */
    function receiveTokens(
        bytes32 messageId,
        address recipient,
        uint256 amount
    ) external onlyRole(BRIDGE_OPERATOR_ROLE) {
        require(!processedMessages[messageId], "Message already processed");
        processedMessages[messageId] = true;

        // Transfer tokens from this contract to recipient (unlock)
        token.transfer(recipient, amount);

        emit BridgeTransferCompleted(messageId, recipient, amount);
    }

    function updateBridge(address newBridge) external onlyRole(DEFAULT_ADMIN_ROLE) {
        icttBridge = newBridge;
    }

    function emergencyWithdraw(address to, uint256 amount) external onlyRole(DEFAULT_ADMIN_ROLE) {
        token.transfer(to, amount);
    }
}
