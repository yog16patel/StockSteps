# Phase 8D — consolidated UI audit

**Baseline:**
- Base `823d247` (8C, committed and pushed). Working tree clean.

**Evidence and method:** real frames on local MOCK `:8087`.
- **Android:** emulator-5554 (412 dp), signed in to the MOCK user-data store, at 1.0, 1.5 and 2.0× and in dark.
- **iOS:** iPhone 16 guest, at Default, AX3 and AX5 and in dark.
- **Also consulted:** the 8A–8C packages, and the Pixel 7 REAL check of 2026-10-17.
- **Method:** screenshot inspection and code inspection. No screen reader was used (`ACCESSIBILITY_REVIEW.md`).

## Per-screen summary

| Screen | Goal / primary action | Findings | Pri | 8D action |
|---|---|---|---|---|
| Login / onboarding | sign in, continue as guest | Not re-audited: auth screens are out of scope, and credentials can't be entered | — | NOT RUN |
| Home | today's markets, my companies | 8A open sections; 1.5× and 2.0× readable; guest and signed in | — | none |
| Markets | indices, movers, sectors | 8A; 1.5×: rows stack, sparklines kept, no clipping | — | none |
| Discover | find companies | **iOS AX5: the title, purpose and ~6 lines of scope pushed search and sectors below the first screen** (8B limitation) | P1 | fixed (D8D-1) |
| Search | find a ticker or name | readable at 1.5×, 2.0× and AX5; provider-error copy issue on REAL (backlog A) | P1 (REAL) | proposed session, not here |
| Company Details | understand a company | 5D.5.4D large-text work holds at 1.5× and 2.0×; REAL "AAPL / AAPL" header with no profile (backlog A) | P2 | backlog |
| My Portfolio | value and gains | 8C order; 1.5× and 2.0× fine; signed-in iOS BLOCKED | — | none |
| Practice | simulated investing | **iOS AX5 guest: title, badge, tagline and explanation filled the screen; Sign in below the fold** (8C limitation) | P1 | fixed (D8D-2) |
| Watchlist | follow companies | readable at all sizes | — | none |
| Learn | where to start | **iOS AX5: the intro sentence filled the first screen ("Start here" below)**; **research step descriptions cut at 2 lines at 2.0×/AX** | P1 / P2 | fixed |
| Settings | preferences | not re-audited (unchanged since 5D.5.6) | — | NOT RUN |

**Not seen on any tested screen:**
- horizontal overflow, overlapping elements or unreachable controls;
- clipping, other than the research-step truncation above (now fixed);
- in dark mode: low contrast or invisible elements.

## Findings and dispositions

| # | Pri | Finding | Disposition |
|---|---|---|---|
| X1 | P1 | iOS Discover AX5: search below the fold | Fixed: short scope label ("160 companies · not the whole market") stays; the server's description is under "About this list" (both platforms) |
| X2 | P1 | iOS Practice AX5 (guest): Sign in below the fold | Fixed: the section title uses the section-heading role inside the Portfolio landing at AX sizes; "Sign in to use Practice" comes right after the tagline, with the explanation below. Android uses the same heading rule at ≥ 1.3× |
| X3 | P1 | Learn AX5: intro sentence fills the first screen | Fixed: the intro is left out at AX sizes (iOS) and ≥ 1.75× (Android); "Start here" and Start learning are visible |
| X4 | P2 | Guided-research step descriptions truncated at large text | Fixed: they wrap in full |
| X5 | P1 | Search error copy on REAL | Proposed session (8B follow-up) |
| X6 | P2 | Company Details REAL header without profile | Backlog |

No P0 found.
