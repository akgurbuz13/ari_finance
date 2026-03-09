import { ethers } from "hardhat";

/**
 * Cross-register burn-mint bridges between ariTR and ariEU.
 * Run on each chain to register the partner bridge.
 *
 * Usage:
 *   # On TR: register EU bridges as partners
 *   DEPLOYER_PRIVATE_KEY=0x... \
 *   npx hardhat run scripts/cross-register-bridges.ts --network ari-tr-testnet
 *
 *   # On EU: register TR bridges as partners
 *   DEPLOYER_PRIVATE_KEY=0x... \
 *   npx hardhat run scripts/cross-register-bridges.ts --network ari-eu-testnet
 */
async function main() {
  const networkName = (await import("hardhat")).default.network.name;
  const isTR = networkName.includes("tr");

  console.log(`Cross-registering bridges on ${networkName}...`);

  if (isTR) {
    // On TR L1: register EU bridges as partners
    const EU_BLOCKCHAIN_ID = "0x0ea0530c367859873c37829bdbc918ad3da9f4c7bed68d083275efc310ab03f4";

    // TR ariTRY bridge → EU ariTRY bridge (same-currency cross-border)
    const trTryBridge = await ethers.getContractAt("AriBurnMintBridge", "0x74CDb2b07e6e6441b71348E7812E7208eF909f24");
    const tx1 = await trTryBridge.registerPartner(EU_BLOCKCHAIN_ID, "0x5EB99416745b310b6D091E7Cb91C3B0297788144");
    await tx1.wait();
    console.log("  TR ariTRY bridge registered EU ariTRY bridge as partner");

    // TR ariEUR bridge → EU ariEUR bridge (same-currency cross-border)
    const trEurBridge = await ethers.getContractAt("AriBurnMintBridge", "0xA2Aa53A97A848343F7D399e186D237E905888Df4");
    const tx2 = await trEurBridge.registerPartner(EU_BLOCKCHAIN_ID, "0x1C3C34dAe1503E64033Ec99A4f2a61F32AA2Be0E");
    await tx2.wait();
    console.log("  TR ariEUR bridge registered EU ariEUR bridge as partner");
  } else {
    // On EU L1: register TR bridges as partners
    const TR_BLOCKCHAIN_ID = "0xb5a82a53e6366b84f980e4d2f13e583ca02f10eaf1ead220e23d036574799345";

    // EU ariEUR bridge → TR ariEUR bridge
    const euEurBridge = await ethers.getContractAt("AriBurnMintBridge", "0x1C3C34dAe1503E64033Ec99A4f2a61F32AA2Be0E");
    const tx1 = await euEurBridge.registerPartner(TR_BLOCKCHAIN_ID, "0xA2Aa53A97A848343F7D399e186D237E905888Df4");
    await tx1.wait();
    console.log("  EU ariEUR bridge registered TR ariEUR bridge as partner");

    // EU ariTRY bridge → TR ariTRY bridge
    const euTryBridge = await ethers.getContractAt("AriBurnMintBridge", "0x5EB99416745b310b6D091E7Cb91C3B0297788144");
    const tx2 = await euTryBridge.registerPartner(TR_BLOCKCHAIN_ID, "0x74CDb2b07e6e6441b71348E7812E7208eF909f24");
    await tx2.wait();
    console.log("  EU ariTRY bridge registered TR ariTRY bridge as partner");
  }

  console.log("Cross-registration complete!");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
