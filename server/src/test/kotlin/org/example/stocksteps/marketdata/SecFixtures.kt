package org.example.stocksteps.marketdata

/**
 * Phase 9B: deterministic SEC `companyfacts` documents for tests, in the SEC's real shape
 * (`facts.<taxonomy>.<tag>.units.<unit>[] = {start?, end, val, accn, fy, fp, form, filed, frame?}`). Values are invented for tests.
 */
internal object SecFixtures {
    data class F(val tag: String, val v: String, val start: String?, val end: String, val fy: Int, val fp: String, val form: String,
                 val filed: String, val accn: String, val unit: String = "USD")

    fun json(cik: Long, name: String, facts: List<F>, extraRaw: String = ""): String {
        val byTag = facts.groupBy { it.tag }
        val taxonomies = byTag.keys.groupBy { it.substringBefore(':') }
        return buildString {
            append("""{"cik":$cik,"entityName":"$name","facts":{""")
            append(taxonomies.entries.joinToString(",") { (tax, tags) ->
                "\"$tax\":{" + tags.joinToString(",") { tag ->
                    val units = byTag.getValue(tag).groupBy { it.unit }
                    "\"${tag.substringAfter(':')}\":{\"label\":\"x\",\"units\":{" + units.entries.joinToString(",") { (u, fs) ->
                        "\"$u\":[" + fs.joinToString(",") { f ->
                            (if (f.start != null) "{\"start\":\"${f.start}\"," else "{") +
                                "\"end\":\"${f.end}\",\"val\":${f.v},\"accn\":\"${f.accn}\",\"fy\":${f.fy},\"fp\":\"${f.fp}\",\"form\":\"${f.form}\",\"filed\":\"${f.filed}\"}"
                        } + "]"
                    } + "}}"
                } + extraRaw.takeIf { tax == "us-gaap" }.orEmpty() + "}"
            })
            append("}}")
        }
    }

    // ---- "Example Growth Co" (CIK 1001, EXGC): calendar fiscal year, 10-Ks with comparatives, 10-Qs with YTD values, a 10-K/A. ----
    private const val K24 = "0001001-25-000010"; private const val K25 = "0001001-26-000010"; private const val KA25 = "0001001-26-000020"
    private const val Q125 = "0001001-25-000020"; private const val Q225 = "0001001-25-000030"; private const val Q325 = "0001001-25-000040"
    private const val Q126 = "0001001-26-000030"

    private fun flow(tag: String, unit: String = "USD", vararg rows: Triple<String, String, String>, fy: Int, fp: String, form: String, filed: String, accn: String) =
        rows.map { (s, e, v) -> F(tag, v, s, e, fy, fp, form, filed, accn, unit) }

    val growthCo: List<F> = buildList {
        // FY2024 10-K (filed 2025-02-01): FY2024 and FY2023 comparatives.
        val y24 = Triple("2024-01-01", "2024-12-31", ""); val y23 = Triple("2023-01-01", "2023-12-31", "")
        fun k24(tag: String, v24: String, v23: String, unit: String = "USD") = flow(tag, unit, y24.copy(third = v24), y23.copy(third = v23), fy = 2024, fp = "FY", form = "10-K", filed = "2025-02-01", accn = K24)
        addAll(k24("us-gaap:RevenueFromContractWithCustomerExcludingAssessedTax", "1000000000", "800000000"))
        addAll(k24("us-gaap:GrossProfit", "400000000", "300000000"))
        addAll(k24("us-gaap:OperatingIncomeLoss", "150000000", "100000000"))
        addAll(k24("us-gaap:NetIncomeLoss", "100000000", "80000000"))
        addAll(k24("us-gaap:EarningsPerShareDiluted", "2.00", "1.60", "USD/shares"))
        addAll(k24("us-gaap:EarningsPerShareBasic", "2.05", "1.64", "USD/shares"))
        addAll(k24("us-gaap:WeightedAverageNumberOfDilutedSharesOutstanding", "50000000", "50000000", "shares"))
        // FY2025 10-K (filed 2026-02-01): FY2025 + FY2024/FY2023 comparatives (fy = 2025 on all of them).
        val y25 = Triple("2025-01-01", "2025-12-31", "")
        fun k25(tag: String, v25: String, v24: String, v23: String? = null, unit: String = "USD") =
            flow(tag, unit, *listOfNotNull(y25.copy(third = v25), y24.copy(third = v24), v23?.let { y23.copy(third = it) }).toTypedArray(),
                fy = 2025, fp = "FY", form = "10-K", filed = "2026-02-01", accn = K25)
        addAll(k25("us-gaap:RevenueFromContractWithCustomerExcludingAssessedTax", "1200000000", "1000000000", "800000000"))
        addAll(k25("us-gaap:GrossProfit", "500000000", "400000000"))
        addAll(k25("us-gaap:OperatingIncomeLoss", "200000000", "150000000"))
        addAll(k25("us-gaap:NetIncomeLoss", "150000000", "100000000", "80000000"))
        addAll(k25("us-gaap:EarningsPerShareDiluted", "3.00", "2.00", unit = "USD/shares"))
        addAll(k25("us-gaap:EarningsPerShareBasic", "3.05", "2.05", unit = "USD/shares"))
        addAll(k25("us-gaap:WeightedAverageNumberOfDilutedSharesOutstanding", "50000000", "50000000", unit = "shares"))
        addAll(k25("us-gaap:NetCashProvidedByUsedInOperatingActivities", "260000000", "150000000"))
        addAll(k25("us-gaap:PaymentsToAcquirePropertyPlantAndEquipment", "60000000", "50000000"))
        addAll(k25("us-gaap:CommonStockDividendsPerShareDeclared", "1.00", "0.80", unit = "USD/shares"))
        // Balance sheets (instants) at both year ends.
        for ((tag, v25, v24) in listOf(Triple("us-gaap:StockholdersEquity", "600000000", "500000000"), Triple("us-gaap:Assets", "1400000000", "1200000000"),
                Triple("us-gaap:AssetsCurrent", "450000000", "400000000"), Triple("us-gaap:LiabilitiesCurrent", "300000000", "200000000"),
                Triple("us-gaap:LongTermDebt", "300000000", "320000000"), Triple("us-gaap:Liabilities", "800000000", "700000000"))) {
            add(F(tag, v25, null, "2025-12-31", 2025, "FY", "10-K", "2026-02-01", K25)); add(F(tag, v24, null, "2024-12-31", 2025, "FY", "10-K", "2026-02-01", K25))
        }
        add(F("us-gaap:ShortTermBorrowings", "30000000", null, "2025-12-31", 2025, "FY", "10-K", "2026-02-01", K25))
        // 2025 10-Qs: discrete quarters and year-to-date values (the YTD values must never be used as quarters).
        addAll(flow("us-gaap:RevenueFromContractWithCustomerExcludingAssessedTax", "USD", Triple("2025-01-01", "2025-03-31", "250000000"), fy = 2025, fp = "Q1", form = "10-Q", filed = "2025-04-30", accn = Q125))
        addAll(flow("us-gaap:RevenueFromContractWithCustomerExcludingAssessedTax", "USD", Triple("2025-04-01", "2025-06-30", "300000000"), Triple("2025-01-01", "2025-06-30", "550000000"), fy = 2025, fp = "Q2", form = "10-Q", filed = "2025-07-30", accn = Q225))
        addAll(flow("us-gaap:RevenueFromContractWithCustomerExcludingAssessedTax", "USD", Triple("2025-07-01", "2025-09-30", "300000000"), Triple("2025-01-01", "2025-09-30", "850000000"), fy = 2025, fp = "Q3", form = "10-Q", filed = "2025-10-30", accn = Q325))
        addAll(flow("us-gaap:NetIncomeLoss", "USD", Triple("2025-01-01", "2025-03-31", "30000000"), fy = 2025, fp = "Q1", form = "10-Q", filed = "2025-04-30", accn = Q125))
        addAll(flow("us-gaap:NetIncomeLoss", "USD", Triple("2025-04-01", "2025-06-30", "35000000"), Triple("2025-01-01", "2025-06-30", "65000000"), fy = 2025, fp = "Q2", form = "10-Q", filed = "2025-07-30", accn = Q225))
        addAll(flow("us-gaap:NetIncomeLoss", "USD", Triple("2025-07-01", "2025-09-30", "40000000"), Triple("2025-01-01", "2025-09-30", "105000000"), fy = 2025, fp = "Q3", form = "10-Q", filed = "2025-10-30", accn = Q325))
        addAll(flow("us-gaap:EarningsPerShareDiluted", "USD/shares", Triple("2025-01-01", "2025-03-31", "0.60"), fy = 2025, fp = "Q1", form = "10-Q", filed = "2025-04-30", accn = Q125))
        addAll(flow("us-gaap:EarningsPerShareDiluted", "USD/shares", Triple("2025-04-01", "2025-06-30", "0.70"), Triple("2025-01-01", "2025-06-30", "1.30"), fy = 2025, fp = "Q2", form = "10-Q", filed = "2025-07-30", accn = Q225))
        addAll(flow("us-gaap:EarningsPerShareDiluted", "USD/shares", Triple("2025-07-01", "2025-09-30", "0.80"), Triple("2025-01-01", "2025-09-30", "2.10"), fy = 2025, fp = "Q3", form = "10-Q", filed = "2025-10-30", accn = Q325))
        for ((s, e, accn, fp, filed) in listOf(listOf("2025-01-01", "2025-03-31", Q125, "Q1", "2025-04-30"), listOf("2025-04-01", "2025-06-30", Q225, "Q2", "2025-07-30"), listOf("2025-07-01", "2025-09-30", Q325, "Q3", "2025-10-30")))
            add(F("us-gaap:WeightedAverageNumberOfDilutedSharesOutstanding", "50000000", s, e, 2025, fp, "10-Q", filed, accn, "shares"))
        add(F("us-gaap:CommonStockDividendsPerShareDeclared", "0.25", "2025-01-01", "2025-03-31", 2025, "Q1", "10-Q", "2025-04-30", Q125, "USD/shares"))
        add(F("us-gaap:CommonStockDividendsPerShareDeclared", "0.25", "2025-04-01", "2025-06-30", 2025, "Q2", "10-Q", "2025-07-30", Q225, "USD/shares"))
        add(F("us-gaap:CommonStockDividendsPerShareDeclared", "0.25", "2025-07-01", "2025-09-30", 2025, "Q3", "10-Q", "2025-10-30", Q325, "USD/shares"))
        // 10-K/A (filed 2026-03-15) restates FY2025 net income 150 → 140.
        add(F("us-gaap:NetIncomeLoss", "140000000", "2025-01-01", "2025-12-31", 2025, "FY", "10-K/A", "2026-03-15", KA25))
        // Q1 2026 10-Q.
        add(F("us-gaap:RevenueFromContractWithCustomerExcludingAssessedTax", "320000000", "2026-01-01", "2026-03-31", 2026, "Q1", "10-Q", "2026-04-30", Q126))
        add(F("us-gaap:NetIncomeLoss", "42000000", "2026-01-01", "2026-03-31", 2026, "Q1", "10-Q", "2026-04-30", Q126))
        add(F("us-gaap:EarningsPerShareDiluted", "0.85", "2026-01-01", "2026-03-31", 2026, "Q1", "10-Q", "2026-04-30", Q126, "USD/shares"))
        add(F("us-gaap:WeightedAverageNumberOfDilutedSharesOutstanding", "50000000", "2026-01-01", "2026-03-31", 2026, "Q1", "10-Q", "2026-04-30", Q126, "shares"))
        add(F("us-gaap:CommonStockDividendsPerShareDeclared", "0.30", "2026-01-01", "2026-03-31", 2026, "Q1", "10-Q", "2026-04-30", Q126, "USD/shares"))
        // Cover-page shares outstanding.
        add(F("dei:EntityCommonStockSharesOutstanding", "50000000", null, "2026-04-20", 2026, "Q1", "10-Q", "2026-04-30", Q126, "shares"))
        add(F("dei:EntityCommonStockSharesOutstanding", "50000000", null, "2026-01-25", 2025, "FY", "10-K", "2026-02-01", K25, "shares"))
        // Ignored: an 8-K fact and a 6-month YTD of a tag StockSteps doesn't use.
        add(F("us-gaap:NetIncomeLoss", "999", "2025-01-01", "2025-12-31", 2025, "FY", "8-K", "2026-01-20", "0001001-26-000001"))
    }

    // ---- "Example Loss Co" (CIK 1002, EXLC): losses, a revenue tag switch, negative equity, no capex. ----
    val lossCo: List<F> = listOf(
        F("us-gaap:SalesRevenueNet", "800000000", "2024-01-01", "2024-12-31", 2024, "FY", "10-K", "2025-02-10", "0001002-25-000001"),
        F("us-gaap:RevenueFromContractWithCustomerExcludingAssessedTax", "900000000", "2025-01-01", "2025-12-31", 2025, "FY", "10-K", "2026-02-10", "0001002-26-000001"),
        F("us-gaap:NetIncomeLoss", "-50000000", "2024-01-01", "2024-12-31", 2024, "FY", "10-K", "2025-02-10", "0001002-25-000001"),
        F("us-gaap:NetIncomeLoss", "-60000000", "2025-01-01", "2025-12-31", 2025, "FY", "10-K", "2026-02-10", "0001002-26-000001"),
        F("us-gaap:NetIncomeLoss", "-50000000", "2024-01-01", "2024-12-31", 2025, "FY", "10-K", "2026-02-10", "0001002-26-000001"),
        F("us-gaap:EarningsPerShareDiluted", "-1.20", "2025-01-01", "2025-12-31", 2025, "FY", "10-K", "2026-02-10", "0001002-26-000001", "USD/shares"),
        F("us-gaap:EarningsPerShareDiluted", "-1.00", "2024-01-01", "2024-12-31", 2025, "FY", "10-K", "2026-02-10", "0001002-26-000001", "USD/shares"),
        F("us-gaap:StockholdersEquity", "-20000000", null, "2025-12-31", 2025, "FY", "10-K", "2026-02-10", "0001002-26-000001"),
        F("us-gaap:LongTermDebt", "400000000", null, "2025-12-31", 2025, "FY", "10-K", "2026-02-10", "0001002-26-000001"),
        F("us-gaap:NetCashProvidedByUsedInOperatingActivities", "-10000000", "2025-01-01", "2025-12-31", 2025, "FY", "10-K", "2026-02-10", "0001002-26-000001"),
        F("us-gaap:Revenues", "1", "2025-01-01", "2025-12-31", 2025, "FY", "10-K", "2026-02-10", "0001002-26-000001", "EUR"),
        // Two classes on the cover page (same date, same filing): ambiguous.
        F("dei:EntityCommonStockSharesOutstanding", "30000000", null, "2026-02-01", 2025, "FY", "10-K", "2026-02-10", "0001002-26-000001", "shares"),
        F("dei:EntityCommonStockSharesOutstanding", "5000000", null, "2026-02-01", 2025, "FY", "10-K", "2026-02-10", "0001002-26-000001", "shares")
    )

    fun tickersJson(vararg rows: Triple<Long, String, String>) =
        """{"fields":["cik","name","ticker","exchange"],"data":[${rows.joinToString(",") { (c, n, t) -> """[$c,"$n","$t","Nasdaq"]""" }}]}"""
}
