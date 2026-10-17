# Phase 7E — known limitations

1. **Licence:** displaying FMP data in the app needs a Data Display and Licensing Agreement (Phase 6 Q4). This remains a launch gate.
2. **Fundamental filters and sorts** (P/E, yield, margins, growth) depend on the fundamentals warm-up. It is off on LAN REAL
   (`SCREENER_FUNDAMENTALS_PER_HOUR=0`), and statements are partly refused on this plan. These filters report low coverage; nothing is
   estimated.
3. **Market cap:** the FMP `profile.marketCap` field isn't verified on this plan. If it's absent, market-cap filters and sorts show the
   companies as missing (excluded and counted).
4. **Volume and 52-week position** need quotes and aren't available for the curated list.
5. **Coverage builds over hours:** ≈ 2.6 h at 60 companies an hour on a cold start. Each instance enriches separately (per-instance
   caches).
6. **Quota:** steady state ≈ 310 requests a day per instance, too much for a 250-per-day key alongside other features. Tune
   `SCREENER_ENRICHMENT_PER_HOUR`.
7. **The list is editorial:** it isn't the market or an index. It's reviewed quarterly. WMT's exchange is left to the provider. There
   are no stable identifiers (CIK/FIGI).
8. **The new search field** hasn't been seen on a device.
9. **A 429 without the provider guard (tests):** it retries every ~30 min per company. With the guard (REAL), the first 429 opens the
   circuit and the rest are refused locally.
