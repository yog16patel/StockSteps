# Phase 8A — Home and Markets UI audit (before changes)

Base: `ddb6dfb` (Phase 7E). Evidence: BEFORE captures on local MOCK `:8087` (a private copy of the current server, see
`SCREEN_BEFORE_AFTER.md`): Android emulator-5554 (Pixel 9 Pro, 412 dp) light / dark / 2.0×, iPhone 16 guest light / dark / AX5.
Originals: `captures/before/`.

## 1. Design system in use (both platforms)

| Area | What exists | Used by Home / Markets |
|---|---|---|
| Spacing | `ThemeSpacing` 4-pt scale + roles (`screen` 16, `cardPadding` 16, `sectionGap` 12, `contentGap` 12, `listRowVertical` 12, `sectionBreak` 8) | Home: `xl` (20) between open sections since KAN-14 (raw scale value, no role). Markets: `sectionGap` (12) between bordered cards. |
| Type | `screenTitle` 28, `sectionHeading` 18 (`StockSectionHeader`), `listTitle` 16, `bodySemiBold`, `small`, `caption`, `numberLabelStrong` | same on both |
| Surfaces | `StockCard` / `.stockCard` (border default **on**, `bordered = false` per call site), `HomeSection` + `HomeRowGroup` / `.homeRowGroup()` (borderless group, KAN-14), `StockInsightCard` (tinted education) | Home: open sections + borderless groups. Markets: 7 bordered cards (status, indices, brief, movers, sectors, tools, news) + the tinted lesson card. |
| Rows | `StockRow`, `StockNavigationRow`, `StockNewsCard`, `StockPriceChange` (arrow + sign + colour) | both |
| Charts | `StockSparkline` (index rows, only when the row is ≥ 380 wide), sector bars drawn inline in each screen | Markets |

## 2. Issue inventory

P0 = broken navigation, inaccessible control or misleading financial data. P1 = usability, hierarchy, readability. P2 = cosmetic.

| # | Pri | Screen | Platform | Issue | Evidence |
|---|---|---|---|---|---|
| H1 | P1 | Home · Your portfolio | both | The summary card shows the presenter's raw `notice` ("Unavailable: FX:USD/CAD, AAPL") while Portfolio maps the codes to words (`PortfolioOverviewDisplay.noticeText`); its Invested / P/L metrics carry no currency (Portfolio: "USD 1,234.00"); an unavailable total reads "USD —" with no spoken "unavailable". Same data, two wordings. | code: `HomePortfolioSummary` vs `PortfolioOverviewDisplay.of` |
| H2 | P2 | Home header | both | "What matters to you today" under the greeting repeats the page's purpose; a third heading line before any data. | before frames `home1` |
| H3 | P2 | Home | both | Section rhythm uses the raw `xl` value; Markets uses another (12). No shared role for the page-section gap. | code |
| M1 | P1 | Markets | both | Every section is a bordered card (7 borders on one page, plus the lesson card): heavy, boxed, inconsistent with Home (KAN-14) and Portfolio (borderless). | `light-markets1..4` |
| M2 | P1 | Markets · US Market | both | The status is a bordered card with its own heading "US Market" directly under the page title "Markets": an extra box and heading for three short lines. | `light-markets1` |
| M3 | P1 | Markets · Sector Performance | both | Bars all grow from the left edge, so gains and losses differ by colour (and the text arrow) only, and the bar uses ~35 % of the row. Hard to compare at a glance. | `light-markets2/3` |
| M4 | P1 | Markets · Top Movers | iOS AX3+ | The company symbol and name are squeezed to one letter per line (price, change and the "?" button keep their width). Readability failure at accessibility sizes. | iOS `ax5-markets1` |
| M5 | P2 | Markets · Daily Brief entry | both | A separate bordered card between indices and movers (entry row + "Previous briefs"), one more box. | `light-markets1` |
| M6 | P2 | Markets | both | Section gap 12 between bordered cards; once borders go, sections need the Home rhythm (20) and the Home header-to-content gap (4) to stay separated. | — |
| M7 | P2 | Markets · News | both | News rows use a 12 inset while every other group uses 16. | code |
| L1 | P2 | Discover (legacy iOS `MarketMoversView`) | iOS | Shows "Unavailable% · Price Unavailable" text formatting. **Out of scope** (Discover; not reachable from Home/Markets) — follow-up. | code |

No P0 issue found on Home or Markets: navigation targets, 48-pt touch targets, Sample labelling, EOD / proxy labels and typed errors are
intact in the BEFORE frames and code. The TSX proxy explanation on Home is long (3 lines) but it is the Daily Brief's own wording (D-B1
rules) and stays.

## 3. Scoped refinement plan (8A)

1. **Token:** add the semantic role `ThemeSpacing.pageSectionGap = xl` (20, no value change) for open landing sections; Home and Markets use it.
2. **Markets surfaces (M1, M5, M6, M7):** reuse `HomeSection` + `HomeRowGroup` / `.homeRowGroup()` (the KAN-14 open-section pattern) for
   indices, movers, sectors, research tools and news — borderless per call site; the brief entry joins the indices group; `pageSectionGap`
   between sections; news inset 16. The tinted lesson card and error states stay. Global `StockCard` default unchanged.
3. **Markets status (M2):** an open status block on the page (dot + "US Market · After-hours", Sample badge, session line, footer) instead of a
   card with its own heading. Text from core / `MarketsScreenPresentation` (new `statusHeadline`).
4. **Sector chart (M3):** diverging bars around a centre line (gains right, losses left, flat = a centre tick), wider track; geometry from a
   shared `MarketsScreenPresentation.sectorBar` (tested). Values, order, captions and the methodology lesson unchanged.
5. **Movers at AX sizes (M4, iOS):** an optional `StockRow(stackTrailing:)` (default false, other screens unchanged) puts price and change under
   the name from `accessibility1`; Markets passes it.
6. **Home (H1–H3):** the portfolio summary takes its total, today, metrics (with currency), notice (in words) and spoken text from
   `PortfolioOverviewDisplay`; drop the "What matters to you today" line; `pageSectionGap`.
7. No new requests, no data, wording or rule change for EOD / proxy / Sample / typed errors / Daily Brief takeaway.

Owner decisions raised: D8A-1 dropping the Home subtitle; D8A-2 diverging sector bars; D8A-3 brief entry inside the indices group.
