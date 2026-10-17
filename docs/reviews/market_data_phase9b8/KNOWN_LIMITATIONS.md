# Known limitations — Phase 9B.8

1. **No Alpha Vantage key, no SEC User-Agent:** REAL fundamentals, live field names and the six-company parity comparison are BLOCKED
   (credentials). Everything is fixture-tested only.
2. **EPS-based metrics unavailable** from Alpha Vantage (P/E, EPS, EPS growth, PEG, forward P/E) until SEC diluted EPS is validated or
   Alpha Vantage confirms its EPS definitions.
3. **No verified share basis:** P/S, P/B, P/FCF, EV, EV/EBITDA, shares unavailable.
4. **Different conventions from FMP** (period vs TTM margins, ROE/ROA averaging, debt definition) — values change when switching; the
   switch is opt-in and documented.
5. Quarterly cash flow and income are used only when verified standalone per company; companies without a complete fiscal year of
   quarters show quarterly cash flow as unavailable.
6. Dividend yield needs a split-free trailing year and SPLITS success; a split in the window → unavailable.
7. Discover fundamentals warm-up with Alpha Vantage costs ≈ 5 requests per company (keep it at 0 on a personal plan).
8. Legacy snapshot/indices (FMP) and news/earnings calendar (Finnhub) unchanged (out of scope).
9. Budgets and caches per instance; bundle cache 15 min, statements 24 h.
10. Alpha Vantage remains personal-licence data: nobody but the allowlisted owner may see it; no testers.
