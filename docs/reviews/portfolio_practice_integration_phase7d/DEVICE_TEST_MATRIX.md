# Phase 7D — device test matrix

| Scenario | Android (physical, REAL) | iOS simulator (REAL) |
|---|---|---|
| Sign in | BLOCKED: owner test account (Claude doesn't enter credentials) | BLOCKED: same |
| My Portfolio: create / retrieve a holding, dated "Closing prices as of …" label | BLOCKED | BLOCKED |
| Practice: preview (basis "Simulated trade using the last available closing price · date"), buy, sell | BLOCKED; also, live closes are unavailable while FMP answers 429 | BLOCKED |
| Navigate away and back; restart; sign in again; persisted state | BLOCKED | BLOCKED |
| Unavailable-price message ("Unavailable", `QUOTE_MISSING` / `QUOTE_RESTRICTED`) | BLOCKED | BLOCKED |
| Guest: Portfolio and Practice ask to sign in | NOT RUN on device; server 401 verified on REAL `:8082` with curl | NOT RUN |
| Builds | PASS (`assembleDebug`) | PASS (`xcodebuild`) |

To run it: the owner signs in with the approved test account on the Pixel 7 (REAL build) and the iPhone 16 simulator, after the FMP limit
resets. The checklist rows above are the script.
