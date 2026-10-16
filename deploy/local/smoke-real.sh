#!/usr/bin/env bash
# StockSteps LAN REAL backend smoke test (docs/LOCAL_REAL_BACKEND_PLAN.md). Deliberately tiny: every REAL request may use paid provider quota.
#   STOCKSTEPS_BASE_URL=http://<host>:8082 deploy/local/smoke-real.sh      (or: STOCKSTEPS_STACK=real deploy/local/deploy.sh smoke)
# Health and metadata never call providers; the one search costs at most one FMP request pair (cached afterwards). Never run smoke-test.sh
# (the MOCK suite, many routes) against REAL. Needs curl and python3.
set -uo pipefail
BASE="${STOCKSTEPS_BASE_URL:?set STOCKSTEPS_BASE_URL, e.g. http://192.0.2.10:8082}"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
pass=0; fail=0

# check <name> <expected status> <python assertion on `d` (parsed JSON) or "-"> <curl args…>
check() {
  local name="$1" want="$2" assertion="$3"; shift 3
  local code; code="$(curl -sS --max-time 20 -o "$TMP/body" -w '%{http_code}' "$@" 2>"$TMP/err")" || code="ERR"
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

echo "REAL smoke test against $BASE"
check "live"               200 'd["status"] == "alive"'  "$BASE/health/live"
check "ready"              200 'd["status"] == "ready"'  "$BASE/health/ready"
check "meta reports REAL"  200 'd["dataMode"] == "real"' "$BASE/api/v1/meta"
check "search 'app' returns AAPL" 200 'len(d) > 0 and any(r["symbol"] == "AAPL" for r in d)' "$BASE/api/v1/stocks/search?query=app"
echo "$pass passed, $fail failed"
[[ "$fail" -eq 0 ]]
