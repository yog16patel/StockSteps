# Phase 8D — focused diff

8D changes layout and wording only. It touches no financial calculations, auth, navigation, concurrency, persistence or presenter
state.

## 1. Discover scope label (core `ScreenerDisplay`)

```kotlin
fun scopeLabel(universe: UniverseInfo?): String? = universe?.let { u ->
    "${u.size} ${if (u.size == 1) "company" else "companies"} · not the whole market"
}
const val ABOUT_LIST = "About this list"
```

- **What it does:** "not the whole market" is always visible, on every universe (curated, MOCK, FMP screener). The server's own
  description (`scopeLine`) moves under the disclosure; nothing is removed.
- **Risk:** the wording is new, so it's owner decision D8D-1.
- **Test:** `scopeLabelIsShortAndHonest`.

## 2. Practice heading at large text

- **Compose:**
  ```kotlin
  style = if (inLanding && large) typography.sectionHeading else typography.screenTitle
  ```
- **SwiftUI:**
  ```swift
  .font(StockStepsTheme.font(landingBar != nil && typeSize.isAccessibilitySize ? type.sectionHeading : type.screenTitle))
  ```
- Only applies inside the Portfolio landing, which already shows the "Portfolio" screen title. The heading semantics are kept.

## 3. iOS Practice guest sign-in

- The sign-in block was one `StockSectionMessage` with the sentence before the button. Now it is: a short line, then the button, then
  the reason.
- At accessibility sizes the button label is "Sign in to use Practice".
- `onSignIn` is unchanged.

## 4. Learn

- **Intro sentence:** left out at very large text (iOS accessibility sizes; Android ≥ 1.75×).
- **Research step descriptions:** wrap in full (`maxLines = 2` and `lineLimit(2)` removed).
