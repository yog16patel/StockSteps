# Review package — Phase 5D.5.2 Learn final polish (r2)

**Phase:** 5D.5.2 Learn — targeted polish after owner approval in principle.
**Git baseline:** `057d6e6` "Redesign Learn for Global UI Refinement Phase 5D.5.2…". Under review: the commit "Polish Learn for Global UI Refinement Phase 5D.5.2 with ranked term search, single-column topics at very large text, a folded Start here sentence at large text and a shorter term illustration on Android and iOS".
**Sheets:**
- `CHATGPT_UI_REVIEW_ANDROID.png`, `CHATGPT_UI_REVIEW_IOS.png`: changed screens only, BEFORE vs AFTER.
- MOCK build; Learn content is local catalogue text.
- Earlier package: `docs/reviews/phase5d_5_2/`.

## Changes implemented
1. **Search relevance** (core `LearnDisplay.search`). Every match is kept, ranked by tier, catalogue order inside a tier:
   - exact term name or title;
   - term name starts with the query;
   - all words in the title;
   - all words in the title or topic;
   - definition-only.

   "earnings" now lists *earnings beat* first (before: EPS, P/E, earnings beat).
2. **Very large text:** topic tiles become one column of compact rows (icon beside the name).
   - Android ≥ 1.75× font scale (covers 2.0×); iOS AX4 and up.
   - Between that and the large-text threshold, the two-column compact tiles stay.
3. **Start here density at large text** (Android ≥ 1.3×, iOS accessibility sizes): the long sentence folds under "What's covered", with the same
   text one tap away. Normal text is unchanged.
4. **Term page illustration band** is shorter: 128 dp/pt band, 96 image (was a 160 band, 128 image).
5. No other changes. Term-page bottom access was verified; it needed no change.

## Affected files
- **core:** `learning/LearnDisplay.kt` (`search`, `searchTier`, `START_DETAILS`), `LearnDisplayTest.kt` (12 tests, +2).
- **shared:** `presentation/learn/LearnScreen.kt`, `LearnTopicsScreen.kt`.
- **iOS:** `LearnViews.swift`.

## Architectural decisions
- Ranking lives in core, so both platforms show the same order. Tiers are pure functions with deterministic tests.
- The one-column switch uses the existing `StockCardGridLayout` rule (its `accessibilityText` flag) rather than a new rule.
- On iOS the column count is passed into the tile instead of being stored as view state during layout.

## Test and build results
| Suite / build | Result |
|---|---|
| core JVM | **PASS** 534/0 |
| core iOS | **PASS** 534/0 on rerun. **FAIL ×1 in the full run:** `UserDataRepositoriesTest.accountsAndEnvironmentsNeverShareData` (≈1 s wait). Untouched code; a second intermittent test, tracked separately |
| shared Android / iOS | **PASS** 246/0, 242/0 |
| server | **PASS** 501/0 |
| Android `assembleDebug` (MOCK URL) | **PASS** |
| iOS `xcodebuild` (generic + iPhone 16 MOCK) | **PASS** |

## Android verification (emulator-5554, 360 dp, local MOCK)
**PASS:**
- 2.0×: Start card folded; "What's covered" expands to the full sentence; topics in one column.
- 1.0×: home unchanged.
- **Start learning** opened Guided Research for Apple (0 of 5 steps).
- Topic navigation (Profit, 3 terms).
- Search "earnings" ranked.
- **Practice** opened Practice Portfolio (signed-in emulator).
- Term page bottom fully above the Sample strip and bottom navigation at 1.0× and 2.0×.

## iOS verification (iPhone 16, guest)
**PASS:**
- AX5: Start folded; four compact topic rows on one screen (before: one tile per screen).
- Topic (Business) → term (revenue) → scrolled to the bottom (links above the Sample strip and home indicator) → **Next** → revenue growth.
- Default home unchanged.

## Known issues / unverified
- **NOT RUN:**
  - dark mode for the changed screens (only layout changed; tokens unchanged);
  - Android 1.3× and iOS AX XL to AX3 (two-column compact tiles, unchanged from `057d6e6`);
  - TalkBack and VoiceOver;
  - iPhone SE.
- **BLOCKED:** iOS signed-in progress cards (guest mode).
- **Intermittent tests:**
  - `PortfolioSessionStateTest`: the fix is uncommitted in worktree `charming-hodgkin-98e850`.
  - `UserDataRepositoriesTest`: new this run; not investigated (out of scope).

## Deviations from the request
- Item 5: the only spacing change justified by the captures was the illustration band; nothing else was changed.
- The one-column switch is at Android 1.75× (not exactly 2.0×), so the same rule covers scales between 1.75 and 2.0.

## Decisions requiring owner approval
1. Investigate `UserDataRepositoriesTest` as a separate task.
