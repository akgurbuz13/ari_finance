#!/bin/bash
# Setup demo data for ARI Platform on Fuji testnet
# Creates demo user, KYC, accounts, and on-chain allowlisting
#
# Prerequisites:
#   1. docker compose up -d (PostgreSQL + Redis)
#   2. core-banking running on :8080
#   3. blockchain-service running on :8081
#   4. Fuji L1s accessible

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
STATE_FILE="$PROJECT_DIR/.demo-state"

# Load Fuji config
source "$PROJECT_DIR/.env.fuji"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

BASE_URL="http://localhost:8080/api/v1"

# Demo user config
DEMO_EMAIL="demo@ari.finance"
DEMO_PASSWORD="AriDemo2026!"
DEMO_PHONE="+905551234567"
DEMO_REGION="TR"

echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${CYAN}║     ARI Platform - Demo Data Setup       ║${NC}"
echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════╝${NC}"
echo ""

# ── Step 1: Health Check ─────────────────────────────────────────────────────
echo -e "${YELLOW}[1/7] Checking service health...${NC}"
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/../actuator/health" 2>/dev/null || echo "000")
if [ "$HEALTH" != "200" ]; then
    echo -e "${RED}  ✗ core-banking not running (HTTP $HEALTH)${NC}"
    echo "  Start with: ./scripts/run-fuji.sh core-banking"
    exit 1
fi
echo -e "${GREEN}  ✓ core-banking healthy${NC}"

# Check L1s
TR_RPC="$TR_L1_RPC_URL"
EU_RPC="$EU_L1_RPC_URL"

TR_CHECK=$(curl -s --max-time 3 -X POST -H "Content-Type: application/json" \
    --data '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' \
    "$TR_RPC" 2>/dev/null || echo "")
if [ -z "$TR_CHECK" ]; then
    echo -e "${RED}  ✗ TR L1 not reachable at $TR_RPC${NC}"
    exit 1
fi
echo -e "${GREEN}  ✓ TR L1 (chain $TR_L1_CHAIN_ID) reachable${NC}"

EU_CHECK=$(curl -s --max-time 3 -X POST -H "Content-Type: application/json" \
    --data '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' \
    "$EU_RPC" 2>/dev/null || echo "")
if [ -z "$EU_CHECK" ]; then
    echo -e "${RED}  ✗ EU L1 not reachable at $EU_RPC${NC}"
    exit 1
fi
echo -e "${GREEN}  ✓ EU L1 (chain $EU_L1_CHAIN_ID) reachable${NC}"
echo ""

# ── Step 2: Register or Login Demo User ──────────────────────────────────────
echo -e "${YELLOW}[2/7] Registering demo user...${NC}"

SIGNUP_RESP=$(curl -s -X POST "$BASE_URL/auth/signup" \
    -H "Content-Type: application/json" \
    -d "{
        \"email\": \"$DEMO_EMAIL\",
        \"phone\": \"$DEMO_PHONE\",
        \"password\": \"$DEMO_PASSWORD\",
        \"region\": \"$DEMO_REGION\"
    }" 2>/dev/null)

TOKEN=$(echo "$SIGNUP_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null || echo "")
USER_ID=$(echo "$SIGNUP_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('userId',''))" 2>/dev/null || echo "")

if [ -z "$TOKEN" ]; then
    echo -e "  User may exist, trying login..."
    LOGIN_RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\": \"$DEMO_EMAIL\", \"password\": \"$DEMO_PASSWORD\"}" 2>/dev/null)
    TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null || echo "")
    USER_ID=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('userId',''))" 2>/dev/null || echo "")
fi

if [ -z "$TOKEN" ] || [ -z "$USER_ID" ]; then
    echo -e "${RED}  ✗ Failed to authenticate demo user${NC}"
    echo "  Signup response: $SIGNUP_RESP"
    exit 1
fi

echo -e "${GREEN}  ✓ Authenticated as $DEMO_EMAIL${NC}"
echo -e "    User ID: $USER_ID"
echo ""

# ── Step 3: KYC Verification ─────────────────────────────────────────────────
echo -e "${YELLOW}[3/7] Running KYC verification...${NC}"

KYC_STATUS=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/kyc/status" 2>/dev/null)
KYC_CURRENT=$(echo "$KYC_STATUS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || echo "")

if [ "$KYC_CURRENT" = "approved" ] || [ "$KYC_CURRENT" = "APPROVED" ]; then
    echo -e "${GREEN}  ✓ KYC already approved${NC}"
else
    KYC_RESP=$(curl -s -X POST "$BASE_URL/kyc/verify" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d '{"provider": "simulated", "level": "basic"}' 2>/dev/null)
    echo -e "${GREEN}  ✓ KYC verification initiated (simulated = auto-approved)${NC}"
fi
echo ""

# ── Step 4: Create Accounts ──────────────────────────────────────────────────
echo -e "${YELLOW}[4/7] Creating accounts...${NC}"

# Get existing accounts
ACCOUNTS=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/accounts" 2>/dev/null)

# Check for existing TRY account
TRY_ACCOUNT_ID=$(echo "$ACCOUNTS" | python3 -c "
import sys, json
accounts = json.load(sys.stdin)
for a in accounts:
    if a['currency'] == 'TRY':
        print(a['id'])
        break
" 2>/dev/null || echo "")

# Check for existing EUR account
EUR_ACCOUNT_ID=$(echo "$ACCOUNTS" | python3 -c "
import sys, json
accounts = json.load(sys.stdin)
for a in accounts:
    if a['currency'] == 'EUR':
        print(a['id'])
        break
" 2>/dev/null || echo "")

# Create TRY account if needed
if [ -z "$TRY_ACCOUNT_ID" ]; then
    CREATE_TRY=$(curl -s -X POST "$BASE_URL/accounts" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d '{"currency": "TRY"}' 2>/dev/null)
    TRY_ACCOUNT_ID=$(echo "$CREATE_TRY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
    echo -e "${GREEN}  ✓ Created TRY account: $TRY_ACCOUNT_ID${NC}"
else
    echo -e "${GREEN}  ✓ TRY account exists: $TRY_ACCOUNT_ID${NC}"
fi

# Create EUR account if needed
if [ -z "$EUR_ACCOUNT_ID" ]; then
    CREATE_EUR=$(curl -s -X POST "$BASE_URL/accounts" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d '{"currency": "EUR"}' 2>/dev/null)
    EUR_ACCOUNT_ID=$(echo "$CREATE_EUR" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
    echo -e "${GREEN}  ✓ Created EUR account: $EUR_ACCOUNT_ID${NC}"
else
    echo -e "${GREEN}  ✓ EUR account exists: $EUR_ACCOUNT_ID${NC}"
fi
echo ""

# ── Step 5: Get User's Custodial Wallet ──────────────────────────────────────
echo -e "${YELLOW}[5/7] Getting custodial wallet address...${NC}"

# The blockchain-service creates wallets on first mint. We need to trigger wallet creation
# by calling the blockchain-service wallet endpoint or let it happen naturally on mint.
# For now, we read it from the database after the first mint.
# Wallet is created during mint flow, so we'll capture it in the E2E script.

echo -e "${CYAN}  → Wallet will be created on first mint operation${NC}"
echo ""

# ── Step 6: Pre-fund User Account via Direct Ledger ──────────────────────────
echo -e "${YELLOW}[6/7] Pre-funding TRY account for demo...${NC}"

# Since banking rails are stubs, we insert a MintRequested outbox event directly
# This triggers: blockchain-service → create wallet → allowlist → mint ariTRY on-chain
MINT_AMOUNT=10000

# Check if the user already has a balance
TRY_BALANCE=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/accounts/$TRY_ACCOUNT_ID/balance" 2>/dev/null)
CURRENT_BALANCE=$(echo "$TRY_BALANCE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balance','0'))" 2>/dev/null || echo "0")

if [ "$CURRENT_BALANCE" != "0" ] && [ "$CURRENT_BALANCE" != "0.00000000" ]; then
    echo -e "${GREEN}  ✓ Account already funded: $CURRENT_BALANCE TRY${NC}"
else
    echo -e "  Inserting MintRequested outbox event for $MINT_AMOUNT TRY..."

    # First credit the ledger (so balance shows in UI)
    # Create a deposit-style ledger entry directly
    docker exec ova-postgres psql -U ova -d ova -c "
    DO \$\$
    DECLARE
        v_tx_id UUID := gen_random_uuid();
        v_safeguard_id UUID;
    BEGIN
        -- Get or create safeguarding account
        SELECT id INTO v_safeguard_id FROM ledger.accounts
        WHERE account_type = 'SAFEGUARDING' AND currency = 'TRY' LIMIT 1;

        IF v_safeguard_id IS NULL THEN
            INSERT INTO ledger.accounts (id, user_id, currency, account_type, status, created_at)
            VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'TRY', 'SAFEGUARDING', 'ACTIVE', NOW())
            RETURNING id INTO v_safeguard_id;
        END IF;

        -- Create ledger transaction
        INSERT INTO ledger.transactions (id, type, status, reference_id, metadata, created_at)
        VALUES (v_tx_id, 'DEPOSIT', 'COMPLETED', 'demo-prefund', '{\"demo\": true}', NOW());

        -- Debit safeguarding
        INSERT INTO ledger.entries (id, transaction_id, account_id, direction, amount, currency, balance_after, created_at)
        VALUES (gen_random_uuid(), v_tx_id, v_safeguard_id, 'DEBIT', $MINT_AMOUNT, 'TRY', 0, NOW());

        -- Credit user account
        INSERT INTO ledger.entries (id, transaction_id, account_id, direction, amount, currency, balance_after, created_at)
        VALUES (gen_random_uuid(), v_tx_id, '$TRY_ACCOUNT_ID', 'CREDIT', $MINT_AMOUNT, 'TRY', $MINT_AMOUNT, NOW());

        RAISE NOTICE 'Ledger prefund complete: tx=%', v_tx_id;
    END \$\$;
    " 2>/dev/null

    # Insert MintRequested outbox event for blockchain-service
    docker exec ova-postgres psql -U ova -d ova -c "
    INSERT INTO shared.outbox_events (id, aggregate_type, aggregate_id, event_type, payload, published, created_at)
    VALUES (
        nextval('shared.outbox_events_id_seq'),
        'payment',
        '$(python3 -c "import uuid; print(uuid.uuid4())")',
        'MintRequested',
        '{
            \"paymentOrderId\": \"$(python3 -c "import uuid; print(uuid.uuid4())")\",
            \"targetAccountId\": \"$TRY_ACCOUNT_ID\",
            \"amount\": $MINT_AMOUNT,
            \"currency\": \"TRY\",
            \"region\": \"TR\"
        }',
        false,
        NOW()
    );
    " 2>/dev/null

    echo -e "${GREEN}  ✓ MintRequested event inserted ($MINT_AMOUNT TRY)${NC}"
    echo -e "${CYAN}  → Blockchain-service will process: create wallet → allowlist → mint ariTRY${NC}"
    echo -e "${CYAN}  → Wait ~30s for on-chain mint to complete${NC}"
fi
echo ""

# ── Step 7: Save Demo State ──────────────────────────────────────────────────
echo -e "${YELLOW}[7/7] Saving demo state...${NC}"

cat > "$STATE_FILE" << EOF
# ARI Demo State - Generated $(date)
DEMO_EMAIL=$DEMO_EMAIL
DEMO_PASSWORD=$DEMO_PASSWORD
DEMO_TOKEN=$TOKEN
DEMO_USER_ID=$USER_ID
DEMO_TRY_ACCOUNT_ID=$TRY_ACCOUNT_ID
DEMO_EUR_ACCOUNT_ID=$EUR_ACCOUNT_ID
DEMO_MINT_AMOUNT=$MINT_AMOUNT
EOF

echo -e "${GREEN}  ✓ State saved to .demo-state${NC}"
echo ""

echo -e "${BOLD}${GREEN}╔══════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${GREEN}║         Demo Setup Complete!              ║${NC}"
echo -e "${BOLD}${GREEN}╚══════════════════════════════════════════╝${NC}"
echo ""
echo -e "  ${BOLD}User:${NC}         $DEMO_EMAIL"
echo -e "  ${BOLD}User ID:${NC}      $USER_ID"
echo -e "  ${BOLD}TRY Account:${NC}  $TRY_ACCOUNT_ID"
echo -e "  ${BOLD}EUR Account:${NC}  $EUR_ACCOUNT_ID"
echo -e "  ${BOLD}Pre-funded:${NC}   $MINT_AMOUNT TRY"
echo ""
echo -e "  Next steps:"
echo -e "    1. Wait ~30s for blockchain-service to mint on-chain"
echo -e "    2. Run: ${CYAN}./scripts/demo-e2e.sh${NC} for full E2E demo"
echo -e "    3. Open: ${CYAN}http://localhost:3000${NC} for web app demo"
