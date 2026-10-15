# Global UI Refinement Phase 5D.5.1 — Daily Market Brief reader

Status: **committed and pushed as "Refine the Daily Market Brief reader for Global UI Refinement Phase 5D.5.1 with the summary as the headline, shared index rows, first-sentence stories, folded notes, a Sample badge and a one-line Home summary on Android and iOS"** (on top of `7326e62`); automated checks pass; device checks run on the local MOCK server (2026-10-14). Plan: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_PLAN.md`.

Owner decisions applied (2026-10-14):
- D-1: the Brief goes first.
- D-2 A: the server `summaryLine` is the headline, unchanged.
- D-3: no "Key points" section.
- D-4: the section stays "Market stories".
- D-5: Home keeps its layout and adds one summary line.
- D-6: the disclosure components are promoted to the design system, with the Earnings names kept as wrappers.
- Deferred: B-1 (index trend) and Learn (5D.5.2).

## 1. What changed (display only)
| Area | Before | After |
|---|---|---|
| Header | In-page "Your Daily Market Brief", tagline, "Notify me", and 4–6 caption lines (UTC times), then the summary in body weight | Nav title "Daily brief"; one meta line ("After-close brief · Oct 7 · 2 min read") with the **SAMPLE badge** (sample briefs only); the **server summary as the headline** (`sectionTitle`, heading, polite live region); one freshness line ("Updated 30 min ago · Market data for the Oct 7 session"); one line per market session; stale (WARNING) / offline (INFO) as `StockBanner` |
| Actions | Header buttons "Notify me" / "Scenarios"; "Previous briefs" at the bottom | One ⋯ menu: Notify me, Previous briefs, and Scenarios **only for sample briefs** (MOCK). Previous briefs stays as a row at the end |
| Markets | Three bordered cards plus an always-open ~60-word explainer | Shared compact **`StockIndexRow`** (the Home row: values move under the name at large text; no weighted label beside an unweighted value), dividers, explainer folded in "What is a market index?" |
| Stories | Bordered cards, UPPERCASE topic, full excerpt, UTC time, the same evidence note in every card, raw buttons/chips | Borderless rows: meta "Earnings · Reuters · 2 hr ago", headline, **first sentence of the provider excerpt** + "Show details" (rest of the excerpt, "View AAPL", "Understand more"), "Read article" on the toggle row; the evidence note **once** in "About these stories" |
| Watchlist / earnings | Bordered cards | `StockNavigationRow` rows with dividers; StockSteps+ rows badged; skeleton while loading |
| Concept of the day | Full explanation, example, why-today, two text buttons | The only tinted card: title, first sentence + "Show details" (example, why today, Open Learn), "Understand more" opens the shared explanation sheet |
| StockSteps+ | Bordered card, Material field | Section with `StockTextField`, secondary Ask button (Android); theme fonts (iOS) |
| Footer | Notes and disclaimer always shown | One folded "About this brief" (server notes, including the sample sentence, and the disclaimer, each once) |
| Loading | Bare progress bar / spinner | Skeleton in the reader's shape, read as one "Loading" |
| History | Bordered cards (Android), plain `List` (iOS) | Navigation rows with the summary line, SAMPLE badge for sample briefs, StockSteps+ row, explanatory empty state |
| Home (D-5) | "Your Daily Market Brief" + meta | Plus the summary's **first sentence** (server text, ≤ 2 lines in the row; the full text is in the reader). Home index rows now use the same `StockIndexRow` (no visual change intended) |

Sentence case everywhere ("Market stories", "From your watchlist", "Upcoming earnings", "Concept of the day", "Understand more"). iOS reader: system
fonts removed (theme fonts with matching `relativeTo:`), `.bordered` replaced by `.stockSecondary`, live Dynamic Type refresh (`dynamicTypeSize`
read in the body).

**Not changed:** server, API, fixtures, presenter, AI, notifications, entitlements, routes, the preferences sheet behaviour, and Earnings behaviour
(the wrappers render the same views).

## 2. Files
- **core (modified):** `brief/DailyBriefPresentation.kt` — `BriefFormat.groupDigits` keeps a negative sign in front (F-3).
- **core (new):**
  - `brief/DailyBriefDisplay.kt`: all reader copy, meta and freshness lines, relative times without a time-zone guess, banners, story meta,
    once-only notes, index helpers moved from Home, earnings and highlight lines.
  - `format/ReadableText.kt`: `firstSentence`, moved from `EarningsDisplay`, which now delegates.
  - Test: `brief/DailyBriefDisplayTest.kt` (11 tests).
- **shared (new):**
  - `designsystem/components/StockDisclosure.kt` (`StockDisclosure`, `StockNotes`, `StockShowDetails`, `StockLinkAction`).
  - `designsystem/components/StockIndexRow.kt`.
  - `presentation/brief/DailyBriefComponents.kt` (`BriefIndexRow`).
- **shared (modified):**
  - `presentation/brief/DailyBriefScreens.kt`: reader and history rewritten; preferences sheet kept as it was.
  - `presentation/earnings/EarningsComponents.kt`: `EarningsDisclosure`, `EarningsNotes` and `EarningsShowDetails` are now wrappers.
  - `presentation/earnings/EarningsDisplay.kt`: `firstSentence` delegates.
  - `presentation/home/HomeComponents.kt`: `HomeIndexRow` replaced by `BriefIndexRow`, plus the summary line.
  - `presentation/home/HomeDashboardPresentation.kt`: index and session helpers delegate; new `briefSummary`.
  - `designsystem/components/StockNavigationRow.kt`: new optional `summary` parameter, default null.
  - `presentation/AppNavigation.kt`: title "Daily brief".
- **iOS (new):** `DesignSystem/StockDisclosure.swift` (`StockDisclosure`, `StockNotes`, `StockShowDetails`, `StockIndexRow`).
- **iOS (modified):**
  - `DailyBriefScenes.swift`: reader and history rewritten, `BriefIndexRow`; preferences form kept.
  - `EarningsComponents.swift`: wrappers.
  - `HomeComponents.swift`: `HomeIndexRow` replaced by `BriefIndexRow`, plus the summary line.
  - `DesignSystem/StockComponents.swift`: `StockNavigationRow.summary`.

## 3. Fixes made during the device checks
| # | Found on | Problem | Fix |
|---|---|---|---|
| F-1 | Android | "Read article" (`StockButton` TEXT) was larger than the "Show details" toggle on the same row | New design-system `StockLinkAction` (label style, 48 dp, optional spoken description) for Read article, Understand more, Open Learn, Preview StockSteps+ and AI sources; iOS already used the label style |
| F-2 | both | One provider excerpt only repeated its headline ("… to come  Reuters" under "… to come - Reuters") | `DailyBriefDisplay.excerpt`: an excerpt equal to the headline on letters and digits is not shown; text never changed (test) |
| F-3 | both, **pre-existing** | A negative EPS estimate read "USD 0.-42" (`BriefFormat.groupDigits` on negatives; the old screen had the same bug) | Sign in front: "USD −0.42" (tests). Capture `BEFORE-FIX-android-360dp-1.0x-light-negative-eps.jpg` |
| F-4 | both | Offline was said twice: "· Offline copy" in the freshness line and the offline banner | Freshness line no longer carries it; the banner says it once (Home's entry row keeps its own "Offline copy", it has no banner) |
| F-5 | iOS AX5 | Home's summary line was cut after two lines as "…S&P 50…", which reads as a different index | `StockNavigationRow.summary` is never truncated (callers pass one sentence); history rows show the summary's first sentence (`historySummary`, test) instead of a clipped full summary |

## 4. Verification
**Automated (run one after another, on the final code after F-1…F-5):**
- core `:core:jvmTest` **514/0**, `:core:iosSimulatorArm64Test` **514/0** (`DailyBriefDisplayTest` 12 tests).
- shared `:app:shared:testAndroidHostTest` **245/0**, `:app:shared:iosSimulatorArm64Test` **241/0**.
- `:server:test` **501/0** (no server change; run because core changed).
- `:app:androidApp:assembleDebug` OK; `xcodebuild` (generic iOS Simulator, unsigned) **BUILD SUCCEEDED**; device build (iPhone 16, ad-hoc signed,
  `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081`) **BUILD SUCCEEDED**.

**Device checks (local MOCK `:server:runMock` on :8081 only; captures in `docs/design/phase5d_5/`):**

| Check | Android `emulator-5554`, 360 dp | iOS iPhone 16 |
|---|---|---|
| Reader top: meta + Sample badge, summary headline, freshness, sessions, stale banner | PASS 1.0× light / dark, 1.3×, 2.0× (badge wraps under the meta line) | PASS Default light / dark, AX XL, AX5 |
| Markets: shared index rows, values under the name at large text, explainer folded | PASS (stacks at 1.3× and 2.0×) | PASS (stacks at AX XL / AX5) |
| Stories: meta with relative time, headline, first sentence, Show details / Read article on one row (wraps at large text), details open | PASS (after F-1, F-2) | PASS; actions stack at AX sizes |
| Watchlist (signed in, empty watchlist), upcoming earnings rows, concept card, StockSteps+ preview, About this brief, Previous briefs | PASS (after F-3) | PASS (after F-3) |
| ⋯ menu: Notify me, Previous briefs, Scenarios (sample only) | PASS | PASS |
| Scenarios: `no-stories`, `missing-index` (left out, never estimated), `stale-index` (caution label), `market-hours` (delayed labels), `holiday`, `missing-publisher` ("Publisher not provided") | PASS (dark) | not repeated (same server data and display object) |
| Offline copy (Android: port forward removed; iOS: MOCK server stopped) | PASS (after F-4) | PASS |
| History: rows, Sample badges, first sentence | PASS | PASS at AX XL (after F-5) |
| Home: same index rows + summary first sentence | PASS light / dark | PASS Default, AX5 (after F-5) |
| Live Dynamic Type change in the reader | — | PASS (Default → AX XL refreshes in place) |

**NOT verified on device:**
- Loading skeleton: it is too brief to capture.
- Error without any saved copy: it needs the app data cleared, which would sign the emulator account out.
- Signed-in watchlist highlights, StockSteps+ (Plus) content and AI answers: these need a watchlist or a Plus plan.
- TalkBack and VoiceOver.
- The iPhone SE width.

**Pre-existing, out of scope (recorded, not changed):**
- iOS Home at AX5: "Market/s today" breaks mid-word beside the Sample badge, and "StockSte/ps" in the header (the known Home Dynamic Type issue).
  Captures: `NOTE-ios-iphone16-ax5-dark-home-*.jpg`.
- iOS: opening a brief from Previous briefs returned to Home in one attempt. The history destination lives on the Home stack (`AppScene`), so the
  back button reads "Home". The navigation code is unchanged in this phase.

**Environment afterwards:**
- MOCK server stopped.
- emulator-5554: font 1.0, `wm size reset`, no `adb reverse`, app theme Light.
- iPhone 16: appearance light, content size large; the latest build is installed.

**Incident:** during the checks, 388 committed captures under `docs/design/` (and the new `phase5d_5/` folder) were deleted by something outside
this session. At the owner's request they were restored from HEAD with `git checkout -- docs/design` (that folder only), and the `phase5d_5`
captures were recreated.

## 5. Known limits / follow-ups
- Upgrade/message dialogs remain platform alerts (no design-system dialog exists); their buttons use `StockButton` on Android.
- Story times are relative within 20 hours and a date after that. The server's session-state labels keep their ET wording.
- Home AX5 heading break and the history → brief navigation (above) belong to a Home / navigation follow-up.
- B-1 (index sparkline) and Learn (5D.5.2) are deferred by the owner.

## 6. Verdict
**PASS WITH CONDITIONS.** All automated suites and builds pass. The device matrix passes on Android and iOS after five fixes (one of them a
pre-existing number-format bug). The conditions are the unverified items above: loading/error capture, signed-in highlights, Plus/AI, and screen
readers.
