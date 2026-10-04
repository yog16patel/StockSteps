package org.example.stocksteps.presentation.settings

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.presentation.account.AccountViewModel

@Composable
internal fun SettingsScene(accounts: AccountDependencies, hinge: WindowHinge?, onSignIn: () -> Unit) {
    val model = viewModel { accounts.accountViewModel() }
    val session by model.session.collectAsStateWithLifecycle()
    val action by model.action.collectAsStateWithLifecycle()
    SettingsScreen(session, action, hinge, onSignIn, model::logout)
}
