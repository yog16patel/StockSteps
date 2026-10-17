# Phase 8D — Android / iOS parity

| Element | Android | iOS | Same? |
|---|---|---|---|
| Discover scope | `scopeLabel` caption + `StockDisclosure("About this list")` with `scopeLine` | same | yes (shared text) |
| Practice heading inside the Portfolio landing | `sectionHeading` from 1.3× (`largeFontScale`) | `sectionHeading` at accessibility sizes | same rule, each platform's large-text threshold |
| Practice guest sign-in | unchanged (`StockEmptyState` with Sign in) | short line → button → reason; at AX sizes the button reads "Sign in to use Practice" | different by design (Android's empty state already shows the button near the top) |
| Learn intro | hidden from 1.75× (Learn's single-column threshold) | hidden at accessibility sizes | same intent |
| Research step description | wraps (no `maxLines`) | wraps (`fixedSize` vertical, no `lineLimit`) | yes |
| Brand colours, five tabs, navigation | unchanged | unchanged | yes |
