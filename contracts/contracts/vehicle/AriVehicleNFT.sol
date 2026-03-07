// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/token/ERC721/extensions/ERC721URIStorage.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/Pausable.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

/**
 * @title AriVehicleNFT
 * @notice ERC-721 NFT for vehicle ownership on ARI platform.
 *         Each token represents a registered vehicle. Transfer restrictions
 *         ensure vehicles can only change hands through approved escrow contracts
 *         or admin override. Only KYC-verified (allowlisted) addresses can hold NFTs.
 */
contract AriVehicleNFT is ERC721URIStorage, AccessControl, Pausable, ReentrancyGuard {
    bytes32 public constant MINTER_ROLE = keccak256("MINTER_ROLE");

    struct Vehicle {
        bytes32 vinHash;
        bytes32 plateHash;
        uint256 registeredAt;
    }

    mapping(uint256 => Vehicle) public vehicles;
    mapping(bytes32 => bool) public vinHashUsed;
    mapping(address => bool) public kycAllowlisted;
    mapping(address => bool) public approvedEscrowContracts;
    uint256 public nextTokenId;

    event VehicleMinted(
        uint256 indexed tokenId,
        address indexed to,
        bytes32 vinHash,
        bytes32 plateHash
    );
    event AllowlistUpdated(address indexed account, bool allowed);
    event EscrowApprovalUpdated(address indexed escrow, bool approved);

    constructor() ERC721("ARI Vehicle", "ariVEH") {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(MINTER_ROLE, msg.sender);
        // Allow zero address for mint/burn
        kycAllowlisted[address(0)] = true;
    }

    /**
     * @notice Mint a new vehicle NFT
     * @param to Owner address (must be KYC allowlisted)
     * @param vinHash Keccak256 hash of VIN
     * @param plateHash Keccak256 hash of plate number
     * @param metadataUri Token URI for metadata
     */
    function mint(
        address to,
        bytes32 vinHash,
        bytes32 plateHash,
        string calldata metadataUri
    ) external onlyRole(MINTER_ROLE) whenNotPaused nonReentrant returns (uint256) {
        require(to != address(0), "AriVehicleNFT: zero address");
        require(kycAllowlisted[to], "AriVehicleNFT: recipient not KYC verified");
        require(!vinHashUsed[vinHash], "AriVehicleNFT: VIN already registered");

        uint256 tokenId = nextTokenId;
        nextTokenId++;

        vinHashUsed[vinHash] = true;
        vehicles[tokenId] = Vehicle({
            vinHash: vinHash,
            plateHash: plateHash,
            registeredAt: block.timestamp
        });

        _safeMint(to, tokenId);
        _setTokenURI(tokenId, metadataUri);

        emit VehicleMinted(tokenId, to, vinHash, plateHash);
        return tokenId;
    }

    function addToAllowlist(address account) external onlyRole(DEFAULT_ADMIN_ROLE) {
        kycAllowlisted[account] = true;
        emit AllowlistUpdated(account, true);
    }

    function removeFromAllowlist(address account) external onlyRole(DEFAULT_ADMIN_ROLE) {
        kycAllowlisted[account] = false;
        emit AllowlistUpdated(account, false);
    }

    function setApprovedEscrow(address escrow, bool approved) external onlyRole(DEFAULT_ADMIN_ROLE) {
        approvedEscrowContracts[escrow] = approved;
        emit EscrowApprovalUpdated(escrow, approved);
    }

    function pause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _pause();
    }

    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _unpause();
    }

    /**
     * @dev Authorization override: only approved escrow contracts and admins can transfer.
     *      Blocks direct owner transfers — vehicles must go through escrow.
     *      Note: approve() uses a separate path and is NOT affected by this.
     */
    function _isAuthorized(
        address,
        address spender,
        uint256
    ) internal view override returns (bool) {
        return approvedEscrowContracts[spender]
            || hasRole(DEFAULT_ADMIN_ROLE, spender);
    }

    /**
     * @dev Transfer restriction: non-mint/burn transfers require KYC-verified recipient.
     */
    function _update(
        address to,
        uint256 tokenId,
        address auth
    ) internal override whenNotPaused returns (address) {
        address from = _ownerOf(tokenId);
        // Allow minting (from == 0) and burning (to == 0) freely
        if (from != address(0) && to != address(0)) {
            require(kycAllowlisted[to], "AriVehicleNFT: recipient not KYC verified");
        }
        return super._update(to, tokenId, auth);
    }

    /**
     * @dev Required override for AccessControl + ERC721
     */
    function supportsInterface(bytes4 interfaceId)
        public
        view
        override(ERC721URIStorage, AccessControl)
        returns (bool)
    {
        return super.supportsInterface(interfaceId);
    }
}
