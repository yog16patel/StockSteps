# Practice Portfolio (virtual investing simulator)

An educational simulation with **virtual money**: no brokerage, no real orders, no real money,
no advice. Journey: Learn → Research → Practice → Understand. It's completely separate from the
real Portfolio tracker (separate storage, routes, models and totals).

## Access model (server-enforced)

| Capability | Free | Trial (14 days) | StockSteps+ |
|---|---|---|---|
| Starting virtual cash | $10,000 | $10,000 | $10,000 |
| Open holdings (distinct instruments) | 3 | Unlimited | Unlimited |
| Buy more of an existing holding / sell any holding | Yes | Yes | Yes |
| Holdings, value, gain/loss, history list, transactions | Yes | Yes | Yes |
| Chart ranges | 1W, 1M | + 3M, 1Y, ALL | + 3M, 1Y, ALL |
| Allocation (holdings, cash, sectors) | Locked | Yes | Yes |
| Guided challenges | #1 + previews | All | All |
| Premium insights | Locked | Yes | Yes |
| Reset | Yes | Yes | Yes |

`PracticePolicy` (core) is the only place these rules live; screens ask for a `PracticeCapability`.
The holding limit applies only when **opening a new distinct instrument** while at or above 3 open
holdings. Closed positions don't count.

### Trial lifecycle

`NOT_STARTED → ACTIVE → EXPIRED`, or `CONVERTED_TO_PLUS` while StockSteps+ is active after a trial.
- Starts only from the explicit confirmation sheet (`POST /practice/trial/activate`); never on install,
  sign-in or first visit.
- Backend UTC time; ends exactly 14 × 24 h after acceptance. Recorded atomically with the account's
  practice document (`users/{uid}/practice/account`), so reinstalling, clearing data, signing out/in,
  switching devices, changing the device clock or tapping twice can't restart or extend it. A reset
  doesn't touch it. One per account; cross-account abuse prevention is not claimed.
- App-managed feature trial: **no payment method, never auto-charges or renews** (distinct from a
  store billing trial).
- Expiry never deletes or sells anything. Expired users keep viewing, selling and adding to existing
  holdings; opening new ones follows the free limit (8 holdings → sell down to 2 → can open 1). The
  "trial has ended" sheet is shown once (server flag), then contextual reminders only.
- StockSteps+ = the existing server entitlement (`EntitlementService`). When it expires, access is
  recomputed from the records; data is kept. Debug plan records only count in MOCK.

## Accounting (deterministic, `PracticeEngine`)

- Money: the existing exact `Decimal` (8 dp strings, never Double). Cash in the portfolio base
  currency (CAD), rounded half-up to cents on every movement. Shares: up to 4 dp (fractional shares
  simulated); amount orders truncate to whole 0.0001 shares (never rounded up). Prices/FX keep source
  precision. Presentation formatting (`PracticeFormat`) is separate.
- Weighted-average cost: buy adds amount to cost; sell removes `cost × sold ÷ held` (all of it on a
  full sale); realized = proceeds − removed cost. Split: shares × ratio, cost unchanged. Dividend:
  cash credit. Cash and shares can't go negative (no margin, shorting, leverage, options).
- Portfolio value = cash + Σ(shares × price × FX). Total gain = value − starting cash (virtual cash
  isn't profit); unrealized, realized, dividends are reported separately. Any missing price/FX makes
  the totals "unavailable" rather than estimated.
- History: end-of-day values using the holdings each day actually had (ledger replay up to that
  date); nothing before the portfolio existed; a held instrument without a close is a gap. When an
  exchange was closed for a holiday, the previous close is used for up to 4 days (disclosed).

## Execution policy

- Immediate simulated fill at the latest **fresh** quote (`AlertRules.freshQuote`: current US
  session; during market hours ≤ 30 min old; after the close, that session's closing price, labelled
  "Latest closing price (market closed)"). Older sessions → `QUOTE_STALE`; no price → `QUOTE_MISSING`.
  No queued, limit, stop or after-hours orders. The NYSE calendar is used for TSX listings too.
- FX: Bank of Canada USD/CAD (REAL) / fixed 1.35 sample (MOCK), latest publication within 7 days of the
  quote date; otherwise `FX_UNAVAILABLE` (never 1:1). Only CAD- and USD-priced instruments.
- The server sets the fill price. The client sends the reviewed price; a move > 1% returns
  `PRICE_CHANGED` and the app asks for a fresh review.
- Instrument id = provider symbol (exchange suffix such as `.TO` disambiguates listings); exchange,
  currency, type and sector are recorded at first trade.

## Safety

- Signed in only; identity from the verified token. Clients never send user id, prices, cash, holding
  counts, plan flags or trial dates (unknown JSON fields are ignored).
- Every mutation runs in one atomic per-user update (Firestore transaction / in-memory lock): orders,
  trial activation, reset, challenge completion, corporate actions. Concurrent buys can't overspend or
  exceed the limit.
- Idempotency: execute and reset require a request id. Same id + same body replays the stored result;
  same id + different body → `IDEMPOTENCY_CONFLICT`. Records kept 30 days (max 500). The app reuses the
  id when retrying a failed confirmation.

## API (`/api/v1/me/practice`)

| Method | Path | Notes |
|---|---|---|
| GET/POST | `` | Overview (creates the portfolio on first use) |
| GET | `/entitlement` | Effective access, trial, capabilities, server time |
| POST | `/trial/activate` | Idempotent; 409 `PLUS_ACTIVE` with StockSteps+ |
| POST | `/notices/trial-expired` | Marks the one-time notice seen |
| GET | `/transactions?type=BUY\|SELL\|DIVIDEND` | Current generation |
| GET | `/performance?range=1W\|1M\|3M\|1Y\|ALL` | 403 `RANGE_LOCKED` for premium ranges |
| POST | `/orders/preview` | Indicative; returns a blocker instead of failing |
| POST | `/orders/execute` | Revalidates everything; codes below |
| POST | `/reset` | `{confirm:true, idempotencyKey}`; archives (last 5 generations kept) |
| GET | `/challenges`, POST `/challenges/{id}/complete` | `{optionId}` |
| PUT | `/debug/scenario` | **MOCK only** (route absent in REAL) |

Error codes: `HOLDING_LIMIT` (403), `INSUFFICIENT_CASH`, `INSUFFICIENT_SHARES`, `NO_HOLDING`,
`INVALID_QUANTITY`, `REVIEW_REQUIRED`, `IDEMPOTENCY_KEY_REQUIRED` (400), `QUOTE_STALE`, `QUOTE_MISSING`,
`FX_UNAVAILABLE`, `PRICE_CHANGED`, `IDEMPOTENCY_CONFLICT`, `LEDGER_FULL` (409), `UNSUPPORTED_INSTRUMENT`.

## Corporate actions

Domain support for cash dividends (eligible shares = held before the ex-date, credited on the pay
date with that day's FX) and splits (shares × ratio, cost unchanged), each applied once per event
and generation. **REAL: no verified corporate-action source is integrated, so none are applied** and
the overview says so. **MOCK:** labelled sample events (MSFT dividend 0.91 ex 2026-08-20; NVDA 2-for-1
ex 2026-09-21, with sample prices divided by the ratio from the ex-date).

## MOCK scenarios

Settings-free: Practice → More → *Sample scenarios (mock)*: `empty`, `one-holding`, `free-limit`,
`eight-holdings-trial-expired`, `trial-active`, `trial-expiring`, `trial-expired`, `plus-active`,
`plus-expired`, `low-cash`, `missing-quote`, `stale-quote`, `missing-fx`, `dividend`, `split`
(also `three-holdings`, `trial-available`). Trades use fixture closing prices; MOCK makes no provider,
Firebase or billing calls. Duplicate/concurrent orders, offline and account switching are covered by
automated tests.

## UI

- Entry points: Portfolio (*My Portfolio* vs *Practice Portfolio* cards), Home card, Learn hub
  ("Practice what you learn"), Company Details **Practice Buy**. No sixth tab.
- Practice: header + SIMULATED badge + access indicator ("2 of 3 free holdings used", "10 days left in
  your Practice trial", "StockSteps+"); tabs Overview (value, gain/loss in words, chart with locked
  ranges, cash, open holdings, quick actions, holdings preview, insights), Holdings (details sheet with
  Buy more / Sell, allocation or locked preview), Activity (All/Buys/Sells/Dividends), Challenges.
- Order: Entry (price, basis, quote time, FX, shares/amount, estimate, cash after, free slots,
  blockers) → Review ("Confirm Practice Buy/Sell" + disclosure) → Confirmation (reference, View
  Holdings / Explore Stocks / Continue Learning).
- Sheets: holding-limit paywall, locked feature, trial confirmation, trial ended, StockSteps+, reset.
- Android Compose (`presentation/practice`); iOS SwiftUI (`PracticeScenes.swift`, `IosPracticeClient`).

## MOCK vs REAL in practice

- MOCK: fixture prices captured 2026-10-07, market clock pinned to the capture time, FX 1.35 sample,
  in-memory storage (resets when the mock server restarts). The app shows "Sample data · mock backend".
- REAL: delayed provider quotes via the backend (FMP or Finnhub), not true real-time; fills need a
  quote ≤ 30 min old during market hours, or use the session close after hours; Bank of Canada FX;
  Firestore storage. Requires the backend to be deployed with these routes and Firestore credentials
  (otherwise 404 / 503). Switch in Settings → Development → Backend Data Source.

## Not done / limits

- **Purchasing StockSteps+ is not implemented** (no verified store products or receipt validation).
  The StockSteps+ sheet lists benefits and says purchase isn't available; no prices are invented.
  Restore/pending/cancelled billing states are therefore not shown. MOCK can simulate plans.
- No corporate-action provider in REAL; TSX freshness uses the US calendar; single-currency cash.
- System-level UI tests (Compose/XCUITest) and device accessibility audits weren't run; presenters
  are covered by unit tests.
- Firestore document per user (bounded: 5,000 transactions per generation, 5 archives).
