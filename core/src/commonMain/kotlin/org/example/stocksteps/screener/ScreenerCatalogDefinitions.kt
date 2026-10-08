package org.example.stocksteps.screener

/**
 * The single list of screenable/comparable metrics and the beginner presets. Thresholds live only
 * here (server evaluates them; apps display them). Presets are screening shortcuts, not
 * recommendations: they describe characteristics, never "good", "safe" or "cheap" companies.
 */
object ScreenerDefinitions {
    private const val TTM = "Trailing twelve months"
    private const val FY = "Latest fiscal year vs the prior fiscal year"
    private const val LATEST = "Latest reported balance sheet"
    private const val EXCLUDED = "Companies without a valid value are left out of results that filter on it."
    private val FINANCIALS = listOf("Financial Services")

    val metrics: List<MetricDefinition> = listOf(
        MetricDefinition("marketCap", "Market cap (USD)", MetricGroup.MARKET, MetricUnit.MONEY, "Latest quote", EXCLUDED, 1e9, 3e12,
            currencyNote = "Converted to USD at the latest Bank of Canada rate so CAD and USD listings are comparable."),
        MetricDefinition("price", "Share price", MetricGroup.MARKET, MetricUnit.PRICE, "Latest quote", EXCLUDED, 1.0, 1000.0,
            currencyNote = "In each listing's trading currency."),
        MetricDefinition("volume", "Trading volume", MetricGroup.MARKET, MetricUnit.COUNT, "Latest session", EXCLUDED, 100_000.0, 50_000_000.0),
        MetricDefinition("yearRangePosition", "52-week range position", MetricGroup.MARKET, MetricUnit.PERCENT, "Last 52 weeks", EXCLUDED, 0.0, 100.0),
        MetricDefinition("pe", "P/E ratio (trailing)", MetricGroup.VALUATION, MetricUnit.MULTIPLE, TTM,
            "Not meaningful when trailing earnings are zero or negative; those companies are left out of P/E filters.", 5.0, 40.0),
        MetricDefinition("forwardPe", "Forward P/E (estimate)", MetricGroup.VALUATION, MetricUnit.MULTIPLE, "Next fiscal year analyst estimate",
            "Shown only when analyst estimates exist; never mixed with trailing P/E.", filterable = false),
        MetricDefinition("priceSales", "Price / sales", MetricGroup.VALUATION, MetricUnit.MULTIPLE, TTM, EXCLUDED, 0.5, 15.0),
        MetricDefinition("priceBook", "Price / book", MetricGroup.VALUATION, MetricUnit.MULTIPLE, TTM, "Not meaningful with zero or negative equity.", 0.5, 10.0),
        MetricDefinition("evEbitda", "EV / EBITDA", MetricGroup.VALUATION, MetricUnit.MULTIPLE, TTM, "Not meaningful with zero or negative EBITDA.", 4.0, 30.0),
        MetricDefinition("dividendYield", "Dividend yield", MetricGroup.VALUATION, MetricUnit.PERCENT, TTM,
            "Companies that paid no dividend in the trailing year count as 0%; unknown dividend history is left out.", 0.0, 8.0),
        MetricDefinition("revenueGrowth", "Revenue growth", MetricGroup.GROWTH, MetricUnit.PERCENT, FY, EXCLUDED, -10.0, 50.0),
        MetricDefinition("epsGrowth", "EPS growth", MetricGroup.GROWTH, MetricUnit.PERCENT, FY,
            "Not calculated when prior EPS was zero or negative (a turnaround isn't a percentage); those companies are left out.", -20.0, 60.0),
        MetricDefinition("netIncomeGrowth", "Net income growth", MetricGroup.GROWTH, MetricUnit.PERCENT, FY,
            "Not calculated when prior net income was zero or negative.", -20.0, 60.0),
        MetricDefinition("fcfGrowth", "Free cash flow growth", MetricGroup.GROWTH, MetricUnit.PERCENT, FY,
            "Not calculated when prior free cash flow was zero or negative, or currencies differ.", -20.0, 60.0),
        MetricDefinition("grossMargin", "Gross margin", MetricGroup.PROFITABILITY, MetricUnit.PERCENT, TTM, EXCLUDED, 0.0, 90.0),
        MetricDefinition("operatingMargin", "Operating margin", MetricGroup.PROFITABILITY, MetricUnit.PERCENT, TTM, EXCLUDED, -10.0, 60.0),
        MetricDefinition("netMargin", "Net margin", MetricGroup.PROFITABILITY, MetricUnit.PERCENT, TTM, EXCLUDED, -10.0, 50.0),
        MetricDefinition("roe", "Return on equity", MetricGroup.PROFITABILITY, MetricUnit.PERCENT, TTM, "Distorted by very small or negative equity.", 0.0, 60.0),
        MetricDefinition("roic", "Return on invested capital", MetricGroup.PROFITABILITY, MetricUnit.PERCENT, TTM, "Provider definition; left out when unavailable.", 0.0, 50.0),
        MetricDefinition("debtEquity", "Debt / equity", MetricGroup.HEALTH, MetricUnit.RATIO, TTM,
            "Not applied to banks and insurers, whose balance sheets aren't comparable; left out with negative equity.", 0.0, 3.0, FINANCIALS),
        MetricDefinition("currentRatio", "Current ratio", MetricGroup.HEALTH, MetricUnit.RATIO, LATEST,
            "Not applied to banks and insurers (no current/non-current split).", 0.5, 3.0, FINANCIALS),
        MetricDefinition("interestCoverage", "Interest coverage", MetricGroup.HEALTH, MetricUnit.MULTIPLE, TTM,
            "Not applied to banks and insurers; unavailable when interest expense is zero.", 1.0, 30.0, FINANCIALS),
        MetricDefinition("operatingCashFlow", "Operating cash flow", MetricGroup.HEALTH, MetricUnit.MONEY, "Latest fiscal year", EXCLUDED,
            currencyNote = "In each company's reporting currency."),
        MetricDefinition("freeCashFlow", "Free cash flow", MetricGroup.HEALTH, MetricUnit.MONEY, "Latest fiscal year", EXCLUDED,
            currencyNote = "In each company's reporting currency."),
        MetricDefinition("cash", "Cash", MetricGroup.HEALTH, MetricUnit.MONEY, LATEST, EXCLUDED, filterable = false, currencyNote = "Reporting currency."),
        MetricDefinition("debt", "Total debt", MetricGroup.HEALTH, MetricUnit.MONEY, LATEST, EXCLUDED, filterable = false, currencyNote = "Reporting currency."),
        MetricDefinition("payoutRatio", "Payout ratio", MetricGroup.SHAREHOLDER, MetricUnit.PERCENT, TTM,
            "Not meaningful when earnings are zero or negative.", 0.0, 100.0),
        MetricDefinition("dividendGrowth", "Dividend growth", MetricGroup.SHAREHOLDER, MetricUnit.PERCENT, "Last completed calendar year vs the year before",
            "Needs complete dividend history for both years.", filterable = false)
    )
    private val byId = metrics.associateBy { it.id }
    fun metric(id: String): MetricDefinition? = byId[id]

    const val GROWING = "growing"
    const val DIVIDEND = "dividend"
    const val STRONG = "strong"
    const val VALUATION = "valuation"

    val presets: List<ScreenerPreset> = listOf(
        ScreenerPreset(GROWING, "Growing Companies", "Revenue and EPS grew at least 5% in the latest fiscal year, with market cap of at least $2B.",
            listOf(RangeFilter("revenueGrowth", min = 5.0), RangeFilter("epsGrowth", min = 5.0), RangeFilter("marketCap", min = 2e9)),
            ScreenerSort(SortField.MARKET_CAP), listOf("revenueGrowth", "epsGrowth", "netMargin"), FY,
            "Companies whose prior EPS was zero or negative have no EPS growth percentage and aren't included.",
            listOf("Past growth doesn't mean growth will continue.")),
        ScreenerPreset(DIVIDEND, "Dividend Stocks", "Paid dividends in the trailing year with a yield of at least 1.5% and a payout ratio of at most 100% of earnings.",
            listOf(RangeFilter("dividendYield", min = 1.5), RangeFilter("payoutRatio", max = 100.0)),
            ScreenerSort(SortField.MARKET_CAP), listOf("dividendYield", "payoutRatio", "dividendGrowth"), TTM,
            "Companies with unknown dividend history or a payout ratio that can't be calculated are left out.",
            listOf("Dividends aren't guaranteed; companies can reduce or stop them.")),
        ScreenerPreset(STRONG, "Financially Strong", "Positive operating cash flow and net margin, debt/equity at most 1.5, interest coverage at least 3× and a current ratio of at least 1.",
            listOf(RangeFilter("operatingCashFlow", min = 1.0), RangeFilter("netMargin", min = 0.1), RangeFilter("debtEquity", max = 1.5),
                RangeFilter("interestCoverage", min = 3.0), RangeFilter("currentRatio", min = 1.0)),
            ScreenerSort(SortField.MARKET_CAP), listOf("netMargin", "debtEquity", "currentRatio"), "$TTM; latest balance sheet",
            "Debt/equity, interest coverage and current ratio aren't applied to banks and insurers; other missing values are left out.",
            listOf("These are transparent thresholds, not a measure of safety.")),
        ScreenerPreset(VALUATION, "Explore Valuations", "Trailing P/E between 5 and 25, price/sales at most 5 and EV/EBITDA at most 15.",
            listOf(RangeFilter("pe", 5.0, 25.0), RangeFilter("priceSales", max = 5.0), RangeFilter("evEbitda", max = 15.0)),
            ScreenerSort(SortField.MARKET_CAP), listOf("pe", "priceSales", "evEbitda"), TTM,
            "Companies with zero or negative earnings or EBITDA have no meaningful ratio and are left out.",
            listOf("A low ratio isn't proof that a stock is undervalued; it can reflect real risks or slowing growth."))
    )
    fun preset(id: String) = presets.firstOrNull { it.id == id }

    /** Metrics shown on custom-screen rows: the first filtered metrics, else a neutral default. */
    fun displayMetricsFor(query: ScreenerQuery): List<String> =
        query.presetId?.let { preset(it) }?.takeIf { p -> p.ranges == query.ranges }?.displayMetrics
            ?: query.ranges.map { it.metric }.filter { it != "marketCap" }.distinct().take(3).ifEmpty { listOf("pe", "revenueGrowth", "netMargin") }
}
