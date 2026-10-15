# Review package — Phase 5D.5.2 Learn redesign

**Phase:** Global UI Refinement 5D.5.2 (Learn) — A (home), B (topics, topic, term page), C (local term filter).
**Git baseline:** `bab5a46` "Add the AI review workflow…". Under review: the commit "Redesign Learn for Global UI Refinement Phase 5D.5.2 with a Start here card, colour-coded topic tiles, today's concept, an Explore topics list, topic pages, term reading pages and a local term search on Android and iOS" on top of `bab5a46`.
**Sheets:** `CHATGPT_UI_REVIEW_ANDROID.png`, `CHATGPT_UI_REVIEW_IOS.png`. Each shows BEFORE plus five AFTER screens. MOCK build; Learn content is
local catalogue text.
**Full report:** `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_2.md`. **Plan:** `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_2_PLAN.md`.
**Reference:** `docs/design/phase5d_5_2/REFERENCE-learn.webp` (illustrative).

## Changes implemented
- **Learn home:**
  - in-page title + search;
  - one "Start here" card with a single Start learning action (resume, else the first company not yet researched, else search);
  - a 2×2 Explore topics grid (coloured icon, topic, its term names) + See all;
  - Today's concept;
  - real-progress cards only;
  - More ways to learn.
- **New screens:** Explore topics (card list), Topic (its terms), and a Term reading page ("lesson": outline + sections from the existing
  catalogue, next term).
- **Search:** a local term filter (title, topic, definition), opened from the search icon.
- **Large text:** compact two-column tiles while every word fits; the decorative band is hidden on the term page.

## Affected files
- **core:** `learning/LearnDisplay.kt` (new), `LearnDisplayTest.kt` (new, 10 tests).
- **shared:** `presentation/learn/LearnScreen.kt`, `LearnTopicsScreen.kt` (new), `LearnScene.kt`, `LearnRoute.kt`, `presentation/AppNavigation.kt`.
- **iOS:** `LearnViews.swift` (new), `LearnScene.swift`, `AppScene.swift`, `GuidedResearchScenes.swift` (old Learn screen removed).
- **docs:** plan, report, the reference image `docs/design/phase5d_5_2/REFERENCE-learn.webp`, this package.

## Architectural decisions
- All copy and rules live in core `LearnDisplay`, and both platforms render from it.
- Topics come from the existing `EducationEntry.topic`. Names and summaries only strip "What is / the / a / ?" from existing titles. No educational
  text was added or reworded.
- Topic colours map to existing theme roles (primary, positive, learn accent, caution). They are decorative, alternate between neighbours (tested),
  and are never a gain/loss cue.
- Navigation:
  - Android adds two type-safe routes, keeps the Learn tab selected and adds a back button.
  - iOS adds one `learnTarget` destination in `AppScene`, with nested pushes inside the Learn views.
- Today's concept reuses `BriefContent.concept` (ordinary-day rotation).
- Shared components (`StockCard`, `StockNavigationRow`) are unchanged. An `iconTint` parameter was added and then reverted once unused.

## Test and build results
| Suite / build | Result |
|---|---|
| core JVM | **PASS** 532/0 on rerun. **One FAIL in the full run:** unrelated `PortfolioSessionStateTest` 5 s timeout under load, seen 3× this session |
| core iOS | **PASS** 532/0 |
| shared Android / iOS | **PASS** 246/0, 242/0 |
| server | **PASS** 501/0 |
| Android `assembleDebug` | **PASS** |
| iOS `xcodebuild` | **PASS** |

## Android verification (emulator-5554, 360 dp)
**PASS:**
- home at 1.0× light and dark, and 2.0×;
- See all → topics;
- Topic → term page;
- search "dividend" (dark).

**NOT RUN:** 1.3×; Start learning / Practice taps on Android.

## iOS verification (iPhone 16 simulator, guest)
**PASS:**
- home in light and dark;
- topics, topic and term pages;
- Research any company → search sheet;
- AX XL live change (tiles, concept);
- AX5 home top.

**NOT RUN:** AX5 below the header; signed-in progress.

## Evidence note
At the owner's request the individual and full-resolution captures were deleted (2026-10-15). The two contact sheets are the only saved
screenshots; the other device results were reviewed live during the session.

## Known issues
- **Unrelated, pre-existing:**
  - the flaky Portfolio test;
  - mixed font sizes on the iOS sign-in screen after a live text-size change.
- Today's concept can differ from the Brief's on earnings-heavy or closed days.
- Term search also matches definitions ("dividend" finds free cash flow).

## Unverified scenarios
- **NOT RUN:** signed-in Continue / Completed cards on device, TalkBack and VoiceOver, iPhone SE, Android 1.3×.
- **BLOCKED:** none.

## Deviations from the plan
- The plan stopped for approval. The owner said to "continue with what you recommend", so the recommended decisions were adopted and
  implemented.
- After the owner asked for a closer match to the reference:
  - the Start card and lesson outline use primary blue instead of the learn tint;
  - topic tiles moved from icon tiles to a coloured icon beside the text;
  - the topics list moved from rows to cards;
  - the topic label was removed from the term page;
  - section headings are smaller.
- **Added during verification:**
  - first-sentence previews;
  - a non-breaking slash ("P/E");
  - iOS bar title hidden (back label kept).

## Decisions requiring owner approval
1. New learning content (the reference's lessons such as "What is a stock?", ETFs, Personal finance) — a separate content phase.
2. Deep links from other screens ("Open Learn", metric ⓘ) to a term page (D-8).
3. Merge the flaky `PortfolioSessionStateTest` timeout fix (test-only, in worktree `charming-hodgkin-98e850`).
