package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/** Thin strip shown whenever the backend reports mock data, so samples are never mistaken for prices. */
@Composable
internal fun StockSampleDataBanner(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .background(StockStepsTheme.colors.warningContainer)
            .padding(vertical = StockStepsTheme.spacing.xxs),
        style = StockStepsTheme.typography.label,
        color = StockStepsTheme.colors.textBody,
        textAlign = TextAlign.Center
    )
}
