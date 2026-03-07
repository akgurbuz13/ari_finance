import { expect } from "chai";
import { ethers } from "hardhat";
import { SignerWithAddress } from "@nomicfoundation/hardhat-ethers/signers";

describe("AriVehicleEscrow", function () {
  let admin: SignerWithAddress;
  let operator: SignerWithAddress;
  let seller: SignerWithAddress;
  let buyer: SignerWithAddress;
  let treasury: SignerWithAddress;
  let unauthorized: SignerWithAddress;

  let stablecoin: any;
  let nft: any;
  let escrow: any;

  const MINTER_ROLE = ethers.keccak256(ethers.toUtf8Bytes("MINTER_ROLE"));
  const OPERATOR_ROLE = ethers.keccak256(ethers.toUtf8Bytes("OPERATOR_ROLE"));
  const VIN_HASH = ethers.keccak256(ethers.toUtf8Bytes("WVWZZZ3CZWE123456"));
  const PLATE_HASH = ethers.keccak256(ethers.toUtf8Bytes("34ABC123"));
  const METADATA_URI = "https://api.arifinance.co/vehicles/1";
  const SALE_AMOUNT = ethers.parseEther("100000"); // 100,000 TRY
  const FEE_AMOUNT = ethers.parseEther("50"); // 50 TRY
  const TOTAL_AMOUNT = SALE_AMOUNT + FEE_AMOUNT;

  let tokenId: number;

  beforeEach(async function () {
    [admin, operator, seller, buyer, treasury, unauthorized] = await ethers.getSigners();

    // Deploy AriStablecoin
    const AriStablecoin = await ethers.getContractFactory("AriStablecoin");
    stablecoin = await AriStablecoin.deploy("ARI Turkish Lira", "ariTRY");
    await stablecoin.waitForDeployment();

    // Deploy AriVehicleNFT
    const AriVehicleNFT = await ethers.getContractFactory("AriVehicleNFT");
    nft = await AriVehicleNFT.deploy();
    await nft.waitForDeployment();

    // Deploy AriVehicleEscrow
    const AriVehicleEscrow = await ethers.getContractFactory("AriVehicleEscrow");
    escrow = await AriVehicleEscrow.deploy(
      await stablecoin.getAddress(),
      await nft.getAddress(),
      treasury.address
    );
    await escrow.waitForDeployment();

    const escrowAddr = await escrow.getAddress();

    // Setup roles on escrow
    await escrow.grantRole(OPERATOR_ROLE, operator.address);

    // Setup NFT: approve escrow contract, allowlist users
    await nft.setApprovedEscrow(escrowAddr, true);
    await nft.addToAllowlist(seller.address);
    await nft.addToAllowlist(buyer.address);

    // Setup stablecoin: grant roles to escrow for burn, allowlist escrow
    await stablecoin.grantRole(MINTER_ROLE, escrowAddr);
    await stablecoin.grantRole(ethers.ZeroHash, escrowAddr); // DEFAULT_ADMIN_ROLE for allowlist
    await stablecoin.addToAllowlist(escrowAddr);
    await stablecoin.addToAllowlist(seller.address);
    await stablecoin.addToAllowlist(buyer.address);
    await stablecoin.addToAllowlist(treasury.address);

    // Mint a vehicle NFT to seller
    const tx = await nft.mint(seller.address, VIN_HASH, PLATE_HASH, METADATA_URI);
    const receipt = await tx.wait();
    tokenId = 0;

    // Seller approves escrow for NFT
    await nft.connect(seller).approve(escrowAddr, tokenId);
  });

  describe("Deployment", function () {
    it("Should set correct immutables", async function () {
      expect(await escrow.stablecoin()).to.equal(await stablecoin.getAddress());
      expect(await escrow.vehicleNFT()).to.equal(await nft.getAddress());
      expect(await escrow.treasury()).to.equal(treasury.address);
    });

    it("Should have correct fee amount", async function () {
      expect(await escrow.FEE_AMOUNT()).to.equal(FEE_AMOUNT);
    });

    it("Should start with escrowId 0", async function () {
      expect(await escrow.nextEscrowId()).to.equal(0);
    });
  });

  describe("Create Escrow", function () {
    it("Should create escrow successfully", async function () {
      const tx = await escrow.connect(operator).createEscrow(
        tokenId, seller.address, buyer.address, SALE_AMOUNT
      );
      await expect(tx)
        .to.emit(escrow, "EscrowCreated")
        .withArgs(0, tokenId, seller.address, buyer.address, SALE_AMOUNT, FEE_AMOUNT);

      const e = await escrow.escrows(0);
      expect(e.tokenId).to.equal(tokenId);
      expect(e.seller).to.equal(seller.address);
      expect(e.buyer).to.equal(buyer.address);
      expect(e.saleAmount).to.equal(SALE_AMOUNT);
      expect(e.feeAmount).to.equal(FEE_AMOUNT);
      expect(e.state).to.equal(0); // CREATED
      expect(e.sellerConfirmed).to.be.false;
      expect(e.buyerConfirmed).to.be.false;
    });

    it("Should reject seller == buyer", async function () {
      await expect(
        escrow.connect(operator).createEscrow(tokenId, seller.address, seller.address, SALE_AMOUNT)
      ).to.be.revertedWith("Seller cannot be buyer");
    });

    it("Should reject wrong NFT owner", async function () {
      await expect(
        escrow.connect(operator).createEscrow(tokenId, buyer.address, seller.address, SALE_AMOUNT)
      ).to.be.revertedWith("Seller does not own NFT");
    });

    it("Should reject zero sale amount", async function () {
      await expect(
        escrow.connect(operator).createEscrow(tokenId, seller.address, buyer.address, 0)
      ).to.be.revertedWith("Sale amount must be > 0");
    });

    it("Should reject non-operator caller", async function () {
      await expect(
        escrow.connect(unauthorized).createEscrow(tokenId, seller.address, buyer.address, SALE_AMOUNT)
      ).to.be.reverted;
    });
  });

  describe("Fund Escrow", function () {
    beforeEach(async function () {
      await escrow.connect(operator).createEscrow(
        tokenId, seller.address, buyer.address, SALE_AMOUNT
      );
    });

    it("Should fund escrow successfully", async function () {
      // Mint ariTRY to escrow contract
      await stablecoin.mint(await escrow.getAddress(), TOTAL_AMOUNT);

      const tx = await escrow.connect(operator).fundEscrow(0);
      await expect(tx)
        .to.emit(escrow, "EscrowFunded")
        .withArgs(0, TOTAL_AMOUNT);

      const e = await escrow.escrows(0);
      expect(e.state).to.equal(1); // FUNDED
      expect(e.fundedAt).to.be.greaterThan(0);
    });

    it("Should reject insufficient balance", async function () {
      // Mint less than required
      await stablecoin.mint(await escrow.getAddress(), SALE_AMOUNT);
      await expect(
        escrow.connect(operator).fundEscrow(0)
      ).to.be.revertedWith("Insufficient balance");
    });

    it("Should reject wrong state (already funded)", async function () {
      await stablecoin.mint(await escrow.getAddress(), TOTAL_AMOUNT);
      await escrow.connect(operator).fundEscrow(0);
      await expect(
        escrow.connect(operator).fundEscrow(0)
      ).to.be.revertedWith("Invalid state");
    });
  });

  describe("Confirmations", function () {
    beforeEach(async function () {
      await escrow.connect(operator).createEscrow(
        tokenId, seller.address, buyer.address, SALE_AMOUNT
      );
      await stablecoin.mint(await escrow.getAddress(), TOTAL_AMOUNT);
      await escrow.connect(operator).fundEscrow(0);
    });

    it("Should record seller confirmation", async function () {
      const tx = await escrow.connect(operator).sellerConfirm(0);
      await expect(tx).to.emit(escrow, "SellerConfirmed").withArgs(0);

      const e = await escrow.escrows(0);
      expect(e.sellerConfirmed).to.be.true;
      expect(e.state).to.equal(1); // Still FUNDED (buyer hasn't confirmed)
    });

    it("Should record buyer confirmation", async function () {
      const tx = await escrow.connect(operator).buyerConfirm(0);
      await expect(tx).to.emit(escrow, "BuyerConfirmed").withArgs(0);

      const e = await escrow.escrows(0);
      expect(e.buyerConfirmed).to.be.true;
      expect(e.state).to.equal(1); // Still FUNDED
    });

    it("Should reject double seller confirmation", async function () {
      await escrow.connect(operator).sellerConfirm(0);
      await expect(
        escrow.connect(operator).sellerConfirm(0)
      ).to.be.revertedWith("Already confirmed");
    });

    it("Should reject double buyer confirmation", async function () {
      await escrow.connect(operator).buyerConfirm(0);
      await expect(
        escrow.connect(operator).buyerConfirm(0)
      ).to.be.revertedWith("Already confirmed");
    });

    it("Should reject confirmation in wrong state (CREATED)", async function () {
      // Create a second escrow, don't fund it
      const VIN2 = ethers.keccak256(ethers.toUtf8Bytes("ANOTHER_VIN"));
      const PLATE2 = ethers.keccak256(ethers.toUtf8Bytes("ANOTHER_PLATE"));
      await nft.mint(seller.address, VIN2, PLATE2, METADATA_URI);
      await nft.connect(seller).approve(await escrow.getAddress(), 1);
      await escrow.connect(operator).createEscrow(1, seller.address, buyer.address, SALE_AMOUNT);

      await expect(
        escrow.connect(operator).sellerConfirm(1)
      ).to.be.revertedWith("Invalid state");
    });
  });

  describe("Atomic Swap (Complete Escrow)", function () {
    beforeEach(async function () {
      await escrow.connect(operator).createEscrow(
        tokenId, seller.address, buyer.address, SALE_AMOUNT
      );
      await stablecoin.mint(await escrow.getAddress(), TOTAL_AMOUNT);
      await escrow.connect(operator).fundEscrow(0);
    });

    it("Should execute swap on second confirmation (seller then buyer)", async function () {
      await escrow.connect(operator).sellerConfirm(0);

      const tx = await escrow.connect(operator).buyerConfirm(0);
      await expect(tx)
        .to.emit(escrow, "BuyerConfirmed").withArgs(0)
        .and.to.emit(escrow, "EscrowCompleted")
        .withArgs(0, seller.address, buyer.address, tokenId, SALE_AMOUNT);

      // Verify NFT transferred to buyer
      expect(await nft.ownerOf(tokenId)).to.equal(buyer.address);

      // Verify ariTRY distributed
      expect(await stablecoin.balanceOf(seller.address)).to.equal(SALE_AMOUNT);
      expect(await stablecoin.balanceOf(treasury.address)).to.equal(FEE_AMOUNT);
      expect(await stablecoin.balanceOf(await escrow.getAddress())).to.equal(0);

      // Verify escrow state
      const e = await escrow.escrows(0);
      expect(e.state).to.equal(2); // COMPLETED
      expect(e.completedAt).to.be.greaterThan(0);
    });

    it("Should execute swap on second confirmation (buyer then seller)", async function () {
      await escrow.connect(operator).buyerConfirm(0);

      const tx = await escrow.connect(operator).sellerConfirm(0);
      await expect(tx)
        .to.emit(escrow, "SellerConfirmed").withArgs(0)
        .and.to.emit(escrow, "EscrowCompleted");

      // Verify NFT transferred to buyer
      expect(await nft.ownerOf(tokenId)).to.equal(buyer.address);

      // Verify ariTRY distributed
      expect(await stablecoin.balanceOf(seller.address)).to.equal(SALE_AMOUNT);
      expect(await stablecoin.balanceOf(treasury.address)).to.equal(FEE_AMOUNT);
    });
  });

  describe("Cancel Escrow", function () {
    beforeEach(async function () {
      await escrow.connect(operator).createEscrow(
        tokenId, seller.address, buyer.address, SALE_AMOUNT
      );
    });

    it("Should cancel before funding", async function () {
      const tx = await escrow.connect(operator).cancel(0);
      await expect(tx)
        .to.emit(escrow, "EscrowCancelled")
        .withArgs(0, false); // refunded = false (wasn't funded)

      const e = await escrow.escrows(0);
      expect(e.state).to.equal(3); // CANCELLED
    });

    it("Should cancel after funding (burns ariTRY)", async function () {
      await stablecoin.mint(await escrow.getAddress(), TOTAL_AMOUNT);
      await escrow.connect(operator).fundEscrow(0);

      const tx = await escrow.connect(operator).cancel(0);
      await expect(tx)
        .to.emit(escrow, "EscrowCancelled")
        .withArgs(0, true); // refunded = true

      // Verify ariTRY was burned
      expect(await stablecoin.balanceOf(await escrow.getAddress())).to.equal(0);

      const e = await escrow.escrows(0);
      expect(e.state).to.equal(3); // CANCELLED
    });

    it("Should cancel after one confirmation", async function () {
      await stablecoin.mint(await escrow.getAddress(), TOTAL_AMOUNT);
      await escrow.connect(operator).fundEscrow(0);
      await escrow.connect(operator).sellerConfirm(0);

      // Can still cancel (only one confirmation)
      await escrow.connect(operator).cancel(0);
      const e = await escrow.escrows(0);
      expect(e.state).to.equal(3); // CANCELLED
    });

    it("Should reject cancel after both confirmations", async function () {
      await stablecoin.mint(await escrow.getAddress(), TOTAL_AMOUNT);
      await escrow.connect(operator).fundEscrow(0);
      await escrow.connect(operator).sellerConfirm(0);
      await escrow.connect(operator).buyerConfirm(0);

      // Both confirmed — cancel should be rejected
      await expect(
        escrow.connect(operator).cancel(0)
      ).to.be.revertedWith("Both confirmed, cannot cancel");
    });

    it("Should reject cancel on already cancelled escrow", async function () {
      await escrow.connect(operator).cancel(0);
      await expect(
        escrow.connect(operator).cancel(0)
      ).to.be.revertedWith("Already cancelled");
    });
  });

  describe("Admin Cancel", function () {
    it("Should allow admin to cancel even after one confirmation", async function () {
      await escrow.connect(operator).createEscrow(
        tokenId, seller.address, buyer.address, SALE_AMOUNT
      );
      await stablecoin.mint(await escrow.getAddress(), TOTAL_AMOUNT);
      await escrow.connect(operator).fundEscrow(0);
      await escrow.connect(operator).sellerConfirm(0);

      await escrow.connect(admin).adminCancel(0);
      const e = await escrow.escrows(0);
      expect(e.state).to.equal(3); // CANCELLED
    });

    it("Should reject admin cancel by non-admin", async function () {
      await escrow.connect(operator).createEscrow(
        tokenId, seller.address, buyer.address, SALE_AMOUNT
      );
      await expect(
        escrow.connect(operator).adminCancel(0)
      ).to.be.reverted;
    });
  });

  describe("Treasury", function () {
    it("Should update treasury address", async function () {
      await expect(escrow.setTreasury(unauthorized.address))
        .to.emit(escrow, "TreasuryUpdated")
        .withArgs(unauthorized.address);
      expect(await escrow.treasury()).to.equal(unauthorized.address);
    });

    it("Should reject zero address treasury", async function () {
      await expect(
        escrow.setTreasury(ethers.ZeroAddress)
      ).to.be.revertedWith("Invalid treasury");
    });
  });

  describe("Full E2E Cycle", function () {
    it("mint -> create -> fund -> seller confirm -> buyer confirm -> verify", async function () {
      // 1. Vehicle NFT already minted in beforeEach

      // 2. Create escrow
      await escrow.connect(operator).createEscrow(
        tokenId, seller.address, buyer.address, SALE_AMOUNT
      );

      // 3. Fund escrow (mint ariTRY to escrow)
      await stablecoin.mint(await escrow.getAddress(), TOTAL_AMOUNT);
      await escrow.connect(operator).fundEscrow(0);

      // 4. Seller confirms
      await escrow.connect(operator).sellerConfirm(0);

      // 5. Buyer confirms -> triggers atomic swap
      await escrow.connect(operator).buyerConfirm(0);

      // 6. Verify everything
      // NFT transferred
      expect(await nft.ownerOf(tokenId)).to.equal(buyer.address);
      // Seller paid
      expect(await stablecoin.balanceOf(seller.address)).to.equal(SALE_AMOUNT);
      // Fee collected
      expect(await stablecoin.balanceOf(treasury.address)).to.equal(FEE_AMOUNT);
      // Escrow drained
      expect(await stablecoin.balanceOf(await escrow.getAddress())).to.equal(0);
      // State completed
      const e = await escrow.escrows(0);
      expect(e.state).to.equal(2); // COMPLETED
    });
  });

  describe("Pausable", function () {
    it("Should block operations when paused", async function () {
      await escrow.pause();
      await expect(
        escrow.connect(operator).createEscrow(tokenId, seller.address, buyer.address, SALE_AMOUNT)
      ).to.be.reverted;
    });
  });
});
