#!/usr/bin/env python3
"""Generates deterministic MOCK earnings events (sample data, not real) for Earnings Intelligence.

Writes server/src/main/resources/fixtures/earnings/events.json (a list of EarningsEvent JSON).

Consistency rules:
- Quarterly revenue and diluted EPS are split from each company's fiscal-year figures in
  stocks/{SYMBOL}/fundamentals-annual.json (so the four quarters of a completed fiscal year add up
  to the annual figure Financials shows). Quarters after the last reported fiscal year continue the
  latest growth rate.
- Estimates are derived from actuals with a fixed per-quarter surprise, except scenario overrides.
- "Price rose / fell after earnings" scenarios pick report dates whose computed reaction (from the
  stocks/{SYMBOL}/chart-daily.json closes the server uses) really rose / fell.
Re-run: python3 scripts/generate_earnings_fixtures.py
"""
import datetime as dt
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "server", "src", "main", "resources", "fixtures")
TODAY = dt.date(2026, 10, 8)
UPDATED = "2026-10-07T21:15:00Z"
SOURCE = "StockSteps sample fixture"
SEASONAL = [0.24, 0.25, 0.25, 0.26]   # share of the fiscal year in Q1..Q4

# symbol: (name, exchange, country, fy_end_month, session, lag_days, quarters_of_history)
COMPANIES = {
    "AAPL": ("Apple Inc.", "NASDAQ", "US", 9, "AFTER_CLOSE", 30, 8),
    "MSFT": ("Microsoft Corporation", "NASDAQ", "US", 6, "AFTER_CLOSE", 27, 8),
    "NVDA": ("NVIDIA Corporation", "NASDAQ", "US", 1, "AFTER_CLOSE", 27, 8),
    "TSLA": ("Tesla Inc", "NASDAQ", "US", 12, "AFTER_CLOSE", 22, 6),
    "TD": ("Toronto-Dominion Bank", "NYSE", "CA", 10, "BEFORE_OPEN", 28, 6),
    "KO": ("Coca-Cola Co", "NYSE", "US", 12, "BEFORE_OPEN", 21, 8),
    "JPM": ("JPMorgan Chase & Co", "NYSE", "US", 12, "BEFORE_OPEN", 14, 8),
    "JNJ": ("Johnson & Johnson", "NYSE", "US", 12, "BEFORE_OPEN", 15, 6),
    "XOM": ("Exxon Mobil Corp", "NYSE", "US", 12, "BEFORE_OPEN", 31, 6),
    "RY.TO": ("Royal Bank of Canada", "TSX", "CA", 10, "BEFORE_OPEN", 27, 6),
    "ENB.TO": ("Enbridge Inc", "TSX", "CA", 12, "BEFORE_OPEN", 38, 6),
    "CNR.TO": ("Canadian National Railway Co", "TSX", "CA", 12, "AFTER_CLOSE", 22, 6),
    "SHOP.TO": ("Shopify Inc", "TSX", "CA", 12, "BEFORE_OPEN", 37, 6),
    "BCE.TO": ("BCE Inc", "TSX", "CA", 12, "BEFORE_OPEN", 37, 6),
    "CSU.TO": ("Constellation Software Inc", "TSX", "CA", 12, "AFTER_CLOSE", 37, 4),
    "BB.TO": ("BlackBerry Ltd", "TSX", "CA", 2, "AFTER_CLOSE", 25, 6),
    "RIVN": ("Rivian Automotive Inc", "NASDAQ", "US", 12, "AFTER_CLOSE", 37, 6),
    # No price history on purpose (manifest keepMissing): price reactions are unavailable; very long name.
    "LONGN": ("Longname Sample Advanced Materials & Renewable Infrastructure Holdings International Corporation", "NASDAQ", "US", 12, "BEFORE_OPEN", 30, 4),
}
# Upcoming-only companies (no fundamentals fixture): (name, exchange, date, session, status, fy, q, eps_est, rev_est)
UPCOMING_ONLY = {
    "AMZN": ("Amazon.com Inc.", "NASDAQ", "2026-10-29", "AFTER_CLOSE", "ESTIMATED", 2026, 3, 1.58, 177.5e9),
    "META": ("Meta Platforms Inc.", "NASDAQ", "2026-10-28", "AFTER_CLOSE", "ESTIMATED", 2026, 3, 6.71, 49.3e9),
    "AMD": ("Advanced Micro Devices Inc.", "NASDAQ", "2026-11-03", "AFTER_CLOSE", "ESTIMATED", 2026, 3, 1.17, 8.7e9),
    "NFLX": ("Netflix Inc.", "NASDAQ", "2026-10-16", "AFTER_CLOSE", "CONFIRMED", 2026, 3, 6.95, 11.5e9),
}

# Earnings Calendar (Phase 1) demo events around the MOCK market date (2026-10-07, New York). Real tickers
# with a stocks/ fixture keep "View Company Details" working; "StockSteps Demo" companies are fictional.
# No logo URLs (avatar fallback). Keys: date, session, fy, q, and optionally status (date status),
# actual=(eps, revenue), source_status, previous, time/zone (only when a "source" states it),
# source_updated=None (source gave no timestamp), exchange/country/name.
CALENDAR_DEMO = [
    dict(symbol="LUCY", name="Innovative Eyewear, Inc.", exchange="NASDAQ", date="2026-10-07", session="BEFORE_OPEN", fy=2026, q=3,
         status="CONFIRMED", eps_est=-0.21, rev_est=0.62e6, actual=(-0.18, 0.66e6)),               # reported today (verified)
    dict(symbol="INTC", name="Intel Corporation", exchange="NASDAQ", date="2026-10-07", session="AFTER_CLOSE", fy=2026, q=3,
         status="CONFIRMED", eps_est=0.18, rev_est=13.6e9, time="16:05", zone="America/New_York"),  # today, after close, exact time
    dict(symbol="CRBU", name="Caribou Biosciences, Inc.", exchange="NASDAQ", date="2026-10-08", session="BEFORE_OPEN", fy=2026, q=3,
         eps_est=-0.42, rev_est=2.4e6),                                                           # tomorrow (several on one day)
    dict(symbol="TOPP", name="Toppoint Holdings Inc.", exchange="AMEX", date="2026-10-08", session="AFTER_CLOSE", fy=2026, q=3,
         eps_est=0.02, rev_est=4.1e6),
    dict(symbol="SPEC", name="Spectaire Holdings Inc.", exchange="OTC", date="2026-10-08", session="UNKNOWN", fy=2026, q=3,
         source_updated=None),                                                                    # unknown time; no source timestamp
    dict(symbol="SSPX", name="StockSteps Demo Postponed Corp.", exchange="NYSE", date="2026-10-09", session="UNKNOWN", fy=2026, q=3,
         source_status="POSTPONED", previous="2026-10-08"),                                       # postponed (explicit source flag)
    dict(symbol="GCDT", name="Green Circle Decarbonize Technology Ltd.", exchange="AMEX", date="2026-10-12", session="BEFORE_OPEN", fy=2026, q=3,
         country="HK"),                                                                           # Canadian Thanksgiving (TSX closed, US open)
    dict(symbol="SSCX", name="StockSteps Demo Canceled Corp.", exchange="NASDAQ", date="2026-10-13", session="AFTER_CLOSE", fy=2026, q=3,
         source_status="CANCELED"),                                                               # canceled (explicit source flag)
    dict(symbol="TD.TO", name="Toronto-Dominion Bank", exchange="TSX", country="CA", date="2026-11-30", session="BEFORE_OPEN", fy=2026, q=4,
         eps_est=2.05, rev_est=14.9e9, currency="CAD", zone="America/Toronto"),                    # same company and ticker as NYSE "TD", other listing
]

# Scenario overrides keyed by (symbol, fiscal_year, quarter).
OVERRIDES = {
    # Upcoming
    ("AAPL", 2026, 4): dict(date="2026-10-29", status="CONFIRMED"),                      # confirmed, after close
    ("MSFT", 2027, 1): dict(date="2026-10-28", status="ESTIMATED"),                      # estimated
    ("TSLA", 2026, 3): dict(date="2026-10-21", status="ESTIMATED", session="UNKNOWN"),   # unknown session
    ("KO", 2026, 3): dict(date="2026-10-21", status="ESTIMATED"),                        # before open, estimated
    ("JPM", 2026, 3): dict(date="2026-10-14", status="CONFIRMED"),                       # before open, confirmed
    ("JNJ", 2026, 3): dict(date="2026-10-14", status="ESTIMATED"),                       # same day as JPM (sorting)
    ("RY.TO", 2026, 4): dict(date="2026-12-03", status="TENTATIVE", session="UNKNOWN", updated="2026-08-20T12:00:00Z"),  # tentative, stale
    ("CNR.TO", 2026, 3): dict(date="2026-10-22", status="CONFIRMED", previous="2026-10-20"),  # date changed
    ("SHOP.TO", 2026, 3): dict(date="2026-11-04", status="ESTIMATED", session="UNKNOWN"),
    # Recent results
    ("BCE.TO", 2026, 3): dict(date="2026-10-06", status="CONFIRMED", pending=True),     # date passed, results not in → pending
    ("RY.TO", 2026, 3): dict(eps=0.04, rev=-0.02),                                       # EPS beat, revenue miss (Canadian)
    ("TD", 2026, 3): dict(eps=-0.03, rev=0.025),                                         # EPS miss, revenue beat; no price history
    ("BB.TO", 2027, 2): dict(eps_est=-0.08, eps_actual=-0.05, no_revenue_actual=True),   # negative EPS, partial results
    ("SHOP.TO", 2026, 2): dict(eps=0.0, rev=0.03),                                       # EPS in line, revenue beat
    ("CSU.TO", 2026, 2): dict(no_eps_estimate=True, est_currency="CAD"),                 # missing EPS estimate; currency mismatch
    ("CNR.TO", 2026, 2): dict(no_revenue_estimate=True),                                 # missing revenue estimate
    ("ENB.TO", 2026, 2): dict(weekday_shift=False, date="2026-08-01", session="UNKNOWN"),  # Saturday announcement
    ("RIVN", 2026, 2): dict(eps_est=0.0, eps_actual=0.03),                               # zero EPS estimate
    ("KO", 2026, 2): dict(eps=-0.04, rev=-0.02),                                         # miss/miss
    ("AAPL", 2026, 3): dict(eps=0.03, rev=-0.012, react="down"),                          # EPS beat, revenue miss; price fell
    ("MSFT", 2026, 4): dict(eps=0.05, rev=0.02, react="up"),                              # beat/beat; price rose
    ("NVDA", 2027, 2): dict(eps=0.06, rev=0.04),                                         # beat/beat (NVDA beats every quarter: streak)
    ("XOM", 2026, 2): dict(eps=0.0, rev=0.0),                                            # in line both
    ("JNJ", 2026, 2): dict(basis_mismatch=True),                                          # adjusted estimate vs GAAP actual
}
# Default surprise pattern (EPS, revenue) by quarter index; NVDA beats every quarter (streak).
PATTERN = [(0.04, 0.012), (-0.03, 0.008), (0.02, -0.011), (0.05, 0.015)]


def fy_quarter_end(fy, q, fy_end_month):
    """End date of fiscal quarter q of fiscal year fy (fiscal year named by the calendar year it ends in)."""
    month = fy_end_month - 3 * (4 - q)
    year = fy
    while month <= 0:
        month += 12
        year -= 1
    nxt = dt.date(year + (month == 12), month % 12 + 1, 1)
    return nxt - dt.timedelta(days=1)


def weekday(d):
    while d.weekday() >= 5:
        d += dt.timedelta(days=1)
    return d


def load(path):
    try:
        return json.load(open(path))
    except FileNotFoundError:
        return None


def closes(symbol):
    rows = load(os.path.join(ROOT, "stocks", symbol, "chart-daily.json")) or []
    return {r["time"][:10]: r["close"] for r in rows}


def reaction(series, date, session):
    days = sorted(series)
    if session == "AFTER_CLOSE":
        base = max((d for d in days if d <= date), default=None)
        end = min((d for d in days if d > date), default=None)
    else:
        base = max((d for d in days if d < date), default=None)
        end = min((d for d in days if d >= date), default=None)
    if not base or not end:
        return None
    return (series[end] / series[base] - 1) * 100


def main():
    events = []
    for symbol, (name, exchange, country, fy_end, session, lag, depth) in COMPANIES.items():
        fundamentals = load(os.path.join(ROOT, "stocks", symbol, "fundamentals-annual.json"))
        annual = {h["fiscalYear"]: h for h in (fundamentals or {}).get("history", []) if h.get("period") == "FY"}
        currency = next(iter(annual.values()))["currency"] if annual else "USD"
        last_fy = max(annual)
        g_rev = annual[last_fy]["revenue"] / annual[last_fy - 1]["revenue"] if last_fy - 1 in annual else 1.05
        g_eps = 1.08
        series = closes(symbol)

        def fy_figures(fy):
            if fy in annual:
                return annual[fy]["revenue"], annual[fy].get("epsDiluted")
            years = fy - last_fy
            return annual[last_fy]["revenue"] * g_rev ** years, (annual[last_fy].get("epsDiluted") or 0.1) * g_eps ** years

        # Quarters: every quarter whose report date is ≤ TODAY (reported), plus the next one (upcoming).
        quarters = []
        fy, q = last_fy - 2, 1
        while True:
            end = fy_quarter_end(fy, q, fy_end)
            report = weekday(end + dt.timedelta(days=lag))
            quarters.append((fy, q, end, report))
            if report > TODAY:
                break
            fy, q = (fy, q + 1) if q < 4 else (fy + 1, 1)
        reported = [x for x in quarters if x[3] <= TODAY][-depth:]
        upcoming = quarters[-1]
        for index, (fy, q, end, report) in enumerate(reported + [upcoming]):
            key = (symbol, fy, q)
            o = OVERRIDES.get(key, {})
            rev_fy, eps_fy = fy_figures(fy)
            revenue = round(rev_fy * SEASONAL[q - 1])
            eps = round((eps_fy or 0) * SEASONAL[q - 1] * 4 / 4, 2)
            if symbol in ("RIVN", "BB.TO"):
                eps = round(-abs(eps) if eps != 0 else -0.1, 2)
            ps, rs = PATTERN[index % 4]
            if symbol == "NVDA":
                ps, rs = abs(ps) + 0.03, abs(rs) + 0.01
            ps, rs = o.get("eps", ps), o.get("rev", rs)
            date = o.get("date") or report.isoformat()
            if o.get("weekday_shift", True):
                date = weekday(dt.date.fromisoformat(date)).isoformat()
            sess = o.get("session", session)
            # Reaction scenarios: move the report date (±3 days) until the computed reaction matches.
            if o.get("react") and series:
                for shift in [0] + [x for n in range(1, 15) for x in (n, -n)]:
                    candidate = weekday(dt.date.fromisoformat(date) + dt.timedelta(days=shift)).isoformat()
                    r = reaction(series, candidate, sess)
                    if r is not None and ((o["react"] == "up" and r > 0.3) or (o["react"] == "down" and r < -0.3)):
                        date = candidate
                        break
                else:
                    raise SystemExit(f"No {o['react']} reaction found near {date} for {symbol}")
            is_upcoming = dt.date.fromisoformat(date) > TODAY or (date == TODAY.isoformat() and sess == "AFTER_CLOSE") or o.get("pending")
            basis = "GAAP_DILUTED"
            eps_est = o.get("eps_est", round(eps / (1 + ps), 2) if eps else 0.0)
            eps_act = o.get("eps_actual", eps)
            estimate = {"eps": None if o.get("no_eps_estimate") else eps_est,
                        "epsBasis": "ADJUSTED" if o.get("basis_mismatch") else basis,
                        "revenue": None if o.get("no_revenue_estimate") else round(revenue / (1 + rs)),
                        "currency": o.get("est_currency", currency), "analysts": 12 + index, "source": SOURCE, "asOf": UPDATED}
            actual = None if is_upcoming else {
                "eps": eps_act, "epsBasis": basis,
                "revenue": None if o.get("no_revenue_actual") else revenue,
                "currency": currency, "source": SOURCE, "reportedAt": f"{date}T{'12:00:00Z' if sess == 'BEFORE_OPEN' else '21:00:00Z'}",
            }
            if actual and o.get("no_revenue_actual"):
                actual.pop("revenue")
            status = o.get("status", "CONFIRMED" if not is_upcoming else "ESTIMATED")
            event = {"id": f"{symbol}:{fy}-Q{q}", "symbol": symbol, "name": name, "exchange": exchange, "country": country,
                     "logoUrl": f"https://images.financialmodelingprep.com/symbol/{symbol}.png",
                     "fiscalYear": fy, "fiscalQuarter": q, "periodEnd": end.isoformat(), "date": date, "session": sess,
                     "dateStatus": status, "estimate": {k: v for k, v in estimate.items() if v is not None},
                     "source": SOURCE, "updatedAt": o.get("updated", UPDATED)}
            if o.get("previous"):
                event["previousDate"] = o["previous"]
            if actual:
                event["actual"] = actual
            events.append(event)
    for symbol, (name, exchange, date, session, status, fy, q, eps_est, rev_est) in UPCOMING_ONLY.items():
        events.append({"id": f"{symbol}:{fy}-Q{q}", "symbol": symbol, "name": name, "exchange": exchange, "country": "US",
                       "logoUrl": f"https://images.financialmodelingprep.com/symbol/{symbol}.png", "fiscalYear": fy, "fiscalQuarter": q,
                       "date": date, "session": session, "dateStatus": status,
                       "estimate": {"eps": eps_est, "epsBasis": "GAAP_DILUTED", "revenue": rev_est, "currency": "USD", "analysts": 30, "source": SOURCE, "asOf": UPDATED},
                       "source": SOURCE, "updatedAt": UPDATED})
    for d in CALENDAR_DEMO:
        currency = d.get("currency", "USD")
        event = {"id": f"{d['symbol']}:{d['fy']}-Q{d['q']}", "symbol": d["symbol"], "name": d["name"], "exchange": d["exchange"],
                 "country": d.get("country", "US"), "fiscalYear": d["fy"], "fiscalQuarter": d["q"], "date": d["date"], "session": d["session"],
                 "dateStatus": d.get("status", "ESTIMATED"), "source": SOURCE, "updatedAt": UPDATED}
        if d.get("eps_est") is not None or d.get("rev_est") is not None:
            event["estimate"] = {k: v for k, v in {"eps": d.get("eps_est"), "epsBasis": "GAAP_DILUTED", "revenue": d.get("rev_est"), "currency": currency,
                                 "analysts": 4, "source": SOURCE, "asOf": UPDATED}.items() if v is not None}
        if d.get("actual"):
            eps, rev = d["actual"]
            event["actual"] = {"eps": eps, "epsBasis": "GAAP_DILUTED", "revenue": rev, "currency": currency, "source": SOURCE,
                               "reportedAt": f"{d['date']}T{'12:00:00Z' if d['session'] == 'BEFORE_OPEN' else '21:00:00Z'}"}
        if d.get("previous"):
            event["previousDate"] = d["previous"]
        if d.get("time"):
            event["eventTime"] = d["time"]
        if d.get("zone"):
            event["timeZone"] = d["zone"]
        if d.get("source_status"):
            event["sourceStatus"] = d["source_status"]
        if d.get("source_updated", UPDATED) is not None:
            event["sourceUpdatedAt"] = d.get("source_updated", UPDATED)
        events.append(event)
    for e in events:
        if "sourceUpdatedAt" not in e and not any(e["symbol"] == d["symbol"] for d in CALENDAR_DEMO):
            e["sourceUpdatedAt"] = e["updatedAt"]
    events.sort(key=lambda e: (e["date"], e["symbol"]))
    os.makedirs(os.path.join(ROOT, "earnings"), exist_ok=True)
    with open(os.path.join(ROOT, "earnings", "events.json"), "w") as f:
        json.dump(events, f, indent=2)
        f.write("\n")
    upcoming = [e for e in events if "actual" not in e and e["date"] >= TODAY.isoformat()]
    print(f"Wrote {len(events)} events ({len(upcoming)} upcoming) for {len(COMPANIES) + len(UPCOMING_ONLY) + len(CALENDAR_DEMO)} companies.")


if __name__ == "__main__":
    main()
