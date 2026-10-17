# Phase 8C — accessibility checklist

| Check | Result | Evidence |
|---|---|---|
| Spoken overview summary | unchanged (`PortfolioOverviewDisplay.spoken`, "Total value unavailable" when missing) | code |
| Order of reading | Total → today → cost and P/L → valuation date → history: the reading order now follows priority | code; Android frames |
| Gain/loss without colour | unchanged: arrows and signs ("↑ +CAD 200.00", "Gain +$224.43", "Loss −$2.23") | frames |
| Practice rows spoken | unchanged (`PracticeFormat.holdingDescription`, activity row "… Simulated.") | code |
| Touch targets | unchanged (borderless cards keep their click area and padding) | code |
| Android 2.0× | PASS: overview metrics stack (`rowsWhenNarrow`), Practice segmented tabs scroll, Watchlist header wraps between parts | `after/android/font2-*` |
| Dark mode | PASS: borderless Practice cards keep their surface contrast | `after/android/dark-*` |
| iOS AX5 | guest Practice only (PASS); signed-in content BLOCKED | `after/ios/ax5-practice` |
| TalkBack / VoiceOver | NOT RUN (owner pass) | — |
