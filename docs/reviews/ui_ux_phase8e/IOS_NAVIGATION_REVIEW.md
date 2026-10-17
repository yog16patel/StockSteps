# Phase 8E — iOS navigation review

**Device:** iPhone 16 simulator (iOS 18.3), guest, local MOCK through the counting proxy. Build: current tree. There are **no iOS code
changes** in 8E.

| Check | Result | Evidence |
|---|---|---|
| Tab switching | Instant (native `TabView`) | `captures/ios-nav.mp4`, `ios-overview.png` |
| Push (Markets → company from Top Movers, and from Search) | Native slide; the page settles correctly | `ios-8e-company-settled.png`, `ios-8e-apple-from-search.png`, `ios-push.png` |
| Pop (back button) | Native; Markets landing without a duplicate title | `ios-8e-markets-after-back.png`, `ios-8e-markets-after-search-back.png` |
| Interactive edge swipe-back | Returns to Markets | `ios-8e-after-swipe.png` |
| Modal (Search sheet) | Native sheet; dismissal before the push | `ios-nav.mp4` |
| Reduce Motion | NOT RUN: no app navigation animations exist to adapt (native transitions follow the system setting); the simulator accessibility setting wasn't changed | — |
| VoiceOver focus | NOT RUN (manual, owner) | — |

**Observations under heavy simulator lag:** the search sheet took about 12 s to open in one run. Neither of the following reproduced in
steady state, in three clean runs:
- once, a transient frame where the pushed company page drew under the status bar during the sheet dismissal plus push;
- once, a small inline "Markets" title over the large landing title after an edge swipe started on the root.

Both are recorded as P2 in `KNOWN_LIMITATIONS.md`. No iOS change was made.
