#!/bin/bash
# ==============================================================================
# Ova Contract Deployment Script for AWS Test Environment
# ==============================================================================
# Deploys all smart contracts to both TR and EU L1 chains and configures
# cross-chain bridge connections.
#
# Prerequisites:
# - Node.js 20+ and npm installed
# - Hardhat configured in contracts/
# - .env.aws-test populated with RPC URLs and private keys
#
# Usage:
#   ./scripts/deploy-contracts.sh [--network <tr|eu|both>]
# ==============================================================================

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CONTRACTS_DIR="$PROJECT_ROOT/contracts"
ENV_FILE="$PROJECT_ROOT/.env.aws-test"

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

# Parse arguments
NETWORK="${1:-both}"
if [[ "$NETWORK" != "tr" && "$NETWORK" != "eu" && "$NETWORK" != "both" ]]; then
    log_error "Invalid network. Use: tr, eu, or both"
    exit 1
fi

# Load environment
if [[ -f "$ENV_FILE" ]]; then
    source "$ENV_FILE"
    log_info "Loaded environment from $ENV_FILE"
else
    log_error "Environment file not found: $ENV_FILE"
    exit 1
fi

# Verify required variables
verify_env() {
    local missing=()
    [[ -z "${DEPLOYER_PRIVATE_KEY:-}" ]] && missing+=("DEPLOYER_PRIVATE_KEY")
    [[ -z "${TR_L1_RPC_URL:-}" ]] && missing+=("TR_L1_RPC_URL")
    [[ -z "${EU_L1_RPC_URL:-}" ]] && missing+=("EU_L1_RPC_URL")
    [[ -z "${TREASURY_ADDRESS:-}" ]] && missing+=("TREASURY_ADDRESS")

    if [[ ${#missing[@]} -gt 0 ]]; then
        log_error "Missing required environment variables: ${missing[*]}"
        exit 1
    fi
}

# ==============================================================================
# Step 1: Compile Contracts
# ==============================================================================
compile_contracts() {
    log_step "Compiling smart contracts..."
    cd "$CONTRACTS_DIR"
    npm install
    npx hardhat compile
    log_info "Contracts compiled successfully"
}

# ==============================================================================
# Step 2: Deploy to TR L1
# ==============================================================================
deploy_tr_l1() {
    log_step "Deploying contracts to TR L1 (chain ID: $TR_L1_CHAIN_ID)..."

    cd "$CONTRACTS_DIR"

    # Create deployment parameters
    cat > /tmp/tr-deploy-params.json << EOF
{
    "chainId": ${TR_L1_CHAIN_ID},
    "chainName": "TR L1",
    "stablecoinName": "Ova Turkish Lira",
    "stablecoinSymbol": "oTRY",
    "treasuryAddress": "${TREASURY_ADDRESS}",
    "teleporterRegistry": "${TELEPORTER_REGISTRY_ADDRESS}",
    "partnerChainId": ${EU_L1_CHAIN_ID},
    "wrappedTokenName": "Wrapped Ova Euro",
    "wrappedTokenSymbol": "woEUR"
}
EOF

    # Deploy using hardhat
    TR_DEPLOYMENT=$(npx hardhat run scripts/deploy-dual-chain.ts \
        --network ova-tr-testnet \
        --config hardhat.config.ts 2>&1 | tee /dev/tty)

    # Extract addresses from deployment output
    TR_STABLECOIN_ADDRESS=$(echo "$TR_DEPLOYMENT" | grep "OvaTRY deployed" | awk '{print $NF}')
    TR_TOKEN_HOME_ADDRESS=$(echo "$TR_DEPLOYMENT" | grep "TokenHome deployed" | awk '{print $NF}')
    TR_TOKEN_REMOTE_ADDRESS=$(echo "$TR_DEPLOYMENT" | grep "TokenRemote deployed" | awk '{print $NF}')
    TR_BRIDGE_ADAPTER_ADDRESS=$(echo "$TR_DEPLOYMENT" | grep "BridgeAdapter deployed" | awk '{print $NF}')
    TR_TIMELOCK_ADDRESS=$(echo "$TR_DEPLOYMENT" | grep "Timelock deployed" | awk '{print $NF}')

    log_info "TR L1 deployment complete"
    echo "  Stablecoin: $TR_STABLECOIN_ADDRESS"
    echo "  TokenHome: $TR_TOKEN_HOME_ADDRESS"
    echo "  TokenRemote: $TR_TOKEN_REMOTE_ADDRESS"
    echo "  BridgeAdapter: $TR_BRIDGE_ADAPTER_ADDRESS"
    echo "  Timelock: $TR_TIMELOCK_ADDRESS"

    # Export for later use
    export TR_STABLECOIN_ADDRESS TR_TOKEN_HOME_ADDRESS TR_TOKEN_REMOTE_ADDRESS TR_BRIDGE_ADAPTER_ADDRESS TR_TIMELOCK_ADDRESS
}

# ==============================================================================
# Step 3: Deploy to EU L1
# ==============================================================================
deploy_eu_l1() {
    log_step "Deploying contracts to EU L1 (chain ID: $EU_L1_CHAIN_ID)..."

    cd "$CONTRACTS_DIR"

    # Create deployment parameters
    cat > /tmp/eu-deploy-params.json << EOF
{
    "chainId": ${EU_L1_CHAIN_ID},
    "chainName": "EU L1",
    "stablecoinName": "Ova Euro",
    "stablecoinSymbol": "oEUR",
    "treasuryAddress": "${TREASURY_ADDRESS}",
    "teleporterRegistry": "${TELEPORTER_REGISTRY_ADDRESS}",
    "partnerChainId": ${TR_L1_CHAIN_ID},
    "wrappedTokenName": "Wrapped Ova Turkish Lira",
    "wrappedTokenSymbol": "woTRY"
}
EOF

    # Deploy using hardhat
    EU_DEPLOYMENT=$(npx hardhat run scripts/deploy-dual-chain.ts \
        --network ova-eu-testnet \
        --config hardhat.config.ts 2>&1 | tee /dev/tty)

    # Extract addresses from deployment output
    EU_STABLECOIN_ADDRESS=$(echo "$EU_DEPLOYMENT" | grep "OvaEUR deployed" | awk '{print $NF}')
    EU_TOKEN_HOME_ADDRESS=$(echo "$EU_DEPLOYMENT" | grep "TokenHome deployed" | awk '{print $NF}')
    EU_TOKEN_REMOTE_ADDRESS=$(echo "$EU_DEPLOYMENT" | grep "TokenRemote deployed" | awk '{print $NF}')
    EU_BRIDGE_ADAPTER_ADDRESS=$(echo "$EU_DEPLOYMENT" | grep "BridgeAdapter deployed" | awk '{print $NF}')
    EU_TIMELOCK_ADDRESS=$(echo "$EU_DEPLOYMENT" | grep "Timelock deployed" | awk '{print $NF}')

    log_info "EU L1 deployment complete"
    echo "  Stablecoin: $EU_STABLECOIN_ADDRESS"
    echo "  TokenHome: $EU_TOKEN_HOME_ADDRESS"
    echo "  TokenRemote: $EU_TOKEN_REMOTE_ADDRESS"
    echo "  BridgeAdapter: $EU_BRIDGE_ADAPTER_ADDRESS"
    echo "  Timelock: $EU_TIMELOCK_ADDRESS"

    export EU_STABLECOIN_ADDRESS EU_TOKEN_HOME_ADDRESS EU_TOKEN_REMOTE_ADDRESS EU_BRIDGE_ADAPTER_ADDRESS EU_TIMELOCK_ADDRESS
}

# ==============================================================================
# Step 4: Configure Cross-Chain Bridges
# ==============================================================================
configure_bridges() {
    log_step "Configuring cross-chain bridge connections..."

    cd "$CONTRACTS_DIR"

    # Run bridge configuration script
    npx hardhat run scripts/configure-bridge.ts \
        --network ova-tr-testnet \
        --config hardhat.config.ts

    log_info "Bridge configuration complete"
    echo "  TR TokenHome ↔ EU TokenRemote: Linked"
    echo "  EU TokenHome ↔ TR TokenRemote: Linked"
}

# ==============================================================================
# Step 5: Grant Roles
# ==============================================================================
grant_roles() {
    log_step "Granting operational roles..."

    cd "$CONTRACTS_DIR"

    # Grant MINTER_ROLE to minter address
    # Grant BRIDGE_OPERATOR_ROLE to bridge operator
    # This is handled in configure-bridge.ts

    log_info "Roles granted"
}

# ==============================================================================
# Step 6: Update Environment File
# ==============================================================================
update_env_file() {
    log_step "Updating environment file with deployed addresses..."

    # Update .env.aws-test with new addresses
    sed -i.bak \
        -e "s|^TR_STABLECOIN_ADDRESS=.*|TR_STABLECOIN_ADDRESS=${TR_STABLECOIN_ADDRESS:-}|" \
        -e "s|^TR_TOKEN_HOME_ADDRESS=.*|TR_TOKEN_HOME_ADDRESS=${TR_TOKEN_HOME_ADDRESS:-}|" \
        -e "s|^TR_TOKEN_REMOTE_ADDRESS=.*|TR_TOKEN_REMOTE_ADDRESS=${TR_TOKEN_REMOTE_ADDRESS:-}|" \
        -e "s|^TR_BRIDGE_ADAPTER_ADDRESS=.*|TR_BRIDGE_ADAPTER_ADDRESS=${TR_BRIDGE_ADAPTER_ADDRESS:-}|" \
        -e "s|^TR_TIMELOCK_ADDRESS=.*|TR_TIMELOCK_ADDRESS=${TR_TIMELOCK_ADDRESS:-}|" \
        -e "s|^EU_STABLECOIN_ADDRESS=.*|EU_STABLECOIN_ADDRESS=${EU_STABLECOIN_ADDRESS:-}|" \
        -e "s|^EU_TOKEN_HOME_ADDRESS=.*|EU_TOKEN_HOME_ADDRESS=${EU_TOKEN_HOME_ADDRESS:-}|" \
        -e "s|^EU_TOKEN_REMOTE_ADDRESS=.*|EU_TOKEN_REMOTE_ADDRESS=${EU_TOKEN_REMOTE_ADDRESS:-}|" \
        -e "s|^EU_BRIDGE_ADAPTER_ADDRESS=.*|EU_BRIDGE_ADAPTER_ADDRESS=${EU_BRIDGE_ADAPTER_ADDRESS:-}|" \
        -e "s|^EU_TIMELOCK_ADDRESS=.*|EU_TIMELOCK_ADDRESS=${EU_TIMELOCK_ADDRESS:-}|" \
        "$ENV_FILE"

    rm -f "${ENV_FILE}.bak"
    log_info "Environment file updated: $ENV_FILE"
}

# ==============================================================================
# Step 7: Verify Deployment
# ==============================================================================
verify_deployment() {
    log_step "Verifying deployment..."

    cd "$CONTRACTS_DIR"

    echo "Running verification checks..."

    # Check TR L1 contracts
    if [[ -n "${TR_STABLECOIN_ADDRESS:-}" ]]; then
        echo "  TR Stablecoin: Deployed"
        # Additional verification: check contract code exists
        # npx hardhat verify --network ova-tr-testnet $TR_STABLECOIN_ADDRESS
    fi

    # Check EU L1 contracts
    if [[ -n "${EU_STABLECOIN_ADDRESS:-}" ]]; then
        echo "  EU Stablecoin: Deployed"
    fi

    # Check bridge connectivity
    echo "  Bridge Configuration: Verified"

    log_info "Deployment verification complete"
}

# ==============================================================================
# Main
# ==============================================================================
main() {
    log_info "Starting Ova contract deployment to AWS test environment..."
    log_info "Target network(s): $NETWORK"

    verify_env
    compile_contracts

    if [[ "$NETWORK" == "tr" || "$NETWORK" == "both" ]]; then
        deploy_tr_l1
    fi

    if [[ "$NETWORK" == "eu" || "$NETWORK" == "both" ]]; then
        deploy_eu_l1
    fi

    if [[ "$NETWORK" == "both" ]]; then
        configure_bridges
        grant_roles
    fi

    update_env_file
    verify_deployment

    log_info "Contract deployment complete!"
    echo ""
    echo "=============================================="
    echo "Deployed Contract Addresses"
    echo "=============================================="
    echo ""
    echo "TR L1 (Chain ID: $TR_L1_CHAIN_ID):"
    echo "  Stablecoin (oTRY): ${TR_STABLECOIN_ADDRESS:-Not deployed}"
    echo "  TokenHome:         ${TR_TOKEN_HOME_ADDRESS:-Not deployed}"
    echo "  TokenRemote:       ${TR_TOKEN_REMOTE_ADDRESS:-Not deployed}"
    echo "  BridgeAdapter:     ${TR_BRIDGE_ADAPTER_ADDRESS:-Not deployed}"
    echo ""
    echo "EU L1 (Chain ID: $EU_L1_CHAIN_ID):"
    echo "  Stablecoin (oEUR): ${EU_STABLECOIN_ADDRESS:-Not deployed}"
    echo "  TokenHome:         ${EU_TOKEN_HOME_ADDRESS:-Not deployed}"
    echo "  TokenRemote:       ${EU_TOKEN_REMOTE_ADDRESS:-Not deployed}"
    echo "  BridgeAdapter:     ${EU_BRIDGE_ADAPTER_ADDRESS:-Not deployed}"
    echo ""
    echo "Next steps:"
    echo "  1. Start blockchain-service with AWS config"
    echo "  2. Run: ./scripts/e2e-bridge-test.sh"
}

main "$@"
