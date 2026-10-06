package org.example.stocksteps.repositoryImpl

import org.example.stocksteps.repository.models.FinnhubNewsArticle
import kotlin.test.*

class CompanyNewsRelevanceTest {
    private fun article(title: String, summary: String? = null, related: String? = "AAPL") =
        FinnhubNewsArticle(title, "https://example.com/article", 1700000000, summary = summary, related = related)
    @Test fun excludesIncidentalCompetitorsAndUnrelatedFeedItems() {
        assertFalse(CompanyNewsRelevance.matches(article("BlackBerry wins new contracts", "Apple forced BlackBerry to pivot."), "AAPL", "Apple Inc."))
        assertFalse(CompanyNewsRelevance.matches(article("Nvidia hits record", "Foxconn sales help Nvidia."), "AAPL", "Apple Inc."))
        assertFalse(CompanyNewsRelevance.matches(article("SpaceX jumps", "The tech industry changes."), "AAPL", "Apple Inc."))
        assertFalse(CompanyNewsRelevance.matches(article("Pineapple market grows"), "AAPL", "Apple Inc."))
    }
    @Test fun retainsCompanyHeadlinesAndExplicitTickerRoundups() {
        assertTrue(CompanyNewsRelevance.matches(article("Apple changes Mac software"), "AAPL", "Apple Inc."))
        assertTrue(CompanyNewsRelevance.matches(article("Microsoft versus Apple"), "AAPL", "Apple Inc."))
        assertTrue(CompanyNewsRelevance.matches(article("Technology stocks", "Apple (AAPL) and Nvidia (NVDA) report earnings."), "AAPL", "Apple Inc."))
        assertTrue(CompanyNewsRelevance.matches(article("AAPL earnings update"), "AAPL", null))
    }
    @Test fun respectsRelatedSymbolsAndDoesNotMatchTickerSubstrings() {
        assertFalse(CompanyNewsRelevance.matches(article("Apple update", related = "NVDA,BB"), "AAPL", "Apple Inc."))
        assertTrue(CompanyNewsRelevance.matches(article("Apple update", related = "NVDA, AAPL"), "AAPL", "Apple Inc."))
        assertFalse(CompanyNewsRelevance.matches(article("XAAPL update", related = null), "AAPL", null))
        assertFalse(CompanyNewsRelevance.matches(article("A technology stock", related = "A"), "A", null))
        assertTrue(CompanyNewsRelevance.matches(article("NYSE:A earnings", related = "A"), "A", null))
    }
    @Test fun missingIdentityIsConservativeAndSupportsOtherCompanies() {
        assertFalse(CompanyNewsRelevance.matches(article("Apple update"), "AAPL", null))
        assertTrue(CompanyNewsRelevance.matches(article("Microsoft earnings", related = "MSFT"), "MSFT", "Microsoft Corporation"))
    }
}
