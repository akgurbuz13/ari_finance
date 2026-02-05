import { ethers, upgrades } from "hardhat";

/**
 * Deployment script for Ova Platform smart contracts on a SINGLE chain.
 *
 * DEPLOYMENT ORDER (important for dependencies):
 * 1. OvaTimelock - Governance timelock (48h delay)
 * 2. ValidatorManager - PoA validator set management
 * 3. OvaStablecoinUpgradeable - UUPS proxy stablecoin
 * 4. OvaTokenHome - ICTT TokenHome for native token bridging
 * 5. OvaTokenRemote - ICTT TokenRemote for wrapped tokens from partner chain
 * 6. OvaBridgeAdapter - High-level bridge orchestration
 * 7. KycAllowList - KYC verification registry
 *
 * SECURITY NOTES:
 * - For production, use hardware wallet or multisig as deployer
 * - Transfer DEFAULT_ADMIN_ROLE to timelock after deployment
 * - Verify all contracts on explorer before announcing
 *
 * USAGE:
 *   npx hardhat run scripts/deploy.ts --network ova-tr-testnet
 *   npx hardhat run scripts/deploy.ts --network ova-eu-testnet
 *
 * ENVIRONMENT VARIABLES:
 *   DEPLOY_ENV=production          # Use production settings (48h timelock)
 *   MULTISIG_ADDRESS=0x...         # Governance multisig
 *   MINTER_ADDRESS=0x...           # Address with minting rights
 *   BRIDGE_OPERATOR_ADDRESS=0x...  # Address for bridge operations
 *   TOKEN_NAME="Ova Turkish Lira"  # Token name
 *   TOKEN_SYMBOL="ovaTRY"          # Token symbol
 *   WRAPPED_TOKEN_NAME="Wrapped ovaEUR"  # Wrapped token from partner chain
 *   WRAPPED_TOKEN_SYMBOL="wEUR"    # Wrapped token symbol
 *   SUPPLY_CAP=0                   # Supply cap (0 = unlimited)
 *   TELEPORTER_ADDRESS=0x...       # Avalanche Teleporter contract
 *   BLOCKCHAIN_ID=0x...            # This chain's blockchain ID (bytes32)
 */
async function main() {
  const [deployer] = await ethers.getSigners();
  console.log("Deploying contracts with account:", deployer.address);
  console.log("Account balance:", (await deployer.provider.getBalance(deployer.address)).toString());

  // Configuration from environment
  const isProduction = process.env.DEPLOY_ENV === "production";
  const multisigAddress = process.env.MULTISIG_ADDRESS || deployer.address;
  const minterAddress = process.env.MINTER_ADDRESS || deployer.address;
  const bridgeOperatorAddress = process.env.BRIDGE_OPERATOR_ADDRESS || deployer.address;
  const tokenName = process.env.TOKEN_NAME || "Ova Turkish Lira";
  const tokenSymbol = process.env.TOKEN_SYMBOL || "ovaTRY";
  const wrappedTokenName = process.env.WRAPPED_TOKEN_NAME || "Wrapped ovaEUR";
  const wrappedTokenSymbol = process.env.WRAPPED_TOKEN_SYMBOL || "wEUR";
  const supplyCap = process.env.SUPPLY_CAP || "0"; // 0 = unlimited
  const teleporterAddress = process.env.TELEPORTER_ADDRESS || ethers.ZeroAddress;
  const blockchainIdHex = process.env.BLOCKCHAIN_ID || ethers.ZeroHash;
  const blockchainID = blockchainIdHex;

  console.log("\n--- Configuration ---");
  console.log("Environment:", isProduction ? "PRODUCTION" : "development");
  console.log("Multisig/Admin:", multisigAddress);
  console.log("Minter:", minterAddress);
  console.log("Bridge Operator:", bridgeOperatorAddress);
  console.log("Token:", tokenName, `(${tokenSymbol})`);
  console.log("Wrapped Token:", wrappedTokenName, `(${wrappedTokenSymbol})`);
  console.log("Supply Cap:", supplyCap === "0" ? "unlimited" : supplyCap);
  console.log("Teleporter:", teleporterAddress);
  console.log("Blockchain ID:", blockchainID);

  // ===== 1. Deploy Timelock =====
  console.log("\n--- Deploying OvaTimelock ---");
  const minDelay = isProduction ? 48 * 60 * 60 : 60 * 60; // 48h prod, 1h dev
  const proposers = [multisigAddress];
  const executors = [ethers.ZeroAddress]; // Anyone can execute after delay
  const timelockAdmin = ethers.ZeroAddress; // No admin (self-governed)

  const OvaTimelock = await ethers.getContractFactory("OvaTimelock");
  const timelock = await OvaTimelock.deploy(minDelay, proposers, executors, timelockAdmin);
  await timelock.waitForDeployment();
  const timelockAddress = await timelock.getAddress();
  console.log("OvaTimelock deployed to:", timelockAddress);
  console.log("  Min delay:", minDelay / 3600, "hours");

  // ===== 2. Deploy ValidatorManager =====
  console.log("\n--- Deploying ValidatorManager ---");
  const ValidatorManager = await ethers.getContractFactory("ValidatorManager");
  const validatorManager = await ValidatorManager.deploy(timelockAddress);
  await validatorManager.waitForDeployment();
  const validatorManagerAddress = await validatorManager.getAddress();
  console.log("ValidatorManager deployed to:", validatorManagerAddress);

  // ===== 3. Deploy KYC AllowList =====
  console.log("\n--- Deploying KycAllowList ---");
  const KycAllowList = await ethers.getContractFactory("KycAllowList");
  const kycAllowList = await KycAllowList.deploy();
  await kycAllowList.waitForDeployment();
  const kycAllowListAddress = await kycAllowList.getAddress();
  console.log("KycAllowList deployed to:", kycAllowListAddress);

  // ===== 4. Deploy Stablecoin (Upgradeable) =====
  console.log("\n--- Deploying OvaStablecoinUpgradeable (UUPS Proxy) ---");
  const OvaStablecoinUpgradeable = await ethers.getContractFactory("OvaStablecoinUpgradeable");
  const stablecoin = await upgrades.deployProxy(
    OvaStablecoinUpgradeable,
    [tokenName, tokenSymbol, timelockAddress, minterAddress, BigInt(supplyCap)],
    {
      kind: "uups",
      initializer: "initialize",
    }
  );
  await stablecoin.waitForDeployment();
  const stablecoinAddress = await stablecoin.getAddress();
  const implementationAddress = await upgrades.erc1967.getImplementationAddress(stablecoinAddress);
  console.log("OvaStablecoin proxy deployed to:", stablecoinAddress);
  console.log("OvaStablecoin implementation:", implementationAddress);

  // ===== 5. Deploy TokenHome =====
  console.log("\n--- Deploying OvaTokenHome ---");
  const OvaTokenHome = await ethers.getContractFactory("OvaTokenHome");
  const tokenHome = await OvaTokenHome.deploy(
    stablecoinAddress,      // Native token
    teleporterAddress,      // Teleporter messenger
    blockchainID,           // This chain's blockchain ID
    timelockAddress         // Admin (timelock)
  );
  await tokenHome.waitForDeployment();
  const tokenHomeAddress = await tokenHome.getAddress();
  console.log("OvaTokenHome deployed to:", tokenHomeAddress);

  // ===== 6. Deploy TokenRemote (for wrapped tokens from partner chain) =====
  console.log("\n--- Deploying OvaTokenRemote ---");
  const OvaTokenRemote = await ethers.getContractFactory("OvaTokenRemote");
  const tokenRemote = await OvaTokenRemote.deploy(
    wrappedTokenName,       // Name of wrapped token
    wrappedTokenSymbol,     // Symbol of wrapped token
    teleporterAddress,      // Teleporter messenger
    blockchainID,           // This chain's blockchain ID
    timelockAddress         // Admin
  );
  await tokenRemote.waitForDeployment();
  const tokenRemoteAddress = await tokenRemote.getAddress();
  console.log("OvaTokenRemote deployed to:", tokenRemoteAddress);

  // ===== 7. Deploy Bridge Adapter =====
  console.log("\n--- Deploying OvaBridgeAdapter ---");
  const OvaBridgeAdapter = await ethers.getContractFactory("OvaBridgeAdapter");
  const bridgeAdapter = await OvaBridgeAdapter.deploy(stablecoinAddress, timelockAddress);
  await bridgeAdapter.waitForDeployment();
  const bridgeAdapterAddress = await bridgeAdapter.getAddress();
  console.log("OvaBridgeAdapter deployed to:", bridgeAdapterAddress);

  // ===== 8. Configure Roles =====
  console.log("\n--- Configuring Roles ---");

  // Grant BRIDGE_OPERATOR_ROLE to bridge operator
  const BRIDGE_OPERATOR_ROLE = await bridgeAdapter.BRIDGE_OPERATOR_ROLE();
  await bridgeAdapter.grantRole(BRIDGE_OPERATOR_ROLE, bridgeOperatorAddress);
  console.log("Granted BRIDGE_OPERATOR_ROLE to:", bridgeOperatorAddress);

  // Transfer bridge adapter admin to timelock
  const BRIDGE_ADMIN_ROLE = await bridgeAdapter.DEFAULT_ADMIN_ROLE();
  await bridgeAdapter.grantRole(BRIDGE_ADMIN_ROLE, timelockAddress);
  console.log("Granted DEFAULT_ADMIN_ROLE on BridgeAdapter to timelock");

  // Grant TokenHome admin roles
  const TOKEN_HOME_BRIDGE_ADMIN = await tokenHome.BRIDGE_ADMIN_ROLE();
  await tokenHome.grantRole(TOKEN_HOME_BRIDGE_ADMIN, timelockAddress);
  console.log("Granted BRIDGE_ADMIN_ROLE on TokenHome to timelock");

  // Grant TokenRemote admin roles
  const TOKEN_REMOTE_BRIDGE_ADMIN = await tokenRemote.BRIDGE_ADMIN_ROLE();
  await tokenRemote.grantRole(TOKEN_REMOTE_BRIDGE_ADMIN, timelockAddress);
  console.log("Granted BRIDGE_ADMIN_ROLE on TokenRemote to timelock");

  if (isProduction) {
    // Renounce deployer's admin role on bridge adapter
    await bridgeAdapter.renounceRole(BRIDGE_ADMIN_ROLE, deployer.address);
    console.log("Renounced deployer's admin role on BridgeAdapter");
  }

  // ===== Summary =====
  console.log("\n" + "=".repeat(60));
  console.log("DEPLOYMENT SUMMARY");
  console.log("=".repeat(60));
  console.log("Network:", (await ethers.provider.getNetwork()).name);
  console.log("Chain ID:", (await ethers.provider.getNetwork()).chainId.toString());
  console.log("");
  console.log("Core Contracts:");
  console.log("  OvaTimelock:              ", timelockAddress);
  console.log("  ValidatorManager:         ", validatorManagerAddress);
  console.log("  KycAllowList:             ", kycAllowListAddress);
  console.log("  OvaStablecoin (proxy):    ", stablecoinAddress);
  console.log("  OvaStablecoin (impl):     ", implementationAddress);
  console.log("");
  console.log("ICTT Bridge Contracts:");
  console.log("  OvaTokenHome:             ", tokenHomeAddress);
  console.log("  OvaTokenRemote:           ", tokenRemoteAddress);
  console.log("  OvaBridgeAdapter:         ", bridgeAdapterAddress);
  console.log("");
  console.log("Admin/Governance:");
  console.log("  Timelock min delay:       ", minDelay / 3600, "hours");
  console.log("  Proposer (multisig):      ", multisigAddress);
  console.log("  Minter:                   ", minterAddress);
  console.log("  Bridge Operator:          ", bridgeOperatorAddress);
  console.log("=".repeat(60));

  // ===== Export addresses for configure-bridge.ts =====
  const deploymentOutput = {
    network: (await ethers.provider.getNetwork()).name,
    chainId: Number((await ethers.provider.getNetwork()).chainId),
    blockchainID: blockchainID,
    contracts: {
      timelock: timelockAddress,
      validatorManager: validatorManagerAddress,
      kycAllowList: kycAllowListAddress,
      stablecoin: stablecoinAddress,
      stablecoinImpl: implementationAddress,
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

  console.log("\n--- Deployment JSON (save this for configure-bridge.ts) ---");
  console.log(JSON.stringify(deploymentOutput, null, 2));

  // ===== Post-Deployment Instructions =====
  console.log("\n" + "!".repeat(60));
  console.log("POST-DEPLOYMENT CHECKLIST");
  console.log("!".repeat(60));
  console.log("1. Save the deployment JSON above");
  console.log("2. Deploy on the PARTNER chain with appropriate settings");
  console.log("3. Run configure-bridge.ts to cross-register TokenHome/TokenRemote");
  console.log("4. Configure BridgeAdapter with ICTT addresses");
  console.log("5. Verify all contracts on block explorer");
  console.log("6. Update blockchain-service config with new addresses");
  if (isProduction) {
    console.log("7. Perform security audit of deployed contracts");
    console.log("8. Add initial validators via timelock");
  }
  console.log("!".repeat(60));
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
