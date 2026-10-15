# Review package: Phase 5D.5.4D (Company Details accessibility & final verification)

## 1. Baseline
- **Base:** `ebe81f4` (C3, pushed).
- **Under review:** the local commit "Finish Company Details accessibility for Global UI Refinement Phase 5D.5.4D with section header actions under long titles, a stacking Financials chart legend and whole Financial Highlights and Key Ratios values at large text on iOS, and the final verification record" (not pushed).

## 2. Fixes (iOS only)
1. **D1 legend:** the Financials chart legend goes one entry per line when both don't fit whole (AX5). The text is not shrunk.
2. **D2a section header:** the action moves under the title only when the title's longest word and the action can't share a line
   ("Financia l Highligh ts" fixed).
3. **D2b rows:** Financial Highlights and Key Ratios use `StockInfoLineGroup`, so "$416.2B" and "↑ +6.4%" stay whole.

## 3. Files
`DesignSystem/StockComponents.swift`, `DesignSystem/StockDetailComponents.swift`, `CompanyDetailsScreen.swift`.

## 4. Tests
PASS:
- core 541/0 ×2;
- server 501/0;
- shared 263/0 (Android) and 259/0 (iOS);
- `assembleDebug` and `xcodebuild`.

## 5. Android
Emulator, 360 dp, MOCK. Sheet: `CHATGPT_UI_REVIEW_ANDROID.png`.
- **PASS:** 1.0×, 1.3×, 2.0× and dark; Quarterly; 10Y missing average.
- **NOT RUN:** guest mode (the emulator is signed in).

## 6. iOS
iPhone 16, MOCK, guest. Sheet: `CHATGPT_UI_REVIEW_IOS.png`.
- **PASS:** Default, dark, AX XL and AX5. Before/after for the legend.
- **No "before" capture** for the Highlights card; the defect is described in the C1 report §5.

## 7. Accessibility
- **Code-level audit: PASS** — tabs announce selection, headings, chart summaries, decorative ⓘ, sheet dismissal.
- **Screen readers:** TalkBack and VoiceOver BLOCKED — awaiting approval. Manual steps are in the report §7.

## 8. Owner decisions
1. Approve 5D.5.4D.
2. Approve a TalkBack session.

## 9. Final accessibility QA (no code change)
- **Section tabs:** Android 2.0× PASS; iOS AX5 PASS (reachable, selected and underlined).
- **Reading order:** Android PASS (tree: Highlights, Key Ratios, chart summary). iOS PASS at code level.
- **TalkBack:** BLOCKED. It starts without a prompt, but injected input can't move its focus, so the walk needs a person.
- **VoiceOver:** BLOCKED (Simulator).
- **Android guest:** BLOCKED (would sign out the owner's session).
- Details: report §9.
