import { expect } from "chai";
import { ethers } from "hardhat";
import { loadFixture } from "@nomicfoundation/hardhat-toolbox/network-helpers";
import { OvaTokenRemote, MockTeleporter } from "../typechain-types";
import { SignerWithAddress } from "@nomicfoundation/hardhat-ethers/signers";

describe("OvaTokenRemote", function () {
  let tokenRemote: OvaTokenRemote;
  let teleporter: MockTeleporter;
  let admin: SignerWithAddress;
  let bridgeAdmin: SignerWithAddress;
  let user1: SignerWithAddress;
  let user2: SignerWithAddress;
  let nonKycUser: SignerWithAddress;

  const HOME_CHAIN_ID = ethers.encodeBytes32String("home-chain-id");
  const THIS_CHAIN_ID = ethers.encodeBytes32String("this-chain-id");
  const TOKEN_HOME_ADDRESS = "0x1234567890123456789012345678901234567890";

  async function deployFixture() {
    [admin, bridgeAdmin, user1, user2, nonKycUser] = await ethers.getSigners();

    // Deploy mock teleporter
    const MockTeleporter = await ethers.getContractFactory("MockTeleporter");
    const teleporter = await MockTeleporter.deploy();
    await teleporter.waitForDeployment();

    // Deploy TokenRemote
    const OvaTokenRemote = await ethers.getContractFactory("OvaTokenRemote");
    const tokenRemote = await OvaTokenRemote.deploy(
      "Wrapped ovaTRY",
      "wTRY",
      await teleporter.getAddress(),
      THIS_CHAIN_ID,
      admin.address
    );
    await tokenRemote.waitForDeployment();

    // Grant BRIDGE_ADMIN_ROLE
    const BRIDGE_ADMIN_ROLE = await tokenRemote.BRIDGE_ADMIN_ROLE();
    await tokenRemote.grantRole(BRIDGE_ADMIN_ROLE, bridgeAdmin.address);

    // Setup allowlist for users
    await tokenRemote.connect(admin).addToAllowlist(user1.address);
    await tokenRemote.connect(admin).addToAllowlist(user2.address);

    return { tokenRemote, teleporter, admin, bridgeAdmin, user1, user2, nonKycUser };
  }

  beforeEach(async function () {
    const fixture = await loadFixture(deployFixture);
    tokenRemote = fixture.tokenRemote;
    teleporter = fixture.teleporter;
    admin = fixture.admin;
    bridgeAdmin = fixture.bridgeAdmin;
    user1 = fixture.user1;
    user2 = fixture.user2;
    nonKycUser = fixture.nonKycUser;
  });

  describe("Deployment", function () {
    it("should set the correct name and symbol", async function () {
      expect(await tokenRemote.name()).to.equal("Wrapped ovaTRY");
      expect(await tokenRemote.symbol()).to.equal("wTRY");
    });

    it("should set the correct teleporter address", async function () {
      expect(await tokenRemote.teleporterMessenger()).to.equal(await teleporter.getAddress());
    });

    it("should set the correct blockchain ID", async function () {
      expect(await tokenRemote.blockchainID()).to.equal(THIS_CHAIN_ID);
    });

    it("should grant roles to admin", async function () {
      const BRIDGE_ADMIN_ROLE = await tokenRemote.BRIDGE_ADMIN_ROLE();
      const PAUSE_ROLE = await tokenRemote.PAUSE_ROLE();
      const FREEZER_ROLE = await tokenRemote.FREEZER_ROLE();
      const DEFAULT_ADMIN_ROLE = await tokenRemote.DEFAULT_ADMIN_ROLE();

      expect(await tokenRemote.hasRole(DEFAULT_ADMIN_ROLE, admin.address)).to.be.true;
      expect(await tokenRemote.hasRole(BRIDGE_ADMIN_ROLE, admin.address)).to.be.true;
      expect(await tokenRemote.hasRole(PAUSE_ROLE, admin.address)).to.be.true;
      expect(await tokenRemote.hasRole(FREEZER_ROLE, admin.address)).to.be.true;
    });

    it("should allowlist zero address and self by default", async function () {
      expect(await tokenRemote.allowlisted(ethers.ZeroAddress)).to.be.true;
      expect(await tokenRemote.allowlisted(await tokenRemote.getAddress())).to.be.true;
    });
  });

  describe("Home Chain Registration", function () {
    it("should register home chain", async function () {
      await expect(
        tokenRemote.connect(bridgeAdmin).registerHomeChain(HOME_CHAIN_ID, TOKEN_HOME_ADDRESS)
      )
        .to.emit(tokenRemote, "HomeChainRegistered")
        .withArgs(HOME_CHAIN_ID, TOKEN_HOME_ADDRESS);

      expect(await tokenRemote.homeChainID()).to.equal(HOME_CHAIN_ID);
      expect(await tokenRemote.tokenHomeAddress()).to.equal(TOKEN_HOME_ADDRESS);
    });

    it("should reject zero address for home", async function () {
      await expect(
        tokenRemote.connect(bridgeAdmin).registerHomeChain(HOME_CHAIN_ID, ethers.ZeroAddress)
      ).to.be.revertedWith("Invalid home address");
    });

    it("should reject same chain as home", async function () {
      await expect(
        tokenRemote.connect(bridgeAdmin).registerHomeChain(THIS_CHAIN_ID, TOKEN_HOME_ADDRESS)
      ).to.be.revertedWith("Cannot be same chain");
    });

    it("should reject registration from non-admin", async function () {
      await expect(
        tokenRemote.connect(user1).registerHomeChain(HOME_CHAIN_ID, TOKEN_HOME_ADDRESS)
      ).to.be.reverted;
    });
  });

  describe("KYC Allowlist", function () {
    it("should add address to allowlist", async function () {
      await expect(tokenRemote.connect(admin).addToAllowlist(nonKycUser.address))
        .to.emit(tokenRemote, "AddressAllowlisted")
        .withArgs(nonKycUser.address);

      expect(await tokenRemote.allowlisted(nonKycUser.address)).to.be.true;
    });

    it("should remove address from allowlist", async function () {
      await tokenRemote.connect(admin).addToAllowlist(nonKycUser.address);

      await expect(tokenRemote.connect(admin).removeFromAllowlist(nonKycUser.address))
        .to.emit(tokenRemote, "AddressRemovedFromAllowlist")
        .withArgs(nonKycUser.address);

      expect(await tokenRemote.allowlisted(nonKycUser.address)).to.be.false;
    });

    it("should batch add to allowlist", async function () {
      const addresses = [nonKycUser.address, "0xabcdef0123456789012345678901234567890abc"];
      await tokenRemote.connect(admin).batchAddToAllowlist(addresses);

      expect(await tokenRemote.allowlisted(nonKycUser.address)).to.be.true;
      expect(await tokenRemote.allowlisted(addresses[1])).to.be.true;
    });

    it("should report canHold correctly", async function () {
      expect(await tokenRemote.canHold(user1.address)).to.be.true;
      expect(await tokenRemote.canHold(nonKycUser.address)).to.be.false;
    });
  });

  describe("Receive Teleporter Message (Mint)", function () {
    beforeEach(async function () {
      await tokenRemote.connect(bridgeAdmin).registerHomeChain(HOME_CHAIN_ID, TOKEN_HOME_ADDRESS);
    });

    it("should mint wrapped tokens on valid message from home chain", async function () {
      const amount = ethers.parseEther("1000");

      // Encode the message from home chain
      const message = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256"],
        [user1.address, user1.address, amount]
      );

      await teleporter.deliverMessage(
        HOME_CHAIN_ID,
        TOKEN_HOME_ADDRESS,
        await tokenRemote.getAddress(),
        message
      );

      expect(await tokenRemote.balanceOf(user1.address)).to.equal(amount);
    });

    it("should emit TokensMinted event", async function () {
      const amount = ethers.parseEther("1000");
      const message = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256"],
        [user1.address, user1.address, amount]
      );

      await expect(
        teleporter.deliverMessage(
          HOME_CHAIN_ID,
          TOKEN_HOME_ADDRESS,
          await tokenRemote.getAddress(),
          message
        )
      ).to.emit(tokenRemote, "TokensMinted");
    });

    it("should reject message from unknown source chain", async function () {
      const unknownChain = ethers.encodeBytes32String("unknown-chain");
      const message = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256"],
        [user1.address, user1.address, ethers.parseEther("1000")]
      );

      await expect(
        teleporter.deliverMessage(
          unknownChain,
          TOKEN_HOME_ADDRESS,
          await tokenRemote.getAddress(),
          message
        )
      ).to.be.revertedWith("Unknown source chain");
    });

    it("should reject message from unknown sender", async function () {
      const unknownSender = "0xabcdef0123456789012345678901234567890abc";
      const message = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256"],
        [user1.address, user1.address, ethers.parseEther("1000")]
      );

      await expect(
        teleporter.deliverMessage(
          HOME_CHAIN_ID,
          unknownSender,
          await tokenRemote.getAddress(),
          message
        )
      ).to.be.revertedWith("Unknown sender");
    });

    it("should reject mint to non-KYC recipient", async function () {
      const amount = ethers.parseEther("1000");
      const message = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256"],
        [user1.address, nonKycUser.address, amount]
      );

      await expect(
        teleporter.deliverMessage(
          HOME_CHAIN_ID,
          TOKEN_HOME_ADDRESS,
          await tokenRemote.getAddress(),
          message
        )
      ).to.be.revertedWith("Recipient not KYC verified");
    });

    it("should reject mint to frozen recipient", async function () {
      await tokenRemote.connect(admin).freeze(user1.address);

      const amount = ethers.parseEther("1000");
      const message = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256"],
        [user2.address, user1.address, amount]
      );

      await expect(
        teleporter.deliverMessage(
          HOME_CHAIN_ID,
          TOKEN_HOME_ADDRESS,
          await tokenRemote.getAddress(),
          message
        )
      ).to.be.revertedWith("Recipient frozen");
    });
  });

  describe("Bridge Back (Burn)", function () {
    beforeEach(async function () {
      await tokenRemote.connect(bridgeAdmin).registerHomeChain(HOME_CHAIN_ID, TOKEN_HOME_ADDRESS);

      // Mint some tokens to user1 for testing
      const mintMessage = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256"],
        [user2.address, user1.address, ethers.parseEther("5000")]
      );
      await teleporter.deliverMessage(
        HOME_CHAIN_ID,
        TOKEN_HOME_ADDRESS,
        await tokenRemote.getAddress(),
        mintMessage
      );
    });

    it("should burn tokens and send bridgeBack message", async function () {
      const amount = ethers.parseEther("1000");
      const balanceBefore = await tokenRemote.balanceOf(user1.address);

      await expect(
        tokenRemote.connect(user1).bridgeBack(user2.address, amount, 0)
      ).to.emit(tokenRemote, "TokensBurned");

      const balanceAfter = await tokenRemote.balanceOf(user1.address);
      expect(balanceBefore - balanceAfter).to.equal(amount);

      // Verify message was sent
      expect(await teleporter.getMessageCount()).to.be.greaterThan(0);
    });

    it("should reject bridgeBack with zero amount", async function () {
      await expect(
        tokenRemote.connect(user1).bridgeBack(user2.address, 0, 0)
      ).to.be.revertedWith("Amount must be positive");
    });

    it("should reject bridgeBack to zero address", async function () {
      await expect(
        tokenRemote.connect(user1).bridgeBack(ethers.ZeroAddress, ethers.parseEther("100"), 0)
      ).to.be.revertedWith("Invalid recipient");
    });

    it("should reject bridgeBack before home chain is set", async function () {
      // Deploy a new TokenRemote without registering home chain
      const MockTeleporter = await ethers.getContractFactory("MockTeleporter");
      const newTeleporter = await MockTeleporter.deploy();

      const OvaTokenRemote = await ethers.getContractFactory("OvaTokenRemote");
      const newRemote = await OvaTokenRemote.deploy(
        "Test Token",
        "TEST",
        await newTeleporter.getAddress(),
        THIS_CHAIN_ID,
        admin.address
      );

      await newRemote.connect(admin).addToAllowlist(user1.address);

      // This should fail because home chain not registered
      await expect(
        newRemote.connect(user1).bridgeBack(user2.address, ethers.parseEther("100"), 0)
      ).to.be.revertedWith("Home chain not set");
    });

    it("should reject bridgeBack from non-allowlisted user", async function () {
      // First we need to mint tokens to nonKycUser (via admin bypass)
      // Since direct mint isn't available, we'll test transfer restriction instead
      await expect(
        tokenRemote.connect(nonKycUser).bridgeBack(user2.address, ethers.parseEther("100"), 0)
      ).to.be.revertedWith("Not KYC verified");
    });

    it("should reject bridgeBack from frozen user", async function () {
      await tokenRemote.connect(admin).freeze(user1.address);

      await expect(
        tokenRemote.connect(user1).bridgeBack(user2.address, ethers.parseEther("100"), 0)
      ).to.be.revertedWith("Account frozen");
    });

    it("should deduct fee from balance", async function () {
      const amount = ethers.parseEther("1000");
      const feeAmount = ethers.parseEther("10");
      const balanceBefore = await tokenRemote.balanceOf(user1.address);

      await tokenRemote.connect(user1).bridgeBack(user2.address, amount, feeAmount);

      const balanceAfter = await tokenRemote.balanceOf(user1.address);
      // Both amount and fee should be deducted
      expect(balanceBefore - balanceAfter).to.equal(amount + feeAmount);
    });
  });

  describe("Freeze/Unfreeze", function () {
    beforeEach(async function () {
      await tokenRemote.connect(bridgeAdmin).registerHomeChain(HOME_CHAIN_ID, TOKEN_HOME_ADDRESS);

      // Mint tokens to users for testing
      const mintMessage = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256"],
        [admin.address, user1.address, ethers.parseEther("5000")]
      );
      await teleporter.deliverMessage(
        HOME_CHAIN_ID,
        TOKEN_HOME_ADDRESS,
        await tokenRemote.getAddress(),
        mintMessage
      );
    });

    it("should freeze an account", async function () {
      await expect(tokenRemote.connect(admin).freeze(user1.address))
        .to.emit(tokenRemote, "AddressFrozen")
        .withArgs(user1.address);

      expect(await tokenRemote.frozen(user1.address)).to.be.true;
      expect(await tokenRemote.canHold(user1.address)).to.be.false;
    });

    it("should unfreeze an account", async function () {
      await tokenRemote.connect(admin).freeze(user1.address);

      await expect(tokenRemote.connect(admin).unfreeze(user1.address))
        .to.emit(tokenRemote, "AddressUnfrozen")
        .withArgs(user1.address);

      expect(await tokenRemote.frozen(user1.address)).to.be.false;
      expect(await tokenRemote.canHold(user1.address)).to.be.true;
    });

    it("should prevent transfers from frozen account", async function () {
      await tokenRemote.connect(admin).freeze(user1.address);

      await expect(
        tokenRemote.connect(user1).transfer(user2.address, ethers.parseEther("100"))
      ).to.be.revertedWith("Sender frozen");
    });

    it("should prevent transfers to frozen account", async function () {
      await tokenRemote.connect(admin).freeze(user2.address);

      await expect(
        tokenRemote.connect(user1).transfer(user2.address, ethers.parseEther("100"))
      ).to.be.revertedWith("Recipient frozen");
    });
  });

  describe("Transfer Restrictions", function () {
    beforeEach(async function () {
      await tokenRemote.connect(bridgeAdmin).registerHomeChain(HOME_CHAIN_ID, TOKEN_HOME_ADDRESS);

      // Mint tokens to user1
      const mintMessage = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256"],
        [admin.address, user1.address, ethers.parseEther("5000")]
      );
      await teleporter.deliverMessage(
        HOME_CHAIN_ID,
        TOKEN_HOME_ADDRESS,
        await tokenRemote.getAddress(),
        mintMessage
      );
    });

    it("should allow transfer between KYC verified users", async function () {
      const amount = ethers.parseEther("100");
      await tokenRemote.connect(user1).transfer(user2.address, amount);

      expect(await tokenRemote.balanceOf(user2.address)).to.equal(amount);
    });

    it("should reject transfer to non-KYC user", async function () {
      await expect(
        tokenRemote.connect(user1).transfer(nonKycUser.address, ethers.parseEther("100"))
      ).to.be.revertedWith("Recipient not KYC verified");
    });

    it("should reject transfer from non-KYC user", async function () {
      // This test is tricky because non-KYC user can't receive tokens
      // We test by removing user1 from allowlist after they have balance
      await tokenRemote.connect(admin).removeFromAllowlist(user1.address);

      await expect(
        tokenRemote.connect(user1).transfer(user2.address, ethers.parseEther("100"))
      ).to.be.revertedWith("Sender not KYC verified");
    });
  });

  describe("Pause/Unpause", function () {
    beforeEach(async function () {
      await tokenRemote.connect(bridgeAdmin).registerHomeChain(HOME_CHAIN_ID, TOKEN_HOME_ADDRESS);

      const mintMessage = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256"],
        [admin.address, user1.address, ethers.parseEther("5000")]
      );
      await teleporter.deliverMessage(
        HOME_CHAIN_ID,
        TOKEN_HOME_ADDRESS,
        await tokenRemote.getAddress(),
        mintMessage
      );
    });

    it("should pause the bridge", async function () {
      await expect(tokenRemote.connect(admin).pause())
        .to.emit(tokenRemote, "BridgePaused")
        .withArgs(admin.address);

      expect(await tokenRemote.paused()).to.be.true;
    });

    it("should unpause the bridge", async function () {
      await tokenRemote.connect(admin).pause();

      await expect(tokenRemote.connect(admin).unpause())
        .to.emit(tokenRemote, "BridgeUnpaused")
        .withArgs(admin.address);

      expect(await tokenRemote.paused()).to.be.false;
    });

    it("should reject bridgeBack when paused", async function () {
      await tokenRemote.connect(admin).pause();

      await expect(
        tokenRemote.connect(user1).bridgeBack(user2.address, ethers.parseEther("100"), 0)
      ).to.be.revertedWith("Bridge is paused");
    });

    it("should reject incoming messages when paused", async function () {
      await tokenRemote.connect(admin).pause();

      const message = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "address", "uint256"],
        [user1.address, user1.address, ethers.parseEther("1000")]
      );

      await expect(
        teleporter.deliverMessage(
          HOME_CHAIN_ID,
          TOKEN_HOME_ADDRESS,
          await tokenRemote.getAddress(),
          message
        )
      ).to.be.revertedWith("Bridge is paused");
    });
  });

  describe("Set Teleporter", function () {
    it("should update teleporter address", async function () {
      // Use a valid checksum address
      const newTeleporter = ethers.getAddress("0xabcdef0123456789012345678901234567890abc");
      await tokenRemote.connect(admin).setTeleporter(newTeleporter);

      expect(await tokenRemote.teleporterMessenger()).to.equal(newTeleporter);
    });

    it("should reject zero address", async function () {
      await expect(
        tokenRemote.connect(admin).setTeleporter(ethers.ZeroAddress)
      ).to.be.revertedWith("Invalid teleporter");
    });

    it("should reject from non-admin", async function () {
      await expect(
        tokenRemote.connect(user1).setTeleporter("0xabcdef0123456789012345678901234567890abc")
      ).to.be.reverted;
    });
  });
});
