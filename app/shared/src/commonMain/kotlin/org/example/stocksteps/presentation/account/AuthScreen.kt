package org.example.stocksteps.presentation.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.theme.AuthTokens

/**
 * Sign in / create account (the screen shown at launch for signed-out users and from every "Sign in"
 * prompt). Rendering only: authentication state and actions come from [AuthScene]. Follows the app's
 * Light/Dark/System theme through StockStepsTheme; scrolls, and keeps the focused field above the keyboard.
 */
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
    val colors = StockStepsTheme.colors
    var revealed by remember(signup) { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Navy (or pale blue) wash at the top fading into the app background.
    val wash = Brush.verticalGradient(
        0f to colors.primary.copy(alpha = if (colors.isDark) 0.16f else 0.10f),
        0.45f to colors.appBackground.copy(alpha = 0f),
        startY = 0f, endY = Float.POSITIVE_INFINITY
    )
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.appBackground).background(wash)) {
        val compact = maxHeight < 700.dp
        AdaptiveSinglePane(hinge) { region ->
            Column(
                modifier = region.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = AuthTokens.screenPadding.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(Modifier.widthIn(max = AuthTokens.contentWidth.dp).fillMaxWidth()) {
                    StockStepsAuthHeader()
                    Spacer(Modifier.height((if (compact) 14 else AuthTokens.headerGap).dp))
                    StockStepsAuthHero(signup, compact)
                    Spacer(Modifier.height((if (compact) 14 else AuthTokens.heroGap).dp))
                    StockStepsAuthCard {
                        StockStepsGoogleButton(enabled = configured && !busy, onClick = onGoogle)
                        StockStepsAuthDivider("or use email")
                        StockStepsAuthTextField("Email", email, "you@example.com", StockIcons.Mail, !busy, onEmailChange)
                        Column {
                            StockStepsAuthTextField("Password", password, if (signup) "At least 6 characters" else "••••••••", StockIcons.Lock, !busy, onPasswordChange,
                                password = true, revealed = revealed, onReveal = { revealed = !revealed })
                            if (!signup) TextButton(
                                onClick = { notice = "Password recovery is not available in the app yet." },
                                enabled = !busy,
                                modifier = Modifier.align(Alignment.End).heightIn(min = 44.dp),
                                contentPadding = PaddingValues(horizontal = 0.dp)
                            ) { Text("Forgot password?", style = AuthTokens.linkText.authStyle(), color = colors.primary) }
                        }
                        error?.let { StockStepsAuthError(it) }
                        StockStepsAuthButton(if (signup) "Create account" else "Sign in",
                            enabled = configured && !busy && email.isNotBlank() && password.isNotBlank(), loading = busy, onClick = onSubmit)
                        StockStepsAuthDivider("or")
                        StockStepsGuestAction(enabled = !busy, onClick = onGuest)
                        StockStepsAuthSwitch(if (signup) "Already have an account?" else "New to StockSteps?", if (signup) "Sign in" else "Create account", !busy, onSwitchMode)
                    }
                    Spacer(Modifier.height(12.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }
    notice?.let { message ->
        AlertDialog(
            onDismissRequest = { notice = null },
            containerColor = colors.surface,
            title = { Text("Reset your password", color = colors.textPrimary) },
            text = { Text(message, color = colors.textBody) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("OK", color = colors.primary) } }
        )
    }
}
