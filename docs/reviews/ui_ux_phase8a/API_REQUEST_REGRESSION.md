# Phase 8A — API request regression

**Result: no added requests.** Phase 8A changes layout and wording-joins only; no client, presenter, ViewModel, repository or server code
changed, and no provider (FMP / Finnhub) path is touched.

## Code evidence

- Files changed: Compose/SwiftUI screens (`MarketsScene`, `HomeScreen`, `HomeComponents`), the iOS `StockRow` view, display objects
  (`MarketsScreenPresentation`, `HomeDashboardPresentation`), one spacing token, tests. No `*ViewModel`, `*Client`, `*Repository`,
  `*Presenter` (core) or `server/` file is in the diff.
- Markets still renders from the single `GET /api/v1/markets/overview` response (`MarketsViewModel` / iOS `MarketsModel`, unchanged);
  sector bars, the status headline and the Home portfolio summary are computed from data already in memory.
- The brief entry moved into the indices group but still reads the shared `DailyBriefPresenter` state; the Earnings Calendar row still
  starts the same `startSummary()` once.

## Runtime evidence (Android DEBUG API log, `StockSteps-API`)

Same scenario for both builds on emulator-5554 against local MOCK `:8087`: cold start on Home → Markets → Home → Markets.

| Path | BEFORE (`ddb6dfb` build) | AFTER (8A build) |
|---|---|---|
| `GET /api/v1/meta` | 1 | 1 |
| `GET /api/v1/daily-brief/latest` | 1 | 1 |
| `GET /api/v1/markets/overview` | 1 | 1 |
| `GET /api/v1/earnings/calendar` | 1 | 1 |
| `GET /api/v1/me/*` (alerts, personalized brief, earnings following, entitlements, learning, portfolio, screens, watchlists) | 8 | 8 |
| **Total** | **12** | **12** |

The tab switches back to Markets sent nothing (once-per-entry loading from 5D.5.6D is intact). Logs (redacted by the app's default-deny
logger): `captures/logs/req-before.log`, `captures/logs/req-after.log`.

iOS: not measured at runtime (NOT RUN); the iOS diff touches views only (`MarketsScene`, `HomeScreen`, `HomeComponents`, `StockRow`), and
`MarketsModel` / `BriefModel` / `EarningsModel` are unchanged.

## Phase 7E enrichment budget

Untouched: no server or screener code changed.
