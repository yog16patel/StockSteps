# Global UI Refinement Phase 5D.5.4D: Company Details accessibility & final verification

- **Status:** implemented and verified; approved by the owner, who runs the TalkBack/VoiceOver pass manually (§9 steps). Committed locally as "Finish Company Details accessibility for Global UI Refinement Phase 5D.5.4D with section header actions under long titles, a stacking Financials chart legend and whole Financial Highlights and Key Ratios values at large text on iOS, and the final verification record" (**not pushed**).
- **Git baseline:** `ebe81f4` (C3, committed and pushed; `main` = `origin/main`). The working tree was clean before 5D.5.4D.
- **C3 / C4 status:**
  - C3 is committed and pushed.
  - A separate C4 pass had not been run; its outstanding checks are folded into this phase (§5).
- **Review package:** `docs/reviews/phase5d_5_4d/` (two contact sheets plus `CHATGPT_REVIEW_PACKAGE.md`; `captures/` is git-ignored). Screens
  unchanged since C1–C3 are in those packages.

## 1. Confirmed defects (audit)
| # | Defect | Source |
|---|---|---|
| D1 | iOS AX5: the Financials "Revenue & Net Income" legend wrapped as "• Revenue • Net" / "Income" | `StockBarChart` legend: one fixed `HStack` (`DesignSystem/StockDetailComponents.swift`) |
| D2a | iOS AX5: the Company Details section title broke mid-word beside its action ("Financia l Highligh ts" next to "See Financials") | `StockSectionHeader`: title squeezed by the trailing action (`DesignSystem/StockComponents.swift`) |
| D2b | iOS AX5: Financial Highlights values split ("$416. 2B") beside the change column | `StockInfoLine` rows with a change column never stacked (`CompanyDetailsScreen.swift`) |

No other clipping or overlap was found on these screens at the checked sizes (§5). Android had none of these defects (2.0× PASS).

## 2. Changed files (iOS only; no shared, Android or server change)
- `app/iosApp/iosApp/DesignSystem/StockComponents.swift` — `StockSectionHeader`:
  - the action moves under the title only when the title's longest word and the action can't share the line;
  - this is the shared `StockLayout.metricRowStacked` rule on text widths;
  - otherwise the row is unchanged.
- `app/iosApp/iosApp/DesignSystem/StockDetailComponents.swift` — `StockBarChart` legend:
  - side by side while both entries fit whole, otherwise one entry per line;
  - each swatch stays beside its label, and the text keeps the user's size.
- `app/iosApp/iosApp/CompanyDetailsScreen.swift`: Financial Highlights and Key Ratios rows go through `StockInfoLineGroup` (C3). There is one
  decision per group, and values and changes stay whole.

## 3. Responsive-layout decisions
- **Reuse:** every decision reuses `StockLayout.metricRowStacked` (already covered by `ResponsiveLayoutRulesTest`) or C3's `StockInfoLineGroup`.
  No shared rule changed, so no new shared test was needed.
- **Default sizes unchanged:** layouts change only where the old one would break a word or a figure. The Default captures show the unchanged
  layout.
- **No shrinking:** nothing is shrunk to force a fit. The legend stacks instead of scaling.

## 4. Accessibility audit (code-level; screen readers not run)
| Area | Finding | Result |
|---|---|---|
| Section bar / pills | Compose `selectable(role = Role.Tab)` in a `selectableGroup`; iOS `.isSelected` (+ `.isButton`, tab bar container) | PASS (code) |
| Headings | `StockSectionHeader` uses `heading()` / `.isHeader`; Understanding P/E toggle is a heading with Expanded/Collapsed | PASS (code) |
| Charts and range bars | One summary per chart (`chart.description_` / `contentDescription`), e.g. "Revenue by quarter: Q1 FY2024: $91.7B; …" (read from the Android tree) | PASS (code and tree) |
| Info icons | ⓘ is decorative (`contentDescription = null`; iOS label = metric name); rows and tiles are buttons with "Explain …" hints | PASS (code) |
| Explanation sheets | iOS has a Done button; Compose `ModalBottomSheet` (back/scrim dismiss); sheets close before navigating to Learn | PASS (code and device) |
| Financial values | Values and units are single text runs; ratio abbreviations use word joiners (C2/C3) | PASS (code) |
| Observation | Compose charts use `semantics(mergeDescendants = true)` with a description, so the visible detail line may also be read | NEEDS TalkBack to confirm |
| TalkBack / VoiceOver runtime | Not operated | BLOCKED — awaiting approval (§7) |

## 5. Verification matrix
Android: emulator-5554, 360 dp, MOCK, Apple. iOS: iPhone 16, MOCK, guest. Changed iOS states were captured on the final code.

| Check | Android | iOS |
|---|---|---|
| Company Details header and chart | PASS 1.0× / 1.3× / 2.0× | PASS Default / AX5 |
| Financial Highlights and Key Ratios (values whole, header) | PASS 1.0× / 1.3× / 2.0×, dark | PASS Default, dark, AX XL and AX5 (after D2) |
| Financials legend | PASS 1.0×, dark | PASS AX XL (one line); AX5 (stacked, after D1) |
| Section bar jump (Overview → Financials) | PASS | PASS |
| Annual / Quarterly | PASS (Quarterly, dark: 8 quarters, "Only 8 quarters…" note) | C1 PASS (not re-run) |
| Valuation periods; missing average (10Y) | PASS 10Y dark: no comparison or average line, reason shown | C2 PASS |
| Explanation sheet → Learn → Back, scroll restoration | C3 PASS (not re-run; no code change) | C3 PASS (not re-run) |
| Light / dark | PASS | PASS |
| Guest mode | NOT RUN (the emulator holds a signed-in session) | PASS (guest) |
| Error / offline | Offline reopen showed cached content (no banner); "Unable to load company data · Try again" was seen earlier this session on a failed first load | NOT RUN |
| Stale data | Home showed "From an earlier session (Oct 7)" labels; Company Details has no separate stale banner | NOT RUN |
| Sample labelling | "Sample data · mock backend" on every screen | PASS |
| Controls hidden behind the bottom bar | None found | None found |

Devices were restored:
- emulator 1.0×, Light theme, `adb reverse` removed;
- simulator Default, light.

The MOCK server is stopped.

## 6. Automated tests and builds
| Suite / build | Result |
|---|---|
| core JVM / iOS | PASS 541/0 / 541/0 |
| server | PASS 501/0 |
| shared Android host / iOS simulator | PASS 263/0 / 259/0 |
| `:app:androidApp:assembleDebug` | PASS |
| `xcodebuild` iPhone 16, MOCK | PASS |

## 7. Known issues, limits and owner approvals
- **Screen readers:**
  - TalkBack: BLOCKED — awaiting approval. Its first start asks for a notification permission only the owner may answer.
  - VoiceOver: BLOCKED (needs a device).
  - **Manual steps:**
    1. Enable the screen reader.
    2. Open Apple → Company Details.
    3. Swipe through the header, chart (one summary), section bar (selected tab announced) and Financial Highlights (label, value and change
       per row).
    4. Open the P/E tile → sheet → Learn → Back.
    5. In Financials, check the bar chart is read once, without its detail line repeated.
- **iOS AX5:**
  - In Highlights rows with no change, "—" sits on its own line. This is the existing "not available" mark.
  - The P/E chart is narrow, but readable.
- **Not re-run in 5D.5.4D:** Android guest mode, and the iOS offline/stale and Quarterly checks.
- **Owner approvals:** approve 5D.5.4D; approve a TalkBack session.

## 8. Recommended next phase
- Close 5D.5.4 after owner review and the screen-reader session.
- Then move to the next Global UI Refinement area, per the 5D.5.4 plan.

## 9. Final accessibility QA pass (owner request, 2026-10-15)
No code changed in this pass: no in-scope accessibility defect was confirmed.

| # | Item | Result | Evidence |
|---|---|---|---|
| 1a | TalkBack (Android emulator, Android 17) | **BLOCKED** (partial run) | Turned on from adb with no first-start prompt this time. The focus box appears, and Back returns with the scroll position kept. Injected gestures and `Alt+→` don't move TalkBack focus (16 steps stayed on Back), and some injected taps activated controls. TalkBack doesn't log its speech, so a walk needs a person. TalkBack was turned off and the settings restored (`enabled_accessibility_services` = null) |
| 1b | VoiceOver (iOS) | **BLOCKED** | VoiceOver doesn't run in the iOS Simulator; needs a device |
| 2 | Android guest mode | **BLOCKED** | The emulator holds the owner's signed-in session. Guest mode means signing out, and signing back in needs the owner's password, which Claude can't enter |
| 3 | Android section tabs at 2.0× | **PASS** | All four tabs are in a horizontally scrollable bar. Each tap moves `selected="true"` to that tab (Financials → News → About → Overview); tabs are `Role.Tab` in a `selectableGroup` (code). Capture `android-2.0x-light-mock-tabs.jpg` |
| 3 | iOS section tabs at AX5 | **PASS** (reachable, selectable, underline); spoken state code-level | The bar scrolls to News and About; About and News become selected with the underline. `.isSelected` traits in code. Captures `ios-ax5-light-mock-bar-scrolled/about2/news.jpg` |
| 4 | Android reading order: Financial Highlights, Key Ratios | **PASS** (accessibility tree) | Tree order: "Financial Highlights" (heading) → "See Financials" → per row label → value → change (spoken "up +6.4%") → basis note → "Key Ratios" → label → value pairs |
| 4 | Android chart legend | **PASS** (tree) | The chart, legend and detail line are one merged node: "Revenue by fiscal year: … Net income …", then "Key Metrics (FY2025)". The legend isn't read separately or out of order |
| 4 | iOS reading order (Highlights, Key Ratios, legend) | **PASS (code-level)**; runtime BLOCKED with VoiceOver | Each `StockInfoLine` is one combined element (label, value, change, helper); the stacked layout keeps the same order. The legend sits inside `StockBarChart`'s `.accessibilityElement(children: .ignore)` with the chart summary naming both series |
| — | Observation, not a confirmed defect | — | A missing change shows "—", which a screen reader may announce as "dash". Confirm in the manual pass before changing |

**Manual screen-reader steps (owner):**
1. Turn on TalkBack (Android) or VoiceOver (iPhone).
2. Open Apple → Company Details.
3. Swipe through the header, chart (one summary) and the section bar: "Financials, tab, selected".
4. Continue through Financial Highlights (each row: label, value, change) and Key Ratios.
5. Open the P/E tile → sheet → "Learn more" → lesson → Back.
6. In Financials, check the bar chart is read once and the legend isn't read separately.

**Devices restored:**
- emulator 1.0×, Light, TalkBack off, `adb reverse` removed;
- simulator Default, light.

The MOCK server is stopped.
