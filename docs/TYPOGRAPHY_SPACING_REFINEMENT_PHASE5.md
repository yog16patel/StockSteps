# Typography & Spacing Refinement — Phase 5 (app-wide, Android + iOS)

Status: **APPROVED by the owner (2026-10-16), D1–D3 approved; committed locally as "Refine app-wide typography and spacing for Phase 5 with section heading, list title and compact value roles, roomier Company Search rows without the Best match badge, readable navigation rows, two-line company names and clearer Key Ratios on Android and iOS"** (not pushed). Recommendation was APPROVE WITH FIXES (§11); the fixes are tracked as QA follow-ups: Android dark mode, small phones (≤ 360 dp / iPhone SE), signed-in iOS, TalkBack/VoiceOver, Search loading/error/no-results states and Practice AFTER screenshots.
Base: `b8321ce` "Normalize inner whitespace in Company Search queries for Phase 4 …" (`main` = `origin/main`). Baselines kept: Company
Details Phase 3 (`337274e`, approval `17b14a7`) and Company Search Phase 4 (`b8321ce`, no UI change).
Review package: `docs/reviews/typography_spacing_phase5/`. Originals and logs: `docs/reviews/typography_spacing_phase5/captures/` (git-ignored).

A targeted design-system refinement, not a redesign: three new semantic type roles, four spacing/dimension roles, and fixes in shared
components and the confirmed problem areas. No backend, provider, Firebase, navigation, ranking, caching or data change; no new font,
colour or dependency.

## 1. Inspection

- `git status` clean at start; `git log -5` = `b8321ce`, `6d6b68e`, `17b14a7`, `337274e`, `0696f7e`.
- Read `CLAUDE.md`, `docs/AI_REVIEW_WORKFLOW.md`, `docs/PROJECT_HANDOFF.md` (§COMPANY-SEARCH-PHASE4, §COMPANY-DETAILS-UI-PHASE3),
  `docs/project-status.md` §0.
- Design tokens: `app/shared/.../theme/Typography.kt` (`ThemeTypography`), `Spacing.kt` (`ThemeSpacing`, `ThemeDimensions`,
  `StockLayout`), `StockSemanticStyles.kt`, Compose `designsystem/theme/StockStepsTheme.kt`, SwiftUI `Theme.swift` (adapters only — iOS reads
  the same shared tokens; `StockStepsTheme.font` maps each token to the nearest Dynamic Type style).
- Existing scale before this phase: display 36, largeNumber 32, screenTitle 28, sectionTitle 20, cardTitle 17, body 15 (+Medium/SemiBold),
  small 14, label 13, caption 12, tiny 11; numbers 18/17/13/13. Spacing is already a 4-pt grid (2/4/8/12/16/20/24/32) with semantic roles.
- No hard-coded text sizes in the components touched (all through tokens); the one hard-coded size found nearby (`SearchStateView` icon 44)
  is decorative and unchanged.

## 2. Screen inventory (MOCK, Pixel 9 Pro 427 dp 1.0×, iPhone 16 Default)

| Screen | Result | Finding |
|---|---|---|
| Company Search | NEEDS REFINEMENT | Android rows 8+8 dp padding, 15 sp names, 32 dp logos, 2 dp line gap → ~53 dp rows that read as congested; "Best match" badge repeats the section heading; Android field and rows sat ~46 dp from the screen edges (gesture-zone insets) instead of 16; iOS rows used List insets (~75 pt rows, fewer visible) and headers at ~20 pt while rows/field are at 16. |
| Home | PASS | Phase 1 open sections; section titles and rows read well. Daily Brief row detail inherits the navigation-row change. |
| Markets | NEEDS REFINEMENT | Top Movers names cut to one line ("China SXT Pharmace…" on iOS, "Pinnacle Food Group Limited Cl…" on Android); Research tools descriptions at 12 caption. |
| Discover Stocks | PASS (nav rows refined) | Category tiles and result rows fine; "More tools" descriptions at 12 caption (navigation row). |
| Company Details | NEEDS REFINEMENT | Key Ratios values 13 under 14 labels (value smaller than its label); the highlights basis caption sat 8 dp/pt above "Key Ratios" and read as its subtitle; Valuation sentence broke "P/" + "E." (iOS Default). Price header, chart, At a Glance, Understand rows: PASS (approved Phase 3). |
| Financials / Valuation | PASS | Phase 5D.5.4C/Phase 3 layouts; compact rows (`StockInfoRow(compact)`) gain the same value fix. |
| Portfolio / Practice | PASS | Reference screens; unchanged (Portfolio AFTER capture identical in layout; Practice captured BEFORE only). |
| Watchlist | PASS (nav rows refined) | Rows already 2-line names; "Alerts and more" descriptions at 12 caption. |
| Learn | PASS (nav rows refined) | "More ways to learn" descriptions at 12 caption. |
| Settings / common | PASS | Settings rows use `StockSettingsRow` (not changed). Section headers everywhere move 17 → 18. |

ACCESSIBILITY ISSUE: none newly found; the Key Ratios inversion and the one-line mover names are readability defects fixed here.
Not changed deliberately: Understand-row explanation sentences (caption, approved 5D.5.4B), Discover tile descriptions, screen titles.

## 3. Token changes

| Token | Before | After | Where | Why |
|---|---|---|---|---|
| `ThemeTypography.sectionHeading` | — (headers used `cardTitle` 17/22/600) | **18/24/600** | `StockSectionHeader` (Compose + SwiftUI), search section titles | A section heading outranks the card titles (17) under it; brief target 18. |
| `ThemeTypography.listTitle` | — (rows used `bodySemiBold` 15/21/600) | **16/22/600** | Search rows, `StockNavigationRow` titles | Primary list text 16 (brief); the name reads before its supporting line. |
| `ThemeTypography.numberCompact` | — (`numberLabelStrong` 13/18/500) | **14/20/600 tabular** | Compact `StockInfoRow` / `StockInfoLine` values (Key Ratios, classification, Financials compact rows) | A value was smaller than its 14 label. |
| `ThemeSpacing.listRowVertical` | 8 (`sm`) | **12 (`md`)** | Search rows | Breathing room between results without card chrome. |
| `ThemeSpacing.listTitleSubtitle` | 2 (`xxs`) | **4 (`xs`)** | Search rows | Name and ticker/exchange no longer touch. |
| `ThemeSpacing.sectionBreak` | — | **8 (`sm`)**, added to the existing gap | Android "Other results" header; Company Details Highlights / Key Ratios | Separates groups; iOS plain lists already leave this gap above a following header. |
| `ThemeDimensions.logoList` | 32 (`logo`) | **40** | Search and recent rows | Logos legible next to 16 names (brief ~40). `logo` 32 stays for dense rows. |
| `StockNavigationRow` detail | `caption` 12 | `small` 14 | All navigation rows | Secondary text 14 (brief); it is the sentence saying what a row opens. `secondaryDetail` stays 12 (metadata). |

Unchanged on purpose: `screenTitle` 28 (brief starting target 24). Landing large titles are the iOS-large-title equivalent approved in
the Top bar phase (KAN-16) and outrank the 18 headings clearly; 24 would weaken hierarchy without fixing a confirmed defect. `body` 15,
`small` 14, `caption` 12, `label` 13, number roles, chart labels, nav labels and all colours are unchanged. No blanket padding increase: card
padding, screen margin (16), section gap and row min heights are untouched.

## 4. Components and screens changed

Compose (`app/shared`): `theme/Typography.kt`, `theme/Spacing.kt`, `designsystem/theme/StockStepsTheme.kt` (new roles exposed),
`StockSearchResultRow.kt` (16/14, 40 logo, 12/4 spacing, no badge, `SEARCH_BADGE_MAX_SCALE` removed), `StockSearchPane.kt` (`sectionBreak`
above later sections; divider inset 40 + 12), `StockSearchScreen.kt` (`safeDrawingPadding` instead of `safeContentPadding`),
`StockSectionHeader.kt` (18), `StockNavigationRow.kt` (16 / 14), `StockMetric.kt` (`StockInfoRow` compact value 14), `StockRow.kt` (company
name up to 2 lines by default), `CompanyDetailsScreen.kt` (sub-section break; `ValuationDisplay.keepTogether` on the valuation sentence).

SwiftUI (`app/iosApp`): `DesignSystem/SearchResultRow.swift` (as Compose, badge removed), `StockSearchScreen.swift` (row insets 0/16 so the
row owns its 12 pt padding; headers on the 16 pt margin, `searchListHeader`; 18 headings), `DesignSystem/StockComponents.swift`
(`StockSectionHeader` 18 + its stacking measurement, `StockNavigationRow` 16/14, `StockRow` 2-line names), `DesignSystem/StockDetailComponents.swift`
(`StockInfoLine` compact value 14), `CompanyDetailsScreen.swift` (sub-section break, keepTogether).

Tests: `DesignTokensTest` — new roles in the strict hierarchy and line-height/tabular checks; `phase5SemanticRolesKeepTheReadingOrder`
(18/16/15/14/12–13 roles, heading > card title, compact value ≥ label); `phase5ListRowSpacingStaysOnTheGridAndDense` (grid, 40 logo,
two-line search row between 48 and 80 dp/pt).

## 5. Before / after (what was wrong → what changed → why better → density → parity)

1. **Search rows** — congested, 15 names, tiny logos, badge duplicating the heading → 16/14, 40 logo, 12/4 spacing, no badge → names lead,
   rows separate without cards → Android rows 53 → 65 dp, iOS 75 → 64 pt → same on both (row owns its padding on iOS).
2. **Search margins (Android)** — 46 dp side margins from gesture insets → 16 dp → long names wrap less, row text +60 dp wider → more text per
   row → parity with iOS (16 pt).
3. **Search sections** — "Other results" close under the best row (Android); iOS headers 4 pt off the row margin → `sectionBreak` / header
   insets → clear groups, aligned left edges.
4. **Section headings** — same 17 as card titles → 18 → heading > card title everywhere → +1–2 dp per heading.
5. **Navigation rows** — 12 caption descriptions → 15→16 titles, 14 descriptions → readable explanations → +4–5 dp per row.
6. **Top Movers names** — truncated to 1 line → up to 2 lines → company identity visible → +17–20 dp only on rows with long names.
7. **Key Ratios values** — 13 under 14 labels → 14 semibold tabular → value ≥ label, matching the highlights above → no height change.
8. **Highlights → Key Ratios** — caption read as Key Ratios' subtitle → +8 dp/pt break → caption belongs to its table.
9. **Valuation sentence** — "P/" + "E." break → word joiners via the existing `ValuationDisplay.keepTogether` (iOS confirmed; Android same code).

## 6. Density comparison (same device, same scale, same MOCK data)

| Case | BEFORE | AFTER |
|---|---|---|
| Android Search "apple", 1.0×, results fully visible (keyboard hidden) | 12 rows (row ≈ 53 dp) | 9 rows (row ≈ 65 dp) |
| iOS Search "apple", Default | 8 rows (row ≈ 75 pt) | 9 rows (row ≈ 64 pt) |
| Android Search 2.0× | ~3.5 rows | ~3.5 rows (wider text column offsets taller type) |
| iOS Search AX5 | ~1.5 rows | ~1.5 rows |
| Android Markets "Research tools" group (3 rows) | ≈ 187 dp | ≈ 204 dp (+9%) |
| Android Watchlist "Alerts and more" (4 rows, one-line descriptions) | ≈ 212 dp | ≈ 216 dp (+2%) |
| Company Details Key Ratios (6 rows) | unchanged height | unchanged height |
| Portfolio / Practice | — | identical layout |

The Android search loss (12 → 9 visible) is the requested breathing room; it stays a dense list (no cards), and iOS gains one row. The
12 dp padding (not the brief's 14–16) keeps the 4-pt grid and at least nine results on screen; 16 dp would leave ~8 on Android. No primary
action moved below the fold on any captured screen.

## 7. Accessibility

| Check | Android | iOS |
|---|---|---|
| Default (1.0× / Default) light | PASS (Search, Home, Markets, Discover, Details, Watchlist, Learn, Portfolio) | PASS (Search, Home, Markets, Details, Learn) |
| Large text (2.0× / AX5) | PASS: Search, Learn rows, Watchlist, Key Ratios at 2.0× — no clipping or overlap | PASS: Search at AX5 (ticker/exchange stack, logo left) |
| Dark | NOT RUN on device (the emulator's system night mode doesn't switch the app; the in-app theme setting was not changed) | PASS: Search dark |
| Long names / multiline | PASS: "Kurv Yield Premium Strategy Apple (AAPL) ETF", "Pinnacle Food Group Limited Class A Common Shares" | PASS (two-line names, movers 2 lines) |
| Touch targets | Rows ≥ 48 (unchanged min heights) | Rows ≥ 48 |
| Reading order / spoken labels | Unchanged (one element per row; "best match" still spoken) — code review | Same |
| TalkBack / VoiceOver | NOT RUN (owner pass) | NOT RUN |
| Small phones (≤ 360 dp / SE) | NOT RUN (no free small emulator; iOS SE sim can't install the app, iOS ≥ 18.2) | NOT RUN |

## 8. Parity

Every change is in the shared tokens and mirrored component pairs (`StockSearchResultRow`/`SearchResultRow`, `StockSectionHeader`,
`StockNavigationRow`, `StockRow`, `StockInfoRow`/`StockInfoLine`, Company Details). iOS keeps native list behaviour (plain `List` header gap
instead of Compose's `sectionBreak`).

## 9. Tests and builds

Sequential run: `:core:jvmTest` PASS 612/0 and `:core:iosSimulatorArm64Test` PASS 610/0 (forced `--rerun`; core unchanged),
`:app:shared:testAndroidHostTest` PASS 284/0 (+2), `:app:shared:iosSimulatorArm64Test` PASS 281/0 (+2), `assembleDebug` PASS, `xcodebuild`
PASS. Server tests NOT RUN (no server change). Details and interaction checks: `docs/reviews/typography_spacing_phase5/CHATGPT_TEST_RESULTS.md`.

## 10. Environment notes and outstanding issues

- The `:8083` MOCK server answered 500 (class-loading errors: its jars were rebuilt under the running process) for `markets/overview` and
  `screener/search`. It was **not** touched. This phase ran a private MOCK copy on `:8085` (scratchpad copy of `installDist`, stopped at the
  end) and pointed emulator-5554 at it with `adb reverse tcp:8083 tcp:8085` (restored to `tcp:8083` at the end). iOS builds used
  `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8085`. Restarting `:8083` is a follow-up for whoever owns it.
- Android BEFORE Home shows an empty watchlist; three MOCK watchlist entries (AAPL, MSFT, NVDA) were added on `:8085` afterwards for the
  Watchlist captures, so Home AFTER differs below the fold for that reason only.
- iOS AFTER Company Details was opened from Home (Back label "Home"); BEFORE from Markets. Layout is otherwise comparable.
- Open: Android dark capture; small phones; TalkBack/VoiceOver; signed-in iOS; Understand-row explanation sentences (12) and Discover tile
  descriptions (12) left as approved — candidates for a later pass if the owner wants them at 13–14.

## 11. Recommendation

**APPROVE WITH FIXES** — the fixes being the unverified items in §7 (Android dark, small phones, screen readers) and the owner's call on
D1–D3 below.

Decisions for the owner:
- **D1** Search row padding 12 (4-pt grid, 9 results on Android) instead of the brief's 14–16.
- **D2** Keep `screenTitle` at 28 (approved KAN-16 landing titles) rather than 24.
- **D3** "Best match" badge removed (requested change to the 5D.5.5D design); the section heading stays and the row still speaks "best match".
