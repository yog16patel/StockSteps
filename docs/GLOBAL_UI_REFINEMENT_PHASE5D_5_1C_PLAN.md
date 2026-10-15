# Global UI Refinement Phase 5D.5.1c — Daily Brief final polish: visual audit and plan

Status: **READ-ONLY AUDIT AND PLAN. No source code changed.** Waiting for the owner's approval.
- Date: 2026-10-14. Base: `main` at `5bfe273` (5D.5.1b, pushed). The working tree was clean before this file.
- Inputs:
  - captures in `docs/design/phase5d_5b/` (33 files, taken during the 5D.5.1b device checks);
  - `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_1B.md`, `docs/DAILY_BRIEF_UI_REFINEMENT_PLAN.md`, `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_1.md`;
  - the Compose / SwiftUI / design-system code at `5bfe273`.

Scope: visual polish only. No change to the takeaway logic, data, calculations, navigation, MOCK scenarios, disclosures (Sample, stale, delayed,
offline) or global component defaults. No Learn redesign, no charts.

---

## A. Screenshot-by-screenshot findings

**Caveat:**
- `android-360dp-1.0x-light-top.jpg` and `android-360dp-1.0x-light-2.jpg` were captured **before** the 5D.5.1b tile fix F-1, so they show
  index rows at 1.0×.
- The post-fix tiles at 1.0× are `android-360dp-1.0x-light-tiles.jpg` and `android-360dp-1.0x-dark-tiles.jpg`.

| # | Capture(s) | Finding | Severity |
|---|---|---|---|
| A-1 | `android-360dp-1.0x-dark-tiles.jpg`, `ios-iphone16-default-dark-top.jpg`, `ios-iphone16-default-light-2.jpg`, `android-360dp-1.3x-light-2.jpg` | **Mixed surface language.** The index tiles are borderless surfaces; the story group directly below is a **bordered** `StockCard` (a 1 px outline, very visible in dark). Two styles for two neighbouring "groups". | P1 |
| A-2 | `android-360dp-1.0x-light-top.jpg` | **Gap above the meta line on Android:** about 56 dp between the top bar and "After-close brief · Oct 7 · 1 min read". iOS has no such gap (`ios-iphone16-default-light-top.jpg`). The source needs measuring (list top padding vs top-bar padding) before changing. | P1 |
| A-3 | `android-360dp-1.0x-light-top.jpg`, `ios-iphone16-default-light-top.jpg` | **Dense metadata block under the summary:** three caption lines ("Updated Oct 7 · Market data for the Oct 7 session", "US stocks: Closed for the day (after-hours trading)", "Canadian stocks: Closed"). "Oct 7 session" repeats what "All values: Close · Oct 7" says under the tiles. | P1 |
| A-4 | `android-360dp-1.0x-light-3.jpg`, `ios-iphone16-default-light-2.jpg` (guest) | **Watchlist empty-state action looks indented and weak:** `StockEmptyState` draws "Add companies" as a TEXT `StockButton` with its own horizontal padding, so it sits about 6 dp right of the text above. It is the section's only action, but it reads as a stray link. | P1 |
| A-5 | `android-360dp-1.0x-light-4.jpg`, `android-360dp-1.3x-light-earnings.jpg` | **Concept card bottom:** the 48 dp action row plus the full card padding leaves a visible empty band under "Show details / Understand more" (plan U-13). | P2 |
| A-6 | `android-360dp-1.0x-light-4.jpg`, `android-360dp-1.3x-light-earnings.jpg` | **Footer rows look different:** "About this brief" (blue label with an info icon, disclosure style) sits above "Previous briefs" (bold title with a calendar icon, navigation-row style). Two styles for two secondary links. | P2 |
| A-7 | `android-360dp-1.0x-light-3.jpg` | **Story card bottom:** extra space under the last story's "Show details / Read article" row (card padding + 48 dp row). Acceptable, but it adds to the "boxed" feel. | P2 |
| A-8 | `android-360dp-1.0x-light-tiles.jpg` | Tiles in light mode: a white surface on `#F7FAFD` is a low but visible contrast. Names in `label`/`textSupporting` are readable. % changes are prominent with arrow + sign + colour. Equal heights. **Keep.** | — |
| A-9 | `android-360dp-1.0x-dark-scenario-us-open-ca-closed-tiles.jpg` | Per-tile labels when states differ make the live tiles tall ("During today's session (may be delayed) · 10:40 AM ET" wraps 4 lines). Correct and honest; only the length is long. The text is server wording, so no change proposed (a shorter label would be a data-wording change). | note |
| A-10 | `android-360dp-1.3x-light-earnings.jpg`, `android-360dp-2.0x-light-earnings.jpg` | Earnings rows: structure reads well; the badge wraps below the date at 2.0× as designed; dividers between rows help scanning. **Keep.** | — |
| A-11 | `android-360dp-1.0x-dark-history.jpg` | History: clean rows with one divider style, badge beside the title. **Keep.** | — |
| A-12 | `android-360dp-1.0x-light-home.jpg`, `android-360dp-1.0x-dark-home.jpg`, `ios-iphone16-default-light-home.jpg` | Home "Markets today" is a bordered card, as are all Home cards (Home design from 4A). The brief entry inside it is a row. Changing the card would be a Home redesign (excluded), so it stays. | — |
| A-13 | `android-360dp-1.0x-dark-offline-stale.jpg`, `ios-iphone16-default-dark-offline-stale.jpg` | The single stale + offline banner (bordered `StockBanner`, WARNING) is correct. **Keep the border:** it is a warning. | — |
| A-14 | `ios-iphone16-axxl-dark-live-top.jpg`, `ios-iphone16-ax5-dark-live.jpg`, `android-360dp-2.0x-light-top.jpg` | At large text the header is long. The takeaway stays the largest text, and the summary is folded. The three metadata lines (A-3) become six or more at AX sizes. | P1 (same as A-3) |

**Missing captures** (not verified visually; the 5D.5.1c checks should add them):
- **Android:** dark 1.3× and 2.0×; light history; a populated watchlist; the StockSteps+ (Plus) state; an expanded story and an expanded concept.
- **iOS:** light AX XL and AX5; stories, earnings, Learn and footer at AX sizes; history; dark Home; a signed-in watchlist and earnings (the app
  is in guest mode); any MOCK scenario.

## B. Border removal matrix
| Section | Current | Proposed | Reason | Android | iOS |
|---|---|---|---|---|---|
| Market tiles | Borderless `surface`, card radius | **Keep** | Already the target style | `StockIndexTile` | `StockIndexTile` |
| Index rows (large text) | Rows + `StockDivider` | **Keep** | Dividers help scanning stacked numbers | `BriefIndexRow` | `BriefIndexRow` |
| Market stories group | **Bordered** `StockCard` (surface + 1 px border) | **Borderless surface** (`StockCard(bordered = false)` / `.stockCard(bordered: false)`), the same `surface` and radius as the tiles; keep the thin divider between stories | Matches the tiles; removes the most visible outline; still one intentional group (D-B7) | `DailyBriefScreens.kt` (stories item) | `DailyBriefScenes.swift` (`stories`) |
| Watchlist empty state | Text + TEXT button (no border) | Text + **one secondary button** (`StockButtonVariant.SECONDARY` / `.stockSecondary`): a tinted fill, left-aligned with the text | A visible, intentional action without an outline; fixes the indentation (A-4) | local composition in `WatchlistSection` (not `StockEmptyState`) | `watchlist()` (not `StockSectionMessage`) |
| Watchlist rows (populated) | `StockNavigationRow` + dividers | **Keep** | Rows, not cards | — | — |
| Upcoming earnings | Rows + dividers, compact badge | **Keep** | Clean already | — | — |
| Concept of the day | Borderless education-tint card | **Keep tint**; trim the bottom padding (C2) | The one highlighted card | `ConceptCard` | `conceptCard` |
| StockSteps+ insights | Plain section | **Keep** | No container needed | — | — |
| About this brief / Previous briefs | Disclosure row + navigation row | **Keep both, align styling** (D-C4): Previous briefs as the same compact link style as the disclosure (icon + label + chevron) | One footer language | `DailyBriefScreens.kt` footer | footer `VStack` |
| Previous briefs screen | Rows + dividers | **Keep** | — | — | — |
| Stale/offline banner | Bordered `StockBanner` | **Keep** | Warning boundary | — | — |
| Home brief entry | Row inside the bordered Home card | **Keep** (Home out of scope) | — | — | — |

Net border change: **one outline removed** (the story group). Every other surface is already borderless or is a warning or control.
`StockCard` / `.stockCard` defaults are unchanged (the change is per call site).

## C. Spacing and typography (existing tokens only)
| Item | Now | Proposed | Tokens |
|---|---|---|---|
| Android gap above the meta line (A-2) | about 56 dp | Measure first. If the reader's own list top padding is the cause, use `spacing.xs` top padding for this list only; if it's the shared top bar, leave it and record it (out of scope) | `spacing.xs` |
| Header rhythm | meta → takeaway → summary → freshness spaced `sm` | meta→takeaway `xs`; takeaway→summary `xs`; summary→freshness `sm`; banner `sm` | `xs`, `sm` |
| Metadata block (A-3, D-C1) | 3 lines | Freshness = "Updated Oct 7" (+ edition is already in the meta). The two market-session lines move to the top of **Markets**, under the section title, as one caption line each. "Market data for the Oct 7 session" is dropped: "All values: Close · Oct 7" and each row's label carry the session | `caption` / `textMeta` |
| Story group | `StockCard` padding `cardPadding`, inner spacing `md` | Same padding; inner spacing `md`; divider unchanged | `cardPadding`, `md` |
| Concept card bottom (A-5) | `cardPadding` all round | Bottom padding `xs` (the 48 dp action row already gives the space) | `cardPadding`, `xs` |
| Watchlist action | TEXT button | SECONDARY button, start-aligned | — |
| Footer (A-6, D-C4) | two styles | Both as label-style rows (icon + label + chevron, 48 dp): "About this brief" (expands) and "Previous briefs" (navigates) | `label`, `primaryText` |
| Typography | — | No change: takeaway `sectionTitle`, section titles `cardTitle` (`StockSectionHeader`), headlines `bodySemiBold`, tile % `numberMedium` | — |

## D. Component changes and exact files
| Change | How | Files |
|---|---|---|
| Borderless story group | Existing parameter (`bordered = false` / `bordered: false`) | `app/shared/.../presentation/brief/DailyBriefScreens.kt`; `app/iosApp/iosApp/DailyBriefScenes.swift` |
| Header rhythm and metadata move | Local layout code; `DailyBriefDisplay.freshness` loses its second part (core; test update) | the same two files; `core/.../brief/DailyBriefDisplay.kt` + `DailyBriefDisplayTest.kt` |
| Watchlist action | Local composition with the existing `StockButton` / `.stockSecondary` | same two files |
| Concept bottom padding | Existing `StockCard(contentPadding = …)`; iOS `.padding(...)` per edge | same two files |
| Footer link style | Compose: `StockLinkAction` + chevron, or `StockDisclosure`-styled row; iOS `linkButton` + chevron. No new component | same two files |
| Android top gap | Measure; per-screen `contentPadding` only | `DailyBriefScreens.kt` (if the cause is local) |

Untouched: `StockCard`, `StockIndexTile`, `StockIndexRow`, `StockNavigationRow`, `StockBanner`, `StockStatusBadge` defaults; Home; history; server.

## E. Responsive behaviour
- **Default text (Android 1.0×, iOS Default/Large):**
  - meta + Sample → takeaway → summary → "Updated Oct 7" → banner;
  - Markets: two session lines, three tiles, "All values: …", index explainer;
  - stories in one borderless surface → watchlist → earnings → Learn → StockSteps+ → footer.
- **Large text (Android ≥ 1.3×, iOS accessibility sizes):** the same order.
  - The summary folds under "Full summary".
  - Rows replace the tiles (unchanged rule).
  - The session lines wrap.
  - Story actions stack.
  - Earnings badges wrap below the date.
  - Nothing truncated; touch targets 48 dp/pt.

## F. Before / after (default text)
```
BEFORE (5D.5.1b)                         AFTER (5D.5.1c)
After-close brief · Oct 7 … [Sample] ⋯   After-close brief · Oct 7 … [Sample] ⋯
                                         US stocks closed lower.
US stocks closed lower. Canadian …       Canadian stocks closed higher.
At the close on Oct 7, S&P 500 fell …    At the close on Oct 7, S&P 500 fell …
Updated Oct 7 · Market data for the …    Updated Oct 7
US stocks: Closed for the day (…)        ┌⚠ Latest brief available. …┐
Canadian stocks: Closed                  
┌⚠ Latest brief available. …┐           Markets
                                         US stocks: Closed for the day (…)
Markets                                  Canadian stocks: Closed
[S&P][Nasdaq][TSX]  (borderless)         [S&P][Nasdaq][TSX]
All values: Close · Oct 7                All values: Close · Oct 7
                                         
Market stories                           Market stories
┌──────────── bordered ───────────┐      ░░░░░░ surface, no outline ░░░░░░
│ story · divider · story          │      ░ story ─ divider ─ story        ░
└──────────────────────────────────┘      ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
From your watchlist                      From your watchlist
Follow companies …                       Follow companies …
  Add companies   (indented link)        [ Add companies ]  (secondary)
…                                        …
Concept card (tall bottom band)          Concept card (tight bottom)
ⓘ About this brief            ›          ⓘ About this brief              ›
📅 Previous briefs (bold)      ›          📅 Previous briefs (same style)  ›
```

## G. Regression risks
| Area | Risk | Mitigation |
|---|---|---|
| Distinguishing interactive elements | Without the outline, the story group's rows could blend into the page | The surface fill differs from the background (light `#FFFFFF` on `#F7FAFD`, dark `#0D1B2A` on `#07111C`), the same as the tiles; check contrast in both themes on device |
| Touch targets | Footer restyle could shrink targets | Keep 48 dp/pt rows (`StockLinkAction` / `linkButton` already enforce it) |
| Accessibility | Moving the session lines changes the reading order | New order: header → Markets title → session lines → tiles. That is more logical; check with the spoken-label dump on Android |
| Financial readability | Dropping "Market data for the Oct 7 session" could hide the session date when indices are missing | Keep the freshness second part when `marketSnapshot` is empty or has no shared label and no rows (test: missing-index, empty snapshot) |
| Theme contrast | Light-mode surface contrast is subtle | Device check in light at 1.0× and 2.0× |
| Navigation | Footer restyle of Previous briefs | Same action (`BriefAction.History` / `onHistory`) |
| Home | None | Not touched |

## H. Implementation phases (minimal)
| Step | Content | Notes |
|---|---|---|
| **C1+C2** (one change) | Borderless story group; header rhythm; metadata move; watchlist secondary action; concept bottom padding; footer link style; Android top gap (if local) | Both platforms together (parity is built in, so no separate C3) |
| **C4** | Core + shared tests sequentially; Android debug build; iOS build; device captures (below); report `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_1C.md` | Results as PASS / FAIL / BLOCKED / NOT RUN |

### Verification plan (after approval)
- **Tests and builds:** `:core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test` (sequential);
  `:server:test` (no server change); `assembleDebug`; `xcodebuild`.
- **Android 360 dp:** 1.0× / 1.3× / 2.0× in **light and dark**; light history; offline + stale; Home.
- **iOS iPhone 16:** Default / AX XL / AX5 in **light and dark**; stories, Learn and footer at AX sizes; history; offline + stale; Home light/dark.
- **BLOCKED unless the owner signs in on iOS:** signed-in watchlist and earnings on iOS (guest mode).
- **NOT RUN unless arranged:** populated watchlist (needs watchlist data), StockSteps+ content, screen readers.

## I. Decisions for the owner
| ID | Decision | Recommendation |
|---|---|---|
| D-C1 | Move the two market-session lines under the Markets title, and shorten freshness to "Updated Oct 7" (keeping the session wording when no index shows it) | **Yes** |
| D-C2 | Story group: borderless surface (like the tiles) vs keep the outline | **Borderless surface** |
| D-C3 | Empty-watchlist action: secondary (tinted) button vs text link | **Secondary button** |
| D-C4 | Footer: give "Previous briefs" the same compact link style as "About this brief" | **Yes** |
| D-C5 | Android top gap: fix only if it is the reader's own padding (otherwise record it as a shared top-bar item) | **Yes, local only** |
