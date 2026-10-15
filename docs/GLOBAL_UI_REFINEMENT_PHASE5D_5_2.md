# Global UI Refinement Phase 5D.5.2 — Learn redesign (A + B + C)

Status: **committed and pushed as "Redesign Learn for Global UI Refinement Phase 5D.5.2 with a Start here card, colour-coded topic tiles, today's concept, an Explore topics list, topic pages, term reading pages and a local term search on Android and iOS"** (on top of `bab5a46`); verified on devices.
- Plan: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_2_PLAN.md`.
- Reference: `docs/design/phase5d_5_2/REFERENCE-learn.webp`.
- Review package: `docs/reviews/phase5d_5_2/`.
- Owner direction (2026-10-14):
  - "Don't ask me continue with what you recommend": the plan's recommended decisions D-L1…D-L8 were adopted.
  - "try match the ui and icons with the reference … Explore topic section" and "closely match the reference image all sections, type,
    style, icons, sections": followed, using existing theme tokens only.

## 1. What changed
Display and navigation inside Learn only. Educational text is unchanged; no backend, progress or entitlement change.

| Screen | Before | After (matches the reference's structure) |
|---|---|---|
| **Learn home** | Intro → Start learning card with four company links → Research a company → Practice card → 14 flat term rows | In-page **Learn** title + 🔍 (both platforms) → intro → **Start here** light-blue card: existing illustration, "Understand a stock in five steps", **one Start learning button** (resume, else the first company not yet researched, else search) → **Explore topics** 2×2 tiles (coloured filled icon, topic name, the topic's term names) + **See all** → **Today's concept** card (💡, title, first sentence) → Continue learning (real progress only) → More ways to learn (Research any company, Practice) → Completed research |
| **Explore topics** (new) | — | "‹ Learn", large title, intro; every topic as its own borderless card with a coloured icon, name, term names and a chevron. The term filter appears when opened from 🔍 |
| **Topic** (new) | — | Topic name, "N terms", each term as a card (title + first sentence) |
| **Term page** (new; the reference's "lesson") | Bottom sheet only | "‹ Learn" → illustration band (hidden at large text) → title → "what it is" → light-blue **In this lesson** outline → Why it matters / How to read it / Example / More detail / Keep in mind → Next term, Explore all topics, Research any company |
| **Large text** | One long column | Topic tiles become compact (icon above, name only) and stay in two columns while every word fits (reference "Accessibility" screens); the term page drops the illustration |

**Not in the reference's sense (content doesn't exist; deferred, plan §G):**
- the reference's lesson titles ("What is a stock?", ETFs, Personal finance…), levels, bookmarks and share, a photo hero and diagrams;
- a search icon in the Topics bar (search is the 🔍 on Learn home).

## 2. Files
- **core (new):**
  - `learning/LearnDisplay.kt`: topics, term names and summaries, colour role per topic, filter, outline and sections, next term, today's concept,
    day of year, first-sentence preview, non-breaking slash.
  - Test: `learning/LearnDisplayTest.kt` (10 tests).
- **shared:**
  - `presentation/learn/LearnScreen.kt` (home rewrite), new `LearnTopicsScreen.kt` (topics, topic, term page), `LearnScene.kt`, `LearnRoute.kt`
    (`LearnTopicsRoute`, `LearnTermRoute`);
  - `presentation/AppNavigation.kt`: two destinations; the Learn tab stays selected; back button; title "Learn".
- **iOS:**
  - new `LearnViews.swift` (home, topics, term page, `LearnCardRow`);
  - `LearnScene.swift`;
  - `AppScene.swift`: `learnTarget` destination; Learn uses its own in-page header;
  - the old `LearnScreen` was removed from `GuidedResearchScenes.swift`.
- **Unchanged:** `BeginnerEducation` text, Guided Research, `BeginnerExplanationSheet` (still used by the Brief and Guided Research), shared
  `StockNavigationRow` / `StockCard` defaults, the server.

## 3. Verification
### 3.1 Automated (final code, run one after another)
| Suite / build | Result |
|---|---|
| `:core:jvmTest` | **532/0** on `--rerun`. The full sequential run had 1 failure: `PortfolioSessionStateTest`, a 5 s timeout under load. It is unrelated: the Portfolio test was last changed in `095005d` |
| `:core:iosSimulatorArm64Test` | **532/0** |
| `:app:shared:testAndroidHostTest` | **246/0** |
| `:app:shared:iosSimulatorArm64Test` | **242/0** |
| `:server:test` | **501/0** |
| `:app:androidApp:assembleDebug` | **PASS** (default and MOCK-URL builds) |
| `xcodebuild` (generic simulator, unsigned) | **BUILD SUCCEEDED**; iPhone 16 ad-hoc MOCK build BUILD SUCCEEDED |

### 3.2 Android (`emulator-5554`, 360 dp, MOCK build; Learn content is local)
| Check | Result |
|---|---|
| Learn home 1.0× light: title + 🔍, Start here, topic tiles, today's concept | PASS |
| See all → Explore topics (cards, coloured icons) | PASS |
| Topic → Valuation (3 terms) → term page (EPS): outline, sections | PASS |
| 🔍 → filter "dividend" → 2 terms (dark) | PASS |
| 2.0×: compact two-column tiles; concept wraps "P/E ratio" whole | PASS |
| 1.0× dark home | PASS |
| Start learning → Guided Research; Practice; Research any company | NOT RUN on Android (routes unchanged; tapped on iOS) |

### 3.3 iOS (iPhone 16, guest mode)
| Check | Result |
|---|---|
| Learn home Default light / dark | PASS |
| Topics, topic (Profit, Valuation), term pages (profit margin, P/E) incl. Next / Explore all topics / Research any company | PASS |
| Bar shows only "‹ Learn" on pushed pages (no repeated title) | PASS (after fix) |
| AX XL dark (live change): compact two-column tiles; "P/E ratio" not split | PASS (after fix) |
| AX5 home (top) | PASS |
| Research any company opens the company search sheet | PASS |

### 3.4 Fixes during verification
1. Concept and term previews: the first sentence only (the full definition stays on the term page).
2. iOS duplicated "Learn" (back label + title): the title is hidden in the bar but kept for back labels.
3. "What is the P/ / E ratio?" slash wrap: a word joiner after "/" in Learn titles (wording unchanged; tested).
4. Topic tile grid rule counts the icon beside the text, so no topic word breaks.

**Screenshot evidence:** at the owner's request (2026-10-15) the individual captures were deleted. The evidence kept is the two contact sheets in
`docs/reviews/phase5d_5_2/` (BEFORE + five AFTER screens per platform). The other results above were reviewed during the session but have no
saved image.

## 4. Known issues and limits
- **iOS sign-in screen (pre-existing, not Learn):** mixed font sizes after a *live* Dynamic Type change. During navigation a stray tap focused its
  email field; nothing was typed.
- **Today's concept** uses the Brief's ordinary-day rotation, in UTC. On earnings-heavy or market-closed days the Brief can show a different
  concept.
- **Term search** matches the "what it is" text too, so "dividend" also finds free cash flow (its definition mentions dividends).
- **Flaky test (unrelated):** `PortfolioSessionStateTest` (core JVM) timed out twice under full-suite load (5 s); it passes on rerun.
- **NOT RUN:**
  - signed-in progress cards (Continue / Completed) on device;
  - TalkBack and VoiceOver;
  - iPhone SE;
  - Android 1.3×;
  - iOS AX5 below the header.
- **Deferred:** new lessons and content, levels, bookmarks and share, per-term art, deep links to a term from other screens (D-8).

## 5. Verdict
**PASS WITH CONDITIONS:** the conditions are the NOT RUN items above.

## 6. Final polish (r2, 2026-10-15; committed as "Polish Learn for Global UI Refinement Phase 5D.5.2 with ranked term search, single-column topics at very large text, a folded Start here sentence at large text and a shorter term illustration on Android and iOS")
Targeted changes after the owner approved the Learn UI in principle:
- **Search ranking:** exact name/title → name prefix → title → title or topic → definition; all matches kept (core tests +2).
- **Very large text:** topic tiles become one column of compact rows (Android ≥ 1.75× font scale, iOS AX4 and up).
- **Start here at large text:** the sentence folds under "What's covered", with the same text one tap away.
- **Term page:** a shorter illustration band.

Verified on device:
- Android: Start learning → Guided Research; topic navigation; ranked search; Practice; term-page bottom above the bottom navigation at 1.0×
  and 2.0×.
- iOS AX5: topic → term → Next; term-page bottom above the safe area.

Tests: core 534/0 (JVM) and 534/0 (iOS on rerun; one intermittent `UserDataRepositoriesTest` failure in the full run), shared 246/0 and 242/0,
server 501/0; builds OK.

Review package: `docs/reviews/phase5d_5_2_r2/`.

## 7. Closure (2026-10-15)
**Status:** the owner approved closing Phase 5D.5.2 Learn after polish r2. Implementation commits: `057d6e6` (redesign) and `0730773` (polish r2),
both pushed. This closure record is a separate docs-only commit.

### Final verification
| Area | Result |
|---|---|
| Core tests | JVM 534/0; iOS 534/0 on rerun |
| Shared tests | Android 246/0; iOS 242/0 |
| Server tests | 501/0 |
| Builds | Android `assembleDebug` and iOS `xcodebuild` PASS |
| Android device (360 dp) | Home at 1.0× light and dark, and 2.0× (compact single-column topics, folded Start sentence); Start learning → Guided Research; topic navigation; ranked search; Practice; term-page bottom above the bottom navigation at 1.0× and 2.0× — PASS |
| iOS device (iPhone 16, guest) | Home at Default light and dark, AX XL and AX5; topics, topic and term pages; Next term; Research any company; term-page bottom above the safe area at AX5 — PASS |
| Review packages | `docs/reviews/phase5d_5_2/` and `docs/reviews/phase5d_5_2_r2/` |

### Remaining limitations and follow-ups
**Outstanding checks (not closed by this phase):**
- iOS signed-in progress (Continue / Completed cards): **BLOCKED** (the simulator runs in guest mode; the owner must sign in).
- TalkBack and VoiceOver: **NOT RUN** (they need the owner).
- Smaller devices (iPhone SE, narrow Android): **NOT RUN**.
- Dark mode for the r2-changed screens, Android 1.3× and iOS AX XL–AX3 after r2: **NOT RUN**.

**Minor UI follow-up:**
- iOS AX5 term-page footer rows ("Next…", "Explore all topics", "Research any company") show the icon vertically centred against a multi-line
  label, so it doesn't line up with the label's first line.
- Evidence: the r2 iOS sheet, "term page bottom above safe area · AX5".
- Cosmetic; it does not affect access.

**Separate investigation items (not part of Learn):**
- `UserDataRepositoriesTest.accountsAndEnvironmentsNeverShareData` (core iOS) failed once in a full sequential run ("Expected <[AAPL]>, actual
  <[]>" after ~1 s) and passed on rerun. Untouched watchlist-data code; needs its own investigation.
- `PortfolioSessionStateTest` timeout: a test-only fix is uncommitted in worktree `charming-hodgkin-98e850`, waiting for the owner.

**Deferred product work:**
- new lessons and content, levels, bookmarks and share;
- deep links to a term from other screens (D-8).
