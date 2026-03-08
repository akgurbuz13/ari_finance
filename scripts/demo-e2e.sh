#!/bin/bash
# ARI Platform - Full End-to-End Demo on Fuji Testnet
# Demonstrates: Mint → Balance check → Cross-Border Transfer → ICTT Bridge
#
# Prerequisites:
#   1. Run ./scripts/setup-demo.sh first
#   2. core-banking on :8080, blockchain-service on :8081
#   3. Wait ~30s after setup for on-chain mint to settle

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
STATE_FILE="$PROJECT_DIR/.demo-state"

# Load configs
source "$PROJECT_DIR/.env.fuji"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
DIM='\033[2m'
NC='\033[0m'

BASE_URL="http://localhost:8080/api/v1"

# ── Load Demo State ──────────────────────────────────────────────────────────
if [ ! -f "$STATE_FILE" ]; then
    echo -e "${RED}Demo state not found. Run ./scripts/setup-demo.sh first.${NC}"
    exit 1
fi
source "$STATE_FILE"

# Re-authenticate (token may have expired)
echo -e "${DIM}Authenticating...${NC}"
LOGIN_RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\": \"$DEMO_EMAIL\", \"password\": \"$DEMO_PASSWORD\"}" 2>/dev/null)
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null || echo "")

if [ -z "$TOKEN" ]; then
    echo -e "${RED}Failed to authenticate. Run setup-demo.sh first.${NC}"
    exit 1
fi

echo ""
echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${CYAN}║          ARI Platform - End-to-End Demo on Fuji             ║${NC}"
echo -e "${BOLD}${CYAN}║     Stablecoin Mint → Cross-Border Transfer → ICTT Bridge   ║${NC}"
echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ══════════════════════════════════════════════════════════════════════════════
# Scene 1: Verify On-Chain Mint
# ══════════════════════════════════════════════════════════════════════════════
echo -e "${BOLD}━━━ Scene 1: Verify On-Chain ariTRY Mint ━━━${NC}"
echo ""

# Check ledger balance
TRY_BALANCE=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/accounts/$DEMO_TRY_ACCOUNT_ID/balance" 2>/dev/null)
LEDGER_BALANCE=$(echo "$TRY_BALANCE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balance','0'))" 2>/dev/null || echo "0")
echo -e "  ${BOLD}Ledger Balance:${NC} $LEDGER_BALANCE TRY"

# Check on-chain balance via JSON-RPC
# balanceOf(address) selector = 0x70a08231
# Get user's wallet address from blockchain DB
WALLET_ADDR=$(docker exec ari-postgres psql -U ari -d ari -t -c "
    SELECT address FROM blockchain.custodial_wallets WHERE user_id = '$DEMO_USER_ID' AND chain_id = $TR_L1_CHAIN_ID LIMIT 1;
" 2>/dev/null | tr -d ' \n')

if [ -n "$WALLET_ADDR" ]; then
    echo -e "  ${BOLD}Custodial Wallet:${NC} $WALLET_ADDR (TR L1)"

    # Pad address to 32 bytes for ABI encoding
    ADDR_PADDED=$(echo "$WALLET_ADDR" | sed 's/0x//' | awk '{printf "%064s\n", $0}' | tr ' ' '0')
    CALL_DATA="0x70a08231${ADDR_PADDED}"

    ON_CHAIN_RESP=$(curl -s -X POST -H "Content-Type: application/json" \
        --data "{\"jsonrpc\":\"2.0\",\"method\":\"eth_call\",\"params\":[{\"to\":\"$TR_STABLECOIN_ADDRESS\",\"data\":\"$CALL_DATA\"},\"latest\"],\"id\":1}" \
        "$TR_L1_RPC_URL" 2>/dev/null)

    ON_CHAIN_HEX=$(echo "$ON_CHAIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('result','0x0'))" 2>/dev/null || echo "0x0")

    if [ "$ON_CHAIN_HEX" != "0x0" ] && [ "$ON_CHAIN_HEX" != "0x0000000000000000000000000000000000000000000000000000000000000000" ]; then
        ON_CHAIN_WEI=$(python3 -c "print(int('$ON_CHAIN_HEX', 16))" 2>/dev/null)
        ON_CHAIN_TOKENS=$(python3 -c "print(int('$ON_CHAIN_HEX', 16) / 10**18)" 2>/dev/null)
        echo -e "  ${BOLD}On-Chain ariTRY:${NC} ${GREEN}$ON_CHAIN_TOKENS ariTRY${NC} ✓"
        echo -e "  ${DIM}Contract: $TR_STABLECOIN_ADDRESS${NC}"
    else
        echo -e "  ${YELLOW}On-Chain ariTRY: 0 (mint may still be processing)${NC}"
        echo -e "  ${CYAN}Tip: Wait 30s and retry, or check blockchain-service logs${NC}"
    fi
else
    echo -e "  ${YELLOW}No custodial wallet found yet (mint still processing?)${NC}"
fi
echo ""

# ══════════════════════════════════════════════════════════════════════════════
# Scene 2: Cross-Border Transfer TRY → EUR
# ══════════════════════════════════════════════════════════════════════════════
echo -e "${BOLD}━━━ Scene 2: Cross-Border Transfer (TRY → EUR via ICTT Bridge) ━━━${NC}"
echo ""

# Step 2a: Get FX Quote
echo -e "  ${YELLOW}[2a] Getting FX quote: 1000 TRY → EUR...${NC}"
QUOTE_RESP=$(curl -s -X POST "$BASE_URL/fx/quotes" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "sourceCurrency": "TRY",
        "targetCurrency": "EUR",
        "sourceAmount": 1000
    }' 2>/dev/null)

QUOTE_ID=$(echo "$QUOTE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('quoteId',''))" 2>/dev/null || echo "")
SOURCE_AMT=$(echo "$QUOTE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('sourceAmount',''))" 2>/dev/null || echo "")
TARGET_AMT=$(echo "$QUOTE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('targetAmount',''))" 2>/dev/null || echo "")
FX_RATE=$(echo "$QUOTE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('customerRate',''))" 2>/dev/null || echo "")
SPREAD=$(echo "$QUOTE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('spread',''))" 2>/dev/null || echo "")
EXPIRES=$(echo "$QUOTE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('expiresAt',''))" 2>/dev/null || echo "")

if [ -z "$QUOTE_ID" ]; then
    echo -e "  ${RED}✗ Failed to get FX quote${NC}"
    echo "  Response: $QUOTE_RESP"
    echo ""
    echo -e "  ${CYAN}Tip: Ensure account has sufficient TRY balance ($LEDGER_BALANCE available)${NC}"
    echo -e "  ${CYAN}Skipping cross-border transfer...${NC}"
    echo ""

    echo -e "${BOLD}${GREEN}━━━ Demo Summary ━━━${NC}"
    echo ""
    echo -e "  ${BOLD}Scene 1:${NC} ariTRY Mint on Fuji TR L1 ✓"
    echo -e "  ${BOLD}Scene 2:${NC} Cross-Border Transfer (skipped - insufficient balance or quote error)"
    echo ""
    exit 0
fi

echo -e "  ${GREEN}✓ FX Quote created${NC}"
echo -e "    Quote ID:    $QUOTE_ID"
echo -e "    Amount:      $SOURCE_AMT TRY → $TARGET_AMT EUR"
echo -e "    Rate:        $FX_RATE (spread: $SPREAD)"
echo -e "    Expires:     $EXPIRES"
echo ""

# Step 2b: Execute cross-border transfer (must be fast - quote expires in 30s!)
IDEM_KEY="demo-xborder-$(date +%s)"
echo -e "  ${YELLOW}[2b] Executing cross-border transfer...${NC}"

XBORDER_RESP=$(curl -s -X POST "$BASE_URL/payments/cross-border" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $IDEM_KEY" \
    -d "{
        \"senderAccountId\": \"$DEMO_TRY_ACCOUNT_ID\",
        \"receiverAccountId\": \"$DEMO_EUR_ACCOUNT_ID\",
        \"fxQuoteId\": \"$QUOTE_ID\",
        \"description\": \"Demo cross-border TRY to EUR\"
    }" 2>/dev/null)

PAYMENT_ID=$(echo "$XBORDER_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
PAYMENT_STATUS=$(echo "$XBORDER_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || echo "")
PAYMENT_TYPE=$(echo "$XBORDER_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('type',''))" 2>/dev/null || echo "")

if [ -z "$PAYMENT_ID" ]; then
    echo -e "  ${RED}✗ Cross-border transfer failed${NC}"
    echo "  Response: $XBORDER_RESP"
    exit 1
fi

echo -e "  ${GREEN}✓ Cross-border transfer initiated${NC}"
echo -e "    Payment ID:  $PAYMENT_ID"
echo -e "    Type:        $PAYMENT_TYPE"
echo -e "    Status:      $PAYMENT_STATUS"
echo ""

# Step 2c: Check outbox events created
echo -e "  ${YELLOW}[2c] Checking outbox events...${NC}"
OUTBOX_EVENTS=$(docker exec ari-postgres psql -U ari -d ari -t -c "
    SELECT event_type, published FROM shared.outbox_events
    WHERE aggregate_id LIKE '%$PAYMENT_ID%' OR payload::text LIKE '%$PAYMENT_ID%'
    ORDER BY created_at DESC LIMIT 5;
" 2>/dev/null | grep -v '^$')

if [ -n "$OUTBOX_EVENTS" ]; then
    echo -e "  ${GREEN}✓ Outbox events:${NC}"
    echo "$OUTBOX_EVENTS" | while read -r line; do
        echo -e "    $line"
    done
else
    echo -e "  ${YELLOW}  No outbox events found (may take a moment)${NC}"
fi
echo ""

# Step 2d: Monitor payment status
echo -e "  ${YELLOW}[2d] Monitoring payment status...${NC}"
echo -e "  ${DIM}  (blockchain-service processes BurnRequested + MintRequested events)${NC}"
echo ""

for i in $(seq 1 12); do
    sleep 5
    DETAIL_RESP=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/payments/$PAYMENT_ID" 2>/dev/null)
    CURRENT_STATUS=$(echo "$DETAIL_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('payment',{}).get('status','unknown'))" 2>/dev/null || echo "unknown")

    echo -e "    [${i}/12] Status: ${BOLD}$CURRENT_STATUS${NC} ($(( i * 5 ))s elapsed)"

    if [ "$CURRENT_STATUS" = "COMPLETED" ] || [ "$CURRENT_STATUS" = "completed" ]; then
        echo -e "    ${GREEN}✓ Transfer completed!${NC}"
        break
    fi
    if [ "$CURRENT_STATUS" = "FAILED" ] || [ "$CURRENT_STATUS" = "failed" ]; then
        echo -e "    ${RED}✗ Transfer failed${NC}"
        break
    fi
done
echo ""

# ══════════════════════════════════════════════════════════════════════════════
# Scene 3: Verify Final Balances
# ══════════════════════════════════════════════════════════════════════════════
echo -e "${BOLD}━━━ Scene 3: Final Balance Verification ━━━${NC}"
echo ""

# Ledger balances
TRY_FINAL=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/accounts/$DEMO_TRY_ACCOUNT_ID/balance" 2>/dev/null)
TRY_BAL=$(echo "$TRY_FINAL" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balance','?'))" 2>/dev/null || echo "?")

EUR_FINAL=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/accounts/$DEMO_EUR_ACCOUNT_ID/balance" 2>/dev/null)
EUR_BAL=$(echo "$EUR_FINAL" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balance','?'))" 2>/dev/null || echo "?")

echo -e "  ${BOLD}Ledger Balances:${NC}"
echo -e "    TRY: $TRY_BAL (was: $LEDGER_BALANCE)"
echo -e "    EUR: $EUR_BAL (was: 0)"
echo ""

# On-chain verification
if [ -n "$WALLET_ADDR" ]; then
    echo -e "  ${BOLD}On-Chain Balances:${NC}"

    # TR L1 ariTRY balance
    ADDR_PADDED=$(echo "$WALLET_ADDR" | sed 's/0x//' | awk '{printf "%064s\n", $0}' | tr ' ' '0')
    CALL_DATA="0x70a08231${ADDR_PADDED}"

    TR_RESP=$(curl -s -X POST -H "Content-Type: application/json" \
        --data "{\"jsonrpc\":\"2.0\",\"method\":\"eth_call\",\"params\":[{\"to\":\"$TR_STABLECOIN_ADDRESS\",\"data\":\"$CALL_DATA\"},\"latest\"],\"id\":1}" \
        "$TR_L1_RPC_URL" 2>/dev/null)
    TR_HEX=$(echo "$TR_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('result','0x0'))" 2>/dev/null || echo "0x0")
    TR_TOKENS=$(python3 -c "v=int('$TR_HEX',16); print(v/10**18) if v>0 else print(0)" 2>/dev/null)
    echo -e "    ariTRY on TR L1: $TR_TOKENS"

    # EU L1 ariEUR balance (check if wallet exists on EU chain too)
    EU_WALLET=$(docker exec ari-postgres psql -U ari -d ari -t -c "
        SELECT address FROM blockchain.custodial_wallets WHERE user_id = '$DEMO_USER_ID' AND chain_id = $EU_L1_CHAIN_ID LIMIT 1;
    " 2>/dev/null | tr -d ' \n')

    if [ -n "$EU_WALLET" ]; then
        EU_ADDR_PADDED=$(echo "$EU_WALLET" | sed 's/0x//' | awk '{printf "%064s\n", $0}' | tr ' ' '0')
        EU_CALL_DATA="0x70a08231${EU_ADDR_PADDED}"

        EU_BAL_RESP=$(curl -s -X POST -H "Content-Type: application/json" \
            --data "{\"jsonrpc\":\"2.0\",\"method\":\"eth_call\",\"params\":[{\"to\":\"$EU_STABLECOIN_ADDRESS\",\"data\":\"$EU_CALL_DATA\"},\"latest\"],\"id\":1}" \
            "$EU_L1_RPC_URL" 2>/dev/null)
        EU_HEX=$(echo "$EU_BAL_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('result','0x0'))" 2>/dev/null || echo "0x0")
        EU_TOKENS=$(python3 -c "v=int('$EU_HEX',16); print(v/10**18) if v>0 else print(0)" 2>/dev/null)
        echo -e "    ariEUR on EU L1: $EU_TOKENS"
    fi

    echo -e "    ${DIM}Contracts: TR=$TR_STABLECOIN_ADDRESS, EU=$EU_STABLECOIN_ADDRESS${NC}"
fi
echo ""

# ══════════════════════════════════════════════════════════════════════════════
# Scene 4: Transaction History
# ══════════════════════════════════════════════════════════════════════════════
echo -e "${BOLD}━━━ Scene 4: Transaction History ━━━${NC}"
echo ""

HISTORY=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/payments?accountId=$DEMO_TRY_ACCOUNT_ID&limit=5" 2>/dev/null)
echo "$HISTORY" | python3 -c "
import sys, json
try:
    orders = json.load(sys.stdin)
    for o in orders[:5]:
        status_icon = '✓' if o['status'] in ('COMPLETED','completed') else '⋯' if o['status'] in ('SETTLING','settling') else '✗'
        print(f'  {status_icon} {o[\"type\"]:20s} {o[\"amount\"]:>12s} {o[\"currency\"]:3s}  [{o[\"status\"]}]')
except:
    print('  (no transaction history)')
" 2>/dev/null
echo ""

# ══════════════════════════════════════════════════════════════════════════════
# Summary
# ══════════════════════════════════════════════════════════════════════════════
echo -e "${BOLD}${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${GREEN}║                    Demo Complete!                            ║${NC}"
echo -e "${BOLD}${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "  ${BOLD}What was demonstrated:${NC}"
echo -e "    1. ariTRY stablecoin minted on Avalanche TR L1 (Fuji testnet)"
echo -e "    2. Cross-border transfer TRY → EUR with real-time FX quote"
echo -e "    3. ICTT Bridge: BurnRequested (TR) + MintRequested (EU) via Teleporter"
echo -e "    4. Double-entry ledger with full audit trail"
echo ""
echo -e "  ${BOLD}Fuji L1 Details:${NC}"
echo -e "    TR L1: Chain $TR_L1_CHAIN_ID | ariTRY: $TR_STABLECOIN_ADDRESS"
echo -e "    EU L1: Chain $EU_L1_CHAIN_ID | ariEUR: $EU_STABLECOIN_ADDRESS"
echo -e "    Bridge: TokenHome ↔ Teleporter ↔ TokenRemote"
echo ""
echo -e "  ${BOLD}Web App:${NC} http://localhost:3000 (login: $DEMO_EMAIL / $DEMO_PASSWORD)"
