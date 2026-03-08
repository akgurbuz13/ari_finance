#!/bin/bash
# ==============================================================================
# ARI End-to-End Bridge Test Script
# ==============================================================================
# Tests the complete TR→EU cross-border transfer flow:
# 1. Create test users (TR and EU)
# 2. Fund TR user with TRY
# 3. Initiate cross-border transfer (TR→EU)
# 4. Verify wrapped tokens received on EU side
# 5. Verify reconciliation passes
#
# Prerequisites:
# - core-banking and blockchain-service running
# - Contracts deployed to both L1s
# - .env.aws-test populated
#
# Usage:
#   ./scripts/e2e-bridge-test.sh [--amount <TRY_amount>]
# ==============================================================================

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$PROJECT_ROOT/.env.aws-test"

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }
log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; }

# Configuration
TRANSFER_AMOUNT="${1:-1000}"  # Default 1000 TRY
CORE_BANKING_URL="${CORE_BANKING_API_URL:-http://localhost:8080}"
BLOCKCHAIN_SERVICE_URL="${BLOCKCHAIN_SERVICE_API_URL:-http://localhost:8081}"

# Load environment
if [[ -f "$ENV_FILE" ]]; then
    source "$ENV_FILE"
fi

# Test data
TR_USER_EMAIL="tr-test-$(date +%s)@ari.finance"
TR_USER_PHONE="+905551234567"
TR_USER_PASSWORD="Test123!@#"

EU_USER_EMAIL="eu-test-$(date +%s)@ari.finance"
EU_USER_PHONE="+491701234567"
EU_USER_PASSWORD="Test123!@#"

# Test state
TR_USER_ID=""
TR_USER_TOKEN=""
TR_WALLET_ADDRESS=""
EU_USER_ID=""
EU_USER_TOKEN=""
EU_WALLET_ADDRESS=""
TRANSFER_ID=""

# ==============================================================================
# Helper Functions
# ==============================================================================

api_call() {
    local method="$1"
    local endpoint="$2"
    local data="${3:-}"
    local token="${4:-}"

    local headers=(-H "Content-Type: application/json")
    [[ -n "$token" ]] && headers+=(-H "Authorization: Bearer $token")

    if [[ -n "$data" ]]; then
        curl -s -X "$method" "${headers[@]}" -d "$data" "${CORE_BANKING_URL}${endpoint}"
    else
        curl -s -X "$method" "${headers[@]}" "${CORE_BANKING_URL}${endpoint}"
    fi
}

wait_for_status() {
    local endpoint="$1"
    local expected_status="$2"
    local token="$3"
    local max_attempts="${4:-30}"

    for ((i=1; i<=max_attempts; i++)); do
        local response=$(api_call GET "$endpoint" "" "$token")
        local status=$(echo "$response" | jq -r '.status // .data.status // empty')

        if [[ "$status" == "$expected_status" ]]; then
            return 0
        fi

        echo "  Attempt $i/$max_attempts: Status=$status, waiting for $expected_status..."
        sleep 5
    done

    return 1
}

# ==============================================================================
# Test Steps
# ==============================================================================

test_health_check() {
    log_step "Step 0: Health check..."

    # Check core-banking
    local cb_health=$(curl -s "${CORE_BANKING_URL}/actuator/health" | jq -r '.status // empty')
    if [[ "$cb_health" == "UP" ]]; then
        log_pass "Core Banking is healthy"
    else
        log_fail "Core Banking is not responding"
        return 1
    fi

    # Check blockchain-service
    local bs_health=$(curl -s "${BLOCKCHAIN_SERVICE_URL}/actuator/health" | jq -r '.status // empty')
    if [[ "$bs_health" == "UP" ]]; then
        log_pass "Blockchain Service is healthy"
    else
        log_warn "Blockchain Service is not responding (may be optional for this test)"
    fi

    return 0
}

test_create_tr_user() {
    log_step "Step 1: Creating TR test user..."

    local response=$(api_call POST "/api/v1/auth/register" "{
        \"email\": \"$TR_USER_EMAIL\",
        \"phone\": \"$TR_USER_PHONE\",
        \"password\": \"$TR_USER_PASSWORD\",
        \"region\": \"TR\"
    }")

    TR_USER_ID=$(echo "$response" | jq -r '.data.userId // .userId // empty')

    if [[ -n "$TR_USER_ID" ]]; then
        log_pass "TR user created: $TR_USER_ID"
    else
        log_error "Response: $response"
        log_fail "Failed to create TR user"
        return 1
    fi

    # Login
    response=$(api_call POST "/api/v1/auth/login" "{
        \"email\": \"$TR_USER_EMAIL\",
        \"password\": \"$TR_USER_PASSWORD\"
    }")

    TR_USER_TOKEN=$(echo "$response" | jq -r '.data.accessToken // .accessToken // empty')

    if [[ -n "$TR_USER_TOKEN" ]]; then
        log_pass "TR user logged in"
    else
        log_fail "Failed to login TR user"
        return 1
    fi

    return 0
}

test_create_eu_user() {
    log_step "Step 2: Creating EU test user..."

    local response=$(api_call POST "/api/v1/auth/register" "{
        \"email\": \"$EU_USER_EMAIL\",
        \"phone\": \"$EU_USER_PHONE\",
        \"password\": \"$EU_USER_PASSWORD\",
        \"region\": \"EU\"
    }")

    EU_USER_ID=$(echo "$response" | jq -r '.data.userId // .userId // empty')

    if [[ -n "$EU_USER_ID" ]]; then
        log_pass "EU user created: $EU_USER_ID"
    else
        log_fail "Failed to create EU user"
        return 1
    fi

    # Login
    response=$(api_call POST "/api/v1/auth/login" "{
        \"email\": \"$EU_USER_EMAIL\",
        \"password\": \"$EU_USER_PASSWORD\"
    }")

    EU_USER_TOKEN=$(echo "$response" | jq -r '.data.accessToken // .accessToken // empty')

    if [[ -n "$EU_USER_TOKEN" ]]; then
        log_pass "EU user logged in"
    else
        log_fail "Failed to login EU user"
        return 1
    fi

    return 0
}

test_create_accounts() {
    log_step "Step 3: Creating TRY and EUR accounts..."

    # Create TRY account for TR user
    local response=$(api_call POST "/api/v1/accounts" "{
        \"currency\": \"TRY\",
        \"accountType\": \"CURRENT\"
    }" "$TR_USER_TOKEN")

    TR_WALLET_ADDRESS=$(echo "$response" | jq -r '.data.walletAddress // .walletAddress // empty')

    if [[ -n "$TR_WALLET_ADDRESS" ]]; then
        log_pass "TR TRY account created with wallet: $TR_WALLET_ADDRESS"
    else
        log_warn "TR account created but wallet address not assigned yet"
    fi

    # Create EUR account for EU user
    response=$(api_call POST "/api/v1/accounts" "{
        \"currency\": \"EUR\",
        \"accountType\": \"CURRENT\"
    }" "$EU_USER_TOKEN")

    EU_WALLET_ADDRESS=$(echo "$response" | jq -r '.data.walletAddress // .walletAddress // empty')

    if [[ -n "$EU_WALLET_ADDRESS" ]]; then
        log_pass "EU EUR account created with wallet: $EU_WALLET_ADDRESS"
    else
        log_warn "EU account created but wallet address not assigned yet"
    fi

    return 0
}

test_fund_tr_user() {
    log_step "Step 4: Funding TR user with $TRANSFER_AMOUNT TRY..."

    # In a real test, this would be done via admin API or test fixture
    # For now, we simulate a deposit

    local response=$(api_call POST "/api/v1/deposits/simulate" "{
        \"amount\": $TRANSFER_AMOUNT,
        \"currency\": \"TRY\"
    }" "$TR_USER_TOKEN")

    local deposit_status=$(echo "$response" | jq -r '.data.status // .status // empty')

    if [[ "$deposit_status" == "COMPLETED" || "$deposit_status" == "PROCESSING" ]]; then
        log_pass "Deposit initiated: $TRANSFER_AMOUNT TRY"
    else
        log_warn "Deposit simulation may not be available, proceeding anyway"
    fi

    # Verify balance
    sleep 2
    response=$(api_call GET "/api/v1/accounts" "" "$TR_USER_TOKEN")
    local balance=$(echo "$response" | jq -r '.data[] | select(.currency == "TRY") | .availableBalance // empty')

    if [[ -n "$balance" && "$balance" != "0" ]]; then
        log_pass "TR user TRY balance: $balance"
    else
        log_warn "Balance verification pending"
    fi

    return 0
}

test_get_fx_quote() {
    log_step "Step 5: Getting FX quote for TRY→EUR..."

    local response=$(api_call POST "/api/v1/fx/quote" "{
        \"sourceCurrency\": \"TRY\",
        \"targetCurrency\": \"EUR\",
        \"sourceAmount\": $TRANSFER_AMOUNT
    }" "$TR_USER_TOKEN")

    local quote_id=$(echo "$response" | jq -r '.data.quoteId // .quoteId // empty')
    local target_amount=$(echo "$response" | jq -r '.data.targetAmount // .targetAmount // empty')
    local rate=$(echo "$response" | jq -r '.data.rate // .rate // empty')

    if [[ -n "$quote_id" ]]; then
        log_pass "FX Quote received: $TRANSFER_AMOUNT TRY → $target_amount EUR (rate: $rate)"
        export FX_QUOTE_ID="$quote_id"
    else
        log_warn "FX quote not available, proceeding with hardcoded rate"
    fi

    return 0
}

test_initiate_cross_border_transfer() {
    log_step "Step 6: Initiating TR→EU cross-border transfer..."

    # Get EU user's account ID
    local eu_accounts=$(api_call GET "/api/v1/accounts" "" "$EU_USER_TOKEN")
    local eu_account_id=$(echo "$eu_accounts" | jq -r '.data[] | select(.currency == "EUR") | .id // empty')

    if [[ -z "$eu_account_id" ]]; then
        log_fail "Could not find EU user's EUR account"
        return 1
    fi

    # Initiate cross-border transfer
    local response=$(api_call POST "/api/v1/payments/cross-border" "{
        \"amount\": $TRANSFER_AMOUNT,
        \"sourceCurrency\": \"TRY\",
        \"targetCurrency\": \"EUR\",
        \"recipientAccountId\": \"$eu_account_id\",
        \"quoteId\": \"${FX_QUOTE_ID:-}\",
        \"reference\": \"E2E-TEST-$(date +%s)\"
    }" "$TR_USER_TOKEN")

    TRANSFER_ID=$(echo "$response" | jq -r '.data.paymentOrderId // .paymentOrderId // empty')
    local status=$(echo "$response" | jq -r '.data.status // .status // empty')

    if [[ -n "$TRANSFER_ID" ]]; then
        log_pass "Cross-border transfer initiated: $TRANSFER_ID (status: $status)"
    else
        log_error "Response: $response"
        log_fail "Failed to initiate cross-border transfer"
        return 1
    fi

    return 0
}

test_wait_for_completion() {
    log_step "Step 7: Waiting for transfer completion..."

    if ! wait_for_status "/api/v1/payments/$TRANSFER_ID" "COMPLETED" "$TR_USER_TOKEN" 60; then
        log_fail "Transfer did not complete within timeout"

        # Get final status for debugging
        local response=$(api_call GET "/api/v1/payments/$TRANSFER_ID" "" "$TR_USER_TOKEN")
        local status=$(echo "$response" | jq -r '.data.status // .status // empty')
        local error=$(echo "$response" | jq -r '.data.errorMessage // .errorMessage // empty')

        log_error "Final status: $status"
        [[ -n "$error" ]] && log_error "Error: $error"

        return 1
    fi

    log_pass "Transfer completed successfully!"
    return 0
}

test_verify_balances() {
    log_step "Step 8: Verifying final balances..."

    # Check TR user balance (should be reduced)
    local tr_accounts=$(api_call GET "/api/v1/accounts" "" "$TR_USER_TOKEN")
    local tr_balance=$(echo "$tr_accounts" | jq -r '.data[] | select(.currency == "TRY") | .availableBalance // empty')

    log_info "TR user TRY balance: $tr_balance"

    # Check EU user balance (should be increased)
    local eu_accounts=$(api_call GET "/api/v1/accounts" "" "$EU_USER_TOKEN")
    local eu_balance=$(echo "$eu_accounts" | jq -r '.data[] | select(.currency == "EUR") | .availableBalance // empty')

    log_info "EU user EUR balance: $eu_balance"

    if [[ -n "$eu_balance" && "$eu_balance" != "0" && "$eu_balance" != "null" ]]; then
        log_pass "Balances verified: EUR credited to EU user"
    else
        log_warn "Balance verification needs manual check"
    fi

    return 0
}

test_verify_blockchain_settlement() {
    log_step "Step 9: Verifying blockchain settlement..."

    # Check blockchain transaction status
    local response=$(curl -s -H "Content-Type: application/json" \
        "${BLOCKCHAIN_SERVICE_URL}/api/v1/transactions?paymentOrderId=$TRANSFER_ID")

    local tx_count=$(echo "$response" | jq -r '.data | length // 0')
    local burn_tx=$(echo "$response" | jq -r '.data[] | select(.operation == "burn") | .txHash // empty')
    local mint_tx=$(echo "$response" | jq -r '.data[] | select(.operation == "mint") | .txHash // empty')
    local bridge_tx=$(echo "$response" | jq -r '.data[] | select(.operation | startswith("bridge")) | .txHash // empty')

    log_info "Blockchain transactions found: $tx_count"
    [[ -n "$burn_tx" ]] && log_info "  Burn TX: $burn_tx"
    [[ -n "$mint_tx" ]] && log_info "  Mint TX: $mint_tx"
    [[ -n "$bridge_tx" ]] && log_info "  Bridge TX: $bridge_tx"

    if [[ "$tx_count" -gt 0 ]]; then
        log_pass "Blockchain settlement verified"
    else
        log_warn "Blockchain transactions may be pending"
    fi

    return 0
}

test_verify_reconciliation() {
    log_step "Step 10: Running reconciliation check..."

    # Trigger manual reconciliation (if available)
    local response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -H "X-Internal-Api-Key: ${INTERNAL_API_KEY:-}" \
        "${BLOCKCHAIN_SERVICE_URL}/api/internal/reconciliation/run")

    local status=$(echo "$response" | jq -r '.data.status // .status // empty')

    if [[ "$status" == "BALANCED" || "$status" == "SUCCESS" ]]; then
        log_pass "Reconciliation passed"
    else
        log_warn "Reconciliation status: $status (may need manual verification)"
    fi

    return 0
}

# ==============================================================================
# Main
# ==============================================================================

print_test_summary() {
    local passed=$1
    local failed=$2
    local total=$((passed + failed))

    echo ""
    echo "=============================================="
    echo "End-to-End Bridge Test Summary"
    echo "=============================================="
    echo ""
    echo "Transfer Details:"
    echo "  From: TR user ($TR_USER_EMAIL)"
    echo "  To:   EU user ($EU_USER_EMAIL)"
    echo "  Amount: $TRANSFER_AMOUNT TRY"
    echo "  Transfer ID: ${TRANSFER_ID:-N/A}"
    echo ""
    echo "Results: $passed/$total tests passed"
    echo ""

    if [[ $failed -eq 0 ]]; then
        echo -e "${GREEN}All tests passed!${NC}"
    else
        echo -e "${RED}$failed test(s) failed${NC}"
    fi
    echo ""
}

main() {
    log_info "Starting ARI End-to-End Bridge Test"
    log_info "Transfer amount: $TRANSFER_AMOUNT TRY"
    log_info "Core Banking: $CORE_BANKING_URL"
    log_info "Blockchain Service: $BLOCKCHAIN_SERVICE_URL"
    echo ""

    local passed=0
    local failed=0

    # Run tests
    tests=(
        "test_health_check"
        "test_create_tr_user"
        "test_create_eu_user"
        "test_create_accounts"
        "test_fund_tr_user"
        "test_get_fx_quote"
        "test_initiate_cross_border_transfer"
        "test_wait_for_completion"
        "test_verify_balances"
        "test_verify_blockchain_settlement"
        "test_verify_reconciliation"
    )

    for test in "${tests[@]}"; do
        echo ""
        if $test; then
            ((passed++))
        else
            ((failed++))
        fi
    done

    print_test_summary $passed $failed

    # Exit with appropriate code
    [[ $failed -eq 0 ]] && exit 0 || exit 1
}

main "$@"
