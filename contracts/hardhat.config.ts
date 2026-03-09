import { HardhatUserConfig } from "hardhat/config";
import "@nomicfoundation/hardhat-toolbox";
import "@openzeppelin/hardhat-upgrades";

const config: HardhatUserConfig = {
  solidity: {
    version: "0.8.24",
    settings: {
      optimizer: {
        enabled: true,
        runs: 200,
      },
      viaIR: true, // Enable IR-based code generation for complex contracts
    },
  },
  networks: {
    hardhat: {
      chainId: 31337,
    },
    // ARI TR L1 (Turkey) - Fuji Chain ID 1279
    "ari-tr-testnet": {
      url: process.env.TR_L1_RPC_URL || "https://nodes-prod.18.182.4.86.sslip.io/ext/bc/2P1BXtVXL2xnUjDzLYnDu114Z8dhqV8iLrcKbMdmmWaTkmtKfM/rpc",
      accounts: process.env.DEPLOYER_PRIVATE_KEY ? [process.env.DEPLOYER_PRIVATE_KEY] : [],
      chainId: Number(process.env.TR_L1_CHAIN_ID) || 1279,
    },
    "ari-tr-mainnet": {
      url: process.env.TR_L1_MAINNET_RPC_URL || "",
      accounts: process.env.DEPLOYER_PRIVATE_KEY ? [process.env.DEPLOYER_PRIVATE_KEY] : [],
      chainId: Number(process.env.TR_L1_MAINNET_CHAIN_ID) || 1279,
    },
    // ARI EU L1 (Europe) - Fuji Chain ID 1832
    "ari-eu-testnet": {
      url: process.env.EU_L1_RPC_URL || "https://nodes-prod.18.182.4.86.sslip.io/ext/bc/7ScHYNLYUpWHr5wN5xtBjPN9UV9dTCAYSqYgeMUc6x5ssaXLt/rpc",
      accounts: process.env.DEPLOYER_PRIVATE_KEY ? [process.env.DEPLOYER_PRIVATE_KEY] : [],
      chainId: Number(process.env.EU_L1_CHAIN_ID) || 1832,
    },
    "ari-eu-mainnet": {
      url: process.env.EU_L1_MAINNET_RPC_URL || "",
      accounts: process.env.DEPLOYER_PRIVATE_KEY ? [process.env.DEPLOYER_PRIVATE_KEY] : [],
      chainId: Number(process.env.EU_L1_MAINNET_CHAIN_ID) || 1832,
    },
    // Legacy testnet/mainnet for backwards compatibility
    testnet: {
      url: process.env.TESTNET_RPC_URL || "",
      accounts: process.env.DEPLOYER_PRIVATE_KEY ? [process.env.DEPLOYER_PRIVATE_KEY] : [],
      chainId: Number(process.env.TESTNET_CHAIN_ID) || 99999,
    },
    mainnet: {
      url: process.env.MAINNET_RPC_URL || "",
      accounts: process.env.DEPLOYER_PRIVATE_KEY ? [process.env.DEPLOYER_PRIVATE_KEY] : [],
      chainId: Number(process.env.MAINNET_CHAIN_ID) || 43114,
    },
  },
  // Etherscan verification config (for Avalanche)
  etherscan: {
    apiKey: {
      "ari-tr-mainnet": process.env.SNOWTRACE_API_KEY || "",
      "ari-eu-mainnet": process.env.SNOWTRACE_API_KEY || "",
    },
    customChains: [
      {
        network: "ari-tr-mainnet",
        chainId: Number(process.env.TR_L1_MAINNET_CHAIN_ID) || 99999,
        urls: {
          apiURL: process.env.TR_EXPLORER_API_URL || "",
          browserURL: process.env.TR_EXPLORER_URL || "",
        },
      },
      {
        network: "ari-eu-mainnet",
        chainId: Number(process.env.EU_L1_MAINNET_CHAIN_ID) || 99998,
        urls: {
          apiURL: process.env.EU_EXPLORER_API_URL || "",
          browserURL: process.env.EU_EXPLORER_URL || "",
        },
      },
    ],
  },
};

export default config;
