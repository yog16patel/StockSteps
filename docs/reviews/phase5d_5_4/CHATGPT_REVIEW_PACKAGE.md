# Review package: Phase 5D.5.4 Company Details (plan only)

- **Phase:** 5D.5.4 audit and plan.
- **Baseline:** `73a6c23` (5D.5.3 closed).
- **App code changed:** none.
- **Plan:** `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4_PLAN.md`.
- **Mockups:** `CHATGPT_COMPANY_DETAILS_REFERENCES.png` (6 PROPOSED mockups with sample data; sources in `docs/design/phase5d_5_4/references/`).
- **Owner reference:** `docs/design/phase5d_5_4/REFERENCE-company-details.webp`.

## Top findings
- **P0:**
  - iOS AX5 chart stats split words and values ("Op/en", "$33/3.74"), and the axis dates truncate.
  - The first viewport is mostly action buttons, so At a Glance starts on screen 2 (1.0×) or 3 (2.0×).
  - The iOS AX5 header tag is truncated.
- **P1:**
  - Every main-page section is bordered (owner: remove, as in earlier screens).
  - The same values repeat: P/E ×3, revenue growth ×4, sector/industry ×2, day High/Low ×2.
  - The Earnings card has 9 elements.
  - Learning entry points are scattered.
  - Key Ratios have no contextual Learn.
  - There is no section navigation on a ≈9-screen page.

## Proposed (display only; no data, calculation or route changes)
- **Order:** identity → price → borderless chart (Open · Volume) → one action row (Add to portfolio, Compare) → section bar → "What Apple does" →
  At a Glance → Understand → Why Did It Move? (compact) → Valuation & health → Earnings (compact) → News → About → Research further (Guided
  Research, Practice Buy).
- **Borderless at every Company Details call site;** the global card default is unchanged.
- **Large text:** single column, label above value, no truncated data.
- **Financials:** metric switcher; **Valuation:** historical P/E range in the top card. There is no industry P/E or employee count, because the data
  has neither.

## Phases
5D.5.4A header/chart/hierarchy → B Glance/Understand → C Financials/Valuation → D News/Earnings/spacing → E verification.

## Owner decisions
D-E1 section bar, D-E2 actions, D-E3 tags, D-E4 duplicates, D-E5 Why it moved, D-F1 Financials switcher, D-V1 P/E range, D-N1 Earnings, D-B1
borders (plan §K).

## Unverified
iOS signed-in (BLOCKED), screen readers (NOT RUN), AX XL and 1.3× not captured.
