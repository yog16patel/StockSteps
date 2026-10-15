# Review package: Phase 5D.5.5B (Company Search ranking and result rows)

## 1. Baseline
- **Base:** `0c5c4c4`.
- **Under review:** the local commit "Add Company Search ranking and result rows for Global UI Refinement Phase 5D.5.5B with deterministic server-side relevance, a conservative Best match label and borderless search result rows on Android and iOS, plus the 5D.5.5A search audit and plan" (not pushed). Plan approved with D1–D8 as recommended.

## 2. Changes
1. **Ranking:** deterministic `SearchRanking` (core), applied on the server over both provider endpoints.
   - Tiers: exact ticker → exact name → ticker prefix → name word/prefix → later word → substring → provider.
   - Within a tier: major exchange before OTC, then provider order.
   - Nothing is dropped.
2. **Best match:** only for a unique exact ticker or name (no label for share classes such as GOOG/GOOGL). Shown as "Best match" and
   "Other results" sections.
3. **Result row:** borderless, initials avatar, name, ticker · exchange, chevron; stacks at large text.
4. **D1 copy fixes:** iOS "USD or CAD" text and chip; Comparison "RY.TO" hint.

## 3. Tests
- **PASS:** core JVM and iOS 549/0; server 502/0; shared Android 263/0 and iOS 259/0; `assembleDebug`; `xcodebuild`.

## 4. Android
Emulator, 360 dp, local MOCK. **PASS:** 1.0×, 1.3×, 2.0× and dark; row → Company Details; spoken labels. Sheet: `CHATGPT_UI_REVIEW_ANDROID.png`.

## 5. iOS
iPhone 16, local MOCK, guest. **PASS:** Default, dark, AX XL and AX5; GOOG with no label; corrected empty-state copy; row → Company Details.
Sheet: `CHATGPT_UI_REVIEW_IOS.png`.

## 6. Deviations
- The "Best match" badge is blue (INFO), not green: green means a gain in StockSteps.
- The header and states are unchanged until 5D.5.5C.

## 7. Owner decisions
1. Approve 5D.5.5B.
2. Then 5D.5.5C: header, states, recent searches, Comparison.
