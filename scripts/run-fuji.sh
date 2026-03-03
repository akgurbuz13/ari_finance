#!/bin/bash
# Run ARI Platform services against Fuji testnet L1s
# Prerequisites:
#   1. Docker: docker compose up -d (PostgreSQL + Redis)
#   2. Avalanche L1s running: avalanche network start (if stopped)
#   3. .env.fuji populated with deployment values

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}=== ARI Platform - Fuji Testnet Runner ===${NC}"

# Check prerequisites
if ! docker ps | grep -q postgres; then
    echo -e "${YELLOW}Starting Docker infrastructure...${NC}"
    cd "$PROJECT_DIR" && docker compose up -d
    sleep 3
fi

# Check L1 connectivity
echo -e "${YELLOW}Checking Fuji L1 connectivity...${NC}"
TR_RPC="http://127.0.0.1:9650/ext/bc/9x7zHB85vsWaX2BiVPGRWVWh4KHWcroZWGBWbzR958JYRQZWP/rpc"
EU_RPC="http://127.0.0.1:9652/ext/bc/21Euii5No2ut9NyF7VWkkWhkeiDk2fcZkE1GkfMjNtTtgL3DWE/rpc"

if curl -s --max-time 3 -X POST -H "Content-Type: application/json" \
    --data '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' \
    "$TR_RPC" > /dev/null 2>&1; then
    echo -e "${GREEN}  TR L1 (ariTR): Connected${NC}"
else
    echo -e "${RED}  TR L1 (ariTR): NOT REACHABLE - run 'avalanche network start' first${NC}"
    exit 1
fi

if curl -s --max-time 3 -X POST -H "Content-Type: application/json" \
    --data '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' \
    "$EU_RPC" > /dev/null 2>&1; then
    echo -e "${GREEN}  EU L1 (ariEU): Connected${NC}"
else
    echo -e "${RED}  EU L1 (ariEU): NOT REACHABLE - run 'avalanche network start' first${NC}"
    exit 1
fi

SERVICE="${1:-all}"

case "$SERVICE" in
    core-banking|cb)
        echo -e "${GREEN}Starting core-banking with dev profile...${NC}"
        cd "$PROJECT_DIR"
        ./gradlew :core-banking:bootRun --args='--spring.profiles.active=dev'
        ;;
    blockchain-service|bs)
        echo -e "${GREEN}Starting blockchain-service with fuji profile...${NC}"
        cd "$PROJECT_DIR"
        JAVA_HOME=$(/usr/libexec/java_home -v 21) \
        ./gradlew :blockchain-service:bootRun --args='--spring.profiles.active=fuji'
        ;;
    all)
        echo -e "${GREEN}Starting both services...${NC}"
        echo -e "${YELLOW}  Terminal 1: core-banking (dev profile) on :8080${NC}"
        echo -e "${YELLOW}  Terminal 2: blockchain-service (fuji profile) on :8081${NC}"
        echo ""
        echo "Run in separate terminals:"
        echo -e "  ${GREEN}./scripts/run-fuji.sh core-banking${NC}"
        echo -e "  ${GREEN}./scripts/run-fuji.sh blockchain-service${NC}"
        echo ""
        echo "Or run both in background:"
        echo -e "  ${GREEN}./scripts/run-fuji.sh core-banking &${NC}"
        echo -e "  ${GREEN}./scripts/run-fuji.sh blockchain-service &${NC}"
        ;;
    test)
        echo -e "${GREEN}Running E2E test against local services...${NC}"
        echo ""

        BASE_URL="http://localhost:8080/api/v1"

        # 1. Health check
        echo -e "${YELLOW}1. Health check...${NC}"
        curl -s "$BASE_URL/../actuator/health" | python3 -m json.tool 2>/dev/null || echo "Core-banking not running"

        # 2. Register user
        echo -e "\n${YELLOW}2. Registering test user...${NC}"
        SIGNUP_RESP=$(curl -s -X POST "$BASE_URL/auth/signup" \
            -H "Content-Type: application/json" \
            -d '{
                "email": "fuji-test@ari.finance",
                "password": "TestPassword123!",
                "phone": "+905551234567",
                "region": "TR"
            }')
        echo "$SIGNUP_RESP" | python3 -m json.tool 2>/dev/null || echo "$SIGNUP_RESP"

        TOKEN=$(echo "$SIGNUP_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null)

        if [ -z "$TOKEN" ]; then
            # Try login if already registered
            echo -e "\n${YELLOW}   User may exist, trying login...${NC}"
            LOGIN_RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
                -H "Content-Type: application/json" \
                -d '{"email": "fuji-test@ari.finance", "password": "TestPassword123!"}')
            TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null)
        fi

        if [ -z "$TOKEN" ]; then
            echo -e "${RED}Failed to get auth token${NC}"
            exit 1
        fi
        echo -e "${GREEN}   Got auth token${NC}"

        # 3. Get accounts
        echo -e "\n${YELLOW}3. Getting accounts...${NC}"
        curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/accounts" | python3 -m json.tool 2>/dev/null

        # 4. Trigger deposit (this should create a MintRequested outbox event)
        echo -e "\n${YELLOW}4. Triggering TRY deposit...${NC}"
        DEPOSIT_RESP=$(curl -s -X POST "$BASE_URL/payments/deposit" \
            -H "Authorization: Bearer $TOKEN" \
            -H "Content-Type: application/json" \
            -H "Idempotency-Key: fuji-test-$(date +%s)" \
            -d '{
                "amount": 1000,
                "currency": "TRY",
                "reference": "fuji-e2e-test"
            }')
        echo "$DEPOSIT_RESP" | python3 -m json.tool 2>/dev/null || echo "$DEPOSIT_RESP"

        echo -e "\n${GREEN}=== E2E Test Complete ===${NC}"
        echo "Check blockchain-service logs for MintRequested processing"
        echo "Check Fuji TR L1 explorer for mint transaction"
        ;;
    demo-setup|setup)
        echo -e "${GREEN}Running demo data setup...${NC}"
        "$SCRIPT_DIR/setup-demo.sh"
        ;;
    demo|demo-e2e)
        echo -e "${GREEN}Running full E2E demo...${NC}"
        "$SCRIPT_DIR/demo-e2e.sh"
        ;;
    *)
        echo "Usage: $0 {core-banking|blockchain-service|all|test|demo-setup|demo}"
        echo ""
        echo "  core-banking (cb)       Start core-banking on :8080"
        echo "  blockchain-service (bs) Start blockchain-service on :8081"
        echo "  all                     Show instructions for both"
        echo "  test                    Quick E2E smoke test"
        echo "  demo-setup (setup)      Set up demo data (user, accounts, funding)"
        echo "  demo (demo-e2e)         Run full demo (mint + cross-border + bridge)"
        exit 1
        ;;
esac
