package org.example.stocksteps.model

/** Platform-neutral configuration; scenes own callbacks and navigation. */
enum class AppBarBackButton { NONE, BACK, CLOSE }

data class AppBarAction(
    val id: String,
    val label: String,
    val enabled: Boolean = true
)

data class AppBarConfiguration(
    val title: String,
    val visible: Boolean = true,
    val backButton: AppBarBackButton = AppBarBackButton.NONE,
    val backEnabled: Boolean = true,
    val actions: List<AppBarAction> = emptyList()
)
