# Consolidated launch-readiness backlog (after Phase 8D)

None of these items is resolved by 8D unless it says so.

**Severity:**
- **B:** blocks public launch.
- **H:** high, fix before launch if possible.
- **M:** medium.
- **L:** post-launch.

## A. UI/UX launch blockers

| Item | Sev | Impact | Evidence | Owner | Next phase | Blocks launch |
|---|---|---|---|---|---|---|
| Owner decisions D8A-1..3, D8B-1..2, D8C-1..3, D8D-1..2 | M | layout and copy choices pending approval | 8A–8D packages | owner | review | no |
| Company Search error copy blames the connection for provider errors (seen on Pixel 7 REAL: 502 PROVIDER_UNAVAILABLE → "Check your connection") | H | misleading error message | `ui_ux_phase8b/captures/real-pixel7/` | mobile | proposed session "Search error copy for provider outages on REAL" | no (copy) |
| Company Details with no profile: "AAPL / AAPL" header and an empty "Understand AAPL" heading on REAL | M | sparse page looks broken | real-pixel7 frames | mobile | 8E / polish | no |
| Watchlist add takes three steps (Search → Details → star) | L | friction | 8C audit | product | post-launch | no |

## B. Accessibility blockers

| Item | Sev | Impact | Evidence | Owner | Next phase | Blocks launch |
|---|---|---|---|---|---|---|
| TalkBack pass not run (focus can't be driven by injected input; the first TalkBack start needs a permission the owner must answer) | B | screen-reader users unverified | 8A–8D checklists | owner (manual) | manual a11y pass | **yes** (store accessibility review) |
| VoiceOver pass not run (needs a device or manual simulator interaction) | B | same | same | owner (manual) | manual a11y pass | **yes** |
| Signed-in iOS large-text screens (My Portfolio, Practice with data) not seen | H | possible AX5 issues on data screens | 8C/8D BLOCKED | owner (test sign-in) | device pass | no |

## C. Financial correctness blockers

| Item | Sev | Impact | Evidence | Owner | Next phase | Blocks launch |
|---|---|---|---|---|---|---|
| Corporate actions incomplete (split-like moves only warned, never adjusted) | B | wrong P/L across splits | 7C `CORPORATE_ACTIONS_GAPS.md` | backend/finance | corporate-action phase | **yes** for real-money tracking |
| Mixed-date FX / accounting questions (basis FX on trade date vs valuation FX) | H | totals may differ from broker statements | 7C/7D docs, missing-FX scenario | finance | accounting review | to decide |
| Allocation denominator proposal (`PORTFOLIO_ALLOCATION_DENOMINATOR_PROPOSAL.md`) | M | % meaning | proposal doc | owner | separate phase | no |

## D. Market-data licensing

| Item | Sev | Impact | Evidence | Owner | Next phase | Blocks launch |
|---|---|---|---|---|---|---|
| FMP Data Display and Licensing Agreement (display and caching rights), including logo display | B | legal right to show data and logos | Phase 6 Q4, 5D.5.5D | owner/legal | licensing | **yes** |

## E. Backend security

| Item | Sev | Impact | Evidence | Owner | Next phase | Blocks launch |
|---|---|---|---|---|---|---|
| Production deployment, Firestore rules and secrets review not done for a public launch (only the LAN deployment exists; Cloud Run staging deferred) | B | exposure | handoff | backend | production-readiness | **yes** |

## F. API quota and cost

| Item | Sev | Impact | Evidence | Owner | Next phase | Blocks launch |
|---|---|---|---|---|---|---|
| Phase 7E enrichment budget: ≈ 310 requests a day per instance vs a 250-a-day key | B | quota exhaustion; Discover prices never load | 7E `KNOWN_LIMITATIONS.md` §6 | owner/backend | budget decision (a `discover-quota-fix` session exists) | **yes** on the current plan |
| FMP key at its limit (429) during every REAL check | B | REAL prices, indices, charts and search unavailable | Pixel 7 REAL check 2026-10-17 | owner | provider plan | **yes** |

## G. REAL device verification gaps

| Item | Sev | Impact | Evidence | Owner | Next phase | Blocks launch |
|---|---|---|---|---|---|---|
| REAL signed-in Android and iOS flows with live prices (Portfolio valuation, Practice trade at EOD close) | H | unverified under real data | Pixel 7: signed-in user data 200, prices unavailable (429) | owner | device pass after quota | yes until verified |
| Practice trade preview, result and history on a device | H | execution UI unverified on device | 8C NOT RUN | owner | device pass | no |
| Firestore emulator coverage final confirmation (last run 7D: 565/0) | M | regression safety | 7D | backend | before launch | no |

## H. Post-launch enhancements

| Item | Sev |
|---|---|
| Chart Y-axis currency labels (8B P2) | L |
| Two "Add" entries on My Portfolio (8C P2) | L |
| Learn reading times or difficulty (needs catalogue fields) | L |
| Practice empty-state screenshot and the order flow at AX5 | L |
