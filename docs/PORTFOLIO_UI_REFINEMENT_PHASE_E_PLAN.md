# Portfolio UI Refinement — Phase E plan: accessibility and cross-platform QA

Status: **approved by the owner on 2026-10-13 (all recommendations: Q1–Q7); fixes and runtime checks committed as "Add Portfolio Phase E accessibility fixes with iOS error and rejection announcements, unbroken P/L labels, filter-only history announcements and single-announcer save errors, plus Android and iOS large-text QA".**
- Base: `fa7c563` (main, pushed). Portfolio D3.1 is `2bb39af`. Working tree clean at the audit.
- Phase E is verification and accessibility stabilization: no redesign, no financial change, no new test framework.

## 1. Audit findings (static, 2026-10-13)

### Confirmed defects

| ID | Platform / screen | Priority | Evidence | Fix |
|---|---|---|---|---|
| E-01 | iOS · dashboard, history, details | P2 | Error / warning banners (`portfolioErrorBanner` → `StockBanner`) appear without an announcement; Android banners are polite live regions | Announce the error text when it changes |
| E-02 | iOS · transaction form | P2 | The form-level message (a server rejection) isn't announced (`PortfolioForms.swift`); Android uses a live region | Announce `formMessage` when it changes |
| E-03 | Holding details (Android 360 dp, 1.0×) | P2 | `docs/design/portfolio_d3_1/a-light1x-holding-details-tab-highlighted`: "Unrealized P/" + "L" | Join "P/L" with word joiners in the shared labels, plus a test |
| E-04 | Android · Transaction history | P3 | The count text is a polite live region, so it re-announces on any list change (after a row delete: "Transaction deleted" + "All: 3 transactions"); iOS announces only on a filter change | Announce the count only on a filter change |

### Risks that need runtime checks

| ID | Risk |
|---|---|
| R-01 | Android `StockTextField` sets `contentDescription = label`, which may hide the typed value or repeat the label |
| R-02 | Android: an error caption plus `error()` semantics may announce the error twice |
| R-03 | Reading and focus order: rows (tap target + separate ⋯), month headings, details summary, the pinned banner |
| R-04 | Initial focus and dismissal in dialogs and sheets |
| R-05 | Success banners auto-hide after 4 s. **Owner decision: keep** (screen readers get the announcement) |
| R-06 | Large-text states never checked at runtime: Android 1.3× and dark 2.0×; iOS since D2 |
| R-07 | The iOS Portfolio tab highlight relies on the screen structure |

### Known issue (not changed)

K-01: Insights excludes borrowed cash from its denominator; the dashboard includes it (deferred in D3.1).

### Out of scope (owner, Q7)

Reference-sheet features that don't exist:
- an "All accounts" aggregate view;
- a transaction "Status" field;
- a type-filter dropdown;
- a "Cash" row in the allocation.

## 2. Stages

| Stage | Work | Approval |
|---|---|---|
| E-A | Static audit (§1) | Done |
| E-D | Fix E-01, E-02, E-03, E-04; shared test `PortfolioPhaseETest` | Approved (Q6) |
| E-B | Android 360 dp, local MOCK: text scale 1.0 / 1.3 / 2.0 light and 1.0 / 2.0 dark on the dashboard, entry, history, details, dialogs and keyboard. Then TalkBack over 10 journeys, with spoken output captured via "Display speech output"; the owner answers TalkBack's first-run notification permission prompt | Approved (Q2) |
| E-C | iPhone 16 simulator, MOCK build: standard, Large and AX5 text, light and dark, keyboard, long values, empty and error states. The signed-in session uses the **local** Firebase Auth emulator (test account, never production). Accessibility Inspector audit. Real VoiceOver is **Blocked** (needs a physical device) | Approved (Q3, Q4) |
| E-E | Sequential tests (never in parallel with the backend session's builds), both builds, repeat the affected device checks. Report: `docs/PORTFOLIO_UI_REFINEMENT_PHASE_E.md`; captures: `docs/design/portfolio_phase_e/` | Commit / push only on request |

The fixes go first, so the runtime passes check the fixed build once.

## 3. TalkBack journeys

1. Dashboard
2. Holdings
3. Allocation information
4. Transaction entry
5. Validation errors
6. Transaction history
7. Transaction details
8. Edit and delete confirmation
9. Success and error feedback
10. Account switching

Spoken-output evidence comes from screenshots of TalkBack's speech display. Static semantics inspection is reported separately and never counted as
TalkBack verification.

## 4. Constraints

- No change to calculations, ledger, API, entitlements or Firebase configuration.
- No production services; MOCK and the local emulators only.
- Stage by explicit path only; never `git add .`, `reset` or `clean`.
- No commit or push without a request.
