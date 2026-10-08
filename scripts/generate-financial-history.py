#!/usr/bin/env python3
"""Deterministic MOCK financial-statement history for the Financials screen.

Writes `history` (FinancialPeriodStatement rows, newest first) into
server/src/main/resources/fixtures/stocks/{SYMBOL}/fundamentals-{annual,quarter}.json and
rewrites the statement-derived facts (revenue, growth, margins, cash flow, balance sheet) from
the same rows, so every number on Company Details and Financials agrees. Valuation ratios,
dividend yield and other TTM facts already in the fixture are kept.

Sample development data only: values are realistic-looking but are NOT actual filings.
Scenarios: MSFT complete (June FY), AAPL different margins (Sept FY), NVDA fast growth (Jan FY,
quarterly balance sheet "temporarily unavailable", one quarter without net income), TSLA a
net-loss year, a negative-FCF year, no dividend and a missing quarter, LONGN pre-revenue start
(zero denominator), a missing fiscal year, missing net income and too little history for CAGR,
TD reporting in CAD with an October FY and no gross profit/capex (bank).

    python3 scripts/generate-financial-history.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "server/src/main/resources/fixtures"
B = 1e9


def fy_end(year, month, day):
    return f"{year:04d}-{month:02d}-{day:02d}"


def add_months(date, months):
    y, m, d = (int(x) for x in date.split("-"))
    m += months
    while m <= 0:
        m += 12; y -= 1
    while m > 12:
        m -= 12; y += 1
    last = [31, 29 if y % 4 == 0 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][m - 1]
    return f"{y:04d}-{m:02d}-{min(d, last):02d}"


def r(x):
    return None if x is None else round(x)


# Per-company annual specs, oldest → newest. Amounts in billions of the reporting currency.
SPECS = {
    "MSFT": dict(currency="USD", fye=(6, 30), years=list(range(2020, 2026)), shares=7.47,
                 revenue=[143.0, 168.1, 198.3, 211.9, 245.1, 281.7], gm=[.68, .69, .68, .69, .70, .69],
                 om=[.37, .42, .42, .42, .45, .46], nm=[.31, .36, .37, .34, .36, .36],
                 ocf=[60.7, 76.7, 89.0, 87.6, 118.5, 136.2], capex=[15.4, 20.6, 23.9, 28.1, 44.5, 64.6],
                 div=[15.1, 16.5, 18.1, 19.8, 21.8, 24.1], cash=[13.6, 14.2, 13.9, 34.7, 18.3, 30.2],
                 debt=[70.9, 67.8, 61.3, 59.9, 67.1, 60.6], assets=[301, 333, 365, 411, 512, 619],
                 liab=[183, 191, 198, 205, 243, 275], ca=[181, 184, 169, 184, 159, 191], cl=[72, 88, 95, 104, 125, 141]),
    "AAPL": dict(currency="USD", fye=(9, 30), years=list(range(2020, 2026)), shares=[17.5, 16.9, 16.3, 15.8, 15.4, 15.0],
                 revenue=[274.5, 365.8, 394.3, 383.3, 391.0, 416.2], gm=[.38, .42, .43, .44, .46, .47],
                 om=[.24, .30, .30, .30, .32, .32], nm=[.21, .26, .25, .25, .24, .27],
                 ocf=[80.7, 104.0, 122.2, 110.5, 118.3, 121.0], capex=[7.3, 11.1, 10.7, 11.0, 9.4, 12.7],
                 div=[14.1, 14.5, 14.8, 15.0, 15.2, 15.4], cash=[38, 35, 24, 30, 30, 36],
                 debt=[112, 125, 120, 111, 107, 99], assets=[324, 351, 353, 353, 365, 359],
                 liab=[258, 288, 302, 290, 308, 285], ca=[143, 135, 135, 143, 153, 148], cl=[105, 125, 154, 145, 177, 165]),
    # NVIDIA's fiscal year ends in late January of the following calendar year.
    "NVDA": dict(currency="USD", fye=(1, 31), fye_next_year=True, years=list(range(2021, 2027)), shares=24.9,
                 revenue=[16.7, 26.9, 27.0, 60.9, 130.5, 187.1], gm=[.62, .65, .57, .73, .75, .70],
                 om=[.26, .37, .16, .54, .62, .60], nm=[.26, .36, .16, .49, .56, .53],
                 ocf=[5.8, 9.1, 5.6, 28.1, 64.1, 96.0], capex=[1.1, 1.0, 1.8, 1.1, 3.2, 6.0],
                 div=[0.4, 0.4, 0.4, 0.4, 0.8, 1.0], cash=[0.85, 1.99, 3.39, 7.28, 8.59, 11.5],
                 debt=[6.9, 11.8, 11.0, 9.7, 8.5, 8.4], assets=[28.8, 44.2, 41.2, 65.7, 111.6, 140.0],
                 liab=[11.9, 17.6, 19.1, 22.8, 32.3, 38.0], ca=[16.1, 28.8, 23.1, 44.3, 80.1, 102.0], cl=[3.9, 4.3, 6.6, 10.6, 18.0, 22.0],
                 quarter_balance_unavailable=True, quarter_missing_net_income=3),
    "TSLA": dict(currency="USD", fye=(12, 31), years=list(range(2020, 2026)), shares=3.48,
                 revenue=[31.5, 53.8, 81.5, 93.0, 96.83, 97.7], gm=[.21, .25, .26, .18, .18, .179],
                 om=[.06, .12, .17, .09, .07, .072], net=[-0.86, 5.5, 12.6, 15.0, 7.9, 7.1],
                 ocf=[5.9, 11.5, 14.7, 13.3, 13.0, 14.9], capex=[3.2, 6.5, 7.2, 8.9, 14.1, 11.3],
                 div=None, cash=[19.4, 17.6, 16.3, 16.4, 16.1, 16.0], debt=[13.3, 8.9, 5.7, 9.6, 13.6, 13.0],
                 assets=[52, 62, 82, 106, 122, 128], liab=[28, 30, 36, 43, 48, 50],
                 ca=[26.7, 27.1, 40.9, 49.6, 58.4, 60.0], cl=[14.2, 19.7, 26.7, 28.7, 28.8, 30.0], quarter_missing=2),
    # Sparse on purpose: pre-revenue first year, FY2023 missing, FY2024 net income not reported.
    "LONGN": dict(currency="USD", fye=(12, 31), years=[2021, 2022, 2024, 2025], shares=0.68,
                  revenue=[0.0, 0.4, 1.6, 2.1], gm=[None, .31, .34, .36], om=[None, -1.2, .05, .09],
                  net=[-0.9, -0.6, None, 0.05], ocf=[-0.7, -0.5, 0.12, 0.21], capex=[0.2, 0.3, 0.25, 0.15],
                  div=None, cash=[1.4, 0.9, None, 0.6], debt=[0.0, 0.5, None, 0.8], assets=[2.1, 2.4, None, 3.1],
                  liab=[0.4, 1.0, None, 1.6], ca=[1.6, 1.4, None, 1.2], cl=[0.3, 0.6, None, 0.7], no_quarters=True),
    # Canadian bank: CAD, fiscal year ends October 31; banks report no gross profit or capex.
    "TD": dict(currency="CAD", fye=(10, 31), years=list(range(2020, 2026)), shares=1.82,
               revenue=[43.6, 42.7, 49.0, 50.8, 55.6, 59.0], gm=None, om=[.36, .41, .44, .29, .24, .33],
               nm=[.27, .34, .36, .21, .16, .25], ocf=None, capex=None,
               div=[5.6, 5.8, 6.4, 7.1, 7.4, 7.6], cash=[6.9, 5.9, 8.6, 6.6, 6.4, 7.0], debt=None,
               assets=[1716, 1729, 1918, 1955, 2062, 2100], liab=[1621, 1630, 1806, 1843, 1950, 1985], ca=None, cl=None),
}


def series(spec, key, i):
    values = spec.get(key)
    return None if values is None else values[i]


def annual_rows(symbol, spec):
    rows = []
    for i, year in enumerate(spec["years"]):
        month, day = spec["fye"]
        end_year = year if not spec.get("fye_next_year") else year
        date = fy_end(end_year, month, day)
        revenue = spec["revenue"][i]
        net = series(spec, "net", i) if "net" in spec else (revenue * spec["nm"][i])
        gm, om = series(spec, "gm", i), series(spec, "om", i)
        shares = spec["shares"][i] if isinstance(spec["shares"], list) else spec["shares"]
        ocf, capex = series(spec, "ocf", i), series(spec, "capex", i)
        assets, liab = series(spec, "assets", i), series(spec, "liab", i)
        rows.append(dict(
            period="FY", fiscalYear=year, date=date, currency=spec["currency"],
            revenue=r(revenue * B), grossProfit=r(revenue * gm * B) if gm is not None else None,
            operatingIncome=r(revenue * om * B) if om is not None else None,
            netIncome=r(net * B) if net is not None else None,
            epsDiluted=round(net / shares, 2) if net is not None else None,
            operatingCashFlow=r(ocf * B) if ocf is not None else None,
            capitalExpenditure=r(capex * B) if capex is not None else None,
            freeCashFlow=r((ocf - capex) * B) if ocf is not None and capex is not None else None,
            dividendsPaid=r(series(spec, "div", i) * B) if series(spec, "div", i) is not None else None,
            cash=r(series(spec, "cash", i) * B) if series(spec, "cash", i) is not None else None,
            totalDebt=r(series(spec, "debt", i) * B) if series(spec, "debt", i) is not None else None,
            totalAssets=r(assets * B) if assets is not None else None,
            totalLiabilities=r(liab * B) if liab is not None else None,
            equity=r((assets - liab) * B) if assets is not None and liab is not None else None,
            currentAssets=r(series(spec, "ca", i) * B) if series(spec, "ca", i) is not None else None,
            currentLiabilities=r(series(spec, "cl", i) * B) if series(spec, "cl", i) is not None else None,
        ))
    return [{k: v for k, v in row.items() if v is not None} for row in reversed(rows)]


SEASON = [0.23, 0.24, 0.25, 0.28]  # share of the fiscal year's flows per quarter (Q1..Q4)
FLOWS = ["revenue", "grossProfit", "operatingIncome", "netIncome", "operatingCashFlow", "capitalExpenditure", "dividendsPaid"]
BALANCE = ["cash", "totalDebt", "totalAssets", "totalLiabilities", "equity", "currentAssets", "currentLiabilities"]


def quarter_rows(symbol, spec, annual):
    """Eight fiscal quarters from the two latest fiscal years; flows split by season, balances interpolated."""
    newest, prior, older = annual[0], annual[1], annual[2] if len(annual) > 2 else annual[1]
    shares_list = spec["shares"] if isinstance(spec["shares"], list) else None
    rows = []
    for fy, previous in ((newest, prior), (prior, older)):
        for q in range(4):
            date = add_months(fy["date"], -3 * (3 - q))
            row = dict(period=f"Q{q + 1}", fiscalYear=fy["fiscalYear"], date=date, currency=fy["currency"])
            for key in FLOWS:
                if key in fy:
                    row[key] = r(fy[key] * SEASON[q] * (1 + 0.02 * ((q + fy["fiscalYear"]) % 3 - 1)))
            if "operatingCashFlow" in row and "capitalExpenditure" in row:
                row["freeCashFlow"] = row["operatingCashFlow"] - row["capitalExpenditure"]
            if "netIncome" in row:
                shares = shares_list[-1] if shares_list else spec["shares"]
                row["epsDiluted"] = round(row["netIncome"] / B / shares, 2)
            weight = (q + 1) / 4
            for key in BALANCE:
                if key in fy and key in previous:
                    row[key] = r(previous[key] + (fy[key] - previous[key]) * weight)
            rows.append(row)
    rows.sort(key=lambda x: x["date"], reverse=True)
    if spec.get("quarter_balance_unavailable"):
        for row in rows:
            for key in BALANCE:
                row.pop(key, None)
    if spec.get("quarter_missing_net_income") is not None:
        row = rows[spec["quarter_missing_net_income"]]
        row.pop("netIncome", None); row.pop("epsDiluted", None)
    if spec.get("quarter_missing") is not None:
        rows.pop(spec["quarter_missing"])
    return rows


def fact(value=None, amount=None, basis=None, availability=None, source="PROVIDER_DIRECT"):
    available = value is not None or amount is not None
    out = {"source": source if available else "NOT_AVAILABLE",
           "availability": availability or ("AVAILABLE" if available else "MISSING")}
    if value is not None: out["value"] = round(value, 4)
    if amount is not None: out["amount"] = int(amount)
    if basis: out["basis"] = basis
    return out


def growth(current, previous):
    if current is None or previous is None or previous <= 0 or current < 0:
        return None
    return (current - previous) / previous * 100


def signed_growth(current, previous):
    if current is None or previous is None or previous <= 0:
        return None
    return (current - previous) / previous * 100


def cagr(end, start, years):
    if end is None or start is None or years <= 0 or end <= 0 or start <= 0:
        return None
    return ((end / start) ** (1 / years) - 1) * 100


def margin(amount, revenue):
    return None if amount is None or not revenue or revenue <= 0 else amount / revenue * 100


def statement_facts(history, period_name):
    latest = history[0]
    previous = next((h for h in history if h["period"] == latest["period"] and h.get("fiscalYear") == latest["fiscalYear"] - 1), None)
    basis = {"period": period_name if latest["period"] == "FY" else latest["period"], "date": latest["date"],
             "fiscalYear": latest["fiscalYear"], "currency": latest["currency"]}
    g = lambda key: latest.get(key)
    p = lambda key: previous.get(key) if previous else None
    calc = "BACKEND_CALCULATED"
    growth_facts = {
        "revenue": fact(amount=g("revenue"), basis=basis),
        "revenueGrowth": fact(growth(g("revenue"), p("revenue")), basis=basis, source=calc),
        "netIncome": fact(amount=g("netIncome"), basis=basis),
        "netIncomeGrowth": fact(signed_growth(g("netIncome"), p("netIncome")), basis=basis, source=calc),
        "eps": fact(g("epsDiluted"), basis=basis),
        "epsGrowth": fact(signed_growth(g("epsDiluted"), p("epsDiluted")), basis=basis, source=calc),
    }
    if latest["period"] == "FY":
        for n in (3, 5):
            start = next((h for h in history if h.get("fiscalYear") == latest["fiscalYear"] - n), None)
            reason = "INSUFFICIENT_HISTORY"
            for key, field in (("revenueCagr%d" % n, "revenue"), ("epsCagr%d" % n, "epsDiluted")):
                value = cagr(g(field), start.get(field) if start else None, n)
                growth_facts[key] = fact(value, basis=basis, source=calc, availability=None if value is not None else reason)
    profitability = {k: fact(margin(g(f), g("revenue")), basis=basis, source=calc)
                     for k, f in (("grossMargin", "grossProfit"), ("operatingMargin", "operatingIncome"), ("netMargin", "netIncome"))}
    health = {
        "cash": fact(amount=g("cash"), basis=basis), "debt": fact(amount=g("totalDebt"), basis=basis),
        "assets": fact(amount=g("totalAssets"), basis=basis), "liabilities": fact(amount=g("totalLiabilities"), basis=basis),
        "equity": fact(amount=g("equity"), basis=basis),
        "currentRatio": fact(g("currentAssets") / g("currentLiabilities") if g("currentAssets") and g("currentLiabilities") else None, basis=basis, source=calc),
        "debtEquity": fact(g("totalDebt") / g("equity") if g("totalDebt") is not None and g("equity") and g("equity") > 0 else None, basis=basis, source=calc),
    }
    cash_flow = {
        "operatingCashFlow": fact(amount=g("operatingCashFlow"), basis=basis),
        "capex": fact(amount=g("capitalExpenditure"), basis=basis),
        "freeCashFlow": fact(amount=g("freeCashFlow"), basis=basis, source=calc),
        "fcfMargin": fact(margin(g("freeCashFlow"), g("revenue")), basis=basis, source=calc),
    }
    return growth_facts, profitability, health, cash_flow


def write(symbol, period_file, history, unavailable=()):
    path = ROOT / f"stocks/{symbol}/fundamentals-{period_file}.json"
    data = json.loads(path.read_text()) if path.exists() else {"symbol": symbol}
    data["symbol"] = symbol
    financials = data.setdefault("financials", {})
    growth_facts, profitability, health, cash_flow = statement_facts(history, "annual" if period_file == "annual" else "quarter")
    for category, values in (("growth", growth_facts), ("profitability", profitability), ("financialHealth", health), ("cashFlow", cash_flow)):
        merged = financials.setdefault(category, {})
        for key, value in values.items():
            if category == "financialHealth" and "balance" in unavailable:
                value = fact(availability="TEMPORARILY_UNAVAILABLE")
            merged[key] = value
    data.setdefault("valuation", {"metrics": {}, "historical": {}})
    data["datasets"] = {"income": "AVAILABLE", "cashFlow": "AVAILABLE",
                        "balance": "TEMPORARILY_UNAVAILABLE" if "balance" in unavailable else "AVAILABLE"}
    data["warnings"] = ["Sample development data; not actual company filings."]
    data["history"] = history
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=4) + "\n")
    print("saved", path.relative_to(ROOT))


def ensure_td():
    """TD sample: trades on the NYSE in USD but reports its statements in CAD (October fiscal year)."""
    base = ROOT / "stocks/TD"
    base.mkdir(parents=True, exist_ok=True)
    (base / "profile.json").write_text(json.dumps({
        "symbol": "TD", "companyName": "Toronto-Dominion Bank",
        "description": "Sample profile for development: a Canadian bank offering personal, commercial and wealth services. Values are not actual filings.",
        "sector": "Financial Services", "industry": "Banks - Diversified", "country": "CA", "currency": "USD", "exchange": "NYSE",
        "logoUrl": "https://images.financialmodelingprep.com/symbol/TD.png"}, indent=4) + "\n")
    (base / "quote.json").write_text(json.dumps({
        "symbol": "TD", "companyName": "Toronto-Dominion Bank", "price": 60.12, "change": 0.47, "changePercent": 0.7879,
        "dayHigh": 60.45, "dayLow": 59.42, "previousClose": 59.65, "volume": 2310000, "timestamp": 1791403200,
        "marketCap": 105200000000, "open": 59.71, "yearHigh": 64.7, "yearLow": 53.6}, indent=4) + "\n")
    search = ROOT / "stocks/search.json"
    results = json.loads(search.read_text())
    results = [item for item in results if item["symbol"] != "TD"]
    results.insert(0, {"symbol": "TD", "name": "Toronto-Dominion Bank", "currency": "USD", "exchange": "NYSE", "exchangeFullName": "New York Stock Exchange"})
    search.write_text(json.dumps(results, indent=2) + "\n")


def td_returns(period_file):
    path = ROOT / f"stocks/TD/fundamentals-{period_file}.json"
    data = json.loads(path.read_text())
    ttm = {"period": "TTM", "currency": "CAD"}
    data["financials"]["shareholderReturns"] = {
        "dividendYield": fact(4.97, basis=ttm), "dividendPerShare": fact(4.08, basis=ttm), "payoutRatio": fact(52.4, basis=ttm)}
    data["valuation"] = {"metrics": {"pe": fact(10.6, basis=ttm), "priceSales": fact(2.4, basis=ttm)}, "historical": {}}
    path.write_text(json.dumps(data, indent=4) + "\n")


def tsla_no_dividend(period_file):
    path = ROOT / f"stocks/TSLA/fundamentals-{period_file}.json"
    data = json.loads(path.read_text())
    data["financials"].setdefault("shareholderReturns", {})["dividendYield"] = fact(availability="NO_DIVIDEND", basis={"period": "TTM", "currency": "USD"})
    path.write_text(json.dumps(data, indent=4) + "\n")


def consistent_pe(symbol):
    """P/E = quote price ÷ latest annual diluted EPS, so the sample ratios agree with the sample EPS."""
    quote_path = ROOT / f"stocks/{symbol}/quote.json"
    if not quote_path.exists():
        return
    price = json.loads(quote_path.read_text()).get("price")
    for period_file in ("annual", "quarter"):
        path = ROOT / f"stocks/{symbol}/fundamentals-{period_file}.json"
        if not path.exists():
            continue
        data = json.loads(path.read_text())
        annual = json.loads((ROOT / f"stocks/{symbol}/fundamentals-annual.json").read_text())["history"][0]
        eps = annual.get("epsDiluted")
        metrics = data["valuation"].setdefault("metrics", {})
        if annual["currency"] != "USD":
            pass  # price (USD) and EPS (CAD) differ in currency; keep the provider-style TTM ratio
        elif price and eps and eps > 0:
            pe = price / eps
            metrics["pe"] = fact(pe, basis={"period": "TTM", "currency": annual["currency"]})
            history = data["valuation"].get("historical", {}).get("pe")
            if history and history.get("average"):
                history["differencePercent"] = round((pe / history["average"] - 1) * 100, 2)
        elif eps is not None and eps <= 0:
            metrics["pe"] = fact(availability="NON_POSITIVE_DENOMINATOR")
        path.write_text(json.dumps(data, indent=4) + "\n")


if __name__ == "__main__":
    ensure_td()
    for symbol, spec in SPECS.items():
        annual = annual_rows(symbol, spec)
        write(symbol, "annual", annual)
        if not spec.get("no_quarters"):
            write(symbol, "quarter", quarter_rows(symbol, spec, annual),
                  unavailable=("balance",) if spec.get("quarter_balance_unavailable") else ())
    for period_file in ("annual", "quarter"):
        td_returns(period_file)
        tsla_no_dividend(period_file)
    for symbol in SPECS:
        consistent_pe(symbol)
