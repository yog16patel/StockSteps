# Phase 7D — financial accuracy matrix (CAD base; USD/CAD 1.35 in Practice fakes)

| Case | Hand calculation | Test | Result |
|---|---|---|---|
| Single buy | 2 × 110 × 1.35 = 297.00; cash 10,000 − 297 = 9,703.00 | `PracticeEodTest`, emulator test | PASS |
| Multiple buys, weighted average | 3 × 100 × 1.35 = 405.00 + 1 × 120 × 1.35 = 162.00 → cost 567.00, average 141.75 / share | `PracticeAccountingReferenceTest` | PASS |
| Partial sell, realized | 2 × 130 × 1.35 = 351.00 − 2 × 141.75 = 67.50; cost left 283.50; cash 9,784.00 | same | PASS |
| Unrealized, market value | 2 × 125 × 1.35 = 337.50 − 283.50 = 54.00 | same | PASS |
| Fractional, rounding | 0.5 × 101 × 1.35 = 68.175 → 68.18 (cents, half-up); exit 2.5 × 125 × 1.35 = 421.875 → 421.88 | same | PASS |
| Full sell | realized 67.50 + (421.88 − 351.68) = 137.70; cash 10,137.70 = start + realized | same | PASS |
| Restart replay | a new service replays to the same cash and realized | same + emulator | PASS |
| Emulator round trip | buy 2 @ 110, sell 1 @ 120, sell 1 @ 120 → 9,703.00 / 9,865.00 (realized 13.50) / 10,027.00 | emulator | PASS |
| My Portfolio value | 2 × 120 USD × 1.40 = 336.00; basis 2 × 100 × 1.30 = 260.00; unrealized 76.00; allocation 100 % | `PortfolioEodTest` | PASS |
| My Portfolio (emulator) | 2 × 110 × 1.35 = 297.00; basis 270.00 | emulator | PASS |
| Missing close | total, unrealized and allocations unavailable; cost basis kept | `PortfolioEodTest` | PASS |
| Missing FX | totals unavailable (`FX:` in `missing`), never 1.0 | existing `PortfolioEngine` tests + code (`convert` returns null) | PASS (code + existing tests) |
| Zero / missing cost basis | `percent` returns null for a zero whole (no division by zero) | code (`PracticeEngine.percent`) + existing engine tests | PASS (code) |
| Insufficient cash/shares, invalid quantity | blockers | `PracticeEodTest`, emulator | PASS |

Precision:
- money and quantities are exact `Decimal` strings;
- Practice cash rounds per trade to 2 places;
- Portfolio values keep 8 places in conversion and display at 2.

No financial definition was changed.
