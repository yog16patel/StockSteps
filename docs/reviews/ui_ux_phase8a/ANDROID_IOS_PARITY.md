# Phase 8A — Android / iOS parity

| Element | Android (Compose) | iOS (SwiftUI) | Parity |
|---|---|---|---|
| Section pattern | `HomeSection` + `HomeRowGroup` | `HomeSection` + `.homeRowGroup()` | same |
| Section gap | `spacing.pageSectionGap` (20) | `space.pageSectionGap` (20) | same token |
| Status block | dot + `statusHeadline` + Sample badge, session line, footer (spoken notice) | same; `.id(typeSize)` for live Dynamic Type | same text (shared) |
| Indices + brief entry | one group; brief rows after a divider; own group when the overview failed | same | same |
| Sector bars | `sectorBar` geometry, 0.45 / 0.35 / 0.20 row weights, centre line | `sectorBar` geometry, name and bar share the free width equally, centre line | same geometry; widths differ slightly (pre-existing layout choice per platform) |
| Sector rows at large text | name above, bar + change below (`largeFontScale`) | same from `xxxLarge` | unchanged per platform |
| Top Movers at large text | Compose `StockRow` keeps values beside the name; names ellipsize at 2 lines (2.0× readable) | `stackTrailing` from `accessibility1`: values under the name | **differs by design**: Android did not have the squeeze (frame `font2-markets3`); iOS did |
| Index sparkline | shown when the row ≥ 380 dp (412-dp Pixel: yes) | shown when ≥ 380 pt (iPhone 16 361 pt: no) | unchanged rule (`TREND_MIN_ROW_WIDTH`) |
| Home header | greeting only | greeting only | same |
| Home portfolio summary | `portfolioOverview`: total, today, metrics with currency, notice in words, one spoken summary | same | same (shared object) |
| Home "Refresh dashboard" button | present (no pull-to-refresh on Android Home) | pull-to-refresh only | pre-existing difference, not changed |

Shared code is not visual proof: each platform's frames were reviewed separately (`SCREEN_BEFORE_AFTER.md`).
