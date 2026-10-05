# Configurable top app bar

Shared configuration lives in core's `model/AppBarConfiguration.kt`:

- `title`: screen title.
- `visible`: show/hide the app bar.
- `backButton`: NONE, BACK, or CLOSE.
- `backEnabled`: disables the navigation action while an operation is busy.
- `actions`: stable unique ID, accessible label, and enabled flag for each trailing action.

Scenes own navigation and action callbacks. The shared configuration does not own
ViewModels or a navigation controller. Route types remain identity/arguments only.

Android renders configuration through `StockStepsTopBar` in shared presentation
components, using Material TopAppBar and its system-bar insets. The tab shell sets
titles from the current destination, hides the bar on the login design, and gives
Search a Back action. iOS renders the same configuration through the
`stockStepsTopBar` native SwiftUI modifier. AppScene owns the root NavigationStack;
WatchList/Settings/Learn screens no longer create nested navigation containers.
Auth sheets use Close (disabled when busy), search columns use Done, and compact
stock details retain the native automatic back button. Native toolbar controls
receive the OS toolbar appearance, including Liquid Glass on supported systems.

Kotlin:

```kotlin
StockStepsTopBar(
    configuration = AppBarConfiguration(
        title = "Stock details",
        backButton = AppBarBackButton.BACK,
        actions = listOf(AppBarAction("refresh", "Refresh", enabled = !loading))
    ),
    onBack = onBack,
    onAction = { id -> if (id == "refresh") onRefresh() }
)
```

SwiftUI (inside a navigation container):

```swift
.stockStepsTopBar(
    .screen(
        "Stock details",
        backButton: .back,
        actions: [AppBarAction(id: "refresh", label: "Refresh", enabled: !loading)]
    ),
    onBack: onBack,
    onAction: { id in if id == "refresh" { onRefresh() } }
)
```

Use native automatic back navigation with backButton NONE when a NavigationStack
or NavigationSplitView owns the pushed-detail history. Do not add a second custom
back control for the same transition. This component is the top application header;
the operating system still supplies the clock, battery and signal indicators.
