# Phase 8B — screens before and after

All frames are real captures against **local MOCK `:8087`** (a private copy of the server; the app's "Sample data · mock backend" strip
and Sample badges are visible). Nothing is from REAL.

- **Android:** emulator-5554 (Pixel_9_Pro, 412 dp, 480 dpi), stored session signed in.
  - Builds: BEFORE = the 8A build (`852afb9`), AFTER = 8B; both with `-PstockstepsMockBackendUrl=http://127.0.0.1:8087`.
  - Dark uses the app's own appearance preference; 2.0× uses `font_scale`.
  - Scripted with `acap8b.sh`: the same taps for every pass.
- **iOS:** iPhone 16 (iOS 18.3), guest, builds with `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8087`. Dark mode and Dynamic Type were
  set live with `simctl ui`.

## Originals (`captures/`)

| Screen | Android BEFORE (`before/android/`) | Android AFTER (`after/android/`) | iOS BEFORE (`before/ios/`) | iOS AFTER (`after/ios/`) |
|---|---|---|---|---|
| Discover landing | `{light,dark,font2}-discover1..2` | same names | `light-discover1` | `light-discover1` |
| Discover results (Growing Companies) | `{light,dark,font2}-results1..2` | same names | `light-discover-results1` | `light-results1` |
| Sector tap (Basic Materials) | — | `light-sector-selected`, `light-cleared` | — | `light-sector-selected`, `dark-sector-selected` |
| Discover at large text | `font2-*` | `font2-*` | NOT RUN | `ax5-results-top` |
| Search | `{light,dark,font2}-search-empty`, `-search-apple` | same names | `light-search-apple` | — (unchanged code) |
| Company Details | `{light,dark,font2}-details1..3` | same names | `light-details1..2`, `dark-details2`, `ax5-details2` | — (unchanged code) |

iOS BEFORE Discover in dark and at AX5 is **NOT RUN**: the Markets tab had returned to its root before those captures, so the frames
showed Markets and were deleted.

Contact sheets: `CHATGPT_UI_REVIEW_ANDROID.png`, `CHATGPT_UI_REVIEW_IOS.png`. Downscaled copies are in `docs/design/ui_ux_phase8b/`.

## What changed on screen

| Area | Before | After | Status |
|---|---|---|---|
| Discover scope | not shown before results | "160 companies · MOCK screener universe: … (not real data)" under the subtitle | PASS (both) |
| Discover search field | Material outlined field (Android); label repeated as placeholder (iOS) | design-system search field with icon (both) | PASS (both) |
| Sector filter | Edit filters → sheet → Sector | pills: All sectors · Basic Materials · … ; tap = 1 filter, highlighted | PASS (both; Android and iOS taps verified) |
| Clear all | only when nothing matched | beside "N filters"; returns to all 160 | PASS (Android tap verified; iOS rendered) |
| Sort label | "Sort: Market cap ↓" | "Sort: Market cap (high to low)" | PASS (both) |
| Results header | explanation + link + notes always open | warnings visible; the rest in "About these results" | PASS (both) |
| Row sector | — | "Technology" caption under the name | PASS (both) |
| Legacy movers line | "Unavailable% · Price Unavailable" | "Change unavailable · Price unavailable" / "+1.23% · $12.34" | unit tests only (the screens aren't reachable) |
| Search, Company Details | — | unchanged | verified, no regression in the AFTER Android frames |
