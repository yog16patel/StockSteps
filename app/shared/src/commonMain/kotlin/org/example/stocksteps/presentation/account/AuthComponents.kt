package org.example.stocksteps.presentation.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.theme.*

/*
 * Sign-in / create-account building blocks. Colours come from StockStepsTheme (Light, Dark, System), sizes
 * from the shared AuthTokens; the iOS SwiftUI screen mirrors these one to one (AuthComponents.swift).
 */

internal fun ThemeTextStyle.authStyle() = TextStyle(fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = FontWeight(weight))
private val Int.adp: Dp get() = this.dp

/** Rounded-square "S" mark, wordmark and tagline (compact, horizontal). */
@Composable
internal fun StockStepsAuthHeader() {
    val colors = StockStepsTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
        Box(
            Modifier.size(AuthTokens.logoSize.adp).clip(RoundedCornerShape(AuthTokens.logoRadius.adp))
                .background(Brush.linearGradient(listOf(colors.primaryBright, colors.primary, colors.primaryDark))),
            contentAlignment = Alignment.Center
        ) { Text("S", style = AuthTokens.logo.authStyle(), color = colors.onPrimary) }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("StockSteps", style = AuthTokens.brand.authStyle(), color = colors.textPrimary)
            Text("Learn  •  Practice  •  Invest Smarter", style = AuthTokens.tagline.authStyle(), color = colors.textSecondary)
        }
    }
}

/**
 * "Build your investing skills" with a decorative chart (rising curve, translucent bars, soft glow).
 * Purely illustrative: no values, tickers or performance claims, and hidden from screen readers.
 */
@Composable
internal fun StockStepsAuthHero(signup: Boolean, compact: Boolean) {
    val colors = StockStepsTheme.colors
    val height = (if (compact) AuthTokens.heroHeightCompact else AuthTokens.heroHeight).adp
    Box(Modifier.fillMaxWidth().heightIn(min = height)) {
        AuthChartIllustration(Modifier.align(Alignment.TopEnd).fillMaxWidth(if (compact) 0.42f else 0.5f).height(height).clearAndSetSemantics { })
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.82f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(buildAnnotatedString {
                append(if (signup) "Start your" else "Build your"); append("\n")
                withStyle(SpanStyle(color = colors.primary)) { append(if (signup) "investing journey" else "investing skills") }
            }, style = AuthTokens.heroTitle.authStyle().copy(fontSize = if (compact) 30.sp else AuthTokens.heroTitle.size.sp), color = colors.textPrimary,
                modifier = Modifier.semantics { heading() })
            Text(if (signup) "Save your watchlists, practice\nand progress on every device." else "Simple insights. Real data.\nA smarter you.",
                style = AuthTokens.heroBody.authStyle(), color = colors.textBody)
        }
    }
}

@Composable
private fun AuthChartIllustration(modifier: Modifier) {
    val colors = StockStepsTheme.colors
    val dark = colors.isDark
    val primary = colors.primary
    val glow = colors.brandGlow
    Canvas(modifier) {
        val w = size.width; val h = size.height
        // Soft glow behind the chart.
        drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = if (dark) 0.28f else 0.16f), Color.Transparent), center = Offset(w * 0.72f, h * 0.45f), radius = w * 0.55f),
            radius = w * 0.55f, center = Offset(w * 0.72f, h * 0.45f))
        // Translucent rising bars (decorative proportions only).
        val bars = listOf(0.22f, 0.34f, 0.46f, 0.60f, 0.76f)
        val gap = w * 0.035f
        val barWidth = (w * 0.80f - gap * (bars.size - 1)) / bars.size
        bars.forEachIndexed { i, f ->
            val left = w * 0.18f + i * (barWidth + gap)
            val top = h * (1f - f)
            drawRoundRect(Brush.verticalGradient(listOf(primary.copy(alpha = if (dark) 0.38f else 0.24f), primary.copy(alpha = 0.02f)), startY = top, endY = h),
                topLeft = Offset(left, top), size = Size(barWidth, h - top), cornerRadius = CornerRadius(6.dp.toPx()))
            drawRoundRect(primary.copy(alpha = if (dark) 0.30f else 0.22f), topLeft = Offset(left, top), size = Size(barWidth, h - top),
                cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
        }
        // Upward curve with a glow and an arrow head.
        val start = Offset(w * 0.12f, h * 0.88f); val end = Offset(w * 0.96f, h * 0.06f)
        val curve = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(w * 0.35f, h * 0.90f, w * 0.55f, h * 0.62f, w * 0.70f, h * 0.38f)
            cubicTo(w * 0.80f, h * 0.22f, w * 0.88f, h * 0.13f, end.x, end.y)
        }
        drawPath(curve, primary.copy(alpha = 0.25f), style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
        drawPath(curve, Brush.linearGradient(listOf(primary.copy(alpha = 0.4f), primary, glow), start = start, end = end),
            style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        val head = 11.dp.toPx()
        drawPath(Path().apply {
            moveTo(end.x + head * 0.35f, end.y - head * 0.35f)
            lineTo(end.x - head, end.y - head * 0.05f)
            lineTo(end.x - head * 0.05f, end.y + head)
            close()
        }, glow)
    }
}

/** The rounded surface that groups every sign-in option. */
@Composable
internal fun StockStepsAuthCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = StockStepsTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(AuthTokens.cardRadius.adp)).background(colors.surface)
            .border(AuthTokens.borderWidth.adp, colors.border.copy(alpha = if (colors.isDark) 0.9f else 1f), RoundedCornerShape(AuthTokens.cardRadius.adp))
            .padding(AuthTokens.cardPadding.adp),
        verticalArrangement = Arrangement.spacedBy(AuthTokens.groupGap.adp),
        content = content
    )
}

/** White "Continue with Google" button with Google's own "G" (Google branding: white in light and dark). */
@Composable
internal fun StockStepsGoogleButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = StockStepsTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = AuthTokens.controlHeight.adp).clip(RoundedCornerShape(AuthTokens.controlRadius.adp))
            .background(Color.White).border(AuthTokens.borderWidth.adp, if (colors.isDark) Color.White else colors.border, RoundedCornerShape(AuthTokens.controlRadius.adp))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.55f).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(StockIcons.GoogleLogo, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(AuthTokens.googleSize.adp))
        Text("Continue with Google", Modifier.weight(1f).padding(horizontal = 14.dp), style = AuthTokens.button.authStyle(), color = Color(0xFF1F1F1F))
        Icon(StockIcons.ChevronRight, contentDescription = null, tint = Color(0xFF5F6368), modifier = Modifier.size(AuthTokens.iconSize.adp))
    }
}

@Composable
internal fun StockStepsAuthDivider(text: String) {
    val colors = StockStepsTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = colors.border)
        Text(text, style = AuthTokens.caption.authStyle().copy(fontSize = 13.sp), color = colors.textSecondary)
        HorizontalDivider(Modifier.weight(1f), color = colors.border)
    }
}

/** Label above a rounded, theme-tinted input with a leading icon and an optional trailing action. */
@Composable
internal fun StockStepsAuthTextField(
    label: String,
    value: String,
    placeholder: String,
    leading: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onChange: (String) -> Unit,
    password: Boolean = false,
    revealed: Boolean = false,
    onReveal: () -> Unit = {}
) {
    val colors = StockStepsTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(AuthTokens.controlRadius.adp)
    Column(verticalArrangement = Arrangement.spacedBy(AuthTokens.fieldGap.adp)) {
        Text(label, style = AuthTokens.label.authStyle(), color = colors.textSecondary)
        Row(
            Modifier.fillMaxWidth().heightIn(min = AuthTokens.controlHeight.adp).clip(shape)
                .background(if (colors.isDark) colors.appBackground.copy(alpha = 0.55f) else colors.appBackground)
                .border(if (focused) 1.5.dp else AuthTokens.borderWidth.adp, if (focused) colors.primary else colors.border, shape)
                .padding(start = 16.dp, end = if (password) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(leading, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(AuthTokens.iconSize.adp))
            Spacer(Modifier.width(14.dp))
            BasicTextField(
                value = value, onValueChange = onChange, enabled = enabled, singleLine = true,
                textStyle = AuthTokens.body.authStyle().copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.primary),
                visualTransformation = if (password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Email,
                    imeAction = if (password) ImeAction.Done else ImeAction.Next, autoCorrectEnabled = false),
                interactionSource = interaction,
                modifier = Modifier.weight(1f).padding(vertical = 15.dp).semantics { contentDescription = label },
                decorationBox = { input ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, style = AuthTokens.body.authStyle(), color = colors.textTertiary)
                        input()
                    }
                }
            )
            if (password) Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onReveal)
                    .semantics { contentDescription = if (revealed) "Hide password" else "Show password" },
                contentAlignment = Alignment.Center
            ) { Icon(if (revealed) StockIcons.Visibility else StockIcons.VisibilityOff, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp)) }
        }
    }
}

/** Full-width primary action: blue, white semibold text, trailing arrow; a spinner while working. */
@Composable
internal fun StockStepsAuthButton(text: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val colors = StockStepsTheme.colors
    Box(
        Modifier.fillMaxWidth().heightIn(min = AuthTokens.controlHeight.adp).clip(RoundedCornerShape(AuthTokens.controlRadius.adp))
            .background(Brush.horizontalGradient(listOf(colors.primary, colors.primaryGradientEnd)))
            .clickable(enabled = enabled && !loading, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .alpha(if (enabled || loading) 1f else 0.6f)
            .semantics { if (loading) stateDescription = "Signing in" },
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = AuthTokens.button.authStyle().copy(fontSize = 17.sp), color = colors.onPrimary)
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = colors.onPrimary, strokeWidth = 2.dp)
            else Icon(StockIcons.ArrowForward, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(22.dp))
        }
    }
}

/** Outlined "Continue as guest" with its explanation underneath. */
@Composable
internal fun StockStepsGuestAction(enabled: Boolean, onClick: () -> Unit) {
    val colors = StockStepsTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = AuthTokens.controlHeight.adp).clip(RoundedCornerShape(AuthTokens.controlRadius.adp))
                .border(BorderStroke(1.dp, colors.textSecondary.copy(alpha = if (colors.isDark) 0.55f else 0.45f)), RoundedCornerShape(AuthTokens.controlRadius.adp))
                .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else 0.55f).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(StockIcons.Person, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
            Text("Continue as guest", Modifier.weight(1f).padding(horizontal = 14.dp), style = AuthTokens.button.authStyle(), color = colors.textPrimary)
            Icon(StockIcons.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(AuthTokens.iconSize.adp))
        }
        Text("Explore stocks, try features, and learn — no account needed.", style = AuthTokens.caption.authStyle().copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** "New to StockSteps? Create account" (or the reverse) on a slightly raised strip. */
@Composable
internal fun StockStepsAuthSwitch(prompt: String, action: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = StockStepsTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (colors.isDark) colors.surfaceElevated else colors.surfaceSecondary)
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = action, onClick = onClick)
            .heightIn(min = 56.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(prompt, Modifier.weight(1f), style = AuthTokens.linkText.authStyle().copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
        Text(action, style = AuthTokens.linkText.authStyle().copy(fontSize = 15.sp), color = colors.primary)
        Icon(StockIcons.ChevronRight, contentDescription = null, tint = colors.primary, modifier = Modifier.size(AuthTokens.iconSize.adp))
    }
}

/** Inline error (sign-in failures, configuration problems): icon + text, never colour alone. */
@Composable
internal fun StockStepsAuthError(message: String) {
    val colors = StockStepsTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.negativeContainer)
            .border(1.dp, colors.negativeBorder, RoundedCornerShape(12.dp)).padding(12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top
    ) {
        Icon(StockIcons.Warning, contentDescription = "Error", tint = colors.negativeText, modifier = Modifier.size(18.dp))
        Text(message, style = AuthTokens.body.authStyle().copy(fontSize = 14.sp), color = colors.negativeText)
    }
}
