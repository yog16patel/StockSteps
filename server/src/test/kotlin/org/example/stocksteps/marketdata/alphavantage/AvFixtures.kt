package org.example.stocksteps.marketdata.alphavantage

/**
 * Phase 9B.7: Alpha Vantage responses in the provider's shapes with **invented values** (no Alpha Vantage data is stored in Git).
 * The daily shapes match the documentation's own sample responses checked on 2026-10-17; the rest follow the documented shapes.
 */
internal object AvFixtures {
    fun daily(symbol: String, rows: List<Pair<String, String>>, adjusted: Boolean = false, events: Map<String, Pair<String, String>> = emptyMap()) = buildString {
        append("""{"Meta Data":{"1. Information":"${if (adjusted) "Daily Time Series with Splits and Dividend Events" else "Daily Prices (open, high, low, close) and Volumes"}",""")
        append(""""2. Symbol":"$symbol","3. Last Refreshed":"${rows.map { it.first }.filter { it.first().isDigit() }.max()}","4. Output Size":"Compact","5. Time Zone":"US/Eastern"},"Time Series (Daily)":{""")
        append(rows.sortedByDescending { it.first }.joinToString(",") { (d, c) ->
            if (adjusted) {
                val (div, split) = events[d] ?: ("0.0000" to "1.0")
                """"$d":{"1. open":"$c","2. high":"$c","3. low":"$c","4. close":"$c","5. adjusted close":"$c","6. volume":"1000","7. dividend amount":"$div","8. split coefficient":"$split"}"""
            } else """"$d":{"1. open":"$c","2. high":"$c","3. low":"$c","4. close":"$c","5. volume":"1000"}"""
        })
        append("}}")
    }

    fun quote(symbol: String, price: String, day: String, prev: String) =
        """{"Global Quote":{"01. symbol":"$symbol","02. open":"$prev","03. high":"$price","04. low":"$prev","05. price":"$price","06. volume":"123456","07. latest trading day":"$day","08. previous close":"$prev","09. change":"1.0000","10. change percent":"1.0000%"}}"""
    const val EMPTY_QUOTE = """{"Global Quote":{}}"""

    const val SEARCH = """{"bestMatches":[
        {"1. symbol":"EXMP","2. name":"Example Corp","3. type":"Equity","4. region":"United States","5. marketOpen":"09:30","6. marketClose":"16:00","7. timezone":"UTC-04","8. currency":"USD","9. matchScore":"1.0000"},
        {"1. symbol":"EXMP.LON","2. name":"Example Corp","3. type":"Equity","4. region":"United Kingdom","5. marketOpen":"08:00","6. marketClose":"16:30","7. timezone":"UTC+01","8. currency":"GBX","9. matchScore":"0.8000"},
        {"1. symbol":"EXMPF","2. name":"Example Fund","3. type":"Mutual Fund","4. region":"United States","5. marketOpen":"09:30","6. marketClose":"16:00","7. timezone":"UTC-04","8. currency":"USD","9. matchScore":"0.5000"},
        {"1. symbol":"EXET","2. name":"Example ETF","3. type":"ETF","4. region":"United States","5. marketOpen":"09:30","6. marketClose":"16:00","7. timezone":"UTC-04","8. currency":"USD","9. matchScore":"0.4000"}]}"""

    const val OVERVIEW = """{"Symbol":"EXMP","AssetType":"Common Stock","Name":"Example Corp","Description":"Example Corp makes examples.","CIK":"1001",
        "Exchange":"NYSE","Currency":"USD","Country":"USA","Sector":"TECHNOLOGY","Industry":"SERVICES-PREPACKAGED SOFTWARE","OfficialSite":"https://example.com",
        "FiscalYearEnd":"December","LatestQuarter":"2026-06-30","MarketCapitalization":"12345000000","SharesOutstanding":"50000000","PERatio":"None"}"""

    const val MOVERS = """{"metadata":"Top gainers, losers, and most actively traded US tickers","last_updated":"2026-10-16 16:15:59 US/Eastern",
        "top_gainers":[{"ticker":"UPUP","price":"2.50","change_amount":"1.00","change_percentage":"66.6667%","volume":"900000"}],
        "top_losers":[{"ticker":"DOWN","price":"1.00","change_amount":"-1.00","change_percentage":"-50.0%","volume":"800000"}],
        "most_actively_traded":[{"ticker":"BUSY","price":"10.00","change_amount":"0.10","change_percentage":"1.0101%","volume":"99000000"}]}"""

    /** Calendar fiscal year; FY2025 + FY2024 annual and three 2026 quarters. Missing values are "None" (never 0). */
    const val INCOME = """{"symbol":"EXMP","annualReports":[
        {"fiscalDateEnding":"2025-12-31","reportedCurrency":"USD","grossProfit":"500000000","totalRevenue":"1200000000","costOfRevenue":"700000000","operatingIncome":"200000000","netIncome":"140000000"},
        {"fiscalDateEnding":"2024-12-31","reportedCurrency":"USD","grossProfit":"None","totalRevenue":"1000000000","costOfRevenue":"600000000","operatingIncome":"150000000","netIncome":"100000000"}],
        "quarterlyReports":[
        {"fiscalDateEnding":"2026-06-30","reportedCurrency":"USD","totalRevenue":"330000000","grossProfit":"140000000","operatingIncome":"60000000","netIncome":"44000000"},
        {"fiscalDateEnding":"2026-03-31","reportedCurrency":"USD","totalRevenue":"320000000","grossProfit":"135000000","operatingIncome":"55000000","netIncome":"42000000"},
        {"fiscalDateEnding":"2025-12-31","reportedCurrency":"USD","totalRevenue":"350000000","grossProfit":"-","operatingIncome":"61000000","netIncome":"35000000"},
        {"fiscalDateEnding":"bad-date","reportedCurrency":"USD","totalRevenue":"1"}]}"""
    const val BALANCE = """{"symbol":"EXMP","annualReports":[{"fiscalDateEnding":"2025-12-31","reportedCurrency":"USD","totalAssets":"1400000000","totalCurrentAssets":"450000000",
        "cashAndCashEquivalentsAtCarryingValue":"120000000","totalLiabilities":"800000000","totalCurrentLiabilities":"300000000","shortLongTermDebtTotal":"330000000","totalShareholderEquity":"600000000"}],
        "quarterlyReports":[]}"""
    const val CASH_FLOW = """{"symbol":"EXMP","annualReports":[{"fiscalDateEnding":"2025-12-31","reportedCurrency":"USD","operatingCashflow":"260000000","capitalExpenditures":"60000000","dividendPayout":"50000000"}],
        "quarterlyReports":[]}"""

    /** Fiscal year ending on the last Saturday of September (52/53-week): quarter ends drift into the next month's first days. */
    const val INCOME_52_53 = """{"symbol":"FRUT","annualReports":[{"fiscalDateEnding":"2025-09-27","reportedCurrency":"USD","totalRevenue":"400"},{"fiscalDateEnding":"2024-09-28","reportedCurrency":"USD","totalRevenue":"390"}],
        "quarterlyReports":[{"fiscalDateEnding":"2026-03-28","reportedCurrency":"USD","totalRevenue":"95"},{"fiscalDateEnding":"2025-12-27","reportedCurrency":"USD","totalRevenue":"124"},
        {"fiscalDateEnding":"2025-09-27","reportedCurrency":"USD","totalRevenue":"102"},{"fiscalDateEnding":"2025-06-28","reportedCurrency":"USD","totalRevenue":"94"}]}"""

    const val EARNINGS = """{"symbol":"EXMP","annualEarnings":[{"fiscalDateEnding":"2025-12-31","reportedEPS":"2.80"}],
        "quarterlyEarnings":[{"fiscalDateEnding":"2026-06-30","reportedDate":"2026-07-28","reportedEPS":"0.88","estimatedEPS":"0.85","surprise":"0.03","surprisePercentage":"3.5294","reportTime":"post-market"},
        {"fiscalDateEnding":"2026-03-31","reportedDate":"2026-04-29","reportedEPS":"None","estimatedEPS":"0.80","surprise":"0","surprisePercentage":"None","reportTime":"pre-market"}]}"""

    const val DIVIDENDS = """{"symbol":"EXMP","data":[{"ex_dividend_date":"2026-08-10","declaration_date":"2026-07-28","record_date":"2026-08-11","payment_date":"2026-09-10","amount":"0.25"},
        {"ex_dividend_date":"2026-05-10","declaration_date":"None","record_date":"None","payment_date":"None","amount":"0.25"},{"ex_dividend_date":"None","amount":"0.25"}]}"""
    const val SPLITS = """{"symbol":"EXMP","data":[{"effective_date":"2026-02-15","split_factor":"4.0000"},{"effective_date":"2010-05-01","split_factor":"0.1000"}]}"""

    // HTTP-200 refusals (wording patterns of Alpha Vantage's answers; the demo one is verbatim from the documentation's sample run).
    const val RATE_LIMIT = """{"Information":"Thank you for using Alpha Vantage! Our standard API rate limit is 25 requests per day. Please subscribe to any of the premium plans to instantly remove all daily rate limits."}"""
    const val NOTE_FREQUENCY = """{"Note":"Thank you for using Alpha Vantage! Our standard API call frequency is 5 calls per minute and 500 calls per day."}"""
    const val PREMIUM = """{"Information":"Thank you for using Alpha Vantage! This is a premium endpoint. You may subscribe to any of the premium plans to instantly unlock all premium endpoints."}"""
    const val DEMO_KEY = """{"Information":"The **demo** API key is for demo purposes only. Please claim your free API key at (https://www.alphavantage.co/support/#api-key) to explore our full API offerings. It takes fewer than 20 seconds."}"""
    const val INVALID_CALL = """{"Error Message":"Invalid API call. Please retry or visit the documentation (https://www.alphavantage.co/documentation/) for TIME_SERIES_DAILY."}"""
    const val UNKNOWN_NOTICE = """{"Information":"Something unexpected happened."}"""
}
