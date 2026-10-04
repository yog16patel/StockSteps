package org.example.stocksteps.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.model.AuthSession
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.presentation.account.AccountActionState
import org.example.stocksteps.theme.ThemeSpacing

@Composable
internal fun SettingsScreen(session: AuthSession, action: AccountActionState, hinge: WindowHinge?, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    AdaptiveSinglePane(hinge) { region ->
        Column(
            modifier = region.verticalScroll(rememberScrollState()).padding(ThemeSpacing.extraLarge.dp),
            verticalArrangement = Arrangement.spacedBy(ThemeSpacing.large.dp)
        ) {
            Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
            Text(text = "Account", style = MaterialTheme.typography.titleLarge)
            if (session.initializing || action.busy) CircularProgressIndicator()
            else if (session.user == null) {
                Text(text = "Use StockSteps without an account, or sign in to sync your watchlist.")
                Button(onClick = onSignIn) { Text(text = "Sign in or create account") }
            } else {
                Text(text = session.user?.email ?: "Signed in")
                Button(onClick = onSignOut) { Text(text = "Sign out") }
            }
            (action.error ?: session.configurationError)?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
