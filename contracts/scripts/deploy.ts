import { ethers } from "hardhat";

async function main() {
  const [deployer] = await ethers.getSigners();
  console.log("Deploying contracts with account:", deployer.address);
  console.log("Account balance:", (await deployer.provider.getBalance(deployer.address)).toString());

  // Deploy KYC AllowList
  const KycAllowList = await ethers.getContractFactory("KycAllowList");
  const kycAllowList = await KycAllowList.deploy();
  await kycAllowList.waitForDeployment();
  console.log("KycAllowList deployed to:", await kycAllowList.getAddress());

  // Deploy Stablecoin
  const tokenName = process.env.TOKEN_NAME || "Ova Turkish Lira";
  const tokenSymbol = process.env.TOKEN_SYMBOL || "ovaTRY";

  const OvaStablecoin = await ethers.getContractFactory("OvaStablecoin");
  const stablecoin = await OvaStablecoin.deploy(tokenName, tokenSymbol);
  await stablecoin.waitForDeployment();
  console.log("OvaStablecoin deployed to:", await stablecoin.getAddress());

  // Deploy Bridge Adapter (with placeholder bridge address)
  const bridgeAddress = process.env.ICTT_BRIDGE_ADDRESS || ethers.ZeroAddress;
  const OvaBridgeAdapter = await ethers.getContractFactory("OvaBridgeAdapter");
  const bridgeAdapter = await OvaBridgeAdapter.deploy(
    await stablecoin.getAddress(),
    bridgeAddress
  );
  await bridgeAdapter.waitForDeployment();
  console.log("OvaBridgeAdapter deployed to:", await bridgeAdapter.getAddress());

  // Grant minter role to bridge adapter
  const MINTER_ROLE = await stablecoin.MINTER_ROLE();
  await stablecoin.grantRole(MINTER_ROLE, await bridgeAdapter.getAddress());
  console.log("Granted MINTER_ROLE to bridge adapter");

  console.log("\n--- Deployment Summary ---");
  console.log("KycAllowList:", await kycAllowList.getAddress());
  console.log("OvaStablecoin:", await stablecoin.getAddress());
  console.log("OvaBridgeAdapter:", await bridgeAdapter.getAddress());
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
