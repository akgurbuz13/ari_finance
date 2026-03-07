import { expect } from "chai";
import { ethers } from "hardhat";
import { SignerWithAddress } from "@nomicfoundation/hardhat-ethers/signers";

describe("AriVehicleNFT", function () {
  let admin: SignerWithAddress;
  let minter: SignerWithAddress;
  let user1: SignerWithAddress;
  let user2: SignerWithAddress;
  let unauthorized: SignerWithAddress;

  let nft: any;

  const VIN_HASH = ethers.keccak256(ethers.toUtf8Bytes("WVWZZZ3CZWE123456"));
  const VIN_HASH_2 = ethers.keccak256(ethers.toUtf8Bytes("WVWZZZ3CZWE654321"));
  const PLATE_HASH = ethers.keccak256(ethers.toUtf8Bytes("34ABC123"));
  const PLATE_HASH_2 = ethers.keccak256(ethers.toUtf8Bytes("06DEF456"));
  const METADATA_URI = "https://api.arifinance.co/vehicles/1";
  const MINTER_ROLE = ethers.keccak256(ethers.toUtf8Bytes("MINTER_ROLE"));

  beforeEach(async function () {
    [admin, minter, user1, user2, unauthorized] = await ethers.getSigners();

    const AriVehicleNFT = await ethers.getContractFactory("AriVehicleNFT");
    nft = await AriVehicleNFT.deploy();
    await nft.waitForDeployment();

    // Grant minter role
    await nft.grantRole(MINTER_ROLE, minter.address);

    // KYC allowlist users
    await nft.addToAllowlist(user1.address);
    await nft.addToAllowlist(user2.address);
  });

  describe("Deployment", function () {
    it("Should set deployer as admin and minter", async function () {
      expect(await nft.hasRole(ethers.ZeroHash, admin.address)).to.be.true;
      expect(await nft.hasRole(MINTER_ROLE, admin.address)).to.be.true;
    });

    it("Should have correct name and symbol", async function () {
      expect(await nft.name()).to.equal("ARI Vehicle");
      expect(await nft.symbol()).to.equal("ariVEH");
    });

    it("Should start with tokenId 0", async function () {
      expect(await nft.nextTokenId()).to.equal(0);
    });
  });

  describe("Minting", function () {
    it("Should mint vehicle NFT successfully", async function () {
      const tx = await nft.connect(minter).mint(user1.address, VIN_HASH, PLATE_HASH, METADATA_URI);
      await expect(tx)
        .to.emit(nft, "VehicleMinted")
        .withArgs(0, user1.address, VIN_HASH, PLATE_HASH);

      expect(await nft.ownerOf(0)).to.equal(user1.address);
      expect(await nft.tokenURI(0)).to.equal(METADATA_URI);
      expect(await nft.nextTokenId()).to.equal(1);

      const vehicle = await nft.vehicles(0);
      expect(vehicle.vinHash).to.equal(VIN_HASH);
      expect(vehicle.plateHash).to.equal(PLATE_HASH);
      expect(vehicle.registeredAt).to.be.greaterThan(0);
    });

    it("Should reject duplicate VIN", async function () {
      await nft.connect(minter).mint(user1.address, VIN_HASH, PLATE_HASH, METADATA_URI);
      await expect(
        nft.connect(minter).mint(user2.address, VIN_HASH, PLATE_HASH_2, METADATA_URI)
      ).to.be.revertedWith("AriVehicleNFT: VIN already registered");
    });

    it("Should reject non-allowlisted recipient", async function () {
      await expect(
        nft.connect(minter).mint(unauthorized.address, VIN_HASH, PLATE_HASH, METADATA_URI)
      ).to.be.revertedWith("AriVehicleNFT: recipient not KYC verified");
    });

    it("Should reject non-minter caller", async function () {
      await expect(
        nft.connect(unauthorized).mint(user1.address, VIN_HASH, PLATE_HASH, METADATA_URI)
      ).to.be.reverted;
    });

    it("Should reject zero address recipient", async function () {
      await expect(
        nft.connect(minter).mint(ethers.ZeroAddress, VIN_HASH, PLATE_HASH, METADATA_URI)
      ).to.be.revertedWith("AriVehicleNFT: zero address");
    });

    it("Should mint multiple vehicles with sequential IDs", async function () {
      await nft.connect(minter).mint(user1.address, VIN_HASH, PLATE_HASH, METADATA_URI);
      await nft.connect(minter).mint(user2.address, VIN_HASH_2, PLATE_HASH_2, METADATA_URI);

      expect(await nft.ownerOf(0)).to.equal(user1.address);
      expect(await nft.ownerOf(1)).to.equal(user2.address);
      expect(await nft.nextTokenId()).to.equal(2);
    });
  });

  describe("Transfer Restrictions", function () {
    beforeEach(async function () {
      await nft.connect(minter).mint(user1.address, VIN_HASH, PLATE_HASH, METADATA_URI);
    });

    it("Should block direct transfer between users", async function () {
      await expect(
        nft.connect(user1).transferFrom(user1.address, user2.address, 0)
      ).to.be.reverted; // ERC721InsufficientApproval — owner cannot transfer directly
    });

    it("Should allow transfer via approved escrow contract", async function () {
      // Deploy a mock escrow (just use admin as escrow for simplicity)
      const mockEscrow = unauthorized; // Use unauthorized as mock escrow
      await nft.setApprovedEscrow(mockEscrow.address, true);

      // User1 approves the mock escrow
      await nft.connect(user1).approve(mockEscrow.address, 0);

      // Escrow contract transfers NFT
      await nft.connect(mockEscrow).transferFrom(user1.address, user2.address, 0);
      expect(await nft.ownerOf(0)).to.equal(user2.address);
    });

    it("Should allow transfer by admin", async function () {
      await nft.connect(admin).transferFrom(user1.address, user2.address, 0);
      expect(await nft.ownerOf(0)).to.equal(user2.address);
    });

    it("Should reject transfer to non-allowlisted address via escrow", async function () {
      const mockEscrow = unauthorized;
      await nft.setApprovedEscrow(mockEscrow.address, true);
      await nft.connect(user1).approve(mockEscrow.address, 0);

      // Remove user2 from allowlist
      await nft.removeFromAllowlist(user2.address);

      await expect(
        nft.connect(mockEscrow).transferFrom(user1.address, user2.address, 0)
      ).to.be.revertedWith("AriVehicleNFT: recipient not KYC verified");
    });
  });

  describe("KYC Allowlist", function () {
    it("Should add to allowlist", async function () {
      await expect(nft.addToAllowlist(unauthorized.address))
        .to.emit(nft, "AllowlistUpdated")
        .withArgs(unauthorized.address, true);
      expect(await nft.kycAllowlisted(unauthorized.address)).to.be.true;
    });

    it("Should remove from allowlist", async function () {
      await expect(nft.removeFromAllowlist(user1.address))
        .to.emit(nft, "AllowlistUpdated")
        .withArgs(user1.address, false);
      expect(await nft.kycAllowlisted(user1.address)).to.be.false;
    });

    it("Should reject non-admin for allowlist operations", async function () {
      await expect(
        nft.connect(unauthorized).addToAllowlist(unauthorized.address)
      ).to.be.reverted;
    });
  });

  describe("Escrow Registration", function () {
    it("Should register escrow contract", async function () {
      await expect(nft.setApprovedEscrow(user2.address, true))
        .to.emit(nft, "EscrowApprovalUpdated")
        .withArgs(user2.address, true);
      expect(await nft.approvedEscrowContracts(user2.address)).to.be.true;
    });

    it("Should unregister escrow contract", async function () {
      await nft.setApprovedEscrow(user2.address, true);
      await nft.setApprovedEscrow(user2.address, false);
      expect(await nft.approvedEscrowContracts(user2.address)).to.be.false;
    });
  });

  describe("Pausable", function () {
    it("Should pause and unpause", async function () {
      await nft.pause();
      await expect(
        nft.connect(minter).mint(user1.address, VIN_HASH, PLATE_HASH, METADATA_URI)
      ).to.be.reverted;

      await nft.unpause();
      await nft.connect(minter).mint(user1.address, VIN_HASH, PLATE_HASH, METADATA_URI);
      expect(await nft.ownerOf(0)).to.equal(user1.address);
    });
  });
});
