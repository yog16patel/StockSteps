# Phase 8E — Motion before and after

All videos are in `captures/` (git-ignored; originals from `adb screenrecord` and `simctl io recordVideo`). Frame strips were extracted from
them at fixed intervals (AVFoundation, zero tolerance).

| Flow | BEFORE | AFTER |
|---|---|---|
| Bottom-tab switch | `before-tabs.mp4`, `bt-strip.png`: ~0.5 s double exposure; the tab bar switches first | `after-tabs.mp4`, `at-strip.png`: one-frame switch, no overlap |
| Push Markets → Discover | `before-forward.mp4`, `bf-strip.png`: new top bar over the old page, then a long overlap | `after-forward.mp4`, `af-strip.png`: old page out in 70 ms, new page fades and slides in |
| Back Discover → Markets | `bb-strip.png`, `before-back1/2.mp4`: stall, then a long overlap | `after-back3.mp4`, `after-off.mp4`: no overlap; the composition stall remains (see limitations) |
| Animations Off | — | `after-off.mp4`, `off-strip.png`: instant push and Back |
| Dark mode | NOT RUN as video (the transitions use alpha and offset only, so they are theme-independent) | NOT RUN |
| iOS native | `ios-nav.mp4`, `ios-overview.png`, `ios-push.png` | unchanged |

Key stills:
- `before-tab-switch-double-exposure.png` — Home and Markets blended during a tab switch;
- `before-push-topbar-over-old-page.png` — "← Discover" over the old Markets page.
