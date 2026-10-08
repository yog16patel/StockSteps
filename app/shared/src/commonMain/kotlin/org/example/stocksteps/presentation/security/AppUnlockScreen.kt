package org.example.stocksteps.presentation.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.resources.*
import org.example.stocksteps.security.AppLockManager
import org.example.stocksteps.security.BiometricKind
import org.example.stocksteps.security.BiometricResult
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen, opaque lock surface: nothing underneath is visible or reachable by screen readers.
 * The system prompt opens once automatically; after a cancel the user taps to retry, so prompts
 * never loop. "Use account login" signs out of Firebase and returns to Login — the fallback when
 * biometrics are locked out or removed.
 */
@Composable
internal fun AppUnlockScreen(lock: AppLockManager, onUseAccountLogin: () -> Unit) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val reason = stringResource(Res.string.lock_reason)
    val lockout = stringResource(Res.string.lock_lockout)
    val unavailable = stringResource(Res.string.lock_unavailable)
    val failed = stringResource(Res.string.lock_failed)
    fun attempt() {
        if (busy) return
        busy = true
        scope.launch {
            message = when (lock.unlock(reason)) {
                BiometricResult.Success, BiometricResult.Cancelled -> null // cancelling is not an error
                BiometricResult.Lockout -> lockout
                BiometricResult.Unavailable -> unavailable
                is BiometricResult.Failure -> failed
            }
            busy = false
        }
    }
    LaunchedEffect(Unit) { attempt() }
    val label = when (lock.settings()?.kind) {
        BiometricKind.FACE_ID -> Res.string.lock_unlock_face
        BiometricKind.TOUCH_ID -> Res.string.lock_unlock_touch
        BiometricKind.FINGERPRINT -> Res.string.lock_unlock_fingerprint
        BiometricKind.DEVICE_CREDENTIAL -> Res.string.lock_unlock_credential
        else -> Res.string.lock_unlock_biometric
    }
    Box(Modifier.fillMaxSize().background(colors.appBackground).safeDrawingPadding().padding(spacing.screen), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            StockBrandMark(stringResource(Res.string.home_brand))
            Box(Modifier.padding(top = spacing.xl).size(StockStepsTheme.dimensions.avatar * 2).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(StockIcons.Shield, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(StockStepsTheme.dimensions.avatar))
            }
            Text(stringResource(Res.string.lock_title), Modifier.semantics { heading() }, style = StockStepsTheme.typography.screenTitle, color = colors.textPrimary)
            Text(stringResource(Res.string.lock_body), style = StockStepsTheme.typography.body, color = colors.textBody, textAlign = TextAlign.Center)
            message?.let { Text(it, style = StockStepsTheme.typography.small, color = colors.negativeText, textAlign = TextAlign.Center) }
            StockButton(stringResource(label), onClick = ::attempt, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = spacing.md))
            StockButton(stringResource(Res.string.lock_use_account), onClick = onUseAccountLogin, variant = StockButtonVariant.TEXT)
        }
    }
}
