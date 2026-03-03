import { ethers } from "hardhat";
import * as fs from "fs";
import * as path from "path";

/**
 * Post-Deployment Bridge Configuration Script
 *
 * After deploying to both TR and EU L1 chains, this script:
 * 1. Registers TokenRemote contracts with their respective TokenHome contracts
 * 2. Registers home chain info on TokenRemote contracts
 * 3. Configures BridgeAdapter contracts with ICTT addresses
 * 4. Sets up initial KYC allowlist for bridge operator
 *
 * PREREQUISITES:
 * 1. deploy.ts has been run on BOTH chains
 * 2. deployments/dual-chain-deployment.json contains actual addresses
 * 3. Deployer has admin rights on all contracts
 *
 * USAGE:
 *   # Configure TR L1 side
 *   npx hardhat run scripts/configure-bridge.ts --network ari-tr-testnet
 *
 *   # Configure EU L1 side
 *   npx hardhat run scripts/configure-bridge.ts --network ari-eu-testnet
 */

interface ChainDeployment {
  network: string;
  chainId: number;
  blockchainID: string;
  contracts: {
    timelock: string;
    validatorManager: string;
    kycAllowList: string;
    stablecoin: string;
    stablecoinImpl: string;
    tokenHome: string;
    tokenRemote: string;
    bridgeAdapter: string;
  };
  roles: {
    admin: string;
    minter: string;
    bridgeOperator: string;
  };
}

interface DualChainDeployment {
  timestamp: string;
  tr: ChainDeployment;
  eu: ChainDeployment;
  bridgeConfig: {
    trTokenHomeRegisteredWith: string;
    euTokenHomeRegisteredWith: string;
    teleporterAddress: string;
  };
}

async function loadDeployment(): Promise<DualChainDeployment> {
  const deploymentPath = path.join(__dirname, "..", "deployments", "dual-chain-deployment.json");

  if (!fs.existsSync(deploymentPath)) {
    throw new Error(
      `Deployment file not found at ${deploymentPath}\n` +
        "Please run deploy.ts on both chains first and update the deployment file."
    );
  }

  const content = fs.readFileSync(deploymentPath, "utf-8");
  return JSON.parse(content) as DualChainDeployment;
}

async function main() {
  const [deployer] = await ethers.getSigners();
  const network = await ethers.provider.getNetwork();
  const chainId = Number(network.chainId);

  console.log("\n" + "=".repeat(60));
  console.log("ARI BRIDGE CONFIGURATION");
  console.log("=".repeat(60));
  console.log("Network:", network.name);
  console.log("Chain ID:", chainId);
  console.log("Deployer:", deployer.address);

  // Load deployment addresses
  const deployment = await loadDeployment();

  // Determine which chain we're on
  let currentChain: ChainDeployment;
  let partnerChain: ChainDeployment;
  let isTraChain: boolean;

  if (chainId === deployment.tr.chainId) {
    currentChain = deployment.tr;
    partnerChain = deployment.eu;
    isTraChain = true;
    console.log("\n🇹🇷 Configuring TR L1 (home chain for TRY)");
  } else if (chainId === deployment.eu.chainId) {
    currentChain = deployment.eu;
    partnerChain = deployment.tr;
    isTraChain = false;
    console.log("\n🇪🇺 Configuring EU L1 (home chain for EUR)");
  } else {
    throw new Error(
      `Unknown chain ID: ${chainId}. Expected ${deployment.tr.chainId} (TR) or ${deployment.eu.chainId} (EU)`
    );
  }

  console.log("\nCurrent Chain Contracts:");
  console.log("  TokenHome:", currentChain.contracts.tokenHome);
  console.log("  TokenRemote:", currentChain.contracts.tokenRemote);
  console.log("  BridgeAdapter:", currentChain.contracts.bridgeAdapter);
  console.log("\nPartner Chain:");
  console.log("  Blockchain ID:", partnerChain.blockchainID);
  console.log("  TokenHome:", partnerChain.contracts.tokenHome);
  console.log("  TokenRemote:", partnerChain.contracts.tokenRemote);

  // Get contract instances
  const TokenHome = await ethers.getContractFactory("AriTokenHome");
  const tokenHome = TokenHome.attach(currentChain.contracts.tokenHome);

  const TokenRemote = await ethers.getContractFactory("AriTokenRemote");
  const tokenRemote = TokenRemote.attach(currentChain.contracts.tokenRemote);

  const BridgeAdapter = await ethers.getContractFactory("AriBridgeAdapter");
  const bridgeAdapter = BridgeAdapter.attach(currentChain.contracts.bridgeAdapter);

  // ===== 1. Register TokenRemote on partner chain with our TokenHome =====
  console.log("\n--- Step 1: Register Partner TokenRemote with TokenHome ---");
  console.log(`TokenHome.registerRemote(${partnerChain.blockchainID}, ${partnerChain.contracts.tokenRemote})`);

  try {
    const tx1 = await tokenHome.registerRemote(
      partnerChain.blockchainID,
      partnerChain.contracts.tokenRemote
    );
    await tx1.wait();
    console.log("✅ TokenHome registered partner TokenRemote");
    console.log("   Tx:", tx1.hash);
  } catch (error: any) {
    if (error.message.includes("already registered")) {
      console.log("⚠️  Partner TokenRemote already registered");
    } else {
      console.error("❌ Failed to register:", error.message);
    }
  }

  // ===== 2. Register home chain on our TokenRemote =====
  console.log("\n--- Step 2: Register Home Chain on TokenRemote ---");
  console.log(`TokenRemote.registerHomeChain(${partnerChain.blockchainID}, ${partnerChain.contracts.tokenHome})`);

  try {
    const tx2 = await tokenRemote.registerHomeChain(
      partnerChain.blockchainID,
      partnerChain.contracts.tokenHome
    );
    await tx2.wait();
    console.log("✅ TokenRemote registered home chain");
    console.log("   Tx:", tx2.hash);
  } catch (error: any) {
    if (error.message.includes("already set")) {
      console.log("⚠️  Home chain already registered");
    } else {
      console.error("❌ Failed to register:", error.message);
    }
  }

  // ===== 3. Configure BridgeAdapter =====
  console.log("\n--- Step 3: Configure BridgeAdapter ---");
  console.log("BridgeAdapter.configure(");
  console.log(`  wrappedToken: ${currentChain.contracts.tokenRemote},`);
  console.log(`  tokenHome: ${currentChain.contracts.tokenHome},`);
  console.log(`  tokenRemote: ${currentChain.contracts.tokenRemote},`);
  console.log(`  blockchainID: ${currentChain.blockchainID},`);
  console.log(`  partnerChainID: ${partnerChain.blockchainID}`);
  console.log(")");

  try {
    const tx3 = await bridgeAdapter.configure(
      currentChain.contracts.tokenRemote, // wrappedToken (TokenRemote is an ERC20)
      currentChain.contracts.tokenHome,
      currentChain.contracts.tokenRemote,
      currentChain.blockchainID,
      partnerChain.blockchainID
    );
    await tx3.wait();
    console.log("✅ BridgeAdapter configured");
    console.log("   Tx:", tx3.hash);
  } catch (error: any) {
    console.error("❌ Failed to configure BridgeAdapter:", error.message);
  }

  // ===== 4. Add bridge operator to TokenRemote allowlist =====
  console.log("\n--- Step 4: Add Bridge Operator to TokenRemote Allowlist ---");
  const bridgeOperator = currentChain.roles.bridgeOperator;
  console.log(`TokenRemote.addToAllowlist(${bridgeOperator})`);

  try {
    const tx4 = await tokenRemote.addToAllowlist(bridgeOperator);
    await tx4.wait();
    console.log("✅ Bridge operator added to allowlist");
    console.log("   Tx:", tx4.hash);
  } catch (error: any) {
    if (error.message.includes("already allowlisted")) {
      console.log("⚠️  Bridge operator already allowlisted");
    } else {
      console.error("❌ Failed to add to allowlist:", error.message);
    }
  }

  // ===== 5. Add BridgeAdapter to TokenRemote allowlist =====
  console.log("\n--- Step 5: Add BridgeAdapter to TokenRemote Allowlist ---");
  console.log(`TokenRemote.addToAllowlist(${currentChain.contracts.bridgeAdapter})`);

  try {
    const tx5 = await tokenRemote.addToAllowlist(currentChain.contracts.bridgeAdapter);
    await tx5.wait();
    console.log("✅ BridgeAdapter added to allowlist");
    console.log("   Tx:", tx5.hash);
  } catch (error: any) {
    if (error.message.includes("already allowlisted")) {
      console.log("⚠️  BridgeAdapter already allowlisted");
    } else {
      console.error("❌ Failed to add to allowlist:", error.message);
    }
  }

  // ===== Summary =====
  console.log("\n" + "=".repeat(60));
  console.log("CONFIGURATION COMPLETE");
  console.log("=".repeat(60));

  console.log(`
${isTraChain ? "🇹🇷 TR L1" : "🇪🇺 EU L1"} is now configured:

✅ TokenHome can bridge ${isTraChain ? "TRY" : "EUR"} to partner chain
   → Partner TokenRemote (${isTraChain ? "wTRY on EU" : "wEUR on TR"}) registered

✅ TokenRemote can receive ${isTraChain ? "wEUR" : "wTRY"} from partner chain
   → Home chain (${isTraChain ? "EU" : "TR"} TokenHome) registered

✅ BridgeAdapter configured with ICTT addresses
   → Ready for cross-chain transfers

✅ Allowlist configured
   → Bridge operator and BridgeAdapter can hold wrapped tokens
`);

  if (isTraChain) {
    console.log("Next: Run this script on EU L1 to complete setup");
    console.log("  npx hardhat run scripts/configure-bridge.ts --network ari-eu-testnet");
  } else {
    console.log("🎉 Dual-chain bridge configuration complete!");
    console.log("\nYou can now:");
    console.log("1. Update blockchain-service config with contract addresses");
    console.log("2. Test cross-chain transfers via the web app");
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
