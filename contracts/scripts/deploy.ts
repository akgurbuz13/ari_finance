import { ethers, upgrades } from "hardhat";

/**
 * Deployment script for Ova Platform smart contracts.
 *
 * DEPLOYMENT ORDER (important for dependencies):
 * 1. OvaTimelock - Governance timelock (48h delay)
 * 2. ValidatorManager - PoA validator set management
 * 3. OvaStablecoinUpgradeable - UUPS proxy stablecoin
 * 4. OvaBridgeAdapter - ICTT bridge integration
 * 5. KycAllowList - KYC verification registry
 *
 * SECURITY NOTES:
 * - For production, use hardware wallet or multisig as deployer
 * - Transfer DEFAULT_ADMIN_ROLE to timelock after deployment
 * - Verify all contracts on explorer before announcing
 */
async function main() {
  const [deployer] = await ethers.getSigners();
  console.log("Deploying contracts with account:", deployer.address);
  console.log("Account balance:", (await deployer.provider.getBalance(deployer.address)).toString());

  // Configuration from environment
  const isProduction = process.env.DEPLOY_ENV === "production";
  const multisigAddress = process.env.MULTISIG_ADDRESS || deployer.address;
  const minterAddress = process.env.MINTER_ADDRESS || deployer.address;
  const tokenName = process.env.TOKEN_NAME || "Ova Turkish Lira";
  const tokenSymbol = process.env.TOKEN_SYMBOL || "ovaTRY";
  const supplyCap = process.env.SUPPLY_CAP || "0"; // 0 = unlimited

  console.log("\n--- Configuration ---");
  console.log("Environment:", isProduction ? "PRODUCTION" : "development");
  console.log("Multisig/Admin:", multisigAddress);
  console.log("Minter:", minterAddress);
  console.log("Token:", tokenName, `(${tokenSymbol})`);
  console.log("Supply Cap:", supplyCap === "0" ? "unlimited" : supplyCap);

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

  // ===== 5. Deploy Bridge Adapter =====
  console.log("\n--- Deploying OvaBridgeAdapter ---");
  const bridgeAddress = process.env.ICTT_BRIDGE_ADDRESS || ethers.ZeroAddress;
  const OvaBridgeAdapter = await ethers.getContractFactory("OvaBridgeAdapter");
  const bridgeAdapter = await OvaBridgeAdapter.deploy(stablecoinAddress, bridgeAddress);
  await bridgeAdapter.waitForDeployment();
  const bridgeAdapterAddress = await bridgeAdapter.getAddress();
  console.log("OvaBridgeAdapter deployed to:", bridgeAdapterAddress);

  // ===== 6. Configure Roles =====
  console.log("\n--- Configuring Roles ---");

  // Grant MINTER_ROLE to bridge adapter (through timelock in production)
  if (!isProduction) {
    // In dev, we can directly grant since deployer is admin
    const MINTER_ROLE = await stablecoin.MINTER_ROLE();
    // Note: In production, this should be done via timelock proposal
    console.log("Note: Bridge adapter MINTER_ROLE should be granted via timelock in production");
  }

  // Transfer bridge adapter admin to timelock
  const BRIDGE_ADMIN_ROLE = await bridgeAdapter.DEFAULT_ADMIN_ROLE();
  await bridgeAdapter.grantRole(BRIDGE_ADMIN_ROLE, timelockAddress);
  console.log("Granted DEFAULT_ADMIN_ROLE on BridgeAdapter to timelock");

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
  console.log("Contracts:");
  console.log("  OvaTimelock:              ", timelockAddress);
  console.log("  ValidatorManager:         ", validatorManagerAddress);
  console.log("  KycAllowList:             ", kycAllowListAddress);
  console.log("  OvaStablecoin (proxy):    ", stablecoinAddress);
  console.log("  OvaStablecoin (impl):     ", implementationAddress);
  console.log("  OvaBridgeAdapter:         ", bridgeAdapterAddress);
  console.log("");
  console.log("Admin/Governance:");
  console.log("  Timelock min delay:       ", minDelay / 3600, "hours");
  console.log("  Proposer (multisig):      ", multisigAddress);
  console.log("  Minter:                   ", minterAddress);
  console.log("=".repeat(60));

  // ===== Post-Deployment Instructions =====
  if (isProduction) {
    console.log("\n" + "!".repeat(60));
    console.log("POST-DEPLOYMENT CHECKLIST (PRODUCTION)");
    console.log("!".repeat(60));
    console.log("1. Verify all contracts on block explorer");
    console.log("2. Add initial validators to ValidatorManager via timelock");
    console.log("3. Grant MINTER_ROLE to bridge adapter via timelock");
    console.log("4. Configure ICTT bridge address via timelock");
    console.log("5. Perform security audit of deployed contracts");
    console.log("6. Update blockchain-service config with new addresses");
    console.log("!".repeat(60));
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
