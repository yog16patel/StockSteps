# Phase 8D — screens before and after

All frames are real captures on **local MOCK `:8087`**, with the app's "Sample data · mock backend" strip visible.

**Android:**
- Device: emulator-5554 (Pixel 9 Pro, 412 dp), signed in to the MOCK user-data store.
- Builds: BEFORE = 8C final (`823d247`); AFTER = 8D. Both use `-PstockstepsMockBackendUrl=http://127.0.0.1:8087`.
- Same scripts for both: `acap8d-learn.sh` and `acap8b.sh`.

**iOS:**
- Device: iPhone 16 (iOS 18.3), guest.
- Builds: BEFORE = 8C; AFTER = 8D. Both use `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8087`.
- Appearance and Dynamic Type set live with `simctl ui`.

## Originals (`captures/`)

| Set | Files |
|---|---|
| Android Learn BEFORE/AFTER | `{before,after}/android/{light,dark,fs2.0}-learn-1..2`, `-topics-1..2`, `-topic-valuation(-2)`, `-term-1..2`, `-research-1..2` |
| Android Discover BEFORE/AFTER | `{before,after}/android/{light,dark,fs2.0}-discover1..2`, `-results1..2`, `-search-*`, `-details1..3` |
| Android 1.5× sweep (8C build) | `sweep-1.5/{markets,discover,portfolio,learn}/fs1.5-*` |
| iOS BEFORE | `before/ios/{light,dark,ax3,ax5}-discover`, `{light,dark,ax3,ax5}-practice`, `{light,dark,ax5}-learn` |
| iOS AFTER | the same names in `after/ios/`, plus `ax5-discover-about` (disclosure opened) |

Contact sheets: `CHATGPT_UI_REVIEW_ANDROID.png`, `CHATGPT_UI_REVIEW_IOS.png`. Downscaled frames are in `docs/design/ui_ux_phase8d/`.

## Results

| Screen | Before | After | Status |
|---|---|---|---|
| iOS Discover AX5 | the title, purpose and 6 lines of scope fill the screen; search below | short scope label + "About this list"; search and sectors on the first screen | PASS |
| iOS Discover Default / AX3 / dark | full scope caption | short label + disclosure | PASS |
| iOS Practice AX5 (guest) | Sign in below the fold | section-heading title, SIMULATED, tagline, "Sign in to use Practice" visible | PASS |
| iOS Practice Default / AX3 / dark | sentence, then a Sign in link | short line → Sign in button → reason | PASS |
| iOS Learn AX5 | intro fills the screen | "Start here" + Start learning visible | PASS |
| Android Learn 2.0× | intro, then Start here | Start here first | PASS |
| Android research steps 2.0× | descriptions cut ("…stock p…") | full descriptions | PASS |
| Android Discover 1.0× / dark / 2.0× | full scope caption | short label + disclosure; search higher | PASS |
| Android 1.5× sweep | — | no issues found | PASS (no change needed) |

**Not run:**
- signed-in iOS (BLOCKED);
- loading and error states;
- Android AFTER at 1.5× (no 8D change there);
- Settings and login screens.
