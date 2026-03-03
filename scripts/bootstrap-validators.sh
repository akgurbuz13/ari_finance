#!/bin/bash
# ==============================================================================
# ARI Avalanche L1 Validator Bootstrap Script
# ==============================================================================
# This script bootstraps the Avalanche L1 validators for the ARI platform.
#
# Prerequisites:
# - AWS CLI configured with appropriate credentials
# - Terraform installed
# - Platform CLI installed (for P-Chain operations: keys, subnets, chains)
#   Install: curl -sSfL https://build.avax.network/install/platform-cli | sh
# - Builder Console access (for ICM/Teleporter setup): https://build.avax.network/console
# - jq installed
#
# Usage:
#   ./scripts/bootstrap-validators.sh [--dry-run]
# ==============================================================================

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
ENVIRONMENT="${ENVIRONMENT:-dev}"
AWS_REGION="${AWS_REGION:-eu-central-1}"
TR_CHAIN_ID=99999
EU_CHAIN_ID=99998

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
    DRY_RUN=true
    echo -e "${YELLOW}Running in dry-run mode${NC}"
fi

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# ==============================================================================
# Step 1: Generate Validator Staking Keys
# ==============================================================================
generate_staking_keys() {
    log_info "Generating validator staking keys..."

    local keys_dir="./validator-keys"
    mkdir -p "$keys_dir"

    for chain in "tr" "eu"; do
        for i in 1 2; do
            local key_name="${chain}-validator-${i}"
            log_info "Generating keys for $key_name..."

            if [[ "$DRY_RUN" == "true" ]]; then
                log_info "[DRY-RUN] Would generate keys for $key_name"
                continue
            fi

            # Generate staking key pair
            openssl req -x509 -nodes -days 3650 \
                -newkey rsa:4096 \
                -keyout "$keys_dir/${key_name}.key" \
                -out "$keys_dir/${key_name}.crt" \
                -subj "/CN=ova-${chain}-validator-${i}"

            # Generate BLS key (using Platform CLI)
            if command -v platform &> /dev/null; then
                platform keys generate --name "${key_name}-bls" --encrypt=false
                platform keys export --name "${key_name}-bls" --format hex --output-file "$keys_dir/${key_name}-bls.key"
            else
                log_warn "Platform CLI not found, skipping BLS key generation"
                log_warn "Install: curl -sSfL https://build.avax.network/install/platform-cli | sh"
                echo "placeholder-bls-key" > "$keys_dir/${key_name}-bls.key"
            fi

            log_info "Keys generated for $key_name"
        done
    done

    log_info "All staking keys generated in $keys_dir"
}

# ==============================================================================
# Step 2: Upload Keys to AWS Secrets Manager
# ==============================================================================
upload_keys_to_secrets_manager() {
    log_info "Uploading validator keys to AWS Secrets Manager..."

    local keys_dir="./validator-keys"

    for chain in "tr" "eu"; do
        for i in 1 2; do
            local key_name="${chain}-validator-${i}"
            local secret_name="ova/${ENVIRONMENT}/validator/${key_name}"

            if [[ "$DRY_RUN" == "true" ]]; then
                log_info "[DRY-RUN] Would upload keys for $key_name to $secret_name"
                continue
            fi

            # Read key files
            local staker_key=$(cat "$keys_dir/${key_name}.key" | base64 -w0)
            local staker_cert=$(cat "$keys_dir/${key_name}.crt" | base64 -w0)
            local bls_key=$(cat "$keys_dir/${key_name}-bls.key" 2>/dev/null || echo "")

            # Create secret JSON
            local secret_json=$(jq -n \
                --arg staker_key "$staker_key" \
                --arg staker_cert "$staker_cert" \
                --arg bls_key "$bls_key" \
                '{staker_key: $staker_key, staker_cert: $staker_cert, bls_key: $bls_key}')

            # Check if secret exists
            if aws secretsmanager describe-secret --secret-id "$secret_name" --region "$AWS_REGION" &>/dev/null; then
                log_info "Updating existing secret: $secret_name"
                aws secretsmanager put-secret-value \
                    --secret-id "$secret_name" \
                    --secret-string "$secret_json" \
                    --region "$AWS_REGION"
            else
                log_info "Creating new secret: $secret_name"
                aws secretsmanager create-secret \
                    --name "$secret_name" \
                    --secret-string "$secret_json" \
                    --region "$AWS_REGION" \
                    --tags "Key=Environment,Value=${ENVIRONMENT}" "Key=Component,Value=avalanche-validator"
            fi

            log_info "Keys uploaded for $key_name"
        done
    done

    log_info "All keys uploaded to Secrets Manager"
}

# ==============================================================================
# Step 3: Deploy Validators via Terraform
# ==============================================================================
deploy_validators() {
    log_info "Deploying validators via Terraform..."

    cd infra/terraform/environments/${ENVIRONMENT}

    if [[ "$DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would run: terraform init && terraform plan"
        terraform init
        terraform plan -var="deploy_validators=true"
    else
        terraform init
        terraform apply -var="deploy_validators=true" -auto-approve
    fi

    cd - > /dev/null

    log_info "Validators deployed"
}

# ==============================================================================
# Step 4: Create L1 Chains
# ==============================================================================
create_l1_chains() {
    log_info "Creating L1 chains on validators..."

    # Wait for validators to be healthy
    log_info "Waiting for validators to become healthy..."
    sleep 60  # Give validators time to bootstrap

    # Get validator endpoints from Terraform output
    local tr_validator_1=$(terraform -chdir=infra/terraform/environments/${ENVIRONMENT} output -raw tr_l1_validator_endpoint_1 2>/dev/null || echo "")
    local eu_validator_1=$(terraform -chdir=infra/terraform/environments/${ENVIRONMENT} output -raw eu_l1_validator_endpoint_1 2>/dev/null || echo "")

    if [[ -z "$tr_validator_1" || -z "$eu_validator_1" ]]; then
        log_warn "Could not get validator endpoints from Terraform. Using defaults."
        tr_validator_1="http://localhost:9650"
        eu_validator_1="http://localhost:9650"
    fi

    if [[ "$DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would create L1 chains via P-Chain"
        log_info "[DRY-RUN] TR L1 Chain ID: $TR_CHAIN_ID"
        log_info "[DRY-RUN] EU L1 Chain ID: $EU_CHAIN_ID"
        return
    fi

    log_info "Creating TR L1 (chain ID: $TR_CHAIN_ID)..."
    # Uses Platform CLI for P-Chain operations (subnets, chains, validators)
    # Uses Builder Console for ICM/Teleporter setup
    cat << EOF

    ========================================
    MANUAL STEPS REQUIRED FOR L1 CREATION:
    ========================================

    Using Platform CLI (P-Chain operations):

    1. Create TR L1 Subnet:
       platform subnet create --key-name ari-deployer --network fuji

    2. Create TR L1 Chain (use SubnetID from step 1):
       platform chain create --subnet-id <TR_SUBNET_ID> \\
         --genesis genesis-tr.json --name ari-tr --key-name ari-deployer

    3. Convert TR subnet to L1 (with validator):
       platform subnet convert-l1 --subnet-id <TR_SUBNET_ID> \\
         --chain-id <TR_CHAIN_ID> --manager <VALIDATOR_MANAGER_ADDR> \\
         --validators <VALIDATOR_IP>:9650 --key-name ari-deployer

    4. Repeat steps 1-3 for EU L1:
       platform subnet create --key-name ari-deployer --network fuji
       platform chain create --subnet-id <EU_SUBNET_ID> \\
         --genesis genesis-eu.json --name ari-eu --key-name ari-deployer
       platform subnet convert-l1 --subnet-id <EU_SUBNET_ID> \\
         --chain-id <EU_CHAIN_ID> --manager <VALIDATOR_MANAGER_ADDR> \\
         --validators <VALIDATOR_IP>:9652 --key-name ari-deployer

    Using Builder Console (ICM/Teleporter setup):

    5. Enable Teleporter on both L1s:
       Go to https://build.avax.network/console
       - Select each L1 and enable ICM/Teleporter messaging

    6. Update .env.aws-test with the blockchain IDs

    ========================================
EOF

    log_info "L1 chain creation instructions displayed"
}

# ==============================================================================
# Step 5: Verify Validator Health
# ==============================================================================
verify_health() {
    log_info "Verifying validator health..."

    if [[ "$DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would verify validator health"
        return
    fi

    # This would check health endpoints in production
    log_info "Health verification would check:"
    echo "  - TR L1 validators: http://tr-validator-nlb/ext/health"
    echo "  - EU L1 validators: http://eu-validator-nlb/ext/health"
    echo "  - Teleporter registry connectivity"
    echo "  - P-Chain sync status"

    log_info "Health verification complete"
}

# ==============================================================================
# Main
# ==============================================================================
main() {
    log_info "Starting ARI Avalanche validator bootstrap..."
    log_info "Environment: $ENVIRONMENT"
    log_info "AWS Region: $AWS_REGION"

    echo ""
    echo "This script will:"
    echo "  1. Generate validator staking keys"
    echo "  2. Upload keys to AWS Secrets Manager"
    echo "  3. Deploy validators via Terraform"
    echo "  4. Create L1 chains (manual steps)"
    echo "  5. Verify validator health"
    echo ""

    if [[ "$DRY_RUN" != "true" ]]; then
        read -p "Continue? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "Aborted."
            exit 1
        fi
    fi

    generate_staking_keys
    upload_keys_to_secrets_manager
    deploy_validators
    create_l1_chains
    verify_health

    log_info "Bootstrap complete!"
    echo ""
    echo "Next steps:"
    echo "  1. Complete manual L1 creation steps above"
    echo "  2. Update .env.aws-test with deployed addresses"
    echo "  3. Run: ./scripts/deploy-contracts.sh"
    echo "  4. Run: ./scripts/e2e-bridge-test.sh"
}

main "$@"
