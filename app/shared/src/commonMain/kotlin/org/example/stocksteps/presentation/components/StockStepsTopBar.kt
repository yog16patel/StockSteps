package org.example.stocksteps.presentation.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.example.stocksteps.model.AppBarBackButton
import org.example.stocksteps.model.AppBarConfiguration
import org.example.stocksteps.designsystem.theme.StockStepsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StockStepsTopBar(
    configuration: AppBarConfiguration,
    onBack: () -> Unit = {},
    onAction: (String) -> Unit = {},
    backIcon: @Composable () -> Unit = { Text("Back") },
    modifier: Modifier = Modifier,
    trailing: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null
) {
    if (!configuration.visible) return
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = StockStepsTheme.colors.appBackground,
            titleContentColor = StockStepsTheme.colors.textPrimary
        ),
        title = {
            Text(
                text = configuration.title,
                style = StockStepsTheme.typography.sectionTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            when (configuration.backButton) {
                AppBarBackButton.BACK -> IconButton(
                    onClick = onBack,
                    enabled = configuration.backEnabled,
                    content = backIcon
                )
                AppBarBackButton.CLOSE -> TextButton(onClick = onBack, enabled = configuration.backEnabled) {
                    Text("Close")
                }
                AppBarBackButton.NONE -> Unit
            }
        },
        actions = {
            trailing?.invoke(this)
            configuration.actions.forEach { action ->
                TextButton(onClick = { onAction(action.id) }, enabled = action.enabled) {
                    Text(action.label)
                }
            }
        }
    )
}
