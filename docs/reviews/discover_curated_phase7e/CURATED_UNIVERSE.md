# Phase 7E — curated Discover universe

- **Version** `2026-10-17.1`; **155 companies**; source `server/src/main/resources/screener/curated-us-universe.json`.
- **Description shown to users (`UniverseInfo.description`):** "A fixed list of about 155 well-known US-listed companies chosen by StockSteps for learning. It isn't the whole market or an index membership list."
- **Provenance:** Curated by StockSteps from public company names and tickers (no market-data feed, no scraping). Sectors use the provider's sector names; the exchange is given only where StockSteps is confident (otherwise null). Company profile data from the market-data provider (sector, industry, exchange) is shown instead when it has loaded. Not independently verified against a licensed reference; review quarterly.

## Sector coverage

| Sector (provider naming) | Companies |
|---|---:|
| Technology | 20 |
| Healthcare | 20 |
| Financial Services | 20 |
| Industrials | 18 |
| Consumer Cyclical | 16 |
| Consumer Defensive | 15 |
| Communication Services | 12 |
| Energy | 10 |
| Utilities | 8 |
| Basic Materials | 8 |
| Real Estate | 8 |

## Entries (ticker · name · exchange; exchange blank = left to the provider profile)

**Basic Materials:** LIN · Linde plc · NASDAQ; SHW · The Sherwin-Williams Company · NYSE; APD · Air Products and Chemicals, Inc. · NYSE; ECL · Ecolab Inc. · NYSE; FCX · Freeport-McMoRan Inc. · NYSE; NEM · Newmont Corporation · NYSE; NUE · Nucor Corporation · NYSE; DOW · Dow Inc. · NYSE

**Communication Services:** GOOGL · Alphabet Inc. · NASDAQ; META · Meta Platforms, Inc. · NASDAQ; NFLX · Netflix, Inc. · NASDAQ; DIS · The Walt Disney Company · NYSE; CMCSA · Comcast Corporation · NASDAQ; T · AT&T Inc. · NYSE; VZ · Verizon Communications Inc. · NYSE; TMUS · T-Mobile US, Inc. · NASDAQ; CHTR · Charter Communications, Inc. · NASDAQ; EA · Electronic Arts Inc. · NASDAQ; TTWO · Take-Two Interactive Software, Inc. · NASDAQ; OMC · Omnicom Group Inc. · NYSE

**Consumer Cyclical:** AMZN · Amazon.com, Inc. · NASDAQ; TSLA · Tesla, Inc. · NASDAQ; HD · The Home Depot, Inc. · NYSE; MCD · McDonald's Corporation · NYSE; NKE · NIKE, Inc. · NYSE; LOW · Lowe's Companies, Inc. · NYSE; SBUX · Starbucks Corporation · NASDAQ; TJX · The TJX Companies, Inc. · NYSE; BKNG · Booking Holdings Inc. · NASDAQ; GM · General Motors Company · NYSE; F · Ford Motor Company · NYSE; MAR · Marriott International, Inc. · NASDAQ; CMG · Chipotle Mexican Grill, Inc. · NYSE; ROST · Ross Stores, Inc. · NASDAQ; ORLY · O'Reilly Automotive, Inc. · NASDAQ; EBAY · eBay Inc. · NASDAQ

**Consumer Defensive:** WMT · Walmart Inc. · —; PG · The Procter & Gamble Company · NYSE; KO · The Coca-Cola Company · NYSE; PEP · PepsiCo, Inc. · NASDAQ; COST · Costco Wholesale Corporation · NASDAQ; PM · Philip Morris International Inc. · NYSE; MO · Altria Group, Inc. · NYSE; MDLZ · Mondelez International, Inc. · NASDAQ; CL · Colgate-Palmolive Company · NYSE; KMB · Kimberly-Clark Corporation · NYSE; GIS · General Mills, Inc. · NYSE; KR · The Kroger Co. · NYSE; TGT · Target Corporation · NYSE; HSY · The Hershey Company · NYSE; STZ · Constellation Brands, Inc. · NYSE

**Energy:** XOM · Exxon Mobil Corporation · NYSE; CVX · Chevron Corporation · NYSE; COP · ConocoPhillips · NYSE; EOG · EOG Resources, Inc. · NYSE; SLB · SLB Limited · NYSE; PSX · Phillips 66 · NYSE; MPC · Marathon Petroleum Corporation · NYSE; OXY · Occidental Petroleum Corporation · NYSE; KMI · Kinder Morgan, Inc. · NYSE; WMB · The Williams Companies, Inc. · NYSE

**Financial Services:** JPM · JPMorgan Chase & Co. · NYSE; BAC · Bank of America Corporation · NYSE; WFC · Wells Fargo & Company · NYSE; C · Citigroup Inc. · NYSE; GS · The Goldman Sachs Group, Inc. · NYSE; MS · Morgan Stanley · NYSE; BRK-B · Berkshire Hathaway Inc. (Class B) · NYSE; V · Visa Inc. · NYSE; MA · Mastercard Incorporated · NYSE; AXP · American Express Company · NYSE; SCHW · The Charles Schwab Corporation · NYSE; BLK · BlackRock, Inc. · NYSE; SPGI · S&P Global Inc. · NYSE; CB · Chubb Limited · NYSE; PGR · The Progressive Corporation · NYSE; USB · U.S. Bancorp · NYSE; PNC · The PNC Financial Services Group, Inc. · NYSE; COF · Capital One Financial Corporation · NYSE; ICE · Intercontinental Exchange, Inc. · NYSE; CME · CME Group Inc. · NASDAQ

**Healthcare:** LLY · Eli Lilly and Company · NYSE; JNJ · Johnson & Johnson · NYSE; UNH · UnitedHealth Group Incorporated · NYSE; ABBV · AbbVie Inc. · NYSE; MRK · Merck & Co., Inc. · NYSE; PFE · Pfizer Inc. · NYSE; TMO · Thermo Fisher Scientific Inc. · NYSE; ABT · Abbott Laboratories · NYSE; DHR · Danaher Corporation · NYSE; AMGN · Amgen Inc. · NASDAQ; ISRG · Intuitive Surgical, Inc. · NASDAQ; BMY · Bristol-Myers Squibb Company · NYSE; GILD · Gilead Sciences, Inc. · NASDAQ; CVS · CVS Health Corporation · NYSE; MDT · Medtronic plc · NYSE; SYK · Stryker Corporation · NYSE; VRTX · Vertex Pharmaceuticals Incorporated · NASDAQ; REGN · Regeneron Pharmaceuticals, Inc. · NASDAQ; CI · The Cigna Group · NYSE; ELV · Elevance Health, Inc. · NYSE

**Industrials:** CAT · Caterpillar Inc. · NYSE; GE · GE Aerospace · NYSE; HON · Honeywell International Inc. · NASDAQ; UNP · Union Pacific Corporation · NYSE; UPS · United Parcel Service, Inc. · NYSE; RTX · RTX Corporation · NYSE; LMT · Lockheed Martin Corporation · NYSE; DE · Deere & Company · NYSE; BA · The Boeing Company · NYSE; MMM · 3M Company · NYSE; ADP · Automatic Data Processing, Inc. · NASDAQ; ETN · Eaton Corporation plc · NYSE; WM · Waste Management, Inc. · NYSE; CSX · CSX Corporation · NASDAQ; FDX · FedEx Corporation · NYSE; NOC · Northrop Grumman Corporation · NYSE; GD · General Dynamics Corporation · NYSE; EMR · Emerson Electric Co. · NYSE

**Real Estate:** PLD · Prologis, Inc. · NYSE; AMT · American Tower Corporation · NYSE; EQIX · Equinix, Inc. · NASDAQ; CCI · Crown Castle Inc. · NYSE; SPG · Simon Property Group, Inc. · NYSE; O · Realty Income Corporation · NYSE; PSA · Public Storage · NYSE; WELL · Welltower Inc. · NYSE

**Technology:** AAPL · Apple Inc. · NASDAQ; MSFT · Microsoft Corporation · NASDAQ; NVDA · NVIDIA Corporation · NASDAQ; AVGO · Broadcom Inc. · NASDAQ; ORCL · Oracle Corporation · NYSE; CRM · Salesforce, Inc. · NYSE; ADBE · Adobe Inc. · NASDAQ; AMD · Advanced Micro Devices, Inc. · NASDAQ; CSCO · Cisco Systems, Inc. · NASDAQ; INTC · Intel Corporation · NASDAQ; QCOM · QUALCOMM Incorporated · NASDAQ; TXN · Texas Instruments Incorporated · NASDAQ; IBM · International Business Machines Corporation · NYSE; INTU · Intuit Inc. · NASDAQ; NOW · ServiceNow, Inc. · NYSE; AMAT · Applied Materials, Inc. · NASDAQ; MU · Micron Technology, Inc. · NASDAQ; ADI · Analog Devices, Inc. · NASDAQ; PANW · Palo Alto Networks, Inc. · NASDAQ; ACN · Accenture plc · NYSE

**Utilities:** NEE · NextEra Energy, Inc. · NYSE; DUK · Duke Energy Corporation · NYSE; SO · The Southern Company · NYSE; D · Dominion Energy, Inc. · NYSE; AEP · American Electric Power Company, Inc. · NASDAQ; EXC · Exelon Corporation · NASDAQ; SRE · Sempra · NYSE; XEL · Xcel Energy Inc. · NASDAQ

## Data rules

- **Metadata only.** The file never holds prices or financials, so nothing in it can go stale as market data.
- **Provider profile wins.** Once a company's FMP profile loads, its name, sector, industry, exchange and country are shown instead of the curated values. Those values are the provider's, not StockSteps' guess.
- **Exchange unknown.** It is left null where StockSteps wasn't confident: WMT, whose listing venue may have changed. It's never guessed.
- **Ticker form.** Tickers use the provider form (`BRK-B`). Search treats `BRK.B` and `BRK-B` alike.
- **No stable identifier** (CIK, FIGI) is included. None was available from a licensed source in this phase.

## Maintenance

1. Review quarterly, and on corporate events (mergers, delistings, renames, ticker changes).
2. Change the file only in a reviewed commit and bump `version` (`YYYY-MM-DD.n`).
3. `CuratedDiscoverTest.theCuratedListIsAboutOneHundredFiftyValidUniqueCompaniesAcrossAllSectors` checks the list:
   - 140–160 entries;
   - valid tickers and names;
   - known exchanges only;
   - no duplicates after ticker normalization;
   - all 11 sectors;
   - the "isn't the whole market" wording.
4. Delisted or renamed companies: when FMP's profile or closes stop for a symbol, it shows as unavailable (never estimated). Replace it in the next version.
