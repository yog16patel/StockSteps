# Daily Market Brief

A short, source-backed, educational summary (NUMBER → CONTEXT → EXPLANATION → EDUCATION). Not advice:
no predictions, no buy/sell language, no invented causes. Bottom navigation is unchanged.

## Entry points

- **Home** — compact preview right after the header: icon, "Your Daily Market Brief", freshness
  ("After-close brief · Updated 30 min ago", or "Latest available · Oct 7" when older than 20 hours —
  never "today" for an old brief), one-line summary, reading time from the actual text, "Read Today's
  Brief" / "Read Latest Brief". The older watchlist-facts section was renamed "Today in your watchlist".
- **Markets** — the same preview near the top with "Previous briefs".
- **Notifications** — opt-in "Your StockSteps Market Brief is ready." with data `type=daily-brief,
  briefId`; Android (`MainActivity`) and iOS (`PushNotifications.swift`) open that brief.
- Destinations: `DailyBriefRoute(briefId?)` and `DailyBriefHistoryRoute` (Android); `BriefTarget`
  and `DailyBriefHistoryScene` (iOS).

## Reader sections

1. Header — title, subtitle, edition, brief date, session date, updated time, reading time, US/Canada
   session status; stale/offline banners; MOCK scenario menu (sample data only).
2. Market at a Glance — S&P 500, Nasdaq Composite, S&P/TSX Composite: value, change, %, and a state label:
   `Close · Oct 7`, `During today's session (may be delayed) · 11:02 AM ET`, `Previous close · … (today's
   values aren't available yet)`, `From an earlier session (Oct 2)`, `Not available right now`. One
   missing index never hides the others. Plus a plain index explainer.
3. Market Stories — up to 3: headline, provider excerpt (≤ 2 sentences), publisher (or "Publisher not
   provided"), time, related symbols, an evidence note, "Read article" (original URL), "Understand More".
4. From Your Watchlist — signed-in only: price moves ≥ 2% from the brief's own session (no cause),
   upcoming earnings (≤ 7 days, date status shown), recent results, recent company news (≤ 48 h).
5. Upcoming Earnings — followed companies (watchlist and real-portfolio holdings from the existing
   earnings service), else a few labelled general examples; estimated vs confirmed, timing only when known,
   EPS estimate when available.
6. Concept of the Day — from the Learn catalogue (`BeginnerEducation`), with "Understand more" (the
   shared explanation sheet) and "Open Learn".
7. StockSteps+ insights — Plus: deterministic personalized facts, extra company stories, Ask box;
   Free: a short preview (no full-screen paywall).

## Architecture

- **Core (`core/.../brief`)**: models, `BriefPolicy` (Free: 3 history, 3 highlights, no AI; Plus: 60
  history, 12 highlights, 4 company stories, AI), `BriefContent` (concept, reading time), `StoryRanker`,
  `BriefWording` (neutral "rose/fell/about unchanged"), `DailyBriefPresenter` (+ `BriefFormat`).
- **Server (`server/.../brief`)**:
  - `BriefSessions` — US (existing `UsMarketCalendar`) and new `TsxMarketCalendar`, explicit zones
    (America/New_York, America/Toronto). Editions from the US session: PRE_MARKET (describes the last
    completed session), MARKET_HOURS, AFTER_CLOSE, WEEKEND, HOLIDAY; Canadian status shown separately.
  - `DailyBriefService` — layer 1 global brief per edition (`{date}-{edition}`), built from
    `MarketsBriefSource` = the cached Markets overview (no extra provider calls per user), kept in memory
    and persisted (`dailyBriefs/{id}` in Firestore; in-memory in MOCK). Market-hours briefs refresh every
    15 min, keeping `generatedAt` and moving `updatedAt` only with new data; other editions are built once.
    Layer 2 `personalized(uid, id)` — private, per-user cache key includes plan and watchlist revision.
  - Story pipeline: validate (https URL, headline) → normalize → dedupe (URL or ≥ 60% headline words) →
    **market relevance** (company link or market vocabulary; off-topic news never selected) → rank
    (recency, company link, watchlist, earnings, publisher present) → diversity (one per company/topic).
    Fewer than three suitable → fewer shown. Only provider ids and URLs are used.
  - AI (`BriefAi.kt`): Plus checked first, then input validation, then provider; daily quota
    (`BRIEF_AI_DAILY_LIMIT`, default 15); bounded context (index facts + selected stories only);
    `BriefAiValidator` rejects links, unknown source ids, numbers not in the context, advice, predictions
    and invented causes. MOCK: `TemplateBriefAi` (labelled "Sample explanation"); REAL: `GeminiBriefAi`
    on the existing Gemini JSON client when `GEMINI_API_KEY` is set, otherwise 503.
  - Notifications: `dispatch()` sends to opted-in users at their local delivery hour, once per brief
    (claimed atomically before sending), not in quiet hours, not when both markets are closed; invalid
    tokens are removed. Uses the existing devices and `PushSender` (MOCK: simulated, nothing sent).

## API

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/daily-brief/latest[?scenario=]` | Public; `scenario` MOCK only (404 in REAL) |
| GET | `/api/v1/daily-brief/history` | Public: latest 3 |
| GET | `/api/v1/daily-brief/{id}`, `/{id}/sources` | Public within the free history (403 `HISTORY_LOCKED`) |
| GET | `/api/v1/me/daily-brief/history`, `/{id}` | Signed in; Plus sees full history |
| GET | `/api/v1/me/daily-brief/{id}/personalized` | Signed in; private overlay |
| POST | `/api/v1/me/daily-brief/{id}/ai/explain` `{storyId}` / `ai/ask` `{question, storyId?}` | Plus only (403 `PLUS_REQUIRED`), 429 `AI_LIMIT`, 503 `AI_UNAVAILABLE`, 502 `AI_UNRELIABLE` |
| GET/PUT | `/api/v1/me/daily-brief/preferences` | Opt-in, hour, time zone, markets, Plus-only personalized text, quiet hours |
| POST | `/internal/daily-brief/dispatch` | Scheduler token (`ALERTS_EVALUATOR_TOKEN`) in REAL; open in MOCK |

Public routes are rate limited (`BRIEF_REQUESTS_PER_MINUTE`, default 120) and never contain user data.

## Client caching

Public briefs: the last 5 opened briefs are stored on the device (`env|public`) and shown offline with
an "Offline copy" label; nothing is promised that wasn't downloaded. Personal overlays: per account
(`env|user:uid`), cleared from memory on account change. Sign-out also clears account caches (existing).

## MOCK scenarios

`normal`, `after-close`, `pre-market`, `market-hours`, `weekend`, `holiday` (Christmas), `us-open-ca-closed`
(Canadian Thanksgiving), `ca-open-us-closed` (US Thanksgiving), `three-stories`, `one-story`,
`no-stories`, `duplicate-stories`, `missing-index`, `stale-index`, `missing-publisher`, `invalid-url`,
`ai-unavailable`, `ai-quota` — from the reader's Scenarios menu (sample data only). Scenario briefs have
ids ending in `-mock-{name}` and are never stored in history. Watchlist (empty/populated), Free/Plus/expired
(Settings → simulated plan), offline/no cache, history, notifications off and account switching use the
real app state and are covered by tests.

## Tests

- Core `brief/DailyBriefTest.kt`: validity, dedupe, ranking/diversity/relevance, excerpts, reading time,
  concepts, wording, policy, formatting/freshness, presenter (anonymous, account switch isolation, Free
  upgrade without sending, offline copies only, locked history).
- Server `brief/DailyBriefServiceTest.kt`: sessions (pre/open/after, weekend, US/CA holidays, DST),
  TSX holidays, quote states and wording, stale/missing indices, provider failures, shared global cache,
  history limits, AI gating/quota/unavailable/expiry, validator, preferences, notification
  dedupe/quiet hours/weekend, per-user isolation, routes (anonymous vs signed in, REAL has no scenarios
  or open scheduler, rate limit), every MOCK scenario over the real fixtures.

## Not done / limits

- **Scheduled generation/delivery isn't deployed**: briefs are built on first request per edition;
  `POST /internal/daily-brief/dispatch` needs a Cloud Scheduler job (e.g. every 15 minutes) with the
  token. MOCK can trigger it manually.
- Notification delivery uses the existing push stack; there's no retry queue for brief notifications.
- REAL AI depends on `GEMINI_API_KEY`; no provider usage beyond the Markets overview cache is added.
- Story summaries are provider excerpts; StockSteps doesn't write its own summaries or store articles.
- TSX early closes aren't modelled; Canadian session labels use the TSX holiday rules.
- No UI-automation tests; screens are covered through presenter and server tests.
