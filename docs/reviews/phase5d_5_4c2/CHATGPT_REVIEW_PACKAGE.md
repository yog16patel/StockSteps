# Review package: Phase 5D.5.4C C2 (Valuation hierarchy)

## 1. Phase and Git baseline
- **Phase:** 5D.5.4C, stage C2.
- **Base:** `33fa94c` (C1 Financials, pushed).
- **Under review:** the local commit "Refine Company Details Valuation for Global UI Refinement Phase 5D.5.4C C2 with ratio tiles first, a P/E vs History card, one Learn link, a folded P/E explanation and a neutral ratio footnote on Android and iOS" (not pushed).

## 2. Changes
1. **Key Valuation Metrics:** tiles for P/E, P/S, P/B, EV/EBITDA, PEG and Dividend Yield, showing the model's values. Captions are the first
   sentence of each existing description. Each tile opens the existing explanation sheet.
2. **P/E vs History:**
   - current, average and difference (only with a reliable average);
   - the existing badge and meaning;
   - pills, chart and methodology.
3. **Learn link:** one "Learn more about P/E ratio" link.
4. **Understanding P/E Ratio:** folded.
5. **Other sections:** Valuation & Growth, Range and Insight unchanged.
6. **Footnote:** the neutral footnote (D-V2) above the disclaimer.
7. **Large-text fixes:**
   - iOS badge fallback (regular → compact → wrapping) so the card fits at AX5;
   - "P/E" kept whole in labels (word joiners);
   - iOS trend-chart dates shrink instead of truncating.

## 3. Files
- **shared:** new `ValuationDisplay.kt` + `ValuationDisplayTest` (4 tests); `CompanyValuationScene.kt`.
- **iOS:** `CompanyValuationScene.swift`, `DesignSystem/StockDetailComponents.swift` (trend-chart labels).
- **Docs:** `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4C_C2.md`.

## 4. Decisions
- Display only: no value, average, badge or methodology changes; no new educational text except the approved D-V2 footnote.
- Missing ratios have no tile, "N/A" is never 0, and with no reliable average there is no comparison.
- Global defaults are unchanged. The trend-chart label scaling only applies when labels would truncate.

## 5. Tests
PASS:
- core 541/0 ×2;
- server 501/0;
- shared 262/0 (Android) and 258/0 (iOS) on the final code;
- `assembleDebug` and `xcodebuild` (MOCK).

## 6. Android
Emulator, 360 dp. Sheet: `CHATGPT_UI_REVIEW_ANDROID.png`.
- **PASS:** 1.0×, 1.3×, 2.0× and dark; tile → sheet → Learn → Back; the fold; scroll restoration.
- **Missing data:** the 10Y no-average state is PASS.

## 7. iOS
iPhone 16, guest. Sheet: `CHATGPT_UI_REVIEW_IOS.png`.
- **PASS:** Default, dark and AX XL; tile → sheet → Learn → Back; the fold; scroll restoration.
- **AX5:** PASS after the fixes.

## 8. Known issues
- **Pre-existing (5D.5.4D):** at iOS AX5 the unchanged Valuation & Growth rows break "+75.5%" before the "%".
- **iOS live Dynamic Type limit:** AX checks reopen the page after changing the size.

## 9. Unverified
- The "N/A" and no-dividend states are unit-tested only.
- **Screen readers:** TalkBack NOT RUN; VoiceOver BLOCKED.
- iOS signed-in BLOCKED.

## 10. Deviations
- **Three large-text fixes beyond the plan** (§2.7). Each is local, or applies only when text would otherwise overflow or truncate.
- **P/E** still appears in Valuation & Growth, as the plan keeps that section unchanged.

## 11. Owner decisions
1. Approve C2 for closure and commit.
2. Confirm the iOS wrapping-badge fallback at AX5.
3. Should Valuation & Growth drop its P/E row, now that P/E is the first tile (C3)?
