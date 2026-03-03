import { expect } from "chai";
import { ethers } from "hardhat";
import { loadFixture } from "@nomicfoundation/hardhat-toolbox/network-helpers";
import { AriBridgeAdapter, AriTokenHome, AriTokenRemote, AriStablecoin, MockTeleporter } from "../typechain-types";
import { SignerWithAddress } from "@nomicfoundation/hardhat-ethers/signers";

describe("AriBridgeAdapter", function () {
  let bridgeAdapter: AriBridgeAdapter;
  let tokenHome: AriTokenHome;
  let tokenRemote: AriTokenRemote;
  let nativeToken: AriStablecoin;
  let teleporter: MockTeleporter;
  let admin: SignerWithAddress;
  let bridgeOperator: SignerWithAddress;
  let user1: SignerWithAddress;
  let user2: SignerWithAddress;

  const THIS_CHAIN_ID = ethers.encodeBytes32String("tr-chain-id");
  const PARTNER_CHAIN_ID = ethers.encodeBytes32String("eu-chain-id");

  async function deployFixture() {
    [admin, bridgeOperator, user1, user2] = await ethers.getSigners();

    // Deploy mock teleporter
    const MockTeleporter = await ethers.getContractFactory("MockTeleporter");
    const teleporter = await MockTeleporter.deploy();
    await teleporter.waitForDeployment();

    // Deploy native token (ariTRY)
    const AriStablecoin = await ethers.getContractFactory("AriStablecoin");
    const nativeToken = await AriStablecoin.deploy("ARI Turkish Lira", "ariTRY");
    await nativeToken.waitForDeployment();

    // Setup native token
    const MINTER_ROLE = await nativeToken.MINTER_ROLE();
    await nativeToken.grantRole(MINTER_ROLE, admin.address);
    await nativeToken.addToAllowlist(user1.address);
    await nativeToken.addToAllowlist(user2.address);
    await nativeToken.mint(user1.address, ethers.parseEther("100000"));

    // Deploy TokenHome
    const AriTokenHome = await ethers.getContractFactory("AriTokenHome");
    const tokenHome = await AriTokenHome.deploy(
      await nativeToken.getAddress(),
      await teleporter.getAddress(),
      THIS_CHAIN_ID,
      admin.address
    );
    await tokenHome.waitForDeployment();

    // Deploy TokenRemote (for wrapped partner tokens)
    const AriTokenRemote = await ethers.getContractFactory("AriTokenRemote");
    const tokenRemote = await AriTokenRemote.deploy(
      "Wrapped ariEUR",
      "wEUR",
      await teleporter.getAddress(),
      THIS_CHAIN_ID,
      admin.address
    );
    await tokenRemote.waitForDeployment();

    // Setup TokenRemote
    const BRIDGE_ADMIN_ROLE = await tokenRemote.BRIDGE_ADMIN_ROLE();
    await tokenRemote.grantRole(BRIDGE_ADMIN_ROLE, admin.address);
    await tokenRemote.addToAllowlist(user1.address);
    await tokenRemote.addToAllowlist(user2.address);

    // Deploy BridgeAdapter
    const AriBridgeAdapter = await ethers.getContractFactory("AriBridgeAdapter");
    const bridgeAdapter = await AriBridgeAdapter.deploy(
      await nativeToken.getAddress(),
      admin.address
    );
    await bridgeAdapter.waitForDeployment();

    // Configure BridgeAdapter
    await bridgeAdapter.connect(admin).configure(
      await tokenRemote.getAddress(),
      await tokenHome.getAddress(),
      await tokenRemote.getAddress(),
      THIS_CHAIN_ID,
      PARTNER_CHAIN_ID
    );

    // Grant BRIDGE_OPERATOR_ROLE
    const OPERATOR_ROLE = await bridgeAdapter.BRIDGE_OPERATOR_ROLE();
    await bridgeAdapter.grantRole(OPERATOR_ROLE, bridgeOperator.address);

    // Setup allowlists and approvals
    await nativeToken.addToAllowlist(await tokenHome.getAddress());
    await nativeToken.addToAllowlist(await bridgeAdapter.getAddress());
    await tokenRemote.addToAllowlist(await bridgeAdapter.getAddress());

    // TokenHome needs to know about partner remote
    const TOKEN_HOME_ADMIN = await tokenHome.BRIDGE_ADMIN_ROLE();
    await tokenHome.grantRole(TOKEN_HOME_ADMIN, admin.address);
    await tokenHome.registerRemote(PARTNER_CHAIN_ID, user2.address); // Partner TokenRemote address

    // TokenRemote needs home chain registration
    await tokenRemote.registerHomeChain(PARTNER_CHAIN_ID, user2.address);

    return {
      bridgeAdapter,
      tokenHome,
      tokenRemote,
      nativeToken,
      teleporter,
      admin,
      bridgeOperator,
      user1,
      user2,
    };
  }

  beforeEach(async function () {
    const fixture = await loadFixture(deployFixture);
    bridgeAdapter = fixture.bridgeAdapter;
    tokenHome = fixture.tokenHome;
    tokenRemote = fixture.tokenRemote;
    nativeToken = fixture.nativeToken;
    teleporter = fixture.teleporter;
    admin = fixture.admin;
    bridgeOperator = fixture.bridgeOperator;
    user1 = fixture.user1;
    user2 = fixture.user2;
  });

  describe("Deployment", function () {
    it("should set the correct native token", async function () {
      expect(await bridgeAdapter.nativeToken()).to.equal(await nativeToken.getAddress());
    });

    it("should grant roles to admin", async function () {
      const DEFAULT_ADMIN_ROLE = await bridgeAdapter.DEFAULT_ADMIN_ROLE();
      const BRIDGE_OPERATOR_ROLE = await bridgeAdapter.BRIDGE_OPERATOR_ROLE();

      expect(await bridgeAdapter.hasRole(DEFAULT_ADMIN_ROLE, admin.address)).to.be.true;
      expect(await bridgeAdapter.hasRole(BRIDGE_OPERATOR_ROLE, admin.address)).to.be.true;
    });

    it("should not be paused by default", async function () {
      expect(await bridgeAdapter.paused()).to.be.false;
    });
  });

  describe("Configuration", function () {
    it("should configure contract addresses", async function () {
      expect(await bridgeAdapter.wrappedToken()).to.equal(await tokenRemote.getAddress());
      expect(await bridgeAdapter.tokenHome()).to.equal(await tokenHome.getAddress());
      expect(await bridgeAdapter.tokenRemote()).to.equal(await tokenRemote.getAddress());
      expect(await bridgeAdapter.blockchainID()).to.equal(THIS_CHAIN_ID);
      expect(await bridgeAdapter.partnerChainID()).to.equal(PARTNER_CHAIN_ID);
    });

    it("should emit ContractsUpdated event on configure", async function () {
      const AriBridgeAdapter = await ethers.getContractFactory("AriBridgeAdapter");
      const newAdapter = await AriBridgeAdapter.deploy(
        await nativeToken.getAddress(),
        admin.address
      );

      await expect(
        newAdapter.configure(
          await tokenRemote.getAddress(),
          await tokenHome.getAddress(),
          await tokenRemote.getAddress(),
          THIS_CHAIN_ID,
          PARTNER_CHAIN_ID
        )
      ).to.emit(newAdapter, "ContractsUpdated");
    });

    it("should reject configuration from non-admin", async function () {
      await expect(
        bridgeAdapter.connect(user1).configure(
          await tokenRemote.getAddress(),
          await tokenHome.getAddress(),
          await tokenRemote.getAddress(),
          THIS_CHAIN_ID,
          PARTNER_CHAIN_ID
        )
      ).to.be.reverted;
    });
  });

  describe("Bridge Native Tokens", function () {
    beforeEach(async function () {
      // Approve adapter to spend user's native tokens
      await nativeToken.connect(user1).approve(
        await bridgeAdapter.getAddress(),
        ethers.parseEther("100000")
      );
    });

    it("should bridge native tokens successfully", async function () {
      const amount = ethers.parseEther("1000");
      const feeAmount = ethers.parseEther("1");
      const balanceBefore = await nativeToken.balanceOf(user1.address);

      await expect(
        bridgeAdapter.connect(user1).bridgeNativeTokens(user2.address, amount, feeAmount)
      ).to.emit(bridgeAdapter, "BridgeTransferInitiated");

      const balanceAfter = await nativeToken.balanceOf(user1.address);
      expect(balanceBefore - balanceAfter).to.equal(amount + feeAmount);
    });

    it("should create transfer record", async function () {
      const amount = ethers.parseEther("1000");

      const tx = await bridgeAdapter.connect(user1).bridgeNativeTokens(user2.address, amount, 0);
      const receipt = await tx.wait();

      // Extract transfer ID from event
      const event = receipt?.logs.find(
        log => bridgeAdapter.interface.parseLog({ topics: log.topics as string[], data: log.data })?.name === "BridgeTransferInitiated"
      );
      const parsedEvent = bridgeAdapter.interface.parseLog({
        topics: event!.topics as string[],
        data: event!.data,
      });
      const transferId = parsedEvent!.args.transferId;

      const transfer = await bridgeAdapter.getTransfer(transferId);
      expect(transfer.sender).to.equal(user1.address);
      expect(transfer.recipient).to.equal(user2.address);
      expect(transfer.amount).to.equal(amount);
      expect(transfer.status).to.equal(1); // INITIATED
    });

    it("should reject when TokenHome not configured", async function () {
      const AriBridgeAdapter = await ethers.getContractFactory("AriBridgeAdapter");
      const unconfiguredAdapter = await AriBridgeAdapter.deploy(
        await nativeToken.getAddress(),
        admin.address
      );

      await nativeToken.addToAllowlist(await unconfiguredAdapter.getAddress());
      await nativeToken.connect(user1).approve(
        await unconfiguredAdapter.getAddress(),
        ethers.parseEther("1000")
      );

      await expect(
        unconfiguredAdapter.connect(user1).bridgeNativeTokens(
          user2.address,
          ethers.parseEther("100"),
          0
        )
      ).to.be.revertedWith("TokenHome not configured");
    });

    it("should reject zero amount", async function () {
      await expect(
        bridgeAdapter.connect(user1).bridgeNativeTokens(user2.address, 0, 0)
      ).to.be.revertedWith("Amount must be positive");
    });

    it("should reject zero recipient", async function () {
      await expect(
        bridgeAdapter.connect(user1).bridgeNativeTokens(
          ethers.ZeroAddress,
          ethers.parseEther("100"),
          0
        )
      ).to.be.revertedWith("Invalid recipient");
    });

    it("should reject when paused", async function () {
      await bridgeAdapter.connect(admin).pause();

      await expect(
        bridgeAdapter.connect(user1).bridgeNativeTokens(
          user2.address,
          ethers.parseEther("100"),
          0
        )
      ).to.be.revertedWith("Adapter paused");
    });
  });

  describe("Bridge Wrapped Tokens Back", function () {
    beforeEach(async function () {
      // Mint wrapped tokens to user1 for testing
      const mintMessage = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256"],
        [user2.address, user1.address, ethers.parseEther("5000")]
      );
      await teleporter.deliverMessage(
        PARTNER_CHAIN_ID,
        user2.address, // TokenHome address
        await tokenRemote.getAddress(),
        mintMessage
      );

      // Approve adapter to spend wrapped tokens
      await tokenRemote.connect(user1).approve(
        await bridgeAdapter.getAddress(),
        ethers.parseEther("5000")
      );
    });

    it("should bridge wrapped tokens back successfully", async function () {
      const amount = ethers.parseEther("1000");
      const balanceBefore = await tokenRemote.balanceOf(user1.address);

      await expect(
        bridgeAdapter.connect(user1).bridgeWrappedTokensBack(user2.address, amount, 0)
      ).to.emit(bridgeAdapter, "BridgeTransferInitiated");

      const balanceAfter = await tokenRemote.balanceOf(user1.address);
      expect(balanceBefore - balanceAfter).to.equal(amount);
    });

    it("should reject when TokenRemote not configured", async function () {
      const AriBridgeAdapter = await ethers.getContractFactory("AriBridgeAdapter");
      const unconfiguredAdapter = await AriBridgeAdapter.deploy(
        await nativeToken.getAddress(),
        admin.address
      );

      await expect(
        unconfiguredAdapter.connect(user1).bridgeWrappedTokensBack(
          user2.address,
          ethers.parseEther("100"),
          0
        )
      ).to.be.revertedWith("TokenRemote not configured");
    });

    it("should reject zero amount", async function () {
      await expect(
        bridgeAdapter.connect(user1).bridgeWrappedTokensBack(user2.address, 0, 0)
      ).to.be.revertedWith("Amount must be positive");
    });
  });

  describe("Transfer Status Management", function () {
    let transferId: string;

    beforeEach(async function () {
      await nativeToken.connect(user1).approve(
        await bridgeAdapter.getAddress(),
        ethers.parseEther("100000")
      );

      const tx = await bridgeAdapter.connect(user1).bridgeNativeTokens(
        user2.address,
        ethers.parseEther("1000"),
        0
      );
      const receipt = await tx.wait();

      const event = receipt?.logs.find(
        log => bridgeAdapter.interface.parseLog({ topics: log.topics as string[], data: log.data })?.name === "BridgeTransferInitiated"
      );
      const parsedEvent = bridgeAdapter.interface.parseLog({
        topics: event!.topics as string[],
        data: event!.data,
      });
      transferId = parsedEvent!.args.transferId;
    });

    it("should mark transfer as completed", async function () {
      await expect(
        bridgeAdapter.connect(bridgeOperator).markTransferCompleted(transferId)
      ).to.emit(bridgeAdapter, "BridgeTransferCompleted");

      const transfer = await bridgeAdapter.getTransfer(transferId);
      expect(transfer.status).to.equal(2); // COMPLETED
    });

    it("should mark transfer as failed", async function () {
      await bridgeAdapter.connect(bridgeOperator).markTransferFailed(transferId);

      const transfer = await bridgeAdapter.getTransfer(transferId);
      expect(transfer.status).to.equal(3); // FAILED
    });

    it("should reject marking completed if not initiated", async function () {
      await bridgeAdapter.connect(bridgeOperator).markTransferCompleted(transferId);

      // Try to mark as completed again
      await expect(
        bridgeAdapter.connect(bridgeOperator).markTransferCompleted(transferId)
      ).to.be.revertedWith("Invalid transfer status");
    });

    it("should reject marking from non-operator", async function () {
      await expect(
        bridgeAdapter.connect(user1).markTransferCompleted(transferId)
      ).to.be.reverted;
    });

    it("should retrieve transfer details", async function () {
      const transfer = await bridgeAdapter.getTransfer(transferId);

      expect(transfer.sender).to.equal(user1.address);
      expect(transfer.recipient).to.equal(user2.address);
      expect(transfer.amount).to.equal(ethers.parseEther("1000"));
      expect(transfer.destinationChainID).to.equal(PARTNER_CHAIN_ID);
      expect(transfer.status).to.equal(1); // INITIATED
    });
  });

  describe("Pause/Unpause", function () {
    it("should pause the adapter", async function () {
      await expect(bridgeAdapter.connect(admin).pause())
        .to.emit(bridgeAdapter, "AdapterPaused")
        .withArgs(admin.address);

      expect(await bridgeAdapter.paused()).to.be.true;
    });

    it("should unpause the adapter", async function () {
      await bridgeAdapter.connect(admin).pause();

      await expect(bridgeAdapter.connect(admin).unpause())
        .to.emit(bridgeAdapter, "AdapterUnpaused")
        .withArgs(admin.address);

      expect(await bridgeAdapter.paused()).to.be.false;
    });

    it("should reject pause from non-admin", async function () {
      await expect(bridgeAdapter.connect(user1).pause()).to.be.reverted;
    });

    it("should reject unpause from non-admin", async function () {
      await bridgeAdapter.connect(admin).pause();
      await expect(bridgeAdapter.connect(user1).unpause()).to.be.reverted;
    });
  });

  describe("Emergency Withdrawal", function () {
    beforeEach(async function () {
      // Send some tokens to the adapter
      await nativeToken.connect(user1).transfer(
        await bridgeAdapter.getAddress(),
        ethers.parseEther("100")
      );
      // Ensure admin is allowlisted for withdrawal
      await nativeToken.addToAllowlist(admin.address);
    });

    it("should allow emergency withdrawal when paused", async function () {
      await bridgeAdapter.connect(admin).pause();

      const balanceBefore = await nativeToken.balanceOf(admin.address);
      await bridgeAdapter.connect(admin).emergencyWithdraw(
        await nativeToken.getAddress(),
        admin.address,
        ethers.parseEther("50")
      );
      const balanceAfter = await nativeToken.balanceOf(admin.address);

      expect(balanceAfter - balanceBefore).to.equal(ethers.parseEther("50"));
    });

    it("should reject emergency withdrawal when not paused", async function () {
      await expect(
        bridgeAdapter.connect(admin).emergencyWithdraw(
          await nativeToken.getAddress(),
          admin.address,
          ethers.parseEther("50")
        )
      ).to.be.revertedWith("Must be paused");
    });

    it("should reject withdrawal to zero address", async function () {
      await bridgeAdapter.connect(admin).pause();

      await expect(
        bridgeAdapter.connect(admin).emergencyWithdraw(
          await nativeToken.getAddress(),
          ethers.ZeroAddress,
          ethers.parseEther("50")
        )
      ).to.be.revertedWith("Invalid recipient");
    });

    it("should reject from non-admin", async function () {
      await bridgeAdapter.connect(admin).pause();

      await expect(
        bridgeAdapter.connect(user1).emergencyWithdraw(
          await nativeToken.getAddress(),
          user1.address,
          ethers.parseEther("50")
        )
      ).to.be.reverted;
    });
  });
});
