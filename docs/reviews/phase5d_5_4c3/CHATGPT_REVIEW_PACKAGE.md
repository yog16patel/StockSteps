# Review package: Phase 5D.5.4C C3 (Financials & Valuation polish)

## 1. Baseline
- **Base:** `e8265bd` (C2, pushed).
- **Under review:** the local commit "Polish Company Details Financials and Valuation for Global UI Refinement Phase 5D.5.4C C3 without the duplicate P/E row, with a measured range grid, row groups that keep values whole and P/E kept whole at large text on Android and iOS" (not pushed).

## 2. Changes
1. **Duplicate P/E removed** from Valuation & Growth. P/E stays in the tile, P/E vs History, the chart and the range card. The section's
   visibility rule is unchanged.
2. **Range stats:** a measured grid — 2 × 2 normally, aligned rows at large text. Android 2.0× split "Averag e".
3. **iOS row groups** (`StockInfoLineGroup`, shared `metricRowStacked` rule):
   - Valuation & Growth: "+75.5%" stays whole;
   - Financials sections stack per section at AX5 ("Operatin g Cash" fixed).
4. **"P/E" kept whole** in Valuation prose, the Learn link and the chart reference (invisible word joiners).
5. **iOS chart labels:** bar-chart period labels and trend-chart dates no longer run together at large sizes.
6. **Borders:** both screens were already borderless, so no outline changes.

## 3. Files
- **shared:** `ValuationDisplay.kt`, `CompanyValuationScene.kt`, `ValuationDisplayTest.kt` (+2 tests).
- **iOS:** `StockDetailComponents.swift`, `LearnViews.swift`, `CompanyValuationScene.swift`, `CompanyFinancialsScene.swift`.

## 4. Tests
PASS:
- core 541/0 ×2;
- server 501/0;
- shared 263/0 (Android) and 259/0 (iOS);
- `assembleDebug` and `xcodebuild`.

## 5. Android
Sheet: `CHATGPT_UI_REVIEW_ANDROID.png`.
- **PASS:** 1.0×, 1.3×, 2.0× and dark; growth sheet → Learn → Back with scroll restored; 3Y switch.
- **NOT RUN in C3:** Financials 2.0× and dark (unchanged; C1 PASS).

## 6. iOS
Sheet: `CHATGPT_UI_REVIEW_IOS.png`.
- **PASS:** Default, dark, AX XL and AX5 on both screens; sheet → Learn → Back.
- **NOT RUN in C3:** Financials dark.

## 7. Open
- **iOS AX5:** the Financials legend wrap and the Company Details Financial Highlights card (5D.5.4D).
- **Screen readers:** TalkBack NOT RUN; VoiceOver BLOCKED.

## 8. Owner decisions
1. Approve C3.
2. Profitability rows that repeat the Financials tiles: kept (owner decision).
