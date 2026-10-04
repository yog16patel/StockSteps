package org.example.stocksteps.presentation.stocksearch

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.stocksteps.theme.ThemeSpacing

@Composable
internal fun CompanyProfileSection(state: StockSearchState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)
    ) {
        HorizontalDivider()
        Text(text = "About the company", style = MaterialTheme.typography.titleLarge)
        if (state.profileLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(text = "Loading company profile…")
        }
        state.profileError?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(text = "Retry profile") }
        }
        state.profile?.let { profile ->
            profile.companyName?.takeIf { it.isNotBlank() }?.let { Text(text = it) }
            listOf("Sector" to profile.sector, "Industry" to profile.industry, "Country" to profile.country)
                .forEach { (label, value) ->
                    value?.takeIf { it.isNotBlank() }?.let { Text(text = "$label: $it") }
                }
            Text(text = profile.description?.takeIf { it.isNotBlank() } ?: "Company description unavailable.")
        }
    }
}
