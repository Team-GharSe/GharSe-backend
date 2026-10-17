#!/usr/bin/env bash
# Smoke test for module C seller product endpoints.
# Needs: backend running on $BASE, curl, python3, and at least one category row.
set -u

BASE="${BASE:-http://localhost:8080/v1}"
RUN_ID="$(date +%s)"
PHONE="9${RUN_ID: -9}"
EMAIL="smoke-${RUN_ID}@example.com"
PASS=0
FAIL=0

check() {
  local label="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    echo "PASS  $label ($actual)"
    PASS=$((PASS + 1))
  else
    echo "FAIL  $label (expected $expected, got $actual)"
    echo "      body: $BODY"
    FAIL=$((FAIL + 1))
  fi
}

# Calls the API. Sets STATUS and BODY.
call() {
  local method="$1" path="$2" token="${3:-}" data="${4:-}"
  local args=(-s -o /tmp/smoke_body -w "%{http_code}" -X "$method" "$BASE$path")
  args+=(-H "Content-Type: application/json")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(--data "$data")
  STATUS="$(curl "${args[@]}")"
  BODY="$(cat /tmp/smoke_body)"
}

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)" <<<"$BODY"; }

echo "== Setup: register buyer, become seller =="
call POST /auth/register "" "{\"name\":\"Smoke Seller\",\"phone\":\"$PHONE\",\"email\":\"$EMAIL\",\"password\":\"secret123\"}"
check "register" 200 "$STATUS"
TOKEN="$(json 'd["data"]["token"]')"

call POST /seller/profile "$TOKEN" '{"businessName":"Smoke Studio","latitude":19.076,"longitude":72.877}'
check "become seller" 200 "$STATUS"
TOKEN="$(json 'd["data"]["token"]')"

CATEGORY_ID=1   # the Bakery row inserted by hand; change if your id differs

echo "== Create =="
READY='{"name":"Ragi Cookies","description":"Small batch","price":180,"categoryId":'$CATEGORY_ID',"fulfilmentType":"READY_STOCK","inventoryQty":5,"supportsPickup":true}'
call POST /products "$TOKEN" "$READY"
check "create ready-stock" 201 "$STATUS"
PID="$(json 'd["data"]["id"]' 2>/dev/null || echo "")"

MTO='{"name":"Custom Mehendi Cone","price":250,"categoryId":'$CATEGORY_ID',"fulfilmentType":"MADE_TO_ORDER","productionTimeMinutesPerUnit":90,"supportsPostal":true}'
call POST /products "$TOKEN" "$MTO"
check "create made-to-order" 201 "$STATUS"

call POST /products "$TOKEN" '{"name":"Bad","price":10,"categoryId":'$CATEGORY_ID',"fulfilmentType":"READY_STOCK","inventoryQty":1}'
check "reject no fulfilment mode" 400 "$STATUS"

call POST /products "$TOKEN" '{"name":"Bad","price":10,"categoryId":'$CATEGORY_ID',"fulfilmentType":"READY_STOCK","supportsPickup":true}'
check "reject ready-stock without quantity" 400 "$STATUS"

call POST /products "$TOKEN" '{"name":"Bad","price":-5,"categoryId":'$CATEGORY_ID',"fulfilmentType":"READY_STOCK","inventoryQty":1,"supportsPickup":true}'
check "reject negative price" 400 "$STATUS"

call POST /products "$TOKEN" '{"name":"Bad","price":10,"categoryId":99999,"fulfilmentType":"READY_STOCK","inventoryQty":1,"supportsPickup":true}'
check "reject unknown category" 400 "$STATUS"

echo "== Auth =="
call POST /products "" "$READY"
check "create without token" 401 "$STATUS"

echo "== Edit and delete =="
call PUT "/products/$PID" "$TOKEN" '{"name":"Ragi Cookies v2","price":200,"categoryId":'$CATEGORY_ID',"fulfilmentType":"READY_STOCK","inventoryQty":3,"supportsPickup":true}'
check "edit own product" 200 "$STATUS"

call DELETE "/products/$PID" "$TOKEN"
check "soft delete own product" 200 "$STATUS"

echo "== My products =="
call GET /seller/me/products "$TOKEN"
check "list my products" 200 "$STATUS"
COUNT="$(json 'len(d["data"])')"
check "list includes both products (incl. deactivated)" 2 "$COUNT"

echo
echo "PASS: $PASS   FAIL: $FAIL"
[ "$FAIL" -eq 0 ]