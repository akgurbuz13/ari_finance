#!/bin/bash
# E2E Vehicle Escrow Test
# Tests the full vehicle escrow lifecycle against running core-banking + blockchain-service
#
# Prerequisites:
#   - core-banking running on :8080
#   - blockchain-service running on :8081
#   - Contracts deployed with VEHICLE_NFT_ADDRESS and VEHICLE_ESCROW_ADDRESS configured
#
# Usage:
#   ./scripts/e2e-vehicle-escrow-test.sh [SALE_AMOUNT]

set -e

API_BASE="${API_BASE:-http://localhost:8080/api/v1}"
SALE_AMOUNT="${1:-150000}"

echo "=== ARI Vehicle Escrow E2E Test ==="
echo "API: ${API_BASE}"
echo "Sale Amount: ${SALE_AMOUNT} TRY"
echo ""

# Step 1: Create seller account
echo "--- Step 1: Create seller ---"
SELLER_EMAIL="seller_$(date +%s)@test.com"
SELLER=$(curl -s -X POST "${API_BASE}/auth/signup" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${SELLER_EMAIL}\",\"password\":\"Test1234!\",\"phone\":\"+905551234567\"}")
SELLER_TOKEN=$(echo $SELLER | jq -r '.accessToken')
SELLER_ID=$(echo $SELLER | jq -r '.userId')
echo "  Seller created: ${SELLER_ID}"

# Step 2: Create buyer account
echo "--- Step 2: Create buyer ---"
BUYER_EMAIL="buyer_$(date +%s)@test.com"
BUYER=$(curl -s -X POST "${API_BASE}/auth/signup" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${BUYER_EMAIL}\",\"password\":\"Test1234!\",\"phone\":\"+905559876543\"}")
BUYER_TOKEN=$(echo $BUYER | jq -r '.accessToken')
BUYER_ID=$(echo $BUYER | jq -r '.userId')
echo "  Buyer created: ${BUYER_ID}"

# Step 3: Register vehicle (seller)
echo "--- Step 3: Register vehicle ---"
VIN="WVWZZZ3CZWE$(date +%s | tail -c 7)"
VEHICLE=$(curl -s -X POST "${API_BASE}/vehicles" \
  -H "Authorization: Bearer ${SELLER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"vin\":\"${VIN}\",\"plateNumber\":\"34TEST$(date +%s | tail -c 4)\",\"make\":\"Volkswagen\",\"model\":\"Golf\",\"year\":2024}")
VEHICLE_ID=$(echo $VEHICLE | jq -r '.id')
echo "  Vehicle registered: ${VEHICLE_ID}"
echo "  Status: $(echo $VEHICLE | jq -r '.status')"

# Step 4: Wait for mint
echo "--- Step 4: Waiting for NFT mint... ---"
for i in $(seq 1 30); do
  sleep 2
  V=$(curl -s "${API_BASE}/vehicles/${VEHICLE_ID}" -H "Authorization: Bearer ${SELLER_TOKEN}")
  STATUS=$(echo $V | jq -r '.status')
  TOKEN_ID=$(echo $V | jq -r '.tokenId')
  if [ "$STATUS" = "MINTED" ]; then
    echo "  Vehicle minted! Token #${TOKEN_ID}"
    echo "  Tx: $(echo $V | jq -r '.mintTxHash')"
    break
  fi
  echo "  Still pending... (attempt ${i}/30)"
done

if [ "$STATUS" != "MINTED" ]; then
  echo "  WARNING: Mint not confirmed after 60s. Continuing anyway..."
fi

# Step 5: Create escrow (seller)
echo "--- Step 5: Create escrow ---"
ESCROW=$(curl -s -X POST "${API_BASE}/vehicles/escrow" \
  -H "Authorization: Bearer ${SELLER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"vehicleRegistrationId\":\"${VEHICLE_ID}\",\"saleAmount\":${SALE_AMOUNT}}")
ESCROW_ID=$(echo $ESCROW | jq -r '.id')
SHARE_CODE=$(echo $ESCROW | jq -r '.shareCode')
echo "  Escrow created: ${ESCROW_ID}"
echo "  Share code: ${SHARE_CODE}"

# Step 6: Buyer joins
echo "--- Step 6: Buyer joins escrow ---"
JOIN=$(curl -s -X POST "${API_BASE}/vehicles/escrow/join/${SHARE_CODE}" \
  -H "Authorization: Bearer ${BUYER_TOKEN}")
echo "  State: $(echo $JOIN | jq -r '.state')"

# Step 7: Wait for on-chain setup
echo "--- Step 7: Waiting for on-chain setup... ---"
for i in $(seq 1 30); do
  sleep 2
  E=$(curl -s "${API_BASE}/vehicles/escrow/${ESCROW_ID}" -H "Authorization: Bearer ${SELLER_TOKEN}")
  STATE=$(echo $E | jq -r '.state')
  if [ "$STATE" = "SETUP_COMPLETE" ]; then
    echo "  On-chain escrow created! ID: $(echo $E | jq -r '.onChainEscrowId')"
    break
  fi
  echo "  State: ${STATE} (attempt ${i}/30)"
done

# Step 8: Buyer funds escrow
echo "--- Step 8: Buyer funds escrow ---"
FUND=$(curl -s -X POST "${API_BASE}/vehicles/escrow/${ESCROW_ID}/fund" \
  -H "Authorization: Bearer ${BUYER_TOKEN}")
echo "  State: $(echo $FUND | jq -r '.state')"

# Step 9: Wait for funding confirmation
echo "--- Step 9: Waiting for funding confirmation... ---"
for i in $(seq 1 20); do
  sleep 2
  E=$(curl -s "${API_BASE}/vehicles/escrow/${ESCROW_ID}" -H "Authorization: Bearer ${SELLER_TOKEN}")
  STATE=$(echo $E | jq -r '.state')
  if [ "$STATE" = "FUNDED" ]; then
    echo "  Escrow funded on-chain!"
    break
  fi
  echo "  State: ${STATE} (attempt ${i}/20)"
done

# Step 10: Seller confirms
echo "--- Step 10: Seller confirms ---"
curl -s -X POST "${API_BASE}/vehicles/escrow/${ESCROW_ID}/confirm" \
  -H "Authorization: Bearer ${SELLER_TOKEN}" | jq -r '.state'

# Step 11: Buyer confirms (triggers atomic swap)
echo "--- Step 11: Buyer confirms ---"
curl -s -X POST "${API_BASE}/vehicles/escrow/${ESCROW_ID}/confirm" \
  -H "Authorization: Bearer ${BUYER_TOKEN}" | jq -r '.state'

# Step 12: Wait for completion
echo "--- Step 12: Waiting for completion... ---"
for i in $(seq 1 30); do
  sleep 2
  E=$(curl -s "${API_BASE}/vehicles/escrow/${ESCROW_ID}" -H "Authorization: Bearer ${SELLER_TOKEN}")
  STATE=$(echo $E | jq -r '.state')
  if [ "$STATE" = "COMPLETED" ]; then
    echo "  ESCROW COMPLETED!"
    echo "  Complete tx: $(echo $E | jq -r '.completeTxHash')"
    break
  fi
  echo "  State: ${STATE} (attempt ${i}/30)"
done

# Step 13: Verify
echo ""
echo "=== VERIFICATION ==="
FINAL_VEHICLE=$(curl -s "${API_BASE}/vehicles/${VEHICLE_ID}" -H "Authorization: Bearer ${BUYER_TOKEN}" 2>/dev/null || echo '{}')
echo "Vehicle owner (should be buyer): $(echo $FINAL_VEHICLE | jq -r '.ownerUserId' 2>/dev/null || echo 'N/A')"
echo "Buyer ID:                        ${BUYER_ID}"
echo ""
echo "=== E2E Test Complete ==="
