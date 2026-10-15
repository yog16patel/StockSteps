package org.example.stocksteps.learning

import org.example.stocksteps.companydetail.MetricEducation

/** A tapped metric's explanation sheet: the screen's own label, the existing `MetricEducation` text, and the Learn term when one exists. */
data class MetricExplanation(val metricId: String, val title: String, val explanation: String, val term: EducationEntry?) {
    val learnMore: String? get() = term?.let(MetricLearnTerms::learnMoreLabel)
}

/**
 * Links a displayed financial metric (a `MetricEducation` id such as "pe") to its Learn term (Phase 5D.5.3). By id only, never by visible
 * titles: an exact catalogue id first, then the single term whose `relatedMetrics` lists the metric; none or several owners → null (an ambiguous
 * match is never guessed). No educational text is created here.
 */
object MetricLearnTerms {
    /** The sheet for [metricId] (null when the metric has no beginner text); [title] keeps the label the screen shows ("Dividend Yield (TTM)"). */
    fun explain(metricId: String, title: String? = null): MetricExplanation? = MetricEducation.find(metricId)?.let {
        MetricExplanation(metricId, title ?: it.label, it.explanation, termFor(metricId))
    }

    fun termFor(metricId: String): EducationEntry? {
        BeginnerEducation.entry(metricId)?.let { return it }
        return BeginnerEducation.entries.filter { metricId in it.relatedMetrics }.singleOrNull()
    }

    fun termIdFor(metricId: String): String? = termFor(metricId)?.id

    fun hasTerm(metricId: String): Boolean = termFor(metricId) != null

    /** "Learn more about P/E ratio" (the term's name from its title). */
    fun learnMoreLabel(entry: EducationEntry): String = "Learn more about ${LearnDisplay.termName(entry)}"

    /** Spoken hint for a tappable metric: "Explain P/E Ratio". */
    fun explainLabel(metricLabel: String): String = "Explain $metricLabel"
}
