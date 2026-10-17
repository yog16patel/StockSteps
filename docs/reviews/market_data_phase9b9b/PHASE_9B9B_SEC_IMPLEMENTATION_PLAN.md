# Phase 9B.9B — SEC EDGAR integration plan (plan only; nothing implemented)

Baseline: the 9B SEC adapter (`marketdata/sec/`) and `FinancialMetricsEngine` exist, fixture-tested (29 tests), **not wired** and
**never run live** (no approved `SEC_USER_AGENT`). FMP stays the default; SEC stays opt-in until validated.

## What exists (audit)
| Area | State in code | Gap |
|---|---|---|
| Ticker ↔ CIK | `company_tickers_exchange.json`, `BRK.B` = `BRK-B`, ambiguous → null, 24 h cache | none for US common stock; funds/ADRs not covered |
| Company facts | 25 us-gaap/dei tags, 10-K/10-Q(/A) only, bounded 48 MB, single flight, 403/429 cooldowns, 404 = none | IFRS / 20-F filers unsupported (by design) |
| Periods | by start/end dates; FY 350–380 d, quarter 84–98 d; fiscal labels from the period's own filing | — |
| Q4 | flows only: FY − 9-month YTD (same tag), marked derived | — |
| **Q2/Q3 from YTD** | **missing**: 10-Q cash flows are usually YTD only, so Q2/Q3 cash flow is absent | derive Q2 = 6M − 3M and Q3 = 9M − 6M (flows only) |
| Restatements | latest filed wins, original kept, `/A` flagged, same-filing duplicates → AMBIGUOUS | **mixed split bases**: later comparatives may be post-split while older periods stay as reported |
| EPS | `EarningsPerShareDiluted` / `Basic`, weighted diluted shares | per-share Q4 never derived (correct); TTM EPS needs four reported quarters or the FY |
| Shares | `dei:EntityCommonStockSharesOutstanding`; several classes → AMBIGUOUS | multi-class companies (GOOGL, BRK) have no single share count |
| Metrics | engine: growth, margins, ROE/ROA, D/E, current ratio, FCF, P/E, P/S, yield, split guard | P/B not implemented |
| Provenance | provider `sec`, accession, filed date, as-of = latest period end | not yet in the `CompanyFundamentals` contract (warnings/basis only) |

## Plan (ordered; each step with its tests)
1. **Live validation first (approval D-9B9B-1).** One `SEC_USER_AGENT` ("StockSteps <contact e-mail>"), ≤ 20 requests (ticker file +
   companyfacts + submissions for AAPL, MSFT, JPM, AMZN, RIVN and one REIT or insurer). An opt-in command like the Alpha Vantage smoke
   test: report fields present, periods, amendments, ambiguous facts, no bodies stored in Git.
2. **CIK/ticker mapping:** keep the SEC file; add `submissions.tickers` cross-check; record delisted/renamed tickers as "no current listing".
3. **Normalization:** extend `SecPeriodBuilder` with Q2/Q3 derivation from consecutive YTD facts (same tag, same fiscal year, start dates
   equal), marked derived; per-field like Alpha Vantage; never derive per-share values or share counts.
4. **Diluted EPS and shares:** quarterly EPS only as reported; TTM EPS = FY when newest, else four reported quarters, else derived Q4
   (FY − Q1 − Q2 − Q3, flagged approximate — existing engine rule). Weighted diluted shares carried for split detection.
5. **Fiscal periods, amendments, duplicates:** keep latest-filed default; add a **split-basis normalizer**:
   - detect splits from `us-gaap:StockholdersEquityNoteStockSplitConversionRatio1` when filed, else from share-count jumps;
   - express every per-share value on the latest basis, or leave the period out when the split date isn't known;
   - never mix bases in one series.
6. **YTD → standalone:** the step 3 derivation plus the D-9B9A-6 reconciliation check (quarters must add up to the year for the 3 recent
   complete years) before any SEC quarter is trusted.
7. **P/E, P/S, P/B, market cap:**
   - only with a licensed, dated price on a compatible basis (raw current close; per-share values on the current share basis) and a
     single-class share count;
   - P/B = price × shares ÷ equity (new engine metric, equity ≤ 0 → not meaningful);
   - market cap = price × cover shares, as-of the cover date.

   **Depends on price licensing (FMP DDLA or an Alpha Vantage agreement)** — SEC has no prices.
8. **Provenance and freshness:** add optional, additive `CompanyFundamentals.source` and per-fact `basis.date`/filing reference
   (D-9B9B-4); freshness from the latest filing date (`submissions`); cache 24 h, invalidated when a new 10-Q/10-K accession appears.
9. **User-Agent, rate limits, caching, retries:**
   - `sec` lane at 5/s (≤ 10/s published), 2 concurrent;
   - 24 h companyfacts cache, 6 h submissions;
   - 403 → 1 h and 429 → 10 min cooldowns; no retries; bounded bodies;
   - one instance (budgets are per instance).
10. **Provider selection** (`FUNDAMENTALS_PRIMARY_PROVIDER=sec`, opt-in):
    - SEC for reported statements, EPS and shares;
    - prices from the configured price provider;
    - Alpha Vantage/FMP only as explicitly configured sources;
    - **no runtime fallback**: an SEC refusal or a missing fact is shown as unavailable, never answered by another provider.

    Where AV/FMP and SEC differ, the comparison report shows both with the reason; the screens use one provider per dataset.

## Can start now (official SEC data only) vs blocked
| Now (after D-9B9B-1 User-Agent) | Blocked |
|---|---|
| live validation run; Q2/Q3 YTD derivation; split-basis normalizer; per-field quarter trust; reported statements, diluted EPS, weighted shares, growth, margins, ROE/ROA, D/E, current ratio, FCF in the fundamentals bundle (`FUNDAMENTALS_PRIMARY_PROVIDER=sec`); provenance fields | P/E, P/S, P/B, market cap, dividend yield shown to users: need a licensed price (D-9B9B-3); commercial display of any non-SEC input; multi-class share counts |

Never: combine SEC with incompatible periods, currencies or split bases; fabricate values; fall back silently between providers.
