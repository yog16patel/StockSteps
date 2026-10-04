package org.example.stocksteps.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.stocksteps.theme.*
import kotlin.math.round

internal fun homeColor(rgb: Int) = Color(0xFF000000L or rgb.toLong())
internal fun ThemeTextStyle.homeStyle() = TextStyle(fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = FontWeight(weight))

@Composable
internal fun HomeChange(row: HomeStock, index: Boolean = false) {
    when {
        row.loading -> CircularProgressIndicator(Modifier.size(ThemeSpacing.large.dp), strokeWidth = 2.dp)
        row.change != null -> Text(
            text = (if (row.change > 0) "+" else "") + "${round(row.change * 10) / 10}%",
            style = (if (index) HomeTokens.indexChange else HomeTokens.change).homeStyle(),
            color = homeColor(if (row.change < 0) HomeTokens.negative else if (row.change > 0) HomeTokens.positive else HomeTokens.muted)
        )
        else -> Text("—", color = homeColor(HomeTokens.muted), style = HomeTokens.body.homeStyle())
    }
}

@Composable
internal fun HomeIndexCard(row: HomeStock, modifier: Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = HomeTokens.indexHeight.dp)
            .background(homeColor(HomeTokens.surface), RoundedCornerShape(HomeTokens.cardRadius.dp))
            .padding(ThemeSpacing.space10.dp),
        verticalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)
    ) {
        Text(row.name ?: row.symbol, style = HomeTokens.indexLabel.homeStyle(), color = homeColor(HomeTokens.muted))
        Text(row.price?.let { "$${round(it * 100) / 100}" } ?: "—", style = HomeTokens.body.homeStyle(), color = homeColor(HomeTokens.ink))
        HomeChange(row, index = true)
    }
}

@Composable
internal fun HomeStockRow(row: HomeStock, onExplore: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HomeTokens.rowHeight.dp)
            .background(homeColor(HomeTokens.surface), RoundedCornerShape(HomeTokens.cardRadius.dp))
            .clickable { onExplore(row.symbol) }
            .padding(horizontal = ThemeSpacing.large.dp, vertical = ThemeSpacing.small.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.symbol, style = HomeTokens.ticker.homeStyle(), color = homeColor(HomeTokens.ink))
            Text(row.name ?: if (row.error) "Quote unavailable" else "Saved stock", style = HomeTokens.caption.homeStyle(), color = homeColor(HomeTokens.muted))
        }
        Column(horizontalAlignment = Alignment.End) {
            row.price?.let { Text("$${round(it * 100) / 100}", style = HomeTokens.caption.homeStyle(), color = homeColor(HomeTokens.ink)) }
            HomeChange(row)
        }
    }
}

@Composable
internal fun HomeLessonCard(onLearn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(homeColor(HomeTokens.lesson), RoundedCornerShape(HomeTokens.lessonRadius.dp))
            .clickable(onClick = onLearn)
            .padding(ThemeSpacing.large.dp),
        verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)
    ) {
        Text("Today's 2-minute lesson", style = HomeTokens.caption.homeStyle().copy(fontWeight = FontWeight.SemiBold), color = homeColor(HomeTokens.positive))
        Text("What is a P/E ratio?", style = HomeTokens.section.homeStyle(), color = homeColor(HomeTokens.ink))
        Text("Learn with a simple example →", style = HomeTokens.caption.homeStyle(), color = homeColor(HomeTokens.muted))
    }
}

@Composable
internal fun HomeMovers(title: String, section: String, movers: List<org.example.stocksteps.model.MarketMover>, state: HomeState, onRetry: () -> Unit, onExplore: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)) {
        Text(title, style = HomeTokens.section.homeStyle(), color = homeColor(HomeTokens.ink))
        val error = state.snapshot?.errors?.firstOrNull { it.section == section }?.error?.message
        when {
            error != null -> {
                Text(error, style = HomeTokens.caption.homeStyle(), color = homeColor(HomeTokens.muted))
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            state.snapshotLoading && movers.isEmpty() -> LinearProgressIndicator(Modifier.fillMaxWidth())
            movers.isEmpty() -> Text("Nothing available right now.", style = HomeTokens.caption.homeStyle(), color = homeColor(HomeTokens.muted))
        }
        movers.forEach { mover ->
            HomeStockRow(HomeStock(mover.symbol, mover.name, mover.changePercent, price = mover.price), onExplore)
        }
    }
}
