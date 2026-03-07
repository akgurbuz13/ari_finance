// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import "@openzeppelin/contracts/utils/Pausable.sol";

/**
 * @title IAriStablecoinEscrow
 * @notice Minimal interface for AriStablecoin used by escrow
 */
interface IAriStablecoinEscrow {
    function transfer(address to, uint256 amount) external returns (bool);
    function burn(address from, uint256 amount) external;
    function balanceOf(address account) external view returns (uint256);
    function allowlisted(address account) external view returns (bool);
    function addToAllowlist(address account) external;
}

/**
 * @title IAriVehicleNFT
 * @notice Minimal interface for AriVehicleNFT used by escrow
 */
interface IAriVehicleNFT {
    function ownerOf(uint256 tokenId) external view returns (address);
    function safeTransferFrom(address from, address to, uint256 tokenId) external;
    function getApproved(uint256 tokenId) external view returns (address);
    function isApprovedForAll(address owner, address operator) external view returns (bool);
}

/**
 * @title AriVehicleEscrow
 * @notice On-chain escrow for vehicle sales on ARI platform.
 *         Manages the atomic swap of vehicle NFT ownership and ariTRY payment.
 *         Both seller and buyer must confirm before the swap executes.
 *         Flat 50 TRY fee per escrow transaction.
 */
contract AriVehicleEscrow is AccessControl, ReentrancyGuard, Pausable {
    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");

    // 50 TRY fee with 18 decimals (matching AriStablecoin which uses 18 decimals as ERC20 default)
    uint256 public constant FEE_AMOUNT = 50 * 10**18;

    IAriStablecoinEscrow public immutable stablecoin;
    IAriVehicleNFT public immutable vehicleNFT;
    address public treasury;

    enum EscrowState { CREATED, FUNDED, COMPLETED, CANCELLED }

    struct Escrow {
        uint256 tokenId;
        address seller;
        address buyer;
        uint256 saleAmount;
        uint256 feeAmount;
        EscrowState state;
        bool sellerConfirmed;
        bool buyerConfirmed;
        uint256 createdAt;
        uint256 fundedAt;
        uint256 completedAt;
    }

    mapping(uint256 => Escrow) public escrows;
    uint256 public nextEscrowId;

    event EscrowCreated(
        uint256 indexed escrowId,
        uint256 indexed tokenId,
        address indexed seller,
        address buyer,
        uint256 saleAmount,
        uint256 feeAmount
    );
    event EscrowFunded(uint256 indexed escrowId, uint256 totalAmount);
    event SellerConfirmed(uint256 indexed escrowId);
    event BuyerConfirmed(uint256 indexed escrowId);
    event EscrowCompleted(
        uint256 indexed escrowId,
        address indexed seller,
        address indexed buyer,
        uint256 tokenId,
        uint256 saleAmount
    );
    event EscrowCancelled(uint256 indexed escrowId, bool refunded);
    event TreasuryUpdated(address indexed newTreasury);

    constructor(
        address _stablecoin,
        address _vehicleNFT,
        address _treasury
    ) {
        require(_stablecoin != address(0), "Invalid stablecoin");
        require(_vehicleNFT != address(0), "Invalid vehicleNFT");
        require(_treasury != address(0), "Invalid treasury");

        stablecoin = IAriStablecoinEscrow(_stablecoin);
        vehicleNFT = IAriVehicleNFT(_vehicleNFT);
        treasury = _treasury;

        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(OPERATOR_ROLE, msg.sender);
    }

    /**
     * @notice Create a new escrow for a vehicle sale
     * @param tokenId The vehicle NFT token ID
     * @param seller The seller's address (must own the NFT)
     * @param buyer The buyer's address
     * @param saleAmount The agreed sale price in ariTRY (wei units)
     */
    function createEscrow(
        uint256 tokenId,
        address seller,
        address buyer,
        uint256 saleAmount
    ) external onlyRole(OPERATOR_ROLE) whenNotPaused nonReentrant returns (uint256) {
        require(seller != address(0), "Invalid seller");
        require(buyer != address(0), "Invalid buyer");
        require(seller != buyer, "Seller cannot be buyer");
        require(saleAmount > 0, "Sale amount must be > 0");
        require(vehicleNFT.ownerOf(tokenId) == seller, "Seller does not own NFT");

        // Verify escrow is approved on the NFT contract
        require(
            vehicleNFT.getApproved(tokenId) == address(this) ||
            vehicleNFT.isApprovedForAll(seller, address(this)),
            "Escrow not approved for NFT"
        );

        uint256 escrowId = nextEscrowId;
        nextEscrowId++;

        escrows[escrowId] = Escrow({
            tokenId: tokenId,
            seller: seller,
            buyer: buyer,
            saleAmount: saleAmount,
            feeAmount: FEE_AMOUNT,
            state: EscrowState.CREATED,
            sellerConfirmed: false,
            buyerConfirmed: false,
            createdAt: block.timestamp,
            fundedAt: 0,
            completedAt: 0
        });

        emit EscrowCreated(escrowId, tokenId, seller, buyer, saleAmount, FEE_AMOUNT);
        return escrowId;
    }

    /**
     * @notice Mark escrow as funded after ariTRY has been minted to this contract
     * @param escrowId The escrow ID
     */
    function fundEscrow(uint256 escrowId) external onlyRole(OPERATOR_ROLE) whenNotPaused nonReentrant {
        Escrow storage escrow = escrows[escrowId];
        require(escrow.state == EscrowState.CREATED, "Invalid state");

        uint256 totalRequired = escrow.saleAmount + escrow.feeAmount;
        require(
            stablecoin.balanceOf(address(this)) >= totalRequired,
            "Insufficient balance"
        );

        escrow.state = EscrowState.FUNDED;
        escrow.fundedAt = block.timestamp;

        emit EscrowFunded(escrowId, totalRequired);
    }

    /**
     * @notice Seller confirms the deal
     */
    function sellerConfirm(uint256 escrowId) external onlyRole(OPERATOR_ROLE) whenNotPaused nonReentrant {
        Escrow storage escrow = escrows[escrowId];
        require(escrow.state == EscrowState.FUNDED, "Invalid state");
        require(!escrow.sellerConfirmed, "Already confirmed");

        escrow.sellerConfirmed = true;
        emit SellerConfirmed(escrowId);

        if (escrow.buyerConfirmed) {
            _executeSwap(escrowId);
        }
    }

    /**
     * @notice Buyer confirms the deal
     */
    function buyerConfirm(uint256 escrowId) external onlyRole(OPERATOR_ROLE) whenNotPaused nonReentrant {
        Escrow storage escrow = escrows[escrowId];
        require(escrow.state == EscrowState.FUNDED, "Invalid state");
        require(!escrow.buyerConfirmed, "Already confirmed");

        escrow.buyerConfirmed = true;
        emit BuyerConfirmed(escrowId);

        if (escrow.sellerConfirmed) {
            _executeSwap(escrowId);
        }
    }

    /**
     * @notice Cancel escrow. If funded, burns the escrowed ariTRY.
     */
    function cancel(uint256 escrowId) external onlyRole(OPERATOR_ROLE) whenNotPaused nonReentrant {
        Escrow storage escrow = escrows[escrowId];
        require(
            !(escrow.sellerConfirmed && escrow.buyerConfirmed),
            "Both confirmed, cannot cancel"
        );
        _cancelEscrow(escrowId, escrow);
    }

    /**
     * @notice Admin emergency cancel — bypasses confirmation check
     */
    function adminCancel(uint256 escrowId) external onlyRole(DEFAULT_ADMIN_ROLE) nonReentrant {
        _cancelEscrow(escrowId, escrows[escrowId]);
    }

    /**
     * @dev Shared cancellation logic: validates state, burns if funded, emits event
     */
    function _cancelEscrow(uint256 escrowId, Escrow storage escrow) internal {
        require(escrow.state != EscrowState.COMPLETED, "Already completed");
        require(escrow.state != EscrowState.CANCELLED, "Already cancelled");

        bool wasFunded = escrow.state == EscrowState.FUNDED;

        if (wasFunded) {
            uint256 totalAmount = escrow.saleAmount + escrow.feeAmount;
            stablecoin.burn(address(this), totalAmount);
        }

        escrow.state = EscrowState.CANCELLED;
        emit EscrowCancelled(escrowId, wasFunded);
    }

    /**
     * @notice Update treasury address
     */
    function setTreasury(address newTreasury) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(newTreasury != address(0), "Invalid treasury");
        treasury = newTreasury;
        emit TreasuryUpdated(newTreasury);
    }

    function pause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _pause();
    }

    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _unpause();
    }

    /**
     * @dev Execute the atomic swap: pay seller, pay fee, transfer NFT
     */
    function _executeSwap(uint256 escrowId) internal {
        Escrow storage escrow = escrows[escrowId];

        // Ensure seller is allowlisted to receive ariTRY
        if (!stablecoin.allowlisted(escrow.seller)) {
            stablecoin.addToAllowlist(escrow.seller);
        }
        // Ensure treasury is allowlisted
        if (!stablecoin.allowlisted(treasury)) {
            stablecoin.addToAllowlist(treasury);
        }

        // 1. Transfer sale amount to seller
        require(
            stablecoin.transfer(escrow.seller, escrow.saleAmount),
            "Payment to seller failed"
        );

        // 2. Transfer fee to treasury
        require(
            stablecoin.transfer(treasury, escrow.feeAmount),
            "Fee payment failed"
        );

        // 3. Transfer NFT from seller to buyer
        vehicleNFT.safeTransferFrom(escrow.seller, escrow.buyer, escrow.tokenId);

        escrow.state = EscrowState.COMPLETED;
        escrow.completedAt = block.timestamp;

        emit EscrowCompleted(
            escrowId,
            escrow.seller,
            escrow.buyer,
            escrow.tokenId,
            escrow.saleAmount
        );
    }
}
