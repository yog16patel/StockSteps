# Phase 7D — remaining launch blockers (REAL Portfolio / Practice)

| # | Blocker | Owner action |
|---|---|---|
| 1 | **FMP display and redistribution licence**: individual plans don't allow display to app users (Phase 6 Q4) | an FMP Data Display and Licensing quote, or another licensed EOD vendor |
| 2 | **FMP quota**: the key answers 429; tier and daily limit unconfirmed (Q1); `PROVIDER_FMP_DAILY_LIMIT` unset | confirm the tier; set the daily limit |
| 3 | **Corporate actions**: REAL Practice doesn't apply splits or dividends. A split would understate value and returns; today there is only a warning | **proposed launch gate**: until a licensed split source exists, keep Practice labelled as not adjusting splits (current notice), or block trading in a symbol whose last two closes show a split-like move |
| 4 | **EOD adjustment basis and publication delay** unverified (Q6) | FMP documentation or support |
| 5 | **Mixed FX dates** in My Portfolio current value (latest close vs the latest FX) | accept and document, or align FX to the close date (accounting decision) |
| 6 | **Device verification** (signed-in REAL on Android and iOS) | the owner's test account pass |
| 7 | **Copy D1**: the 401 says "watchlists and alerts" on Portfolio and Practice | approve "Sign in to continue." |
| 8 | **"Today's change"** unavailable on closing prices | a "Last session" line in a UI phase (optional) |
