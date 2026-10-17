# Android / iOS regression — Phase 9B.7

No `core/`, `app/shared/`, `app/androidApp/` or `app/iosApp/` file changed. All response models are unchanged; Alpha Vantage fills the
same nullable fields. With Alpha Vantage disabled (the default) the server behaves exactly as at `93fca36`.

What the apps would see with Alpha Vantage primary (not exercised on devices — no key):
| Screen | Difference | Contract impact |
|---|---|---|
| Company Details chart | as-traded history; free plan: 100 sessions only (1Y+ ranges truncated) | none (fewer points) |
| 1D chart / sparklines | unavailable | existing empty state |
| Quote / day range | end-of-day values timestamped at the close | none |
| Profile | premium: AV name/description/industry, **sector null**; free: FMP | nullable fields |
| Movers | tickers without company names | `name` already nullable |
| Market-data routes for callers off the personal allowlist | 502 `PROVIDER_RESTRICTED` ("isn't licensed for display…") | existing code; apps show "trying again won't help" |
| Statement history | AV figures, EPS null | nullable |

Suites and builds: see `CHATGPT_TEST_RESULTS.md` (shared Android/iOS suites, APK and simulator build re-run). Device checks NOT RUN.
