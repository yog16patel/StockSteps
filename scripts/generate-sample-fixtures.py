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
