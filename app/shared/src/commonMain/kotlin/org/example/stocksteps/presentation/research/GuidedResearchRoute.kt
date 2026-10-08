package org.example.stocksteps.presentation.research

import kotlinx.serialization.Serializable

/** One company's five-step research guide; [step] opens a step directly (0 = overview). */
@Serializable
internal data class GuidedResearchRoute(val symbol: String, val name: String? = null, val step: Int? = null)
