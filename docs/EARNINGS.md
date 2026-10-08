# Earnings Intelligence & Earnings Calendar

Markets → **Earnings Center** (Upcoming · Results · Following) and **Earnings Details** for a
company. These are pushed destinations; the bottom bar is unchanged. Entry points:

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

`EarningsReactionCalculator` uses regular-session daily closes:

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
