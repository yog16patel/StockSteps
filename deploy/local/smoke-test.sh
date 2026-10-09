#!/usr/bin/env bash
# StockSteps local MOCK backend smoke test (Phase 5B Local). Read-mostly HTTP checks against real routes; needs curl and python3.
#   STOCKSTEPS_BASE_URL=http://<host>:8081 deploy/local/smoke-test.sh      (or: deploy/local/deploy.sh smoke)
# Signed-in routes use the MOCK-only bearer form `mock-user:<uid>` (MockUserAuthenticator); REAL never accepts it. This proves API routes,
# not Firebase sign-in or the mobile apps. The practice section places one simulated order for a throwaway uid (MOCK memory only).
set -uo pipefail
BASE="${STOCKSTEPS_BASE_URL:?set STOCKSTEPS_BASE_URL, e.g. http://192.0.2.10:8081}"
UID_="smoke-$(date +%s)"
AUTH="Authorization: Bearer mock-user:$UID_"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
pass=0; fail=0

# check <name> <expected status> <python assertion on `d` (parsed JSON) or "-"> <curl args…>
check() {
  local name="$1" want="$2" assertion="$3"; shift 3
  local code; code="$(curl -sS --max-time 15 -o "$TMP/body" -w '%{http_code}' "$@" 2>"$TMP/err")" || code="ERR"
  local why=""
  if [[ "$code" != "$want" ]]; then why="HTTP $code (want $want) $(cat "$TMP/body" "$TMP/err" 2>/dev/null | tr -d '\n' | head -c 160)"
  elif [[ "$assertion" != "-" ]]; then
    why="$(python3 - "$TMP/body" "$assertion" <<'PY' 2>&1
import json, sys
d = json.load(open(sys.argv[1]))
ok = eval(sys.argv[2], {"d": d, "any": any, "all": all, "len": len})
print("" if ok else "assertion false: " + sys.argv[2])
PY
)"
  fi
  if [[ -z "$why" ]]; then pass=$((pass + 1)); printf 'PASS  %s\n' "$name"; else fail=$((fail + 1)); printf 'FAIL  %s — %s\n' "$name" "$why"; fi
}
JSON=(-H 'Content-Type: application/json')

echo "Smoke test against $BASE (uid $UID_)"
echo "-- health and metadata"
check "live"                    200 'd["status"] == "alive"'   "$BASE/health/live"
check "ready"                   200 'd["status"] == "ready"'   "$BASE/health/ready"
check "meta reports MOCK"       200 'd["dataMode"] == "mock"'  "$BASE/api/v1/meta"

echo "-- market overview"
check "overview is labelled sample" 200 'd["sampleData"] is True and len(d["indices"]) > 0 and "Not live" in d["dataNotice"]' "$BASE/api/v1/markets/overview"

echo "-- company search"
check "search finds AAPL"       200 'any(r["symbol"] == "AAPL" for r in d)' "$BASE/api/v1/stocks/search?query=apple"
check "search without query"    400 'd["code"] == "INVALID_QUERY"'          "$BASE/api/v1/stocks/search"

echo "-- company details"
check "details AAPL"            200 'd["symbol"] == "AAPL" and d["quote"]["price"] is not None' "$BASE/api/v1/stocks/AAPL/details"
check "details invalid symbol"  400 'd["code"] == "INVALID_SYMBOL"'         "$BASE/api/v1/stocks/%40%40/details"

echo "-- stock screener"
check "screener catalog"        200 'len(d["presets"]) > 0'                 "$BASE/api/v1/screener/catalog"
check "screener preset search"  200 'd["sampleData"] is True and len(d["rows"]) > 0' -X POST "${JSON[@]}" -d '{"presetId":"growing"}' "$BASE/api/v1/screener/search"

echo "-- company comparison"
check "compare AAPL,MSFT"       200 'd["sampleData"] is True and len(d["companies"]) == 2' "$BASE/api/v1/compare?symbols=AAPL,MSFT"
check "compare one company"     400 'd["code"] == "INVALID_COMPARISON"'     "$BASE/api/v1/compare?symbols=AAPL"

echo "-- earnings calendar"
check "earnings calendar"       200 'd["sampleData"] is True and len(d["items"]) > 0' "$BASE/api/v1/earnings/calendar"

echo "-- daily market brief"
check "latest brief"            200 'd["sampleData"] is True and len(d["stories"]) > 0' "$BASE/api/v1/daily-brief/latest"

echo "-- guided research (signed-in, MOCK token)"
check "learning progress"       200 '"journeys" in d'                       -H "$AUTH" "$BASE/api/v1/me/learning"
check "learning needs sign-in"  401 '-' "$BASE/api/v1/me/learning"

echo "-- practice portfolio (signed-in, MOCK token)"
check "practice account"        200 'd["cash"] == "10000" and d["holdings"] == []' -H "$AUTH" "$BASE/api/v1/me/practice"
check "order preview"           200 'd["symbol"] == "AAPL" if "symbol" in d else d["instrument"]["symbol"] == "AAPL"' -X POST "${JSON[@]}" -H "$AUTH" \
  -d '{"symbol":"AAPL","side":"BUY","quantity":"1"}' "$BASE/api/v1/me/practice/orders/preview"
# Execution requires the price the user reviewed in the preview (REVIEW_REQUIRED otherwise), as in the apps.
PRICE="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["price"])' "$TMP/body" 2>/dev/null)"
check "execute without review"  400 'd["code"] == "REVIEW_REQUIRED"' -X POST "${JSON[@]}" -H "$AUTH" \
  -d "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":\"1\",\"idempotencyKey\":\"$UID_-0\"}" "$BASE/api/v1/me/practice/orders/execute"
check "order execute"           200 '-' -X POST "${JSON[@]}" -H "$AUTH" \
  -d "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":\"1\",\"idempotencyKey\":\"$UID_-1\",\"reviewedPrice\":\"$PRICE\"}" "$BASE/api/v1/me/practice/orders/execute"
check "holding recorded"        200 'len(d["holdings"]) == 1'               -H "$AUTH" "$BASE/api/v1/me/practice"
check "practice needs sign-in"  401 '-' "$BASE/api/v1/me/practice"

echo "-- no provider traffic (MOCK usage counters; the route is open only in MOCK)"
check "zero upstream requests"  200 'sum(c["count"] for c in d["counters"] if c["event"] == "upstream") == 0' "$BASE/internal/metrics/usage"

echo "Result: $pass passed, $fail failed"
[[ $fail -eq 0 ]]
