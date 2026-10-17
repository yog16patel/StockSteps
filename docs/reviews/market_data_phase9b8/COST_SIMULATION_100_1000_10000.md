# Cost simulation 100 / 1,000 / 10,000 DAU — Phase 9B.8 (Alpha Vantage prices + fundamentals)

Verified quotas only (published): free 25/day; premium 75 / 150 / 300 / 600 / 1,200 per minute, no daily cap. Assumptions labelled, as in
9B/9B.7: distinct companies viewed per day D = 150 / 600 / 2,000; search miss 70 / 50 / 30 %, ≈ 4 prefixes per search.

Formula: per distinct company per day = daily 1 + quote 1 + income/balance/cash flow 3 + dividends 1 + splits 1 (+ OVERVIEW 1 premium)
= **7–8**; search = DAU × 1.5 × 4 × miss; fixed ≈ 220 (movers, Markets, Discover cap); Discover fundamentals warm-up 0 (recommended).

| | 100 | 1,000 | 10,000 |
|---|---:|---:|---:|
| Company data (8 × D) | 1,200 | 4,800 | 16,000 |
| Search | 420 | 3,000 | 18,000 |
| Fixed | 220 | 220 | 220 |
| **Alpha Vantage / day (base)** | **≈ 1,850** | **≈ 8,000** | **≈ 34,000** |
| busiest-hour rate (10 % in one hour) | ≈ 3/min | ≈ 13/min | ≈ 57/min |
| FMP after (fundamentals moved) | quarterly EPS only (≈ 1 × D) | | |
| Finnhub | ≈ 500 | ≈ 2,500 | ≈ 8,000 |

Fits a 75/min plan to ≈ 1,000 DAU with room; 10,000 DAU needs 150/min (bursts). Free tier: one Company Details open = 7–8 requests —
unusable. The commercial licence (unpriced, Q13) is the cost driver, not volume. Per instance only.
