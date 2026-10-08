#!/usr/bin/env python3
"""Generates deterministic MOCK companies for the Screener / Comparison (sample data, not real).

Writes server/src/main/resources/fixtures/stocks/{SYMBOL}/{profile,quote,fundamentals-annual,chart-daily}.json
in the same formats Company Details already reads, plus fixtures/screener/universe.json. Every ratio is
derived from the generated statements (P/E = price / diluted EPS, margins = amount / revenue, ...), so
values are internally consistent. Re-run after editing COMPANIES: python3 scripts/generate_screener_fixtures.py
"""
import datetime as dt
import json
import math
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "server", "src", "main", "resources", "fixtures")
END = dt.date(2026, 10, 7)          # last captured session in the existing fixtures
TIMESTAMP = 1791403200               # same quote timestamp as captured fixtures
RETRIEVED = "2026-10-07T21:13:43Z"

# symbol, name, exchange, country, currency, sector, industry, fy_end (MM-DD), last_fy,
# revenue (latest, reporting currency), revenue growth path (oldest→newest yearly %), gross, operating, net margin %,
# shares, price, dividend per share, debt, cash, equity, bank?, options
COMPANIES = [
    dict(s="KO", n="Coca-Cola Co", ex="NYSE", c="US", cur="USD", sec="Consumer Defensive", ind="Beverages - Non-Alcoholic", fy="12-31", y=2025,
         rev=47.1e9, g=[3, 6, 11, 6, 3], gm=60.5, om=29.0, nm=22.6, sh=4.31e9, px=69.4, dps=2.04, debt=44e9, cash=10.8e9, eq=26.9e9),
    dict(s="JPM", n="JPMorgan Chase & Co", ex="NYSE", c="US", cur="USD", sec="Financial Services", ind="Banks - Diversified", fy="12-31", y=2025,
         rev=166e9, g=[9, 13, 22, 12, 4], gm=None, om=40.0, nm=33.5, sh=2.78e9, px=298.0, dps=5.60, debt=420e9, cash=470e9, eq=350e9, bank=True),
    dict(s="XOM", n="Exxon Mobil Corp", ex="NYSE", c="US", cur="USD", sec="Energy", ind="Oil & Gas Integrated", fy="12-31", y=2025,
         rev=334e9, g=[50, 45, -16, -2, -3], gm=31.0, om=14.5, nm=10.2, sh=4.31e9, px=114.0, dps=3.96, debt=41e9, cash=23e9, eq=270e9),
    dict(s="JNJ", n="Johnson & Johnson", ex="NYSE", c="US", cur="USD", sec="Healthcare", ind="Drug Manufacturers - General", fy="12-28", y=2025,
         rev=92.1e9, g=[14, 1, 7, 4, 5], gm=68.5, om=25.5, nm=18.9, sh=2.41e9, px=176.0, dps=5.10, debt=36e9, cash=24e9, eq=72e9),
    dict(s="RIVN", n="Rivian Automotive Inc", ex="NASDAQ", c="US", cur="USD", sec="Consumer Cyclical", ind="Auto - Manufacturers", fy="12-31", y=2025,
         rev=5.1e9, g=[0, 2400, 167, 12, 2], gm=-12.0, om=-90.0, nm=-80.0, sh=1.19e9, px=13.2, dps=0, debt=5.9e9, cash=7.7e9, eq=6.6e9,
         loss=True, missing_keep=True),
    dict(s="RY.TO", n="Royal Bank of Canada", ex="TSX", c="CA", cur="CAD", sec="Financial Services", ind="Banks - Diversified", fy="10-31", y=2025,
         rev=64.0e9, g=[6, 8, 12, 9, 10], gm=None, om=36.0, nm=26.1, sh=1.41e9, px=205.0, dps=6.08, debt=330e9, cash=160e9, eq=130e9, bank=True),
    dict(s="ENB.TO", n="Enbridge Inc", ex="TSX", c="CA", cur="CAD", sec="Energy", ind="Oil & Gas Midstream", fy="12-31", y=2025,
         rev=58.0e9, g=[19, 11, -19, 22, 8], gm=40.0, om=19.0, nm=8.6, sh=2.18e9, px=66.8, dps=3.77, debt=99e9, cash=1.5e9, eq=70e9),
    dict(s="CNR.TO", n="Canadian National Railway Co", ex="TSX", c="CA", cur="CAD", sec="Industrials", ind="Railroads", fy="12-31", y=2025,
         rev=17.3e9, g=[3, 20, 2, 2, 3], gm=None, om=37.5, nm=26.0, sh=0.627e9, px=137.0, dps=3.55, debt=21.5e9, cash=0.6e9, eq=21.3e9),
    dict(s="SHOP.TO", n="Shopify Inc", ex="TSX", c="CA", cur="USD", sec="Technology", ind="Software - Application", fy="12-31", y=2025,
         rev=11.6e9, g=[57, 21, 26, 26, 31], gm=49.0, om=13.5, nm=18.0, sh=1.30e9, px=212.0, dps=0, debt=1.1e9, cash=5.6e9, eq=13.2e9,
         note_currency="Listed in CAD on the TSX; Shopify reports its financial statements in USD."),
    dict(s="BCE.TO", n="BCE Inc", ex="TSX", c="CA", cur="CAD", sec="Communication Services", ind="Telecom Services", fy="12-31", y=2025,
         rev=24.4e9, g=[2, 3, 2, -1, -1], gm=None, om=19.5, nm=4.6, sh=0.912e9, px=33.1, dps=1.75, debt=38.5e9, cash=0.9e9, eq=17.0e9, eps_drop=True),
    dict(s="CSU.TO", n="Constellation Software Inc", ex="TSX", c="CA", cur="USD", sec="Technology", ind="Software - Application", fy="12-31", y=2025,
         rev=11.5e9, g=[27, 29, 27, 19, 14], gm=None, om=15.0, nm=7.4, sh=0.0212e9, px=4400.0, dps=None, debt=3.0e9, cash=2.3e9, eq=2.6e9,
         missing_keep=True, note_currency="Listed in CAD; reports in USD."),
    dict(s="BB.TO", n="BlackBerry Ltd", ex="TSX", c="CA", cur="USD", sec="Technology", ind="Software - Infrastructure", fy="02-28", y=2024,
         rev=0.85e9, g=[-14, -2, -24, 4, -3], gm=64.0, om=-12.0, nm=-15.0, sh=0.59e9, px=5.6, dps=0, debt=0.2e9, cash=0.27e9, eq=0.73e9,
         loss=True, missing_keep=True, stale=True, short_history=True),
]
# Captured or sample-filled companies that also belong to the MOCK screener universe.
EXISTING = ["AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "AMD", "INTC", "NFLX", "TSLA", "TD"]


def fact(value, source="BACKEND_CALCULATED", availability="AVAILABLE", basis=None, note=None, money=False):
    if value is None or (isinstance(value, float) and not math.isfinite(value)):
        out = {"source": "NOT_AVAILABLE", "availability": availability if availability != "AVAILABLE" else "MISSING"}
    else:
        out = {"source": source, "availability": availability}
        if money:
            out["amount"] = int(round(value))
        else:
            out["value"] = round(value, 4)
    if basis:
        out["basis"] = basis
    if note:
        out["note"] = note
    return out


def yoy(cur, prev):
    if cur is None or prev is None or prev <= 0:
        return None
    return (cur - prev) / prev * 100


def cagr(cur, beg, n):
    if cur is None or beg is None or cur <= 0 or beg <= 0:
        return None
    return ((cur / beg) ** (1 / n) - 1) * 100


def weekdays(start, end):
    d = start
    while d <= end:
        if d.weekday() < 5:
            yield d
        d += dt.timedelta(days=1)


def generate(co, index):
    sym, cur = co["s"], co["cur"]
    years = 6
    # Revenue path: walk back from the latest revenue with the growth path.
    revs = [co["rev"]]
    for g in reversed(co["g"]):
        revs.insert(0, revs[0] / (1 + g / 100))
    revs = revs[-years:]
    fy_month, fy_day = map(int, co["fy"].split("-"))
    history = []
    for i in range(years):
        year = co["y"] - (years - 1 - i)
        rev = revs[i]
        drift = (i - (years - 1)) * 0.6
        nm = co["nm"] + drift * (1 if not co.get("eps_drop") else -1.4)
        om = co["om"] + drift
        if co.get("loss"):
            nm = co["nm"] - drift * 2
            om = co["om"] - drift * 2
        ni = rev * nm / 100
        shares = co["sh"] * (1 + 0.012 * (years - 1 - i))
        ocf = ni * 1.25 + rev * 0.04 if ni > 0 else rev * 0.03 + ni * 0.5
        capex = rev * 0.06
        dividends_paid = (co["dps"] or 0) * shares * (1 - 0.04 * (years - 1 - i))
        date = dt.date(year, fy_month, min(fy_day, 28 if fy_month == 2 else fy_day)).isoformat()
        history.append(dict(period="FY", fiscalYear=year, date=date, currency=cur, revenue=int(rev),
                            grossProfit=int(rev * co["gm"] / 100) if co["gm"] is not None else None,
                            operatingIncome=int(rev * om / 100), netIncome=int(ni), epsDiluted=round(ni / shares, 2),
                            operatingCashFlow=int(ocf), capitalExpenditure=int(capex), freeCashFlow=int(ocf - capex),
                            dividendsPaid=int(dividends_paid) if dividends_paid else None,
                            cash=int(co["cash"]), totalDebt=int(co["debt"]),
                            totalAssets=int(co["eq"] + co["debt"] * 1.6), totalLiabilities=int(co["debt"] * 1.6),
                            equity=int(co["eq"]),
                            currentAssets=None if co.get("bank") else int(co["cash"] + rev * 0.25),
                            currentLiabilities=None if co.get("bank") else int(rev * 0.22)))
    history = [{k: v for k, v in row.items() if v is not None} for row in reversed(history)]  # newest first
    latest, prior = history[0], history[1]
    basis = {"period": "annual", "date": latest["date"], "fiscalYear": latest["fiscalYear"], "currency": cur}
    ttm = {"period": "TTM", "date": latest["date"], "fiscalYear": latest["fiscalYear"], "currency": cur}
    sheet = {"period": "annual", "date": latest["date"], "fiscalYear": latest["fiscalYear"], "currency": cur}
    price = co["px"]
    # The listing may trade in another currency than the statements (SHOP.TO, CSU.TO): convert price at 1.35.
    listing_ccy = "CAD" if co["ex"] == "TSX" else "USD"
    price_in_reporting = price / 1.35 if listing_ccy != cur else price
    eps = latest["epsDiluted"]
    mcap_reporting = price_in_reporting * co["sh"]
    ni, rev = latest["netIncome"], latest["revenue"]
    turnaround = "Prior fiscal year was zero or a loss, so growth isn't shown as a percentage."
    growth = {
        "revenue": fact(rev, money=True, basis=basis),
        "revenueGrowth": fact(yoy(rev, prior["revenue"]), basis=basis),
        "revenueCagr3": fact(cagr(rev, history[3]["revenue"], 3), basis=basis, availability="AVAILABLE" if len(history) > 3 else "INSUFFICIENT_HISTORY"),
        "revenueCagr5": fact(cagr(rev, history[5]["revenue"], 5), basis=basis),
        "netIncome": fact(ni, money=True, basis=basis),
        "netIncomeGrowth": fact(yoy(ni, prior["netIncome"]), basis=basis,
                                availability="AVAILABLE" if prior["netIncome"] > 0 else "UNRELIABLE_COMPARISON",
                                note=None if prior["netIncome"] > 0 else turnaround),
        "eps": fact(eps, source="PROVIDER_DIRECT", basis=basis, note="Diluted EPS."),
        "epsGrowth": fact(yoy(eps, prior["epsDiluted"]), basis=basis,
                          availability="AVAILABLE" if prior["epsDiluted"] > 0 else "UNRELIABLE_COMPARISON",
                          note=None if prior["epsDiluted"] > 0 else turnaround),
        "epsCagr3": fact(cagr(eps, history[3]["epsDiluted"], 3), basis=basis, availability="AVAILABLE" if eps > 0 else "INSUFFICIENT_HISTORY"),
        "epsCagr5": fact(cagr(eps, history[5]["epsDiluted"], 5), basis=basis, availability="AVAILABLE" if eps > 0 else "INSUFFICIENT_HISTORY"),
    }
    gross = latest.get("grossProfit")
    profitability = {
        # Margins are computed from the latest fiscal year, so they carry the annual basis.
        "grossMargin": fact(gross / rev * 100 if gross is not None else None, basis=basis),
        "operatingMargin": fact(latest["operatingIncome"] / rev * 100, basis=basis),
        "netMargin": fact(ni / rev * 100, basis=basis),
        "roe": fact(ni / co["eq"] * 100 if co["eq"] > 0 else None, source="PROVIDER_DIRECT", basis=ttm),
        "roa": fact(ni / latest["totalAssets"] * 100, source="PROVIDER_DIRECT", basis=ttm),
        "roic": fact(latest["operatingIncome"] * 0.79 / (co["eq"] + co["debt"]) * 100 if not co.get("bank") else None, source="PROVIDER_DIRECT", basis=ttm),
    }
    interest = co["debt"] * 0.045
    health = {
        "cash": fact(co["cash"], money=True, basis=sheet), "debt": fact(co["debt"], money=True, basis=sheet),
        "assets": fact(latest["totalAssets"], money=True, basis=sheet), "liabilities": fact(latest["totalLiabilities"], money=True, basis=sheet),
        "equity": fact(co["eq"], money=True, basis=sheet), "netDebt": fact(co["debt"] - co["cash"], money=True, basis=sheet),
        "debtEquity": fact(co["debt"] / co["eq"], source="PROVIDER_DIRECT", basis=ttm),
        "currentRatio": fact(latest["currentAssets"] / latest["currentLiabilities"] if "currentAssets" in latest else None, basis=sheet,
                             note="Banks don't report a current/non-current split." if co.get("bank") else None),
        "quickRatio": fact(latest["currentAssets"] / latest["currentLiabilities"] * 0.8 if "currentAssets" in latest else None, source="PROVIDER_DIRECT", basis=ttm),
        "interestCoverage": fact(latest["operatingIncome"] / interest if not co.get("bank") and latest["operatingIncome"] > 0 else None,
                                 source="PROVIDER_DIRECT", basis=ttm),
        "netDebtEbitda": fact((co["debt"] - co["cash"]) / (latest["operatingIncome"] * 1.15) if latest["operatingIncome"] > 0 else None, source="PROVIDER_DIRECT", basis=ttm),
    }
    fcf = latest["freeCashFlow"]
    cash_flow = {
        "operatingCashFlow": fact(latest["operatingCashFlow"], money=True, basis=basis),
        "capex": fact(latest["capitalExpenditure"], money=True, basis=basis, note="Shown as cash spent."),
        "freeCashFlow": fact(fcf, money=True, basis=basis),
        "fcfMargin": fact(fcf / rev * 100, basis=basis),
    }
    dps = co["dps"]
    if dps is None:   # dividend history unknown
        div_yield = fact(None, availability="MISSING", basis=ttm, note="Dividend history isn't available for this company.")
        div_ps = fact(None, availability="MISSING", basis=ttm)
        payout = fact(None, basis=ttm)
        div_growth = fact(None, availability="MISSING")
    elif dps == 0:
        note = "No dividends paid in the trailing year."
        div_yield = fact(None, availability="NO_DIVIDEND", basis=ttm, note=note)
        div_ps = fact(None, availability="NO_DIVIDEND", basis=ttm, note=note)
        payout = fact(None, basis=ttm)
        div_growth = fact(None, availability="MISSING")
    else:
        dps_listing = dps
        div_yield = fact(dps_listing / price * 100, source="PROVIDER_DIRECT", basis=ttm)
        div_ps = fact(dps_listing, source="PROVIDER_DIRECT", basis=ttm)
        payout = fact(latest.get("dividendsPaid", 0) / ni * 100 if ni > 0 else None, source="PROVIDER_DIRECT", basis=ttm,
                      note=None if ni > 0 else "Not meaningful with non-positive earnings.")
        div_growth = fact(4.2 if not co.get("eps_drop") else 3.1, basis={"period": "calendar year", "fiscalYear": END.year - 1},
                          note="Adjusted dividend totals; includes special payments.")
    shareholder = {
        "dividendYield": div_yield, "dividendPerShare": div_ps, "dividendGrowth": div_growth, "payoutRatio": payout,
        "buybacks": fact(rev * 0.02, money=True, basis=basis, note="Gross common share repurchase spending."),
        "shares": fact(co["sh"], money=True, basis={"period": "point in time", "date": END.isoformat()}, note="Actual shares outstanding; not free float."),
        "sharesChange5": fact(None, note="Exact historical shares aren't verified."),
    }
    ev = mcap_reporting + co["debt"] - co["cash"]
    ebitda = latest["operatingIncome"] * 1.15
    loss_note = "P/E is not meaningful with non-positive trailing earnings."
    valuation = {
        "pe": fact(price_in_reporting / eps if eps > 0 else None, source="PROVIDER_DIRECT", basis=ttm,
                   availability="AVAILABLE" if eps > 0 else "NON_POSITIVE_DENOMINATOR", note=None if eps > 0 else loss_note),
        "peg": fact(None, basis=ttm),
        "priceSales": fact(mcap_reporting / rev, source="PROVIDER_DIRECT", basis=ttm),
        "priceBook": fact(mcap_reporting / co["eq"] if co["eq"] > 0 else None, source="PROVIDER_DIRECT", basis=ttm),
        "priceFcf": fact(mcap_reporting / fcf if fcf > 0 else None, source="PROVIDER_DIRECT", basis=ttm),
        "enterpriseValue": fact(ev, source="PROVIDER_DIRECT", money=True, basis=ttm),
        "evEbitda": fact(ev / ebitda if ebitda > 0 else None, source="PROVIDER_DIRECT", basis=ttm),
        "forwardPe": fact(price_in_reporting / (eps * 1.08) if eps > 0 and not co.get("bank") else None,
                          basis={"period": "next fiscal year estimate", "currency": cur},
                          note="Price / analyst consensus EPS. Forecasts may change."),
    }
    fundamentals = {
        "symbol": sym,
        "financials": {"growth": growth, "profitability": profitability, "financialHealth": health, "cashFlow": cash_flow, "shareholderReturns": shareholder},
        "valuation": {"metrics": valuation, "historical": {}},
        "datasets": {}, "retrievedAt": "2025-03-01T12:00:00Z" if co.get("stale") else RETRIEVED,
        "warnings": ["Sample data for development: these are generated values, not real financial statements."],
        "history": history,
    }
    # Daily closes: deterministic walk ending at the quote's previous close.
    start = END - dt.timedelta(days=240 if co.get("short_history") else 365 * 5 + 2)
    days = list(weekdays(start, END))
    n = len(days)
    drift = {"loss": -0.0006}.get("loss" if co.get("loss") else "", 0.00035)
    closes = []
    for i, d in enumerate(days):
        t = i - (n - 1)
        value = price * math.exp(drift * t + 0.05 * math.sin((i + index * 7) / 23) - 0.05 * math.sin((n - 1 + index * 7) / 23))
        closes.append({"time": d.isoformat(), "close": round(value, 2)})
    previous = closes[-1]["close"]
    change_pct = round(math.sin(index * 1.7) * 1.6, 4)
    last_price = round(previous * (1 + change_pct / 100), 2)
    year = [p["close"] for p in closes[-252:]]
    quote = {"symbol": sym, "companyName": co["n"], "price": last_price, "change": round(last_price - previous, 2),
             "changePercent": change_pct, "dayHigh": round(max(last_price, previous) * 1.006, 2), "dayLow": round(min(last_price, previous) * 0.994, 2),
             "previousClose": previous, "volume": int(1_500_000 + index * 731_000), "timestamp": TIMESTAMP,
             "marketCap": int(last_price * co["sh"]), "open": previous, "yearHigh": max(year + [last_price]), "yearLow": min(year + [last_price])}
    profile = {"symbol": sym, "companyName": co["n"],
               "description": f"Sample profile for development. This is not real information about {co['n']}." +
                              (" " + co["note_currency"] if co.get("note_currency") else ""),
               "sector": co["sec"], "industry": co["ind"], "country": co["c"], "currency": listing_ccy, "exchange": co["ex"],
               "logoUrl": f"https://images.financialmodelingprep.com/symbol/{sym}.png", "isEtf": False}
    folder = os.path.join(ROOT, "stocks", sym)
    os.makedirs(folder, exist_ok=True)
    for name, data in [("profile", profile), ("quote", quote), ("fundamentals-annual", fundamentals), ("chart-daily", closes)]:
        with open(os.path.join(folder, f"{name}.json"), "w") as f:
            json.dump(data, f, indent=2)
            f.write("\n")


def main():
    for i, co in enumerate(COMPANIES):
        generate(co, i + 1)
    os.makedirs(os.path.join(ROOT, "screener"), exist_ok=True)
    universe = {
        "description": "MOCK screener universe: captured US large caps plus generated sample US and Canadian companies (not real data).",
        "symbols": EXISTING + [c["s"] for c in COMPANIES],
    }
    with open(os.path.join(ROOT, "screener", "universe.json"), "w") as f:
        json.dump(universe, f, indent=2)
        f.write("\n")
    manifest_path = os.path.join(ROOT, "manifest.json")
    manifest = json.load(open(manifest_path))
    keep = manifest.get("keepMissing", [])
    for co in COMPANIES:   # generated files are complete by design: never gap-fill them with other samples
        if co["s"] not in keep:
            keep.append(co["s"])
    manifest["keepMissing"] = keep
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
    print(f"Wrote {len(COMPANIES)} companies and a {len(universe['symbols'])}-symbol universe.")


if __name__ == "__main__":
    main()
