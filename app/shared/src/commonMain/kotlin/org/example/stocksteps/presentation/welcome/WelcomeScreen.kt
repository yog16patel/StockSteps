package org.example.stocksteps.presentation.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.stocksteps.*
import org.example.stocksteps.theme.*

internal fun ThemeTextStyle.welcomeStyle() = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = FontWeight(weight)
)
internal fun welcomeColor(rgb: Int) = Color(0xFF000000L or rgb.toLong())

@Composable
internal fun WelcomeScreen(hinge: WindowHinge?, onStartExploring: () -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(welcomeColor(WelcomeTokens.background))
            .safeContentPadding()
            .onGloballyPositioned { origin = it.positionInWindow() }
    ) {
        val pane = paneLayout(
            constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(),
            density.density, origin.x, origin.y, hinge
        ).search
        val region = if (hinge == null) Modifier.fillMaxSize() else with(density) {
            Modifier.absoluteOffset(pane.x.toDp(), pane.y.toDp())
                .size(pane.width.toDp(), pane.height.toDp())
        }
        BoxWithConstraints(modifier = region) {
            val availableHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = availableHeight)
                    .padding(horizontal = ThemeSpacing.extraLarge.dp, vertical = ThemeSpacing.space30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(modifier = Modifier.widthIn(max = WelcomeTokens.contentWidth.dp).fillMaxWidth()) {
                    WelcomeBrand()
                    Spacer(modifier = Modifier.height(ThemeSpacing.space30.dp))
                    Text(
                        text = "Invest with more\nunderstanding.",
                        style = WelcomeTokens.title.welcomeStyle(),
                        color = welcomeColor(WelcomeTokens.ink)
                    )
                    Spacer(modifier = Modifier.height(ThemeSpacing.medium.dp))
                    Text(
                        text = "Stocks can feel complicated. We turn prices, financials and market news into simple explanations.",
                        style = WelcomeTokens.body.welcomeStyle(),
                        color = welcomeColor(WelcomeTokens.muted)
                    )
                    Spacer(modifier = Modifier.height(ThemeSpacing.space40.dp))
                    WelcomeInsightCard()
                    Spacer(modifier = Modifier.height(ThemeSpacing.extraLarge.dp))
                    WelcomeBenefits()
                }
                Spacer(modifier = Modifier.weight(1f).heightIn(min = ThemeSpacing.space40.dp))
                Column(modifier = Modifier.widthIn(max = WelcomeTokens.contentWidth.dp).fillMaxWidth()) {
                    Button(
                        onClick = onStartExploring,
                        modifier = Modifier.fillMaxWidth().heightIn(min = WelcomeTokens.buttonHeight.dp),
                        shape = RoundedCornerShape(WelcomeTokens.buttonRadius.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = welcomeColor(WelcomeTokens.ink),
                            contentColor = welcomeColor(WelcomeTokens.surface)
                        )
                    ) { Text(text = "Start exploring", style = WelcomeTokens.body.welcomeStyle().copy(fontWeight = FontWeight.SemiBold)) }
                    Spacer(modifier = Modifier.height(ThemeSpacing.medium.dp))
                    Text(
                        text = "No trading required · Built for beginners",
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = WelcomeTokens.footer.welcomeStyle(),
                        color = welcomeColor(WelcomeTokens.muted)
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeBrand() {
    Row(horizontalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(WelcomeTokens.brandSize.dp).background(welcomeColor(WelcomeTokens.ink), RoundedCornerShape(WelcomeTokens.iconRadius.dp)),
            contentAlignment = Alignment.Center
        ) { Text(text = "S", color = welcomeColor(WelcomeTokens.surface), style = ThemeTypography.title.welcomeStyle()) }
        Column(verticalArrangement = Arrangement.spacedBy(ThemeSpacing.tiny.dp)) {
            Text(text = "StockSteps", style = WelcomeTokens.brand.welcomeStyle(), color = welcomeColor(WelcomeTokens.ink))
            Text(text = "INVESTING, EXPLAINED", style = WelcomeTokens.benefit.welcomeStyle(), color = welcomeColor(WelcomeTokens.green))
        }
    }
}
