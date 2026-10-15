# Review package: Phase 5D.5.4B (At a Glance, Understand, Why Did It Move?)

## 1. Phase and Git baseline
- **Phase:** 5D.5.4B.
- **Base:** `789c2a9` (5D.5.4A closed).
- **Under review:** the local commit "Refine Company Details Understand rows and Why Did It Move for Global UI Refinement Phase 5D.5.4B with category, interpretation, one supporting fact and a note per row, an explanation sheet on every row, a bold lead sentence and one P/E comparison on Android and iOS" (not pushed).

## 2. Changes
- **Understand rows:** Category → tinted interpretation → one supporting fact → note, the same on every row.
  - Every row opens its metric's explanation: revenue growth, net margin, total debt, P/E. Each sheet has its Learn link.
  - The Valuation row shows "P/E 28.0"; P/E against the 5-year average stays in the Valuation card.
- **Valuation card:** the duplicate "Understand P/E Ratio" entry is removed.
- **Why Did It Move?:** a bold lead sentence plus the rest of the same summary, verbatim. On iOS the source pill never breaks at AX5.
- **At a Glance:** unchanged; it already matched the reference since 5D.5.4A.
- **Fix:** the `firstSentence` initialism bug ("0.24%." now ends a sentence).

## 3. Files
- **shared:** `CompanyDetailsPresentation.kt`, `CompanyDetailsScreen.kt`, `StockAssessmentRow.kt` (optional `statusBelowTitle`), test
  `CompanyDetailsPresentationTest` (+2).
- **iOS:** `CompanyDetailsScreen.swift`, `DesignSystem/StockDetailComponents.swift`.

## 4. Decisions
- **Display only:** labels, tones, sentences and values come from the existing `AssessmentRow`, `WhyMoving` and `MetricEducation`.
- **Row → sheet mapping** is shared (`assessment().metricId`); `MetricLearnTerms` is unchanged.
- **The shared badge rule is unchanged;** a new optional row mode is used instead.

## 5. Tests
PASS: core 541/0 (JVM, iOS), shared 252/0 Android and 248/0 iOS, server 501/0, `assembleDebug`, `xcodebuild` on final code.

## 6. Android
emulator-5554, 360 dp, 1.0×/1.3×/2.0×, light and dark (in-app theme):
- **PASS:** labels, facts, row sheets and Learn → Back, Valuation card, Why Did It Move?, section bar.
- Sheet: `CHATGPT_UI_REVIEW_ANDROID.png`.

## 7. iOS
iPhone 16, Default and AX5, light and dark, guest:
- **PASS:** the same checks, plus the AX5 header fix.
- **NOT RUN:** AX XL for the changed screens.
- Sheet: `CHATGPT_UI_REVIEW_IOS.png`.

## 8. Known issues
- The reference's shortened texts ("Near historical average", short insight and summary) aren't available from the data; the existing text is
  kept.
- No stale state of its own for Why Did It Move?.
- iOS live Dynamic Type limit.

## 9. Unverified
TalkBack NOT RUN; VoiceOver BLOCKED; iOS signed-in BLOCKED; offline state not reproducible (app cache).

## 10. Deviations
- All rows tappable (reference chevrons).
- Tinted interpretation instead of a pill.
- No disclosure for Why Did It Move?.

## 11. Owner decisions
Approve 5D.5.4B; keep the existing copy; proceed to 5D.5.4C.

## 12. Links
Report `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4B.md`; plan `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4_PLAN.md`; focused diff `CHATGPT_FOCUSED_DIFF.md`.
