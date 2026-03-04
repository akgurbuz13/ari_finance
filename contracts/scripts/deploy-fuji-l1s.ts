import { ethers, upgrades } from "hardhat";
import * as fs from "fs";
import * as path from "path";

/**
 * Simplified deployment for Builder Console managed Fuji L1 nodes.
 *
 * Deploys all contracts to the CURRENT network (--network flag).
 * Run once per chain:
 *
 *   # TR L1
 *   DEPLOYER_PRIVATE_KEY=0x... \
 *   TELEPORTER_ADDRESS=0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf \
 *   BLOCKCHAIN_ID=<from-builder-console> \
 *   npx hardhat run scripts/deploy-fuji-l1s.ts --network ari-tr-testnet
 *
 *   # EU L1
 *   DEPLOYER_PRIVATE_KEY=0x... \
 *   TELEPORTER_ADDRESS=0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf \
 *   BLOCKCHAIN_ID=<from-builder-console> \
 *   npx hardhat run scripts/deploy-fuji-l1s.ts --network ari-eu-testnet
 *
 * Outputs addresses JSON to contracts/deployments/<network>.json
 */

async function main() {
  const [deployer] = await ethers.getSigners();
  const networkInfo = await ethers.provider.getNetwork();
  const networkName = (await import("hardhat")).default.network.name;
  const balance = await deployer.provider.getBalance(deployer.address);

  console.log(`\nDeploying to: ${networkName} (chainId: ${networkInfo.chainId})`);
  console.log(`Deployer: ${deployer.address}`);
  console.log(`Balance: ${ethers.formatEther(balance)} AVAX\n`);

  if (balance === 0n) {
    throw new Error("Deployer has no balance. Fund via Builder Console faucet.");
  }

  // Determine chain type from network name
  const isTR = networkName.includes("tr");
  const tokenName = isTR ? "ARI Turkish Lira" : "ARI Euro";
  const tokenSymbol = isTR ? "ariTRY" : "ariEUR";
  const wrappedTokenName = isTR ? "Wrapped ariEUR" : "Wrapped ariTRY";
  const wrappedTokenSymbol = isTR ? "wEUR" : "wTRY";

  const teleporterAddress = process.env.TELEPORTER_ADDRESS || "0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf";
  const blockchainID = process.env.BLOCKCHAIN_ID || ethers.ZeroHash;

  console.log(`Token: ${tokenName} (${tokenSymbol})`);
  console.log(`Wrapped: ${wrappedTokenName} (${wrappedTokenSymbol})`);
  console.log(`Teleporter: ${teleporterAddress}`);
  console.log(`Blockchain ID: ${blockchainID}\n`);

  // 1. Timelock (1h delay for testnet)
  console.log("1/7 Deploying AriTimelock...");
  const AriTimelock = await ethers.getContractFactory("AriTimelock");
  const timelock = await AriTimelock.deploy(
    3600, // 1 hour for testnet
    [deployer.address], // proposer
    [ethers.ZeroAddress], // anyone can execute
    ethers.ZeroAddress // no admin
  );
  await timelock.waitForDeployment();
  console.log(`   AriTimelock: ${await timelock.getAddress()}`);

  // 2. ValidatorManager
  console.log("2/7 Deploying ValidatorManager...");
  const ValidatorManager = await ethers.getContractFactory("ValidatorManager");
  const validatorManager = await ValidatorManager.deploy(await timelock.getAddress());
  await validatorManager.waitForDeployment();
  console.log(`   ValidatorManager: ${await validatorManager.getAddress()}`);

  // 3. KycAllowList
  console.log("3/7 Deploying KycAllowList...");
  const KycAllowList = await ethers.getContractFactory("KycAllowList");
  const kycAllowList = await KycAllowList.deploy();
  await kycAllowList.waitForDeployment();
  console.log(`   KycAllowList: ${await kycAllowList.getAddress()}`);

  // 4. Stablecoin (UUPS Proxy)
  console.log("4/7 Deploying Stablecoin (UUPS Proxy)...");
  const AriStablecoin = await ethers.getContractFactory("AriStablecoinUpgradeable");
  const stablecoin = await upgrades.deployProxy(
    AriStablecoin,
    [tokenName, tokenSymbol, deployer.address, deployer.address, BigInt(0)],
    { kind: "uups", initializer: "initialize" }
  );
  await stablecoin.waitForDeployment();
  const stablecoinAddress = await stablecoin.getAddress();
  const implAddress = await upgrades.erc1967.getImplementationAddress(stablecoinAddress);
  console.log(`   Stablecoin proxy: ${stablecoinAddress}`);
  console.log(`   Stablecoin impl:  ${implAddress}`);

  // 5. TokenHome
  console.log("5/7 Deploying AriTokenHome...");
  const AriTokenHome = await ethers.getContractFactory("AriTokenHome");
  const tokenHome = await AriTokenHome.deploy(
    stablecoinAddress,
    teleporterAddress,
    blockchainID,
    deployer.address
  );
  await tokenHome.waitForDeployment();
  console.log(`   AriTokenHome: ${await tokenHome.getAddress()}`);

  // 6. TokenRemote
  console.log("6/7 Deploying AriTokenRemote...");
  const AriTokenRemote = await ethers.getContractFactory("AriTokenRemote");
  const tokenRemote = await AriTokenRemote.deploy(
    wrappedTokenName,
    wrappedTokenSymbol,
    teleporterAddress,
    blockchainID,
    deployer.address
  );
  await tokenRemote.waitForDeployment();
  console.log(`   AriTokenRemote: ${await tokenRemote.getAddress()}`);

  // 7. BridgeAdapter
  console.log("7/7 Deploying AriBridgeAdapter...");
  const AriBridgeAdapter = await ethers.getContractFactory("AriBridgeAdapter");
  const bridgeAdapter = await AriBridgeAdapter.deploy(stablecoinAddress, deployer.address);
  await bridgeAdapter.waitForDeployment();
  const bridgeAdapterAddress = await bridgeAdapter.getAddress();
  console.log(`   AriBridgeAdapter: ${bridgeAdapterAddress}`);

  // Configure bridge operator role
  const BRIDGE_OPERATOR_ROLE = await bridgeAdapter.BRIDGE_OPERATOR_ROLE();
  await bridgeAdapter.grantRole(BRIDGE_OPERATOR_ROLE, deployer.address);
  console.log("\n   Granted BRIDGE_OPERATOR_ROLE to deployer");

  // Add deployer to stablecoin allowlist for testing
  const stablecoinContract = await ethers.getContractAt("AriStablecoinUpgradeable", stablecoinAddress);
  try {
    await stablecoinContract.addToAllowlist(deployer.address);
    console.log("   Added deployer to stablecoin allowlist");
  } catch {
    console.log("   Allowlist add skipped (may already be added)");
  }

  // Save deployment
  const deployment = {
    network: networkName,
    chainId: Number(networkInfo.chainId),
    blockchainID,
    deployer: deployer.address,
    timestamp: new Date().toISOString(),
    contracts: {
      timelock: await timelock.getAddress(),
      validatorManager: await validatorManager.getAddress(),
      kycAllowList: await kycAllowList.getAddress(),
      stablecoin: stablecoinAddress,
      stablecoinImpl: implAddress,
      tokenHome: await tokenHome.getAddress(),
      tokenRemote: await tokenRemote.getAddress(),
      bridgeAdapter: bridgeAdapterAddress,
    },
  };

  const deploymentsDir = path.join(__dirname, "..", "deployments");
  if (!fs.existsSync(deploymentsDir)) {
    fs.mkdirSync(deploymentsDir, { recursive: true });
  }
  const outPath = path.join(deploymentsDir, `${networkName}.json`);
  fs.writeFileSync(outPath, JSON.stringify(deployment, null, 2));

  console.log(`\n${"=".repeat(50)}`);
  console.log("DEPLOYMENT COMPLETE");
  console.log(`${"=".repeat(50)}`);
  console.log(`Saved to: ${outPath}`);
  console.log("\nEnvironment variables for blockchain-service:");
  console.log(`  ${isTR ? "TR" : "EU"}_STABLECOIN_ADDRESS=${stablecoinAddress}`);
  console.log(`  ${isTR ? "TR" : "EU"}_TOKEN_HOME_ADDRESS=${await tokenHome.getAddress()}`);
  console.log(`  ${isTR ? "TR" : "EU"}_TOKEN_REMOTE_ADDRESS=${await tokenRemote.getAddress()}`);
  console.log(`  ${isTR ? "TR" : "EU"}_BRIDGE_ADAPTER_ADDRESS=${bridgeAdapterAddress}`);
  console.log(`  ${isTR ? "TR" : "EU"}_BLOCKCHAIN_ID=${blockchainID}`);
  console.log(`\nNext: Deploy to the other chain, then run configure-bridge.ts`);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
