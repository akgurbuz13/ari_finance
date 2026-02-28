import { expect } from "chai";
import { ethers } from "hardhat";
import { AriStablecoin } from "../typechain-types";
import { SignerWithAddress } from "@nomicfoundation/hardhat-ethers/signers";

describe("AriStablecoin", function () {
  let stablecoin: AriStablecoin;
  let owner: SignerWithAddress;
  let minter: SignerWithAddress;
  let user1: SignerWithAddress;
  let user2: SignerWithAddress;
  let nonKycUser: SignerWithAddress;

  beforeEach(async function () {
    [owner, minter, user1, user2, nonKycUser] = await ethers.getSigners();

    const AriStablecoin = await ethers.getContractFactory("AriStablecoin");
    stablecoin = await AriStablecoin.deploy("ARI Turkish Lira", "ariTRY");
    await stablecoin.waitForDeployment();

    // Grant minter role
    const MINTER_ROLE = await stablecoin.MINTER_ROLE();
    await stablecoin.grantRole(MINTER_ROLE, minter.address);

    // Allowlist users
    await stablecoin.addToAllowlist(user1.address);
    await stablecoin.addToAllowlist(user2.address);
  });

  describe("Minting", function () {
    it("should mint tokens to allowlisted address", async function () {
      const amount = ethers.parseEther("1000");
      await stablecoin.connect(minter).mint(user1.address, amount);
      expect(await stablecoin.balanceOf(user1.address)).to.equal(amount);
    });

    it("should reject mint to non-allowlisted address", async function () {
      const amount = ethers.parseEther("1000");
      await expect(
        stablecoin.connect(minter).mint(nonKycUser.address, amount)
      ).to.be.revertedWith("AriStablecoin: recipient not KYC verified");
    });

    it("should reject mint from non-minter", async function () {
      const amount = ethers.parseEther("1000");
      await expect(
        stablecoin.connect(user1).mint(user1.address, amount)
      ).to.be.reverted;
    });
  });

  describe("Burning", function () {
    it("should burn tokens from allowlisted address", async function () {
      const amount = ethers.parseEther("1000");
      await stablecoin.connect(minter).mint(user1.address, amount);

      const burnAmount = ethers.parseEther("500");
      await stablecoin.connect(minter).burn(user1.address, burnAmount);
      expect(await stablecoin.balanceOf(user1.address)).to.equal(
        ethers.parseEther("500")
      );
    });
  });

  describe("Transfers", function () {
    it("should allow transfer between allowlisted addresses", async function () {
      const amount = ethers.parseEther("1000");
      await stablecoin.connect(minter).mint(user1.address, amount);

      const transferAmount = ethers.parseEther("300");
      await stablecoin.connect(user1).transfer(user2.address, transferAmount);
      expect(await stablecoin.balanceOf(user2.address)).to.equal(transferAmount);
      expect(await stablecoin.balanceOf(user1.address)).to.equal(
        ethers.parseEther("700")
      );
    });

    it("should reject transfer to non-allowlisted address", async function () {
      const amount = ethers.parseEther("1000");
      await stablecoin.connect(minter).mint(user1.address, amount);

      await expect(
        stablecoin.connect(user1).transfer(nonKycUser.address, amount)
      ).to.be.revertedWith("AriStablecoin: recipient not KYC verified");
    });

    it("should reject transfer from non-allowlisted address", async function () {
      // Remove user1 from allowlist after minting
      const amount = ethers.parseEther("1000");
      await stablecoin.connect(minter).mint(user1.address, amount);
      await stablecoin.removeFromAllowlist(user1.address);

      await expect(
        stablecoin.connect(user1).transfer(user2.address, amount)
      ).to.be.revertedWith("AriStablecoin: sender not KYC verified");
    });
  });

  describe("Freezing", function () {
    it("should freeze an account", async function () {
      const amount = ethers.parseEther("1000");
      await stablecoin.connect(minter).mint(user1.address, amount);

      await stablecoin.freeze(user1.address);
      expect(await stablecoin.frozen(user1.address)).to.be.true;

      await expect(
        stablecoin.connect(user1).transfer(user2.address, amount)
      ).to.be.revertedWith("AriStablecoin: sender account frozen");
    });

    it("should unfreeze an account", async function () {
      const amount = ethers.parseEther("1000");
      await stablecoin.connect(minter).mint(user1.address, amount);

      await stablecoin.freeze(user1.address);
      await stablecoin.unfreeze(user1.address);
      expect(await stablecoin.frozen(user1.address)).to.be.false;

      await stablecoin.connect(user1).transfer(user2.address, amount);
      expect(await stablecoin.balanceOf(user2.address)).to.equal(amount);
    });

    it("should reject mint to frozen address", async function () {
      await stablecoin.freeze(user1.address);

      const amount = ethers.parseEther("1000");
      await expect(
        stablecoin.connect(minter).mint(user1.address, amount)
      ).to.be.revertedWith("AriStablecoin: recipient account frozen");
    });
  });

  describe("Pausable", function () {
    it("should pause all transfers", async function () {
      const amount = ethers.parseEther("1000");
      await stablecoin.connect(minter).mint(user1.address, amount);

      await stablecoin.pause();

      await expect(
        stablecoin.connect(user1).transfer(user2.address, amount)
      ).to.be.reverted;
    });

    it("should resume after unpause", async function () {
      const amount = ethers.parseEther("1000");
      await stablecoin.connect(minter).mint(user1.address, amount);

      await stablecoin.pause();
      await stablecoin.unpause();

      await stablecoin.connect(user1).transfer(user2.address, amount);
      expect(await stablecoin.balanceOf(user2.address)).to.equal(amount);
    });
  });

  describe("Batch operations", function () {
    it("should batch add to allowlist", async function () {
      const addresses = [nonKycUser.address];
      await stablecoin.batchAddToAllowlist(addresses);
      expect(await stablecoin.allowlisted(nonKycUser.address)).to.be.true;
    });
  });
});
