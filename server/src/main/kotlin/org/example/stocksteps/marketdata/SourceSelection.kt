package org.example.stocksteps.marketdata

import kotlinx.coroutines.CancellationException
import org.example.stocksteps.appconfig.DataMode
import org.example.stocksteps.repository.StockProviderException

/**
 * Phase 9B: one candidate source for a dataset. [freshness] ranks how current its data is (lower = fresher: 0 delayed intraday,
 * 1 end of day, 2 periodic filings…), [completeness] how much of the dataset it covers (0–100), [costRank] what one request costs us
 * (0 = free official). [mode] is the data mode the source belongs to: MOCK fixtures are never a candidate for REAL and vice versa.
 * [adjustment] is the price basis for price datasets (null for others).
 */
data class SourceCandidate(
    val provider: String,
    val dataset: Dataset,
    val mode: DataMode,
    val freshness: Int,
    val completeness: Int,
    val costRank: Int,
    val adjustment: AdjustmentBasis? = null
)

/** What the caller needs: its own data mode, and (for prices) the exact adjustment basis it will combine the series with. */
data class SourceRequirement(val mode: DataMode, val channel: DisplayChannel = DisplayChannel.MOBILE_APP, val adjustment: AdjustmentBasis? = null)

/** Why a candidate wasn't used. */
enum class SkipReason { WRONG_MODE, NOT_LICENSED, ADJUSTMENT_MISMATCH, RESTRICTED, RATE_LIMITED, FAILED, NO_DATA, STALE_KEPT_LOOKING }

data class SourceAttempt(val provider: String, val reason: SkipReason?)

/** The chosen value (null when no candidate had data) and every attempt, for internal provenance and diagnostics. */
data class Selection<T>(val result: Sourced<T>?, val attempts: List<SourceAttempt>)

/**
 * Deterministic source selection and fallback for one dataset.
 *
 * Order: license (VERIFIED_ALLOWED first), then [SourceCandidate.freshness], completeness (desc), cost, provider name — the same inputs
 * always give the same order. In [LicenseMode.COMMERCIAL] candidates not cleared for the channel are removed (fail closed).
 *
 * Fallback never:
 * - switches modes (a REAL request never reaches a MOCK source);
 * - combines a different or unknown adjustment basis with the one required;
 * - presents stale as current (a stale result is kept only if no candidate returns current data, and stays flagged);
 * - fabricates (a source without data is skipped; no zero or default is produced);
 * - retries a provider that answered 402/403/429 — one attempt per candidate per call; cooldowns stay with the source's own cache.
 */
class SourceSelector(private val gate: LicenseGate) {
    private fun licenseRank(c: SourceCandidate): Int = when (gate.registry.rights(c.provider, c.dataset)?.display) {
        LicenseStatus.VERIFIED_ALLOWED -> 0
        LicenseStatus.PENDING_CONFIRMATION -> 1
        LicenseStatus.VERIFIED_RESTRICTED -> 2
        LicenseStatus.NOT_ALLOWED, null -> 3
    }

    fun order(candidates: List<SourceCandidate>): List<SourceCandidate> =
        candidates.sortedWith(compareBy<SourceCandidate>({ licenseRank(it) }, { it.freshness }, { -it.completeness }, { it.costRank }, { it.provider }))

    suspend fun <T> select(
        candidates: List<SourceCandidate>,
        requirement: SourceRequirement,
        load: suspend (SourceCandidate) -> Sourced<T>?
    ): Selection<T> {
        val attempts = mutableListOf<SourceAttempt>()
        var stale: Sourced<T>? = null
        for (c in order(candidates)) {
            val skip = when {
                c.mode != requirement.mode -> SkipReason.WRONG_MODE
                !gate.permits(c.provider, c.dataset, requirement.channel) -> SkipReason.NOT_LICENSED
                requirement.adjustment != null && c.adjustment != requirement.adjustment -> SkipReason.ADJUSTMENT_MISMATCH
                else -> null
            }
            if (skip != null) { attempts += SourceAttempt(c.provider, skip); continue }
            val result = try { load(c) } catch (cause: CancellationException) { throw cause } catch (cause: StockProviderException) {
                attempts += SourceAttempt(c.provider, when {
                    cause.restricted -> SkipReason.RESTRICTED
                    cause.failure == StockProviderException.Failure.RATE_LIMITED -> SkipReason.RATE_LIMITED
                    else -> SkipReason.FAILED
                }); continue
            }
            if (result == null) { attempts += SourceAttempt(c.provider, SkipReason.NO_DATA); continue }
            // The provenance must name the source that produced it (never hidden or relabelled by a fallback).
            check(result.provenance.provider == c.provider) { "Provenance must name its source" }
            if (result.provenance.stale) {
                if (stale == null) stale = result
                attempts += SourceAttempt(c.provider, SkipReason.STALE_KEPT_LOOKING); continue
            }
            attempts += SourceAttempt(c.provider, null)
            return Selection(result, attempts)
        }
        return Selection(stale, attempts)
    }
}
