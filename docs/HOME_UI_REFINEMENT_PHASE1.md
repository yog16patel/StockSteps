# Home UI Refinement — Phase 1 (KAN-14)

Status: committed and pushed as "Refine Home for UI/UX Refinement Phase 1 (KAN-14) with open sections, borderless row groups, a wider section rhythm, first-load skeletons and live Dynamic Type on Android and iOS" (owner review pending). KAN-14 is **not** Done.
Base: `44d0247` "Stabilize navigation for Phase 5D.5.6D …" (working tree also holds other sessions' uncommitted DEBUG API logging work,
untouched by this phase). Review package: `docs/reviews/home_ui_phase1/`.

Scope: a focused refinement of the existing Home screen on Android (Compose, `presentation/home/`) and iOS (SwiftUI, `HomeScreen.swift`,
`HomeComponents.swift`). No new features, data, requests, navigation, auth or guest-rule changes.

## 1. Audit (before)

Baseline: emulator-5554 (Pixel 9 Pro, 411 dp, signed-in cached test session) and iPhone 16 simulator (guest), both on local MOCK (:8083),
scenario "watchlist-and-portfolio" for the filled state. Every section was a **bordered card** (`StockCard()` / `.stockCard()`), 12 dp/pt
apart (`sectionGap`), title inside the card.

| # | Section | Hierarchy / type | Borders, background, padding | Primary action | Empty / loading / error | Decision | Android vs iOS |
|---|---|---|---|---|---|---|---|
| 0 | Header: landing bar, greeting, "What matters to you today" | L1 greeting (`sectionTitle`), supporting line (`small`) | none, page background | Search, Notifications, Settings icons | — | **Keep** | same |
| 1 | Markets today (sessions, 3 index rows, Daily Brief entry) | L2 title `cardTitle` + Sample badge, caption session lines, rows | bordered card, 16 padding | Brief row (chevron) | loading: thin progress bar (A) / spinner (iOS); unavailable: one line | **Open section**: title + sessions on the page, index rows + brief entry in one borderless group; skeleton rows on first load | iOS session lines did not follow a live Dynamic Type change (stayed small at AX5) |
| 2 | Your portfolio (summary or "Create portfolio") | L2 title, `largeNumber` total, metric grid | bordered card, whole card tappable when owned (chevron) | open Portfolio / Create portfolio | loading: progress bar / spinner; no account: text + secondary button | **Stays a card** (one tap target), borderless; skeleton lines on first load | same (guests see the existing "Create portfolio" card — unchanged guest rule) |
| 3 | Watchlist highlights | L2 title + "View all", `StockRow`s with dividers | bordered card, rows edge to edge | View all; rows open the stock | empty: text + "Find stocks"; loading: row skeleton; errors: section message + Try again; refresh: thin bar (A) / spinner (iOS) | **Open section**: rows in one borderless group; empty state plain text + button (no box) | iOS refresh used a spinner; Android a thin bar → both thin bar |
| 4 | Today in your watchlist | L2 title, `StockNavigationRow`s | bordered card | rows | shown only with facts | **Open section** + borderless row group | same |
| 5 | Upcoming & alerts | L2 title, rows, alerts error inside | bordered card | rows; Try again | shown with facts or an alerts error | **Open section** + borderless row group | same |
| 6 | Learn and practice | L2 title on the page, gradient Learn banner, Practice card (education tint) | already open; banners are tinted, borderless | banner / card buttons | — | **Keep** (header gap tightened to `xs`, banners `sm` apart) | same |
| 7 | News for you | L2 title, muted notice, company label + compact news row per story | bordered card around rows | open article | loading: news skeleton; error: message + Try again; empty: one line | **Open section**; stories in one borderless group with dividers | Android story gap `xs`+label padding, iOS `sm` → unified |
| 8 | Recently viewed | L2 title + "Clear", compact `StockRow`s | bordered card | Clear; rows | only when non-empty | **Open section** + borderless row group | same |
| 9 | MOCK scenario picker / "Refresh dashboard" | Sample badge + text button; Android-only refresh button (iOS: pull to refresh) | none | — | — | **Keep** (MOCK tooling; refresh unchanged) | Android-only refresh button is pre-existing (iOS uses `.refreshable`) |
| — | First load (dashboard restoring) | — | — | — | Android: one row skeleton; **iOS: full-width spinner "Restoring your dashboard"** | Both: three row skeletons in a borderless group | iOS spinner → skeleton |

Page rhythm before: top `md` (12), section gap 12, bottom 12. Learn and Daily Brief already use `xl` (20) between open sections.
KAN-36 (loading philosophy) has no work in this repository; its stated rules (skeletons on first load, localized loading, content kept during
refresh, no full-screen spinner) were applied to Home only.

## 2. Decisions

| ID | Decision | Why |
|---|---|---|
| H1 | Page sections are **open**: `HomeSection` = `StockSectionHeader` on the page background, `xs` to the content (Daily Brief 5D.5.1c pattern) | Hierarchy from spacing and type instead of a box per section |
| H2 | Rows of a section sit in **one borderless surface group** (`HomeRowGroup` / `.homeRowGroup()` = `StockCard(bordered = false)` / `.stockCard(padding: 0, bordered: false)`, `xs` vertical, card-padding inset unless the row has its own) | Rows keep a calm surface and dividers without outlines; per call site, so the global card default is unchanged |
| H3 | Section gap `sectionGap` (12) → `spacing.xl` (20), bottom padding `md` → `xl`; top stays `md` | Open sections need more air than bordered cards; same values as Learn and Daily Brief. No new token (existing scale values) |
| H4 | Portfolio summary **stays a card** (borderless); title inside | It is one tap target with a chevron; moving the title out would split the button |
| H5 | Learn banner and Practice card unchanged (already tinted, borderless); header-to-banner gap `xs`, banners `sm` apart on both platforms | Already the reference treatment |
| H6 | Empty watchlist and section errors render as plain text + action on the page (no box) | No card around a single message |
| H7 | News: stories in one borderless group with a divider between stories; company label above each | No card per story; unifies Android `xs`+padding and iOS `sm` spacing |
| H8 | First load: skeletons (Markets: three label/value placeholders; Portfolio: two; dashboard restore: three row skeletons on iOS too). Price refresh keeps rows visible with a thin linear bar on both platforms | KAN-36 rules: skeleton on first load, localized loading, never a full-width spinner |
| H9 | iOS live Dynamic Type: `HomeMarketOverview` rebuilds on a type change (`.id(typeSize)`); `HomeLearnBanner` and `PracticeEntryCard` read `dynamicTypeSize` in `body` | Session lines, Sample badge and the Practice body text stayed small after a live switch to AX5 (correct after relaunch) — the repo's documented iOS rule |

Unchanged: copy, order, data, requests, navigation and destinations, auth/guest rules, `StockCard` / `.stockCard` defaults, the Android-only
"Refresh dashboard" button (iOS uses pull to refresh), the MOCK scenario picker, the 60 s `onVisible` refresh TTL.

## 3. Files

| Module | File | Change |
|---|---|---|
| shared (Android UI) | `app/shared/src/commonMain/kotlin/org/example/stocksteps/presentation/home/HomeComponents.kt` | `HomeSection`, `HomeRowGroup`, `HomeRowsSkeleton`; Markets as an open section with skeleton; portfolio card borderless with skeleton |
| shared (Android UI) | `app/shared/src/commonMain/kotlin/org/example/stocksteps/presentation/home/HomeScreen.kt` | section gap / bottom padding; Watchlist, facts, Learn, News, Recently viewed as open sections; restore skeleton in a group |
| iOS | `app/iosApp/iosApp/HomeComponents.swift` | `HomeSection`, `.homeRowGroup()`, `HomeRowsSkeleton`; Markets open section + skeleton + `.id(typeSize)`; portfolio card borderless + skeleton; Learn banner live type |
| iOS | `app/iosApp/iosApp/HomeScreen.swift` | same section structure as Android; restore skeleton instead of a spinner; linear refresh bar |
| iOS | `app/iosApp/iosApp/PracticeScenes.swift` | `PracticeEntryCard` reads `dynamicTypeSize` (only consumer: Home) |
| docs | this report, `docs/reviews/home_ui_phase1/`, `docs/design/home_ui_phase1/`, `docs/PROJECT_HANDOFF.md`, `docs/project-status.md` | |

Shared design-system components: **none changed** (`StockCard`, `.stockCard`, `StockSectionHeader`, `StockRow`, skeletons used as they are).
`HomeSection` / `HomeRowGroup` are Home-local and only used on Home; no other screen is affected.

## 4. Verification

Local MOCK server :8083 (`STOCKSTEPS_DATA_MODE=mock`), Android built with `-PstockstepsMockBackendUrl=http://127.0.0.1:8083` +
`adb -s emulator-5554 reverse tcp:8083 tcp:8083`, iOS with `STOCKSTEPS_MOCK_BACKEND_URL=http://127.0.0.1:8083`. Originals in
`docs/reviews/home_ui_phase1/captures/` (git-ignored); contact sheets in the package.

| Scenario | Android emulator-5554 (signed-in test session) | iOS iPhone 16 (guest) |
|---|---|---|
| Light, default size, top / middle / bottom | PASS (before + after) | PASS (before + after) |
| Dark, top / middle / bottom | PASS (before + after) | PASS (before + after: top, bottom) |
| Large text | PASS 2.0× dark (before + after) | PASS AX5 live change (before + after; after relaunch also PASS) |
| Empty (default "My saved companies" on a fresh MOCK store) | PASS (after) | PASS (after) |
| Error / offline (MOCK server stopped) | PASS (before + after) | PASS after; before NOT RUN |
| Loading skeletons | NOT RUN — first load finished before a capture (brief cached; app-level splash shows first) | NOT RUN — same |
| Guest on Android / signed-in on iOS | BLOCKED — no sign-out allowed on the emulator's cached session | BLOCKED — no approved iOS test account |

Checks on every captured frame: no outlines around sections; no clipping, overflow or overlap; consistent 16 dp/pt margins; no duplicate headers;
actions ("View all", "Clear", "Find stocks", brief row, Try again) visible; bottom bar visible; dark-mode surfaces distinct from the background
(#0D1B2A on #07111C); gains/losses keep arrows and signs. No personal data in any frame (Home shows no e-mail; Settings never captured).

### Tests and builds (sequential, this working tree)
| Suite / build | Result |
|---|---|
| `:core:jvmTest` | PASS — 606 tests, 0 failures (`--rerun`) |
| `:core:iosSimulatorArm64Test` | PASS — 604 tests, 0 failures (`--rerun`) |
| `:app:shared:testAndroidHostTest` | PASS — 276 tests, 0 failures |
| `:app:shared:iosSimulatorArm64Test` | PASS — 273 tests, 0 failures |
| `:app:androidApp:assembleDebug` | PASS |
| `xcodebuild` (iphonesimulator, `CODE_SIGNING_ALLOWED=NO`) | PASS |

No new unit tests: no shared rule, `*Display` or presenter changed.

**Recommendation: APPROVE WITH FIXES** — owner passes still needed: signed-in iOS, guest Android, signed-in portfolio card with an account,
TalkBack / VoiceOver; owner decisions on H3 (section gap) and H4 (portfolio card).

## 5. Limitations and follow-ups
- iOS content scrolls under the status bar on landing pages (no bar material) — pre-existing, all landing pages; not changed here.
- Compose `StockSectionHeader` has no stacked-action rule at large text (iOS has one); "Recently viewed / Clear" fits at 2.0× — not changed.
- The compact Android skeleton announces "Loading" (shared `StockLoadingState`), where the old bar said "Loading market overview" /
  "Loading your portfolio"; iOS keeps the specific labels.
- TalkBack / VoiceOver passes: NOT RUN (owner runs them manually).
- During QA a mistyped cleanup command killed both emulators, the :8081 MOCK server and the :8083 server (see the review package §8). Both
  emulators and a :8081 MOCK server were restarted from the same AVDs and the installed server build.
