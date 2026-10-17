# Phase 8D — API request regression

**Result: no added requests.** 8D changes no client, presenter, repository or server code. `selectSector`, the order flow and data
loading are untouched; only text placement and wrapping changed.

**Method:** the same scripted flows as 8B and 8C, using the Android DEBUG API log (`StockSteps-API`) on local MOCK `:8087`. These are
MOCK counts, not live-provider savings.

| Flow | 8C build | 8D build |
|---|---|---|
| Learn (cold start → Learn → See all → Valuation → P/E term → Learn → Start learning) | 11 | 11 (identical paths: `meta`, `daily-brief/latest`, 6 `me/*`, `watch-data`, `stocks/AAPL/details`) |

Learn's catalogue is bundled in the app. The only network request is the research company's details, when a guided research opens.

The Discover and Portfolio flows are covered by the 8B (25 = 25) and 8C (12 = 12) measurements. Their code isn't changed in 8D apart
from header text. The "About this list" disclosure is local.

Logs: `captures/logs/req8d-before.log`, `req8d-after.log`.
