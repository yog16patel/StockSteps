package org.example.stocksteps.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
enum class FinancialSource { PROVIDER_DIRECT, BACKEND_CALCULATED, CONTEXT_DERIVED, EDUCATIONAL_STATIC, NOT_AVAILABLE }
@Serializable(with = FinancialAvailabilitySerializer::class)
enum class FinancialAvailability { AVAILABLE, MISSING, NO_DIVIDEND, TEMPORARILY_UNAVAILABLE, INVALID_VALUE, NON_POSITIVE_DENOMINATOR, INSUFFICIENT_HISTORY, PERIOD_MISMATCH, UNRELIABLE_COMPARISON }
/** Decode older servers without carrying provider diagnostics into the public model. */
object FinancialAvailabilitySerializer : KSerializer<FinancialAvailability> {
    override val descriptor = PrimitiveSerialDescriptor("FinancialAvailability", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: FinancialAvailability) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): FinancialAvailability = when (val value = decoder.decodeString()) {
        "ACCESS_RESTRICTED", "PROVIDER_UNAVAILABLE" -> FinancialAvailability.TEMPORARILY_UNAVAILABLE
        else -> FinancialAvailability.valueOf(value)
    }
}
@Serializable
data class FinancialBasis(
    val period: String,
    val date: String? = null,
    val fiscalYear: Int? = null,
    val currency: String? = null
)
/** Amounts remain whole currency units; ratios and percentages remain numeric. */
@Serializable
data class FinancialFact(
    val value: Double? = null,
    val amount: Long? = null,
    val source: FinancialSource = FinancialSource.NOT_AVAILABLE,
    val availability: FinancialAvailability = FinancialAvailability.MISSING,
    val basis: FinancialBasis? = null,
    val note: String? = null,
    val contextSource: FinancialSource = FinancialSource.EDUCATIONAL_STATIC,
    val changePercent: Double? = null
) {
    fun numericValue(): Double? = amount?.toDouble() ?: value
}
@Serializable
data class FinancialObservation(val year: Int, val date: String, val value: Double?)
@Serializable
data class HistoricalComparison(
    val observations: List<FinancialObservation> = emptyList(),
    val average: Double? = null,
    val median: Double? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val validCount: Int = 0,
    val requestedYears: Int = 5,
    val differencePercent: Double? = null,
    val reliable: Boolean = false,
    val note: String? = null,
    val source: FinancialSource = FinancialSource.BACKEND_CALCULATED,
    val comparisonSource: FinancialSource = FinancialSource.CONTEXT_DERIVED
)
@Serializable
data class CompanyValuation(
    val metrics: Map<String, FinancialFact> = emptyMap(),
    val historical: Map<String, HistoricalComparison> = emptyMap()
)
@Serializable
data class CompanyFinancials(
    val growth: Map<String, FinancialFact> = emptyMap(),
    val profitability: Map<String, FinancialFact> = emptyMap(),
    val financialHealth: Map<String, FinancialFact> = emptyMap(),
    val cashFlow: Map<String, FinancialFact> = emptyMap(),
    val shareholderReturns: Map<String, FinancialFact> = emptyMap()
) {
    fun metrics(): Map<String, FinancialFact> = growth + profitability + financialHealth + cashFlow + shareholderReturns
}
@Serializable
data class CompanyFundamentals(
    val symbol: String,
    val financials: CompanyFinancials = CompanyFinancials(),
    val valuation: CompanyValuation = CompanyValuation(),
    val datasets: Map<String, FinancialAvailability> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val retrievedAt: String? = null
)
