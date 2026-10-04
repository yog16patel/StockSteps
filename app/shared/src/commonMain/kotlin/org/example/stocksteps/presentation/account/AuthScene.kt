package org.example.stocksteps.presentation.account

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.di.AccountDependencies

@Composable
internal fun AuthScene(accounts: AccountDependencies, route: AuthRoute, hinge: WindowHinge?, onDone: () -> Unit) {
    val model = viewModel { accounts.accountViewModel() }
    val session by model.session.collectAsStateWithLifecycle()
    val action by model.action.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    // Password is deliberately neither saveable nor part of ViewModel/UI state snapshots.
    var password by remember { mutableStateOf("") }
    var signup by rememberSaveable { mutableStateOf(route.signup) }
    LaunchedEffect(action.completed) {
        if (action.completed) { password = ""; onDone() }
    }
    AuthScreen(
        email = email,
        password = password,
        signup = signup,
        busy = action.busy || session.initializing,
        error = action.error ?: session.configurationError,
        configured = session.configurationError == null,
        hinge = hinge,
        onEmailChange = { email = it },
        onPasswordChange = { password = it },
        onSwitchMode = { signup = !signup; password = "" },
        onGoogle = { password = ""; model.googleSignIn() },
        onSubmit = { model.submit(email, password, signup) },
        onGuest = { password = ""; onDone() }
    )
}
