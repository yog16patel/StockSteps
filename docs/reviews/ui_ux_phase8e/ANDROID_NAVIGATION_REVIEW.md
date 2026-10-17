# Phase 8E — Android navigation review

**Device:** emulator-5554
- Pixel_9_Pro AVD, 1280×2856, 60 Hz, Android 17 image;
- debug build on local MOCK (own server on :8093 behind a counting proxy on :8092; other sessions' servers untouched);
- cached signed-in test session (no credentials typed).

The host was heavily loaded during QA (load average 6–8, with a second emulator, a second simulator and several servers), so the emulator
renders slowly: median frame about 40–50 ms in every run. Absolute timings are therefore unreliable; comparisons below use the same device
in the same session.

## Root cause of the awkward transitions (evidence)
1. **The library default runs on every destination.** `fadeIn(tween(700))` and `fadeOut(tween(700))` run at the same time, so for about
   half a second both pages are drawn semi-transparently over each other. This includes **bottom-tab switches**, because a tab switch is a
   graph navigate.
   - Evidence: `captures/before-tab-switch-double-exposure.png` (Home and Markets blended at t=1.3 s) and `captures/bt-strip.png`.
2. **The top bar is outside the NavHost.** The Scaffold's `topBar` follows the current destination immediately, while the NavHost's
   transition only starts once the new page has composed. On a push, "← Discover" appears over the old, shifted Markets page; on Back, the
   bar disappears over the old page before Markets returns.
   - Evidence: `captures/before-push-topbar-over-old-page.png`, `captures/bf-strip.png`, `captures/bb-strip.png`.
3. **There is no direction:** forward and Back are the same cross-fade.

Not causes:
- the bottom bar is never recreated;
- no screen animates twice;
- back-stack state is kept;
- no loading flash is triggered by navigation.

These were measured, not assumed.

## Fix
One central transition policy (see `MOTION_POLICY.md`):
- tabs and sections are instant;
- a push is a 220 ms fade-through with a 24 dp slide; Back mirrors it;
- modals fade in 150 ms.

There was no library migration; no route or argument changed.

## Verification (AFTER build)
| Flow | Result | Evidence |
|---|---|---|
| Home → Markets → Portfolio → Watchlist → Learn → Home | Instant switch in one frame (t=1.7 s Home, t=1.8 s Markets), no overlap; bottom bar static | `captures/at-strip.png`, `after-tabs.mp4` |
| Markets → Discover → Back | Short fade-through; the old page is gone after 70 ms (no long double exposure) | `captures/af-strip.png`, `after-forward.mp4` |
| Home → Search → Apple → Back → Home | Search modal fades; Apple opens under Markets; Back returns to Home | `after-forward.mp4`, dumps |
| Learn → Profit → term → Back → Back | term → topic → Learn root | UI dumps |
| Portfolio → Practice → My Portfolio | In place, no transition (sections) | UI dumps |
| Settings open → Back | Fades; returns to the same landing | UI dumps |
| Rapid tab taps (15 taps, 250 ms apart) | Ends on the last tapped tab; no stuck or duplicate destination | UI dump |
| Apple → Back → Back | Apple → Home → the app closes at the Home root (standard) | UI dumps |
| Background → foreground | Same screen | UI dump |
| Animator scale 0.5× | Push and Back work | UI dumps |
| Animator scale Off | Push and Back with no fades or slides | `captures/off-strip.png`, `after-off.mp4` |
| Process recreation | NOT RUN in 8E: `am kill` didn't end the process on this image. Unchanged code path; verified in 5D.5.6D | — |

## Frame timing (dumpsys gfxinfo; emulator, same session, warm)
| Flow | Build | Frames | Janky | p50 | p90 | p99 |
|---|---|---|---|---|---|---|
| Tab round (5 taps) | BEFORE | 130 | 95.4 % | 48 ms | 101 ms | 150 ms |
| Tab round (5 taps) | AFTER | 146 | 92.5 % | 38 ms | 61 ms | 150 ms |
| Forward/back flow | BEFORE | 174 | 97.1 % | 53 ms | 109 ms | 550 ms |
| Forward/back flow | AFTER | 126 | 93.7 % | 48 ms | 113 ms | 200 ms |

On this loaded emulator almost every frame misses 16 ms either way: the jank percentages describe the emulator, not the app. The AFTER build
renders fewer frames for the same flow (shorter animations) and has a lower worst case. Physical-device validation is NOT RUN: the attached
phone is the owner's personal device with its own setup.

## Remaining Android limitation
The top bar still changes before the new page's first frame. With animations Off the same stall shows (`off-strip.png`: "← Discover" over
Markets for one or two frames), which confirms it is composition time, not motion. Back to a large landing such as Markets can take several
hundred ms on this loaded emulator.

Fixing it needs the pushed screens' top bars inside each route (so they move with their page), a larger change outside 8E. See
`KNOWN_LIMITATIONS.md`.
