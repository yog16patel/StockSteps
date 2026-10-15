package org.example.stocksteps.presentation.learn

import kotlinx.serialization.Serializable

@Serializable
internal data object LearnRoute

/** Topics (Phase 5D.5.2): all topics, or one topic's terms when [topic] is set; [search] focuses the term filter on open. */
@Serializable
internal data class LearnTopicsRoute(val topic: String? = null, val search: Boolean = false)

/** A term's reading page (Phase 5D.5.2), by `EducationEntry.id`. */
@Serializable
internal data class LearnTermRoute(val id: String)
