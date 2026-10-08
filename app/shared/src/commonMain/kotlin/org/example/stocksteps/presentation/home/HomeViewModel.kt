package org.example.stocksteps.presentation.home

import androidx.lifecycle.ViewModel
import org.example.stocksteps.home.PersonalDashboardStore

/** Scene-scoped lifecycle owner; account graph owns the shared, cached dashboard aggregation. */
internal class HomeViewModel(private val dashboard: PersonalDashboardStore) : ViewModel() {
    val state = dashboard.state
    fun onVisible() = dashboard.onVisible()
    fun refresh() = dashboard.refresh()
    fun retryQuotes() = dashboard.retryQuotes()
    fun retryNews() = dashboard.retryNews()
    fun retryWatchlists() = dashboard.retryWatchlists()
    fun retryAlerts() = dashboard.retryAlerts()
    fun clearRecent() = dashboard.clearRecent()
    fun persona(id: String?) { if (id == null) dashboard.useSavedCompanies() else dashboard.showMockPersona(id) }
}
