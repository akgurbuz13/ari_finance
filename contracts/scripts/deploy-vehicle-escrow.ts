import { ethers } from "hardhat";
import * as fs from "fs";
import * as path from "path";

/**
 * Deployment script for AriVehicleNFT + AriVehicleEscrow on TR L1.
 *
 * USAGE:
 *   npx hardhat run scripts/deploy-vehicle-escrow.ts --network ari-tr-testnet
 *
 * ENVIRONMENT VARIABLES:
 *   STABLECOIN_ADDRESS=0x...           # Existing ariTRY on TR L1
 *   TREASURY_ADDRESS=0x...             # Fee collection address
 *   OPERATOR_ADDRESS=0x...             # Backend operator key (optional, defaults to deployer)
 */
async function main() {
  const [deployer] = await ethers.getSigners();
  console.log("Deploying vehicle escrow contracts with account:", deployer.address);
  console.log("Balance:", ethers.formatEther(await deployer.provider.getBalance(deployer.address)), "AVAX");

  const stablecoinAddress = process.env.STABLECOIN_ADDRESS;
  const treasuryAddress = process.env.TREASURY_ADDRESS || deployer.address;
  const operatorAddress = process.env.OPERATOR_ADDRESS || deployer.address;

  if (!stablecoinAddress) {
    throw new Error("STABLECOIN_ADDRESS environment variable required");
  }

  const MINTER_ROLE = ethers.keccak256(ethers.toUtf8Bytes("MINTER_ROLE"));
  const OPERATOR_ROLE = ethers.keccak256(ethers.toUtf8Bytes("OPERATOR_ROLE"));

  // Step 1: Deploy AriVehicleNFT
  console.log("\n--- Step 1: Deploying AriVehicleNFT ---");
  const AriVehicleNFT = await ethers.getContractFactory("AriVehicleNFT");
  const nft = await AriVehicleNFT.deploy();
  await nft.waitForDeployment();
  const nftAddress = await nft.getAddress();
  console.log(`  AriVehicleNFT deployed at: ${nftAddress}`);

  // Step 2: Deploy AriVehicleEscrow
  console.log("\n--- Step 2: Deploying AriVehicleEscrow ---");
  const AriVehicleEscrow = await ethers.getContractFactory("AriVehicleEscrow");
  const escrow = await AriVehicleEscrow.deploy(stablecoinAddress, nftAddress, treasuryAddress);
  await escrow.waitForDeployment();
  const escrowAddress = await escrow.getAddress();
  console.log(`  AriVehicleEscrow deployed at: ${escrowAddress}`);

  // Step 3: Grant MINTER_ROLE on vehicleNFT to operator
  console.log("\n--- Step 3: Grant MINTER_ROLE on NFT to operator ---");
  await (await nft.grantRole(MINTER_ROLE, operatorAddress)).wait();
  console.log(`  Granted MINTER_ROLE to ${operatorAddress}`);

  // Step 4: Register escrow as approved on vehicleNFT
  console.log("\n--- Step 4: Register escrow on NFT ---");
  await (await nft.setApprovedEscrow(escrowAddress, true)).wait();
  console.log(`  Escrow ${escrowAddress} approved on NFT`);

  // Step 5: Grant MINTER_ROLE on stablecoin to escrow (for burn on cancel)
  console.log("\n--- Step 5: Grant stablecoin roles to escrow ---");
  const stablecoin = await ethers.getContractAt("AriStablecoin", stablecoinAddress);
  await (await stablecoin.grantRole(MINTER_ROLE, escrowAddress)).wait();
  console.log(`  Granted MINTER_ROLE on stablecoin to escrow`);

  // Step 6: Grant DEFAULT_ADMIN_ROLE on stablecoin to escrow (for allowlist management)
  await (await stablecoin.grantRole(ethers.ZeroHash, escrowAddress)).wait();
  console.log(`  Granted DEFAULT_ADMIN_ROLE on stablecoin to escrow`);

  // Step 7: Allowlist escrow contract on stablecoin
  await (await stablecoin.addToAllowlist(escrowAddress)).wait();
  console.log(`  Allowlisted escrow on stablecoin`);

  // Step 8: Grant OPERATOR_ROLE on escrow to operator
  console.log("\n--- Step 6: Grant OPERATOR_ROLE on escrow ---");
  await (await escrow.grantRole(OPERATOR_ROLE, operatorAddress)).wait();
  console.log(`  Granted OPERATOR_ROLE to ${operatorAddress}`);

  // Save deployment JSON
  const deployment = {
    network: (await ethers.provider.getNetwork()).name,
    chainId: Number((await ethers.provider.getNetwork()).chainId),
    deployer: deployer.address,
    operator: operatorAddress,
    treasury: treasuryAddress,
    contracts: {
      AriVehicleNFT: nftAddress,
      AriVehicleEscrow: escrowAddress,
      AriStablecoin: stablecoinAddress,
    },
    deployedAt: new Date().toISOString(),
  };

  const deployDir = path.join(__dirname, "..", "deployments");
  if (!fs.existsSync(deployDir)) {
    fs.mkdirSync(deployDir, { recursive: true });
  }
  const filename = `vehicle-escrow-${deployment.chainId}.json`;
  fs.writeFileSync(path.join(deployDir, filename), JSON.stringify(deployment, null, 2));

  console.log("\n=== Deployment Summary ===");
  console.log(`AriVehicleNFT:    ${nftAddress}`);
  console.log(`AriVehicleEscrow: ${escrowAddress}`);
  console.log(`AriStablecoin:    ${stablecoinAddress}`);
  console.log(`Treasury:         ${treasuryAddress}`);
  console.log(`Operator:         ${operatorAddress}`);
  console.log(`\nDeployment saved to: deployments/${filename}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
