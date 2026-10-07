#!/usr/bin/env bash
# Captures MOCK-mode fixtures from a running REAL StockSteps backend's public API.
# Uses provider quota (roughly 100-150 FMP requests with the defaults), so run it rarely.
#
#   scripts/capture-fixtures.sh                       # backend at http://localhost:8080
#   BACKEND=http://localhost:8081 scripts/capture-fixtures.sh
#   EXTRA_SYMBOLS="AAPL MSFT" FUNDAMENTALS_SYMBOLS="AAPL" scripts/capture-fixtures.sh
#
# Only successful (HTTP 200) responses are written. The run stops at the first rate-limit
# response so a quota problem never burns the remaining requests.
set -euo pipefail

BACKEND="${BACKEND:-http://localhost:8080}"
OUT="${OUT:-server/src/main/resources/fixtures}"
EXTRA_SYMBOLS="${EXTRA_SYMBOLS:-AAPL MSFT NVDA TSLA AMZN GOOGL META}"
FUNDAMENTALS_SYMBOLS="${FUNDAMENTALS_SYMBOLS:-AAPL MSFT NVDA}"
SEARCH_QUERIES="${SEARCH_QUERIES:-apple microsoft nvidia tesla amazon alphabet meta}"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

mode="$(curl -fsS "$BACKEND/api/v1/meta" | python3 -c 'import sys,json; print(json.load(sys.stdin)["dataMode"])')"
if [[ "$mode" != "real" ]]; then
  echo "Refusing to capture: backend data mode is '$mode', expected 'real'." >&2
  exit 1
fi

# fetch <api path> <fixture path>: writes the fixture only on HTTP 200.
fetch() {
  local status
  status="$(curl -sS -o "$TMP" -w '%{http_code}' "$BACKEND$1")"
  case "$status" in
    200) mkdir -p "$(dirname "$OUT/$2")"; cp "$TMP" "$OUT/$2"; echo "saved  $2" ;;
    503) if grep -q PROVIDER_RATE_LIMITED "$TMP"; then echo "Provider rate limit reached at $1; stopping." >&2; exit 2; fi
         echo "skip   $2 (HTTP 503)" ;;
    *)   echo "skip   $2 (HTTP $status)" ;;
  esac
}

mkdir -p "$OUT"
fetch /api/v1/market/snapshot market/snapshot.json
fetch "/api/v1/news?page=0&limit=20" news/market.json
python3 -c '
import json,sys
errors=json.load(open(sys.argv[1])).get("errors",[])
if errors: print("warning: snapshot captured with provider errors in:", ", ".join(e["section"] for e in errors), file=sys.stderr)
' "$OUT/market/snapshot.json" 2>/dev/null || true

# Movers from the captured snapshot plus well-known symbols.
movers="$(python3 -c '
import json,sys
d=json.load(open(sys.argv[1]))
print(" ".join(dict.fromkeys(m["symbol"] for k in ("gainers","losers","mostActive") for m in d.get(k,[])[:5])))
' "$OUT/market/snapshot.json" 2>/dev/null || true)"
symbols="$(printf '%s\n' $movers $EXTRA_SYMBOLS | awk 'NF && !seen[$0]++' | tr '\n' ' ')"

for symbol in $symbols; do
  fetch "/api/v1/stocks/$symbol/quote" "stocks/$symbol/quote.json"
  fetch "/api/v1/stocks/$symbol/profile" "stocks/$symbol/profile.json"
  fetch "/api/v1/stocks/$symbol/sparkline" "stocks/$symbol/sparkline.json"
done
for symbol in $EXTRA_SYMBOLS; do
  fetch "/api/v1/stocks/$symbol/news" "news/company/$symbol.json"
done
for symbol in $FUNDAMENTALS_SYMBOLS; do
  fetch "/api/v1/stocks/$symbol/fundamentals?period=annual" "stocks/$symbol/fundamentals-annual.json"
  fetch "/api/v1/stocks/$symbol/fundamentals?period=quarter" "stocks/$symbol/fundamentals-quarter.json"
done

# Merge search results for several queries into one searchable list.
results=()
for query in $SEARCH_QUERIES; do
  status="$(curl -sS -o "$TMP.$query" -w '%{http_code}' "$BACKEND/api/v1/stocks/search?query=$query")"
  [[ "$status" == 200 ]] && results+=("$TMP.$query") || echo "skip   search '$query' (HTTP $status)"
done
if (( ${#results[@]} )); then
  python3 -c '
import json,sys
merged={}
for path in sys.argv[2:]:
    for r in json.load(open(path)): merged.setdefault(r["symbol"], r)
json.dump(list(merged.values()), open(sys.argv[1], "w"), indent=2)
' "$OUT/stocks/search.json" "${results[@]}"
  rm -f "${results[@]}"
  echo "saved  stocks/search.json"
fi

printf '{\n  "capturedAt": "%s",\n  "source": "%s"\n}\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "StockSteps REAL backend public API" > "$OUT/manifest.json"
echo "Fixtures written to $OUT"
