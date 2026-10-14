# Global UI Refinement Phase 5D.1A — Earnings currency accuracy follow-up

Date: 2026-10-13. Baseline: `c224890` plus the uncommitted Phase 5D.1 work. Status: **INVESTIGATION COMPLETE — no code changed.** Both issues
sit in code that runs on the server, so this follow-up's rules require stopping at a proposal: Issue A is a server-owned deterministic template,
and Issue B is a formatter the server also uses for AI grounding text and the digest, whose fix needs a new unknown-currency convention.

## Issue A — CAD Insights text shows "$"

**Observed** (Android, CNR.TO, `docs/design/earnings_phase5d_1/issue-a360-details-cnr-insight-usd-sign.png`): cards "C$1.94" / "C$2.00",
insight "EPS was $1.94 against an estimate of $2.00 (GAAP diluted)."

**Path:**
1. Source data: the event's `SurpriseResult` carries `currency = "CAD"` (the same value the cards use).
2. Generation: **server**, `EarningsService.kt:414` → `EarningsInsightEngine.generate` (deterministic template, no AI) in
   `core/.../earnings/EarningsCalculations.kt:120-170`.
3. Transport: `EarningsDetails.insights[].explanation`, a finished string in the API response.
4. Rendering: Android `StockInsightCard` and iOS show the string as received; neither platform builds it.

**Where the currency is lost:** `EarningsInsightEngine`'s private formatter
`money(v) = (if (v < 0) "−$" else "$") + …` (`EarningsCalculations.kt:171`) hard-codes "$" and never receives `eps.currency`, although it's
right there in `SurpriseResult`.

**Proposed fix (needs approval: changes server-generated text):** in `EarningsInsightEngine`, format with the existing
`EarningsFormatter.eps(value, eps.currency)` and delete the private `money`. Result: "EPS was C$1.94 against an estimate of C$2.00 (GAAP diluted)."
USD output stays "$1.94". No API or contract change (same field, text only). Needs a MOCK restart. Tests: CAD/USD/null explanation, negative EPS
("−C$0.05"), no double prefix. The client can't fix it safely: changing "$" inside a server sentence would be the blind string substitution
this phase forbids.

## Issue B — a missing currency is shown as "$"

**Formatter:** `EarningsFormatter.symbol(currency) = when (currency) { null, "USD" -> "$"; "CAD" -> "C$"; else -> "$currency " }`
(`core/.../earnings/EarningsPresentation.kt`). `eps()` and `revenue()` use it; `EarningsResultsFormat.eps/revenue` delegate to it.

**What null means (from the data contract):** case 1, **the source didn't provide a currency**. `EarningsEstimate.currency`,
`EarningsActual.currency`, `SurpriseResult.currency` and `PriceReaction.currency` are nullable with no documented default. In **REAL**, the
Finnhub calendar mapping (`server/.../earnings/EarningsSources.kt:77-79`) **always sets `currency = null`** for estimates and actuals, so every
REAL calendar figure, including TSX listings, would show "$". MOCK fixtures always set a currency (0 null in `fixtures/earnings/events.json`),
so this doesn't show on local devices. No call site loses a known currency in mapping (case 3 not found); 5D.1 removed the one `eps(it, null)`
call in the chart.

**Affected call sites (all Earnings-only, but some run on the server):**

| Where | Uses | Runs on |
|---|---|---|
| Details cards, history rows, chart tooltips, estimate sentence (`EarningsPresentation.kt`) | `EarningsFormatter.eps/revenue` | clients |
| Results cards (`EarningsResults.kt` `EarningsResultsFormat`) | delegates to the above | clients |
| Price-reaction closes (`EarningsPresentation.kt`, `EarningsPriceReaction.kt`) | `eps(close, r.currency)` | clients |
| StockSteps+ history chart (`EarningsPremiumPresentation.kt`) | same | clients |
| **AI grounding context** (`server/.../EarningsAi.kt`, 14 calls) | `EarningsResultsFormat.eps/revenue` | **server** |
| **Weekly digest** (`server/.../EarningsDigest.kt`, 2 calls) | same | **server** |
| Home / Company Details (`PersonalDashboard.kt`, `FinancialStatementsPresentation.kt`) | dates/countdown only, not amounts | clients (unaffected) |

**Why not implemented:** changing `symbol(null)` changes the text sent to the AI provider as grounding context (an AI-input change) and the
digest, and there is **no existing app-wide unknown-currency convention** to reuse. Both need owner approval under this phase's rules. The AI
validator only matches digits (`EarningsAiValidator.number`), so it wouldn't break.

**Proposed policy (for approval), smallest option first:**
- **B1 (recommended):** `symbol(null)` returns no symbol, so the number shows plain ("1.94", "4.45B"). Each card or section with a currency-less
  amount gets one caption, "Currency not provided by the source." (on the card, not repeated per number). Applies to clients and server
  (AI/digest text would then also stop asserting USD).
- B2: client-only. A new `EarningsFormatter.money(value, currency, unknown = …)` for client call sites, leaving server text unchanged.
  Inconsistent (the AI answer could still say "$"), so not recommended.
- Separate, larger (server): fill `currency` for REAL events from an authoritative source, the provider's reporting currency for that company
  (never the ticker suffix or exchange). API-adjacent; its own phase.

Tests planned with the fix: USD unchanged ("$1.25"), CAD ("C$1.25"), null (no symbol + caption), zero/negative, partial estimates, tooltip/row
consistency, E-01 labels unchanged, no double prefix, never a currency that wasn't supplied.

## Verification

| Check | Status | Evidence |
|---|---|---|
| Core JVM | NOT RE-RUN | no source change since the 5D.1 run: 486 / 0 |
| Core iOS | NOT RE-RUN | 486 / 0 (5D.1 run) |
| Shared Android | NOT RE-RUN | 229 / 0 (5D.1 run) |
| Shared iOS | NOT RE-RUN | 225 / 0 (5D.1 run) |
| Android build | NOT RE-RUN | OK (5D.1 run) |
| iOS build | NOT RE-RUN | OK (5D.1 run) |
| Android runtime | Issue A **reproduced** (FAIL, pre-existing) | `issue-a360-details-cnr-insight-usd-sign.png` |
| Android runtime, Issue B | NOT REPRODUCIBLE on MOCK | MOCK fixtures always carry a currency; REAL-only path (code evidence above) |
| iOS runtime | BLOCKED | simulator taps (see 5D.1) |

## Owner decisions

| ID | Decision | Recommendation |
|---|---|---|
| 5D1A-1 | Approve the server template fix for Insights (Issue A): use the existing formatter with `eps.currency` | Approve; small, no contract change |
| 5D1A-2 | Unknown-currency convention (Issue B): B1 plain number + one card caption, applied to clients and the server's AI/digest text | Approve B1 |
| 5D1A-3 | REAL currency enrichment for Finnhub events from an authoritative reporting currency | Separate later phase |

## Recommendation

**BLOCKED (owner approval needed)** for closing 5D.1's currency accuracy. Both issues are real and understood, and both fixes are small, but
they change server-generated or AI-input text (A) and introduce a currency convention (B). The 5D.1 changes themselves (E-01/E-02/E-03) are
unaffected and remain PASS WITH CONDITIONS.
