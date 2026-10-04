package org.example.stocksteps.presentation.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.theme.*

@Composable
internal fun AuthScreen(
    email: String,
    password: String,
    signup: Boolean,
    busy: Boolean,
    error: String?,
    configured: Boolean,
    hinge: WindowHinge?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSwitchMode: () -> Unit,
    onGoogle: () -> Unit,
    onSubmit: () -> Unit,
    onGuest: () -> Unit
) {
    var revealed by remember(signup) { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(authColor(AuthTokens.background))) {
        AdaptiveSinglePane(hinge) { region ->
            Column(
                modifier = region
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(ThemeSpacing.extraLarge.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = AuthTokens.contentWidth.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AuthTokens.stackGap.dp)
                ) {
                    AuthBrand()
                    Spacer(Modifier.height(ThemeSpacing.space14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(ThemeSpacing.tiny.dp)) {
                        Text(if (signup) "Create account" else "Welcome back", style = AuthTokens.title.authStyle(), color = authColor(AuthTokens.ink))
                        Text("Sign in to sync your Watchlist across devices.", style = AuthTokens.body.authStyle(), color = authColor(AuthTokens.muted))
                    }
                    AuthButton("Continue with Google", enabled = configured && !busy, onClick = onGoogle) {
                        Box(
                            modifier = Modifier.size(AuthTokens.googleSize.dp)
                                .border(AuthTokens.googleBorderWidth.dp, authColor(AuthTokens.googleBorder), RoundedCornerShape(AuthTokens.googleRadius.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("G", style = AuthTokens.linkText.authStyle(), color = authColor(AuthTokens.googleInk))
                        }
                    }
                    AuthDivider("or use email")
                    AuthField("Email", email, "you@example.com", !busy, onChange = onEmailChange)
                    AuthField("Password", password, "••••••••", !busy, password = true, revealed = revealed, onChange = onPasswordChange, onReveal = { revealed = !revealed })
                    if (!signup) {
                        TextButton(
                            onClick = { notice = "Password recovery is not available in the app yet." },
                            enabled = !busy,
                            modifier = Modifier.align(Alignment.End),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Forgot password?", style = AuthTokens.linkText.authStyle(), color = authColor(AuthTokens.link))
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = AuthTokens.body.authStyle()) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    AuthButton(if (signup) "Create account" else "Sign in", configured && !busy && email.isNotBlank() && password.isNotBlank(), primary = true, onClick = onSubmit)
                    AuthDivider("or")
                    AuthButton("Continue as guest", !busy, onClick = onGuest)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (signup) "Already have an account?" else "New to StockSteps?", style = AuthTokens.linkText.authStyle(), color = authColor(AuthTokens.muted))
                        TextButton(onClick = onSwitchMode, enabled = !busy) {
                            Text(if (signup) "Sign in" else "Create account", style = AuthTokens.linkText.authStyle(), color = authColor(AuthTokens.link))
                        }
                    }
                }
            }
        }
    }
    notice?.let { message ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text("Sign-in options") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } }
        )
    }
}
