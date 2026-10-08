package org.example.stocksteps.presentation.companydetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.stocksteps.companydetail.CompanyOverviewPresenter
import org.example.stocksteps.companydetail.FinancialPeriod
import org.example.stocksteps.companydetail.FinancialRange
import org.example.stocksteps.companydetail.FinancialStatementsModel
import org.example.stocksteps.companydetail.FinancialStatementsPresenter
import org.example.stocksteps.domain.GetCompanyFundamentals
import org.example.stocksteps.domain.GetCompanyProfile
import org.example.stocksteps.model.CompanyFundamentals

internal data class CompanyFinancialsState(
    val symbol: String,
    val name: String? = null,
    val listing: String? = null,
    val frequency: FinancialPeriod = FinancialPeriod.ANNUAL,
    /** Null until the user picks one; then the default follows how much history exists. */
    val selectedRange: FinancialRange? = null,
    /** Loaded statements per frequency; switching back to a loaded frequency makes no request. */
    val loaded: Map<FinancialPeriod, CompanyFundamentals> = emptyMap(),
    val loading: Boolean = true,
    val failed: Boolean = false
) {
    val fundamentals: CompanyFundamentals? get() = loaded[frequency]
    val range: FinancialRange get() = selectedRange
        ?: FinancialStatementsPresenter.defaultRange(frequency, fundamentals?.history?.size ?: 0)
    val model: FinancialStatementsModel? get() =
        fundamentals?.let { FinancialStatementsPresenter.build(it, frequency, range, shortName) }
    /** Short company name for titles and sentences ("Microsoft", not "Microsoft Corporation"). */
    val shortName: String get() = name?.let(CompanyOverviewPresenter::shortName) ?: symbol
}

/**
 * Financials for one company. Annual statements and the profile (for the header) load first;
 * Quarterly loads on demand and both are kept for the screen's lifetime. Range changes only
 * re-slice loaded history. The backend URL decides Mock vs Real; the scene keys this ViewModel
 * by environment so results from one environment never appear in the other.
 */
internal class CompanyFinancialsViewModel(
    private val symbol: String,
    private val getFundamentals: GetCompanyFundamentals,
    private val getProfile: GetCompanyProfile,
    private val closeResources: () -> Unit
) : ViewModel() {
    private val mutableState = MutableStateFlow(CompanyFinancialsState(symbol))
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val profile = try { getProfile(symbol) } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                null
            }
            if (profile != null) mutableState.update {
                it.copy(name = profile.companyName?.takeIf(String::isNotBlank), listing = listOfNotNull(symbol, profile.exchange).joinToString(" · "))
            }
        }
        load()
    }

    fun selectFrequency(frequency: FinancialPeriod) {
        if (frequency == state.value.frequency) return
        mutableState.update { it.copy(frequency = frequency, selectedRange = null, failed = false) }
        if (frequency !in state.value.loaded) load() else job?.cancel()
    }

    fun selectRange(range: FinancialRange) = mutableState.update { it.copy(selectedRange = range) }

    fun load() {
        val frequency = state.value.frequency
        job?.cancel()
        // Current content stays visible while the new frequency loads.
        mutableState.update { it.copy(loading = true, failed = false) }
        job = viewModelScope.launch {
            try {
                val result = getFundamentals(symbol, if (frequency == FinancialPeriod.ANNUAL) "annual" else "quarter")
                mutableState.update { state ->
                    val loaded = state.loaded + (frequency to result)
                    // A response for a frequency the user already left is cached but doesn't end the newer load.
                    if (state.frequency == frequency) state.copy(loaded = loaded, loading = false) else state.copy(loaded = loaded)
                }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutableState.update { if (it.frequency == frequency) it.copy(loading = false, failed = true) else it }
            }
        }
    }

    override fun onCleared() = closeResources()
}
