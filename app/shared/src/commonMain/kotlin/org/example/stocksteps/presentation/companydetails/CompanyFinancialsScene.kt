package org.example.stocksteps.presentation.companydetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.companydetail.*
import org.example.stocksteps.designsystem.components.StockChip
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.domain.GetCompanyFundamentals
import org.example.stocksteps.model.CompanyFundamentals
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.presentation.companydetail.CompanyFinancialsSection
import org.example.stocksteps.presentation.companydetail.DetailCard
import org.example.stocksteps.presentation.companydetail.FinancialMetricRow
import org.example.stocksteps.presentation.companydetail.MetricInfoBottomSheet
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendRouter

internal data class CompanyFinancialsState(
    val period: FinancialPeriod = FinancialPeriod.ANNUAL,
    val fundamentals: CompanyFundamentals? = null,
    val loading: Boolean = true,
    val failed: Boolean = false
)

/** Full Financials + Valuation for one company (the existing Annual/Quarterly experience). */
internal class CompanyFinancialsViewModel(
    private val symbol: String,
    private val getFundamentals: GetCompanyFundamentals,
    private val closeResources: () -> Unit
) : ViewModel() {
    private val mutableState = MutableStateFlow(CompanyFinancialsState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    init { load() }

    fun selectPeriod(period: FinancialPeriod) {
        if (period == state.value.period) return
        // A new period clears old facts so annual values never appear under Quarterly.
        mutableState.update { it.copy(period = period, fundamentals = null) }
        load()
    }

    fun load() {
        job?.cancel()
        mutableState.update { it.copy(loading = true, failed = false) }
        job = viewModelScope.launch {
            try {
                val values = getFundamentals(symbol, if (state.value.period == FinancialPeriod.ANNUAL) "annual" else "quarter")
                mutableState.update { it.copy(fundamentals = values, loading = false) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutableState.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    override fun onCleared() = closeResources()
}

@Composable
internal fun CompanyFinancialsScene(route: CompanyFinancialsRoute, backend: BackendRouter, environment: BackendEnvironment, hinge: WindowHinge?) {
    val model = viewModel(key = "company-financials:${route.symbol}:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        CompanyFinancialsViewModel(route.symbol, data.getCompanyFundamentals(), data::close)
    }
    val state by model.state.collectAsStateWithLifecycle()
    var info by remember { mutableStateOf<String?>(null) }
    val financials = FinancialsPresenter.build(state.fundamentals, state.loading, state.failed)
    val valuation = CompanyDetailPresenter.build(StockSearchResult(route.symbol, route.symbol), null, null, state.fundamentals).valuation
    val spacing = StockStepsTheme.spacing
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    Box(Modifier.fillMaxSize().background(StockStepsTheme.colors.appBackground)) {
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(
                modifier = region,
                contentPadding = PaddingValues(spacing.screen),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item(key = "period") {
                    Row(content.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        FinancialPeriod.entries.forEach { period ->
                            StockChip(period.label, selected = period == state.period, onClick = { model.selectPeriod(period) })
                        }
                    }
                }
                financials.sections.forEach { section ->
                    item(key = section.title) {
                        Box(content) { CompanyFinancialsSection(section, onRetry = model::load, onInfo = { info = it }) }
                    }
                }
                if (state.fundamentals != null) {
                    valuation.forEach { metric ->
                        item(key = "valuation-${metric.id}") {
                            Box(content) {
                                DetailCard(metric.label) {
                                    FinancialMetricRow(metric) { info = metric.id }
                                    Text("${metric.historicalLabel}: ${CompanyDetailPresenter.number(metric.historicalAverage)}")
                                    metric.historicalRange?.let { Text(it) }
                                    CompanyDetailPresenter.historicalContext(metric)?.let { Text(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    info?.let { id -> MetricInfoBottomSheet(id, valuation) { info = null } }
}
