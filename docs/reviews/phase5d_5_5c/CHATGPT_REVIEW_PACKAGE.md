# Review package: Phase 5D.5.5C (Company Search experience)

## 1. Baseline
- **Base:** `08723a6` (5D.5.5B, pushed).
- **Under review:** the local commit "Refine Company Search for Global UI Refinement Phase 5D.5.5C with a cleaner Android search header, local recent searches, search states with Retry and the shared result rows in the Comparison picker on Android and iOS" (not pushed).

## 2. Summary
1. **Android header:** "Search stocks" top bar plus one borderless field. The "StockSteps" headline is removed. iOS keeps the native sheet.
2. **Recent searches:** local only (SharedPreferences / UserDefaults).
   - Selected companies only, newest first, max 10, deduplicated per listing.
   - Survives restart, Clear all, guest-friendly.
3. **States:** recent, hint, Searching…, results, "No companies found" + "Try a different search", "Couldn't load search results" + Retry.
   - Stale responses can't overwrite newer queries.
4. **Comparison picker:** full-screen "Add to comparison" on the shared row and states.
   - "Already added" rows are disabled.
   - The add/replace flow is unchanged and never opens Company Details.
5. **No changes:** ranking, API, provider or scope.

## 3. Tests
PASS:
- core JVM and iOS 554/0;
- server 502/0;
- shared Android 264/0 and iOS 260/0;
- `assembleDebug` and `xcodebuild`.

## 4. Device QA
Android 360 dp (1.0×, 1.3×, 2.0×, light and dark) and iOS iPhone 16 (Default, dark, AX XL, AX5): the scenarios pass as listed in report §4.
- **Not run:** iOS Clear all, iOS rapid typing, loading captures.
- **Screen readers:** not run.

## 5. Known issues
- The Android error capture shows the old "Try again" label.
- The Android picker's status-bar scrim is grey.
- iOS "Try a different search" doesn't re-raise the keyboard.
- The iOS AX XL badge text is large.

## 6. Owner decisions
1. Approve 5D.5.5C.
2. Accept the deviations from the reference: initials instead of logos, and no typing suggestions (as decided in D5/D6).

## 7. Screenshots
`CHATGPT_UI_REVIEW_ANDROID.png`, `CHATGPT_UI_REVIEW_IOS.png`; raw captures in `captures/` (git-ignored).
