# Focused diff: Phase 5D.5.6D (navigation lifecycle and state fixes)
These are excerpts of the decisive hunks. Full files are listed in the review package §1.

## 1. Android lifecycle rule (`presentation/EntryOnce.kt`, new)
```kotlin
internal class EntryOnce : ViewModel() {
    private val done = mutableSetOf<Any?>()
    fun first(key: Any?): Boolean = done.add(key)
}
@Composable internal fun rememberEntryOnce(): EntryOnce = viewModel(key = "stocksteps-entry-once") { EntryOnce() }
@Composable internal fun LaunchedOncePerEntry(key: Any?, block: suspend CoroutineScope.() -> Unit) {
    val once = rememberEntryOnce()
    LaunchedEffect(key) { if (once.first(key)) block() }
}
```
**Decision.** A tab switch disposes and later restores the tab's screens, so plain `LaunchedEffect`s re-ran. The flags live in the back-stack
entry's ViewModel:
- they survive tab switches (the ViewModelStore is saved with the tab);
- they are cleared with the entry and on process death, when loading again is needed.

This is the Android counterpart of the iOS `taskOnce`.

**Applied to:** Daily Brief history, Earnings Reminders, comparison research reload, AI usage, and the Practice refresh after a confirmed order.

**Risk.** Data may be older after a return. Mitigation: pull-to-refresh and Retry stay. Home's own 60 s freshness rule is unchanged.

**Evidence:**
- device: reminders return 1 → 0 requests;
- `EntryOnceTest`.

## 2. Daily Brief re-open guard (`brief/DailyBriefScenes.kt`)
```kotlin
val once = rememberEntryOnce()
LaunchedEffect(route.briefId) {
    presenter.start()
    val first = once.first(route.briefId)
    val shown = presenter.state.value
    if (first || shown.current == null || shown.current?.id != (route.briefId ?: shown.latest?.id)) presenter.open(route.briefId)
}
```
**Decision.** The presenter is shared (Home, history, notification links), so "once" alone isn't enough. A returning entry re-opens only when
the presenter now shows another brief, for example Back from a second brief.

**Bug fixed.** `open()` refetched the personal section and cleared AI answers on every tab return.

**Evidence:** device: Home → Brief → Markets → Home sends 0 requests, and the brief is pixel-identical.

## 3. Reminders duplicate load (core `EarningsReminders.kt`)
```kotlin
private var loading = false
… collectLatest { id -> uid = id; loading = id != null; try { …; if (id != null) load(id) } finally { loading = false } }
fun refresh() { if (loading) return; val id = uid ?: return; loading = true
    scope.launch { try { …; load(id) } finally { loading = false } } }
```
**Decision.** A refresh during a running load would only repeat the same GET. This follows the shared-failure and shared-load principle.

**Tests:** `aRefreshDuringALoadDoesNotRepeatTheRequest` fails without the guard (verified), and later refreshes still load.

## 4. iOS reused-top restore keeps the shared model (`TabRouter.swift`)
```swift
var symbolsInUse: Set<String> { Set((Array(paths.values) + Array(restoring.values)).flatMap { $0.compactMap(\.symbol) }) }
…
if hop.reused {
    restoring[tab] = old
    if let page = old.last { willRestore?(page) }
    DispatchQueue.main.async {
        self.restoring[tab] = nil
        if self.path(tab) == new { self.paths[tab] = old; self.revision += 1 }
    }
}
```
**Root cause.** The pop's `revision` bump ran `companies.retain(only:)` while Markets' path lacked AAPL for one run-loop turn. The shared
company model was released, so the restored page refetched six endpoints and lost its view state.

**Decision.** Keep the C3 contract (native back button and swipe-back, no duplicate page). Only the ownership gap is closed.

**Evidence:** device: reused Apple → Back → Markets sends 0 page requests; two rapid edge swipes cause one Back and no double pop.

## 5. iOS reading position of a rebuilt company page (`CompanyDetailsScreen.swift`, `CompanyDetailsModel.swift`)
```swift
.scrollPosition($restoredPosition)
.onScrollGeometryChange(for: CGPoint.self) { g in CGPoint(x: g.contentSize.height - g.containerSize.height, y: g.contentOffset.y + g.contentInsets.top) }
    action: { _, g in
        if model.restoreScroll { guard g.x >= model.scrollOffset else { return }
            model.restoreScroll = false; if model.scrollOffset > 1 { restoredPosition.scrollTo(y: model.scrollOffset) }; return }
        model.scrollOffset = g.y }
// AppScene: router.willRestore = { if case let .companyDetails(s) = $0 { companies.model(s).restoreScroll = true } }
```
**Decision.** Only the page the router restores after a reused-top Back returns to the saved offset. A new push still starts at the top. The
restore waits for the content to be tall enough (geometry, not a timer).

The next-earnings model and the viewed flag moved from the scene's `@State` to the per-symbol model, so the rebuilt page doesn't refetch
them.

**Risk.** Restoring is offset-based: if the content above the position changed height meanwhile, the page can land slightly off.

## 6. Accessibility
- **Android:** `StockSegmentedControl(scrollWhenTight = true)` measures with the selected (semibold) style and uses the shared
  `StockPillLayout.fitsEqualWidth`. If any label can't fit its equal share, the options take their natural width in `StockFadingScrollRow`
  (`softWrap = false`). The Practice tabs pass `asTabs = true` (`Role.Tab` + selected).
- **iOS:**
  - the Practice title uses the token's curve instead of `.largeTitle`;
  - `StockTitleWithBadge` and `StockSectionMessage` read `dynamicTypeSize` in `body`, so kept-alive sections follow live changes;
  - `AlertsScene(signedIn:onSignIn:)` shows the Android sign-in card to guests.
