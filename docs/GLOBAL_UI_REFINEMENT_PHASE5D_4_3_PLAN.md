# Global UI Refinement Phase 5D.4.3 — Earnings QA and stabilization (PLAN, awaiting owner approval)

Date: 2026-10-14. Status: **approved and implemented** (see `GLOBAL_UI_REFINEMENT_PHASE5D_4_3.md`).

## 1. Repository status
- `main` = `origin/main` at `93aac9c` (Phase 5D.4.2, **committed and pushed**). The prompt's "not committed on top of `a4f35c4`" is out of date.
- Working tree: only the backend session's root `PROJECT_HANDOFF.md` and `docs/project-status.md` (not ours; untouched).

## 2. Phase 5D.4.2 review (what holds)
- Borderless content sections read clearly in light and dark on Android (`docs/design/earnings_phase5d_4_2/after-a360-details-top.png`, `…-dark.png`). No border was reintroduced, and the global card default is unchanged.
- Reported value emphasis, "Comparison unavailable" below the title, "No estimate", the reaction % line, and Show details + Learn more on one row all work at 1.0× / 1.3× / 2.0× (`…-1.3x.png`, `…-2.0x.png`).
- **No P0 defects** (no clipping, overlap, letter-by-letter wrapping or hidden values) in any Android capture.
- **iOS runtime for 5D.4.2 is unverified** (simulator input); iOS Details is still blocked (sign-in).

## 3. Findings (defect vs preference marked)

| ID | Sev | Type | Finding | Evidence |
|---|---|---|---|---|
| Q-01 | P1 | Defect (redundancy) | **"Reported" appears twice** in the header: the status badge "Reported" and the line "Reported · Wed, Jul 22 · After market close" | `after-a360-details-top.png`, `…-top-2.0x.png` |
| Q-02 | P1 | Defect (large text) | **Header is ~1 full screen at 2.0×.** The 40 dp logo keeps a left column, so the 3-line name, the ticker and all following lines wrap in a narrower width ("Thu, Oct / 22"); the stacked full-width buttons add 2 × 56 dp. EPS starts below the fold | `after-a360-details-top-2.0x.png` |
| Q-03 | P2 | Defect (spacing) | **About 24 dp of dead space under the last toggle row** in every section (16 dp card padding + the 48 dp touch row's centred text) | `after-a360-details-2.png` (Revenue, reaction) |
| Q-04 | P2 | Preference | In the large-text row mode every key metric has the same weight (the "quiet" emphasis only applies in columns) | `after-a360-details-2-2.0x.png` |
| Q-05 | P2 | Verification | The last content (Learn / Sources) must scroll clear of the Sample strip and the bottom bar. Not yet checked at 2.0× | — |
| Q-06 | P2 | Verification | Results reaction chips (First Session / 3 / 5 Sessions) at 2.0× and in dark: scroll row with fade; not re-captured since 5D.3 | — |
| Q-07 | — | Blocked | iOS: simulator taps stop registering after a few minutes (recovered once by shutdown + boot in 5D.4); Earnings Details needs a signed-in session (the local Auth emulator starts empty; the saved session has ended) | 5D.4.1/5D.4.2 reports |

Already reviewed and **not** changed: EPS explanations (one sentence + Show details since 5D.4), insights (compact rows, server text unchanged), the history chart (width-based height, legend, y labels), Learn rows, the StockSteps+ field and actions, missing-data states.

## 4. Proposed changes (BEFORE → AFTER)

| ID | Before | After | Platforms |
|---|---|---|---|
| Q-01 | Badges "Reported" + "Sample", then "Reported · Wed, Jul 22 · After market close" | When the status is Reported / Partially reported, the badge row shows only "Sample" (if sample data) and the date line keeps "Reported · …" (the status stays visible in words). Other statuses (Upcoming, Results pending, Unavailable) keep their badge, since their line has no status word. Shared rule `EarningsDisplay.statusBadgeShown(status)` + test | Android, iOS |
| Q-02 | Logo beside the name at every size | At large text (Compose `fontScale ≥ largeFontScale`, iOS `≥ .xxxLarge`, the same thresholds `StockActionLayout` uses): the name uses the **full width** on line 1, and the logo moves onto the ticker line ("[logo] CNR.TO · TSX"). Normal sizes are unchanged. No text is shrunk and nothing is truncated; this removes the narrow-column wrapping (~2–3 lines at 2.0×) | Android, iOS |
| Q-02b *(decision)* | Stacked full-width Remind me / Company details at large text | Option A (recommended): unchanged; they are touch targets and stay full-width. Option B: render them as one row of two text-style buttons at large text (shorter, but less prominent) | — |
| Q-03 | Card bottom padding 16 dp below a 48 dp toggle row | Sections whose last child is the toggle row use `contentPadding` bottom `sm` (8) instead of 16; the 48 dp target stays. Per Earnings call site only | Android, iOS |
| Q-04 *(optional)* | All row-mode values the same weight | Leave as is (recommended). Changing the shared `StockMetricGrid` row mode would affect Portfolio and Home (Portfolio uses `rowsWhenNarrow`) and needs a separate decision | — |
| Q-05/06 | — | Verify only; fix (bottom content padding) only if something is hidden | — |

## 5. Implementation approach
- **Shared Kotlin:** `EarningsDisplay.statusBadgeShown` (Reported / Partially reported → false), a unit test, and nothing else.
- **Compose:** in `DetailsHeader`, a `largeText` branch (logo on the ticker row); the status badge is guarded by the rule; a `bottomPadding` parameter on the Earnings call sites of `StockCard(contentPadding = …)`.
- **SwiftUI:** the same with `dynamicTypeSize >= .xxxLarge` and `.stockCard(padding:)` (custom `EdgeInsets` via `.padding` + `.stockCard(padding: 0, bordered: false)` where needed).
- **Not touched:** core, server, data, calculations, currency, classifications, fixtures, auth, reminders, navigation, entitlements, AI, other screens, global component defaults.

## 6. Expected improvement
- At 2.0×: the header gets ~3–4 lines shorter (no repeated "Reported", name and dates at full width), so the EPS takeaway starts about a third of a screen higher.
- At 1.0×: one fewer badge, and each section ends ~8 dp tighter.

## 7. Regression risks
- **Q-01:** the status must stay visible in words for every status (tested per enum value). Screen readers still hear "Reported" from the date line.
- **Q-02:** the logo row must not squeeze the ticker at AX sizes (it wraps); the heading semantics stay on the name.
- **Q-03:** bottom padding must not cut the focus ring or the touch target of the last toggle.

## 8. Test matrix

| Config | Android 360 dp | iPhone 16 |
|---|---|---|
| Light 1.0× / Default | Details + Results | Results; Details BLOCKED until sign-in |
| Light 1.3× | Details | — |
| Light 2.0× / AX large | Details + Results (chips, chart, last item above the strip) | Results (fresh launch + live change) |
| Dark 1.0× / Default | Details + Results | Results |
| Dark 2.0× / AX large | Details | Results |
| Expand/collapse, navigation (Remind me sheet, Company details, Results ↔ Details) | yes | Results only |

- Suites, sequentially: shared Android and iOS tests, `assembleDebug`, `xcodebuild` (core only if touched; it isn't planned).
- Screenshots go in `docs/design/earnings_phase5d_4_3/`, at the same scroll positions as 5D.4.2.

## 9. Blockers and how to clear them safely
- **iOS input:** shut down and boot the iPhone 16 simulator (keeps all data; no erase). Needs your OK this time (decision 5D43-2).
- **iOS Earnings Details:** start the local Auth emulator, then **you** sign in or create the test account in the app. I won't enter credentials or create accounts. Without that, iOS Details stays BLOCKED.
- **Screen readers:** NOT VERIFIED (deferred).

## 10. Decisions for you

| ID | Decision | Recommendation |
|---|---|---|
| 5D43-1 | Approve Q-01, Q-02 and Q-03 | Yes |
| 5D43-2 | Restart (shut down and boot, no erase) the iPhone 16 simulator for the iOS runs | Yes |
| 5D43-3 | Q-02b: header actions at large text stay full-width stacked (A) or become a compact text row (B) | A |
| 5D43-4 | Q-04: leave the shared row-mode emphasis unchanged | Yes (avoids touching Portfolio and Home) |
| 5D43-5 | iOS Earnings Details: you sign in on the local Auth emulator when convenient | Your action |
