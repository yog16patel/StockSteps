# Owner decisions — Phase 9B.9A

| # | Decision | Recommendation |
|---|---|---|
| D-9B9A-1 | Is the development use personal under ToS §2? | **Revised 2026-10-17 by the owner: a free personal key is used for owner-only local testing.** Caveat recorded: the owner earlier described the work as company-associated, and ToS §2 (ii) treats use on behalf of a corporation as commercial — anything beyond the owner's own local testing (testers, beta, launch) needs the written agreement; draft questions kept in `ALPHA_VANTAGE_SALES_QUESTIONS.md` (not sent) |
| D-9B9A-2 | Set the key and run the bounded smoke test | owner runs it locally with the key in their own shell (never in chat, Git or docs); ≤ 10 requests |
| D-9B9A-3 | Keep the dev instance private (loopback / adb reverse / simulator; no LAN exposure) | yes |
| D-9B9A-4 | Keep FMP as the default until the smoke results are reviewed | yes |
| D-9B9A-5 | Approve the 9B.9A code for commit after review | review first |

Smoke-test command — run by the owner in their own terminal with the key already exported (≤ 10 requests, no retries, no key in output):
```bash
ALPHA_VANTAGE_SMOKE_CONFIRM=run-bounded-free-tier-smoke-test ALPHA_VANTAGE_TERMS_CONFIRMED=personal-non-commercial ALPHA_VANTAGE_SMOKE_SYMBOL=AAPL ./gradlew :server:alphaVantageSmokeTest
```
(`ALPHA_VANTAGE_API_KEY` must already be in that shell's environment; add `ALPHA_VANTAGE_SMOKE_OPTIONAL=true` for EARNINGS and SPLITS.)
The report is printed and written to `server/build/alphavantage-smoke.md`.
