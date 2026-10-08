package org.example.stocksteps.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Inviting learning banner: soft gradient, decorative illustration, indigo title and a round
 * arrow cue. The whole banner is one button; Learn owns the content.
 */
@Composable
internal fun HomeLearnCard(onLearn: () -> Unit, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val dimensions = StockStepsTheme.dimensions
    val shape = StockStepsTheme.shapes.cardLarge
    val action = stringResource(Res.string.home_learn_action)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(colors.learnContainerStart, colors.learnContainerEnd)))
            .clickable(onClickLabel = action, role = Role.Button, onClick = onLearn)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Image(
            painter = painterResource(Res.drawable.ill_learn_basics),
            contentDescription = null,
            modifier = Modifier.size(dimensions.learnIllustration)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(
                text = stringResource(Res.string.home_learn_title),
                style = StockStepsTheme.typography.cardTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.learnAccent
            )
            Text(
                text = stringResource(Res.string.home_learn_body),
                style = StockStepsTheme.typography.caption,
                color = colors.textBody
            )
        }
        Box(
            modifier = Modifier
                .size(dimensions.learnAction)
                .clip(CircleShape)
                .background(colors.learnAccent)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Icon(StockIcons.ArrowForward, contentDescription = null, tint = colors.onLearnAccent, modifier = Modifier.size(dimensions.iconSmall))
        }
    }
}
