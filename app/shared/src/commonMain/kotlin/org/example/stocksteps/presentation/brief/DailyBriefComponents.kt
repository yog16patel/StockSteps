package org.example.stocksteps.presentation.brief

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.example.stocksteps.brief.BriefFormat
import org.example.stocksteps.brief.BriefIndex
import org.example.stocksteps.brief.DailyBriefDisplay
import org.example.stocksteps.designsystem.components.StockIndexRow

/** A brief index as the shared [StockIndexRow] (Home "Markets today" and the reader, Phase 5D.5.1). */
@Composable
internal fun BriefIndexRow(index: BriefIndex, modifier: Modifier = Modifier) = StockIndexRow(
    name = index.displayName,
    stateLabel = index.stateLabel,
    value = BriefFormat.value(index),
    change = DailyBriefDisplay.indexChange(index),
    direction = DailyBriefDisplay.indexDirection(index),
    accessibilityLabel = BriefFormat.accessibility(index),
    modifier = modifier,
    caution = DailyBriefDisplay.indexCaution(index),
    note = index.proxyNote,
    changeUnavailable = DailyBriefDisplay.CHANGE_UNAVAILABLE
)
