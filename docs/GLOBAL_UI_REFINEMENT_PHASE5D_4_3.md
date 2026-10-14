# Global UI Refinement Phase 5D.4.3 — Earnings QA and stabilization

Date: 2026-10-14. Status: **committed and pushed** as "Stabilize Global UI Refinement Phase 5D.4.3 Earnings with one Reported status in the header, a full-width company name at large text, tighter section bottoms and a fading session-chip row on Android and iOS" (on top of `93aac9c`). Plan: `GLOBAL_UI_REFINEMENT_PHASE5D_4_3_PLAN.md`.

Owner approval: "continue with your recommendation":
- 5D43-1: Q-01, Q-02 and Q-03 approved;
- 5D43-2: simulator restart (done: shutdown + boot, no erase);
- 5D43-3: header actions stay full-width and stacked at large text;
- 5D43-4: shared row-mode emphasis left unchanged.

Display only. No core, server, data, calculation, currency, classification, auth, reminder, navigation, entitlement or AI change.

## Changes (Android and iOS)
| ID | Before → After |
|---|---|
| Q-01 | The "Reported" badge **and** the "Reported · Wed, Jul 22 · …" line → only the line; the badge row keeps "Sample". Other statuses keep their badge (`EarningsDisplay.statusBadgeShown`, tested for every status) |
| Q-02 | Logo column beside a wrapping name at every size → at large text (Compose `fontScale ≥ 1.3`, iOS `≥ .xxxLarge`) the name uses the full width and the logo moves onto the ticker line. Normal sizes unchanged |
| Q-03 | 16 dp bottom padding under a 48 dp toggle row → 8 dp (`EarningsSectionPadding` / `.earningsSection()`) on the takeaway, Revenue/EPS cards, Details reaction, Results EPS/Revenue and Results reaction |
| New (found in QA, P2) | The Results reaction chips (First Session / 3 / 5 Sessions) were a plain horizontal scroll: at 2.0× "3 Sessions" was cut at the edge with no cue. They now use the shared `StockFadingScrollRow` (the CLAUDE.md rule), on both platforms |

## Verification
| Check | Result |
|---|---|
| shared Android / iOS tests | **PASS** 245/0 / 241/0 (+1 status-badge test) |
| core tests | NOT RUN (core not touched) |
| `assembleDebug`, `xcodebuild` (iPhone 16) | **PASS** |
| Android 360 dp light 1.0× / 1.3× / 2.0× Details | **PASS** (`after-a360-details-top*.png`): one status in words, full-width name at 2.0× (~3 lines shorter), tighter sections |
| Android 2.0× last content above the Sample strip and bottom bar | **PASS** (`after-a360-details-bottom-2.0x.png`) |
| Android dark 2.0× Results reaction | **PASS**, captured before the fading-row fix (`after-a360-results-reaction-dark-2.0x.png`, which shows the cut chip that the fix addresses). The fix itself is build-verified only |
| Android dark 1.0× | PASS in 5D.4.2 (same tokens); not re-captured |
| **iOS Results**: light, dark, live AX XL dark and light | **PASS** (`after-ios-results-*.png`), including a live Dynamic Type change |
| iOS input | **Root cause found:** earlier "taps not registering" were caused by passing image-pixel coordinates instead of device points. With points, every tap worked. The restart was not the fix |
| iOS Earnings Details | **BLOCKED**: needs a signed-in session on the local Auth emulator (the owner signs in) |
| TalkBack / VoiceOver | NOT VERIFIED |

Out of scope, noted: the iOS **Home** screen after a live Dynamic Type change shows mixed sizes, and "StockSte/ps" breaks mid-word
(`note-ios-home-live-dynamic-type-out-of-scope.png`). This predates this phase.

**Recommendation: PASS WITH CONDITIONS** (iOS Details after the owner signs in; the Android fading chip row re-captured on device).
