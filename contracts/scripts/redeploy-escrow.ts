import { ethers } from "hardhat";

/**
 * Redeploy ONLY AriVehicleEscrow (keeps existing AriVehicleNFT).
 * Sets up all roles and registers the new escrow on the existing NFT.
 *
 * USAGE:
 *   STABLECOIN_ADDRESS=0x... NFT_ADDRESS=0x... npx hardhat run scripts/redeploy-escrow.ts --network ari-tr-testnet
 *
 * ENVIRONMENT VARIABLES:
 *   STABLECOIN_ADDRESS  — existing ariTRY on TR L1
 *   NFT_ADDRESS         — existing AriVehicleNFT
 *   TREASURY_ADDRESS    — fee collection (defaults to deployer)
 *   OLD_ESCROW_ADDRESS  — previous escrow to revoke (optional)
 */
async function main() {
  const [deployer] = await ethers.getSigners();
  console.log("Redeploying escrow with account:", deployer.address);
  console.log("Balance:", ethers.formatEther(await deployer.provider.getBalance(deployer.address)), "AVAX");

  const stablecoinAddress = process.env.STABLECOIN_ADDRESS;
  const nftAddress = process.env.NFT_ADDRESS;
  const treasuryAddress = process.env.TREASURY_ADDRESS || deployer.address;
  const oldEscrowAddress = process.env.OLD_ESCROW_ADDRESS;

  if (!stablecoinAddress) throw new Error("STABLECOIN_ADDRESS required");
  if (!nftAddress) throw new Error("NFT_ADDRESS required");

  const MINTER_ROLE = ethers.keccak256(ethers.toUtf8Bytes("MINTER_ROLE"));
  const OPERATOR_ROLE = ethers.keccak256(ethers.toUtf8Bytes("OPERATOR_ROLE"));

  // Step 1: Deploy new AriVehicleEscrow
  console.log("\n--- Step 1: Deploying new AriVehicleEscrow ---");
  const AriVehicleEscrow = await ethers.getContractFactory("AriVehicleEscrow");
  const escrow = await AriVehicleEscrow.deploy(stablecoinAddress, nftAddress, treasuryAddress);
  await escrow.waitForDeployment();
  const escrowAddress = await escrow.getAddress();
  console.log(`  New AriVehicleEscrow deployed at: ${escrowAddress}`);

  // Step 2: Grant OPERATOR_ROLE on escrow to deployer
  console.log("\n--- Step 2: Grant OPERATOR_ROLE on escrow ---");
  await (await escrow.grantRole(OPERATOR_ROLE, deployer.address)).wait();
  console.log(`  Granted OPERATOR_ROLE to ${deployer.address}`);

  // Step 3: Grant MINTER_ROLE on stablecoin to new escrow (for burn on cancel)
  console.log("\n--- Step 3: Grant stablecoin roles to new escrow ---");
  const stablecoin = await ethers.getContractAt("AriStablecoin", stablecoinAddress);
  await (await stablecoin.grantRole(MINTER_ROLE, escrowAddress)).wait();
  console.log(`  Granted MINTER_ROLE on stablecoin to escrow`);

  // Step 4: Grant DEFAULT_ADMIN_ROLE on stablecoin to escrow (for allowlist management)
  await (await stablecoin.grantRole(ethers.ZeroHash, escrowAddress)).wait();
  console.log(`  Granted DEFAULT_ADMIN_ROLE on stablecoin to escrow`);

  // Step 5: Allowlist escrow contract on stablecoin
  await (await stablecoin.addToAllowlist(escrowAddress)).wait();
  console.log(`  Allowlisted escrow on stablecoin`);

  // Step 6: Register new escrow on NFT contract
  console.log("\n--- Step 6: Register new escrow on NFT ---");
  const nft = await ethers.getContractAt("AriVehicleNFT", nftAddress);
  await (await nft.setApprovedEscrow(escrowAddress, true)).wait();
  console.log(`  New escrow ${escrowAddress} approved on NFT`);

  // Step 7: Revoke old escrow if provided
  if (oldEscrowAddress) {
    console.log("\n--- Step 7: Revoke old escrow ---");
    await (await nft.setApprovedEscrow(oldEscrowAddress, false)).wait();
    console.log(`  Old escrow ${oldEscrowAddress} revoked on NFT`);
    // Revoke stablecoin roles from old escrow
    await (await stablecoin.revokeRole(MINTER_ROLE, oldEscrowAddress)).wait();
    await (await stablecoin.revokeRole(ethers.ZeroHash, oldEscrowAddress)).wait();
    console.log(`  Revoked stablecoin roles from old escrow`);
  }

  console.log("\n=== Escrow Redeployment Complete ===");
  console.log(`New AriVehicleEscrow: ${escrowAddress}`);
  console.log(`AriVehicleNFT:       ${nftAddress} (unchanged)`);
  console.log(`AriStablecoin:       ${stablecoinAddress}`);
  console.log(`Treasury:            ${treasuryAddress}`);
  console.log(`\nUpdate application-fuji.yml escrow-address to: "${escrowAddress}"`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
