#!/usr/bin/env bash
# Existing backend required. Default mode is read-only. No signup or seed writes.
set -euo pipefail
BASE="${BASE_URL:-http://localhost:8080/v1}"
BASE="${BASE%/}"
: "${BUYER_TOKEN:?Set BUYER_TOKEN to an existing buyer JWT}"
: "${SELLER_TOKEN:?Set SELLER_TOKEN to the owning seller JWT}"
command -v curl >/dev/null || { echo "curl is required."; exit 1; }
if command -v python3 >/dev/null; then PY=python3
elif command -v python >/dev/null; then PY=python
else echo "Python is required to check JSON responses."; exit 1; fi
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
PASS=0
FAIL=0
LAST_CODE=""

run() {
    # name expected method path body token
    local name="$1" expected="$2" method="$3" path="$4"
    local body="${5:-}" token="${6:-}" code
    local args=(-sS --connect-timeout 5 --max-time 25 -o "$TMP" -w '%{http_code}'
        -X "$method" "$BASE$path" -H 'Content-Type: application/json')
    [[ -n "$body" ]] && args+=(-d "$body")
    [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
    if ! code=$(curl "${args[@]}"); then
        echo "FAIL $name: connection/request failed"; FAIL=$((FAIL + 1)); LAST_CODE=000; return
    fi
    LAST_CODE="$code"
    if [[ "$code" == "$expected" ]] && "$PY" -c '
import json,sys
body=json.load(open(sys.argv[1], encoding="utf-8"))
status=int(sys.argv[2])
assert isinstance(body,dict)
assert body.get("success") == (status < 400)
assert body.get("status") == status
' "$TMP" "$expected" 2>/dev/null; then
        echo "PASS $name"; PASS=$((PASS + 1))
    else
        echo "FAIL $name (expected $expected with ApiResponse, got $code)"; FAIL=$((FAIL + 1))
    fi
}

run 'orders without token' 401 GET /orders/me
run 'invalid token' 401 GET /orders/me '' invalid
run 'buyer own orders' 200 GET /orders/me '' "$BUYER_TOKEN"
run 'seller own historical purchases' 200 GET /orders/me '' "$SELLER_TOKEN"
run 'seller incoming orders' 200 GET /seller/me/orders '' "$SELLER_TOKEN"
run 'seller status filter' 200 GET '/seller/me/orders?status=CONFIRMED' '' "$SELLER_TOKEN"
run 'buyer cannot list seller orders' 403 GET /seller/me/orders '' "$BUYER_TOKEN"
run 'invalid status filter' 400 GET '/seller/me/orders?status=NOT_A_STATUS' '' "$SELLER_TOKEN"
run 'invalid order ID' 400 GET /orders/not-a-number '' "$BUYER_TOKEN"

if [[ -n "${ORDER_ID:-}" ]]; then
    [[ "$ORDER_ID" =~ ^[1-9][0-9]*$ ]] || { echo 'ORDER_ID must be positive numeric.'; exit 1; }
    run 'buyer reads owned order' 200 GET "/orders/$ORDER_ID" '' "$BUYER_TOKEN"
    run 'seller reads owned order' 200 GET "/orders/$ORDER_ID" '' "$SELLER_TOKEN"
    run 'buyer cannot change status' 403 PATCH "/orders/$ORDER_ID/status" '{"status":"IN_PRODUCTION"}' "$BUYER_TOKEN"
    if [[ -n "${OTHER_BUYER_TOKEN:-}" ]]; then
        run 'other buyer cannot read order' 404 GET "/orders/$ORDER_ID" '' "$OTHER_BUYER_TOKEN"
    fi
    if [[ -n "${OTHER_SELLER_TOKEN:-}" ]]; then
        run 'other seller cannot read order' 404 GET "/orders/$ORDER_ID" '' "$OTHER_SELLER_TOKEN"
        run 'other seller cannot change status' 404 PATCH "/orders/$ORDER_ID/status" '{"status":"IN_PRODUCTION"}' "$OTHER_SELLER_TOKEN"
    fi
fi

if [[ "${RUN_STATUS_WRITES:-0}" == 1 ]]; then
    : "${ORDER_ID:?Status writes require an isolated existing test ORDER_ID}"
    run 'read initial test state' 200 GET "/orders/$ORDER_ID" '' "$SELLER_TOKEN"
    [[ "$LAST_CODE" == 200 ]] || { echo 'Cannot read test order; refusing writes.'; exit 1; }
    "$PY" -c 'import json,sys; assert json.load(open(sys.argv[1]))["data"]["status"] == "CONFIRMED"' "$TMP" || {
        echo 'Test order must begin CONFIRMED. Refusing writes.'; exit 1;
    }
    run 'reject skip CONFIRMED to READY' 409 PATCH "/orders/$ORDER_ID/status" '{"status":"READY"}' "$SELLER_TOKEN"
    run 'advance to IN_PRODUCTION' 200 PATCH "/orders/$ORDER_ID/status" '{"status":"IN_PRODUCTION"}' "$SELLER_TOKEN"
    run 'reject same state' 409 PATCH "/orders/$ORDER_ID/status" '{"status":"IN_PRODUCTION"}' "$SELLER_TOKEN"
    run 'reject reversal to CONFIRMED' 409 PATCH "/orders/$ORDER_ID/status" '{"status":"CONFIRMED"}' "$SELLER_TOKEN"
    run 'advance to READY' 200 PATCH "/orders/$ORDER_ID/status" '{"status":"READY"}' "$SELLER_TOKEN"
    run 'reject skip READY to DELIVERED' 409 PATCH "/orders/$ORDER_ID/status" '{"status":"DELIVERED"}' "$SELLER_TOKEN"
    run 'advance to OUT_FOR_DELIVERY' 200 PATCH "/orders/$ORDER_ID/status" '{"status":"OUT_FOR_DELIVERY"}' "$SELLER_TOKEN"
    run 'advance to DELIVERED' 200 PATCH "/orders/$ORDER_ID/status" '{"status":"DELIVERED"}' "$SELLER_TOKEN"
    for state in CONFIRMED IN_PRODUCTION READY OUT_FOR_DELIVERY DELIVERED; do
        run "reject DELIVERED to $state" 409 PATCH "/orders/$ORDER_ID/status" "{\"status\":\"$state\"}" "$SELLER_TOKEN"
    done
fi
if [[ -n "${ADMIN_TOKEN:-}" ]]; then
    run 'admin has no buyer-list bypass' 403 GET /orders/me '' "$ADMIN_TOKEN"
fi
printf '\nPassed: %s   Failed: %s\n' "$PASS" "$FAIL"
[[ "$FAIL" == 0 ]]
