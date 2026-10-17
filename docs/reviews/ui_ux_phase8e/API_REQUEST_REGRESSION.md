# Phase 8E — API request regression

**Measured:** mobile → Ktor requests through a counting proxy (:8092 → local MOCK :8093), with the same device and session and the BEFORE
and AFTER APKs built from the same tree (AFTER = BEFORE + 8E).

**Not measured:**
- Ktor → provider calls: MOCK makes none;
- cache hits: not instrumented on the client.

8E changes no data code, so neither can change.

| Sequence | BEFORE | AFTER |
|---|---|---|
| Bottom-tab round (warm) | 0 | 0 |
| Markets → Discover → Back; Home → Search → Apple → Back | 11 (catalog 1, search 1, AAPL details/chart/news/why-moving/earnings ×2, reminders 1, Home's 60 s alerts and watchlists refresh) | 11 (identical set) |
| Learn → term → Back → Back | — | 0 |
| Portfolio → Practice (first show) → My Portfolio | — | 2 (Practice's own first load) |
| Settings open → close | — | 0 |
| Rapid tab taps | — | 2 (Home's documented 60 s refresh) |

No request was added or removed by the motion change.
