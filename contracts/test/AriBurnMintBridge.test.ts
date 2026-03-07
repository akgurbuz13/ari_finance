import { expect } from "chai";
import { ethers } from "hardhat";
import { SignerWithAddress } from "@nomicfoundation/hardhat-ethers/signers";

describe("AriBurnMintBridge", function () {
  let admin: SignerWithAddress;
  let operator: SignerWithAddress;
  let user: SignerWithAddress;
  let unauthorized: SignerWithAddress;

  let stablecoin: any;
  let teleporter: any;
  let bridge: any;

  const TR_CHAIN_ID = ethers.encodeBytes32String("TR_L1");
  const EU_CHAIN_ID = ethers.encodeBytes32String("EU_L1");
  const MINTER_ROLE = ethers.keccak256(ethers.toUtf8Bytes("MINTER_ROLE"));
  const BRIDGE_OPERATOR_ROLE = ethers.keccak256(ethers.toUtf8Bytes("BRIDGE_OPERATOR_ROLE"));
  const AMOUNT = ethers.parseEther("1000");

  beforeEach(async function () {
    [admin, operator, user, unauthorized] = await ethers.getSigners();

    // Deploy AriStablecoin
    const AriStablecoin = await ethers.getContractFactory("AriStablecoin");
    stablecoin = await AriStablecoin.deploy("ARI Turkish Lira", "ariTRY");
    await stablecoin.waitForDeployment();

    // Deploy MockTeleporter
    const MockTeleporter = await ethers.getContractFactory("MockTeleporter");
    teleporter = await MockTeleporter.deploy();
    await teleporter.waitForDeployment();

    // Deploy AriBurnMintBridge
    const AriBurnMintBridge = await ethers.getContractFactory("AriBurnMintBridge");
    bridge = await AriBurnMintBridge.deploy(
      await teleporter.getAddress(),
      await stablecoin.getAddress()
    );
    await bridge.waitForDeployment();

    // Grant bridge MINTER_ROLE and DEFAULT_ADMIN_ROLE on stablecoin
    // (MINTER_ROLE for mint/burn, DEFAULT_ADMIN_ROLE for addToAllowlist)
    await stablecoin.grantRole(MINTER_ROLE, await bridge.getAddress());
    await stablecoin.grantRole(ethers.ZeroHash, await bridge.getAddress());

    // Grant operator BRIDGE_OPERATOR_ROLE on bridge
    await bridge.grantRole(BRIDGE_OPERATOR_ROLE, operator.address);

    // Grant operator MINTER_ROLE on stablecoin (to hold tokens that get burned)
    await stablecoin.grantRole(MINTER_ROLE, operator.address);

    // Allowlist participants
    await stablecoin.addToAllowlist(operator.address);
    await stablecoin.addToAllowlist(user.address);
    await stablecoin.addToAllowlist(await bridge.getAddress());

    // Mint tokens to operator for burn tests
    await stablecoin.mint(operator.address, AMOUNT);
  });

  describe("Deployment", function () {
    it("Should set teleporter and stablecoin addresses", async function () {
      expect(await bridge.teleporterMessenger()).to.equal(await teleporter.getAddress());
      expect(await bridge.stablecoin()).to.equal(await stablecoin.getAddress());
    });

    it("Should grant admin role to deployer", async function () {
      const DEFAULT_ADMIN_ROLE = ethers.ZeroHash;
      expect(await bridge.hasRole(DEFAULT_ADMIN_ROLE, admin.address)).to.be.true;
    });

    it("Should revert with zero teleporter address", async function () {
      const AriBurnMintBridge = await ethers.getContractFactory("AriBurnMintBridge");
      await expect(
        AriBurnMintBridge.deploy(ethers.ZeroAddress, await stablecoin.getAddress())
      ).to.be.revertedWith("Invalid teleporter");
    });

    it("Should revert with zero stablecoin address", async function () {
      const AriBurnMintBridge = await ethers.getContractFactory("AriBurnMintBridge");
      await expect(
        AriBurnMintBridge.deploy(await teleporter.getAddress(), ethers.ZeroAddress)
      ).to.be.revertedWith("Invalid stablecoin");
    });
  });

  describe("Partner Registration", function () {
    it("Should register a partner bridge", async function () {
      const partnerAddr = ethers.Wallet.createRandom().address;
      await expect(bridge.registerPartner(EU_CHAIN_ID, partnerAddr))
        .to.emit(bridge, "PartnerRegistered")
        .withArgs(EU_CHAIN_ID, partnerAddr);

      expect(await bridge.registeredPartners(EU_CHAIN_ID)).to.equal(partnerAddr);
    });

    it("Should reject zero address partner", async function () {
      await expect(
        bridge.registerPartner(EU_CHAIN_ID, ethers.ZeroAddress)
      ).to.be.revertedWith("Invalid bridge address");
    });

    it("Should reject non-admin partner registration", async function () {
      const partnerAddr = ethers.Wallet.createRandom().address;
      await expect(
        bridge.connect(unauthorized).registerPartner(EU_CHAIN_ID, partnerAddr)
      ).to.be.reverted;
    });
  });

  describe("burnAndBridge", function () {
    let partnerBridgeAddr: string;

    beforeEach(async function () {
      // Register a partner bridge on EU chain
      partnerBridgeAddr = ethers.Wallet.createRandom().address;
      await bridge.registerPartner(EU_CHAIN_ID, partnerBridgeAddr);
    });

    it("Should burn tokens and send Teleporter message", async function () {
      const bridgeAmount = ethers.parseEther("500");
      const operatorBalanceBefore = await stablecoin.balanceOf(operator.address);

      const tx = await bridge.connect(operator).burnAndBridge(
        EU_CHAIN_ID,
        user.address,
        bridgeAmount
      );

      // Verify burn
      const operatorBalanceAfter = await stablecoin.balanceOf(operator.address);
      expect(operatorBalanceBefore - operatorBalanceAfter).to.equal(bridgeAmount);

      // Verify Teleporter message sent
      expect(await teleporter.getMessageCount()).to.equal(1);

      // Verify event
      await expect(tx).to.emit(bridge, "BurnAndBridge");
    });

    it("Should reject unregistered destination", async function () {
      const unknownChain = ethers.encodeBytes32String("UNKNOWN");
      await expect(
        bridge.connect(operator).burnAndBridge(unknownChain, user.address, AMOUNT)
      ).to.be.revertedWith("Dest not registered");
    });

    it("Should reject zero recipient", async function () {
      await expect(
        bridge.connect(operator).burnAndBridge(EU_CHAIN_ID, ethers.ZeroAddress, AMOUNT)
      ).to.be.revertedWith("Invalid recipient");
    });

    it("Should reject zero amount", async function () {
      await expect(
        bridge.connect(operator).burnAndBridge(EU_CHAIN_ID, user.address, 0)
      ).to.be.revertedWith("Amount must be > 0");
    });

    it("Should reject unauthorized caller", async function () {
      await expect(
        bridge.connect(unauthorized).burnAndBridge(EU_CHAIN_ID, user.address, AMOUNT)
      ).to.be.reverted;
    });
  });

  describe("receiveTeleporterMessage (mint on receive)", function () {
    let partnerBridgeAddr: string;

    beforeEach(async function () {
      partnerBridgeAddr = ethers.Wallet.createRandom().address;
      await bridge.registerPartner(TR_CHAIN_ID, partnerBridgeAddr);
    });

    it("Should mint tokens when receiving valid Teleporter message", async function () {
      const mintAmount = ethers.parseEther("750");
      const payload = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "uint256"],
        [user.address, mintAmount]
      );

      const userBalanceBefore = await stablecoin.balanceOf(user.address);

      // Deliver message via MockTeleporter
      await teleporter.deliverMessage(
        TR_CHAIN_ID,
        partnerBridgeAddr,
        await bridge.getAddress(),
        payload
      );

      const userBalanceAfter = await stablecoin.balanceOf(user.address);
      expect(userBalanceAfter - userBalanceBefore).to.equal(mintAmount);
    });

    it("Should auto-allowlist non-allowlisted recipient", async function () {
      const newRecipient = ethers.Wallet.createRandom().address;
      const mintAmount = ethers.parseEther("100");
      const payload = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "uint256"],
        [newRecipient, mintAmount]
      );

      expect(await stablecoin.allowlisted(newRecipient)).to.be.false;

      await teleporter.deliverMessage(
        TR_CHAIN_ID,
        partnerBridgeAddr,
        await bridge.getAddress(),
        payload
      );

      expect(await stablecoin.allowlisted(newRecipient)).to.be.true;
      expect(await stablecoin.balanceOf(newRecipient)).to.equal(mintAmount);
    });

    it("Should reject message from non-Teleporter sender", async function () {
      const payload = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "uint256"],
        [user.address, AMOUNT]
      );

      await expect(
        bridge.receiveTeleporterMessage(TR_CHAIN_ID, partnerBridgeAddr, payload)
      ).to.be.revertedWith("Only Teleporter");
    });

    it("Should reject message from unknown partner", async function () {
      const unknownPartner = ethers.Wallet.createRandom().address;
      const payload = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "uint256"],
        [user.address, AMOUNT]
      );

      await expect(
        teleporter.deliverMessage(
          TR_CHAIN_ID,
          unknownPartner,
          await bridge.getAddress(),
          payload
        )
      ).to.be.revertedWith("Unknown partner");
    });

    it("Should reject replay of same message", async function () {
      const mintAmount = ethers.parseEther("100");
      const payload = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "uint256"],
        [user.address, mintAmount]
      );

      // First delivery succeeds
      await teleporter.deliverMessage(
        TR_CHAIN_ID,
        partnerBridgeAddr,
        await bridge.getAddress(),
        payload
      );

      // Second delivery with same data should fail
      await expect(
        teleporter.deliverMessage(
          TR_CHAIN_ID,
          partnerBridgeAddr,
          await bridge.getAddress(),
          payload
        )
      ).to.be.revertedWith("Already processed");
    });

    it("Should reject zero recipient in message", async function () {
      const payload = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "uint256"],
        [ethers.ZeroAddress, AMOUNT]
      );

      await expect(
        teleporter.deliverMessage(
          TR_CHAIN_ID,
          partnerBridgeAddr,
          await bridge.getAddress(),
          payload
        )
      ).to.be.revertedWith("Invalid recipient in message");
    });

    it("Should reject zero amount in message", async function () {
      const payload = ethers.AbiCoder.defaultAbiCoder().encode(
        ["address", "uint256"],
        [user.address, 0]
      );

      await expect(
        teleporter.deliverMessage(
          TR_CHAIN_ID,
          partnerBridgeAddr,
          await bridge.getAddress(),
          payload
        )
      ).to.be.revertedWith("Invalid amount in message");
    });
  });

  describe("End-to-end: burn on source, deliver, mint on dest", function () {
    it("Should complete full bridge cycle with separate bridge instances", async function () {
      // Deploy a second stablecoin + bridge (simulating destination chain)
      const AriStablecoin = await ethers.getContractFactory("AriStablecoin");
      const destStablecoin = await AriStablecoin.deploy("ARI Turkish Lira", "ariTRY");
      await destStablecoin.waitForDeployment();

      const AriBurnMintBridge = await ethers.getContractFactory("AriBurnMintBridge");
      const destBridge = await AriBurnMintBridge.deploy(
        await teleporter.getAddress(),
        await destStablecoin.getAddress()
      );
      await destBridge.waitForDeployment();

      // Grant dest bridge MINTER_ROLE and DEFAULT_ADMIN_ROLE on dest stablecoin
      await destStablecoin.grantRole(MINTER_ROLE, await destBridge.getAddress());
      await destStablecoin.grantRole(ethers.ZeroHash, await destBridge.getAddress());
      await destStablecoin.addToAllowlist(await destBridge.getAddress());

      // Cross-register partners
      await bridge.registerPartner(EU_CHAIN_ID, await destBridge.getAddress());
      await destBridge.registerPartner(TR_CHAIN_ID, await bridge.getAddress());

      // Step 1: Burn on source chain
      const bridgeAmount = ethers.parseEther("500");
      await bridge.connect(operator).burnAndBridge(
        EU_CHAIN_ID,
        user.address,
        bridgeAmount
      );

      // Verify tokens burned
      expect(await stablecoin.balanceOf(operator.address)).to.equal(AMOUNT - bridgeAmount);

      // Step 2: Deliver Teleporter message to dest bridge
      const messageId = await teleporter.getMessageIdAt(0);
      const msgPayload = await teleporter.getMessagePayload(messageId);

      await teleporter.deliverMessage(
        TR_CHAIN_ID,
        await bridge.getAddress(),
        await destBridge.getAddress(),
        msgPayload
      );

      // Step 3: Verify tokens minted on dest chain
      expect(await destStablecoin.balanceOf(user.address)).to.equal(bridgeAmount);
    });
  });
});
