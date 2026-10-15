# 5D.5.4C Financials and Valuation: decision summary (plan only)

- **Plan:** `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4C_PLAN.md`.
- **Mockups:** `CHATGPT_FINANCIALS_REFERENCE.png` and `CHATGPT_VALUATION_REFERENCE.png`. Each holds two BEFORE captures of the implemented
  screens and four "MOCKUP — NOT IMPLEMENTED" screens.
- **App code changed:** none.

## Top problems
- **Financials:** the key numbers are spread across six long sections; the best chart (revenue + net income) is buried in Profitability.
- **Valuation:** P/E dominates; the other five ratios sit in a plain list at the bottom.

## Proposal (display only)
- **Financials:** an Income · Balance Sheet · Cash Flow section bar; the selectors in one row; the revenue + net income chart at the top; four
  key-metric tiles (revenue, net income, gross and operating margin); every existing section, row, sheet and Learn link kept.
- **Valuation:** a six-ratio tile grid with formula captions; P/E vs History (average, current, existing chart, badge); one Learn link; the P/E
  explanation folded; a neutral footnote.

## Decisions (recommended, proceeding unless changed)
- **D-F1:** section bar yes.
- **D-F2:** move the two-series chart up.
- **D-F3:** no margin pp change (not in the data).
- **D-V1:** formula captions.
- **D-V2:** neutral footnote.
- **D-V3:** keep only the existing P/E history badge.

## Not followed from the reference
- Revenue $383.3B (the app shows $416.2B).
- "+0.8 pp YoY" margin changes.
- "10x sales" captions.

## Stages
C1 Financials → C2 Valuation → C3 responsive polish → C4 verification.
