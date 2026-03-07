import { ethers } from "hardhat";

/**
 * Deployment script for AriBurnMintBridge on both TR and EU L1 chains.
 *
 * This script deploys:
 * 1. ariTRY natively on EU L1 (new deployment — ariTRY already exists on TR L1)
 * 2. ariEUR natively on TR L1 (new deployment — ariEUR already exists on EU L1)
 * 3. AriBurnMintBridge on TR L1
 * 4. AriBurnMintBridge on EU L1
 * 5. Cross-registers both bridges as partners
 * 6. Grants MINTER_ROLE + DEFAULT_ADMIN_ROLE to bridges on stablecoins
 *
 * USAGE:
 *   # Deploy on TR L1:
 *   npx hardhat run scripts/deploy-burn-mint-bridge.ts --network ari-tr-testnet
 *
 *   # Deploy on EU L1:
 *   npx hardhat run scripts/deploy-burn-mint-bridge.ts --network ari-eu-testnet
 *
 * ENVIRONMENT VARIABLES:
 *   TELEPORTER_ADDRESS=0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf
 *   STABLECOIN_ADDRESS=0x...           # Existing stablecoin on this chain
 *   NEW_STABLECOIN_NAME="ARI Turkish Lira"  # Name for new cross-currency stablecoin
 *   NEW_STABLECOIN_SYMBOL="ariTRY"          # Symbol for new cross-currency stablecoin
 *   BRIDGE_OPERATOR_ADDRESS=0x...      # Backend bridge operator key
 *   PARTNER_CHAIN_BLOCKCHAIN_ID=0x...  # Avalanche blockchain ID of partner chain
 *   PARTNER_BRIDGE_ADDRESS=0x...       # Bridge address on partner chain (for registration)
 */
async function main() {
  const [deployer] = await ethers.getSigners();
  console.log("Deploying AriBurnMintBridge with account:", deployer.address);
  console.log("Balance:", ethers.formatEther(await deployer.provider.getBalance(deployer.address)), "AVAX");

  const teleporterAddress = process.env.TELEPORTER_ADDRESS || "0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf";
  const existingStablecoinAddress = process.env.STABLECOIN_ADDRESS;
  const bridgeOperator = process.env.BRIDGE_OPERATOR_ADDRESS || deployer.address;
  const partnerBlockchainId = process.env.PARTNER_CHAIN_BLOCKCHAIN_ID;
  const partnerBridgeAddress = process.env.PARTNER_BRIDGE_ADDRESS;

  const MINTER_ROLE = ethers.keccak256(ethers.toUtf8Bytes("MINTER_ROLE"));
  const BRIDGE_OPERATOR_ROLE = ethers.keccak256(ethers.toUtf8Bytes("BRIDGE_OPERATOR_ROLE"));
  const DEFAULT_ADMIN_ROLE = ethers.ZeroHash;

  // Step 1: Deploy new cross-currency stablecoin (if needed)
  const newStablecoinName = process.env.NEW_STABLECOIN_NAME;
  const newStablecoinSymbol = process.env.NEW_STABLECOIN_SYMBOL;
  let newStablecoinAddress: string | undefined;

  if (newStablecoinName && newStablecoinSymbol) {
    console.log(`\n--- Deploying new stablecoin: ${newStablecoinName} (${newStablecoinSymbol}) ---`);
    const AriStablecoin = await ethers.getContractFactory("AriStablecoin");
    const newStablecoin = await AriStablecoin.deploy(newStablecoinName, newStablecoinSymbol);
    await newStablecoin.waitForDeployment();
    newStablecoinAddress = await newStablecoin.getAddress();
    console.log(`  New stablecoin deployed at: ${newStablecoinAddress}`);
  }

  // Step 2: Deploy AriBurnMintBridge for existing stablecoin
  if (existingStablecoinAddress) {
    console.log(`\n--- Deploying AriBurnMintBridge for existing stablecoin ${existingStablecoinAddress} ---`);
    const AriBurnMintBridge = await ethers.getContractFactory("AriBurnMintBridge");
    const bridge = await AriBurnMintBridge.deploy(teleporterAddress, existingStablecoinAddress);
    await bridge.waitForDeployment();
    const bridgeAddress = await bridge.getAddress();
    console.log(`  Bridge deployed at: ${bridgeAddress}`);

    // Grant bridge roles on stablecoin
    const stablecoin = await ethers.getContractAt("AriStablecoin", existingStablecoinAddress);
    console.log("  Granting MINTER_ROLE to bridge...");
    await (await stablecoin.grantRole(MINTER_ROLE, bridgeAddress)).wait();
    console.log("  Granting DEFAULT_ADMIN_ROLE to bridge (for allowlist)...");
    await (await stablecoin.grantRole(DEFAULT_ADMIN_ROLE, bridgeAddress)).wait();

    // Grant BRIDGE_OPERATOR_ROLE to backend operator
    console.log(`  Granting BRIDGE_OPERATOR_ROLE to ${bridgeOperator}...`);
    await (await bridge.grantRole(BRIDGE_OPERATOR_ROLE, bridgeOperator)).wait();

    // Register partner if provided
    if (partnerBlockchainId && partnerBridgeAddress) {
      console.log(`  Registering partner: chain=${partnerBlockchainId}, bridge=${partnerBridgeAddress}`);
      await (await bridge.registerPartner(partnerBlockchainId, partnerBridgeAddress)).wait();
    }

    console.log(`  Bridge for existing stablecoin READY`);
  }

  // Step 3: Deploy AriBurnMintBridge for new cross-currency stablecoin
  if (newStablecoinAddress) {
    console.log(`\n--- Deploying AriBurnMintBridge for new stablecoin ${newStablecoinAddress} ---`);
    const AriBurnMintBridge = await ethers.getContractFactory("AriBurnMintBridge");
    const bridge = await AriBurnMintBridge.deploy(teleporterAddress, newStablecoinAddress);
    await bridge.waitForDeployment();
    const bridgeAddress = await bridge.getAddress();
    console.log(`  Bridge deployed at: ${bridgeAddress}`);

    const newStablecoin = await ethers.getContractAt("AriStablecoin", newStablecoinAddress);
    console.log("  Granting MINTER_ROLE to bridge...");
    await (await newStablecoin.grantRole(MINTER_ROLE, bridgeAddress)).wait();
    console.log("  Granting DEFAULT_ADMIN_ROLE to bridge (for allowlist)...");
    await (await newStablecoin.grantRole(DEFAULT_ADMIN_ROLE, bridgeAddress)).wait();

    console.log(`  Granting BRIDGE_OPERATOR_ROLE to ${bridgeOperator}...`);
    await (await bridge.grantRole(BRIDGE_OPERATOR_ROLE, bridgeOperator)).wait();

    if (partnerBlockchainId && partnerBridgeAddress) {
      console.log(`  Registering partner: chain=${partnerBlockchainId}, bridge=${partnerBridgeAddress}`);
      await (await bridge.registerPartner(partnerBlockchainId, partnerBridgeAddress)).wait();
    }

    console.log(`  Bridge for new stablecoin READY`);
  }

  console.log("\n=== Deployment Summary ===");
  if (newStablecoinAddress) {
    console.log(`New stablecoin (${newStablecoinSymbol}): ${newStablecoinAddress}`);
  }
  console.log("Bridge operator:", bridgeOperator);
  console.log("\nDone! Remember to cross-register partner bridges if deploying on both chains.");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
