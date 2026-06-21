#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="smoke-$(date +%s)@example.com"
PASSWORD='Customer12345!'
SKU='20000000-0000-0000-0000-000000000001'

curl --fail --silent --show-error -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  "$BASE_URL/api/auth/register" >/dev/null
TOKEN=$(curl --fail --silent --show-error -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  "$BASE_URL/api/auth/login" | jq -r .accessToken)
curl --fail --silent --show-error -X PUT -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"skuId\":\"$SKU\",\"quantity\":1}" "$BASE_URL/api/cart/items" >/dev/null
ORDER_ID=$(curl --fail --silent --show-error -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: smoke-$(date +%s%N)" \
  -d '{"paymentToken":"tok_success","shippingAddress":{"recipient":"Smoke Test","addressLine1":"Main Street 1","city":"Gdansk","postalCode":"80-001","country":"PL"}}' \
  "$BASE_URL/api/orders/checkout" | jq -r .orderId)
for _ in $(seq 1 40); do
  STATUS=$(curl --fail --silent --show-error -H "Authorization: Bearer $TOKEN" \
    "$BASE_URL/api/orders/$ORDER_ID" | jq -r .status)
  case "$STATUS" in
    COMPLETED) echo "Smoke test completed: $ORDER_ID"; exit 0 ;;
    CANCELLED) echo "Smoke test cancelled unexpectedly: $ORDER_ID" >&2; exit 1 ;;
  esac
  sleep 1
done
echo "Order did not reach a terminal state: $ORDER_ID" >&2
exit 1
