# Phase 9B.9A — REAL free-tier validation report (in progress, 2026-10-17)

**Status: smoke test (8 requests) and the one-request INCOME_STATEMENT diagnostic run by the owner (2026-10-17, Mac, key passed only to
the Gradle run); results below; awaiting review. Authenticated Alpha Vantage requests: 10 of 25 today (all from the owner's runs; 0 from
this session).** No secret was printed,
nothing was changed on the host, nothing deployed.

## 1. Git baseline
`main` = `origin/main` = `1ada8f5` (9B.9A, committed and pushed). Clean tree before this report.

## 2. Ubuntu key loading (read-only checks; names and set/unset only)
| Check | Result |
|---|---|
| `~/stocksteps-real/secrets.env` | present, mode **600** |
| `ALPHA_VANTAGE_API_KEY` in the file | **set (non-empty)** — value not read or printed |
| Compose loads it | yes: `docker-compose.real.yml` `env_file: ./secrets.env` (required) |
| Running container `stocksteps-real-api-1` | image **`ddb6dfb898e3`** (Phase 7E), started 2026-10-17T03:15Z — **before** the key was added and **without any Alpha Vantage code** |
| `ALPHA_VANTAGE_*` inside the running container | **unset** (the container predates the file change) |
| Java on the host | no (the smoke test needs a JVM: a newer image or another machine) |

## 3. Entitlement and budget (values of the non-secret settings in `secrets.env`)
| Setting | Value | Assessment |
|---|---|---|
| `ALPHA_VANTAGE_ENTITLEMENT` | not set | **FREE** by default (code ≥ `1ada8f5`) ✓ |
| `ALPHA_VANTAGE_DAILY_BUDGET` | 20 | ✓ |
| `ALPHA_VANTAGE_ENABLED` | true | — |
| `ALPHA_VANTAGE_LICENSE_MODE` | personal | ✓ (personal gate) |
| `ALPHA_VANTAGE_PERSONAL_UIDS` / `_CLIENTS` | not set | with Alpha Vantage in use, the gate would **refuse every caller** |

## 4. FMP default and Discover — conflict found, **fixed by the owner** (re-checked read-only 2026-10-17)

Re-check after the owner's edit: `MARKET_DATA_PRIMARY_PROVIDER=fmp`, `FUNDAMENTALS_PRIMARY_PROVIDER=fmp`, `SCREENER_ENRICHMENT_PER_DAY=0`,
`ALPHA_VANTAGE_ENTITLEMENT=free`, `ALPHA_VANTAGE_DAILY_BUDGET=20`, `ALPHA_VANTAGE_LICENSE_MODE=personal`, `ALPHA_VANTAGE_ENABLED=true`; each
defined once; `secrets.env` mode 600; key set (value not read). The running container is still image `ddb6dfb` (no Alpha Vantage code);
nothing was redeployed. Original finding, kept for the record:
| Setting | Value | Required by the brief |
|---|---|---|
| `MARKET_DATA_PRIMARY_PROVIDER` | **alpha_vantage** | FMP default → should be `fmp` or removed |
| `FUNDAMENTALS_PRIMARY_PROVIDER` | **alpha_vantage** | FMP default → should be `fmp` or removed |
| `SCREENER_ENRICHMENT_PER_DAY` | not set (code default 100 with FMP; forced 0 while AV free is in use) | disable bulk enrichment → `0` |
| `SCREENER_FUNDAMENTALS_PER_HOUR` | 0 (compose) | ✓ |

Today this has **no effect** (the running image has no Alpha Vantage code). But a redeploy of `1ada8f5` with this file would make Alpha
Vantage the primary source for prices, quotes, search, movers and fundamentals on the LAN server, and with no allowlist every
market-data route would answer 502 `PROVIDER_RESTRICTED`. I did **not** edit the file (no configuration changes).

## 5. Licensing scope — answered 2026-10-17
Owner's answers:
- **Licence:** this smoke test is personal, non-commercial use by the owner as an individual, on their own free key.
- **Approval:** one bounded run for AAPL, at most 8 requests.
- **Who runs it:** the owner, on the Mac, with the key in their own shell. This session never handles the key.
- **Config:** the owner sets `MARKET_DATA_PRIMARY_PROVIDER` and `FUNDAMENTALS_PRIMARY_PROVIDER` to `fmp` (or removes them) and adds
  `SCREENER_ENRICHMENT_PER_DAY=0` before any redeploy.

Recorded caveat: the work was earlier described as company-associated; ToS §2 (ii) makes use on behalf of a corporation commercial.

## 6. Smoke test run and quota

- **Run:** `./gradlew :server:alphaVantageSmokeTest`, AAPL, `ALPHA_VANTAGE_SMOKE_MAX_REQUESTS=8`, optional functions on. BUILD SUCCESSFUL
  in 1 min 48 s. 8 of a budget of 8 sent; not stopped early; no refusals.
- **Earlier attempts:** two runs exited with "ALPHA_VANTAGE_API_KEY is not set". The opt-in guard refuses before any request, so they sent
  **0** requests (the key hadn't loaded into that shell).
- **Quota:** 8 of Alpha Vantage's 25 free requests used today; **17** left at Alpha Vantage. The run used its own counter, separate from
  the server's 20/day budget (the server wasn't involved). Spacing was 15 s; no `Information`/`Note`/`Error Message` refusal was returned.

## 7. Endpoint results on FREE (AAPL)

| Function | Result | Dates | Fields and checks | Missing data |
|---|---|---|---|---|
| TIME_SERIES_DAILY | **works** | last bar 2026-10-16 = `Last Refreshed` (Friday's close, the expected session) | 100 bars (`Compact`, as documented for free), zone `US/Eastern`, every close > 0, no skipped rows, not adjusted (as-traded) | full history is premium |
| GLOBAL_QUOTE | **works** | latest trading day 2026-10-16 (end of day) | symbol matches; price > 0; previous close and change % present | none |
| SYMBOL_SEARCH | **works** | — | 4 matches incl. the exact US listing; other currencies CAD, BRL, INR | the foreign listings are dropped by StockSteps' US-only filter, as intended |
| INCOME_STATEMENT | **works** | latest annual 2025-09-30 (FY2025; FY2026 not yet reported) | 20 annual, 81 quarterly, USD only, `totalRevenue` present in every period, no skipped rows; **quarters UNVERIFIED** (see 7.1) | — |
| BALANCE_SHEET | **works** | latest annual 2025-09-30 | 20 annual, 81 quarterly, USD, `totalAssets` in every period | — |
| CASH_FLOW | **works** | latest annual 2025-09-30 | 20 annual, 81 quarterly, USD, `operatingCashflow` in every period; **quarters STANDALONE** (quarters add up to each complete fiscal year) | — |
| EARNINGS | **works** | — | 31 annual, 122 quarterly, `reportedEPS` in every quarter | EPS basis (GAAP vs adjusted, diluted vs basic) still undocumented → still not used |
| SPLITS | **works** | latest 2020-08-31 | 4 splits (the 2020 4-for-1 among them) | — |

Alpha Vantage records Apple's fiscal year ends (actually the last Saturday of September) as **2025-09-30**, i.e. normalised to the month
end. The parsers and quarter labels expect exactly that (the nearest-month rule).

Not exercised in this run: OVERVIEW (premium; refused locally on FREE), DIVIDENDS, TOP_GAINERS_LOSERS, and any HTTP-200 refusal path.

### 7.1 What "quarters UNVERIFIED" means for income

`quarterBasis` checks, for every fiscal year that has an annual value and four quarters ending in it, whether the quarters add up to the
year (within 1 %). If they do, the year counts as **standalone**. If only the last quarter equals the year, the statement is
**year-to-date**. **Any other disagreement makes the whole statement UNVERIFIED.**
- Cash flow passed every complete year → STANDALONE.
- Income failed the check for at least one of its 20 fiscal years. AAPL quarterly revenue is normally standalone, so the likely cause is a
  restated or inconsistent older year in the provider's history, not a year-to-date series. Which year, and by how much, can't be seen in
  the report: the smoke test prints only the verdict, and responses aren't stored.

What it implies today (9B.8 rules):
- **Fundamentals bundle** (`FUNDAMENTALS_PRIMARY_PROVIDER=alpha_vantage`): quarterly income values are **left out** for AAPL, so:
  - quarterly revenue and earnings growth and margins are unavailable;
  - **TTM from quarters is unavailable**: the engine needs four verified standalone quarters, and quarters newer than FY2025 exist without
    income values;
  - annual metrics are unaffected (FY2025 vs FY2024 growth, margins, ROE/ROA).
  - Quarterly cash-flow metrics remain available (STANDALONE).
- **Inconsistency found:** `statementHistory` (Comparison history and research) uses `AlphaVantageStatements.merge`, which does **not**
  apply the standalone check. It would still show AAPL's quarterly income rows. The same figures are refused in one feature and shown in
  another.

Options (owner decision D-9B9A-6):
1. Apply the same standalone rule in `statementHistory` (consistent; quarterly income disappears from Comparison for such companies).
2. Check only the most recent N fiscal years (e.g. 3), so a single inconsistent old year doesn't refuse all quarters. Recent quarters
   still need verification.
3. Add a diagnostic line to the smoke report (which year failed and the quarter-sum/annual ratio), then re-run INCOME_STATEMENT only:
   1 request.

Recommendation: 3, then 1 + 2. **Approved by the owner and implemented — see §9.**

## 8. Comparison with the fixtures and parsers

| Aspect | Fixture (`AvFixtures`) and parser expectation | REAL response | Verdict |
|---|---|---|---|
| Daily series keys | `Meta Data` 1.–5., `Time Series (Daily)` with `1. open`…`5. volume` | parsed: 100 bars, 0 skipped, zone and output size read | **matches** (also matched the documentation sample) |
| Quote keys | `Global Quote` `01.`–`10.` | symbol, latest trading day, price, previous close, change % all read | **matches** (first live confirmation) |
| Search keys | `bestMatches` `1. symbol` … `9. matchScore`, region and currency | 4 matches; region and currency read | **matches** |
| Statement shape | `annualReports` / `quarterlyReports`, `fiscalDateEnding`, `reportedCurrency`, `"None"` → null | 20 / 81 reports, no skipped rows, USD only | **matches** for the checked fields (`totalRevenue`, `totalAssets`, `operatingCashflow`) |
| Other statement fields | `grossProfit`, `operatingIncome`, `netIncome`, `shortLongTermDebtTotal`, `capitalExpenditures`, `dividendPayout`… | not inspected by the smoke checks | **UNVERIFIED** (add to the next diagnostic run) |
| Fiscal dates | month-end period ends, nearest-month quarter labels | 2025-09-30 for a 52/53-week year | **matches** |
| Quarterly basis | fixtures have standalone income and cash flow; YTD fixture refused | cash flow STANDALONE; income UNVERIFIED | rule works as designed; income needs the diagnostic (7.1) |
| Earnings keys | `annualEarnings`, `quarterlyEarnings`, `reportedEPS` | 31 / 122, no missing EPS | **matches**; definition still unverified |
| Splits keys | `data[]` `effective_date`, `split_factor` | 4 splits, latest 2020-08-31 | **matches** |
| Refusals in HTTP 200 | classified (rate limit / premium / key / Error Message) | none occurred | not exercised live |

**Conclusion:** the free tier serves every dataset StockSteps uses on FREE. The parsers read the real responses without skipped rows, and
the documented field names for daily, quote, search, statements (checked fields), earnings and splits are confirmed for AAPL.

Open items before Alpha Vantage replaces FMP for anything:
- the income quarter-basis question (7.1);
- the uninspected statement fields;
- the EPS definition;
- licensing beyond the owner's personal testing.

FMP stays the default.

## 9. D-9B9A-6 implemented (committed and pushed; fixture-tested; 0 authenticated requests from this session)

1. **Diagnostic.** For INCOME_STATEMENT and CASH_FLOW the smoke report now adds:
   - a per-fiscal-year table: fiscal year end, quarters found, quarter sum ÷ annual value, verdict;
   - the recent years checked and the trusted-quarter count;
   - presence counts for every mapped field (`grossProfit`, `operatingIncome`, `netIncome`, `costOfRevenue`, `ebit`, `interestExpense`;
     balance and cash-flow fields).

   `ALPHA_VANTAGE_SMOKE_FUNCTIONS` selects a subset, so INCOME_STATEMENT alone costs 1 request (command below).
2. **Comparison applies the same check.** `AlphaVantageStatements.merge` (used by `statementHistory`, i.e. Comparison history and
   research) shows quarterly income and cash-flow values only for trusted quarters. Untrusted quarters keep their period and balance
   values with those fields null. Annual rows are unchanged, and the fundamentals bundle uses the same rule.
3. **Only trust recent verified years.** `quarterTrust` takes its verdict from the **3 most recent complete fiscal years**:
   - all standalone → STANDALONE; any year-to-date → YEAR_TO_DATE; otherwise UNVERIFIED.
   - With STANDALONE, the trusted quarters are those of years that pass themselves, plus the fiscal year in progress. An old inconsistent
     year therefore no longer refuses recent quarters, but its own quarters stay untrusted.
   - A failing recent year still refuses all quarters.

Tests (`QuarterTrustTest`, 5):
- an old bad year doesn't refuse recent quarters, but its own quarters stay untrusted;
- a bad recent year or a year-to-date series refuses all quarters;
- the fundamentals bundle uses only trusted quarters;
- Comparison statements apply the same rule;
- the one-request diagnostic run.

Regression (sequential, `--rerun`): core 625/0 and 623/0, server **707/0** (10 emulator skipped; +5), shared 289/0 and 286/0, APK and
`xcodebuild` PASS.

What this means for AAPL is unknown until the diagnostic run. If the failing year is older than the last 3, AAPL's recent quarterly
income becomes usable (and TTM from quarters too); if a recent year fails, quarters stay refused.

### One-request diagnostic command (for the owner; key in your own shell, as before)
```bash
ALPHA_VANTAGE_SMOKE_CONFIRM=run-bounded-free-tier-smoke-test ALPHA_VANTAGE_TERMS_CONFIRMED=personal-non-commercial ALPHA_VANTAGE_SMOKE_SYMBOL=AAPL ALPHA_VANTAGE_SMOKE_FUNCTIONS=INCOME_STATEMENT ALPHA_VANTAGE_SMOKE_MAX_REQUESTS=1 ./gradlew :server:alphaVantageSmokeTest
```
After it: 9 of today's 25 free requests used. The output (and `server/build/alphavantage-smoke.md`) holds no key or URLs.

## 10. INCOME_STATEMENT diagnostic result (AAPL, 1 request, run by the owner)

Run: `ALPHA_VANTAGE_SMOKE_FUNCTIONS=INCOME_STATEMENT ALPHA_VANTAGE_SMOKE_MAX_REQUESTS=1 ./gradlew --no-daemon :server:alphaVantageSmokeTest`,
with the key passed to that single process.
- **Requests:** 1 sent of a budget of 1; BUILD SUCCESSFUL in 11 s.
- **Not counted:** three earlier attempts the same day sent **0** requests ("ALPHA_VANTAGE_API_KEY is not set"; one SSH placeholder error).
- **Repeat run:** the owner ran the same diagnostic a second time (BUILD SUCCESSFUL in 3 s, 1 request). The output was identical: deterministic,
  no data change between the runs.
- **Quota:** 10 of 25 free requests used today (8 smoke test + 2 diagnostic runs).

**Fields (101 periods: 20 annual + 81 quarterly):** `totalRevenue`, `costOfRevenue`, `grossProfit`, `operatingIncome`, `netIncome` and
`ebit` are present in every period. `interestExpense` is missing in 10 of 101 (`"None"` → null, never 0; interest coverage is then
unavailable for those periods). Currency USD only; no skipped rows. These names confirm the 9B.8 income mapping.

**Quarter sum ÷ annual by fiscal year:**

| Years | Result |
|---|---|
| FY2025, FY2024, FY2023 (the recent window) | 1.000 → STANDALONE |
| FY2022 … FY2013, FY2011, FY2010, FY2008, FY2007 | 1.000 → STANDALONE |
| FY2012 | 1.00962 → STANDALONE (within the 1 % tolerance, close to it) |
| **FY2009** | **0.952233 → UNVERIFIED** (the quarters add up to 95.2 % of the annual figure) |
| FY2006 | 2 quarters only → incomplete (history starts mid-year) |

**Cause:** a single old year, not a year-to-date series. The plausible explanation is a provider annual figure that reflects a later
restatement while the quarters keep their originally filed values. That is **not verified** (the provider doesn't say).

**Effect of the D-9B9A-6 rule for AAPL:**
- Verdict **STANDALONE**: the recent 3 years pass.
- **75 of 81 quarters trusted**: FY2009's 4 and FY2006's 2 are excluded; the in-progress FY2026 quarters are included.
- AAPL's quarterly income (revenue, margins, YoY quarterly growth) and TTM from quarters are now usable. Cash flow was already STANDALONE.
- The same quarters are used by Comparison statements.
- Under the 9B.8 all-years rule, the single FY2009 disagreement would have refused all 81 quarters. That is what the 8-request run showed
  as "quarters UNVERIFIED".

**Follow-ups (no action taken):**
- FY2012 at 0.96 % sits near the tolerance. Keep 1 %, but record the ratio in the diagnostics (now available).
- Check one non-calendar filer and one loss-maker the same way before relying on the rule broadly: 1 request each.
