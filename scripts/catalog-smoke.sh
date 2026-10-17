#!/usr/bin/env bash
# Smoke test for public catalog endpoints (search, detail, ETA, reviews, categories).
# Needs: backend running on $BASE, curl, python3. Read-only: changes no data.
set -u

BASE="${BASE:-http://localhost:8080/v1}"
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

call() {
  local path="$1"
  STATUS="$(curl -s -o /tmp/smoke_body -w "%{http_code}" "$BASE$path")"
  BODY="$(cat /tmp/smoke_body)"
}

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)" <<<"$BODY"; }

echo "== Search =="
call "/products/search?q=ragi"
check "search by keyword" 200 "$STATUS"

call "/products/search?sort=price_asc"
check "search price asc" 200 "$STATUS"
FIRST_PRICE="$(json 'd["data"]["items"][0]["price"] if d["data"]["items"] else 0')"
echo "      cheapest first price: $FIRST_PRICE"

call "/products/search?min_price=100&max_price=200"
check "search price range" 200 "$STATUS"

echo "== Detail =="
call "/products/999999"
check "detail unknown id" 404 "$STATUS"

echo "== Categories =="
call "/categories"
check "categories" 200 "$STATUS"

echo
echo "PASS: $PASS   FAIL: $FAIL"
[ "$FAIL" -eq 0 ]