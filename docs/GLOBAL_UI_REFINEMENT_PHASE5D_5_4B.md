# Global UI Refinement Phase 5D.5.4B: At a Glance, Understand and Why Did It Move?

- **Status:** implemented and verified on Android and iOS against the local MOCK server. Approved by the owner and committed locally as "Refine Company Details Understand rows and Why Did It Move for Global UI Refinement Phase 5D.5.4B with category, interpretation, one supporting fact and a note per row, an explanation sheet on every row, a bold lead sentence and one P/E comparison on Android and iOS" (**not pushed**).
- **Baseline:** `789c2a9`, "Record the closure of Global UI Refinement Phase 5D.5.4A …" (pushed). 5D.5.4A is committed and closed.
- **Inputs:**
  - plan `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4_PLAN.md` (phase B);
  - owner brief with a before/after reference image (Android 360 dp and iPhone 16).
- **Review package:** `docs/reviews/phase5d_5_4b/` (report, Android and iOS contact sheets, focused diff). Raw captures are in `captures/`
  (git-ignored), with 5D.5.4A "before" frames in `captures/before/`.

## 1. Changes implemented
- **Understand {Company}:**
  - Each row now reads **Category → Interpretation → Supporting metric → Short explanation**, the same on every row:
    - title "Financial Health";
    - interpretation as tinted words ("More debt than cash", in the badge's semantic colour, words not colour alone);
    - fact "Cash $36B • Debt $99B";
    - note.
  - Because every row is laid out the same way, rows align and a long interpretation wraps between words at any size. This replaces the pill.
  - The new optional `StockAssessmentRow(statusBelowTitle:)` defaults to off, so the shared badge threshold is untouched.
  - The supporting metric is display-formatted in shared code (`CompanyDetailsPresentation.assessment`):
    - "YoY" is spelled out ("Revenue +6.4% year over year");
    - the Valuation row shows only "P/E 28.0". P/E against its 5-year average stays in the Valuation card (D-E4).
  - **Every row opens its explanation:**
    - Growth → the revenue-growth sheet (company sentence plus "Learn more about revenue growth");
    - Profitability → the net-margin sheet ("Learn more about profit margin");
    - Financial Health → the total-debt sheet ("Learn more about debt");
    - Valuation → the P/E sheet (unchanged).
  - The sheets are the existing At a Glance sheet and the 5D.5.3 `MetricExplanationSheet`; the ids come from `MetricEducation`.
  - `MetricLearnTerms` and the catalogue are unchanged.
- **Valuation & financial health:** the "Understand P/E Ratio" row is removed. It opened the same P/E sheet as the P/E tile and the Understand
  Valuation row. "See detailed valuation" stays.
- **Why Did It Move?:**
  - The existing summary is split for scanning: the **first sentence as a bold lead** (what moved) and the rest of the same text below it in a
    quieter style (`CompanyDetailsPresentation.whyMoving`, verbatim, no rewording).
  - Source pill, "Keep in mind" caution and "See full breakdown" are unchanged. The pill appears only when sources exist.
  - iOS: the title and pill share a line only while both fit whole; otherwise the pill sits under the title (at AX5 it had broken into
    "3 source / s").
- **At a Glance:** no change. 5D.5.4A already gives the reference layout: two columns of borderless tiles with ⓘ beside the label (D-C2), a
  single column at large text, and the 52-week range only. Its values, sheets and Learn links were re-verified.
- **Beginner Insight:** content and component unchanged. It sits directly under the Understand rows with "Why does this matter?" (evidence
  sheet). It isn't folded, because the existing card has no disclosure and the text is four sentences.
- **Bug fixed in shared code (5D.5.4A helper):** `firstSentence` treated any word containing a dot as an initialism, so "0.24%." didn't end a
  sentence. Only letter initialisms ("U.S", "J.P") are skipped now; this is covered by a test.

## 2. Before and after hierarchy (overview, under the section bar)
| Before (5D.5.4A) | After (5D.5.4B) |
|---|---|
| Understand rows: title + pill badge (inline or stacked by length), fact, note; only Valuation tappable | title, tinted interpretation, one supporting fact, note; every row tappable with a chevron |
| Valuation row fact "P/E 28.0 • 5Y Avg 27.5" | "P/E 28.0" (P/E vs average in the Valuation card) |
| Valuation card: metrics, explanation, **Understand P/E Ratio**, See detailed valuation | metrics, explanation, See detailed valuation |
| Why Did It Move?: one body paragraph | bold lead sentence plus the rest in a quieter style |

## 3. Duplicate information removed or consolidated
| Item | Was shown in | Now | Still available in |
|---|---|---|---|
| P/E vs 5-year average | Understand Valuation row and Valuation card | Valuation card only | Valuation card; Valuation screen |
| "Understand P/E Ratio" entry | Valuation card (third entry to the same sheet) | removed | P/E tile ⓘ; Understand Valuation row |

Kept deliberately (different purpose, plan §B F5):
- P/E in At a Glance (snapshot) and in the Valuation card (comparison).
- Revenue growth and net margin in Understand (interpretation) and Financial Highlights (detail with the basis).
- The Beginner Insight sentences: an educational summary with its evidence sheet.
- Sector and industry: "What … does" and About (5D.5.4D scope).

## 4. Files
- **shared:**
  - `presentation/companydetails/CompanyDetailsPresentation.kt`: `AssessmentDisplay`, `assessment`, `supportingMetric`, `GLANCE_SHEET_IDS`,
    `WhyMovingDisplay`, `whyMoving`; the `firstSentence` initialism fix.
  - `presentation/companydetails/CompanyDetailsScreen.kt`: Understand rows, sheet routing, Valuation card, Why Did It Move? lead.
  - `designsystem/components/StockAssessmentRow.kt`: `statusBelowTitle` (optional, default off).
  - Test: `CompanyDetailsPresentationTest` (+2).
- **iOS:**
  - `CompanyDetailsScreen.swift`: rows, `explain` routing, `MetricExplanationSheet`, Valuation card, Why Did It Move? lead, title and pill
    helpers.
  - `DesignSystem/StockDetailComponents.swift`: `StockAssessmentRow.statusBelowTitle`.
- **Not changed:** backend, `CompanyOverview` calculations, assessment rules and labels, `MetricLearnTerms`, Learn catalogue, routes, chart,
  section bar, global defaults.

## 5. Component reuse
`StockAssessmentRow` (new optional mode), `StockSemanticStyles.badge` (status colour), `StockInsightCard`, `EducationSheet` / `InfoSheet`,
`MetricExplanationSheet` (5D.5.3), `MetricLearnMore`, `StockCard(bordered = false)`. No new component.

## 6. Accessibility
- Rows are buttons labelled "Learn what this means" (Android `onClickLabel`; iOS combined element), 48 dp/pt high.
- The interpretation is words in a semantic colour, never colour alone.
- At 2.0× / AX5 the interpretation and facts wrap between words, and the title and pill header stacks without breaking words.
- The sheets and the Learn → Back return work as in 5D.5.3.

## 7. Verification
### Automated
| Suite / build | Result |
|---|---|
| `:core:jvmTest` | PASS 541/0 |
| `:core:iosSimulatorArm64Test` | PASS 541/0 |
| `:app:shared:testAndroidHostTest` | PASS 252/0 (+2 new) |
| `:app:shared:iosSimulatorArm64Test` | PASS 248/0 |
| `:server:test` | PASS 501/0 |
| `:app:androidApp:assembleDebug` | PASS |
| `xcodebuild` (iPhone 16, MOCK, ad-hoc) | PASS on final code (after the iOS header fix; no Kotlin changed after the suite run) |

### Android: emulator-5554, 360 dp, MOCK, Apple
| Check | Result |
|---|---|
| At a Glance values, ⓘ, 52-week range; P/E tile → sheet → Learn → Back (5D.5.4A closure) | PASS |
| Understand: order Growth, Profitability, Financial Health, Valuation; labels Growing / High margin / More debt than cash / Near History; facts and notes | PASS |
| Row taps: Growth → "What is revenue growth?" + revenue growth Learn; Profitability → Net margin + profit margin Learn; Financial Health → Total debt + debt Learn | PASS |
| Profit margin Learn → "What is profit margin?" → Back to Understand | PASS |
| Valuation card without "Understand P/E Ratio"; See detailed valuation present | PASS |
| Why Did It Move?: lead sentence, rest, 3 sources, Keep in mind, See full breakdown | PASS |
| 1.0× / 1.3× / 2.0×, light and dark (in-app theme, restored to Light) | PASS |
| Section bar pinned and selecting (Overview → Financials while scrolling) | PASS |

### iOS: iPhone 16, MOCK, guest
| Check | Result |
|---|---|
| Glance, Understand rows, Valuation row "P/E 28.0", Why Did It Move? lead (Default light) | PASS |
| Profitability → Net margin sheet → profit margin term → Back | PASS |
| Dark (Default) | PASS |
| AX5 dark: rows wrap, header pill whole (after the fix) | PASS |
| Default: header pill on the title line after the fix | PASS |
| AX XL | NOT RUN for this phase's screens (checked in 5D.5.4A closure before these changes) |

## 8. Known limitations
- **Not supported by the data:** the reference image's "Near historical average" wording and its shortened insight and summary texts. The UI
  keeps the existing texts ("Near History", the full insight, the verbatim summary) and changes only hierarchy and style.
- **Heading:** the Understand heading stays "Understand {Company}". The reference's "Understand This Stock" is already the Guided Research
  card's title in Research further, and using it twice would repeat a heading.
- **Stale and offline:** the movement section has no stale state of its own. Loading hides it, unavailable shows "Try again" (unchanged), and
  sources are hidden when there are none.
- **Untested states:** with the MOCK server unreachable the app served cached data, so the offline state was not reproduced on device (see
  5D.5.4A §8).
- **Screen readers:** TalkBack NOT RUN; VoiceOver BLOCKED (device). **iOS signed-in:** BLOCKED (guest).
- **iOS live Dynamic Type refresh:** existing limit.

## 9. Deviations from the plan
- Every Understand row became tappable (owner reference chevrons). The plan only kept the Valuation row's sheet.
- The interpretation is tinted text, not a pill (owner reference). This also removes the long-badge stacking case from these rows. The shared
  `StockBadgeLayout` rule is unchanged.
- "Why Did It Move?" was not folded behind a disclosure: the lead plus the rest is two to three short sentences, and the caution must stay
  visible.

## 10. Owner decisions
- Approve 5D.5.4B as implemented.
- Keep "Near History" and the full Beginner Insight text, or plan a copy change in core (out of scope for display phases).
- Proceed to 5D.5.4C (Financials and Valuation).

## 11. Reference refinement r2 (2026-10-15, approved; committed locally as "Refine Company Details for Global UI Refinement Phase 5D.5.4B r2 with tiled At a Glance metrics, assessment pills on the title line, Beginner Insight inside Understand and a Why Did It Move section led by the observed move and possible reasons on Android and iOS", not pushed; on top of `d4843f5`)
The owner's second reference ("Phase 5D.5.4B · Company Details — reference screens") asked to follow it closely. Changes, display only:

- **B1 At a Glance:**
  - Each metric is its own borderless surface tile, with ⓘ at the tile's top-right and a larger value (`sectionTitle`).
  - The 52-week range sits on its own surface below.
  - New optional `StockMetricGrid(tiled)` / `StockMetric(tile)` on both platforms, default off.
  - Values, sheets and Learn links are unchanged; one column at large text as before.
- **B2 Understand:**
  - The interpretation is a pill on the title line again (the 5D.5.4A layout; `statusBelowTitle` is no longer used here). It stacks under the
    title only when it doesn't fit.
  - iOS tries the compact pill on the title line before stacking, then falls back to wrapping tinted words at AX5.
  - The shared `StockBadgeLayout` rule is unchanged.
  - **Beginner Insight** moves inside the Understand group as a tinted inset with the lightbulb (verbatim text, small type, "Why does this
    matter?" opens the evidence sheet). At large text the icon goes above the text so no word splits.
  - Rows stay tappable, with chevrons, to their explanation sheets.
- **B3 Why Did It Move?:**
  - A neutral borderless surface replaces the large orange container.
  - The **observed move** comes first: the quote's own "+0.91% (+$3.04)" with its session ("on Oct 7", the header's `ChangeLabel`).
    `CompanyDetailsPresentation.observedMove` returns null when the change is unavailable, never zero. Each figure stays whole: a line breaks
    only between the percent and the amount.
  - Then the summary's first sentence (verbatim).
  - Then **"Possible reasons"** with the rest of the same summary (verbatim; it already says "may have contributed, but timing alone doesn't
    prove it").
  - The source pill, "Keep in mind" and "See full breakdown" are unchanged.
- **Files:**
  - shared: `StockMetric.kt`, `CompanyDetailsPresentation.kt` (+`observedMove`, `plain`), `CompanyDetailsScreen.kt`;
  - test: `CompanyDetailsPresentationTest` (+1);
  - iOS: `StockControls.swift`, `StockDetailComponents.swift`, `CompanyDetailsScreen.swift`.

**Not followed from the reference (data or approval):**
- The Day Range stays with the chart (approved 5D.5.4A).
- The reference's shortened texts ("More revenue than a year earlier.", "Near historical average", the shorter insight) don't exist in the data.
- The reference's "Possible reasons" bullets ("Positive investor sentiment…", "Tech sector strength") aren't in the data; the section shows only
  the existing hedged sentence.
- At 2.0× Android keeps the single-column tiles (the shared large-text rule); the reference shows two columns.

**Verification r2:**
- **Tests:** core 541/0 (JVM, iOS) and server 501/0 before the r2 Kotlin changes. Shared Android 253/0 and iOS 249/0 after B1–B3; the shared
  rerun after the final word-joiner and icon changes is recorded in §11.1. `assembleDebug` and `xcodebuild` PASS on final code.
- **Android** (emulator-5554, 360 dp, MOCK): PASS.
  - Covered: 1.0× light and dark (in-app theme, restored to Light), 1.3×, 2.0×.
  - Taps: Profitability → net margin sheet with Learn; Beginner Insight → evidence sheet; P/E tile → sheet → Learn → Back to At a Glance.
- **iOS** (iPhone 16, MOCK, guest): PASS. Covered: Default light and dark; AX5 (badges stack or wrap, no split words, observed move whole).
- **Not run:** iOS AX XL.
- **Device incident:** the Android emulator's screen capture hung (adb `screencap` timed out even after an in-place reboot). I cold-restarted the
  emulator (same AVD, headless, no snapshot); captures are now guarded by a 15–20 s timeout.

### 11.1 Final shared test rerun
`:app:shared:testAndroidHostTest` PASS 253/0 and `:app:shared:iosSimulatorArm64Test` PASS 249/0 on the final r2 code (after the word-joiner and icon
changes). Devices were restored afterwards (emulator 1.0×, Light theme, `adb reverse` removed; simulator Default size, light); MOCK server stopped.
