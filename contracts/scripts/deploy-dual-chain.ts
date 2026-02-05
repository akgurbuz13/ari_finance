import { ethers, network } from "hardhat";
import * as fs from "fs";
import * as path from "path";

/**
 * Dual-Chain Deployment Orchestrator for Ova Platform
 *
 * This script orchestrates deployment to BOTH TR and EU L1 chains,
 * ensuring proper cross-chain configuration for ICTT bridge.
 *
 * DEPLOYMENT FLOW:
 * 1. Deploy to TR L1: ovaTRY stablecoin + TokenHome + TokenRemote(wEUR)
 * 2. Deploy to EU L1: ovaEUR stablecoin + TokenHome + TokenRemote(wTRY)
 * 3. Cross-register TokenHome ↔ TokenRemote between chains
 * 4. Configure BridgeAdapters with all contract addresses
 *
 * USAGE:
 *   # Set up environment variables first
 *   export DEPLOYER_PRIVATE_KEY=0x...
 *   export TELEPORTER_ADDRESS=0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf
 *
 *   # Run the orchestrator
 *   npx hardhat run scripts/deploy-dual-chain.ts
 *
 * OUTPUT:
 *   Creates deployments/dual-chain-deployment.json with all addresses
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

// Chain configuration
const TR_CONFIG = {
  networkName: "ova-tr-testnet",
  tokenName: "Ova Turkish Lira",
  tokenSymbol: "ovaTRY",
  wrappedTokenName: "Wrapped ovaEUR",
  wrappedTokenSymbol: "wEUR",
  // Avalanche blockchain ID for TR L1 (generated from subnet)
  blockchainID: process.env.TR_BLOCKCHAIN_ID || ethers.id("ova-tr-l1").slice(0, 66),
};

const EU_CONFIG = {
  networkName: "ova-eu-testnet",
  tokenName: "Ova Euro",
  tokenSymbol: "ovaEUR",
  wrappedTokenName: "Wrapped ovaTRY",
  wrappedTokenSymbol: "wTRY",
  // Avalanche blockchain ID for EU L1 (generated from subnet)
  blockchainID: process.env.EU_BLOCKCHAIN_ID || ethers.id("ova-eu-l1").slice(0, 66),
};

async function deployToChain(
  config: typeof TR_CONFIG,
  teleporterAddress: string,
  isProduction: boolean
): Promise<ChainDeployment> {
  const [deployer] = await ethers.getSigners();
  console.log(`\n${"=".repeat(60)}`);
  console.log(`DEPLOYING TO ${config.networkName.toUpperCase()}`);
  console.log(`${"=".repeat(60)}`);
  console.log("Deployer:", deployer.address);
  console.log("Token:", config.tokenName, `(${config.tokenSymbol})`);
  console.log("Wrapped:", config.wrappedTokenName, `(${config.wrappedTokenSymbol})`);
  console.log("Blockchain ID:", config.blockchainID);

  const multisigAddress = process.env.MULTISIG_ADDRESS || deployer.address;
  const minterAddress = process.env.MINTER_ADDRESS || deployer.address;
  const bridgeOperatorAddress = process.env.BRIDGE_OPERATOR_ADDRESS || deployer.address;

  // 1. Deploy Timelock
  console.log("\n--- Deploying OvaTimelock ---");
  const minDelay = isProduction ? 48 * 60 * 60 : 60 * 60;
  const OvaTimelock = await ethers.getContractFactory("OvaTimelock");
  const timelock = await OvaTimelock.deploy(
    minDelay,
    [multisigAddress],
    [ethers.ZeroAddress],
    ethers.ZeroAddress
  );
  await timelock.waitForDeployment();
  const timelockAddress = await timelock.getAddress();
  console.log("OvaTimelock:", timelockAddress);

  // 2. Deploy ValidatorManager
  console.log("\n--- Deploying ValidatorManager ---");
  const ValidatorManager = await ethers.getContractFactory("ValidatorManager");
  const validatorManager = await ValidatorManager.deploy(timelockAddress);
  await validatorManager.waitForDeployment();
  const validatorManagerAddress = await validatorManager.getAddress();
  console.log("ValidatorManager:", validatorManagerAddress);

  // 3. Deploy KycAllowList
  console.log("\n--- Deploying KycAllowList ---");
  const KycAllowList = await ethers.getContractFactory("KycAllowList");
  const kycAllowList = await KycAllowList.deploy();
  await kycAllowList.waitForDeployment();
  const kycAllowListAddress = await kycAllowList.getAddress();
  console.log("KycAllowList:", kycAllowListAddress);

  // 4. Deploy Stablecoin
  console.log("\n--- Deploying Stablecoin ---");
  const { upgrades } = require("@openzeppelin/hardhat-upgrades");
  const OvaStablecoin = await ethers.getContractFactory("OvaStablecoinUpgradeable");
  const stablecoin = await upgrades.deployProxy(
    OvaStablecoin,
    [config.tokenName, config.tokenSymbol, timelockAddress, minterAddress, BigInt(0)],
    { kind: "uups", initializer: "initialize" }
  );
  await stablecoin.waitForDeployment();
  const stablecoinAddress = await stablecoin.getAddress();
  const stablecoinImpl = await upgrades.erc1967.getImplementationAddress(stablecoinAddress);
  console.log("Stablecoin:", stablecoinAddress);

  // 5. Deploy TokenHome
  console.log("\n--- Deploying TokenHome ---");
  const OvaTokenHome = await ethers.getContractFactory("OvaTokenHome");
  const tokenHome = await OvaTokenHome.deploy(
    stablecoinAddress,
    teleporterAddress,
    config.blockchainID,
    timelockAddress
  );
  await tokenHome.waitForDeployment();
  const tokenHomeAddress = await tokenHome.getAddress();
  console.log("TokenHome:", tokenHomeAddress);

  // 6. Deploy TokenRemote
  console.log("\n--- Deploying TokenRemote ---");
  const OvaTokenRemote = await ethers.getContractFactory("OvaTokenRemote");
  const tokenRemote = await OvaTokenRemote.deploy(
    config.wrappedTokenName,
    config.wrappedTokenSymbol,
    teleporterAddress,
    config.blockchainID,
    timelockAddress
  );
  await tokenRemote.waitForDeployment();
  const tokenRemoteAddress = await tokenRemote.getAddress();
  console.log("TokenRemote:", tokenRemoteAddress);

  // 7. Deploy BridgeAdapter
  console.log("\n--- Deploying BridgeAdapter ---");
  const OvaBridgeAdapter = await ethers.getContractFactory("OvaBridgeAdapter");
  const bridgeAdapter = await OvaBridgeAdapter.deploy(stablecoinAddress, timelockAddress);
  await bridgeAdapter.waitForDeployment();
  const bridgeAdapterAddress = await bridgeAdapter.getAddress();
  console.log("BridgeAdapter:", bridgeAdapterAddress);

  // 8. Configure roles
  console.log("\n--- Configuring Roles ---");
  const BRIDGE_OPERATOR_ROLE = await bridgeAdapter.BRIDGE_OPERATOR_ROLE();
  await bridgeAdapter.grantRole(BRIDGE_OPERATOR_ROLE, bridgeOperatorAddress);
  console.log("Granted BRIDGE_OPERATOR_ROLE to:", bridgeOperatorAddress);

  return {
    network: config.networkName,
    chainId: Number((await ethers.provider.getNetwork()).chainId),
    blockchainID: config.blockchainID,
    contracts: {
      timelock: timelockAddress,
      validatorManager: validatorManagerAddress,
      kycAllowList: kycAllowListAddress,
      stablecoin: stablecoinAddress,
      stablecoinImpl: stablecoinImpl,
      tokenHome: tokenHomeAddress,
      tokenRemote: tokenRemoteAddress,
      bridgeAdapter: bridgeAdapterAddress,
    },
    roles: {
      admin: timelockAddress,
      minter: minterAddress,
      bridgeOperator: bridgeOperatorAddress,
    },
  };
}

async function main() {
  console.log("\n" + "=".repeat(60));
  console.log("OVA DUAL-CHAIN DEPLOYMENT ORCHESTRATOR");
  console.log("=".repeat(60));

  const teleporterAddress = process.env.TELEPORTER_ADDRESS;
  if (!teleporterAddress || teleporterAddress === ethers.ZeroAddress) {
    console.warn("\n⚠️  WARNING: TELEPORTER_ADDRESS not set or is zero address");
    console.warn("   Bridge cross-chain messaging will not work until configured");
    console.warn("   Set TELEPORTER_ADDRESS to the deployed Teleporter contract\n");
  }

  const finalTeleporterAddress = teleporterAddress || ethers.ZeroAddress;
  const isProduction = process.env.DEPLOY_ENV === "production";

  console.log("\nConfiguration:");
  console.log("  Environment:", isProduction ? "PRODUCTION" : "development");
  console.log("  Teleporter:", finalTeleporterAddress);
  console.log("  TR Chain ID:", TR_CONFIG.blockchainID);
  console.log("  EU Chain ID:", EU_CONFIG.blockchainID);

  // For this orchestrator, we assume it's run with Hardhat's network switching
  // In practice, you'd run deploy.ts separately on each network
  // This script generates the configuration for both chains

  console.log("\n" + "!".repeat(60));
  console.log("IMPORTANT: This script generates configuration for dual-chain setup.");
  console.log("Run deploy.ts on EACH chain separately, then use configure-bridge.ts");
  console.log("!".repeat(60));

  // Generate deployment configuration template
  const deploymentTemplate: DualChainDeployment = {
    timestamp: new Date().toISOString(),
    tr: {
      network: TR_CONFIG.networkName,
      chainId: 99999,
      blockchainID: TR_CONFIG.blockchainID,
      contracts: {
        timelock: "0x...",
        validatorManager: "0x...",
        kycAllowList: "0x...",
        stablecoin: "0x...",
        stablecoinImpl: "0x...",
        tokenHome: "0x...",
        tokenRemote: "0x...",
        bridgeAdapter: "0x...",
      },
      roles: {
        admin: "0x...",
        minter: "0x...",
        bridgeOperator: "0x...",
      },
    },
    eu: {
      network: EU_CONFIG.networkName,
      chainId: 99998,
      blockchainID: EU_CONFIG.blockchainID,
      contracts: {
        timelock: "0x...",
        validatorManager: "0x...",
        kycAllowList: "0x...",
        stablecoin: "0x...",
        stablecoinImpl: "0x...",
        tokenHome: "0x...",
        tokenRemote: "0x...",
        bridgeAdapter: "0x...",
      },
      roles: {
        admin: "0x...",
        minter: "0x...",
        bridgeOperator: "0x...",
      },
    },
    bridgeConfig: {
      trTokenHomeRegisteredWith: "EU TokenRemote address (wTRY on EU L1)",
      euTokenHomeRegisteredWith: "TR TokenRemote address (wEUR on TR L1)",
      teleporterAddress: finalTeleporterAddress,
    },
  };

  // Save template
  const deploymentsDir = path.join(__dirname, "..", "deployments");
  if (!fs.existsSync(deploymentsDir)) {
    fs.mkdirSync(deploymentsDir, { recursive: true });
  }

  const templatePath = path.join(deploymentsDir, "dual-chain-template.json");
  fs.writeFileSync(templatePath, JSON.stringify(deploymentTemplate, null, 2));
  console.log("\n✅ Deployment template saved to:", templatePath);

  // Print deployment commands
  console.log("\n" + "=".repeat(60));
  console.log("DEPLOYMENT COMMANDS");
  console.log("=".repeat(60));

  console.log("\n1. Deploy to TR L1:");
  console.log(`   TOKEN_NAME="${TR_CONFIG.tokenName}" \\`);
  console.log(`   TOKEN_SYMBOL="${TR_CONFIG.tokenSymbol}" \\`);
  console.log(`   WRAPPED_TOKEN_NAME="${TR_CONFIG.wrappedTokenName}" \\`);
  console.log(`   WRAPPED_TOKEN_SYMBOL="${TR_CONFIG.wrappedTokenSymbol}" \\`);
  console.log(`   BLOCKCHAIN_ID="${TR_CONFIG.blockchainID}" \\`);
  console.log(`   TELEPORTER_ADDRESS="${finalTeleporterAddress}" \\`);
  console.log(`   npx hardhat run scripts/deploy.ts --network ova-tr-testnet`);

  console.log("\n2. Deploy to EU L1:");
  console.log(`   TOKEN_NAME="${EU_CONFIG.tokenName}" \\`);
  console.log(`   TOKEN_SYMBOL="${EU_CONFIG.tokenSymbol}" \\`);
  console.log(`   WRAPPED_TOKEN_NAME="${EU_CONFIG.wrappedTokenName}" \\`);
  console.log(`   WRAPPED_TOKEN_SYMBOL="${EU_CONFIG.wrappedTokenSymbol}" \\`);
  console.log(`   BLOCKCHAIN_ID="${EU_CONFIG.blockchainID}" \\`);
  console.log(`   TELEPORTER_ADDRESS="${finalTeleporterAddress}" \\`);
  console.log(`   npx hardhat run scripts/deploy.ts --network ova-eu-testnet`);

  console.log("\n3. After both deployments, update dual-chain-deployment.json with actual addresses");
  console.log("   Then run: npx hardhat run scripts/configure-bridge.ts");

  console.log("\n" + "=".repeat(60));
  console.log("CROSS-CHAIN REGISTRATION (after deployment)");
  console.log("=".repeat(60));
  console.log(`
The configure-bridge.ts script will:

1. TR L1 TokenHome.registerRemote(EU_BLOCKCHAIN_ID, EU_TokenRemote)
   - Allows TR L1 to send TRY → EU L1 (mints wTRY)

2. EU L1 TokenRemote.registerHomeChain(TR_BLOCKCHAIN_ID, TR_TokenHome)
   - Allows EU L1 wTRY to be burned and unlocked on TR L1

3. EU L1 TokenHome.registerRemote(TR_BLOCKCHAIN_ID, TR_TokenRemote)
   - Allows EU L1 to send EUR → TR L1 (mints wEUR)

4. TR L1 TokenRemote.registerHomeChain(EU_BLOCKCHAIN_ID, EU_TokenHome)
   - Allows TR L1 wEUR to be burned and unlocked on EU L1

5. Configure BridgeAdapters on both chains with all addresses
`);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
