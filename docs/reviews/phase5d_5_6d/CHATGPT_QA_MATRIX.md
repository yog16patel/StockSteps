# QA matrix: Phase 5D.5.6D (final navigation QA, KAN-15)

## Setup
- **Devices:**
  - Android: emulator-5554 (Pixel_9_Pro, 1280×2856), cached signed-in test session (no credentials typed); sample scenario "Multiple accounts"
    where holdings were needed;
  - iOS: iPhone 16 simulator as a guest (no approved test account).
- **Backend and counts:** local MOCK on :8083 behind a counting proxy on :8082. "N req" is what the proxy saw for the step.
- **Evidence:** names refer to `captures/` (git-ignored); "dump" means a UI-tree dump was read.

**Columns:**
- **Sev:** severity of a defect found by the test.
- **Fix:** a reference to §4 of the report.

## 1. Independent tab stacks
| ID | Plat | Scenario | Pre | Expected | Actual | Status | Evidence | Sev | Fix |
|---|---|---|---|---|---|---|---|---|---|
| A-and | Android | Markets → Apple → Financials (Quarterly) → Portfolio → Markets | signed in | Financials, Quarterly, Markets selected, 0 req | as expected; 0 req | PASS | dump (`selected` Quarterly), `and-A-fin-*` | | |
| A-ios | iOS | the same | guest | the same | as expected; 0 req | PASS | `ios-A-before/after` (tab bar rows only differ) | | |
| B-and | Android | Learn → Profit → term → Home → Learn | signed in | same term, scroll kept, 0 req | term and 0 req as expected; **scroll offset lost** on the term page | FAIL (P2) | `and-B-term-before/after` | P2 | open (§5) |
| B-ios | iOS | the same | guest | the same | term restored, 0 req (short page, scroll not exercised) | PASS | `ios-B-before/after` | | |
| C-and | Android | Portfolio → Holding → Markets → Portfolio → Back | signed in | Holding restored; Back → My Portfolio | pixel-identical, then pixel-identical landing; 0 req | PASS | `and-C-*` | | |
| C-ios | iOS | the same | guest | — | — | BLOCKED (guest has no holdings) | — | | |
| D-and | Android | Watchlist → Earnings reminders → Home → Watchlist | signed in | restored, no repeat request | restored; **open made 2 GETs, return 1 GET** → fixed: 1 GET, return 0 | FAIL → PASS | proxy log, `and-D-*` | P1 | F1, F2 |
| D-ios | iOS | Watchlist children | guest | — | — | BLOCKED (empty guest watchlist; reminders need sign-in) | — | | |
| E-and | Android | Home → Daily Brief → Markets → Home | signed in | Brief restored, 0 req | restored; **return refetched the personal section (and cleared AI answers)** → fixed: 0 req | FAIL → PASS | proxy log, `and-E-*` | P1 | F2 |
| E-ios | iOS | the same | guest | the same | restored, 0 req | PASS | `ios-E-before/after` | | |

## 2. Canonical tab and cross-tab Back
| ID | Plat | Scenario | Expected | Actual | Status | Evidence |
|---|---|---|---|---|---|---|
| S1-and | Android | Watchlist → Apple → P/E term → Back → Back | Watchlist → Markets → Learn → Markets → Watchlist | as expected | PASS | dumps, `and-S1-*` |
| S1-ios | iOS | the same, second Back by swipe | the same | as expected; "‹ AAPL" on the term; 0 req | PASS | `ios-S1-*` |
| S2-and | Android | Home → Apple → Financials → term → Back ×3 | Learn → Markets → Markets → Home | as expected | PASS | dumps |
| S2-ios | iOS | the same, with Apple reused from Markets | the same | "‹ Financials", Financials, Apple ("‹ Home", scroll kept), Home | PASS | `ios-S2-*` |
| S3-and | Android | Portfolio → Holding → Company Details → Back → Back | Markets → Holding (Portfolio) → My Portfolio | as expected | PASS | dumps, `and-S3-company` |
| S3-ios | iOS | the same | — | — | BLOCKED (guest, no holdings) | — |
| S4-and | Android | Markets → Apple → Home → Apple → Back | Apple reused (no duplicate), Back → Home, Markets intact | as expected; only the search request | PASS | `and-S4-*` (identical) |
| S4-ios | iOS | the same, plus Markets afterwards | the same, and Markets' Apple keeps data and position | **Markets' Apple was rebuilt: 6 refetches, scroll lost** → fixed: 0 page requests, position kept | FAIL → PASS | proxy log, `ios-S4-*` |
| S4b-ios | iOS | reused Apple → two rapid edge swipes | one Back to Home, no double pop, Markets intact | as expected; 0 req | PASS | `ios-swipe-*` |
| S5-and | Android | Home → Apple (hop) → tap Learn → tap Markets → Back → Back | Markets root, then Home (no jump to the old origin) | as expected | PASS | dumps |
| S5-ios | iOS | the same | the same | covered in C2/C3 (unchanged code path) | NOT RUN | — |

## 3. Reselection
| ID | Plat | Scenario | Expected | Actual | Status |
|---|---|---|---|---|---|
| R1-and | Android | each tab, from a deep screen (Financials, term, brief, reminders, holding) | pops to root, other stacks kept, no duplicate req | as expected (4 req = fresh entries and Home's 60 s refresh) | PASS |
| R2-and | Android | each tab at its root after scrolling | top | Home, Markets, Portfolio, Learn top (Learn by screenshot); Watchlist content fits | PASS |
| R1-ios | iOS | Markets from Apple; Learn | root, then scroll + reselect → top; 0 req | as expected | PASS |
| R3 | both | reselect while a modal is open | no tab bar on modals, so not reachable | n/a by design | PASS (design) |

## 4. Portfolio nested tabs (C4 final)
| ID | Plat | Scenario | Actual | Status |
|---|---|---|---|---|
| P1 | Android | My Portfolio ↔ Practice | in place; Practice 2 req once, 0 after | PASS |
| P2 | Android | Practice → Markets → Portfolio | Practice kept | PASS |
| P3 | Android | Holding → Back | My Portfolio, pixel-identical | PASS |
| P4 | Android | Practice → Buy → order → Back | Practice selected | PASS |
| P5 | Android | order → tap Portfolio | Practice landing | PASS |
| P6 | Android | reselect at the root (Practice) | top, section kept | PASS |
| P7 | Android | switch after scrolling | pinned tabs; each section keeps its scroll | PASS (C4 D, re-seen in `p-and-dark-practice-scrolled`) |
| P1–P3, P6 | iOS | guest: switch, Markets round trip, reselect | as expected; 0 req | PASS |
| P8 | both | account switch while on Practice | — | BLOCKED (no second test account) |
| P9 | both | sign out on a Portfolio child | — | BLOCKED (no approved account to sign back in) |
| P10 | iOS | guest → Practice → Sign in (modal) → close → Practice | as expected | PASS |
| P10 | Android | the same | — | NOT RUN (emulator is signed in) |
| P11 | iOS | live Dynamic Type change on Practice | **title and guest message stayed small** → fixed | FAIL → PASS |

## 5. Global modals and search
| ID | Plat | Scenario | Actual | Status |
|---|---|---|---|---|
| M1 | Android | Settings / Notifications over Home and over the Portfolio landing | no tab bar; dismissal restores pixel-identical | PASS |
| M1 | iOS | the same (sheets) | no tab bar; Done restores | PASS |
| M2 | iOS | guest My Alerts (bell) | **placeholders never finished** (pre-existing since C2) → fixed: sign-in card; Sign in closes the sheet and opens sign-in | FAIL → PASS |
| M3 | both | Home → Search → Apple | search dismisses, Apple under Markets, Back → Home | PASS |
| M4 | Android | Practice company picker → order | modal, then the order on Portfolio | PASS |

## 6. Entry points (notification links; there are no app or universal links)
| ID | Plat | Link | Actual | Status |
|---|---|---|---|---|
| L1 | Android | `daily-brief` (with and without an id), from Learn | brief under Home; Back → Learn | PASS |
| L2 | Android | `earnings-*` (event fallback) | Earnings Calendar under Markets; Back → Learn | PASS |
| L3 | Android | `alert` + symbol | AAPL alerts under Watchlist; Back → Learn | PASS |
| L1–L3 | iOS | the same payloads | needs a delivered notification tapped; notifications are off for the app and the prompt isn't bypassed | NOT RUN |

## 7. Lifecycle and state restoration
| ID | Plat | Scenario | Actual | Status |
|---|---|---|---|---|
| X1 | Android | background → foreground | 0 req, same screen | PASS |
| X2 | Android | rotation (emulator rotate ×4) | Practice selected, 0 req | PASS |
| X3 | Android | process death (`am kill` in background) → relaunch | stacks (Financials, term) and Practice section restored; 11 cold-start req; Financials period back to Annual (held in memory) | PASS (period: P2 note) |
| X1 | iOS | background → foreground | 0 req | PASS |
| X4 | both | Home re-entry after > 60 s | `/me/alerts` + `/me/watchlists` (Home's documented 60 s freshness rule) | PASS (expected) |

## 8. Accessibility and large text
| ID | Plat | Check | Actual | Status |
|---|---|---|---|---|
| A11y-1 | Android | Practice tabs at 2.0× | broke inside words → whole words, horizontal scroll with fade, `Role.Tab` + selected | FAIL → PASS |
| A11y-2 | iOS | Practice title at AX XL / AX5 | didn't grow → grows (cold and live) | FAIL → PASS |
| A11y-3 | both | nested tabs, landing bars, dark | readable; tab-group semantics (C4) | PASS |
| A11y-4 | both | TalkBack / VoiceOver walkthrough | — | NOT RUN (owner, manual) |
