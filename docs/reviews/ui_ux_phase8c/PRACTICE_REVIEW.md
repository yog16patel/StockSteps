# Phase 8C — Practice Portfolio review

| Brief item | Result |
|---|---|
| Clearly simulated | PASS (unchanged): "Practice Portfolio SIMULATED", "Learn investing without real money.", "Simulated with virtual money. No real orders are placed." footer, "· Simulated" on every activity row |
| Overview (cash, value, holdings, gain/loss, history) | PASS: same values; sections now borderless (summary, Available cash, Open holdings, insights, holdings, allocation, activity rows, challenges) |
| Repetition | Improved: the empty-state text no longer repeats "$10,000.00 in virtual cash" (shown under Available cash; the engine's insight sentence is unchanged) |
| Buy/sell, preview, review, result | Not changed: `PracticeOrderScreen` / iOS order views keep their cards and copy (EOD basis and date, 1 % review tolerance, confirmed-result-only success). Only the iOS result's "Explore Stocks" button uses `.stockSecondary` |
| History | PASS (unchanged data): "Practice buy · AAPL, 5 shares at US$303.42, Aug 3, 2026 · 20:00 UTC · Simulated", from server records |
| Execution semantics | untouched: freshness rules, reviewed-price tolerance, server execution, atomic transaction, idempotency key kept on unknown outcome (7D), oversell and cash checks, user isolation |
| Guest | PASS: "Sign in to use your Practice Portfolio…" (iOS guest frames) |

Trade preview / result frames: NOT RUN in this pass. The order flow is unchanged, so its existing 7C/7D evidence stands.
