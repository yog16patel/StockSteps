# Phase 8D — accessibility review

## Method (what was and wasn't done)

| Kind | Done | Notes |
|---|---|---|
| Automated checks | unit tests on spoken and scope text (`ScreenerDisplayTest.scopeLabelIsShortAndHonest`, earlier `rowSectorIsSpokenOnlyWhenKnown` and spoken-summary tests) | no UI-automation accessibility scanner in the repo |
| Code inspection | semantics of the changed views (headings, disclosure state, button labels) | below |
| Screenshot inspection | Android 1.0×, 1.5×, 2.0× and dark; iOS Default, AX3, AX5 and dark | `SCREEN_BEFORE_AFTER.md` |
| **Manual TalkBack** | **NOT RUN** | TalkBack focus can't be moved by injected input on the emulator (5D.5.4D finding), and its first start asks for a permission the owner must answer |
| **Manual VoiceOver** | **NOT RUN** | needs manual interaction; no accessibility inspector is available from the command line here |

## Required checks (brief §14)

| Item | TalkBack | VoiceOver | Code / screenshot evidence |
|---|---|---|---|
| Bottom navigation | NOT RUN | NOT RUN | 5D.5.6 tab labels; unchanged |
| Discover search / filter | NOT RUN | NOT RUN | `StockSearchField` labelled; sector pills `StockPillSelector` (selected state); "About this list" uses `StockDisclosure` (announces Expanded / Collapsed) |
| Company Details metric | NOT RUN | NOT RUN | ⓘ buttons labelled per metric (5D.5.3) |
| Portfolio summary | NOT RUN | NOT RUN | one spoken summary (`PortfolioOverviewDisplay.spoken`) |
| Practice simulated label | NOT RUN | NOT RUN | `SimulatedBadge` contentDescription "Simulated with virtual money"; tagline "Learn investing without real money." still visible at AX5 |
| Watchlist add / remove | NOT RUN | NOT RUN | star "Add to watchlist" / "Remove from watchlist" (seen in the Android UI tree) |
| Learn lesson navigation | NOT RUN | NOT RUN | lesson headings; "Next: …" row; research steps' spoken label `ResearchStep.accessibilityLabel` |

## Changed views

| Change | Semantics |
|---|---|
| Discover "About this list" | existing disclosure component (heading row, 48 dp/pt, expanded state). The full description is readable when open (iOS AX5 frame) |
| Practice heading at large text | still a header trait / `heading()`; only the text style changes |
| iOS Practice "Sign in to use Practice" | the button carries the purpose in its label at AX sizes |
| Learn intro hidden at very large text | decorative intro only; the "Start here" heading is unchanged |
| Research step description wrap | more text visible; the spoken label is unchanged |

## Gain/loss, charts, currency

Unchanged since 8A–8C:
- arrows and signs with colour;
- chart summaries (Markets sectors, Company Details, Practice performance);
- currencies spoken as codes ("CAD 1,000.00").
