# Phase 8E — Navigation regression

The navigation model, routes, arguments and back stacks are unchanged. 8E only records a motion value in `TabNavigator` and chooses
transitions from it.

| # | Flow | Android | iOS |
|---|---|---|---|
| 1 | Home → Markets → Portfolio → Watchlist → Learn | PASS (instant; 0 requests) | PASS (native) |
| 2 | Markets → Discover → (company) → Back | PASS: Discover → Back. The company row inside Discover needs a preset, so it wasn't reached; Company Details push/Back was covered via Search (#3) | Covered by #3 |
| 3 | Search → Company Details → Back | PASS | PASS |
| 4 | Watchlist → Company Details → Back | NOT RUN in 8E (empty test watchlist); the same `open`/`back` path as #3 | NOT RUN |
| 5 | Learn → lesson → Back | PASS (term → topic → root) | NOT RUN in 8E |
| 6 | Portfolio → My Portfolio ↔ Practice | PASS (instant, in place) | PASS (unchanged, native) |
| 7 | Practice → Holdings → Overview | NOT RUN (no signed-in Practice holdings on MOCK); inner tabs are presenter state, untouched | NOT RUN |
| 8 | Company Details internal tabs | Unchanged (scroll only, no NavHost transition) | Unchanged |
| 9 | Filter sheet | NOT RUN in 8E (MODAL_IN/OUT fade covers modal routes) | Native |
| 10 | Settings open/close | PASS (fade; same landing on close) | PASS (8.x captures; native sheet) |
| 11 | Rapid repeated navigation | PASS (15 fast tab taps end on the last tab) | NOT RUN |
| 12 | Multiple Back presses | PASS (Apple → Home → app closes at the Home root, the existing rule) | PASS (rapid swipes, 5D.5.6D) |
| 13 | Background → foreground | PASS | NOT RUN in 8E |
| 14 | Process recreation | NOT RUN in 8E (`am kill` didn't end the process); unchanged path, verified in 5D.5.6D | — |
| 15 | Guest | — | PASS (iOS session was guest) |
| 16 | Signed in | PASS (cached test session) | BLOCKED (no approved account) |

**Unit tests:**
- core `NavigationMotionTest` (4): tabs instant; forward and Back mirrored; modals fade even when they close themselves; durations bounded;
- existing `TabNavigationModelTest` / `AppScreensTest` (destinations, Back, no duplicates) re-run unchanged.
