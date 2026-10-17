# Phase 7E — data coverage and freshness

| Field | Source (endpoint) | Phase 6 status on this plan | Cache | Freshness shown |
|---|---|---|---|---|
| Name, sector, industry, exchange, country | curated file → FMP `profile` | VERIFIED WORKING (21/21) | `profiles` 24 h (shared with Company Details); failure cooldowns 402/403 1 h, 429 10 min | — |
| Market cap | FMP `profile` `marketCap` | the field isn't verified on this plan (no probe) | as above | "approximate, from the company profile" (row metric basis) |
| Price, daily change | FMP `historical-price-eod/light` | VERIFIED WORKING (3/3 + fixture capture) | `price-chart`: until the next expected close (Phase 7B), 30-min late retry | "Latest close" with the date; page warning "official closing prices (latest close …)" |
| P/E, yield, margins… | fundamentals bundle | statements partly refused (402) | existing 24 h / 6 h / 15 min | existing coverage line |
| Screener, batch quote, S&P 500 constituents | — | VERIFIED RESTRICTED | — | **not called** |

## Enrichment pass (`CuratedScreenerUniverse`)

- **Never on the request path:** a Discover open returns at once with what's loaded. Missing values stay null.
- **Background pass:** LOW provider priority, at most `SCREENER_ENRICHMENT_PER_HOUR` companies an hour (default 60), 4 at a time.
- **A company is due when:**
  - it has never been loaded;
  - a newer close is expected (re-checked at most every 30 min);
  - its profile is older than 24 h;
  - for a failed company, only after 30 min.
- **Cost:**
  - cold start ≈ 2 requests per company (profile + close series): ≈ 120 per hour for ≈ 2.6 h at the default;
  - steady state ≈ 155 profile + ≈ 155 close requests a day per instance, shared with Company Details, Markets and Portfolio.
- **Quota:** on a 250-per-day key (FMP Basic, if that's the tier) this alone would use most of the day. Lower
  `SCREENER_ENRICHMENT_PER_HOUR`, or confirm the tier (Q1).
- **Instances:** caches are per instance, so each Cloud Run instance enriches separately.
