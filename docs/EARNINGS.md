# Earnings Intelligence & Earnings Calendar

> **Earnings Intelligence Lite — Phase 1 (Earnings Calendar)** was added on top of the earlier
> Earnings Center (2026-10-08, commit "Add Earnings Calendar (Earnings Intelligence Lite Phase 1) on Android and iOS").
> **Phase 3 (Post-Earnings Price Reaction)** is directly below (2026-10-08, commit "Add post-earnings price reaction (Earnings Intelligence Lite Phase 3) on Android and iOS"), then **Phase 2** (commit "Add Earnings Results and beginner explanations (Earnings Intelligence Lite Phase 2) on Android and iOS"). See the Phase 1 section right
> below; the rest of this document describes the earlier Earnings Details/results work, which is
> unchanged.

## Phase 3: Post-Earnings Price Reaction

A free **"How Did the Stock React?"** section inside Earnings Results (no new destination or tab).
Reached from every Results entry point: Calendar → Reported → View Results, Company Details →
Latest results → View Results, Daily Brief results highlights, Event Details → View Results.

Shows: window chips (First Session · 3 Sessions · 5 Sessions; incomplete ones say "not yet"), Phase 2
context ("EPS: Beat · Revenue: Miss · Stock reaction: −3.2%"), before/after closes with dates and
"regular-session close" labels, price change, reaction %, the measurement line, a neutral summary, a
deterministic explanation, warnings, a daily-close chart (dashed earnings marker, rings on baseline and
endpoint, drag to inspect, larger-chart toggle, gaps for missing sessions, full text alternative), Learn
links and the price source.

### Measurement policy (server, `earnings/PriceReactionEngine.kt`)
Session 1 = the first regular session whose close reflects the announcement ("1 trading day" is the same,
so it isn't offered separately). Windows end at the close of session 1, 3 or 5 on the exchange calendar.
| Timing | Baseline | Session 1 |
| --- | --- | --- |
| After the close | close of the announcement date's session (last session before, if not a trading day) | next session |
| Before the open | last session before the announcement date | first session on/after it |
| During market hours | previous close (no intraday data) | that session — labelled as including pre-announcement trading |
| Unknown | not guessed: last session before the date | first session after it — status `EVENT_TIME_UNKNOWN`, labelled "broader comparison" |
- A session counts as closed 15 minutes after its scheduled close (13:00 on US half days). An unfinished
  window is `WINDOW_INCOMPLETE` ("Not available yet") — never filled.
- Change = endpoint − baseline; % = change ÷ baseline × 100, exact decimals, rounded only for display.
- `DATA_NOT_COMPARABLE` when a price isn't positive, currencies differ, adjustment bases differ, or the
  market has no supported calendar. `BASELINE_/ENDPOINT_UNAVAILABLE` when a scheduled session has no close
  (missing data or a halt). `CORPORATE_ACTION_AMBIGUITY` for a non-split action inside the window; a split
  inside a split-adjusted series is allowed with a note. `PROVIDER_UNAVAILABLE` (timeout/rate limit) with a
  plain reason; a previously fetched series is served as STALE instead when available.
- Extended hours: not available from the configured provider, so never shown or simulated
  (`extendedHoursAvailable = false`; `includeExtendedHours=true` only adds that note).

### Market calendars
`ExchangeCalendar` wraps the existing rule-based `UsMarketCalendar` (NYSE holidays + half days) and
`TsxMarketCalendar` (TSX holidays; no early closes modelled). US (NYSE, Nasdaq, AMEX, OTC…) and TSX/TSXV
listings are supported; other markets return `DATA_NOT_COMPARABLE`. Unscheduled closures and halts aren't
in any rule set — they show up as missing closes.

### Data and caching
- REAL: daily closes from the existing `PriceChartService` (FMP `historical-price-eod/light`, one cached
  range request per symbol, split-adjusted, closes only — no open/high/low, intraday or extended hours);
  currency from the cached profile. No corporate-action source is wired, so a warning says splits and
  special dividends aren't checked.
- MOCK: the same history for fixture companies; fictional "StockSteps Demo" companies read
  `fixtures/earnings/price-scenarios.json` (generated with the earnings fixtures) incl. corporate actions
  and per-day currency/basis overrides. REAL never reads it.
- Reactions are recomputed from the cached series on every request (cheap), so revised prices, a moved
  announcement date or a now-complete window are reflected immediately; incomplete windows are never stored.
- Device: each window's response is saved (public, per environment) and shown offline, labelled.
- MOCK scenarios: `?scenario=price-timeout`, `price-rate-limit`, `price-stale`.

### API
| Endpoint | Notes |
| --- | --- |
| `GET /api/v1/earnings/reports/{reportId}/price-reaction?window=FIRST_SESSION\|THREE_SESSIONS\|FIVE_SESSIONS&includeExtendedHours` | Reaction + every window's status + chart history + Phase 2 classifications + explanation. 400 invalid window/id, 404 unreported. |
| `GET /api/v1/earnings/reports/{reportId}/price-history` | Chart history only (5 sessions before → 5th session after; closed sessions only). |
Earnings Details' existing reaction now uses the same engine (first session) plus its SPY comparison, so
the two screens can't disagree. (Its old ">10-day gap" rule and "a missing close means a holiday" rule were
replaced by the exchange calendar.)

### Explanations (core `PriceReactionExplainer`)
Deterministic cases A–H from the spec (beat/miss × up/down, mixed, no estimates, no price data, incomplete
window), plus flat and unknown-time wording. Built from the Phase 2 classifications (reused, not
recalculated). Tested against advice/causal phrases. Learn links: existing "How is the price reaction
measured?" and "Why can a stock fall after beating earnings?", plus new topics "Earnings expectations
explained", "What is after-hours trading?", "What is market volatility?" in `EarningsEducation`.

### MOCK scenarios (Phase 3)
| Scenario | Fixture |
| --- | --- |
| Beat & price up (spec example $150.00 → $157.50, +5.0%), after close, US, complete chart, 3/5 sessions incomplete | SSRV Q3 FY2026 |
| Beat & price down (−3.2%), before the open | SSRM Q1 FY2027; AAPL Q3 (real fixture) |
| Miss & price up (+3%), during market hours with a known time (11:30) | SSFC Q3 FY2026 |
| Miss & price down (−10%) | SSLL Q3 FY2026 |
| Mixed EPS/revenue, Canadian, Canada-only holiday (Jul 1) | SSCA.TO Q2 FY2026 |
| Flat price, unknown timing, no estimates | SSNE Q3 FY2026 |
| US-only holiday (Jul 3) | SSHU Q2 FY2026 |
| Half-day session (Dec 24) | SSHD Q1 FY2026 |
| Weekend / holiday announcement | ENB.TO (Sat Aug 1), BB.TO (Dec 25) |
| Missing baseline + chart gaps · missing endpoint / trading halt · zero baseline | SSRV Q2 · SSAN Q3 · SSRV Q3 FY2025 |
| Special dividend (ambiguity) · split in a split-adjusted series | SSLL Q2 · SSRM Q4 FY2026 |
| Adjustment-basis mismatch · currency mismatch | SSFC Q2 · SSFC Q3 FY2025 |
| Provider timeout / rate limit / stale | MOCK scenarios above |
| Revised report, no EPS estimate, no revenue estimate, no logo | SSRV, CSU.TO, CNR.TO, SS* |

### Tests
- Server `EarningsReactionTest` (13, rewritten for the engine): before/after/during/unknown timing,
  3/5-session counting, weekends, US vs TSX holidays, half days, DST completion, incomplete windows,
  missing/zero/currency/basis, corporate actions, rescheduled dates, chart history.
- Server `EarningsPriceReactionServiceTest` (7): spec example, every fixture scenario, same policy as
  Earnings Details, invalid/missing reports, provider failures (no sample data in REAL), routes, rate limit.
- Core `EarningsPriceReactionTest` (3): every explanation case and forbidden phrases, formatting/chart/
  accessibility, presenter window switching, offline copy, retry, window restore.
- `MockModeTest` validates `price-scenarios.json`.
- No Compose UI / XCUITest automation (no UI-test setup in the repo).

### Production gaps
- No intraday, extended-hours or OHLC data from the configured provider; no corporate-action feed; no
  versioned exchange-calendar source (rule-based calendars; TSX early closes not modelled; unscheduled
  closures only appear as missing data). Finnhub gives no announcement timestamps beyond bmo/amc/dmh.
- REAL behaviour verified only through tests with fakes; no paid calls were made.

---

## Phase 2: Earnings Results & Beginner Explanations

A free **Earnings Results** screen for one company's fiscal period (route
`EarningsResultsRoute(symbol, fiscalYear, fiscalQuarter)`, report id `SYMBOL:YYYY-Qn`, deep link
`earnings-results:<reportId>`). Entry points: Earnings Calendar "View Results" on verified reported
events, Earnings Event Details "View Results", Company Details "Latest results" preview → View
Results, Daily Brief watchlist "Reported quarterly results" highlights. An unpublished period shows
"Results for this period haven't been published yet." — never a placeholder result.

Sections: header (company, ticker/exchange, fiscal quarter/year, period end, report date, source
publication time or "not provided", revised flag, sample/freshness labels) → EPS card → Revenue card
(one shared `FinancialComparisonCard`, expandable "What is EPS?/revenue?") → "Is the Business
Growing?" (YoY) → previous-quarter card (QoQ) → Beginner Takeaway (+ comparison warnings) → Learn
More (existing `BeginnerEducation` lessons; a missing lesson falls back to the Learn tab) → sources.
The old Earnings Details screen (history, reaction, AI) is unchanged and still reachable from
Company Details "Earnings history".

### Calculations (core `earnings/EarningsResults.kt`, computed on the server)
- **Exact decimals:** provider numbers are read as decimals from their shortest decimal form
  (`EarningsMath.decimal`, 8 places, so `0.1 + 0.2` is `0.3`); money and percentages travel as
  decimal strings; rounding happens only for display.
- **Classification policy (changed from the earlier ±0.5% tolerance):** BEAT / MISS / MET /
  UNAVAILABLE from the exact difference. MET only when equal; no tolerance. `Classification.IN_LINE`
  was renamed `MET`, and the older Earnings Details screen, insights and reminder wording use the
  same rule.
- **EPS:** surprise = actual − estimate; % = (actual − estimate) / |estimate| × 100. Zero estimate →
  amount only; negative estimate (loss expected) → amount only, explained. Unavailable when either
  side is missing, bases differ or are unknown (adjusted vs GAAP), currencies differ, or the estimate
  covers a full year.
- **Revenue:** same, with a positive estimate required; never quarterly vs annual or across currencies.
- **YoY:** same fiscal quarter of the previous fiscal year; **QoQ:** the previous fiscal quarter
  (Q1 → Q4 of the prior fiscal year). Suppressed with a reason when the base is missing, zero or
  negative, in another currency, a different period length, or when the period-end spacing shows a
  fiscal-calendar change (YoY 350–380 days, QoQ 80–100 days). No year-to-date subtraction.
- **Takeaway:** deterministic text per (EPS, revenue) combination — the four spec sentences, a
  miss/miss sentence, and a composed sentence for MET/unavailable mixes. Tested to contain no advice
  words; never an overall "earnings beat".

### API (free, rate-limited like the calendar)
| Endpoint | Notes |
| --- | --- |
| `GET /api/v1/earnings/reports/{reportId}` | Report + insights + freshness. 400 invalid id, 404 `NOT_FOUND` / `NOT_REPORTED`. |
| `GET /api/v1/earnings/reports/{reportId}/insights` | Insights only. |
| `GET /api/v1/earnings/company/{symbol}/latest` | Latest reported fiscal period; 404 `NO_REPORT`. |
| `GET /api/v1/earnings/company/{symbol}/reports` | Every reported period, newest first, with both classifications. |
Calendar items now carry `reportId` (only when figures exist); Daily Brief highlights carry `reportId`.

### Providers, caching, freshness
- REAL: the existing Finnhub `/calendar/earnings` rows supply EPS/revenue actual and estimate per
  fiscal period from one record (so they're comparable; EPS labelled ADJUSTED on both sides). Finnhub
  gives no publication time, no revision flag, no estimate period type and no revenue definition, so
  REAL shows "Publication time not provided", never "Revised", and only quarterly comparisons. FMP's
  quarterly statements are **not** mixed in (different source and basis). Canadian coverage unverified.
- Server: per-symbol history cache (6 h) with one retry for transient failures; the last real copy is
  served as STALE when the provider fails; otherwise 503 (never sample data). Revised figures replace
  the cached version when the history refreshes. Insights are recomputed per request (cheap, deterministic).
- Device: each opened report is saved (public data, per environment) in the existing user-data cache;
  offline, the saved copy is shown with "You're seeing a copy saved on this device."
- MOCK scenarios: `?scenario=provider-timeout` (503), `?scenario=stale-cache` (STALE).

### MOCK fixtures (Phase 2)
| Scenario | Fixture |
| --- | --- |
| EPS beat & revenue beat (spec example $1.45 vs $1.20, $8.5B vs $8.2B, YoY +7.6%, QoQ +4.9%) | SSRV Q3 FY2026 (also revised + no publication time) |
| Beat/beat · beat/miss · miss/beat · miss/miss | NVDA · AAPL Q3, RY.TO (Canadian) · TD · KO Q2 |
| EPS met / revenue met | SSFC Q3 FY2026 / SSRM Q1 FY2027 |
| EPS estimate missing / revenue estimate missing / both missing | CSU.TO Q2 / CNR.TO Q2 / SSNE Q3 |
| Zero EPS estimate / negative estimate / negative actual | RIVN Q2 / BB.TO, SSLL / SSLL, SSNE |
| Loss smaller / larger than expected | SSLL Q2 FY2026 / SSLL Q3 FY2026 |
| Zero / missing previous-year revenue, missing previous quarter | SSNE / SSAN / SSNE |
| Positive / negative / flat revenue growth | SSRV / SSLL / SSRM |
| Fiscal-year rollover, non-calendar fiscal year | SSRM (June FY: Q1 FY2027 vs Q4 FY2026), NVDA, AAPL, MSFT |
| Currency mismatch / adjusted vs GAAP / quarterly vs annual | CSU.TO / JNJ Q2 / SSAN Q3 |
| Fiscal calendar changed (no YoY) | SSFC |
| Partial provider response | BB.TO Q2 FY2027 (no revenue) |
| Provider timeout / stale cache | MOCK scenarios above |
| No report available | GOOGL, scheduled AAPL Q4 |

`SS*` companies are fictional ("StockSteps Demo …"; `DEMO_RESULTS` in the generator).

### Tests
- Core `EarningsResultsTest` (13): decimal conversion and percent, exact classification, EPS
  (beat, zero/negative estimates, losses, basis/currency/annual mismatch), revenue independence,
  YoY/QoQ and suppression rules, fiscal-year rollover mapping, takeaways (deterministic, no advice),
  presenter formatting/accessibility, offline saved copy + retry, unpublished and failure states.
- Server `EarningsResultsServiceTest` (9): spec example, every fixture classification, growth
  rules, latest/list/missing/invalid ids and calendar `reportId`, retry + stale + no sample in REAL,
  MOCK scenarios, revised figures after cache refresh, routes, rate limit.
- Existing tolerance tests were updated to the exact policy.
- No Compose UI / XCUITest automation (no UI-test setup in the repo).

### Limitations
- REAL: no source publication time, revision flag or estimate period type from Finnhub; TSX
  coverage and plan limits not verified live; no paid calls were made.
- Results are free for every reported period (the older Earnings Details history list keeps its
  StockSteps+ limit).
- Not done in Phase 2 by design: price reaction, AI analysis, notifications.

---

## Phase 1: Earnings Calendar

Markets → **Earnings Center** card → **Earnings Calendar** → **Earnings Event Details**. Also from
Company Details ("Earnings" section → View Earnings Calendar), Daily Brief (Upcoming Earnings rows →
event), Watchlist ("Earnings dates for your watchlists" → calendar filtered to My Watchlist) and deep
links (`earnings:<eventId>`, `earnings-calendar`). No new bottom tab. Free for everyone; no paywall.

**What it shows:** dates, expected timing and status only. Calendar cards and event details show
no EPS/revenue figures, surprises or reactions; a reported event links to the existing Earnings
Details ("See reported results"), which is the Phase 2 extension point.

### Domain (core `earnings/EarningsCalendar.kt`)
- `EarningsEventStatus`: SCHEDULED, REPORTED, POSTPONED, CANCELED, UNKNOWN (`EarningsCalendarRules.status`).
  REPORTED needs reported figures or an explicit source flag; POSTPONED/CANCELED need an explicit
  source flag (`EarningsEvent.sourceStatus`). A passed date without results is UNKNOWN ("Status not
  confirmed"), never REPORTED. Finnhub has no postponed/canceled flag, so REAL never shows them.
- New optional `EarningsEvent` fields: `eventTime` (exchange-local "HH:mm", only when the source
  states it), `timeZone`, `sourceStatus`, `sourceUpdatedAt` (null when the provider doesn't say).
  `updatedAt` is when StockSteps recorded the event. Dates stay date-only `yyyy-MM-dd`
  (exchange-local); "today" is computed in the event's exchange zone, so nothing shifts a day.
- Identity: `SYMBOL:YYYY-Qn` (stable across date changes; a rescheduled event keeps one id and shows
  `previousDate`). The instrument identity is the provider's exchange-qualified symbol (`TD` on
  NYSE ≠ `TD.TO` on TSX).
- Timing labels: Before market open · After market close · During market hours · Time not confirmed
  (+ "· 4:05 PM ET" only when the source gives a time).
- Search: ticker prefix or company-name word prefix, case-insensitive ("td" doesn't match "Ltd.").
- `DataFreshness`: FRESH, CACHED, STALE, UNAVAILABLE.

### Presenters (shared by Android and iOS)
- `EarningsCalendarPresenter`: selected date, Day/Week view, Upcoming/Reported, All Companies/My
  Watchlist, debounced (300 ms) server-side search, paging, per-day counts for the week strip,
  freshness text, MOCK scenarios. In-memory first-page cache (5 min, 24 entries), keyed per user for
  the watchlist scope and cleared on account switch or watchlist change. Restorable via
  `CalendarSelection.encode/decode` (Android saves it with the back-stack entry → survives rotation
  and process recreation).
- `EarningsEventPresenter`, `CompanyEarningsPresenter` (next date on Company Details),
  `EarningsSummaryPresenter` (Markets card: scheduled count for the next 7 days, next date, watchlist
  count; nothing shown when unknown).
- Watchlist identity comes from the existing `UserWatchlistsRepository` (`earningsSession()`,
  `watchedSymbols()`); no new watchlist or company repository.

### API (server `earnings/EarningsService.kt`)
| Endpoint | Notes |
| --- | --- |
| `GET /api/v1/earnings/calendar?from&to&view=upcoming\|reported\|scheduled&q&day&pageSize&cursor` | Public. ≤62-day range, page ≤50, `q` ≤40 chars, `day` inside the range. Response adds `dayCounts` (whole range, before paging/day filter), `freshness`, `fetchedAt`, `partial`. `scope` is rejected (400) here. |
| `GET /api/v1/earnings/calendar/search?q=…` | Same, `q` required. |
| `GET /api/v1/earnings/events/{eventId}` | One event (404 when unknown). |
| `GET /api/v1/earnings/company/{symbol}/next` | Next not-reported, not-canceled event; `event` omitted when none is known. |
| `GET /api/v1/me/earnings/following?…&scope=watchlist` | Signed in. The user's own watchlists (from the verified token), deduplicated across lists by canonical symbol; `followedCount` (0 = empty watchlists). |

`view=results` still works (alias of `reported`). The earlier `/following` without `scope`
(portfolio + watchlists) is unchanged and still used by the Daily Brief.

### Provider mapping, caching, freshness
- REAL: Finnhub `/calendar/earnings` (date, bmo/amc/dmh, fiscal period) through the existing
  `FinnhubEarningsDataSource`; no new paid endpoint and no availability probing. Finnhub gives no
  exact time, no status flag and no source timestamp, so REAL shows timing only from bmo/amc/dmh,
  never POSTPONED/CANCELED, and "The source didn't say when this was last updated".
- Profiles (names/logos) are fetched only for the events on the returned page (no N+1 per week).
  Company-name search in REAL uses the existing (cached, 1 h) stock search for US listings; if it
  fails the page is marked `partial` and ticker search still works.
- Server cache: windows 1 h (12 h for past windows), per-symbol history 6 h. FRESH = fetched in this
  request, CACHED = from the cache. On a source failure the last real copy is served as STALE with
  its fetch time; with no copy → 503. Never replaced with sample data.
- Postponed/canceled events are excluded from the existing reminders' `next()`.

### MOCK scenarios (fixture `earnings/events.json`, MOCK clock 2026-10-07 17:15 New York)
| # | Scenario | Fixture |
| --- | --- | --- |
| 1 | Upcoming today | INTC Oct 7 (after close, source time 4:05 PM ET) |
| 2–3 | Tomorrow, several on one day | CRBU (before open), TOPP (after close), SPEC (time not confirmed) Oct 8 |
| 4 | No earnings on a date | Oct 10–11 (weekend), most dates |
| 5–7 | Before open / after close / unknown time | CRBU / TOPP, INTC / SPEC |
| 8 | Recently reported | LUCY Oct 7 (verified figures) |
| 9 | Past event, status unknown | BCE.TO Oct 6 |
| 10 | Rescheduled | CNR.TO Oct 20 → Oct 22 |
| — | Postponed (explicit flag) | SSPX "StockSteps Demo Postponed Corp." Oct 9 (was Oct 8) |
| 11 | Canceled (explicit flag) | SSCX "StockSteps Demo Canceled Corp." Oct 13 |
| 12–13 | US / Canadian | most / RY.TO, CNR.TO, SHOP.TO, TD.TO… |
| 14–17 | On / not on watchlist, empty, multiple watchlists | your MOCK account's watchlists (tests cover dedup across lists) |
| 18 | Duplicate ticker across exchanges | TD (NYSE) and TD.TO (TSX) Nov 30 |
| 19 | Missing logo | all calendar demo companies (avatar fallback) |
| 20 | Missing source timestamp | SPEC |
| 21 | Provider unavailable | Calendar "Sample scenarios" chip → `scenario=provider-unavailable` (503) |
| 22 | Stale cached events | chip → `scenario=stale-cache` (STALE, fetched 3 h earlier) |
| 23–24 | Search with / without results | "intel", "td" / "zzzz" |
| 25 | Pagination | page size 20 (tests use 2–3) |
| 26–27 | Account switching, app restart | presenter cache per user; Android saved state |
| 28 | Market holiday | GCDT Oct 12 (Canadian Thanksgiving; US open) |
| 29 | Weekend | ENB.TO Sat Aug 1; empty weekend days |
| 30 | No next date for a company | GOOGL ("Next earnings date not available.") |

Scenarios are MOCK-only (ignored in REAL). Demo events were added with
`scripts/generate_earnings_fixtures.py` (`CALENDAR_DEMO`); existing events are unchanged apart from
the new `sourceUpdatedAt` field.

### Tests
- Core `EarningsCalendarTest` (12): status rules, timing, week math, bounded search ranges, name
  search, selection restore, accessibility labels; presenter week/day counts, navigation + cache,
  debounced search, watchlist sign-in/account switch/list change, errors/stale/paging, event details,
  Company Details next date, Markets summary.
- Server `EarningsCalendarServiceTest` (10): verified status, day counts/day filter/validation,
  date-only + timing, search, paging, watchlist scope dedup + isolation + public rejection,
  fresh/cached/stale + no sample fallback, MOCK scenarios, event/next endpoints, routes and auth.
- No Compose UI / XCUITest automation (there is no UI-test setup in the repo); UI state is covered
  through the presenters.

### Limitations
- Date and number formatting is English (the app has no other locale); dates are shown in the
  exchange's local date, not converted to the device time zone.
- iOS restores the calendar selection within a session only (no process-death restoration).
- In REAL, company-name search covers US listings (existing stock search); TSX by ticker.
- REAL Finnhub coverage of TSX and plan limits weren't verified live (no paid calls were made).

---

Earlier work: **Earnings Details** for a company (results, history, reactions, AI). The former
Earnings Center list (Upcoming · Results · Following) was replaced by the Phase 1 calendar above. These are pushed destinations; the bottom bar is unchanged. Entry points:

- Markets tile
- Company Details "Earnings" button
- Home earnings events (open Earnings Details)
- Earnings Center rows
- Watchlist and Portfolio, via the Following tab and Company Details

Android (Compose) and iOS (SwiftUI) render the same shared Kotlin presenters.

## What existed and was reused

- **Reminders:** `AlertType.EARNINGS` with day-before/day-of timing, evaluated by the existing
  `AlertEvaluator` → `AlertRules` → idempotent `recordTrigger` → notification outbox → FCM/APNs
  pipeline. No second scheduler was created.
- **Upcoming dates:** `UpcomingEarnings`, already served through `/api/v1/stocks/watch-data` to
  Home and Watchlist.
- **Entitlements:** `EntitlementService` (StockSteps+, server-authoritative, MOCK debug simulation).
- **Prices:** `PriceChartService.getDailyCloses` (shared cache).
- **Profiles:** `StockService.getProfile` (24 h cache).
- **Education:** `MetricEducation` (EPS and revenue text).

## Architecture

| Layer | Files |
| --- | --- |
| Models | `core/.../earnings/EarningsModels.kt` (event with stable id `SYMBOL:YYYY-Qn`, estimate, actual, EPS basis, statuses, surprise, reaction, insight, details, calendar) |
| Calculations / insights / education | `EarningsCalculations.kt` (`EarningsCalculator`, `EarningsInsightEngine`, `EarningsEducation`) |
| Presenters / formatting | `EarningsPresentation.kt` (`EarningsCenterPresenter`, `EarningsDetailsPresenter`, `EarningsFormatter`, `RemoteEarnings`) |
| Server | `server/.../earnings/EarningsSources.kt` (fixture + Finnhub sources, `EarningsNormalizer`), `EarningsReaction.kt`, `EarningsService.kt` (+ routes) |
| Reminders | `AlertRules.earnings` / `results`, `AlertsService` (StockSteps+ options), `AlertEvaluator` (`recentEarningsResult`) |
| Home | `PersonalDashboardRules.events` (portfolio first, dedupe, weekly summary), `PersonalDashboardStore(ownedSymbols)` |
| Android | `presentation/earnings/{EarningsRoute,EarningsScenes,EarningsScreens}.kt` |
| iOS | `EarningsScenes.swift`, `IosEarningsClient.kt` |
| Fixtures | `scripts/generate_earnings_fixtures.py` → `fixtures/earnings/events.json` (136 events, 22 companies) |

There is one earnings source per environment, behind `EarningsService`. The calendar, details,
watch-data (Home and Watchlist) and reminders all read it, so they always agree. In MOCK, the old
per-stock `earnings-upcoming.json` files were replaced by `events.json`.

## API

**Public:**
- `GET /api/v1/earnings/calendar?from&to&exchange&country&session&symbol&view=upcoming|results&pageSize&cursor`
  - A date range is required, up to 62 days (never a whole year).
- `GET /api/v1/earnings/{symbol}`: details at the free tier.

**Signed in:**
- `GET /api/v1/me/earnings/following?from&to…`: portfolio holdings and watchlist companies,
  deduplicated, each labelled with why it's followed.
- `GET /api/v1/me/earnings/{symbol}`: details shaped by the user's tier.
- `POST /api/v1/me/earnings/{symbol}/ask` `{"question": …}`: StockSteps+ only. The server
  returns 403 before any AI provider is called, and applies a fair-use daily limit
  (`EARNINGS_AI_DAILY_LIMIT`, default 20).

**Reminders** use the existing `/api/v1/me/alerts` (type `EARNINGS`) with new optional fields:
`earningsLeadDays` (1–7), `earningsResults`, `earningsSurprisePercent`. These are StockSteps+ and
server-enforced; free users get 403 `PLUS_REQUIRED`.

**Rate limit:** `EARNINGS_REQUESTS_PER_MINUTE` per client (default 120).

## Calculations

**EPS surprise**
- Absolute surprise = actual − estimate.
- Percent surprise = (actual − estimate) ÷ |estimate| × 100.
- The percentage is shown only when |estimate| ≥ $0.01. With a negative estimate, a smaller loss
  is a positive surprise.
- **In line** when |difference| < $0.005 (rounding) or within ±0.5% of |estimate|. Otherwise beat
  or miss.
- **Not comparable** when the estimate or actual is missing, the currencies differ, or the EPS
  bases differ or are unknown (e.g. adjusted estimate vs GAAP diluted actual).

**Revenue surprise**
- Percent surprise = (actual − estimate) ÷ estimate × 100.
- Only calculated for a positive estimate in the same currency. In line within ±0.5%.
- Amounts are raw currency units; display adds B/M/T.

**Growth**
- Year over year: the same fiscal quarter of the prior fiscal year, same currency only.
- Quarter over quarter: the previous fiscal quarter, shown as context with a seasonality note.
- Nothing is interpolated.

> Phase 2 note: the tolerance rules described in this older section were replaced by the exact
> policy (MET only when equal); see "Phase 2" above.

**Status (from data, not the clock)**
- Reported: EPS and revenue.
- Partially reported: one of the two.
- Results pending: the date has passed with no results.
- Upcoming.
- Unavailable: no date known.

**Summary**
- A headline such as "EPS beat, revenue miss", plus an explanation built from the classifications.
- It says that EPS and revenue can tell different stories and that results don't determine how
  the stock moves.

**Insights** (deterministic):
- Free:
  - EPS surprise percentage.
  - Mixed result (beat on one, miss on the other).
  - Revenue year over year.
- StockSteps+ ("advanced"):
  - A consecutive-beat streak: needs at least 3 consecutive fiscal quarters with comparable
    estimates; any gap or basis mismatch breaks it.
  - Revenue growth accelerating or slowing: needs two year-over-year rates.
- Insights never mention guidance or management commentary (none is available), never use advice
  words, and never make predictions. Tests check this.

## Calendar and date confidence

- **Date status:**
  - `CONFIRMED` only when the source says the company confirmed it.
  - Provider dates are otherwise `ESTIMATED`. Finnhub doesn't flag confirmation, so in REAL a date
    shows as estimated until the event is reported.
  - Also `TENTATIVE` and `UNKNOWN`.
- **Session:** `BEFORE_OPEN`, `AFTER_CLOSE` or `DURING_MARKET`, from the provider's bmo/amc/dmh
  hint only. Anything else is "Time not announced", never inferred from a timestamp.
- **Display:** dates are exchange-local and shown as e.g. "Wed, Oct 14". Screen readers hear the
  full date.
- **Sorting:** by date, then session (before open, during market, after close, unknown), then
  company name.
- **Ranges:** this week and next week (weeks start on Monday), next 30 days, last week, last 30 days.
- **Filters:** US/Canada market, exchange (NASDAQ/NYSE/TSX), session. Following is a separate tab.
- **Changes and staleness:** a moved date keeps `previousDate`, so the change is visible. Upcoming
  dates not refreshed in 14 days are labelled stale.

## Price reaction

> Superseded by Phase 3: Earnings Details now uses `PriceReactionEngine` (exchange calendar). The rules below are historical.

`EarningsReactionCalculator` used regular-session daily closes:

- **Trading days and time:** a trading day is any day with a close, so weekends and exchange
  holidays are skipped. Times are exchange-local (`America/New_York`, or `America/Toronto` for
  TSX), and daylight saving time follows the zone rules.
- **Windows by session:**
  - **Before the open:** previous close → that day's close. A weekend or holiday announcement uses
    the next session.
  - **After the close:** that day's close → the next session's close.
  - **During market hours:** previous close → that day's close, labelled as including trading
    before the announcement.
  - **Unknown time:** previous close → the first close after the date, labelled approximate.
- **Not shown when:**
  - results haven't been reported
  - the end session hasn't completed (4:15 pm exchange-local)
  - prices are missing
  - there's a gap of more than 10 days
- **Market comparison:** the same window for SPY (US listings only; no TSX proxy fixture).
- **Wording:** "The stock rose/fell X% over the measured earnings window", plus a note that other
  news and market moves also affect prices. It never says why.
- **Caching:** cached for 7 days once complete, 10 minutes otherwise.

## Reminders

- **Free:**
  - Day before and/or day of, within the existing alert limit.
  - Unknown sessions use the date: 9:00 the day before, 7:00 on the day (New York).
- **StockSteps+:**
  - Lead time of 2–7 days.
  - "Results are out" (once per event).
  - An optional minimum verified EPS or revenue surprise.
- **Deduplication:** event keys use the stable fiscal-period id
  (`rule:earnings-AAPL:2026-Q4-DAY_BEFORE`). If a date moves within the same fiscal period, the
  existing idempotent `recordTrigger` won't send the same reminder for both the old and new date.
  Rules without an event id keep the previous date-based key.
- **Cancellation:** turning a reminder off deletes the alert rule. Device tokens, history and
  retries are the existing push infrastructure.
- **Permissions:** notification permission, sign-out and account isolation follow the existing
  Alerts behaviour.

## StockSteps+

| Free | StockSteps+ |
| --- | --- |
| Calendar, upcoming dates, EPS/revenue actual vs estimate, beat/miss, the latest 4 quarters of history, basic price reaction, basic reminders, all education | Full available history, streak and acceleration insights, advanced reminder options, AI earnings research |

All of this is enforced on the server. Free users see the AI entry point with a StockSteps+ badge.
Tapping it opens the upgrade sheet and sends nothing; "See StockSteps+" goes to Settings, because
there's no purchase flow yet.

## AI

- `EarningsResearchProvider` is the integration boundary. Questions are only sent on an explicit
  user tap, by Plus users, with public earnings data only (never portfolio holdings).
- **MOCK:** `TemplateEarningsResearch` answers deterministically from the verified metrics. For
  "why" questions it says the data can't explain a cause.
- **REAL:** no provider is configured, so the endpoint answers 503 `AI_UNAVAILABLE`. A future
  Gemini-backed provider would plug in here, reusing the existing Gemini client and grounded in
  the same details, company news and statements.

## FMP / Finnhub capability matrix

The status column uses three meanings:
- **Used** — called by the REAL backend.
- **Documented** — in the provider's public docs but not verified live. No paid quota was spent
  for this feature.
- **Plan** — depends on the subscription tier.

| Capability | Finnhub | FMP | Status / notes |
| --- | --- | --- | --- |
| Earnings calendar (date, bmo/amc/dmh, quarter/year, EPS & revenue estimate and actual) | `/calendar/earnings` | `earnings-calendar` | Finnhub **used** (was already used for reminder dates); FMP not used, so providers are never mixed per event |
| Historical earnings per symbol | `/calendar/earnings?symbol=&from=` (3 years requested) | `earnings?symbol=` | Finnhub used; depth depends on plan, so extended history means "available coverage" |
| Earnings surprises | `/stock/earnings` (last 4 quarters, EPS only) | — | Not used (no revenue) |
| Company-confirmed date flag | not provided | not provided | So REAL dates stay "Estimated" until reported |
| EPS basis | not labelled; consensus-comparable (typically adjusted) | not labelled | Both sides labelled ADJUSTED because they come from the same record |
| Profiles / logos | — | `profile` | Used (cached 24 h) |
| Historical prices | — | `historical-price-eod/light` (split-adjusted) | Used for reactions |
| Exchange trading sessions | — | — | Derived from days with closes, plus the exchange time zone |
| Guidance | not in free calendar | transcripts (plan) | Not shown; never invented |
| US coverage | yes | yes | Documented |
| Canadian (TSX) coverage | partial, plan | partial, plan | Not verified; MOCK covers TSX |
| Rate limits | 60/min free | plan-based (429 seen 2026-10-06) | Calendar windows cached 1 h (12 h for past windows), history 6 h, reactions 7 days; per-client limiter |
| Display rights | per Finnhub terms | per FMP terms | Review before launch |

## MOCK scenarios

All 38 scenarios from the spec are covered.

**Dates and sessions**

| Scenario | Fixture |
| --- | --- |
| Confirmed date | AAPL Oct 29, JPM Oct 14 |
| Estimated date | MSFT, KO |
| Tentative date (also stale) | RY.TO |
| Unknown session | TSLA, SHOP.TO |
| Before open | JPM, KO |
| After close | AAPL |
| Date changed | CNR.TO, Oct 20 → Oct 22 |
| Weekend announcement | ENB.TO, Saturday Aug 1 |
| Holiday announcement | BB.TO, Dec 25 |

**Results and surprises**

| Scenario | Fixture |
| --- | --- |
| Beat / beat | NVDA (also an 8-quarter streak) |
| Miss / miss | KO Q2 |
| In line | XOM, SHOP.TO EPS |
| EPS beat, revenue miss | AAPL Q3, RY.TO |
| EPS miss, revenue beat | TD |
| Negative EPS | BB.TO, RIVN estimates |
| Zero EPS estimate | RIVN Q2 |
| Missing EPS estimate + currency mismatch | CSU.TO |
| Missing revenue estimate | CNR.TO Q2 |
| EPS basis mismatch | JNJ Q2 |
| Partial results | BB.TO |
| Results pending | BCE.TO |

**Price reactions**

| Scenario | Fixture |
| --- | --- |
| Price rose after earnings | MSFT |
| Price fell after earnings (despite an EPS beat) | AAPL |
| Missing historical prices | LONGN |

**Markets, following and accounts**

| Scenario | Fixture |
| --- | --- |
| Canadian companies | RY.TO, ENB.TO, CNR.TO, SHOP.TO, BCE.TO, CSU.TO, BB.TO, TD |
| US companies | the rest |
| Following | from your MOCK watchlists and portfolio |
| Empty calendar | late December |
| Free / Plus / expired | MOCK plan switch in Settings → Development |
| Reminder create, cancel, dedup | through Alerts |
| Provider failure, partial failure | in tests |
| Stale data | RY.TO |

**Consistency:** for companies with a fundamentals fixture, the four quarters of a completed
fiscal year add up to that year's annual revenue in Financials (tested). Price scenarios use the
same daily closes the server measures.

## Tests

- **Core** (`EarningsCalculationsTest`, `PersonalDashboardRulesTest`):
  - EPS: beats and misses, negative estimates and actuals, zero and missing estimates, tolerance,
    basis and currency mismatch.
  - Revenue: surprise and tolerance; growth across periods, fiscal years and currencies.
  - Status from data, summary wording, insight provenance, streak and acceleration validity,
    education.
  - Dates, countdowns, sessions, and week ranges starting on Monday.
  - Presenter: paging, filters, the Results tab, Following without sign-in, failures, and AI
    gating while signed out.
  - Home: portfolio holdings first, deduplication, summary, nothing invented for new users.
- **Server** (`EarningsReactionTest`, `EarningsServiceTest`, `EarningsRemindersTest`):
  - Reactions: before open, after close, unknown time, weekend and holiday, missing prices,
    incomplete session, DST, exchange time zones, wording that never claims a cause.
  - Finnhub normalization with missing fields.
  - Calendar: sorting, filters, Canada, paging, range limit, empty calendar, stale data.
  - Following: portfolio vs watchlist and per-user isolation.
  - Tiers: free, Plus and expired shaping; Financials consistency.
  - Results: pending, partial, missing, basis and zero-estimate cases.
  - Sources: one source for `next` and watch-data; AI Plus-only with fair-use limit and no
    provider in REAL; provider failure 503.
  - Routes: authentication.
  - Reminders: a moved date never notifies twice; lead days, results and threshold; Plus
    enforcement; cancellation.
- `MockModeTest` validates `earnings/events.json` (unique ids, one source per event).

## Limitations / next

- The REAL Finnhub calendar and history (coverage, TSX, plan limits) weren't verified live. There
  is no company-confirmation flag in REAL.
- No guidance or management commentary; AI isn't wired to a provider in REAL.
- There's no TSX market proxy, so Canadian reactions have no market comparison.
- Watchlist rows don't show an earnings badge of their own. Watched companies appear in Following
  and Home, and Company Details links to Earnings.
- Portfolio value and weight per upcoming event aren't shown (shares only); per-account values
  stay on the Portfolio screen.
- No UI or snapshot tests; visual, TalkBack/VoiceOver, Dynamic Type and landscape checks are manual.
