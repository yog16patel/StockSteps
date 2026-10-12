package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.theme.StockSemanticStyles

/**
 * Thin strip shown whenever the backend reports mock data, so samples are never mistaken for prices. Comparison final polish: quieter treatment
 * (`StockSemanticStyles.sampleStrip` — SAMPLE fill, hairline top border, `textSecondary` caption, same as the SwiftUI strip); stacked above the
 * bottom bar, never over tabs, content or the gesture area (placement is the caller's, unchanged).
 */
@Composable
internal fun StockSampleDataBanner(text: String, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val strip = StockSemanticStyles.sampleStrip(colors.palette)
    Column(modifier.fillMaxWidth().background(colors.rgb(strip.container))) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rgb(strip.border)))
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(vertical = StockStepsTheme.spacing.xxs),
            style = StockStepsTheme.typography.caption,
            color = colors.rgb(strip.content),
            textAlign = TextAlign.Center
        )
    }
}
