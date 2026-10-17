# Phase 8B — focused diff

8B adds one presenter method (state management) and changes display text. There are no financial calculations, and no server, auth or
navigation changes.

## 1. `ScreenerPresenter.selectSector` (core, state)

```kotlin
fun selectSector(values: List<String>) {
    mutable.update { it.copy(draft = it.applied ?: it.draft) }
    setChoice(ChoiceField.SECTOR, values)
    apply()
}
```

**Decision:**
- A sector tap starts from the **applied** query.
- Unapplied edits left in the filter sheet are discarded, not silently sent.
- The `SECTOR` choice is replaced: one value, or none for "All sectors".
- `apply()` then runs the existing search (cursor reset, one request).

**Risk:** a pending sheet edit is lost when a pill is tapped. That is intended, because the pill acts on what is on screen.

**Evidence:**
- device log: a tap sends 1 `POST /screener/search`;
- the Basic Materials frame shows 4 filters (3 from the preset + the sector) and 3 matches on iOS;
- on Android, "1 filter" from the landing page.

## 2. Sector options and selection (`ScreenerDisplay`)

```kotlin
fun sectorOptions(choices) = choices[SECTOR].orEmpty().takeIf { it.size > 1 }?.let { listOf(ALL_SECTORS) + it }.orEmpty()
fun selectedSector(query): String = when (values.size) { 0 -> ALL_SECTORS; 1 -> values.single(); else -> "" }
fun sectorValues(option) = if (option == ALL_SECTORS || option.isEmpty()) emptyList() else listOf(option)
```

- The options are the catalogue's own sectors, in its order; nothing is added.
- A multi-sector filter highlights nothing, so the pills never misstate the filter.
- Test: `ScreenerDisplayTest.sectorShortcutMirrorsTheSheetFilter`.

## 3. Scope line and row sector

- `scopeLine(universe)` = "N companies · <server description verbatim>". Test: `scopeLineNamesTheListAndItsSize`.
- `ScreenerRowView.sector` (new, default null) comes from `CompanyRecord.sector`. It's spoken after the identity and is null when missing.
  Test: `rowSectorIsSpokenOnlyWhenKnown`.

## 4. Sort label

- `sortLabel` uses words: "(high to low)" / "(low to high)", where it used to show arrows.
- Test `sortLabelsAreShortAndSpokenInWords` is updated (2 expected strings).

## 5. Legacy movers (`MarketMoverDisplay`, app/shared)

```kotlin
fun change(p) = if (HomePresentation.direction(p) == UNAVAILABLE) "Change unavailable" else HomePresentation.percent(p)
fun price(p) = p?.let { HomePresentation.price(it, "USD") } ?: "Price unavailable"
```

- Replaces `"${changePercent?.let{"$it%"} ?: "Unavailable"} · Price ${price ?: "Unavailable"}"` on Android and iOS.
- A real 0 % reads "0.00%".
- Tests: `MarketMoverDisplayTest` (missing, NaN, zero, positive, negative).
