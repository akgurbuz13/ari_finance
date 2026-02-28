import { expect } from "chai";
import { ethers } from "hardhat";
import { loadFixture, time } from "@nomicfoundation/hardhat-toolbox/network-helpers";
import { AriTokenHome, AriStablecoin, MockTeleporter } from "../typechain-types";
import { SignerWithAddress } from "@nomicfoundation/hardhat-ethers/signers";

describe("AriTokenHome", function () {
  let tokenHome: AriTokenHome;
  let stablecoin: AriStablecoin;
  let teleporter: MockTeleporter;
  let admin: SignerWithAddress;
  let bridgeAdmin: SignerWithAddress;
  let user1: SignerWithAddress;
  let user2: SignerWithAddress;

  const PARTNER_CHAIN_ID = ethers.encodeBytes32String("partner-chain-id");
  const THIS_CHAIN_ID = ethers.encodeBytes32String("this-chain-id");

  async function deployFixture() {
    [admin, bridgeAdmin, user1, user2] = await ethers.getSigners();

    // Deploy mock stablecoin
    const AriStablecoin = await ethers.getContractFactory("AriStablecoin");
    const stablecoin = await AriStablecoin.deploy("ARI Turkish Lira", "ariTRY");
    await stablecoin.waitForDeployment();

    // Deploy mock teleporter
    const MockTeleporter = await ethers.getContractFactory("MockTeleporter");
    const teleporter = await MockTeleporter.deploy();
    await teleporter.waitForDeployment();

    // Deploy TokenHome
    const AriTokenHome = await ethers.getContractFactory("AriTokenHome");
    const tokenHome = await AriTokenHome.deploy(
      await stablecoin.getAddress(),
      await teleporter.getAddress(),
      THIS_CHAIN_ID,
      admin.address
    );
    await tokenHome.waitForDeployment();

    // Setup: Grant minter role, allowlist users, mint tokens
    const MINTER_ROLE = await stablecoin.MINTER_ROLE();
    await stablecoin.grantRole(MINTER_ROLE, admin.address);
    await stablecoin.addToAllowlist(user1.address);
    await stablecoin.addToAllowlist(user2.address);
    await stablecoin.addToAllowlist(await tokenHome.getAddress());

    // Mint tokens to user1 for testing
    await stablecoin.mint(user1.address, ethers.parseEther("100000"));

    // Grant BRIDGE_ADMIN_ROLE to bridgeAdmin
    const BRIDGE_ADMIN_ROLE = await tokenHome.BRIDGE_ADMIN_ROLE();
    await tokenHome.grantRole(BRIDGE_ADMIN_ROLE, bridgeAdmin.address);

    return { tokenHome, stablecoin, teleporter, admin, bridgeAdmin, user1, user2 };
  }

  beforeEach(async function () {
    const fixture = await loadFixture(deployFixture);
    tokenHome = fixture.tokenHome;
    stablecoin = fixture.stablecoin;
    teleporter = fixture.teleporter;
    admin = fixture.admin;
    bridgeAdmin = fixture.bridgeAdmin;
    user1 = fixture.user1;
    user2 = fixture.user2;
  });

  describe("Deployment", function () {
    it("should set the correct token address", async function () {
      expect(await tokenHome.token()).to.equal(await stablecoin.getAddress());
    });

    it("should set the correct teleporter address", async function () {
      expect(await tokenHome.teleporterMessenger()).to.equal(await teleporter.getAddress());
    });

    it("should set the correct blockchain ID", async function () {
      expect(await tokenHome.blockchainID()).to.equal(THIS_CHAIN_ID);
    });

    it("should set default limits", async function () {
      expect(await tokenHome.minBridgeAmount()).to.equal(ethers.parseEther("1"));
      expect(await tokenHome.maxBridgeAmount()).to.equal(ethers.parseEther("1000000"));
      expect(await tokenHome.dailyLimit()).to.equal(ethers.parseEther("10000000"));
    });

    it("should grant roles to admin", async function () {
      const BRIDGE_ADMIN_ROLE = await tokenHome.BRIDGE_ADMIN_ROLE();
      const PAUSE_ROLE = await tokenHome.PAUSE_ROLE();
      const DEFAULT_ADMIN_ROLE = await tokenHome.DEFAULT_ADMIN_ROLE();

      expect(await tokenHome.hasRole(DEFAULT_ADMIN_ROLE, admin.address)).to.be.true;
      expect(await tokenHome.hasRole(BRIDGE_ADMIN_ROLE, admin.address)).to.be.true;
      expect(await tokenHome.hasRole(PAUSE_ROLE, admin.address)).to.be.true;
    });
  });

  describe("Remote Registration", function () {
    it("should register a remote TokenRemote", async function () {
      const remoteAddress = user2.address; // Using any address for test

      await expect(tokenHome.connect(bridgeAdmin).registerRemote(PARTNER_CHAIN_ID, remoteAddress))
        .to.emit(tokenHome, "RemoteRegistered")
        .withArgs(PARTNER_CHAIN_ID, remoteAddress);

      expect(await tokenHome.registeredRemotes(PARTNER_CHAIN_ID)).to.equal(remoteAddress);
    });

    it("should reject registration of zero address", async function () {
      await expect(
        tokenHome.connect(bridgeAdmin).registerRemote(PARTNER_CHAIN_ID, ethers.ZeroAddress)
      ).to.be.revertedWith("Invalid remote address");
    });

    it("should reject registration of same chain", async function () {
      await expect(
        tokenHome.connect(bridgeAdmin).registerRemote(THIS_CHAIN_ID, user2.address)
      ).to.be.revertedWith("Cannot register self");
    });

    it("should reject registration from non-admin", async function () {
      await expect(
        tokenHome.connect(user1).registerRemote(PARTNER_CHAIN_ID, user2.address)
      ).to.be.reverted;
    });

    it("should unregister a remote", async function () {
      await tokenHome.connect(bridgeAdmin).registerRemote(PARTNER_CHAIN_ID, user2.address);

      await expect(tokenHome.connect(bridgeAdmin).unregisterRemote(PARTNER_CHAIN_ID))
        .to.emit(tokenHome, "RemoteUnregistered")
        .withArgs(PARTNER_CHAIN_ID);

      expect(await tokenHome.registeredRemotes(PARTNER_CHAIN_ID)).to.equal(ethers.ZeroAddress);
    });
  });

  describe("Bridge Tokens", function () {
    beforeEach(async function () {
      // Register a remote for testing
      await tokenHome.connect(bridgeAdmin).registerRemote(PARTNER_CHAIN_ID, user2.address);
      // Approve TokenHome to spend user1's tokens
      await stablecoin.connect(user1).approve(await tokenHome.getAddress(), ethers.parseEther("100000"));
    });

    it("should bridge tokens successfully", async function () {
      const amount = ethers.parseEther("1000");
      const feeAmount = ethers.parseEther("1");

      const balanceBefore = await stablecoin.balanceOf(user1.address);

      await expect(
        tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, user2.address, amount, feeAmount)
      ).to.emit(tokenHome, "TokensLocked");

      const balanceAfter = await stablecoin.balanceOf(user1.address);
      expect(balanceBefore - balanceAfter).to.equal(amount + feeAmount);

      // Tokens should be locked in TokenHome (amount + fee for teleporter)
      const tokenHomeLocked = await stablecoin.balanceOf(await tokenHome.getAddress());
      // TokenHome receives amount (locked) + feeAmount (for teleporter approval)
      // After approving teleporter, feeAmount may still be held if teleporter is a mock
      expect(tokenHomeLocked).to.be.gte(amount);

      // Total bridged out should be updated
      expect(await tokenHome.totalBridgedOut()).to.equal(amount);
    });

    it("should send message via Teleporter", async function () {
      const amount = ethers.parseEther("1000");

      await tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, user2.address, amount, 0);

      // Verify message was sent to teleporter
      expect(await teleporter.getMessageCount()).to.equal(1);
    });

    it("should reject bridging to unregistered destination", async function () {
      const unknownChainId = ethers.encodeBytes32String("unknown");
      const amount = ethers.parseEther("1000");

      await expect(
        tokenHome.connect(user1).bridgeTokens(unknownChainId, user2.address, amount, 0)
      ).to.be.revertedWith("Destination not registered");
    });

    it("should reject amount below minimum", async function () {
      const amount = ethers.parseEther("0.5"); // Below 1 token minimum

      await expect(
        tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, user2.address, amount, 0)
      ).to.be.revertedWith("Amount below minimum");
    });

    it("should reject amount above maximum", async function () {
      // Need to mint more tokens first
      await stablecoin.mint(user1.address, ethers.parseEther("2000000"));
      await stablecoin.connect(user1).approve(await tokenHome.getAddress(), ethers.parseEther("2000000"));

      const amount = ethers.parseEther("1500000"); // Above 1M maximum

      await expect(
        tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, user2.address, amount, 0)
      ).to.be.revertedWith("Amount exceeds maximum");
    });

    it("should reject bridging to zero address", async function () {
      const amount = ethers.parseEther("1000");

      await expect(
        tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, ethers.ZeroAddress, amount, 0)
      ).to.be.revertedWith("Invalid recipient");
    });
  });

  describe("Daily Limits", function () {
    beforeEach(async function () {
      await tokenHome.connect(bridgeAdmin).registerRemote(PARTNER_CHAIN_ID, user2.address);
      await stablecoin.connect(user1).approve(await tokenHome.getAddress(), ethers.parseEther("100000"));
    });

    it("should track daily bridged amount", async function () {
      const amount = ethers.parseEther("1000");

      await tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, user2.address, amount, 0);

      expect(await tokenHome.getTodayBridgedAmount()).to.equal(amount);
    });

    it("should calculate remaining daily limit", async function () {
      const amount = ethers.parseEther("1000");
      const dailyLimit = await tokenHome.dailyLimit();

      await tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, user2.address, amount, 0);

      expect(await tokenHome.getRemainingDailyLimit()).to.equal(dailyLimit - amount);
    });

    it("should reject bridging that exceeds daily limit", async function () {
      // Set a lower daily limit for testing
      await tokenHome.connect(bridgeAdmin).setLimits(
        ethers.parseEther("1"),
        ethers.parseEther("5000"),
        ethers.parseEther("6000")
      );

      // Mint more tokens
      await stablecoin.mint(user1.address, ethers.parseEther("10000"));
      await stablecoin.connect(user1).approve(await tokenHome.getAddress(), ethers.parseEther("20000"));

      // Bridge up to the limit
      await tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, user2.address, ethers.parseEther("5000"), 0);

      // Try to bridge more
      await expect(
        tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, user2.address, ethers.parseEther("2000"), 0)
      ).to.be.revertedWith("Daily limit exceeded");
    });

    it("should reset daily limit on new day", async function () {
      await tokenHome.connect(bridgeAdmin).setLimits(
        ethers.parseEther("1"),
        ethers.parseEther("5000"),
        ethers.parseEther("6000")
      );

      await stablecoin.mint(user1.address, ethers.parseEther("10000"));
      await stablecoin.connect(user1).approve(await tokenHome.getAddress(), ethers.parseEther("20000"));

      await tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, user2.address, ethers.parseEther("5000"), 0);

      // Fast forward 1 day
      await time.increase(86400);

      // Should be able to bridge again
      await expect(
        tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, user2.address, ethers.parseEther("5000"), 0)
      ).to.not.be.reverted;
    });
  });

  describe("Pause/Unpause", function () {
    beforeEach(async function () {
      await tokenHome.connect(bridgeAdmin).registerRemote(PARTNER_CHAIN_ID, user2.address);
      await stablecoin.connect(user1).approve(await tokenHome.getAddress(), ethers.parseEther("100000"));
    });

    it("should pause the bridge", async function () {
      await expect(tokenHome.connect(admin).pause())
        .to.emit(tokenHome, "BridgePaused")
        .withArgs(admin.address);

      expect(await tokenHome.paused()).to.be.true;
    });

    it("should unpause the bridge", async function () {
      await tokenHome.connect(admin).pause();
      await expect(tokenHome.connect(admin).unpause())
        .to.emit(tokenHome, "BridgeUnpaused")
        .withArgs(admin.address);

      expect(await tokenHome.paused()).to.be.false;
    });

    it("should reject bridging when paused", async function () {
      await tokenHome.connect(admin).pause();
      const amount = ethers.parseEther("1000");

      await expect(
        tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, user2.address, amount, 0)
      ).to.be.revertedWith("Bridge is paused");
    });

    it("should allow bridging after unpause", async function () {
      await tokenHome.connect(admin).pause();
      await tokenHome.connect(admin).unpause();

      const amount = ethers.parseEther("1000");
      await expect(
        tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, user2.address, amount, 0)
      ).to.not.be.reverted;
    });

    it("should reject pause from non-admin", async function () {
      await expect(tokenHome.connect(user1).pause()).to.be.reverted;
    });
  });

  describe("Receive Teleporter Message", function () {
    const partnerTokenRemote = "0x1234567890123456789012345678901234567890";

    beforeEach(async function () {
      await tokenHome.connect(bridgeAdmin).registerRemote(PARTNER_CHAIN_ID, partnerTokenRemote);
      await stablecoin.connect(user1).approve(await tokenHome.getAddress(), ethers.parseEther("100000"));
      // Lock some tokens first
      await tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, user2.address, ethers.parseEther("1000"), 0);
    });

    it("should release tokens on valid message from registered remote", async function () {
      const burnMessageID = ethers.encodeBytes32String("burn-msg-1");
      const amount = ethers.parseEther("500");

      // Encode the message
      const message = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256", "bytes32"],
        [user2.address, user1.address, amount, burnMessageID]
      );

      const balanceBefore = await stablecoin.balanceOf(user1.address);

      // Deliver message via mock teleporter
      await teleporter.deliverMessage(
        PARTNER_CHAIN_ID,
        partnerTokenRemote,
        await tokenHome.getAddress(),
        message
      );

      const balanceAfter = await stablecoin.balanceOf(user1.address);
      expect(balanceAfter - balanceBefore).to.equal(amount);

      // Total bridged out should decrease
      expect(await tokenHome.totalBridgedOut()).to.equal(ethers.parseEther("500"));
    });

    it("should reject message from unknown remote", async function () {
      const unknownRemote = "0xabcdef0123456789012345678901234567890abc";
      const burnMessageID = ethers.encodeBytes32String("burn-msg-2");
      const message = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256", "bytes32"],
        [user2.address, user1.address, ethers.parseEther("500"), burnMessageID]
      );

      await expect(
        teleporter.deliverMessage(
          PARTNER_CHAIN_ID,
          unknownRemote,
          await tokenHome.getAddress(),
          message
        )
      ).to.be.revertedWith("Unknown remote");
    });

    it("should reject replayed message", async function () {
      const burnMessageID = ethers.encodeBytes32String("burn-msg-3");
      const message = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256", "bytes32"],
        [user2.address, user1.address, ethers.parseEther("100"), burnMessageID]
      );

      // First delivery should succeed
      await teleporter.deliverMessage(
        PARTNER_CHAIN_ID,
        partnerTokenRemote,
        await tokenHome.getAddress(),
        message
      );

      // Second delivery should fail
      await expect(
        teleporter.deliverMessage(
          PARTNER_CHAIN_ID,
          partnerTokenRemote,
          await tokenHome.getAddress(),
          message
        )
      ).to.be.revertedWith("Already processed");
    });
  });

  describe("Set Limits", function () {
    it("should update limits", async function () {
      const newMin = ethers.parseEther("10");
      const newMax = ethers.parseEther("500000");
      const newDaily = ethers.parseEther("5000000");

      await expect(tokenHome.connect(bridgeAdmin).setLimits(newMin, newMax, newDaily))
        .to.emit(tokenHome, "LimitsUpdated")
        .withArgs(newMin, newMax, newDaily);

      expect(await tokenHome.minBridgeAmount()).to.equal(newMin);
      expect(await tokenHome.maxBridgeAmount()).to.equal(newMax);
      expect(await tokenHome.dailyLimit()).to.equal(newDaily);
    });

    it("should reject invalid limits (min >= max)", async function () {
      await expect(
        tokenHome.connect(bridgeAdmin).setLimits(
          ethers.parseEther("1000"),
          ethers.parseEther("100"),
          ethers.parseEther("10000")
        )
      ).to.be.revertedWith("Invalid limits");
    });

    it("should reject invalid limits (max > daily)", async function () {
      await expect(
        tokenHome.connect(bridgeAdmin).setLimits(
          ethers.parseEther("1"),
          ethers.parseEther("10000"),
          ethers.parseEther("5000")
        )
      ).to.be.revertedWith("Max exceeds daily");
    });
  });

  describe("Emergency Withdrawal", function () {
    beforeEach(async function () {
      await tokenHome.connect(bridgeAdmin).registerRemote(PARTNER_CHAIN_ID, user2.address);
      await stablecoin.connect(user1).approve(await tokenHome.getAddress(), ethers.parseEther("100000"));
      await tokenHome.connect(user1).bridgeTokens(PARTNER_CHAIN_ID, user2.address, ethers.parseEther("1000"), 0);
      // Ensure admin is allowlisted for receiving tokens
      await stablecoin.addToAllowlist(admin.address);
    });

    it("should allow emergency withdrawal when paused", async function () {
      await tokenHome.connect(admin).pause();

      const balanceBefore = await stablecoin.balanceOf(admin.address);
      await tokenHome.connect(admin).emergencyWithdraw(admin.address, ethers.parseEther("500"));
      const balanceAfter = await stablecoin.balanceOf(admin.address);

      expect(balanceAfter - balanceBefore).to.equal(ethers.parseEther("500"));
    });

    it("should reject emergency withdrawal when not paused", async function () {
      await expect(
        tokenHome.connect(admin).emergencyWithdraw(admin.address, ethers.parseEther("500"))
      ).to.be.revertedWith("Must be paused");
    });

    it("should reject emergency withdrawal to zero address", async function () {
      await tokenHome.connect(admin).pause();
      await expect(
        tokenHome.connect(admin).emergencyWithdraw(ethers.ZeroAddress, ethers.parseEther("500"))
      ).to.be.revertedWith("Invalid recipient");
    });
  });
});
