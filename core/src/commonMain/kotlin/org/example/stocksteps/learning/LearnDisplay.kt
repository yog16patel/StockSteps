package org.example.stocksteps.learning

import org.example.stocksteps.brief.BriefContent

/** One Learn topic: the catalogue's `topic` value and its terms, in catalogue order. */
data class LearnTopic(val name: String, val terms: List<EducationEntry>)

/** A section of a term's reading page (title + the catalogue's own text). */
data class LearnSection(val title: String, val body: String)

/**
 * Learn screens' copy and display rules (Global UI Refinement Phase 5D.5.2), shared by Compose and SwiftUI. Display only: topics come from
 * [BeginnerEducation]'s `topic` field, names and summaries are derived from existing titles, and no educational text is added or reworded.
 */
object LearnDisplay {
    const val TITLE = "Learn"
    const val INTRO = "Build your investing knowledge with simple explanations and real-world examples."
    const val START_HERE = "Start here"
    const val START_TITLE = "Understand a stock in five steps"
    const val START_BODY = "What it does, whether it's growing, whether it makes money, how much debt it has, and whether the stock looks expensive."
    const val START_ACTION = "Start learning"
    const val CONTINUE_ACTION = "Continue learning"
    const val EXPLORE = "Explore topics"
    const val SEE_ALL = "See all"
    const val TODAY = "Today's concept"
    const val CONTINUE = "Continue learning"
    const val MORE_WAYS = "More ways to learn"
    const val RESEARCH_ANY = "Research any company"
    const val RESEARCH_ANY_DETAIL = "Search, then choose \"Understand This Stock\""
    const val PRACTICE = "Practice with virtual money"
    const val PRACTICE_DETAIL = "Simulated investments and guided challenges"
    const val COMPLETED = "Completed research"
    const val TOPICS_TITLE = "Explore topics"
    const val TOPICS_INTRO = "Browse all investing topics and find what you want to learn next."
    const val SEARCH_LABEL = "Search terms"
    const val SEARCH_ACTION = "Search investing terms"
    const val IN_THIS_LESSON = "In this lesson"
    const val NEXT = "Next"
    const val ALL_TOPICS = "Explore all topics"
    const val SYNC_NOTE = "Your progress is saved on this device and will sync with your account when the connection is back."

    /** Topics in catalogue order (first appearance), each with its terms. */
    fun topics(): List<LearnTopic> = BeginnerEducation.entries.groupBy { it.topic }.map { (name, terms) -> LearnTopic(name, terms) }

    fun topic(name: String): LearnTopic? = topics().firstOrNull { it.name == name }

    /** The first four topics for the landing grid. */
    fun featuredTopics(): List<LearnTopic> = topics().take(4)

    fun termCount(topic: LearnTopic): String = "${topic.terms.size} ${if (topic.terms.size == 1) "term" else "terms"}"

    /** "What is the P/E ratio?" → "P/E ratio"; "What is an earnings beat?" → "earnings beat". Only strips, never rewords. */
    fun termName(entry: EducationEntry): String = entry.title.trim()
        .removePrefix("What is ").removePrefix("What are ").removeSuffix("?")
        .let { t -> listOf("the ", "an ", "a ").firstOrNull { t.startsWith(it) }?.let { t.removePrefix(it) } ?: t }

    /** "Net income (profit), profit margin, free cash flow" — the topic's term names, first letter capitalised. */
    fun topicSummary(topic: LearnTopic): String = topic.terms.joinToString(", ") { termName(it) }.replaceFirstChar { it.uppercase() }

    /** Neutral icon key per topic (platforms map it to an icon); unknown topics get "book". */
    fun topicIcon(name: String): String = when (name) {
        "Business" -> "business"
        "Growth" -> "growth"
        "Profit" -> "profit"
        "Financial health" -> "health"
        "Valuation" -> "valuation"
        "Income" -> "income"
        "Earnings" -> "earnings"
        "Portfolio" -> "portfolio"
        else -> "book"
    }

    /**
     * Icon colour role per topic (reference: multicolour topic icons), mapped by each platform to existing theme colours only — "blue" primary,
     * "green" positive, "indigo" learn accent, "orange" caution. Decorative identity colours, alternating so neighbours differ; never a gain/loss cue.
     */
    fun topicTone(name: String): String = when (name) {
        "Business", "Valuation" -> "blue"
        "Growth", "Income" -> "green"
        "Profit", "Earnings" -> "indigo"
        else -> "orange"
    }

    /** Terms whose title, topic or short text contains every word of [query] (case-insensitive), in catalogue order; blank → none. */
    fun search(query: String): List<EducationEntry> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return BeginnerEducation.entries.filter { e -> val text = "${e.title} ${e.topic} ${e.short}".lowercase(); words.all { it in text } }
    }

    fun searchEmpty(query: String): String = "No terms match \"${query.trim()}\". Try another word."
    fun resultCount(count: Int): String = "$count ${if (count == 1) "term" else "terms"}"

    /**
     * Display text that never breaks right after a slash ("P/ / E ratio" at large text): a word joiner (U+2060, invisible and ignored by screen
     * readers) follows each "/". The wording is unchanged.
     */
    fun unbroken(text: String): String = text.replace("/", "/\u2060")

    /** A one-sentence preview for cards and rows (the first sentence of the catalogue's "what it is"; the full text is on the term page). */
    fun preview(entry: EducationEntry): String = org.example.stocksteps.format.ReadableText.firstSentence(entry.short.trim()).first

    /** The reading page's sections from the catalogue, in the sheet's order; optional ones only when present. */
    fun sections(entry: EducationEntry): List<LearnSection> = listOfNotNull(
        LearnSection("Why it matters", entry.why),
        LearnSection("How to read it", entry.interpret),
        entry.example?.let { LearnSection("Example", it) },
        entry.detailed?.let { LearnSection("More detail", it) },
        LearnSection("Keep in mind", entry.limitations)
    )

    /** "In this lesson" bullets: the section titles that follow. */
    fun outline(entry: EducationEntry): List<String> = sections(entry).map { it.title }

    /** The next term in the same topic, else the first term of the next topic; null after the last. */
    fun next(entry: EducationEntry): EducationEntry? {
        val all = topics().flatMap { it.terms }
        val i = all.indexOfFirst { it.id == entry.id }
        return all.getOrNull(i + 1)
    }

    fun nextLabel(next: EducationEntry): String = "$NEXT: ${next.title}"

    /** Today's concept: the Daily Brief's rotation for an ordinary market day ([BriefContent.concept]); a catalogue entry. */
    fun todaysConcept(dayOfYear: Int): EducationEntry =
        BeginnerEducation.entry(BriefContent.concept(dayOfYear, hasEarningsStory = false, marketsClosed = false).learnTermId) ?: BeginnerEducation.entries.first()

    /**
     * Day of the year (1–366) for an instant, in UTC (the Brief uses the US Eastern date, so the two can differ for a few hours around
     * midnight). Civil-calendar arithmetic, no time-zone database.
     */
    fun dayOfYear(epochMillis: Long): Int {
        val days = epochMillis.floorDiv(86_400_000L)
        // Howard Hinnant's civil_from_days.
        val z = days + 719_468
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
        val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
        val before = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)[month - 1]
        return before + day + if (leap && month > 2) 1 else 0
    }

    /** "Researching Apple · 2 of 5 steps completed". */
    fun journeyDetail(completed: Int): String = "$completed of ${ResearchStep.COUNT} steps completed"
    fun stepCount(): Int = ResearchStep.COUNT
}
