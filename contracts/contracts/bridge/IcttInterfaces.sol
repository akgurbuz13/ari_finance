// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/**
 * @title ITeleporterMessenger
 * @notice Interface for Avalanche Teleporter cross-chain messaging.
 *         Teleporter is the underlying messaging layer for ICTT.
 *
 * @dev This interface matches the Avalanche Teleporter specification.
 *      See: https://github.com/ava-labs/teleporter
 */
interface ITeleporterMessenger {
    struct TeleporterMessageInput {
        bytes32 destinationBlockchainID;
        address destinationAddress;
        TeleporterFeeInfo feeInfo;
        uint256 requiredGasLimit;
        address[] allowedRelayerAddresses;
        bytes message;
    }

    struct TeleporterFeeInfo {
        address feeTokenAddress;
        uint256 amount;
    }

    struct TeleporterMessage {
        uint256 messageNonce;
        address originSenderAddress;
        bytes32 destinationBlockchainID;
        address destinationAddress;
        uint256 requiredGasLimit;
        address[] allowedRelayerAddresses;
        TeleporterReceipt[] receipts;
        bytes message;
    }

    struct TeleporterReceipt {
        uint256 receivedMessageNonce;
        address relayerRewardAddress;
    }

    /**
     * @notice Send a cross-chain message
     * @param messageInput The message to send
     * @return messageID Unique identifier for the sent message
     */
    function sendCrossChainMessage(
        TeleporterMessageInput calldata messageInput
    ) external returns (bytes32 messageID);

    /**
     * @notice Receive a cross-chain message (called by relayer)
     * @param sourceBlockchainID The blockchain ID where the message originated
     * @param message The Teleporter message
     */
    function receiveCrossChainMessage(
        bytes32 sourceBlockchainID,
        TeleporterMessage calldata message
    ) external;

    /**
     * @notice Get the next message nonce for a destination
     */
    function getNextMessageID(bytes32 destinationBlockchainID) external view returns (bytes32);
}

/**
 * @title ITeleporterReceiver
 * @notice Interface that contracts must implement to receive Teleporter messages
 */
interface ITeleporterReceiver {
    /**
     * @notice Called when a Teleporter message is received
     * @param sourceBlockchainID The blockchain ID where the message originated
     * @param originSenderAddress The address that sent the message on the source chain
     * @param message The message payload
     */
    function receiveTeleporterMessage(
        bytes32 sourceBlockchainID,
        address originSenderAddress,
        bytes calldata message
    ) external;
}

/**
 * @title IERC20Bridge
 * @notice Minimal interface for the ICTT ERC20 bridge operations
 */
interface IERC20Bridge {
    function bridgeTokens(
        bytes32 destinationBlockchainID,
        address destinationBridgeAddress,
        address recipient,
        address token,
        uint256 amount,
        uint256 feeAmount
    ) external;
}
