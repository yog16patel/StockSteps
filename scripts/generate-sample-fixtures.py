#!/usr/bin/env python3
# Run from the repo root: python3 scripts/generate-sample-fixtures.py
# Idempotent: keeps imported provider rows, (re)writes synthetic sample values and the manifest.
"""Adds clearly-labelled synthetic sample data around the imported provider responses."""
import json, math, random, datetime, pathlib
root = pathlib.Path("server/src/main/resources/fixtures")
now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)
logo = lambda s: f"https://images.financialmodelingprep.com/symbol/{s}.png"
def load(p, default): f = root / p; return json.loads(f.read_text()) if f.exists() else default
def save(p, v): f = root / p; f.parent.mkdir(parents=True, exist_ok=True); f.write_text(json.dumps(v, indent=4) + "\n")

# Sample companies (real tickers, made-up market values). AAPL keeps the imported real quote.
companies = {
  "NVDA": ("NVIDIA Corporation", "NASDAQ", "Technology", "Semiconductors", 125.48, 5.20, 210_000_000),
  "META": ("Meta Platforms, Inc.", "NASDAQ", "Communication Services", "Internet Content & Information", 598.10, 14.07, 14_500_000),
  "MSFT": ("Microsoft Corporation", "NASDAQ", "Technology", "Software - Infrastructure", 425.18, 7.72, 21_300_000),
  "AMZN": ("Amazon.com, Inc.", "NASDAQ", "Consumer Cyclical", "Internet Retail", 186.42, 2.06, 38_900_000),
  "TSLA": ("Tesla, Inc.", "NASDAQ", "Consumer Cyclical", "Auto - Manufacturers", 241.05, -7.76, 98_400_000),
  "NFLX": ("Netflix, Inc.", "NASDAQ", "Communication Services", "Entertainment", 702.33, -10.33, 3_100_000),
  "INTC": ("Intel Corporation", "NASDAQ", "Technology", "Semiconductors", 22.71, -0.22, 61_700_000),
  "AMD":  ("Advanced Micro Devices, Inc.", "NASDAQ", "Technology", "Semiconductors", 158.64, 3.11, 45_200_000),
}
aapl = load("stocks/AAPL/quote.json", None)

def mover(symbol, name, exchange, price, change, volume=None):
    prev = price - change
    return {"symbol": symbol, "name": name, "price": round(price, 4), "change": round(change, 4),
            "changePercent": round(change / prev * 100, 4), "exchange": exchange,
            **({"volume": float(volume)} if volume else {}), "logoUrl": logo(symbol)}

movers = {s: mover(s, n, ex, p, c, v) for s, (n, ex, _, _, p, c, v) in companies.items()}
if aapl:
    movers["AAPL"] = {"symbol": "AAPL", "name": aapl["companyName"], "price": aapl["price"], "change": aapl["change"],
                      "changePercent": aapl["changePercent"], "exchange": "NASDAQ", "volume": float(aapl["volume"]), "logoUrl": logo("AAPL")}

snapshot = load("market/snapshot.json", {"lastUpdated": now.isoformat().replace("+00:00", "Z")})
def merge(section, extra, key, reverse):
    rows = {m["symbol"]: m for m in snapshot.get(section, [])}
    for m in extra: rows.setdefault(m["symbol"], m)
    snapshot[section] = sorted(rows.values(), key=lambda m: (m.get(key) is not None, m.get(key) or 0), reverse=reverse)
merge("gainers", [movers[s] for s in ("NVDA", "META", "MSFT", "AMZN", "AMD")], "changePercent", True)
merge("losers", [movers[s] for s in ("TSLA", "AAPL", "NFLX", "INTC")], "changePercent", False)
snapshot["losers"].sort(key=lambda m: m["changePercent"])
merge("mostActive", [movers[s] for s in ("NVDA", "TSLA", "INTC", "AMD", "AAPL")], "volume", True)
snapshot["indices"] = [
    {"symbol": "SPY", "name": "S&P 500", "price": 669.21, "change": 3.66, "changePercent": 0.55, "isProxy": True},
    {"symbol": "QQQ", "name": "Nasdaq-100", "price": 602.34, "change": 2.76, "changePercent": 0.46, "isProxy": True},
    {"symbol": "DIA", "name": "Dow 30", "price": 465.80, "change": -0.56, "changePercent": -0.12, "isProxy": True},
]
snapshot["lastUpdated"] = now.isoformat().replace("+00:00", "Z")
snapshot.setdefault("errors", [])
save("market/snapshot.json", snapshot)

# Quotes, profiles (facts only: name, sector, industry, country) and intraday sparklines.
all_rows = {m["symbol"]: m for sec in ("gainers", "losers", "mostActive") for m in snapshot[sec]}
for symbol, m in all_rows.items():
    price, change = m.get("price"), m.get("change")
    if symbol != "AAPL" and price is not None and change is not None:
        save(f"stocks/{symbol}/quote.json", {"symbol": symbol, "companyName": m.get("name"), "price": price, "change": change,
             "changePercent": m["changePercent"], "previousClose": round(price - change, 4),
             "dayHigh": round(max(price, price - change) * 1.008, 4), "dayLow": round(min(price, price - change) * 0.992, 4),
             **({"volume": int(m["volume"])} if m.get("volume") else {})})
    info = companies.get(symbol)
    save(f"stocks/{symbol}/profile.json", {"symbol": symbol, "companyName": m.get("name"),
         **({"sector": info[2], "industry": info[3], "country": "US"} if info else {}),
         "currency": "USD", "exchange": m.get("exchange"), "logoUrl": logo(symbol)})
    if price is not None and change is not None and price > 0.01:
        # Smoothed random walk from the previous close to the latest price (78 five-minute bars).
        rng = random.Random(symbol)
        start, points, walk = price - change, 78, [0.0]
        for _ in range(points - 1):
            walk.append(walk[-1] + rng.gauss(0, 1))
        smooth = [sum(walk[max(0, i - 4):i + 1]) / len(walk[max(0, i - 4):i + 1]) for i in range(points)]
        scale = (abs(change) * 0.35 + price * 0.002) / (max(abs(v) for v in smooth) or 1)
        closes = []
        for i in range(points):
            t = i / (points - 1)
            drift = smooth[i] - smooth[-1] * t  # pin both ends
            closes.append(round(start + change * t + drift * scale * math.sin(math.pi * t), 4))
        closes[-1] = price
        save(f"stocks/{symbol}/sparkline.json", {"symbol": symbol, "closes": closes, "sessionDate": now.date().isoformat()})

save("stocks/search.json", [{"symbol": s, "name": m.get("name") or s, "currency": "USD", "exchange": m.get("exchange"),
                             "exchangeFullName": m.get("exchange")} for s, m in sorted(all_rows.items())])

# Neutral sample news (no claims about real companies), newest first, plus imported articles.
samples = [
    ("Sample story: stocks move as investors weigh new economic data", 45),
    ("Sample story: what market movers can and cannot tell you", 130),
    ("Sample story: a beginner's look at how indexes are built", 260),
]
news = [{"title": t, "url": f"https://example.com/stocksteps/sample-{i + 1}", "source": "StockSteps Sample",
         "publishedAt": (now - datetime.timedelta(minutes=m)).isoformat().replace("+00:00", "Z"), "id": f"sample-{i + 1}"}
        for i, (t, m) in enumerate(samples)]
existing = load("news/market.json", [])
save("news/market.json", sorted(news + [a for a in existing if not a["url"].startswith("https://example.com/stocksteps/")],
                                key=lambda a: a.get("publishedAt") or "", reverse=True))

save("manifest.json", {"capturedAt": now.isoformat().replace("+00:00", "Z"),
     "source": "Mixed: owner-supplied provider responses (AAPL quote, MOTS/SPEC/LUCY movers, market hours, one news article) "
               "plus synthetic sample values (index cards, other movers, quotes, sparklines, sample news). Not market data."})
print("symbols:", ", ".join(sorted(all_rows)))


# ---------------------------------------------------------------------------
# Company Details fixtures (synthetic, stable development data; not market data).
# Scenarios: MSFT complete/up, AAPL complete (real quote) without why-moving,
# NVDA large move + P/E below history, TSLA down + no dividend + no P/E history,
# LONGN long name + no logo + sparse metrics + no news.
# ---------------------------------------------------------------------------

def fact(value=None, amount=None, availability=None, period="annual", year=2025):
    available = value is not None or amount is not None
    f = {"source": "PROVIDER_DIRECT" if available else "NOT_AVAILABLE",
         "availability": availability or ("AVAILABLE" if available else "MISSING"),
         "basis": {"period": period, "date": f"{year}-12-31", "fiscalYear": year, "currency": "USD"}}
    if value is not None: f["value"] = value
    if amount is not None: f["amount"] = int(amount)
    return f

def fundamentals(symbol, d):
    pe_history = d.get("pe_history")
    historical = {}
    if pe_history:
        avg = sum(pe_history) / len(pe_history)
        historical["pe"] = {"observations": [{"year": 2021 + i, "date": f"{2021 + i}-12-31", "value": v} for i, v in enumerate(pe_history)],
                            "average": round(avg, 2), "median": sorted(pe_history)[len(pe_history) // 2],
                            "minimum": min(pe_history), "maximum": max(pe_history), "validCount": len(pe_history),
                            "requestedYears": 5, "differencePercent": round((d["pe"] / avg - 1) * 100, 1), "reliable": True,
                            "source": "BACKEND_CALCULATED", "comparisonSource": "CONTEXT_DERIVED"}
    g = lambda k, money=False: fact(amount=d[k]) if money and d.get(k) is not None else fact(value=d.get(k))
    dividend = fact(availability="NO_DIVIDEND") if d.get("dividendYield") == "none" else fact(value=d.get("dividendYield"), period="TTM")
    return {"symbol": symbol,
            "financials": {
                "growth": {"revenue": g("revenue", True), "revenueGrowth": g("revenueGrowth"), "netIncome": g("netIncome", True),
                           "netIncomeGrowth": g("netIncomeGrowth"), "eps": g("eps"), "epsGrowth": g("epsGrowth")},
                "profitability": {"grossMargin": g("grossMargin"), "operatingMargin": g("operatingMargin"), "netMargin": g("netMargin")},
                "financialHealth": {"cash": g("cash", True), "debt": g("debt", True), "debtEquity": g("debtEquity"), "currentRatio": g("currentRatio")},
                "cashFlow": {"operatingCashFlow": g("operatingCashFlow", True), "freeCashFlow": g("freeCashFlow", True), "fcfMargin": g("fcfMargin")},
                "shareholderReturns": {"dividendYield": dividend}},
            "valuation": {"metrics": {"pe": fact(value=d.get("pe"), period="TTM"), "priceSales": fact(value=d.get("priceSales"), period="TTM")},
                          "historical": historical},
            "datasets": {}, "retrievedAt": now.isoformat().replace("+00:00", "Z")}

details = {
    "MSFT": dict(price=425.18, change=7.72, marketCap=3_160_000_000_000, name="Microsoft Corporation", sector="Technology", industry="Software - Infrastructure",
                 description="Microsoft develops software, cloud computing services, devices and AI products used by consumers and businesses worldwide.",
                 revenue=245e9, revenueGrowth=15.3, netIncome=88e9, netIncomeGrowth=12.4, eps=11.80, epsGrowth=12.1, grossMargin=69.4, operatingMargin=44.6,
                 netMargin=35.9, cash=80e9, debt=76e9, debtEquity=0.30, currentRatio=1.3, operatingCashFlow=118e9, freeCashFlow=74e9, fcfMargin=30.2,
                 dividendYield=0.8, pe=31.2, priceSales=12.9, pe_history=[33.1, 29.5, 24.0, 26.8, 23.6],
                 why=("Sample explanation: Microsoft shares moved higher after the company reported stronger cloud revenue growth.",
                      "Cloud services are one of Microsoft's largest growth businesses, so changes there can shift expectations for future earnings.")),
    "AAPL": dict(marketCap=4_874_072_686_740, name="Apple Inc.", sector="Technology", industry="Consumer Electronics",
                 description="Apple designs smartphones, personal computers, tablets, wearables and related software and services.",
                 revenue=416e9, revenueGrowth=6.4, netIncome=112e9, netIncomeGrowth=4.0, eps=7.45, epsGrowth=5.1, grossMargin=46.2, operatingMargin=31.5,
                 netMargin=26.9, cash=36e9, debt=98e9, debtEquity=1.5, currentRatio=0.9, operatingCashFlow=111e9, freeCashFlow=99e9, fcfMargin=23.8,
                 dividendYield=0.31, pe=39.6, priceSales=11.7, pe_history=[28.9, 25.4, 30.1, 33.6, 31.0], why=None),
    "NVDA": dict(price=125.48, change=5.20, marketCap=3_060_000_000_000, name="NVIDIA Corporation", sector="Technology", industry="Semiconductors",
                 description="NVIDIA designs graphics processors and computing platforms used for gaming, data centers and artificial intelligence.",
                 revenue=130e9, revenueGrowth=114.2, netIncome=72.9e9, netIncomeGrowth=145.0, eps=2.94, epsGrowth=147.0, grossMargin=75.0, operatingMargin=62.4,
                 netMargin=56.1, cash=43e9, debt=8.5e9, debtEquity=0.13, currentRatio=4.4, operatingCashFlow=64e9, freeCashFlow=60.9e9, fcfMargin=46.8,
                 dividendYield=0.03, pe=49.8, priceSales=23.5, pe_history=[72.4, 89.1, 44.0, 52.7, 33.3],
                 why=("Sample explanation: NVIDIA shares rose as investors focused on demand for chips used in AI data centers.",
                      "Data-center sales are a large share of NVIDIA's revenue, so expectations about AI spending strongly affect the stock.")),
    "TSLA": dict(price=241.05, change=-7.76, marketCap=770_000_000_000, name="Tesla, Inc.", sector="Consumer Cyclical", industry="Auto - Manufacturers",
                 description="Tesla designs and sells electric vehicles, and develops energy storage and solar products.",
                 revenue=97.7e9, revenueGrowth=0.9, netIncome=7.1e9, netIncomeGrowth=-52.5, eps=2.04, epsGrowth=-53.0, grossMargin=17.9, operatingMargin=7.2,
                 netMargin=7.3, cash=16e9, debt=13e9, debtEquity=0.18, currentRatio=1.8, operatingCashFlow=14.9e9, freeCashFlow=3.6e9, fcfMargin=3.7,
                 dividendYield="none", pe=108.0, priceSales=7.9, pe_history=None,
                 why=("Sample explanation: Tesla shares moved lower after a delivery update came in below what some investors expected.",
                      "Vehicle deliveries drive most of Tesla's revenue, so delivery numbers can change expectations for upcoming results.")),
    "LONGN": dict(price=18.42, change=0.37, marketCap=1_250_000_000,
                  name="Longname Sample Advanced Materials & Renewable Infrastructure Holdings International Corporation",
                  sector="Industrials", industry="Specialty Industrial Machinery", description=None, logo=False,
                  revenue=2.1e9, why=None, sample_only=True),
}

for symbol, d in details.items():
    quote_path = root / f"stocks/{symbol}/quote.json"
    quote = json.loads(quote_path.read_text()) if quote_path.exists() else {"symbol": symbol}
    if "price" in d and symbol != "AAPL":
        price, change = d["price"], d["change"]
        quote.update({"companyName": d["name"], "price": price, "change": change, "changePercent": round(change / (price - change) * 100, 4),
                      "previousClose": round(price - change, 4), "dayHigh": round(max(price, price - change) * 1.008, 4),
                      "dayLow": round(min(price, price - change) * 0.992, 4)})
    quote["marketCap"] = d["marketCap"]
    save(f"stocks/{symbol}/quote.json", quote)
    profile = {"symbol": symbol, "companyName": d["name"], "sector": d["sector"], "industry": d["industry"], "country": "US",
               "currency": "USD", "exchange": "NASDAQ"}
    if d.get("description"): profile["description"] = d["description"]
    if d.get("logo", True): profile["logoUrl"] = logo(symbol)
    save(f"stocks/{symbol}/profile.json", profile)
    save(f"stocks/{symbol}/fundamentals-annual.json", fundamentals(symbol, d))

    # Daily history: ~5 years of weekday closes ending at the latest price (stable seed per symbol).
    if not d.get("sample_only"):
        last = quote["price"]
        rng = random.Random(f"daily-{symbol}")
        days, day = [], now.date()
        while len(days) < 1260:
            if day.weekday() < 5: days.append(day)
            day -= datetime.timedelta(days=1)
        days.reverse()
        value, closes = last * 0.45, []
        for _ in days:
            value *= math.exp(rng.gauss(math.log(1 / 0.45) / len(days), 0.017))
            closes.append(value)
        scale = last / closes[-1]
        save(f"stocks/{symbol}/chart-daily.json", [{"time": t.isoformat(), "close": round(c * scale, 2)} for t, c in zip(days, closes)])
        spark = load(f"stocks/{symbol}/sparkline.json", None)
        if spark:
            start = datetime.datetime.combine(now.date(), datetime.time(9, 30))
            save(f"stocks/{symbol}/chart-intraday.json", [{"time": (start + datetime.timedelta(minutes=5 * i)).strftime("%Y-%m-%d %H:%M:%S"), "close": c}
                                                          for i, c in enumerate(spark["closes"])])
    if d.get("why"):
        summary, matters = d["why"]
        save(f"stocks/{symbol}/why-moving.json", {"symbol": symbol, "summary": summary, "whyItMatters": matters,
             "changePercent": round(quote["changePercent"], 2), "generatedAt": now.isoformat().replace("+00:00", "Z"),
             "sources": [{"title": f"Sample source {i + 1} for {symbol}", "url": f"https://example.com/stocksteps/{symbol.lower()}-source-{i + 1}",
                          "publisher": "StockSteps Sample"} for i in range(2)]})
    if symbol != "LONGN":
        items = [(f"Sample story: {d['name'].split(',')[0].split(' Corporation')[0]} company update for development testing", 90, True),
                 ("Sample story: how quarterly results are reported", 300, False),
                 ("Sample story: understanding a company's revenue mix", 1500, False)]
        save(f"news/company/{symbol}.json", [{"title": t, "url": f"https://example.com/stocksteps/{symbol.lower()}-news-{i + 1}",
             "symbol": symbol, "source": "StockSteps Sample", "id": f"{symbol}-news-{i + 1}",
             "publishedAt": (now - datetime.timedelta(minutes=m)).isoformat().replace("+00:00", "Z"),
             **({"imageUrl": logo(symbol)} if image else {})} for i, (t, m, image) in enumerate(items)])

search = load("stocks/search.json", [])
known = {r["symbol"] for r in search}
for symbol, d in details.items():
    if symbol not in known:
        search.append({"symbol": symbol, "name": d["name"], "currency": "USD", "exchange": "NASDAQ", "exchangeFullName": "NASDAQ"})
save("stocks/search.json", sorted(search, key=lambda r: r["symbol"]))
print("company details:", ", ".join(details))
