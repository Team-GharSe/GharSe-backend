#!/usr/bin/env bash
# GharSe auth smoke test. Backend must be running on localhost:8080.

BASE="http://localhost:8080/v1"
TMP=$(mktemp)
PASS=0
FAIL=0
STAMP=$(date +%s)
PHONE="9$(echo "$STAMP" | tail -c 10)"
EMAIL="test$STAMP@example.com"

run() {
    # args: name expected_code method path [json_body] [bearer_token]
    local args=(-s -o "$TMP" -w "%{http_code}" -X "$3" "$BASE$4" -H "Content-Type: application/json")
    [ -n "$5" ] && args+=(-d "$5")
    [ -n "$6" ] && args+=(-H "Authorization: Bearer $6")
    local code
    code=$(curl "${args[@]}")
    if [ "$code" = "$2" ]; then
        echo "PASS  $1"
        PASS=$((PASS + 1))
    else
        echo "FAIL  $1  (expected $2, got $code)"
        echo "      $(cat "$TMP")"
        FAIL=$((FAIL + 1))
    fi
}

run "health"                     200 GET  /health
run "register new buyer"         200 POST /auth/register "{\"name\":\"Smoke Test\",\"phone\":\"$PHONE\",\"email\":\"$EMAIL\",\"password\":\"password123\"}"
TOKEN=$(grep -o '"token":"[^"]*"' "$TMP" | cut -d'"' -f4)
run "register duplicate phone"   409 POST /auth/register "{\"name\":\"Smoke Test\",\"phone\":\"$PHONE\",\"password\":\"password123\"}"
run "login by email"             200 POST /auth/login "{\"identifier\":\"$EMAIL\",\"password\":\"password123\"}"
run "login wrong password"       401 POST /auth/login "{\"identifier\":\"$EMAIL\",\"password\":\"wrong\"}"
run "login malformed JSON"       400 POST /auth/login '{"identifier":'
run "protected route, no token"  401 GET  /does-not-exist
run "protected route, real token" 404 GET /does-not-exist "" "$TOKEN"
run "tampered token rejected"    401 GET  /does-not-exist "" "${TOKEN%?}x"
run "public catalog route open"  404 GET  /categories
# --- B2 checks ---
run "users/me with token"      200 GET  /users/me "" "$TOKEN"
run "users/me without token"   401 GET  /users/me
run "update users/me"          200 PUT  /users/me '{"name":"Smoke Renamed","email":null,"addressText":"12 Test Road"}' "$TOKEN"
run "become seller"            200 POST /seller/profile '{"businessName":"Smoke Studio","latitude":19.076,"longitude":72.877}' "$TOKEN"
SELLER_TOKEN=$(grep -o '"token":"[^"]*"' "$TMP" | cut -d'"' -f4)
run "become seller twice"      409 POST /seller/profile '{"businessName":"Again","latitude":19.0,"longitude":72.8}' "$SELLER_TOKEN"
run "my seller profile"        200 GET  /seller/me "" "$SELLER_TOKEN"
run "bad latitude rejected"    400 POST /seller/profile '{"businessName":"X","latitude":200,"longitude":1}' "$TOKEN"

echo ""
echo "Passed: $PASS   Failed: $FAIL"
rm -f "$TMP"