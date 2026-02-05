// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "../bridge/IcttInterfaces.sol";

/**
 * @title MockTeleporter
 * @notice Mock Teleporter contract for testing ICTT bridge functionality
 * @dev Simulates cross-chain message delivery without actual network communication
 */
contract MockTeleporter is ITeleporterMessenger {
    uint256 public messageNonce;

    struct SentMessage {
        bytes32 destinationBlockchainID;
        address destinationAddress;
        bytes message;
        uint256 feeAmount;
        uint256 gasLimit;
    }

    // Track sent messages for verification in tests
    mapping(bytes32 => SentMessage) public sentMessages;
    bytes32[] public messageIds;

    // Events for test verification
    event MessageSent(
        bytes32 indexed messageId,
        bytes32 indexed destinationBlockchainID,
        address destinationAddress,
        bytes message
    );

    event MessageDelivered(
        bytes32 indexed sourceBlockchainID,
        address indexed originSenderAddress,
        address indexed receiver,
        bytes message
    );

    /**
     * @notice Send a cross-chain message (stores it for manual delivery in tests)
     */
    function sendCrossChainMessage(
        TeleporterMessageInput calldata messageInput
    ) external override returns (bytes32 messageID) {
        messageNonce++;
        messageID = keccak256(
            abi.encodePacked(
                messageInput.destinationBlockchainID,
                messageInput.destinationAddress,
                messageNonce,
                block.timestamp
            )
        );

        sentMessages[messageID] = SentMessage({
            destinationBlockchainID: messageInput.destinationBlockchainID,
            destinationAddress: messageInput.destinationAddress,
            message: messageInput.message,
            feeAmount: messageInput.feeInfo.amount,
            gasLimit: messageInput.requiredGasLimit
        });

        messageIds.push(messageID);

        emit MessageSent(
            messageID,
            messageInput.destinationBlockchainID,
            messageInput.destinationAddress,
            messageInput.message
        );

        return messageID;
    }

    /**
     * @notice Receive a cross-chain message (not used in mock, delivery is manual)
     */
    function receiveCrossChainMessage(
        bytes32,
        TeleporterMessage calldata
    ) external override {
        // Not used in mock - use deliverMessage instead
    }

    /**
     * @notice Get the next message ID for a destination
     */
    function getNextMessageID(bytes32 destinationBlockchainID) external view override returns (bytes32) {
        return keccak256(
            abi.encodePacked(destinationBlockchainID, address(this), messageNonce + 1)
        );
    }

    // ============ Test Helper Functions ============

    /**
     * @notice Manually deliver a message to a receiver (simulates Teleporter relay)
     * @param sourceBlockchainID The source chain ID
     * @param originSenderAddress The original sender address on source chain
     * @param receiver The contract that will receive the message
     * @param message The message payload
     */
    function deliverMessage(
        bytes32 sourceBlockchainID,
        address originSenderAddress,
        address receiver,
        bytes calldata message
    ) external {
        ITeleporterReceiver(receiver).receiveTeleporterMessage(
            sourceBlockchainID,
            originSenderAddress,
            message
        );

        emit MessageDelivered(sourceBlockchainID, originSenderAddress, receiver, message);
    }

    /**
     * @notice Deliver the last sent message to its destination
     * @param sourceBlockchainID The source chain ID to simulate
     * @param originSenderAddress The sender address to simulate
     */
    function deliverLastMessage(
        bytes32 sourceBlockchainID,
        address originSenderAddress
    ) external returns (bytes32) {
        require(messageIds.length > 0, "No messages to deliver");

        bytes32 lastMessageId = messageIds[messageIds.length - 1];
        SentMessage storage msg_ = sentMessages[lastMessageId];

        ITeleporterReceiver(msg_.destinationAddress).receiveTeleporterMessage(
            sourceBlockchainID,
            originSenderAddress,
            msg_.message
        );

        emit MessageDelivered(
            sourceBlockchainID,
            originSenderAddress,
            msg_.destinationAddress,
            msg_.message
        );

        return lastMessageId;
    }

    /**
     * @notice Get the number of sent messages
     */
    function getMessageCount() external view returns (uint256) {
        return messageIds.length;
    }

    /**
     * @notice Get a message ID by index
     */
    function getMessageIdAt(uint256 index) external view returns (bytes32) {
        require(index < messageIds.length, "Index out of bounds");
        return messageIds[index];
    }

    /**
     * @notice Get the payload of a sent message
     */
    function getMessagePayload(bytes32 messageId) external view returns (bytes memory) {
        return sentMessages[messageId].message;
    }

    /**
     * @notice Clear all sent messages (for test reset)
     */
    function clearMessages() external {
        for (uint256 i = 0; i < messageIds.length; i++) {
            delete sentMessages[messageIds[i]];
        }
        delete messageIds;
        messageNonce = 0;
    }
}
