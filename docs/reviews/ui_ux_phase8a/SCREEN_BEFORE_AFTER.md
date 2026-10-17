# Phase 8A — screens before and after

All captures are real device frames against a **local MOCK** server (labelled Sample in the app; the "Sample data · mock backend" strip
is visible in every frame). Nothing is from REAL.

## Environment

- **MOCK server:** a private copy of the current server (`./gradlew :server:distZip`, unzipped in the session scratchpad) on `:8087`
  (`STOCKSTEPS_DATA_MODE=mock`). The existing `:8081` and `:8083` servers answer 500 for `markets/overview` (jars rebuilt under them, as
  recorded in Phase 5) and were left untouched.
- **Android:** emulator-5554, Pixel_9_Pro AVD (1280×2856, 480 dpi, 412 dp wide), started by this session (no emulator was running).
  Debug builds with `-PstockstepsMockBackendUrl=http://127.0.0.1:8087` + `adb reverse tcp:8087`. The AVD's stored session is signed in
  (no portfolio account). Dark = the app's own appearance preference (the device night mode doesn't apply while the app is set to
  Light); 2.0× = `font_scale 2.0`. Restored to Light and 1.0× afterwards. The physical phone was not used.
- **iOS:** iPhone 16 simulator (iOS 18.3), guest mode, builds with `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8087`. Dark and
  Dynamic Type via `simctl ui` (live); restored to Light / Large. Taps in device points.

## Frames (originals in `captures/`)

| Scenario | Android BEFORE | Android AFTER | iOS BEFORE | iOS AFTER |
|---|---|---|---|---|
| Home top, light, default | `before/android/light-home1` | `after/android/light-home1` | `before/ios/light-home1` | `after/ios/light-home1` |
| Home scrolled | `light-home2..4` | `light-home2..4` | `light-home2` | `light-home2` |
| Markets top → bottom, light | `light-markets1..6` | `light-markets1..6` | `light-markets1..4` | `light-markets1..4` |
| Dark | `dark-home1..4`, `dark-markets1..6` | same | `dark-home1`, `dark-markets1..2` | same |
| Large text | 2.0×: `font2-home1..4`, `font2-markets1..6` | same | AX5: `ax5-markets1..3` | AX5: `ax5-home1`, `ax5-markets0..3`, `ax5-movers` |

Contact sheets: `CHATGPT_UI_REVIEW_ANDROID.png`, `CHATGPT_UI_REVIEW_IOS.png`.

## What changed on screen

| Area | Before | After | Status |
|---|---|---|---|
| Markets status | Bordered card: "US Market" heading, dot + "After-hours", next open, "Sample quotes · Not live" | Open block: dot + "US Market · After-hours" + Sample badge, next open, same footer | PASS (both, light/dark) |
| Markets sections | 7 bordered cards, 12 apart | Borderless groups on the page surface, 20 apart; section titles on the page | PASS (both) |
| Daily Brief entry | Separate card | Last rows of the Major indices group | PASS (both) |
| Sector bars | All from the left edge (~35 % of the row) | Diverging around a centre line: gains right (green), losses left (red); values and captions unchanged | PASS (both) |
| Top Movers at AX5 (iOS) | Symbol and name one letter per line | Price and change under the name; symbol + 2-line name readable | PASS (iOS AX5) |
| Home header | Greeting + "What matters to you today" | Greeting only | PASS (both) |
| Home portfolio summary (owned account) | Raw notice codes, metrics without currency | Portfolio overview wording (unit-tested) | NOT RUN on device (no MOCK account with holdings reachable: Android account has no portfolio, iOS is guest) |
| EOD / proxy / Sample labels | "As of 4:00 PM ET · Oct 7", "Close · Oct 7", XIC.TO proxy text, CAD 310.94, "Sample" | identical text | PASS (both) |

Not captured: loading skeletons, error states, REAL (`Last close · Oct 16` labels — MOCK index data carries quote times, so the EOD
label path is covered by Phase 7B core tests, unchanged here), tablets, small phones (≤ 360 dp / iPhone SE) — NOT RUN.
