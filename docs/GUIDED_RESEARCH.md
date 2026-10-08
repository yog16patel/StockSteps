# Guided Stock Research & Interactive Beginner Learning

A five-question guide that teaches beginners how to research one company with that company's own
reported numbers. Every step follows **NUMBER → CONTEXT → EXPLANATION → EDUCATION**. It never shows
buy/sell/hold, scores, ratings or predictions. All lessons, steps and quizzes are free; only
"Ask StockSteps AI" requires StockSteps+ (enforced by the backend). Advanced analysis belongs in
PortIQX, not here.

## The five steps

| # | Question | Data (reused, never redefined) | Visual | Missing / special cases |
|---|---|---|---|---|
| 1 | What does {Company} do? | Profile: name, sector, industry, exchange, description (first 3 sentences) | — | No description → "isn't available"; segment revenue is never estimated |
| 2 | Is {Company} growing? | Latest FY revenue, previous FY revenue, `revenueGrowth` fact (else computed from the two years) | Annual revenue bars, ≤5 fiscal years, oldest first, one currency only | Within ±1% → "about the same"; previous year ≤0 → no percentage; one year only → explained |
| 3 | Is {Company} making money? | FY revenue and net income, `netMargin`, `freeCashFlow` | Revenue vs profit/loss bars (labelled educational) | Loss explained without alarm; "about $X for every $100 of revenue" |
| 4 | Does {Company} have a lot of debt? | `cash`, `debt` (balance-sheet date), net debt/net cash, optional `debtEquity` | Cash vs debt bars | Banks/insurers (`Financial Services`): simplified explanation, no ratios |
| 5 | Is {Company}'s stock expensive? | Quote price, FY diluted EPS (labelled), TTM `pe`, five-year P/E average only when `reliable` | P/E now vs 5-year average | EPS ≤ 0 or non-positive denominator → "P/E not meaningful"; TTM vs FY difference noted |

Each step shows: question, description, intro, the numbers (with period and currency), what this
means, keep in mind (limitation), key takeaway, "Learn the terms" (education sheets), an optional
quiz, "Ask StockSteps AI", and Back/Continue (Finish on step 5).

Company kinds: `OPERATING`, `FINANCIAL` (sector Financial Services), `FUND` (profile `isEtf == true`,
never guessed from the name) and `UNSUPPORTED` (no statements). Funds and unsupported companies can
still open every step; data steps explain why company figures don't apply or aren't available.
Statements older than ~18 months, or fundamentals retrieved more than 30 days ago, add a stale note.
Section errors from Company Details are listed.

## Architecture

- **Core (`core/.../learning`)**
  - `BeginnerEducation` — the single education catalogue (`EducationEntry`: id, topic, title,
    short, why, interpret, limitations, detailed, example, relatedMetrics, version). "What it is"
    reuses `MetricEducation` text, so no metric has two definitions.
  - `GuidedResearchContent` — reviewed step wording and the five quizzes, `VERSION = 1`.
  - `GuidedResearchEngine.build(CompanyDetails, today)` — deterministic `ResearchSnapshot` of five
    `StepView`s; no AI, no network.
  - `QuizEngine` — single-choice answers with immediate feedback; retry always allowed; no timers,
    scores or penalties. A quiz saved under an older content version counts as not attempted.
  - `GuidedResearchRepository` — loads `CompanyDetails` **once per company** (15-minute memory
    cache) via the existing `CompanyDetailsRepository`, so the guide's numbers match Company Details.
  - `LearningProgressRepository` — local-first progress (see below).
  - `GuidedResearchPresenter` — overview (0), steps (1–5), summary (6); `startOrContinue`, `open`
    (no step is locked), `next` (marks the step complete), `back`, `answer`, `retryQuiz`, `restart`,
    `requestAi`/`ask` (Plus gating), `close`.
- **Server (`server/.../learning/LearningService.kt`)** — `GET/PUT /api/v1/me/learning` (merge per
  company, most recent visit wins; validated) and `POST /api/v1/me/research/{symbol}/ask`.
  Storage: `UserDataStore.updateLearning` (InMemory for MOCK/tests, Firestore
  `users/{uid}/meta/learning`, Unavailable → 503).
- **Android (Compose)** — `presentation/research` (`GuidedResearchRoute(symbol, name, step)`,
  `GuidedResearchScene` owns the presenter, `GuidedResearchScreen` renders state + actions,
  `UnderstandStockCard`), `presentation/learn` (Learn hub, `MetricInfoIcon`,
  `BeginnerExplanationSheet`).
- **iOS (SwiftUI)** — `IosLearningClient` bridge; `GuidedResearchScenes.swift` (`LearningModel`,
  `ResearchModel`, `GuidedResearchScene/Screen`, `LearnScreen`, `UnderstandStockCard`,
  `BeginnerExplanationSheet`).

### Entry points

- Company Details → **Understand This Stock** card ("Learn how to research this company in five
  simple steps.") with Start learning / Continue learning ("N of 5 steps completed") / Review research.
- Home → **Continue learning** with the most recently visited unfinished company (real progress
  only); otherwise the small "Learn the Basics" discovery card that opens Learn.
- Learn tab → Continue learning, Start learning (Apple, Coca-Cola, JPMorgan Chase, Microsoft quick
  picks), Research a company (existing search → Company Details → card), Completed research,
  Financial terms (every `BeginnerEducation` entry).
- Summary actions: Review steps, Add to Watchlist (existing watchlist toggle), Company details,
  Research another company (search), Continue learning (Learn tab).

## Progress persistence

`ResearchProgress` per company: symbol, name, completedSteps, currentStep (0 overview, 1–5, 6
summary), quizzes (`QuizRecord(correct, version)`), startedAt, lastVisited, contentVersion. A
`LearningProgressDocument` holds up to 50 companies.

- Saved first in `UserDataCache` under key `learning.progress.v1`, owner `"{env}|user:{uid}"` when
  signed in or `"{env}|guest"` when signed out — so Mock/Real and accounts never mix.
- Signed in: on start the account copy is fetched and merged (per company, latest `lastVisited`
  wins); each change is saved locally and then PUT. A failed sync keeps local progress, shows
  "saved on this device", and retries on the next change or start.
- **Guest progress is never merged into an account.** Signing in switches to the account's
  progress; signing out shows the guest progress again; the existing sign-out clears the account's
  device copies.
- Hosts without an account graph (previews/tests) use in-memory guest progress.

## StockSteps+ AI

`POST /api/v1/me/research/{symbol}/ask` `{question (3–300 chars), step?}`:
1. Signed in required (401). 2. **Plus checked first** — 403 `PLUS_REQUIRED` before any provider
call (an expired plan is refused). 3. Provider missing → 503 `AI_UNAVAILABLE` (REAL today).
4. Fair use: `RESEARCH_AI_DAILY_LIMIT` (default 20/day) → 429 `AI_LIMIT`. Only public company data
is used — never learning progress or holdings. MOCK uses `TemplateResearchAi`: a deterministic
"Sample answer" built from the guide's figures that refuses predictions and recommendations.
Clients show the upgrade dialog for free users and send nothing; mobile never calls an AI service.

## MOCK scenarios (fixtures)

AAPL/MSFT/NVDA/KO/XOM — full five steps with growth bars and P/E; JPM/RY.TO — simplified debt step;
RIVN/loss-making companies — loss explanation and "P/E not meaningful"; SPY/QQQ/sector ETFs — only
price fixtures exist, so they're "unsupported" (engine FUND handling is covered by core tests);
LONGN and similar sparse tickers — missing-data explanations; stale statements — note shown;
guest, signed-in, account switch, sign-out, sync failure, free vs Plus AI, AI fair-use limit.

## Tests

- `core/.../learning/GuidedResearchTest.kt` — engine (all five steps, growth band, zero/one prior
  year, currency mismatch, profit/loss, net cash/debt, banks, ETFs, unsupported, EPS ≤ 0, reliable
  history only, stale, accessibility text, no recommendation words), quizzes, versioning, resume,
  merge, JSON, education completeness.
- `core/.../learning/LearningPresentationTest.kt` — guest persistence across restart, sync to another
  device, guest never merged, account switch/sign-out isolation, Mock/Real separation, sync failure
  and retry, presenter flow (start/continue/back/unlocked steps/summary/review), data loaded once,
  load error retry, quiz answer/retry/saved record, AI gating (guest/free/Plus/server refusal),
  restart per company.
- `server/.../learning/LearningRoutesTest.kt` — auth, merge per user, validation, storage 503, Plus
  gating before provider, template answer, daily limit, expired plan, REAL 503, and the guide over
  every relevant MOCK fixture.

## Limitations

- Content is English only and reviewed in code (`GuidedResearchContent`); no CMS.
- Only single-choice quizzes; one quiz per step.
- System Back leaves the guide (in-screen Back moves between steps).
- MOCK ETFs have no profile fixture, so the FUND path isn't visible in MOCK.
- Segment revenue and management commentary aren't available, so step 1 can't show them.
- REAL AI provider not wired (503); UI accessibility verified by structure, not device audits.
