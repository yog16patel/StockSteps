# Phase 8C — screens before and after

All frames are real captures against **local MOCK `:8087`** (a private server copy). The Sample strip and badges are visible.

**Android:**
- Device: emulator-5554 (Pixel_9_Pro, 412 dp).
- Session: signed in to the MOCK user-data store, not production.
- Builds: BEFORE = the 8B build; AFTER = the final 8C build. Both use `-PstockstepsMockBackendUrl=http://127.0.0.1:8087`.
- My Portfolio frames use the read-only sample scenarios One CAD, Missing FX and Multiple accounts. Each scenario switch is verified on
  screen ("Scenario: …"); frames from a failed switch were deleted.
- Practice uses the MOCK scenario `one-holding`.
- Dark uses the app's own appearance preference; 2.0× uses `font_scale`.
- The AFTER Practice and Watchlist frames came from an 8C build made before the G1 revert. G1 only touches allocation, which doesn't
  appear on those screens. The AFTER My Portfolio scenario frames came from the final build.

**iOS:** iPhone 16, guest. Signed-in iOS is **BLOCKED**.

## Originals (`captures/`)

| Screen | Android BEFORE (`before/android/`) | Android AFTER (`after/android/`) | iOS (`before/ios/`, `after/ios/`) |
|---|---|---|---|
| Portfolio empty / guest | `{light,dark,font2}-portfolio-empty` | same | `light-portfolio` |
| My Portfolio summary, holdings, allocation, transactions | `{light,dark,font2}-one-cad-1..5` | same | BLOCKED |
| Missing price/FX valuation | `{light,dark,font2}-missing-fx-1..5` | same | BLOCKED |
| Multiple accounts | `{light,dark,font2}-multiple-accounts-1..5` | same | BLOCKED |
| Practice overview | `{light,dark,font2}-practice-1..4`, `-practice-one-1..2` | same | guest `light-practice` (+ AFTER `dark-practice`, `ax5-practice`) |
| Practice holdings / activity (history) | `{light,dark,font2}-practice-holdings`, `-practice-holdings2`, `-practice-activity` | same | BLOCKED |
| Watchlist | `{light,dark,font2}-watchlist-1..2`, `light-watchlist-filled1` | same | guest `light-watchlist` |

**Not captured:**
- the trade preview, review and result (order flow unchanged): NOT RUN;
- the Practice empty state after the copy change (the MOCK Practice account stayed on the `one-holding` scenario): NOT RUN, verified in
  code;
- transaction entry forms (unchanged): NOT RUN.

## What changed on screen

| Area | Before | After | Status |
|---|---|---|---|
| My Portfolio overview | total → today → **Account value history** → Invested / Unrealized / Realized | total → today → **Invested / Unrealized / Realized → valuation date** → history | PASS (Android light, dark and 2.0×) |
| Missing valuation | "CAD —" and the notice in words | same values; the notice is now above the history | PASS |
| Practice overview and holdings | bordered cards (summary, cash, holdings, insights, allocation, rows) | borderless surfaces | PASS (Android) |
| Watchlist header | "Updated 4:48 AM ET" | "Updated Oct 8, 5:12 AM ET" | PASS (Android) |
| iOS guest Portfolio, Practice, Watchlist | — | unchanged | PASS |

Contact sheets: `CHATGPT_UI_REVIEW_ANDROID.png`, `CHATGPT_UI_REVIEW_IOS.png`. Downscaled copies are in `docs/design/ui_ux_phase8c/`.
