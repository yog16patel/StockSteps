package org.example.stocksteps.presentation.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.stocksteps.theme.*

// Static onboarding illustration from Figma, never presented as a live quote.
@Composable
internal fun WelcomeInsightCard() {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(welcomeColor(WelcomeTokens.surface), RoundedCornerShape(WelcomeTokens.cardRadius.dp))
            .padding(ThemeSpacing.large.dp),
        verticalArrangement = Arrangement.spacedBy(ThemeSpacing.extraLarge.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)) {
            Box(modifier = Modifier.size(WelcomeTokens.logoSize.dp).background(welcomeColor(WelcomeTokens.mint), RoundedCornerShape(WelcomeTokens.iconRadius.dp)))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ThemeSpacing.tiny.dp)) {
                Text(text = "AAPL", style = WelcomeTokens.ticker.welcomeStyle(), color = welcomeColor(WelcomeTokens.ink))
                Text(text = "Apple Inc.", style = WelcomeTokens.caption.welcomeStyle(), color = welcomeColor(WelcomeTokens.muted))
            }
            Text(text = "+1.8%", style = WelcomeTokens.body.welcomeStyle(), color = welcomeColor(WelcomeTokens.green))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)) {
            Text(text = "$255.40", modifier = Modifier.weight(1f), style = WelcomeTokens.price.welcomeStyle(), color = welcomeColor(WelcomeTokens.ink))
            Box(
                modifier = Modifier.widthIn(max = WelcomeTokens.chartWidth.dp).weight(1f).height(WelcomeTokens.chartHeight.dp)
                    .background(welcomeColor(WelcomeTokens.mint), RoundedCornerShape(WelcomeTokens.iconRadius.dp)),
                contentAlignment = Alignment.Center
            ) { Text(text = "╱╲__╱╲___╱", style = WelcomeTokens.brand.welcomeStyle(), color = welcomeColor(WelcomeTokens.green)) }
        }
        HorizontalDivider(color = welcomeColor(WelcomeTokens.divider))
        Row(horizontalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(WelcomeTokens.questionSize.dp).background(welcomeColor(WelcomeTokens.blueTint), RoundedCornerShape(ThemeSpacing.space10.dp)),
                contentAlignment = Alignment.Center
            ) { Text(text = "?", style = WelcomeTokens.ticker.welcomeStyle(), color = welcomeColor(WelcomeTokens.blue)) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ThemeSpacing.tiny.dp)) {
                Text(text = "Why did Apple move today?", style = WelcomeTokens.explanation.welcomeStyle(), color = welcomeColor(WelcomeTokens.ink))
                Text(text = "See the news and events behind the price — explained simply.", style = WelcomeTokens.caption.welcomeStyle(), color = welcomeColor(WelcomeTokens.muted))
            }
            Text(text = "→", style = WelcomeTokens.brand.welcomeStyle(), color = welcomeColor(WelcomeTokens.blue))
        }
    }
}

@Composable
internal fun WelcomeBenefits() {
    Row(horizontalArrangement = Arrangement.spacedBy(ThemeSpacing.space6.dp)) {
        WelcomeBenefit("✓", "Plain-English metrics", WelcomeTokens.green, WelcomeTokens.mint, Modifier.weight(1f))
        WelcomeBenefit("↗", "Why stocks move", WelcomeTokens.blue, WelcomeTokens.blueTint, Modifier.weight(1f))
        WelcomeBenefit("▣", "Learn as you go", WelcomeTokens.purple, WelcomeTokens.purpleTint, Modifier.weight(1f))
    }
}

@Composable
private fun WelcomeBenefit(symbol: String, title: String, tint: Int, background: Int, modifier: Modifier) {
    Column(
        modifier = modifier.heightIn(min = WelcomeTokens.benefitHeight.dp)
            .background(welcomeColor(background), RoundedCornerShape(WelcomeTokens.tileRadius.dp))
            .padding(ThemeSpacing.space10.dp),
        verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)
    ) {
        Text(text = symbol, style = WelcomeTokens.explanation.welcomeStyle(), color = welcomeColor(tint))
        Text(text = title, style = WelcomeTokens.benefit.welcomeStyle(), color = welcomeColor(WelcomeTokens.ink))
    }
}
