import { ethers } from "hardhat";

/**
 * Test Bridge Script for Fuji Deployment Verification
 *
 * Tests the full bridge flow on a SINGLE chain (source side):
 * 1. Adds deployer to stablecoin allowlist
 * 2. Mints test tokens
 * 3. Approves TokenHome to spend tokens
 * 4. Calls TokenHome.bridgeTokens (locks tokens, sends Teleporter message)
 *
 * To verify the destination side, run this script on the partner chain
 * and check that AriTokenRemote minted wrapped tokens.
 *
 * USAGE:
 *   # Test on TR L1 (bridges ariTRY to EU L1)
 *   source .env.fuji
 *   npx hardhat run scripts/test-bridge.ts --network ari-tr-testnet
 *
 *   # Test on EU L1 (bridges ariEUR to TR L1)
 *   npx hardhat run scripts/test-bridge.ts --network ari-eu-testnet
 */

async function main() {
  const [deployer] = await ethers.getSigners();
  const network = await ethers.provider.getNetwork();

  console.log("\n" + "=".repeat(60));
  console.log("ARI BRIDGE TEST");
  console.log("=".repeat(60));
  console.log("Network:", network.name);
  console.log("Chain ID:", network.chainId.toString());
  console.log("Deployer:", deployer.address);
  console.log(
    "Balance:",
    ethers.formatEther(await deployer.provider.getBalance(deployer.address)),
    "AVAX"
  );

  // Read addresses from environment
  const stablecoinAddress = process.env.STABLECOIN_ADDRESS;
  const tokenHomeAddress = process.env.TOKEN_HOME_ADDRESS;
  const tokenRemoteAddress = process.env.TOKEN_REMOTE_ADDRESS;
  const partnerBlockchainId = process.env.PARTNER_BLOCKCHAIN_ID;

  if (!stablecoinAddress || !tokenHomeAddress) {
    console.error(
      "\nMissing required environment variables:"
    );
    console.error("  STABLECOIN_ADDRESS - AriStablecoin proxy address on this chain");
    console.error("  TOKEN_HOME_ADDRESS - AriTokenHome address on this chain");
    console.error("  PARTNER_BLOCKCHAIN_ID - Partner chain blockchain ID (bytes32 hex)");
    console.error("\nExample:");
    console.error(
      "  STABLECOIN_ADDRESS=0x... TOKEN_HOME_ADDRESS=0x... PARTNER_BLOCKCHAIN_ID=0x... \\"
    );
    console.error(
      "  npx hardhat run scripts/test-bridge.ts --network ari-tr-testnet"
    );
    process.exit(1);
  }

  const testAmount = ethers.parseEther("100"); // 100 tokens
  const mintAmount = ethers.parseEther("10000"); // 10,000 tokens

  // Get contract instances
  const stablecoin = await ethers.getContractAt(
    "AriStablecoinUpgradeable",
    stablecoinAddress
  );
  const tokenHome = await ethers.getContractAt(
    "AriTokenHome",
    tokenHomeAddress
  );

  // ===== Step 1: Check and set up allowlist =====
  console.log("\n--- Step 1: Allowlist Setup ---");
  const isAllowlisted = await stablecoin.allowlisted(deployer.address);
  if (!isAllowlisted) {
    console.log("Adding deployer to allowlist...");
    const tx1 = await stablecoin.addToAllowlist(deployer.address);
    await tx1.wait();
    console.log("  Tx:", tx1.hash);
  } else {
    console.log("Deployer already allowlisted");
  }

  // Also allowlist TokenHome so it can receive tokens
  const isTokenHomeAllowlisted = await stablecoin.allowlisted(tokenHomeAddress);
  if (!isTokenHomeAllowlisted) {
    console.log("Adding TokenHome to allowlist...");
    const tx = await stablecoin.addToAllowlist(tokenHomeAddress);
    await tx.wait();
    console.log("  Tx:", tx.hash);
  } else {
    console.log("TokenHome already allowlisted");
  }

  // ===== Step 2: Mint test tokens =====
  console.log("\n--- Step 2: Mint Test Tokens ---");
  const currentBalance = await stablecoin.balanceOf(deployer.address);
  console.log(
    "Current balance:",
    ethers.formatEther(currentBalance),
    await stablecoin.symbol()
  );

  if (currentBalance < testAmount) {
    console.log("Minting", ethers.formatEther(mintAmount), "tokens...");
    const tx2 = await stablecoin.mint(deployer.address, mintAmount);
    await tx2.wait();
    console.log("  Tx:", tx2.hash);

    const newBalance = await stablecoin.balanceOf(deployer.address);
    console.log("New balance:", ethers.formatEther(newBalance));
  } else {
    console.log("Sufficient balance, skipping mint");
  }

  // ===== Step 3: Check token name =====
  console.log("\n--- Step 3: Verify Token Info ---");
  console.log("Name:", await stablecoin.name());
  console.log("Symbol:", await stablecoin.symbol());
  console.log(
    "Total Supply:",
    ethers.formatEther(await stablecoin.totalSupply())
  );

  // ===== Step 4: Bridge tokens (if partner chain configured) =====
  if (partnerBlockchainId) {
    console.log("\n--- Step 4: Bridge Tokens ---");
    console.log("Partner blockchain ID:", partnerBlockchainId);
    console.log("Amount:", ethers.formatEther(testAmount));

    // Check if remote is registered
    const registeredRemote = await tokenHome.registeredRemotes(
      partnerBlockchainId
    );
    if (registeredRemote === ethers.ZeroAddress) {
      console.log(
        "\nTokenRemote not yet registered for partner chain."
      );
      console.log("Run configure-bridge.ts first.");
      console.log("Skipping bridge test.");
    } else {
      console.log("Registered remote:", registeredRemote);

      // Approve TokenHome to spend tokens
      console.log("Approving TokenHome...");
      const approveTx = await stablecoin.approve(tokenHomeAddress, testAmount);
      await approveTx.wait();
      console.log("  Approve tx:", approveTx.hash);

      // Bridge tokens
      console.log("Bridging tokens...");
      const bridgeTx = await tokenHome.bridgeTokens(
        partnerBlockchainId,
        deployer.address, // send to self on destination
        testAmount,
        0 // no fee for test
      );
      const receipt = await bridgeTx.wait();
      console.log("  Bridge tx:", bridgeTx.hash);
      console.log("  Block:", receipt?.blockNumber);
      console.log("  Gas used:", receipt?.gasUsed.toString());

      console.log(
        "\nBridge transfer initiated. Check destination chain for wrapped tokens."
      );
      console.log(
        "Teleporter relay may take 30-60 seconds."
      );
    }
  } else {
    console.log("\n--- Step 4: Bridge Test (SKIPPED) ---");
    console.log(
      "Set PARTNER_BLOCKCHAIN_ID to test bridging."
    );
  }

  // ===== Step 5: Check TokenRemote (if configured) =====
  if (tokenRemoteAddress) {
    console.log("\n--- Step 5: Check TokenRemote ---");
    const tokenRemote = await ethers.getContractAt(
      "AriTokenRemote",
      tokenRemoteAddress
    );
    console.log("TokenRemote name:", await tokenRemote.name());
    console.log("TokenRemote symbol:", await tokenRemote.symbol());
    const wrappedBalance = await tokenRemote.balanceOf(deployer.address);
    console.log(
      "Wrapped token balance:",
      ethers.formatEther(wrappedBalance)
    );
  }

  console.log("\n" + "=".repeat(60));
  console.log("TEST COMPLETE");
  console.log("=".repeat(60));
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
