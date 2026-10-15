# Global UI Refinement Phase 5D.5 — Daily Market Brief and Learn: audit and plan

Status: **READ-ONLY AUDIT AND PLAN. No code changed.** Waiting for the owner's approval before implementation.
Date: 2026-10-14. Base: `main` at `7326e62` (pushed). The only uncommitted files are the earlier session-end handoff edits (`CLAUDE.md`,
`docs/PROJECT_HANDOFF.md`, `docs/project-status.md`). This phase added this file and the reference image
`docs/design/phase5d_5/REFERENCE-daily-brief-learn.webp`.

Plan step 6 of `docs/GLOBAL_UI_REFINEMENT_PLAN.md` ("Learn, Daily Brief and Earnings"). The Earnings part is closed through 5D.4.3. The Earnings
conventions in `CLAUDE.md` are the reference here: borderless sections per call site, first sentence plus "Show details", disclosures folded once,
a SAMPLE badge, sentence case, and shared layout rules.

Method: I read the code statically on all four layers (core, server, Compose, SwiftUI) and compared it with the owner's reference image and
`docs/DAILY_MARKET_BRIEF.md`. **I did not run anything on a device for this audit.** Where a finding depends on runtime behaviour it is marked
*(suspected, to verify on device)*.

---

## 0. The reference image: what we can and cannot copy

The reference (`docs/design/phase5d_5/REFERENCE-daily-brief-learn.webp`) is a **visual direction, not a data or content spec**:

| Reference element | Can we do it? | Why |
|---|---|---|
| Large headline, short summary, "Read full brief" | **Yes**, display only | Uses the server `summaryLine`. The causal wording ("…after strong jobs data") is **not** allowed: StockSteps never states causes it can't source |
| "Key points" numbered list with causes | **Partly** | Causes are forbidden (`StoryRanker.EVIDENCE_NOTE`, AI validator rules). Factual points from existing data are possible (decision D-3) |
| Compact index rows (value, signed change, arrow) | **Yes** | Home already does it (`HomeIndexRow` / Compose `HomeComponents`). The reader still uses one bordered card per index |
| Sparkline per index | **Needs a backend change** | `IndexQuote.trend` exists in the Markets overview, but `BriefIndex` drops it. Adding it is additive and makes no new provider calls (B-1) |
| "Market recap" chart with 1D/1W/1M/3M/1Y and US/Canada/Global tabs | **No** (out of scope) | No intraday or range series exists for the brief. It would need new provider calls. The Markets/Company screens own charts |
| "What moved the markets?" | **No as worded** | Implies cause. Use "In the news" / "Market stories" with the existing evidence note |
| "Stocks in focus" with prices | **Needs a backend change, or a link** | The brief has no stock list. Movers exist in the cached Markets overview (B-2), or link to Markets |
| Learn: Beginner / Intermediate / Advanced filters | **No** (needs content) | The catalogue has no levels. Inventing them is fabricating structure |
| Learn: "Investing basics in 5 lessons", "What is a stock? · 3 min read" | **Needs new content** | No lessons exist. Today Learn has the 5-step company research and 14 glossary terms. The guided research *is* a 5-step "start here" |
| Learn: "Popular topics" icon grid | **Yes**, display only | `EducationEntry.topic` already groups the 14 terms (Business, Growth, Profit, Financial health, Valuation, Income, Earnings, Portfolio). "Popular" is not measured, so the heading would be "Topics" |
| Sample numbers (S&P 500 5,180.74, Apple $182.31, "Thu, Oct 7, 2026") | **Never copied** | They are illustrative. Oct 7, 2026 is a Wednesday. All values come from the API |

---

## 1. Current-state audit — Daily Market Brief

### 1.1 Where it lives
| Layer | Files | Notes |
|---|---|---|
| Core | `core/.../brief/DailyBriefModels.kt` (359), `DailyBriefPresentation.kt` (303: `DailyBriefPresenter`, `BriefFormat`) | Models, policy, `StoryRanker`, `BriefWording`, presenter with offline cache. **No `*Display` object**: screen copy is duplicated in Compose and Swift |
| Server | `server/.../brief/DailyBriefService.kt` (459), `BriefSessions.kt`, `BriefAi.kt`, `DailyBriefRoutes.kt` | Deterministic `summaryLine`, story pipeline, notes, MOCK scenarios (18) |
| Compose | `app/shared/.../presentation/brief/DailyBriefScreens.kt` (371), `DailyBriefScenes.kt`, `DailyBriefRoute.kt` | Reader, history, preferences sheet |
| iOS | `app/iosApp/iosApp/DailyBriefScenes.swift` (421), bridge `app/shared/src/iosMain/.../IosBriefClient.kt` | Reader, history, preferences `Form` |
| Entry points | Home "Markets today" card (`HomeComponents.kt` / `HomeComponents.swift`, `HomeDashboardPresentation`), Markets brief entry (`MarketsScene.swift`, `MarketsScreenPresentation.briefDetail`), notifications | Home and Markets were refined in Phases 4A/4B. **Only the reader and history screens are unrefined** |

### 1.2 Reader order today (both platforms)
In-page title "Your Daily Market Brief" plus "Notify me", then a tagline. After that come up to **six caption lines** (edition · date · read time;
"Market data for the … session · Updated Oct 7 · 21:15 UTC"; one line per market session), the stale/offline banners and the sample row with
Scenarios. Only then does the **summary line** appear. Below it: "Market at a Glance" (3 bordered cards and the full index explainer), "Market
Stories" (bordered cards), "From Your Watchlist", "Upcoming Earnings", "Concept of the Day", "StockSteps+ insights", the notes, the disclaimer and
"Previous briefs". The navigation bar also says "Daily Brief", so the title appears twice.

### 1.3 Evaluation against the brief's criteria
| Criterion | Finding |
|---|---|
| Hierarchy / 30-second test | **Fails.** The answer (the summary line) sits below roughly 8–10 lines of metadata. It is styled `bodyMedium`, the same weight as body text. Index values come next, in tall bordered cards. A beginner needs to scroll before seeing a single number |
| Summary length / repetition | The summary itself is short and factual. Repeated text: the **evidence note is printed in every story card** (the same sentence up to 3×); the "Sample data for development" line appears in the header **and** in `notes`; the index explainer (~60 words) is always expanded; the disclaimer, the notes and the StockSteps+ line ("No predictions or buy/sell advice") stack at the end |
| Index performance | Correct and honest: value, signed change, direction in words, state label ("Close · Oct 7", "From an earlier session"), proxy notes, missing index stays "Unavailable". Visually heavy: one card per index, no trend |
| Important events / stocks | Stories: provider headline + ≤ 2-sentence excerpt, publisher or "Publisher not provided", related symbol. There is no "stocks in focus" (no data). Watchlist highlights (signed in) and upcoming earnings are good and specific |
| States | Loading: a bare progress bar or `ProgressView` (no skeleton). Error: `StockErrorState` / `StockSectionMessage` with retry. Offline: banner plus cached copy (good). Stale: banner (good). Empty stories / missing index: one plain line each (good copy). Personal overlay: loading/error handled. **The banners are hand-made** (`warningContainer` + `textPrimary`), not `StockBanner` |
| Attribution / timestamps / freshness | Every story has a publisher, time and link. **Times are shown in UTC** ("Oct 7 · 21:15 UTC") in the reader, while Home says "Updated 30 min ago" for the same brief. Story times are UTC too. The edition and session are clear |
| Accuracy of explanations | Strong: `summaryLine` is deterministic (`BriefWording`: rose/fell/about unchanged, no adjectives, no causes); the evidence note disclaims causation; the AI path is validated server-side; MOCK answers are labelled "Sample explanation (mock)". **Risk for this phase: the reference invites causal copy. The plan keeps causes out** |
| Navigation | Story symbol → Company Details; watchlist highlight → article / Earnings Results / company; earnings row → Earnings Event; concept → shared explanation sheet + "Open Learn" (tab root). Previous briefs → history. All present on both platforms |
| Components | Mostly raw Material on Android (15 `TextButton`, 3 `AlertDialog`, `AssistChip`, `OutlinedTextField`, 5 `LinearProgressIndicator`). Sentence case is broken ("Market at a Glance", "Market Stories", "From Your Watchlist", "Upcoming Earnings", "Understand More"). iOS: **22 system fonts** (`.font(.caption)`) against 29 theme fonts, `.buttonStyle(.bordered)` on symbol chips (CLAUDE.md button rule), default-style buttons |
| Light/dark | Theme colours are used throughout (no hard-coded colours). In dark mode the banner text is `textPrimary` on `warningContainer`. *Contrast to verify on device* |
| Dynamic Type / large text | Android index row: `Row { Column(weight 1f); Column(values) }`, the "weighted label beside an unweighted value" pattern that broke words in 5D.2. iOS reader index row: `HStack` + `Spacer`, no stacking (Home stacks at `.xxxLarge`). iOS captions use `.caption`, which grows faster than the theme values (the 5A.1 rule). *Suspected word breaks and squeezing at 2.0× / AX5; verify on device* |

### 1.4 History screen
Android: bordered card per brief (`StockCard`), summary clipped to 2 lines with an ellipsis. iOS: plain `List`. Neither has an empty-state component
with explanation, and neither marks sample briefs. The locked count is a tappable card or button. Small, consistent, low priority.

---

## 2. Current-state audit — Learn

### 2.1 What Learn is today
Learn is a **research hub plus a glossary**, not a lesson library:
- the "Understand a stock in five steps" guided research (Guided Research, `GuidedResearchPresenter`), with four quick picks (AAPL, KO, JPM, MSFT);
- Continue / Completed research cards with real saved progress (`LearningProgressRepository`, synced per account; guest progress is ephemeral);
- "Search companies" (then "Understand This Stock" on the company page);
- a Practice entry card;
- "Financial terms": the 14 `BeginnerEducation` entries, each opening `BeginnerExplanationSheet` (what / why / how to read / example / keep in mind).

Files: Compose `presentation/learn/LearnScreen.kt` (130), `LearnScene.kt`, `BeginnerEducationUi.kt`; iOS `LearnScreen` and `BeginnerExplanationSheet`
inside `GuidedResearchScenes.swift`, `LearnScene.swift`; core `learning/BeginnerEducation.kt`, `LearningPresentation.kt`, `GuidedResearch.kt`; server
`learning/LearningService.kt` (research progress + research AI only).

### 2.2 Evaluation
| Criterion | Finding |
|---|---|
| Landing and categories | No categories on screen, although every term has a `topic`. The order is research first, Practice, then **the glossary last** as a flat list of 14 rows. The glossary is the only general learning content, and it is the hardest part to find |
| Discovery and navigation | No filter or search for terms. Contextual links from other screens (Home "Learn and practice", Brief "Open Learn", Earnings, Guided Research "Continue learning") all open the **Learn tab root**, never a topic. Terms open in place as sheets everywhere (good, consistent) |
| Beginner progression | One real path (5-step research) with progress. Terms have no order or progress. The intro says "Every lesson is free", but the screen has no item called a lesson (copy mismatch) |
| Cards and readability | "Start learning" card: title, a 1-sentence description, 4 quick-pick rows. Journey cards: avatar, name, "n of 5 steps completed", progress bar (good). Term rows: title + topic caption |
| Progress tracking | Research progress only (`completedCount` of `ResearchStep.COUNT`). iOS hard-codes `5` and `total: 5` instead of the shared count. No term progress (new feature, §8 L-B2) |
| Accessibility | **iOS Learn has no screen title.** `LearnScene` sets no `navigationTitle` and `LearnScreen` has no heading, so VoiceOver users land on a tab with no name; Android shows an in-page "Learn" heading. Android term rows have **two tap targets for one action** (row `clickable` "Explain" + `MetricInfoIcon` button), which TalkBack reads twice. iOS: 19 system fonts in `GuidedResearchScenes.swift`, `.buttonStyle(.bordered)` on "Search companies" |
| Responsive | No fixed heights on the Learn landing page. Quick-pick rows put the symbol in a trailing slot (could squeeze at AX5; *verify*). Section titles carry `padding(top = md)` on top of the list spacing (uneven rhythm) |
| Parity | Same sections and order on both platforms. Differences: the title (above), the progress count source, button styles, fonts, and the iOS intro sitting directly under the tab bar |

---

## 3. Prioritized issues

P0 = blocks the phase goal (understand in 30 s / find learning) or breaks a non-negotiable rule. P1 = design-system or consistency debt that users
see. P2 = polish or code health.

### Daily Brief
| ID | P | Issue | Platforms |
|---|---|---|---|
| DB-01 | **P0** | The summary and index numbers are buried below 6–10 metadata/banner lines; the title is duplicated (nav bar + in-page) | both |
| DB-02 | **P0** | Large text: the reader index row uses the weighted-label pattern (Android) or a non-stacking `HStack` (iOS) *(suspected, verify)*. Fix: reuse the Home index row / `StockQuoteRowLayout` | both |
| DB-03 | **P0 (guard)** | The new design must not add causal headlines or "what moved the markets" wording. All headline text comes from the server `summaryLine` / stories or a deterministic factual rule (D-2) | both |
| DB-04 | P1 | Sample data is shown as a caption, not the shared SAMPLE `StockStatusBadge`, and it is repeated in `notes` | both |
| DB-05 | P1 | Evidence note repeated in every story; index explainer always expanded; disclaimer + notes + AI caveat stacked. Fold them once (`EarningsDisclosure` pattern, Earnings E-05) | both |
| DB-06 | P1 | Times in UTC in the reader and on stories, while Home uses relative time. Use the relative freshness already in `BriefFormat` ("Updated 30 min ago", "3 hr ago"); keep the session date | both |
| DB-07 | P1 | Hand-made banners → `StockBanner`; raw `TextButton`/`AssistChip`/`AlertDialog`/`OutlinedTextField` → `StockButton`/`StockChip`/design-system dialog and field; iOS `.bordered` → `.stockSecondary` | both |
| DB-08 | P1 | Title Case headings and buttons → sentence case ("Market at a glance", "Understand more") | both |
| DB-09 | P1 | iOS: 22 system fonts → `StockStepsTheme.font(…, relativeTo:)` (same Dynamic Type curve for a value and its caption) | iOS |
| DB-10 | P1 | Screen copy duplicated in Compose and Swift → one core `DailyBriefDisplay` (as `ScreenerDisplay` / `EarningsDisplay`) | both |
| DB-11 | P1 | Story cards: full excerpt always shown; 3 bordered cards. Use first sentence + "Show details" (server text never reworded), borderless sections per call site | both |
| DB-12 | P1 | Loading is a bare spinner/bar → section skeletons (`StockSkeleton`) | both |
| DB-13 | P2 | Header actions ("Notify me", "Scenarios", "Previous briefs" at the bottom) → one ⋯ menu (Notify me, Previous briefs, MOCK-only Scenarios) | both |
| DB-14 | P2 | History: sample briefs unmarked; no explanatory empty state | both |
| DB-15 | P2 | iOS `state!` force unwraps in story buttons (safe today only because stories render inside `if let brief`) | iOS |
| DB-16 | P2 | Related symbol chips in a non-wrapping row (one symbol per story today; future-proof with `StockFadingScrollRow` / flow) | both |

### Learn
| ID | P | Issue | Platforms |
|---|---|---|---|
| LN-01 | **P0** | iOS Learn tab has no title or heading (VoiceOver, orientation); Android has one | iOS |
| LN-02 | **P0** | The glossary, the only general learning content, sits last as a flat 14-row list with no topics. Beginners can't find it | both |
| LN-03 | P1 | Android term rows: two tap targets for one action (row + info button) | Android |
| LN-04 | P1 | Copy mismatch: "Every lesson is free" with no lessons; "Start learning" vs "Understand a stock in five steps" | both |
| LN-05 | P1 | iOS hard-coded `5` steps → `ResearchStep.COUNT` via the bridge | iOS |
| LN-06 | P1 | iOS 19 system fonts, `.bordered` search button → theme fonts, `.stockSecondary` | iOS |
| LN-07 | P1 | Copy in UI code on both platforms → core `LearnDisplay` (intro, section titles, topic order, step label) | both |
| LN-08 | P2 | Uneven section rhythm (title top padding + list spacing); Practice gets a full card where a navigation row would do | both |
| LN-09 | P2 | Contextual "Open Learn" links always land on the tab root. A topic deep link would be a navigation change (D-8) | both |

---

## 4. Proposed screen structure and visual hierarchy

Principles: answer first (number → context → explanation → education), one meta line, borderless sections with dividers (the 5D.4.2 rhythm), folded
detail, every value from the API, missing stays labelled.

### 4.1 Daily Brief reader (display only)
```
┌──────────────────────────────────────────┐
│ ←  Daily brief                        ⋯  │  ⋯ = Notify me · Previous briefs · Scenarios (MOCK only)
├──────────────────────────────────────────┤
│ After-close brief · Oct 7 · 2 min read   │  caption (meta)          [SAMPLE] badge when sampleData
│                                          │
│ At the close on Oct 7, the S&P 500 fell  │  summaryLine as the headline
│ 0.36%, the Nasdaq Composite fell 0.42%…  │  (cardTitle/sectionTitle size; server text, unchanged)
│ Updated 30 min ago · US closed · CA closed│ one freshness + session line (details in ⓘ)
│ ┌ StockBanner (caution) ───────────────┐ │  only when stale / offline
│ └──────────────────────────────────────┘ │
│                                          │
│ Markets                                  │  section title (sentence case)
│ S&P 500                       5,xxx.xx   │  shared index row (Home's), StockQuoteRowLayout:
│ Close · Oct 7          ▼ −18.59 (−0.36%) │  values move under the name at large text
│ ──────────────────────────────────────── │
│ Nasdaq Composite             16,xxx.xx   │
│ ──────────────────────────────────────── │
│ S&P/TSX Composite            20,xxx.xx   │
│ ▸ What is a market index?                │  folded explainer (StockDisclosure)
│                                          │
│ In the news                          3   │
│ EARNINGS · Reuters · 2 hr ago            │  topic + publisher + relative time (meta)
│ Headline in semibold, wraps fully        │
│ First sentence of the provider excerpt.  │
│ Show details ▾        Read article ↗     │  details = rest of excerpt + symbol chip
│ ──────────────────────────────────────── │
│ (2 more stories, same row pattern)       │
│ ▸ About these stories                    │  evidence note + excerpt note, once
│                                          │
│ From your watchlist          (signed in) │  rows (StockNavigationRow-style), not cards
│ AAPL · Apple   AAPL rose 2.4% in the…  › │
│ Your earnings digest  [PLUS]           › │
│                                          │
│ Upcoming earnings                        │
│ Apple (AAPL)   Expected Oct 30 · Est.  › │
│                                          │
│ ┌ Concept of the day (education tint) ─┐ │  one compact card (only tinted surface)
│ │ What is diversification?             │ │
│ │ First sentence…  Understand more ›   │ │  sheet; "Open Learn" as secondary link
│ └──────────────────────────────────────┘ │
│                                          │
│ StockSteps+ insights   [PLUS]            │  unchanged behaviour, design-system field/button
│                                          │
│ ▸ About this brief                       │  notes + sources + disclaimer, folded once
└──────────────────────────────────────────┘
```
States: **loading** = skeleton of meta, headline (2 lines), 3 index rows, 2 story rows; **error, no cache** = `StockErrorState` with "Try again";
**offline / stale** = caution `StockBanner` under the meta line, content below; **no stories** = one line in the section (current copy);
**missing index** = row shows "Unavailable" + state label (current behaviour).

Optional, owner decisions: D-3 "Key points" (factual, from existing data) between headline and Markets; B-1 sparkline in each index row.

### 4.2 Home entry (decision D-5)
Home already shows "Markets today" (indices + one "Your Daily Market Brief" navigation row, Phase 4A). The reference shows a hero brief card.
Recommendation: **keep Home's structure** and add only the summary's first sentence under the entry row. The hero card would duplicate the index
rows that sit right below it.
```
Markets today                    [SAMPLE]
US stocks: Closed · Canada: Closed
S&P 500 …  ▼ −0.36%   (3 compact rows, unchanged)
──────────────────────────────────────────
Your Daily Market Brief                  ›
At the close on Oct 7, the S&P 500 fell…   ← new: 1 line, ellipsis allowed here (full text in the reader)
After-close brief · Updated 30 min ago · 2 min read
```

### 4.3 Learn landing (display only, existing content)
```
┌──────────────────────────────────────────┐
│ Learn                                    │  heading on both platforms (fixes LN-01)
│ Short, free explanations and a guided    │  one-line intro (LearnDisplay; no "lesson" claim)
│ way to research a company.               │
│                                          │
│ Start here                               │
│ ┌ education tint ──────────────────────┐ │  the one highlighted card
│ │ Understand a stock in 5 steps        │ │
│ │ What it does, growth, profit, debt,  │ │
│ │ price.                               │ │
│ │ (AAPL) Research Apple              › │ │  quick picks as rows
│ │ (KO)   Research Coca-Cola          › │ │
│ └──────────────────────────────────────┘ │
│ — or, when research is in progress —     │
│ Continue learning                        │
│ (AAPL) Apple · 2 of 5 steps  ▓▓░░░   ›   │
│                                          │
│ Topics                                   │  2-column grid of icon tiles (StockCardGridLayout:
│ ┌────────────┐ ┌────────────┐            │  one column when a word won't fit)
│ │ ◎ Business │ │ ↗ Growth   │            │  topic names + term count from BeginnerEducation
│ │ 1 term     │ │ 1 term     │            │
│ └────────────┘ └────────────┘            │  tapping a topic filters the list below
│ … Profit · Financial health · Valuation  │  (no new route; D-7)
│   Income · Earnings · Portfolio          │
│                                          │
│ Financial terms            [All ▾ chips] │  StockFadingScrollRow of topic chips (All + 8)
│ What is revenue?                       › │  StockNavigationRow: one tap target, topic caption
│ Business                                 │
│ ──────────────────────────────────────── │
│ What is revenue growth?                › │
│ …                                        │
│                                          │
│ Research any company                   › │  navigation row → search (was a button + sentence)
│ Practice with virtual money            › │  navigation row → Practice
│ Completed research (n)                 › │  rows with "Review"
└──────────────────────────────────────────┘
```
Term sheet (`BeginnerExplanationSheet`): unchanged content. Visual alignment only: title, the "What it is" lead in `body`, the other sections as
label + body, and "Got it" as `.stockSecondary`.

---

## 5. Reusable component opportunities
| Component | Today | Proposal |
|---|---|---|
| Index row | `HomeIndexRow` (iOS, private) + Compose Home row; reader has its own heavier rows | Promote to a shared **`StockIndexRow`** (Compose + SwiftUI) used by Home and the reader; layout through `StockQuoteRowLayout` |
| Show details / disclosure | `EarningsShowDetails`, `EarningsDisclosure` (generic code, Earnings names) | Promote to **`StockShowDetails` / `StockDisclosure`** in the design system; keep the Earnings names as thin wrappers so Earnings code is untouched (D-6) |
| Navigation row | `StockNavigationRow` (both) | Watchlist highlights, upcoming earnings, term rows, Learn links |
| Status badge / banner | `StockStatusBadge(SAMPLE)`, `StockBanner` | Brief header sample badge, stale/offline banners, history sample marker |
| Fading chip row | `StockFadingScrollRow` (both) | Learn topic chips |
| Card grid rule | `StockCardGridLayout` (5B) | Learn topic tiles (2 → 1 column, word-safe) |
| Icon tile | `StockIconTile` (both) | Topic tiles (topic → icon mapping in `LearnDisplay`, existing `StockIcons`/SF Symbols only) |
| Skeleton | `StockSkeleton` (both) | Brief loading layout |
| Copy | none for brief/learn | New core **`DailyBriefDisplay`** and **`LearnDisplay`** (copy, section titles, meta line, topic order/counts, spoken labels) with tests |
| Empty state | Compose `StockEmptyState`; iOS `StockSectionMessage` | Use as is (no new component) |

---

## 6. Exact affected files and dependencies

**Phase 5D.5.1 — Daily Brief (display only)**
- core (new): `core/src/commonMain/kotlin/org/example/stocksteps/brief/DailyBriefDisplay.kt`; test `core/src/commonTest/.../brief/DailyBriefDisplayTest.kt`.
- core (small, additive): `brief/DailyBriefPresentation.kt`, `BriefFormat` only (relative story time, e.g. `publishedAgo`). The presenter is unchanged.
- shared: `presentation/brief/DailyBriefScreens.kt` (rewrite of layout), `DailyBriefScenes.kt` (⋯ menu wiring only);
  `designsystem/components/` new `StockIndexRow.kt`, and `StockDisclosure.kt` if D-6 is approved; `presentation/home/HomeComponents.kt` (reuse
  `StockIndexRow`; Home looks the same).
- iOS: `DailyBriefScenes.swift`; `HomeComponents.swift` (`HomeIndexRow` → `StockIndexRow` in `DesignSystem/StockComponents.swift`);
  `DesignSystem/` disclosure if D-6; `app/shared/src/iosMain/.../IosBriefClient.kt` (expose `DailyBriefDisplay` helpers if needed).
- tests: `app/shared/src/commonTest/.../ResponsiveLayoutRulesTest.kt` (only if a new layout rule is added).
- **Not touched:** server, API, fixtures, presenter logic, AI, notifications, navigation routes, Earnings files (unless D-6 wrappers).

**Phase 5D.5.2 — Learn (display only)**
- core (new): `core/src/commonMain/kotlin/org/example/stocksteps/learning/LearnDisplay.kt` (intro, sections, topic order, counts, icon keys, step label
  from `ResearchStep.COUNT`); test `LearnDisplayTest.kt`.
- shared: `presentation/learn/LearnScreen.kt`, `BeginnerEducationUi.kt` (sheet button style only).
- iOS: `GuidedResearchScenes.swift` (`LearnScreen`, `BeginnerExplanationSheet` styling), `LearnScene.swift` (title),
  `app/shared/src/iosMain/.../IosLearningClient.kt` (topics / step count).
- **Not touched:** `BeginnerEducation` text, Guided Research steps, progress sync, server.

**Optional backend items (separate approval, separate commit)**
- B-1 index trend: `core/.../brief/DailyBriefModels.kt` (`BriefIndex.trend: List<Double> = emptyList()`, additive), `server/.../brief/DailyBriefService.kt`
  (copy `IndexQuote.trend` from the cached overview), `DailyBriefServiceTest.kt`. No new provider calls; old cached briefs decode with an empty trend.
- B-2 stocks in focus: brief model + service (movers from the cached overview), tests. Not recommended now.

---

## 7. Recommended implementation phases
| Phase | Scope | Size | Gate |
|---|---|---|---|
| **5D.5.1** Daily Brief reader | DB-01…DB-13, DB-15, DB-16: answer-first header, SAMPLE badge, one meta line, shared index row, folded explainer/evidence/about, story rows with first sentence + Show details, relative times, design-system buttons/banners/fields, sentence case, iOS theme fonts, skeleton, ⋯ menu; Home uses `StockIndexRow` (no visible change) | M | Owner approval of this plan + D-1…D-6 |
| **5D.5.1a** (optional) | D-5 Home summary line; DB-14 history polish | S | D-5 |
| **5D.5.2** Learn landing | LN-01…LN-08: titles, Start here, topic grid + chip filter, term navigation rows, links as rows, copy via `LearnDisplay`, iOS fonts/buttons/step count | M | Approval + D-7 |
| **5D.5.3** (optional, backend) | B-1 index sparkline in the reader | S | Separate approval |
| Later / new product work | New lessons, levels, term progress, topic deep links (L-B1…L-B3, D-8) | — | Not part of UI refinement |

Each phase is one reviewable change on both platforms, with its own report (`docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_1.md`, `_5D_5_2.md`) and
captures (`docs/design/phase5d_5/`). Commit only on request, with `PROJECT_HANDOFF.md` updated in the same commit.

---

## 8. Testing strategy

**Automated (run one after another; parallel sessions share `build/`):**
1. `./gradlew :core:jvmTest :core:iosSimulatorArm64Test`: new `DailyBriefDisplayTest` (meta line, sample badge decision, relative times, never
   "today" for an old brief, fold once, no copy invents a cause) and `LearnDisplayTest` (topic order and counts from the catalogue, step count
   label, filter keeps catalogue order); existing `DailyBriefTest`, `BriefWordingTest`, `LearningPresentationTest` stay green.
2. `./gradlew :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test` (+ `ResponsiveLayoutRulesTest` if a rule is added).
3. `./gradlew :server:test` only if B-1/B-2 are approved.
4. `./gradlew :app:androidApp:assembleDebug`, then `xcodebuild` (iPhone 16, ad-hoc signed, `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081`).

**Device checks (local MOCK only, after asking):** Android `emulator-5554` at 360 dp; iOS iPhone 16 with **device-point** taps.
| Screen | Android | iOS |
|---|---|---|
| Reader, normal | 1.0× light, 1.0× dark, 1.3×, 2.0× | Default light/dark, AX XL live change, AX5 |
| Reader MOCK scenarios (⋯ → Scenarios) | `pre-market`, `market-hours`, `weekend`, `holiday`, `us-open-ca-closed`, `no-stories`, `one-story`, `missing-index`, `stale-index`, `missing-publisher` | same subset |
| Loading | skeleton visible on cold start (capture early) | same |
| Error, no cache | stop the MOCK server, clear app data → error + Try again | fresh install, server stopped |
| Offline copy | open the brief, stop the server, reopen → offline banner | same |
| Signed out vs signed in | watchlist sign-in prompt; signed-in highlights | signed-in **needs the owner's sign-in** (Auth emulator) |
| Free vs StockSteps+ | Settings → simulated plan (MOCK) | same |
| Home "Markets today" | unchanged look after `StockIndexRow` reuse, 1.0× and 2.0× | Default and AX |
| Learn landing | guest empty, research in progress, completed; topic filter; term sheet; 1.0/1.3/2.0×, dark | same at Default/AX5, dark; VoiceOver rotor shows the "Learn" heading (Accessibility Inspector if available) |

TalkBack and VoiceOver passes need the owner (TalkBack's first-start permission; VoiceOver needs a device). They are listed as conditions, not
claimed.

---

## 9. Open decisions requiring owner approval
| ID | Decision | Options | Recommendation |
|---|---|---|---|
| D-1 | Approve 5D.5.1 (Brief) and 5D.5.2 (Learn) as display-only phases in this order | yes / reorder / Learn first | **Yes, Brief first** (bigger gap to the 30-second goal) |
| D-2 | Headline text | A: server `summaryLine` as the headline (no new copy). B: a short deterministic headline in `DailyBriefDisplay` from the S&P 500 sign ("US stocks closed lower"; "mixed" when US indices disagree), with the summary below | **A** (no new wording to review); B only if you want the reference's big headline |
| D-3 | "Key points" section | None. Or factual points from existing data (one per index move + "n stories from publishers"), never causes | **None**: the summary already lists the moves |
| D-4 | Section name for stories | "In the news" / keep "Market stories" (sentence case). Never "What moved the markets?" | **"Market stories"** (already established, no implied cause) |
| D-5 | Home brief entry | Keep (+ first sentence of the summary under the entry row) / reference hero card / unchanged | **Keep + one summary line** |
| D-6 | Promote `EarningsShowDetails` / `EarningsDisclosure` to `StockShowDetails` / `StockDisclosure` | promote with Earnings wrappers / copy into brief / leave | **Promote with wrappers** (Earnings behaviour unchanged) |
| D-7 | Learn topics | Topic grid + chip filter on the landing page (no new route) / chips only / a new topic screen (navigation change) | **Grid + chips, no new route** |
| D-8 | Topic deep links from other screens ("Open Learn" → topic) | later / now (adds a route argument) | **Later** |
| B-1 | Backend: add `trend` to `BriefIndex` for sparklines | approve as 5D.5.3 / skip | **Approve as optional 5D.5.3** (additive, no new provider calls) |
| B-2 | Backend: "Stocks in focus" from cached movers | approve / link to Markets instead / skip | **Skip**: Markets already shows movers |
| L-B1 | New learning content (lessons such as "What is a stock?", "Investing basics", reading times) | content project with reviewed copy / skip | **Separate content phase**: needs reviewed educational text; not UI refinement |
| L-B2 | Term progress ("viewed" / "learned") | local only / synced (server) / skip | **Skip for now** (new feature; the server is research-progress only) |
| L-B3 | Levels (Beginner / Intermediate / Advanced) | needs per-entry level data / skip | **Skip**: no data; inventing levels is fabricated structure |
| Q-1 | Today's concept also on Learn (from the latest brief) | yes (Learn scene reads the brief presenter) / no | **No for now**: keeps Learn independent of the brief's network state |

---

## 10. Out of scope (unchanged rules)
No new provider calls, no fixture refresh, no AI change, no causal or advisory copy, no sixth tab, no change to `BeginnerEducation` text or the
Guided Research steps, no global card-default change (borderless per call site), Earnings screens untouched unless D-6 wrappers.
