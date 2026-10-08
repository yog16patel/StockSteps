package org.example.stocksteps.presentation.companydetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.stocksteps.companydetail.CompanyOverviewPresenter
import org.example.stocksteps.companydetail.ValuationModel
import org.example.stocksteps.companydetail.ValuationPresenter
import org.example.stocksteps.companydetail.ValuationRange
import org.example.stocksteps.domain.GetCompanyFundamentals
import org.example.stocksteps.domain.GetCompanyProfile
import org.example.stocksteps.domain.GetValuationHistory
import org.example.stocksteps.model.CompanyFundamentals
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.ValuationHistory

internal data class CompanyValuationState(
    val symbol: String,
    val profile: CompanyProfile? = null,
    val history: Section<ValuationHistory> = Section.Loading,
    val fundamentals: Section<CompanyFundamentals> = Section.Loading,
    /** Null until the user picks one; the default follows how much history exists. */
    val selectedRange: ValuationRange? = null
) {
    val shortName: String get() = profile?.companyName?.let(CompanyOverviewPresenter::shortName) ?: symbol
    val listing: String get() = listOfNotNull(symbol, profile?.exchange).joinToString(" · ")
    val range: ValuationRange get() = selectedRange ?: ValuationPresenter.defaultRange((history as? Section.Content)?.value)
    val loading: Boolean get() = history == Section.Loading
    /** Built once both requests settled; a failed request still yields a model (section messages). */
    val model: ValuationModel? get() = if (history == Section.Loading || fundamentals == Section.Loading) null else ValuationPresenter.build(
        (history as? Section.Content)?.value, (fundamentals as? Section.Content)?.value, range, shortName, profile?.currency
    )
}

/**
 * Valuation for one company: the full P/E history (one request), annual fundamentals for ratios
 * and growth (cached by the backend and shared with Financials) and the profile load in parallel.
 * Ranges only re-slice the loaded history. Keyed by environment by the scene.
 */
internal class CompanyValuationViewModel(
    private val symbol: String,
    private val getHistory: GetValuationHistory,
    private val getFundamentals: GetCompanyFundamentals,
    private val getProfile: GetCompanyProfile,
    private val closeResources: () -> Unit
) : ViewModel() {
    private val mutableState = MutableStateFlow(CompanyValuationState(symbol))
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = attempt { getProfile(symbol) }
            if (profile != null) mutableState.update { it.copy(profile = profile) }
        }
        load()
    }

    fun selectRange(range: ValuationRange) = mutableState.update { it.copy(selectedRange = range) }

    fun load() {
        mutableState.update { it.copy(history = Section.Loading, fundamentals = Section.Loading) }
        viewModelScope.launch {
            val history = attempt { getHistory(symbol) }
            mutableState.update { it.copy(history = history?.let { value -> Section.Content(value) } ?: Section.Unavailable) }
        }
        viewModelScope.launch {
            val fundamentals = attempt { getFundamentals(symbol, "annual") }
            mutableState.update { it.copy(fundamentals = fundamentals?.let { value -> Section.Content(value) } ?: Section.Unavailable) }
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try { block() } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        null
    }

    override fun onCleared() = closeResources()
}
