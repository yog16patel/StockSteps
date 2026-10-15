# StockSteps — project status

Last reviewed: 2026-10-14 against the repository (`main`, HEAD `a15cd5f` "Add Global UI Refinement Phase 5D.1 Earnings accuracy …"; earlier: "Record the Ubuntu MOCK backend deployment and Phase 3B Android and iOS connectivity verification against the LAN backend" (docs only), on top of `3d91ef3` (Portfolio Phase E) and `fa7c563` (backend Phase 1); the working tree also holds uncommitted parallel-session work). Verify with
`git log`/`git status` before relying on this file. Per-feature details live in `docs/*.md`; the
milestone log and validation history are in `PROJECT_HANDOFF.md`.

## 0. Current task and next steps (read first)

**2026-10-15 — Phase 5D.5.5C Company Search experience (committed locally as "Refine Company Search for Global UI Refinement Phase 5D.5.5C with a cleaner Android search header, local recent searches, search states with Retry and the shared result rows in the Comparison picker on Android and iOS"; not pushed).**
- **Android header:** cleaner (the headline is removed).
- **Recent searches:** local, max 10.
- **States:** no results, error + Retry.
- **Comparison picker:** on the shared row.
- **Not changed:** ranking and API.
- **Report:** `docs/COMPANY_SEARCH_REFINEMENT_PHASE5D_5_5C.md`.

**2026-10-15 — Phase 5D.5.5B Company Search ranking and result rows (`08723a6`, committed and pushed as "Add Company Search ranking and result rows for Global UI Refinement Phase 5D.5.5B with deterministic server-side relevance, a conservative Best match label and borderless search result rows on Android and iOS, plus the 5D.5.5A search audit and plan").**
- Deterministic server-side ranking (`SearchRanking`, core).
- Conservative Best match and the new result rows on Android and iOS.
- USD/US scope copy fixes.
- Plan: `docs/COMPANY_SEARCH_REFINEMENT_PHASE5D_5_5_PLAN.md`; report: `docs/COMPANY_SEARCH_REFINEMENT_PHASE5D_5_5B.md`.
- **Next:** push on approval, then 5D.5.5C.

**2026-10-15 — Phase 5D.5.4D Company Details accessibility & final verification (`0c5c4c4`, committed and pushed as "Finish Company Details accessibility for Global UI Refinement Phase 5D.5.4D with section header actions under long titles, a stacking Financials chart legend and whole Financial Highlights and Key Ratios values at large text on iOS, and the final verification record"; not pushed).**
- **iOS AX5 fixes:**
  - Financials legend stacks;
  - section header action moves under the title;
  - Financial Highlights and Key Ratios values stay whole.
- **Accessibility:** code audit and final QA (tabs, reading order) PASS. TalkBack, VoiceOver and Android guest are BLOCKED (report §9).
- **Tests:** all suites PASS.
- **Devices:** checks recorded in `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4D.md`.
- **Next:** push on approval; the owner screen-reader pass; then close 5D.5.4.

**2026-10-15 — Phase 5D.5.4C C3 Financials & Valuation polish (`ebe81f4`, committed and pushed as "Polish Company Details Financials and Valuation for Global UI Refinement Phase 5D.5.4C C3 without the duplicate P/E row, with a measured range grid, row groups that keep values whole and P/E kept whole at large text on Android and iOS").**
- **Valuation & Growth:** the duplicate P/E row is removed.
- **Range stats:** a 2 × 2 grid, aligned rows at large text.
- **iOS row groups** stack together at AX5 ("+75.5%" and "Operating Cash" whole).
- **"P/E"** stays whole in Valuation text.
- **iOS chart labels** no longer run together.
- **Tests:** shared 263/0 and 259/0.
- **Devices:** Android and iOS checks PASS (`docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4C_C3.md`).
- **Next:** 5D.5.4D (see above).

**2026-10-15 — Phase 5D.5.4C C2 Valuation (`e8265bd`, committed and pushed as "Refine Company Details Valuation for Global UI Refinement Phase 5D.5.4C C2 with ratio tiles first, a P/E vs History card, one Learn link, a folded P/E explanation and a neutral ratio footnote on Android and iOS").**
- **Layout:**
  - ratio tiles first;
  - P/E vs History (comparison only with a reliable average);
  - one Learn link;
  - Understanding P/E folded;
  - Growth, Range and Insight kept;
  - D-V2 footnote.
- **Large-text fixes:** iOS badge fallback, "P/E" kept whole, iOS chart dates scale.
- **Tests:** shared 262/0 and 258/0.
- **Devices:** Android and iOS checks PASS (`docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4C_C2.md`).
- **Next:** C3 polish (see above), then C4 verification.

**2026-10-15 — Phase 5D.5.4C C1 Financials (`33fa94c`, committed and pushed as "Refine Company Details Financials for Global UI Refinement Phase 5D.5.4C C1 with an Income, Balance Sheet and Cash Flow section bar, one selector row, the revenue and net income chart and key-metric tiles first on Android and iOS").**
- Section bar, one selector row, the revenue + net income chart and key-metric tiles first; all sections and sheets kept.
- Report `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4C1.md`; plan `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4C_PLAN.md`.
- **Next:** C2 Valuation (see above).

**2026-10-15 — Phase 5D.5.4B r2 reference refinement (committed and pushed as "Refine Company Details for Global UI Refinement Phase 5D.5.4B r2 with tiled At a Glance metrics, assessment pills on the title line, Beginner Insight inside Understand and a Why Did It Move section led by the observed move and possible reasons on Android and iOS"; not pushed).**
- Tiled At a Glance; pills on the title line; Beginner Insight inside Understand.
- Why Did It Move? starts with the observed move, then "Possible reasons" (verbatim hedged text).
- Report `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4B.md` §11.
- **Outstanding:** iOS AX XL; screen readers; iOS signed-in. **Next:** push on approval, then 5D.5.4C.

**2026-10-15 — Phase 5D.5.4B Understand and Why Did It Move? (committed and pushed as "Refine Company Details Understand rows and Why Did It Move for Global UI Refinement Phase 5D.5.4B with category, interpretation, one supporting fact and a note per row, an explanation sheet on every row, a bold lead sentence and one P/E comparison on Android and iOS").**
- Understand rows: category, tinted interpretation, one fact, note; every row opens its explanation and Learn link.
- P/E vs average only in the Valuation card; duplicate "Understand P/E Ratio" removed.
- Why Did It Move?: bold lead sentence plus the verbatim rest.
- Report `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4B.md`.
- **Outstanding:** screen readers; iOS signed-in. **Next:** push on approval, then 5D.5.4C.

**2026-10-15 — Phase 5D.5.4A Company Details hierarchy CLOSED (owner approval with conditions).**
- Implementation `0fbce92` (pushed). Closure verification: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4A.md` §8.
  - Shared iOS tests 246/0; badge-rule audit (only the Financial Health badges move inline).
  - Section bar, Back and Learn PASS on both platforms; Android 1.3× and iOS AX XL PASS.
- **Outstanding:**
  - iOS live Dynamic Type;
  - TalkBack NOT RUN, VoiceOver BLOCKED;
  - signed-in iOS BLOCKED;
  - physical devices NOT RUN;
  - scroll profiling NOT RUN.
- **Next:** 5D.5.4B after owner approval.

**2026-10-15 — Phase 5D.5.4A Company Details hierarchy (committed and pushed as "Refine Company Details for Global UI Refinement Phase 5D.5.4A with a borderless chart and day range, one action row, a pinned in-page section bar, a What it does row, a beginner-first section order and borderless sections on Android and iOS").**
- Header without tags; borderless chart with Open/Volume and the Day Range.
- One action row; Practice Buy in "Research further".
- Pinned in-page section bar (Overview · Financials · News · About).
- "What … does" row; new order; borderless sections.
- Long badges share the title line (shared rule 14 → 20 characters).
- Report `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4A.md`; plan `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4_PLAN.md`.
- Closed; see the entry above.

**2026-10-15 — Phase 5D.5.3 contextual learning CLOSED (owner approval).**
- Implementation `3b7a7f5` (pushed). Closure verification: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_3.md` §8.
  - Android dark via the in-app theme: PASS.
  - Mapped and unmapped sheets: consistent.
  - Accessibility in code and UI tree: PASS.
- **Outstanding:**
  - iOS live Dynamic Type refresh (existing limit);
  - TalkBack NOT RUN, VoiceOver BLOCKED;
  - iOS signed-in regression BLOCKED;
  - Android "Key Ratios" contextual-learning opportunity;
  - intermittent `PortfolioSessionStateTest` timeout.
- **Next:** Global UI Refinement step 7 after owner direction.

**2026-10-15 — Phase 5D.5.3 contextual learning (committed and pushed as "Add contextual Learn links for Global UI Refinement Phase 5D.5.3 with an info mark on metrics that have a Learn term, explanation sheets for Financials and Valuation rows, and Learn more buttons that open the term page on the same stack on Android and iOS").**
- Company Details glance tiles, Financials rows and Valuation rows explain their metric; metrics with a Learn term show ⓘ and "Learn more about
  {term}", which opens the term page on the same stack (Back returns to the stock).
- Mapping by id only in core `MetricLearnTerms` (tested); no new educational text, no backend changes.
- Report `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_3.md`; review package `docs/reviews/phase5d_5_3/`.
- Closed; see the entry above.

**2026-10-15 — Phase 5D.5.2 Learn CLOSED (owner approval).**
- Implementation `057d6e6` + `0730773` (pushed). Final verification and limitations: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_2.md` §7.
- **Outstanding:** iOS signed-in progress BLOCKED; screen readers and smaller devices NOT RUN.
- **Follow-ups:**
  - iOS AX5 term-footer icon alignment (minor);
  - investigate the intermittent `UserDataRepositoriesTest`;
  - decide on the `PortfolioSessionStateTest` fix (worktree `charming-hodgkin-98e850`).
- **Next:** Global UI Refinement step 7 (Settings, auth, remaining screens) after owner direction.

**2026-10-15 — Phase 5D.5.2 Learn polish (committed and pushed as "Polish Learn for Global UI Refinement Phase 5D.5.2 with ranked term search, single-column topics at very large text, a folded Start here sentence at large text and a shorter term illustration on Android and iOS").**
- Changes:
  - ranked term search;
  - single-column topics at very large text;
  - a folded Start here sentence at large text;
  - a shorter illustration.
- Tests: core 534/0 ×2, shared 246/0 and 242/0, server 501/0. Device checks on Android and iOS.
- Package: `docs/reviews/phase5d_5_2_r2/`.

**2026-10-15 — Phase 5D.5.2 Learn redesign (committed and pushed as "Redesign Learn for Global UI Refinement Phase 5D.5.2 with a Start here card, colour-coded topic tiles, today's concept, an Explore topics list, topic pages, term reading pages and a local term search on Android and iOS").**
- Changes:
  - Start here card;
  - colour-coded topic grid;
  - Today's concept;
  - Explore topics, Topic and Term reading pages;
  - local term search.
- Built from the existing catalogue only.
- Tests: core 532/0 ×2, shared 246/0 and 242/0, server 501/0; builds OK. Device checks on Android and iOS.
- Review package: `docs/reviews/phase5d_5_2/`. Report: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_2.md`.
- PASS WITH CONDITIONS (screen readers, signed-in progress and iPhone SE NOT RUN).

**2026-10-14 — Review workflow.**
- `docs/AI_REVIEW_WORKFLOW.md` defines the compact ChatGPT review package that ends each approved implementation phase:
  - stored in `docs/reviews/<phase-id>/`;
  - full-resolution originals and logs in its git-ignored `captures/`.
- `CLAUDE.md` requires reading it.

**2026-10-14 — Phase 5D.5.1c Daily Brief final polish; Daily Brief CLOSED (committed and pushed as "Polish the Daily Brief for Global UI Refinement Phase 5D.5.1c with a borderless story group, a compact header with session status under Markets, a secondary Add companies action, matching footer links and a tighter concept card on Android and iOS").**
- Changes:
  - borderless story group;
  - compact header ("Updated Oct 7"; session lines under Markets);
  - secondary "Add companies" action;
  - matching footer links;
  - tighter concept card;
  - Android reader top padding removed.
- Tests: core 522/0 ×2, shared 246/0 and 242/0, server 501/0; both builds OK. Device matrix on Android and iOS (local MOCK).
- Verdict: PASS WITH CONDITIONS (iOS signed-in sections BLOCKED; screen readers, populated watchlist and Plus NOT RUN).
- Report: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_1C.md`.
- **Next:** Phase 5D.5.2 Learn after owner approval (D-7).

**2026-10-14 — Phase 5D.5.1b Daily Brief refinement A+B (committed and pushed as "Refine the Daily Brief for Global UI Refinement Phase 5D.5.1b with a data-derived market takeaway, compact index tiles, one status banner, a grouped story card, a compact watchlist empty state and structured earnings rows on Android and iOS").**
- Display only:
  - a takeaway headline derived from the index data (no magnitude or cause words; holiday-aware; never "today" for an older session), with the
    server summary verbatim below it;
  - compact index tiles at normal text, rows at large text;
  - one stale/offline banner;
  - a grouped story card;
  - an "Add companies" empty state;
  - structured earnings rows;
  - Home preview = takeaway.
- Two device fixes. Tests: core 522/0 ×2, shared 246/0 and 242/0, server 501/0; both builds OK.
- Device matrix PASS (local MOCK). Verdict: PASS WITH CONDITIONS.
- Report: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_1B.md`; plan: `docs/DAILY_BRIEF_UI_REFINEMENT_PLAN.md`.
- **Next:** Phase C (approval) or 5D.5.2 Learn.

**2026-10-14 — Phase 5D.5.1 Daily Market Brief reader (committed and pushed as "Refine the Daily Market Brief reader for Global UI Refinement Phase 5D.5.1 with the summary as the headline, shared index rows, first-sentence stories, folded notes, a Sample badge and a one-line Home summary on Android and iOS").**
- Display only, on Android and iOS: the summary is the headline; one meta line with a Sample badge; shared compact index rows; stories show the
  first sentence with "Show details"; notes fold once; a ⋯ menu; relative times; a skeleton while loading. Home adds the summary's first sentence.
- Five device fixes, including a pre-existing negative-number format bug ("0.-42" → "−0.42").
- Tests: core 514/0 ×2, shared 245/0 and 241/0, server 501/0; both builds OK. Device matrix PASS on Android and iOS (local MOCK).
- Verdict: PASS WITH CONDITIONS. Report: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_1.md`; plan: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_PLAN.md`.
- **Next:** 5D.5.2 Learn (owner decision D-7 first).

**2026-10-14 — Session end #2 (Earnings).** HEAD `7326e62` (pushed). Earnings Phases **5D.2, 5D.3, 5D.4, 5D.4.1, 5D.4.2 and 5D.4.3 are committed and pushed**
(PASS WITH CONDITIONS; display-only, no calculation/API/navigation change). Final 5D.4.3 review: close as PASS WITH CONDITIONS. Only open item: **iOS Earnings
Details BLOCKED pending the owner's sign-in** on the local Auth emulator. Tests: shared 245/0 and 241/0, core 502/0 ×2 (core unchanged since), both builds OK.
**Next:** iOS Details verification after sign-in → Phase 5D.5 (Daily Brief, Learn) read-only audit. Full handoff: `docs/PROJECT_HANDOFF.md`
§SESSION-END-2026-10-14-EARNINGS. (The backend session's earlier uncommitted edits to this file and root `PROJECT_HANDOFF.md` were no longer in
the working tree at session end; not touched here.)

**2026-10-14 — Phase 5D.3 Earnings visual polish (committed as "Add Global UI Refinement Phase 5D.3 Earnings visual polish with an EPS headline, key metric groups, a reaction headline with folded methodology, a Sample badge and collapsed sources on Android and iOS", on top of `3d7b000` = 5D.2; both pushed).** Details and Results now lead with key metrics, a built
EPS headline, a reaction headline with the details collapsed, a Sample badge and a collapsed sources section. All suites pass; Android device PASS;
iOS NOT RUN. Report `docs/GLOBAL_UI_REFINEMENT_PHASE5D_3.md`. Recommendation: PASS WITH CONDITIONS.

**2026-10-14 — Phase 5D.2 Earnings design-system and layout refinement (committed as "Add Global UI Refinement Phase 5D.2 Earnings layout refinement with whole price-reaction labels from a shared label and value rule, design-system badges, banners and buttons, lesson-named links and an iOS EPS history chart on Android and iOS").** The price-reaction "Window"/"Source" break is fixed by the
shared `StockLayout.labelColumn` + `EarningsLines` (both platforms), along with design-system badges, banners and buttons, sentence case and an iOS history chart.
All suites pass and both builds succeed; Android device PASS; iOS runtime NOT RUN (Details BLOCKED by the signed-in session). Report
`docs/GLOBAL_UI_REFINEMENT_PHASE5D_2.md`. Recommendation: PASS WITH CONDITIONS. Next: visual-polish plan (5D.3) awaits owner approval.

**2026-10-14 — Session end.** HEAD `a15cd5f` (pushed). Portfolio refinement closed (E.2 accepted PASS WITH CONDITIONS; follow-ups QA-E2-1…3 open).
Earnings Phase 5D.1 (+1A/1B currency) committed, PASS WITH CONDITIONS (iOS runtime blocked by simulator taps; unknown currency and E-02 unit-tested only;
dark theme not run). **Next: Phase 5D.2 Earnings design-system alignment — plan first, owner approval before code.** Full handoff:
`docs/PROJECT_HANDOFF.md` §SESSION-END-2026-10-14. Tests at HEAD: core 492/0 (JVM, iOS), shared 229/0 / 225/0, server 501/0 (3 skipped), both builds OK.

**2026-10-13 — Global UI Refinement Phase 5D.1 Earnings accuracy (committed and pushed as "Add Global UI Refinement Phase 5D.1 Earnings accuracy with one BEAT, MET and MISS badge, placeholder-free estimate sentences, currency-correct history tooltips and Insights, and no invented dollar sign for a missing currency on Android and iOS"; PASS WITH CONDITIONS).** One BEAT/MET/MISS badge on Results and
Details (`EarningsDisplay`), one estimate sentence and currency-correct history tooltips from core. Suites pass; Android device PASS; iOS runtime BLOCKED.
Report `docs/GLOBAL_UI_REFINEMENT_PHASE5D_1.md`. Follow-up 5D.1A (investigation only): CAD Insights "$" comes from the server template; a null
currency shows as "$" (REAL Finnhub data has none). Fixes approved and implemented in 5D.1B (`docs/GLOBAL_UI_REFINEMENT_PHASE5D_1B.md`): CAD Insights now "C$", and a null currency shows no symbol plus
a caption. All suites pass, including server.

**2026-10-13 — Portfolio Phase E.2 (committed and pushed as "Add Portfolio Phase E.2 with a readable iOS account title, the trade currency shown with the amounts, and a Session expired banner with Sign in again on Android and iOS"; verification in progress).**
- "New investment account" (inline iOS title), Trade currency in Amounts with a reporting-currency note, "Session expired" + "Sign in again",
  Add unavailable only for an expired session. Report `docs/PORTFOLIO_UI_REFINEMENT_PHASE_E_2.md`.
- Suite after the commit: core 478/0 (JVM, iOS), shared 226/0 (Android) and 222/0 (iOS), server 499/0; both builds OK. Device checks mostly
  PASS (report §3). Re-sign-in, iOS AX5 title, edit/history currency: BLOCKED or NOT RUN. Follow-up C5 + report committed as "Record Portfolio Phase E.2 verification with all suites passing, session-expired and currency device checks, and keep Session expired after dismissing a change rejected by an ended session". Screen readers NOT VERIFIED.

**2026-10-13 — Portfolio session end (Portfolio D1.1 → E.1 committed and pushed).**
- HEAD `82fd22e`: "Record Portfolio Phase E.1 iOS signed-in verification with all add, edit, delete, rejection, sample, account-switch and navigation
  checks passing on the iPhone 16 simulator".
- Full handoff: `docs/PROJECT_HANDOFF.md` §SESSION-END-2026-10-13-PORTFOLIO.
- **Portfolio state:**
  - Transaction history (D2) and Transaction details with confirmed-only success feedback (D3) are done.
  - Stabilization (D3.1) is done: allocation note, "-USD 51.50 / Unfunded" cash lines, Portfolio tab selection on sub-screens, errors titled by
    action, samples without actions.
  - Accessibility fixes (E) are done: iOS error announcements, unbroken "P/L", single announcements.
  - Session-ended message fix (E.1) is done.
  - iOS signed-in workflows: all PASS on the iPhone 16 simulator (local Auth emulator + MOCK).
- **Phase E.1 verdict: NOT READY FOR SIGN-OFF** (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_E_1.md`). **NOT VERIFIED:**
  - TalkBack (deferred by the owner until requested);
  - VoiceOver (physical device);
  - Accessibility Inspector.
- **Tests (last full run, code unchanged since):** core 474/0 (JVM, iOS), server 499/0 (3 skipped, run alone), shared 219/0 (Android) and 215/0
  (iOS); `assembleDebug` OK; unsigned `xcodebuild` OK.
- **Pending decisions:** the allocation denominator (`docs/PORTFOLIO_ALLOCATION_DENOMINATOR_PROPOSAL.md`, A1 gross long value recommended; no engine
  change yet).
- **Known P3:** the iOS "Create investment account" title is truncated at Large text.
- **Next:**
  1. Owner decision on the allocation denominator.
  2. Phase E closure (screen readers when requested, or owner acceptance).
  3. Phase 5D.

**2026-10-13 — Ubuntu self-hosted MOCK backend (parallel backend session).** Details: `docs/UBUNTU_BACKEND_VERIFICATION_2026-10-13.md` (§7: Phase 2 record) and `docs/UBUNTU_MOBILE_INTEGRATION_VERIFICATION.md` (Phase 3B); committed in
"Record the Ubuntu MOCK backend deployment and Phase 3B Android and iOS connectivity verification against the LAN backend" and `PROJECT_HANDOFF.md` (top entries).
- **Deployed on the LAN Ubuntu host** (2026-10-13 21:30 UTC): commit `fa7c563`, image `stocksteps-local:fa7c563c94a9` (plain commit SHA, built from a clean
  `git worktree`), Compose project `stocksteps-local`, `STOCKSTEPS_DATA_MODE=mock`, published on the host's LAN IP port 8081 only. Rollback:
  `deploy/local/deploy.sh rollback a50a2e3cc833`.
- **Committed and pushed:** Phase B sample company catalog (`231a0a1`: 137 US companies, screener 160 = 152 US + 8 Canadian), Stabilization Phase 1
  (`fa7c563`: MOCK quote timestamps follow the advancing MOCK clock; fixes 409 `QUOTE_STALE` practice trades and stale price alerts ≈16 h after a start).
- **Verified on the host after deploy:** health 200/200/200; smoke 23/23; reliability PASS (peak 132 MiB of 1 GiB, crash restart healthy in ≈10 s);
  screener 152 US of 160; practice trade (COST) and price alert (triggered 1, stale 0); FMP/Finnhub/Gemini upstream 0; all 8 Immich/Nextcloud
  containers unchanged. Tests on `fa7c563`: server 499 run / 0 failed / 3 skipped.
- **Not yet verified on the host:** freshness after ≥ 16 h of real uptime. Run `deploy/local/deploy.sh smoke` after ≈ 13:50 UTC on 2026-10-14
  without restarting the container (it last restarted 21:30:36 UTC during the reliability check).
- **Phase 3B (2026-10-13, isolated devices): PASS WITH CONDITIONS.** See `docs/UBUNTU_MOBILE_INTEGRATION_VERIFICATION.md` and `docs/design/ubuntu_mobile/`.
  - Android `emulator-5556` and the iOS iPhone 16 Pro simulator, both guest, both against Ubuntu: every signed-out journey passes except the iOS screener's first
    open after a REAL → Mock switch (stale error; "Try again" works).
  - Signed-in journeys, physical devices and the long-uptime smoke are **NOT TESTED**.
  - `~/.gradle/gradle.properties` was removed; Android uses a per-build `-PstockstepsMockBackendUrl`.
  - Issues: iOS screener stale error; MOCK catalog lacks `payoutRatio` (Dividend preset leaves out 76/160); Settings copy; iOS Markets status-bar overlap.
- **Phase 3 first attempt (superseded by 3B) — paused.**
  - Android: `stockstepsMockBackendUrl` is set in `~/.gradle/gradle.properties`. The `fa7c563` debug build (clean worktree) baked the Ubuntu URL; installed;
    Home rendered Ubuntu MOCK data (indices "Close · Oct 7", Sample badge, "Sample data · mock backend" banner); Settings → Development showed Mock Data.
    Then the parallel Portfolio session reinstalled its own build on the same emulator (17:47:40) and set a 1080×2400 size override, so testing stopped.
    Markets, Screener, Stock Details, Portfolio/Practice, Watchlist, Comparison, AI Comparison, Learn, auth, error/retry and reliability: **NOT TESTED**.
  - iOS: not built or launched in Phase 3. Git-ignored `Local.xcconfig` points to the Ubuntu URL (last Debug build, 2026-10-13 00:11, had it in Info.plist).
    **NOT TESTED** end-to-end.
  - Owner decisions pending: keep the machine-wide Gradle property (it also redirects the Portfolio session's debug builds to Ubuntu) or use
    `-P` per build; shared emulator (pause Portfolio device checks) vs a second AVD (signed-out only); iPhone 16 simulator (only one with a cached
    session) coordination.
- **Next (backend track):** long-uptime host smoke → resume Phase 3 after the emulator/simulator decision → security hardening before any remote access
  (`docs/UBUNTU_BACKEND_VERIFICATION_2026-10-13.md` §6). Keep the server LAN-only.

**2026-10-13 (Phase D3.1, committed and pushed as "Stabilize Portfolio Phase D3.1 with cash-aware allocation notes, formatted cash lines, Portfolio tab selection on sub-screens, action-specific error titles and action-free samples on Android and iOS"):** stabilization (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_D3_1.md`).
- Allocation >100% explained: not a bug; cash, including negative, is in the denominator.
- Negative cash confirmed as a supported state.
- Formatted cash lines ("-USD 51.50" + "Unfunded").
- Portfolio tab stays selected on its sub-screens.
- Errors titled by action (additive `errorSource`).
- Samples without actions.
- Verified: core 474/0, shared 217/0 (Android) and 213/0 (iOS), server 499/0, both builds, and Android device on local MOCK.
- **iOS runtime, TalkBack and VoiceOver: NOT VERIFIED.**

The parallel backend session has uncommitted server files in this checkout: never stage them with D3.1.

**2026-10-13 (Phases D2 + D3, committed and pushed):** `main` HEAD is "Add Portfolio Phases D2 and D3 with transaction history filters, transaction details, confirmed-only add/edit/delete feedback, reusable transaction rows and sample read-only handling on Android and iOS", on top of "Close Portfolio Phase D1.1 transaction form polish with all-at-once field validation, type-switch error clearing, selected-security line, scoped Android keyboard resize and stacked large-text actions on Android and iOS" (also pushed).

Phase D3: transaction details and action feedback
(`docs/PORTFOLIO_UI_REFINEMENT_PHASE_D3.md`).
- Details destination; confirmed-only success banners via an additive presenter `actionResult`.
- Reusable rows showing recorded amounts; delete confirmation with a summary.
- Verified: core 473/0, shared 210/0 (Android) and 206/0 (iOS), both builds, and the Android 360 dp flows on local MOCK.
- **iOS runtime, TalkBack and VoiceOver: NOT VERIFIED.**

D2 and D3 share files and were committed together. Next: Phase E (QA, screen readers, iOS runtime pass) → Phase 5D.

**2026-10-13 (Phase D2, in the same commit):** Portfolio Transaction history (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_D2.md`).
- A destination inside Portfolio (Q4 approved), with display-only filters; Opening position goes under Other.
- Month groups; holding scope dropped on account switch.
- Delete hidden in sample mode; "See all transactions (N)" on the dashboard and Holding details.
- Verified: shared 201/0 (Android) and 197/0 (iOS), both builds, and Android 360 dp at 1.0×/2.0× (light and dark) on local MOCK.
- **iOS runtime, TalkBack and VoiceOver: NOT VERIFIED.**

(D3 followed in the same commit.)

**2026-10-13 (D1.1 closure)**: `main` HEAD is "Close Portfolio Phase D1.1 transaction form polish with all-at-once field validation, type-switch error clearing, selected-security line, scoped Android keyboard resize and stacked large-text actions on Android and iOS", committed **locally, not pushed**; it sits on `d86c0e5` (Portfolio Phase D1, pushed). The Portfolio
UI refinement is committed through **D1.1**: A, B, B.1, C, C.1 (closure and cleanup), D1, D1.1. Docs: `docs/PORTFOLIO_UI_REFINEMENT_PHASE_{A,B,B1,C,C1,D1,D1_1}.md`.

Verified at D1.1 closure (sequential, test tasks re-run):
- core 472/0 (JVM, iOS), server 492/0 (3 skipped), shared 190/0 (Android) and 186/0 (iOS);
- `assembleDebug` OK; default unsigned `xcodebuild` OK;
- device captures: Android 360 dp 1.0×/2.0× and iPhone 16 Large/AX5 on local MOCK. Devices were not re-driven for the closure.

NOT VERIFIED:
- a successful iOS save (the Auth emulator isn't running; the owner declined);
- TalkBack, VoiceOver, Android dark form;
- iPhone SE (excluded by the owner), physical devices.

A parallel "Ubuntu backend migration" session commits server work to this checkout (`231a0a1`); never stage its files with Portfolio work.

**Next:**
1. Portfolio **Phase D2 — Transaction history**: plan submitted for owner review; not started (needs Q4 route approval).
2. Phase E (QA, screen readers).
3. Phase 5D.

Push only on request. Full handoff: `docs/PROJECT_HANDOFF.md` §D1-1-CLOSURE-2026-10-13.

**Portfolio Phase D1.1 (2026-10-13, owner-approved, committed as "Close Portfolio Phase D1.1 transaction form polish with all-at-once field validation, type-switch error clearing, selected-security line, scoped Android keyboard resize and stacked large-text actions on Android and iOS"; not pushed):** form polish and regression closure (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_D1_1.md`, §8 closure).

**Portfolio Phase D1 (2026-10-12, committed and pushed as "Add Portfolio Phase D1 transaction entry with sectioned forms, native date pickers, field-level validation, keyboard navigation and read-only sample handling on Android and iOS"):** transaction entry and forms on both platforms (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_D1.md`). Next: Phase D2 after approval.

**C.1 final cleanup (2026-10-12, committed and pushed as "Finish Portfolio Phase C.1 cleanup with concise insight cards, expandable details, currency on insight amounts and clearer stacked metric spacing on Android and iOS"):** 5 owner review items pass (C.1 doc §9).

**C.1 closure (2026-10-12, committed and pushed as "Close Portfolio Phase C.1 verification with pill selectors, inset transaction dividers, vertically scrollable trend charts and large-text chart and selector fixes on Android and iOS"):** final device verification READY TO CLOSE after 11 UI fixes (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_C1.md` §8); SE excluded by the owner; screen readers NOT VERIFIED.

**Next: Portfolio Phase D** (forms + Transactions destination), only after owner approval of its new routes. Full handoff: `docs/PROJECT_HANDOFF.md`
§HANDOFF-2026-10-12-END.

**Current (2026-10-12, committed and pushed as "Close Portfolio visual and accessibility pass with group stacking, grouped transaction rows, single headings and large-text insights fixes (Portfolio Phase C.1) on Android and iOS", on top of `8a86e4e`)**: **Portfolio Phase C.1 visual/accessibility closure** (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_C1.md`):
- group stacking for metrics and legends; structured holding rows; grouped transactions;
- duplicate headings removed; Insights large-text fixes; iOS status-bar backdrop.

Verified: core 471/0, shared 169/0 and 165/0, both builds, Android and iPhone 16 device checks (36 captures). NOT RUN: iPhone SE, TalkBack, VoiceOver.
Phase D not started.

**Earlier (2026-10-12)**: (committed and pushed as "Refine Portfolio holdings, allocation donut, holding details and insights with borderless sections and matching selector icons (Portfolio Phase C) on Android and iOS", on top of `6c1d76b`)**: **Portfolio Phase C — VERIFICATION PARTIAL** (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_C.md`):
- borderless sections; matching selector icons;
- holdings with server weight and display-only sort;
- allocation donut + legend with honest bar fallback, By currency collapsed;
- Holding details grid and actions; chart area fill;
- Portfolio insights design-system pass.

Verified: core 471/0, shared 166/0 and 162/0, both builds, Android 360 dp and iPhone 16 device checks (36 captures). NOT RUN: iPhone SE, TalkBack,
VoiceOver. Phase D not started.

**Earlier (2026-10-12)**: (committed and pushed as "Polish the Portfolio overview with a short sample banner, a grouped scenario sheet, account labels without repeated currency and AX5 fixes (Portfolio Phase B.1) on Android and iOS", on top of `6041e8f`)**: **Portfolio Phase B.1 polish** (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_B1.md`):
- short sample banner + Details; grouped scenario sheet;
- currency not repeated in account labels; selected vs all-accounts totals;
- chart note + ⓘ, width-based chart height;
- disabled-Add explanation; readable unavailable notice;
- iOS AX5 and live Dynamic Type fixes.

Verified: core 471/0, shared 156/0 and 152/0, both builds, Android and iPhone 16 device checks (29 captures). NOT RUN: iPhone SE, TalkBack,
VoiceOver. Phase C not started.

**Earlier (2026-10-12)**: (committed and pushed as "Add the Portfolio overview card with on-demand account value history, currency on every metric and the sample selector in the menu (Portfolio Phase B) on Android and iOS", on top of `37d74cd`)**: **Portfolio Phase B (overview card)** (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_B.md`):
- one overview card: total, today's change, Account value history with on-demand Show 1M and honest flat/empty/error states, and metrics with
  currency codes;
- the sample selector moved into ⋯;
- shared `PortfolioOverviewDisplay` and `StockLayout.metricRowStacked`.

Verified: core 471/0, shared 150/0 and 146/0, both builds, Android device captures on local MOCK. iOS data-backed overview **NOT RUN** (needs a
signed-in simulator). Phase C not started.

**Earlier (2026-10-12)**: (committed and pushed as "Fix Portfolio account selection by id and add Phase A shared form rules, readable labels, formatted holding amounts and consistent copy on Android and iOS", on top of `f55fd90`)**: the Portfolio UI audit (`docs/PORTFOLIO_UI_AUDIT.md`), the revised plan
(`docs/PORTFOLIO_UI_REFINEMENT_PLAN.md`, Phases A–E), the **P0 fix A-F1** (Android form chose the account by name; now by id, with tests) and
**Phase A** (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_A.md`) are done:
- readable transaction and category labels;
- formatted holding-detail amounts;
- design-system buttons, errors and decimal keyboards;
- shared `PortfolioDisplay` copy and `PortfolioFormRules`;
- readable Insights timestamps and "Portfolio insights" naming;
- the chart titled "Account value".

Verified: core 471/0 (JVM, iOS), shared 138/0 (Android) and 134/0 (iOS), `assembleDebug` OK, iOS `xcodebuild` OK. **Not verified**: device captures,
TalkBack, VoiceOver, `:server:test` (no server change). **Phase B is not started**; it needs approval and decisions Q1–Q7 / D1–D3.

**Current (2026-10-12, end of session)**: `main` at "Finish Company Comparison QA with a two-column chart selection summary, scannable chart limitations, heading fit at large text and live Dynamic Type for buttons on Android and iOS" (pushed, on top of `85e24a0`): Company Comparison final polish & QA closure
(`docs/GLOBAL_UI_REFINEMENT_PHASE5C_QA_CLOSURE.md` — chart selected-date grid, split chart limitations, spoken-label fix, iOS live type for buttons,
`StockHeadingFit`; 18 captures `docs/design/phase5c_qa/`). Verified: core 471/0 (JVM, iOS), shared 117/0 (Android) and 113/0 (iOS), `assembleDebug` OK,
iOS `xcodebuild` OK, device checks on local MOCK. Not verified: TalkBack navigation, VoiceOver, physical devices. Company Comparison = **PASS WITH
CONDITIONS**. Next: Portfolio P-B or Phase 5D (approval required). Full handoff: `docs/PROJECT_HANDOFF.md`
§HANDOFF-2026-10-12.

**Earlier (2026-10-12)**: Company Comparison accessibility closure + insights card committed and pushed as "Close Company Comparison accessibility pass with an icon-led insights card, collapsible chart help and sources, readable tab labels at large text and live Dynamic Type fixes on Android and iOS"
(`docs/GLOBAL_UI_REFINEMENT_PHASE5C_ACCESSIBILITY_CLOSURE.md`; core 470/0, shared 116/0 and 112/0, both builds OK, 14 captures; PASS WITH CONDITIONS).

**Earlier (2026-10-12)**: Company Comparison final polish committed and pushed as "Polish Company Comparison with shared chart legend styles, concise What-the-numbers-show insights, a quieter sample strip and large-text fixes on Android and iOS"
(`docs/GLOBAL_UI_REFINEMENT_PHASE5C_FINAL_POLISH.md`; core 468/0, shared 114/0 and 110/0, both builds OK, device checks on local MOCK). Next: Portfolio
P-B or Phase 5D after approval.

**Latest (2026-10-12, committed and pushed as "Close Company Comparison Phase 5C.1 with section-based sticky header and spacing fixes, and add the Portfolio UI audit and Phase P-A display rules")**: Phase 5C.1 closure (`docs/GLOBAL_UI_REFINEMENT_PHASE5C_1_CLOSURE.md`, PASS WITH CONDITIONS), Portfolio audit
(`docs/PORTFOLIO_UI_REFINEMENT_PLAN.md`) and Portfolio P-A baseline + shared display rules (`docs/PORTFOLIO_REFINEMENT_PHASE_PA.md`) are committed on
top of `42c749d`. Tests: core 466/0 (JVM, iOS), shared 113/0 (Android) and 109/0 (iOS), both builds OK. **Next: Portfolio P-B
(Overview Card) only after approval.** Phase 5D (Learn/Brief/Earnings) remains queued after the Portfolio work unless the owner reorders.

**State (2026-10-11, end of session)**: `main` at `19c3105` "Polish Company Comparison with collapsible sections, short periods, comparison bars, concise
notes and one info action (Global UI Refinement Phase 5C.1) on Android and iOS", plus the docs-only handoff commit "Add end-of-session handoff after Global UI Refinement Phase 5C.1 and point to the Phase 5D Earnings audit". Global UI Refinement
plan step 5 (Watchlist, Screener, Comparison) is complete: 5A/5A.1 Watchlist, 5B + 5B closure Screener ("Discover Stocks"), 5C + 5C.1 Company
Comparison — all committed and pushed, each verdict **PASS WITH CONDITIONS**. **Next: Phase 5D (plan step 6: Learn, Daily Brief, Earnings) — start with a
read-only Earnings audit, only after the owner approves.** Full handoff: `docs/PROJECT_HANDOFF.md` §HANDOFF-2026-10-11.

Latest verified (clean test outputs, sequential run, this session): core JVM 465/0, core iOS 465/0, shared Android host 111/0, shared iOS 107/0,
`:app:androidApp:assembleDebug` OK, iOS `xcodebuild` (generic simulator, unsigned) BUILD SUCCEEDED. Device screenshots on the local MOCK server only
(Android emulator 360 dp 1.0×/1.3×, iPhone 16 Large/XXXL, light and iOS dark) in `docs/design/phase5c/` and `docs/design/phase5c_1/`.
**Not verified anywhere in Global UI Refinement**: TalkBack and VoiceOver speech, iPhone SE (compact) for 5B/5C/5C.1, Android dark mode (app-level theme
setting), iOS signed-in Watchlist, Screener device screenshots for 5B, full `:server:test` (no server code changed since Phase 4).

Per-phase docs (newest first): `docs/GLOBAL_UI_REFINEMENT_PHASE5C_1.md`, `…PHASE5C.md`, `…PHASE5C_BRIEF.md`, `…PHASE5B_CLOSURE.md`, `…PHASE5B.md`,
`…PHASE5A_1.md`, `…PHASE5A.md`, plan `docs/GLOBAL_UI_REFINEMENT_PLAN.md`.

**Global UI Refinement Phase 5A.1 (Watchlist polish) — committed and pushed as "Polish Watchlist header wrapping, two-line names, fading list selector, short empty title and single offline label (Global UI Refinement Phase 5A.1) on Android and iOS" (2026-10-10)**: header wraps between parts, two-line names,
fading list selector, short empty title, offline label once. Core 441/0 (JVM, iOS), shared Android 109/0, shared iOS 105/0, builds OK; offline and
missing data verified on device. Screen-reader speech, iOS signed-in and SE XXXL NOT RUN. Details `docs/GLOBAL_UI_REFINEMENT_PHASE5A_1.md`.
**Closure review (committed as "Close Global UI Refinement Phase 5A.1 with a re-verification pass and Phase 5B handoff")**: code re-checked, no changes; tests re-run without cache — core 441/0 (JVM, iOS), shared Android 109/0, shared iOS
105/0, `assembleDebug` OK, iOS `xcodebuild` BUILD SUCCEEDED; device/screen-reader conditions still NOT RUN (procedure in the phase doc §5b). No blocking defects; ready to close.
**Next**: review → Phase 5B on approval.

**Global UI Refinement Phase 5A (Watchlist) — committed and pushed as "Refine Watchlist with compact quote rows, full-name list selector, clear empty and unavailable states and grouped alert actions (Global UI Refinement Phase 5A) on Android and iOS" (2026-10-10)**: 4C.3 selector fade closed (shared `fadesEdge`); compact
Watchlist on Android and iOS with full-name selector, signed amount + percentage rows, shared `StockQuoteRowLayout`, unavailable/loading/error
states, grouped "Alerts and more" actions. Core 439/0 (JVM, iOS), shared Android 109/0, shared iOS 105/0, builds OK. iOS SE/signed-in and screen
readers NOT RUN. Details `docs/GLOBAL_UI_REFINEMENT_PHASE5A.md`. **Next**: review → Phase 5B on approval.

**Global UI Refinement Phases 4C.2 + 4C.3 (Company Details polish) — committed and pushed as "Polish Company Details: natural loss wording, chart error states, dated change label, responsive actions and range selector (Global UI Refinement Phases 4C.2 and 4C.3)" (2026-10-10)**: natural loss wording, classified
chart states (no history / connection / temporary) in a compact centered layout, date-aware change label, responsive header actions (shared
`StockActionLayout`), range-selector scroll affordance, long badges below titles, duplicate valuation headline removed, iOS MOCK banner above the
tab bar. Core 435/0 (JVM, iOS), shared Android 107/0, shared iOS 103/0, `assembleDebug` and iOS `xcodebuild` OK; device pass on local MOCK.
TalkBack/VoiceOver NOT RUN; known issue: "ALL" faded while visible on iPhone 16. Details `docs/GLOBAL_UI_REFINEMENT_PHASE4C_3.md`.
**Next**: review → Phase 5 on approval.

**Global UI Refinement Phase 4C.1 (Company Details polish) — committed and pushed with 4C as "Refine Company Details for beginners with chart scroll fix and accessible price summary (Global UI Refinement Phases 4C and 4C.1)" (2026-10-10)**: chart scroll conflict fixed
on Android and iOS, dated quote time, price block with one spoken summary, primary/secondary header actions, duplicate source links removed.
Core 430/0 (JVM, iOS), shared Android 101/0, shared iOS 97/0, `assembleDebug` and iOS `xcodebuild` OK. **Correction:** iOS device checks in
Phases 4A–4C.1 used the LAN MOCK backend configured in the app (not the local server). TalkBack/VoiceOver passes still outstanding. Details
`docs/GLOBAL_UI_REFINEMENT_PHASE4C_1.md`. **Next**: review → Phase 5 on approval.

**Global UI Refinement Phase 4C (Company Details) — committed and pushed with 4C.1 (2026-10-10)**: beginner order (identity & price
→ chart → why it moved → At a Glance → Understand → Valuation & financial health → Earnings → News), neutral closed status chip, At a Glance
truncation fixed, duplicate values removed, navigation rows for research links. Core 430/0 (JVM, iOS), shared Android 100/0, shared iOS 96/0,
`assembleDebug` and iOS `xcodebuild` OK. Details `docs/GLOBAL_UI_REFINEMENT_PHASE4C.md`. **Next**: review → Phase 5 on approval.

**Global UI Refinement Phase 4B.1 (Markets final-reference refinement) — committed and pushed as "Refine Markets to the final reference: compact US market status, US-only indices and sector methodology sheet (Global UI Refinement Phase 4B.1)" (2026-10-10)**: compact
"US Market" status card (calendar-based next open, one-line sample footer), US-only indices, sector methodology in an (i) sheet, brief
labels on their own line. Core 430/0 (JVM, iOS), shared Android 96/0, shared iOS 92/0, `assembleDebug` and iOS `xcodebuild` OK. Details
`docs/GLOBAL_UI_REFINEMENT_PHASE4.md` §Phase 4B.1. **Next**: review → Phase 4C on approval.

**Global UI Refinement Phase 4B (Markets) — committed and pushed as "Redesign Markets data-first with status, index rows and research tools (Global UI Refinement Phase 4B) on Android and iOS" (2026-10-10)**: data-first Markets (status card with a
neutral closed state, grouped index rows, brief entry row, grouped research-tool rows via the new `StockNavigationRow`, wrapping sector
names), unused `DailyBriefPreviewCard` removed. Core 429/0 (JVM, iOS), shared Android 92/0, shared iOS 88/0, `assembleDebug` and iOS
`xcodebuild` OK; device pass incl. iPhone SE and Android 360 dp. Details `docs/GLOBAL_UI_REFINEMENT_PHASE4.md`. **Next**: review → Phase 4C
(Company Details) on approval.

**Global UI Refinement Phase 4A (Home) — committed and pushed as "Redesign Home with a market overview and portfolio summary (Global UI Refinement Phase 4A) on Android and iOS" (2026-10-10)**: "Markets today" card from the Daily Brief
already on Home (no new requests), compact portfolio summary in the Phase 3 language, fact rows with chevrons, readable UTC dates via core
`ReadableDates`, `StockRow` names wrap at large text. Core 429/0 (JVM, iOS), shared Android 87/0, shared iOS 83/0, server 482/0 (3 skipped),
`assembleDebug` and iOS `xcodebuild` OK. Details `docs/GLOBAL_UI_REFINEMENT_PHASE4.md`. **Next**: review → Phase 4B (Markets) on approval.

**Current state (2026-10-10, end of session)**: Global UI Refinement Phases 1 (tokens), 1A (intermediate components), 2 (remaining components),
3 (Portfolio reference screen) and 3.1 (Portfolio polish) are **committed and pushed**; nothing is in progress. **Next: Phase 4 — Home,
Markets, Company Details — only after the user approves it** (plan and first steps in `docs/PROJECT_HANDOFF.md` §SESSION). Latest verified:
shared Android 81/0, shared iOS 77/0, `assembleDebug` OK, iOS `xcodebuild` OK; server 482/0 (3 skipped), core JVM/iOS 429/0 (earlier this
session, untouched since). Open: LAN backend still on Phase 5C image (5C.1 brief wording not deployed; redeploy needs approval), no UI-test
targets, screen-reader and physical-device passes not run, Phase 5D Firebase persistence and Cloud Run staging deferred.

Phase 4: **`4586889` "Add financial API Phase 4: public API protection, durable AI quotas, provider budgets and usage monitoring"** — pushed (Phase 4: admission/identity, existence gate, watch-data cap, per-job internal auth, App Check monitor mode, durable AI quotas,
provider budgets, usage summaries, audit and implementation docs), on top of `6a70d0e` "Verify financial API Phase 3 with before/after benchmarks and fix earnings-aware statement coverage".

**Current objective**: financial API cost and safety program. Phases 1–3 are done, committed and verified. **Phase 4 (production security,
durable AI quotas, provider budgets, cost monitoring) is implemented, verified and committed** ("Add financial API Phase 4: public API protection, durable AI quotas, provider budgets and usage monitoring"): audit docs
`docs/FINANCIAL_API_PHASE4_*.md` and the report `docs/FINANCIAL_API_PHASE4_IMPLEMENTATION.md` (§8–§9 blockers and validation). Final matrix on the
Phase 4 code: server 473/0 (3 skipped), core JVM 416/0, core iOS 416/0, shared Android host 55/0, shared iOS 49/0, `assembleDebug` OK, iOS
`xcodebuild` BUILD SUCCEEDED. Verdict: READY WITH CONDITIONS. Full session handoff: `docs/PROJECT_HANDOFF.md` (top section).

**Phase 5A (committed: "Add Phase 5A Cloud Run deployment preparation: container image, health probes, startup validation, JSON logging and staging docs")**: Google Cloud Run deployment preparation — `Dockerfile`, `.dockerignore`, `deploy/cloud-run-staging.yaml`,
`/health/live` + `/health/ready`, startup configuration validation, JSON logging, server-only Gradle build flag, and `docs/CLOUD_RUN_*.md`;
report `docs/FINANCIAL_API_PHASE5A_IMPLEMENTATION.md`. Server tests 481/0 (3 skipped). Image built for `linux/amd64` and container-verified on the owner's Ubuntu server
(report §6a: non-root, no secrets, fail-fast config, JSON logs, probes, SIGTERM, `--network none`; 203 MB compressed). Verdict: READY WITH CONDITIONS for Phase 5B (staging deploy). Next: image vulnerability scan;
owner inputs in `docs/CLOUD_RUN_DEPLOYMENT_CHECKLIST.md` §1; then Phase 5B only on explicit request.

**Global UI Refinement Phase 3.1 (Portfolio polish) — committed and pushed as "Polish the Portfolio reference screen (Global UI Refinement Phase 3.1) on Android and iOS" (2026-10-10)**: readable "as of" dates, hero metric rows,
one-tap daily-change reason, combined cash and dividends, consistent allocation rows, per-share trade prices, width-aware holding rows,
chart axis fixes. Shared Android 81/0, shared iOS 77/0, `assembleDebug` OK, iOS `xcodebuild` OK. Details
`docs/GLOBAL_UI_REFINEMENT_PHASE3_1_POLISH.md`; screenshots `docs/design/phase3_1/`. **Next**: Phase 4 on approval.

**Global UI Refinement Phase 3 (Portfolio reference screen) — committed and pushed as "Redesign Portfolio as the Global UI Refinement Phase 3 reference screen on Android and iOS" (2026-10-10)**: Portfolio redesigned on Android and
iOS from `docs/design/stocksteps-portfolio-reference.png` with real data only (mode/account selectors, hero, performance card, compact rows,
allocation bars, overflow menu, one sync banner); shared `PortfolioPresentation`; content-aware metric grids. MSFT 13,144 % traced to a
ledger entry (unit price 4) — not a calculation bug. Shared Android 80/0, shared iOS 76/0, `assembleDebug` OK, iOS `xcodebuild` OK. Details
`docs/GLOBAL_UI_REFINEMENT_PHASE3_PORTFOLIO.md`; screenshots `docs/design/phase3/`. **Next**: Phase 4 on approval.

**Global UI Refinement Phase 2 (remaining reusable components) — committed and pushed as "Add Global UI Refinement Phase 2: badges, pill selectors, banners, search field and component baseline" (2026-10-10)**: shared badge/banner
semantics (`StockSemanticStyles`), pill selector without truncation, selected chips/pills in the action blue, standard status badges
(SIMULATED/Sample never split), title+badge layout, banners, editable search field, AA fixes (light `negativeText`, education eyebrow), row/
news text roles. iOS before/after screenshots in `docs/design/phase2/` (Android NOT RUN). Shared Android 75/0, shared iOS 71/0,
`assembleDebug` OK, iOS `xcodebuild` OK. Details `docs/GLOBAL_UI_REFINEMENT_PHASE2.md`. **Next**: Phase 3 Portfolio.

**Global UI Refinement Phase 1A (intermediate component foundation, between plan steps 1 and 2) — committed and pushed as "Add Global UI Refinement Phase 1A: accessible buttons, wrapping metrics, states and form controls" (2026-10-10)**: accessible primary buttons (`primaryAction`
`0F6FDB`; iOS `.stockPrimary` replaces `.borderedProminent`), card padding 16, wrapping metric + metric grid, full empty/error states, select
and text fields (iOS keyboard Done), `StockLabels`. Shared Android 69/0, shared iOS 65/0, `assembleDebug` OK, iOS `xcodebuild` OK.
Details `docs/GLOBAL_UI_REFINEMENT_PHASE1A.md`; reference image `docs/design/stocksteps-ui-reference.png`. **Next**: Phase 2 (remaining
reusable components, screenshot baseline first), then Phase 3 Portfolio.

**Global UI Refinement Phase 1 (design-system foundation) — committed and pushed as "Add Global UI Refinement Phase 1: shared design-system tokens for Android and iOS" (2026-10-10)**: shared tokens refined (dark palette
targets, AA-fixed `textTertiary`, text-role aliases, type scale body 15/caption 12/tiny 11, new spacing roles, corners 8/12/16/20, auth
gradient colours tokenised); Compose and iOS bridges updated (iOS Dynamic Type style by token size, `stockFont`). Shared Android 65/0, shared iOS
61/0, `assembleDebug` OK, iOS `xcodebuild` OK. Details `docs/GLOBAL_UI_REFINEMENT_PHASE1.md`; handoff `docs/PROJECT_HANDOFF.md` §00000.
**Next**: Phase 2 (reusable components, screenshot baseline first).

**Phase 5C.1 (committed and pushed: "Close Phase 5C.1: fix iOS navigation hang, search and duplicate requests, finish iOS walkthroughs"; closure docs committed and pushed as "Close Phase 5C.1 verification: final test matrix, backend check and Global UI Refinement plan") — current state**: iOS walkthroughs A–G done; critical iOS navigation hang and stuck search fixed;
launch requests 19–21 → 13; duplicate screen requests removed; minor #1–#4/#6 fixed; #7/#8 deferred (MOCK fixtures). **Final matrix on the final
code, all green**: server 482/0 (3 skipped, Firestore emulator), core JVM 429/0, core iOS 429/0, shared Android 55/0, shared iOS 51/0, `assembleDebug`
OK, iOS `xcodebuild` BUILD SUCCEEDED. Not confirmed: in-app checks of #17–#20/#22; no iOS UI-test target for the navigation-hang regression
(report §13a). LAN backend checked read-only: healthy, MOCK, `upstream` 0, still on Phase 5C (`a50a2e3cc833`) — 5C.1 brief wording not deployed
(redeploy + smoke awaiting approval). Verdict **PASS WITH CONDITIONS** (report `docs/PHASE5C_MOBILE_INTEGRATION_TEST_REPORT.md` §9–§16).
UI plan: `docs/GLOBAL_UI_REFINEMENT_PLAN.md` (read-only proposal, awaiting approval). **Next**: approved LAN redeploy + smoke → plan approval →
UI refinement step 1 (global design tokens). Full handoff: `docs/PROJECT_HANDOFF.md` §0000.

**Phase 5C (committed: "Fix Phase 5C mobile integration bugs and add end-to-end verification report") — current state**: Android & iOS end-to-end tests against the LAN MOCK backend; report
`docs/PHASE5C_MOBILE_INTEGRATION_TEST_REPORT.md`, verdict PASS WITH CONDITIONS. 8 bugs fixed (MOCK learning sync, iOS search recursion, iOS banner over
tab titles, "1 shares", holding and research-step a11y labels, iOS Settings sign-in, iOS duplicate launch requests). Matrix: server 482/0 (3 skipped),
core JVM 421/0, core iOS 421/0, shared Android host 55/0, shared iOS 51/0, `assembleDebug` OK, iOS `xcodebuild` OK. **Next**: Phase 5C.1 (verification closure) →
redeploy LAN backend → finish iOS walkthroughs marked NOT RUN → physical devices. Full handoff: `docs/PROJECT_HANDOFF.md` §000.

**Phase 5B Local (committed: "Add Phase 5B Local Docker deployment of the MOCK backend on a LAN host with smoke and reliability checks")**: LAN Docker MOCK backend + mobile integration — `deploy/local/` (Compose, scripts), iOS `Local.xcconfig`
override, docs `docs/LOCAL_DEVELOPMENT_SERVER.md` and `docs/FINANCIAL_API_PHASE5B_LOCAL_IMPLEMENTATION.md`. Smoke 23/23, zero provider calls,
iOS Simulator verified against the LAN server, Android emulator network-verified. Verdict **PASS WITH CONDITIONS** (Android app and physical
devices not run). **Phase 5B Local = local integration milestone. Cloud Run staging deployment remains deferred and unverified.**

**Earlier next task (still open)**: push when asked; then the deployment-side conditions in `docs/FINANCIAL_API_PHASE4_IMPLEMENTATION.md` §8/§9d — verify
ingress and set `TRUSTED_PROXY_HOPS` (D1), set provider plan limits and `CLOUD_RUN_MAX_INSTANCES` (D4/D5), reconfigure Cloud Scheduler with OIDC or
per-job secrets, confirm the deployed logging config (rotate the FMP key if TRACE was ever deployed), add the App Check SDKs (monitor mode), create
the logs-based metrics, dashboard, alerts and billing budgets. Owner decisions: `docs/FINANCIAL_API_PHASE4_DECISIONS.md`.

Other open follow-ups (unchanged by this session's cost work): Comparison Phase 5 AI device walkthrough and authorized REAL Gemini run;
real-Firestore check of `users/{uid}/meta/aiUsage` (now also used by Brief and Earnings AI) and the research paths; billing; deploy + Cloud
Scheduler (OIDC or the new per-job secrets); push verification; per-IP limits once `TRUSTED_PROXY_HOPS` is verified on the deployment.

## 1. Feature status

### Committed and pushed (newest first)

| Feature | Commit (title) | Doc | Notes |
|---|---|---|---|
| Ubuntu backend stabilization Phase 1: MOCK quote timestamps follow the MOCK market clock (practice trades and price alerts keep working on a long-running server) | `fa7c563` "Keep MOCK quotes current with the advancing MOCK market clock so practice trades and price alerts keep working on a long-running Ubuntu backend (Ubuntu backend stabilization Phase 1)" | `docs/UBUNTU_BACKEND_VERIFICATION_2026-10-13.md` | Deployed to the LAN Ubuntu host as `stocksteps-local:fa7c563c94a9`. `MockClockFreshnessTest` 7/7. Host ≥ 16 h check pending. |
| Ubuntu backend Phase A audit + Phase B MOCK sample company catalog (137 US companies; MOCK screener 160 = 152 US + 8 CA; consistent P/E = price ÷ EPS; 52-week range from the 1Y chart) | `231a0a1` "Add a sample company catalog to the MOCK backend so the screener covers about 150 US stocks with consistent P/E, EPS and 52-week ranges (Ubuntu backend Phase B)" | `docs/LOCAL_DEVELOPMENT_SERVER.md` | `MockCatalogTest` 7/7. Deployed with `fa7c563`. |
| Portfolio Phase C.1 closure (group stacking, grouped transactions, single headings, Insights large-text fixes, iOS status-bar backdrop) | "Close Portfolio visual and accessibility pass with group stacking, grouped transaction rows, single headings and large-text insights fixes (Portfolio Phase C.1) on Android and iOS" | `docs/PORTFOLIO_UI_REFINEMENT_PHASE_C1.md` | Shared 169/0, 165/0; captures `docs/design/portfolio_pc1/`; iPhone SE/TalkBack/VoiceOver NOT RUN. |
| Portfolio Phase C (borderless sections, selector icons, holdings weight + sort, `StockAllocationDonut` + `StockAllocationLayout`, holding details grid, chart area fill, Insights pass) | "Refine Portfolio holdings, allocation donut, holding details and insights with borderless sections and matching selector icons (Portfolio Phase C) on Android and iOS" | `docs/PORTFOLIO_UI_REFINEMENT_PHASE_C.md` | Shared 166/0, 162/0; captures `docs/design/portfolio_pc/`; iPhone SE/TalkBack/VoiceOver NOT RUN. |
| Portfolio Phase B.1 polish (short sample banner, grouped scenario sheet, currency not repeated in account labels, selected/all-accounts totals, chart note + ⓘ, `StockChartLayout`, readable unavailable notice, iOS AX5 and live Dynamic Type fixes) | "Polish the Portfolio overview with a short sample banner, a grouped scenario sheet, account labels without repeated currency and AX5 fixes (Portfolio Phase B.1) on Android and iOS" | `docs/PORTFOLIO_UI_REFINEMENT_PHASE_B1.md` | Shared 156/0, 152/0; captures `docs/design/portfolio_pb1/`. |
| Portfolio Phase B overview card (on-demand account value history states, currency on every metric, sample selector in ⋯, `PortfolioOverviewDisplay`, `StockLayout.metricRowStacked`) | "Add the Portfolio overview card with on-demand account value history, currency on every metric and the sample selector in the menu (Portfolio Phase B) on Android and iOS" | `docs/PORTFOLIO_UI_REFINEMENT_PHASE_B.md` | Shared 150/0, 146/0; Android captures `docs/design/portfolio_pb/`; iOS data-backed overview NOT RUN. |
| Portfolio UI audit + P0 account-by-id fix + Phase A (labels, formatted holding amounts, design-system form controls, `PortfolioDisplay`, `PortfolioFormRules`) | "Fix Portfolio account selection by id and add Phase A shared form rules, readable labels, formatted holding amounts and consistent copy on Android and iOS" | `docs/PORTFOLIO_UI_AUDIT.md`, `docs/PORTFOLIO_UI_REFINEMENT_PHASE_A.md` | Tests: shared 138/0 (Android), 134/0 (iOS); no device checks. |
| Financial API Phase 3 implementation (screener re-warm fix, 150-company universe, selective statements, market-/earnings-aware freshness, stale fallback) | `517a46b` "Implement financial API Phase 3: screener re-warm fix, 150-company universe, selective statements, market- and earnings-aware freshness, stale fallback" | `docs/FINANCIAL_API_PHASE3_IMPLEMENTATION.md` | Verified in "Verify financial API Phase 3 with before/after benchmarks and fix earnings-aware statement coverage" (benchmark A–H); per-instance only; no REAL calls. |
| Financial API Phase 3 audit (selective loading, screener warm-up, freshness, earnings invalidation, budgets) + `Phase3AuditBenchmarkTest` | `e4ba2be` "Add financial API Phase 3 audit (selective loading, screener warm-up, freshness) with request benchmark" | `docs/FINANCIAL_API_PHASE3_*.md` | Read-only; found the screener re-warm bug (fixed in `517a46b`). |
| Financial API Phase 1 audit + Phase 2 shared provider cache (failure-safe single flight, duplicate-path removal, local market status, search cache, `ProviderCalls` metering, narration budget) | `75760ab` "Add financial API cost audit and Phase 2 shared provider cache with request reuse" | `docs/FINANCIAL_API_*.md`, `docs/FINANCIAL_API_CACHE_IMPLEMENTATION.md` | Server only; measured 26→20, 74→72, 15→14, 30→1 upstream requests (MockEngine). |
| Company Comparison Phase 5 — AI Comparison Assistant (StockSteps+; `/api/v1/me/compare/ai/*`, durable quota `users/{uid}/meta/aiUsage`) **and** sign-in/create-account redesign on `StockStepsTheme` | `e27991d` "Add Company Comparison Phase 5 AI comparison assistant (StockSteps+) and redesign sign-in screens on Android and iOS" | `docs/SCREENER_AND_COMPARISON.md` (Phase 5) | REAL Gemini not live-verified; no billing; auth behaviour unchanged, no password-reset flow exists. |
| Company Comparison Phase 4 — Guided Research Checklist (free + StockSteps+) | `423f44a` "Add Company Comparison Phase 4 guided research checklist (free + StockSteps+) on Android and iOS" | `docs/SCREENER_AND_COMPARISON.md` (Phase 4) | Firestore path not run against a real project. |
| Company Comparison Phase 3 — Historical Financial Comparison (1Y free; 3Y/5Y StockSteps+) | `f2fed1d` "Add Company Comparison Phase 3 historical financial comparison (1Y free, 3Y/5Y StockSteps+) on Android and iOS" | `docs/SCREENER_AND_COMPARISON.md` (Phase 3) | Core iOS tests now verified (413/0). |
| Company Comparison Phase 2 — Guided Metric Interpretation (free) | `77ef8fb` "Add Guided Company Comparison Phase 2 (guided metric interpretation) on Android and iOS" | `docs/SCREENER_AND_COMPARISON.md` (Phase 2) | |
| Company Comparison Phase 1 review (beginner groups, no horizontal scroll, replace/examples, Watchlist entry, latest-quarter growth, periods/currencies, chart base date) | "Improve Company Comparison Phase 1 for beginners on Android and iOS" | `docs/SCREENER_AND_COMPARISON.md` | Free; full suite not re-run at commit (see §0). |
| StockSteps+ Premium Earnings Intelligence — Earnings Intelligence Lite Phase 5 (AI explanations, report-scoped questions, 8-quarter history, personalized weekly digest, fair-use quotas) | "Add StockSteps+ premium earnings intelligence (Earnings Intelligence Lite Phase 5) on Android and iOS" | `docs/EARNINGS.md` (Phase 5) | StockSteps+; MOCK plans only (no billing); in-memory quotas/caches. |
| Earnings reminders & smart notifications — Earnings Intelligence Lite Phase 4 (backend scheduling, opt-in watchlist reminders, results/date-change notices, deep links) | "Add earnings reminders and smart notifications (Earnings Intelligence Lite Phase 4) on Android and iOS" | `docs/EARNINGS.md` (Phase 4) | Free; earnings alerts migrated; real FCM/APNs delivery and Cloud Scheduler not verified. |
| Post-earnings price reaction — Earnings Intelligence Lite Phase 3 (calendar-aware First/3/5-session windows, chart, explanations) | "Add post-earnings price reaction (Earnings Intelligence Lite Phase 3) on Android and iOS" | `docs/EARNINGS.md` (Phase 3) | Free; regular-session closes only; no corporate-action feed in REAL. |
| Earnings Results & beginner explanations — Earnings Intelligence Lite Phase 2 (exact EPS/revenue comparisons, YoY/QoQ, takeaways) | "Add Earnings Results and beginner explanations (Earnings Intelligence Lite Phase 2) on Android and iOS" | `docs/EARNINGS.md` (Phase 2) | Free; classification now exact (MET only when equal); REAL lacks publication/revision metadata. |
| Earnings Calendar — Earnings Intelligence Lite Phase 1 (calendar, event details, Markets/Company Details/Brief/Watchlist entries) | "Add Earnings Calendar (Earnings Intelligence Lite Phase 1) on Android and iOS" | `docs/EARNINGS.md` (Phase 1) | Free; no UI automation; REAL TSX coverage unverified. |
| Daily Market Brief (Home/Markets previews, reader, personal overlay, Plus AI, notifications) | c92ec89 "Add Daily Market Brief on Android and iOS" | `docs/DAILY_MARKET_BRIEF.md` | Scheduler not deployed. |
| Practice Portfolio (virtual $10,000 simulator; Free 3 holdings / 14-day trial / StockSteps+) | a4a4aeb "Add Practice Portfolio simulator…" | `docs/PRACTICE_PORTFOLIO.md` | Includes the Practice "Buy Stock" → practice search → order fix. Backend not deployed. |
| Guided Stock Research & Beginner Learning (5-step guide, quizzes, Learn hub) | defaade "Add Guided Stock Research…" | `docs/GUIDED_RESEARCH.md` | Includes quiz-card spacing fix (`StockCard.verticalArrangement`). |
| Markets research tools spacing | 54b5559 | — | UI-only. |
| Earnings Intelligence & Earnings Calendar | af39fb4 | `docs/EARNINGS.md` | |
| Smart Stock Screener & Stock Comparison | 4c65521 | `docs/SCREENER_AND_COMPARISON.md` | |
| Portfolio Intelligence (Insights analytics) | bf44bd7 | `docs/PORTFOLIO_INTELLIGENCE.md` | |
| Portfolio tracker (real holdings ledger) | 187d130 | `docs/PORTFOLIO.md` | |
| Personalized Home dashboard | a6b3e2f | `docs/PERSONALIZED_HOME.md` | |
| Watchlists & smart alerts, accounts | e559ffe and earlier | `docs/AUTH_WATCHLIST.md` | Firebase auth; FCM/APNs push. |
| Markets dashboard (indices, movers, sectors, session) | ae05efd | `docs/MARKET_SNAPSHOT.md` | |
| Company News, article explanations, Why Did It Move | 4ddcd10 | `docs/AI_NEWS.md` | Gemini in REAL, templates in MOCK. |
| Biometric app lock + sign-out cleanup | 0d17770 | — | |
| Valuation (monthly historical P/E), Financials, Company Details | 4de8848, b1ed2cb, earlier | `docs/COMPANY_DETAIL.md` | |

### Implemented but NOT committed (working tree)

As of 2026-10-13 (after `3d91ef3` and the backend docs commit):
- Parallel Portfolio session (owned by that session; in progress): `app/androidApp/.../account/AndroidAuthGateway.kt`,
  `app/iosApp/iosApp/FirebaseAccountAdapters.swift`, `docs/PORTFOLIO_UI_REFINEMENT_PHASE_E_1_PLAN.md`, `docs/design/portfolio_phase_e_1/`.
- Backend session: nothing uncommitted. Its deployment and Phase 3B records were committed in "Record the Ubuntu MOCK backend deployment and Phase 3B Android and iOS connectivity verification against the LAN backend".

## 2. Architecture and key decisions

- **Shared presenters in `:core`** (StateFlow) drive both UIs: Compose scenes collect them; SwiftUI
  wraps them in `@Observable` models via `Ios*Client` bridges (`IosPracticeClient`, `IosBriefClient`,
  `IosLearningClient`, `IosEarningsClient`, `IosScreenerClient`, `IosAccountClient`).
- **Account graph** (`AccountDependencies`): long-lived presenters/repositories shared across entry
  points (`practice`, `dailyBrief`, `learning`, `entitlements`, `watchlists`, `portfolio`, `home`…),
  created lazily; `StockStepsDependencies` is a per-screen public-data graph closed with its ViewModel.
- **Owner-tagged state**: every signed-in presenter resets on `(uid, environment)` change and passes
  `expectedOwner` to `UserApi`, so one account's data never flashes for another; caches are namespaced
  `"{env}|user:{uid}"` (`userCacheOwner`) or `"{env}|guest"` / `"{env}|public"`. Sign-out clears account caches.
- **Server user data** goes through `UserDataStore` (InMemory for MOCK/tests, Firestore in REAL,
  `UnavailableUserDataStore` → 503 when REAL has no credentials). Every read-modify-write is an atomic
  `update*` block (Firestore transactions).
- **Entitlements**: `EntitlementService` reads the server-written StockSteps+ record; Practice adds its
  own trial record (`PracticePolicy`) — access = PLUS > active TRIAL > FREE. Briefs use `BriefPolicy`.
- **Market data**: providers (FMP; Finnhub for news/earnings) only on the server; MOCK uses
  `FixtureMarketDataSource` (+ sample fallbacks). Quote freshness via `AlertRules.freshQuote`
  (US session aware); US calendar `UsMarketCalendar`; TSX calendar `brief/TsxMarketCalendar`.
  FX: Bank of Canada USD/CAD (REAL) / 1.35 (MOCK) via `PortfolioFxSource`.
- **AI**: Gemini JSON client (`news/GeminiInsights.kt` `GeminiJsonCall`) with validators
  (`InsightValidator`, `BriefAiValidator`); templates in MOCK.
- **Push**: devices stored via `DeviceRegistrar`; server `PushSender` (FCM in REAL, `SimulatedPushSender`
  in MOCK; `MockScenarioPushSender` for earnings reminders). `PushMessage.channel` selects the Android
  channel (`stock_alerts`, `earnings_reminders`). Deep links: Android `notificationLinks` channel
  (`"brief:<id>"`, `"earnings:<eventId>"`, `"earnings-results:<reportId>"`, `"earnings-calendar"`, or an
  alert symbol); iOS `NotificationCenter` names `.stockStepsOpenAlerts`, `.stockStepsOpenBrief`,
  `.stockStepsOpenEarningsEvent`, `.stockStepsOpenEarningsResults`.
- **Earnings Intelligence Lite** (server-authoritative; apps only format):
  - identity `SYMBOL:YYYY-Qn` (fiscal period, exchange-qualified symbol);
  - calendar status only from source data;
  - exact decimal maths (`EarningsMath`, MET only when equal);
  - one price-reaction engine on exchange calendars (`PriceReactionEngine`, also used by Earnings Details);
  - one earnings notification system (`EarningsReminderService`; legacy EARNINGS alert rules are migrated
    and no longer evaluated by `AlertEvaluator`);
  - reminder deliveries deduplicated by a unique key with an atomic claim and lease.

## 3. Business rules and requirements

- Beginner education first; never advice, ratings, scores or predictions; separate facts from possible
  explanations ("The available sources don't establish…").
- No fabricated data in REAL; stale/missing values labelled with their real timestamps.
- One unified **StockSteps+** subscription. In-app purchase is **not implemented** (no verified store
  products); paywalls say so and show no prices. MOCK can simulate plans (Settings → simulated plan).
- **Practice Portfolio**: $10,000 virtual cash; Free = 3 distinct open holdings forever (only opening a
  *new* instrument is limited; buying more/selling always allowed); optional one-time 14-day trial started
  only by explicit confirmation, server-timed, idempotent, no payment, no auto-renew; expiry keeps all
  data; weighted-average cost; server-priced immediate fills from fresh quotes (>1% move → re-review);
  idempotency keys; reset archives and never resets trial/challenges; real and practice data never mix.
- **Guided Research**: 5 steps, never locked; progress local-first, synced per account, guest progress
  never merged into an account; all education free; AI Plus-only.
- **Daily Brief**: Free = latest market summary, ≤3 stories, limited watchlist highlights, Concept of the
  Day, last 3 briefs; Plus = full personal overlay, more stories, AI, full history, personalized
  notifications. Never "today" for an old brief. Notifications opt-in, once per brief, market days only.

- **Earnings Intelligence Lite (free)**:
  - calendar, event details, results, price reaction and reminders are free for everyone;
  - StockSteps+ keeps the older Earnings Details full history, advanced insights and AI;
  - reminders: 1/3/7 calendar days before, the user's delivery time and IANA zone, opt-in automatic
    watchlist reminders (off by default), results notifications only after verified publication,
    optional date-change notices, cancellation notices only from an explicit source flag;
  - nothing is a trading signal.

## 4. Pending tasks

1. **Deploy the backend** (Cloud Run) with Practice, Learning and Brief routes; Practice/Learning/Brief
   don't work in REAL until then (404). Firestore credentials required (else 503).
2. **Cloud Scheduler** jobs: `POST /internal/earnings-reminders/dispatch` (every 5 min), `POST /internal/daily-brief/dispatch` (and existing
   `/internal/alerts/evaluate`) with header `X-StockSteps-Scheduler-Token` = `ALERTS_EVALUATOR_TOKEN`.
3. **StockSteps+ billing**: Play Billing / StoreKit + server receipt validation writing the entitlement
   record; restore/pending/cancelled states.
4. A verified **dividend/split source** for Practice in REAL (MOCK has sample events only).
5. Device/simulator walkthroughs and UI-automation tests (none exist; presenters are unit-tested).
6. REAL AI: Phase 5 premium earnings AI uses Gemini only when `GEMINI_API_KEY` is set (output unverified); the older Earnings
   Details "Ask" and Guided Research AI still have no REAL provider (503).
7. Optional: system Back inside Guided Research steps; retry queue for brief pushes; TSX early closes.
8. **Earnings Intelligence Lite Phase 5** production gaps: store billing/receipt verification, shared (multi-instance) quota and AI caches,
   REAL Gemini output review, a secondary results provider, filing/press-release links, split data for EPS history.
9. **Verify earnings push end to end**:
   - Firebase project (`FIREBASE_PROJECT_ID`, ADC with the FCM Admin role);
   - APNs key in Firebase; iOS Push Notifications and Background Modes capabilities;
   - Firestore indexes `earningsDeliveries` (status, dueAt) and (status, leaseUntil);
   - one Android and one iOS device;
   - iOS: add the FirebaseMessaging package product and an `aps-environment` entitlement (per the 2026-10-08 audit, iOS remote
     push isn't wired: every `#if canImport(FirebaseMessaging)` block compiles out).
10. A REAL **corporate-action source** for price reactions (and Practice); intraday/extended-hours data if a plan supports it.
11. UI automation and accessibility checks for the earnings screens.
12. Cleanup: the unused `EarningsDetailsState.reminder`/`reminderBusy` and the alerts collection in `EarningsDetailsPresenter`.
13. Phase 5 follow-ups: Settings plan simulator segments for grace/canceled/payment-failed (debug API only today); persist
    conversations/explanations in Firestore; a real paywall/checkout once billing exists.
14. **Company Comparison Phases 1–2**: device UI pass (Explain cards, related-metric scrolling,
    TalkBack/VoiceOver, large text, dark/light); authorized REAL acceptance run (FMP TSX coverage, sector/industry labels, Finnhub
    quarterly coverage, Bank of Canada date in the FX disclosure); consider a lighter REAL metrics path (each compare loads full annual
    fundamentals, ~13 FMP calls per company, cached 6 h). Phase 3 committed: REAL statement coverage (TSX quarterly,
    `netIncome` semantics) unverified; Company Details still exposes raw statements for free (product decision if that should change).
    Phase 4 committed: real-Firestore run, device pass, cross-instance cache decision pending. Phase 5 committed (`e27991d`): device
    walkthrough, authorized REAL Gemini run and real-Firestore `aiUsage` check pending.
15. **Financial API Phase 3**: implemented (`517a46b`) and verified ("Verify financial API Phase 3 with before/after benchmarks and fix earnings-aware statement coverage"). Remaining: stale fallback for profiles/daily closes,
    market-aware watch-data quotes, weekend/multi-instance cost measurement in production metrics (`docs/FINANCIAL_API_PHASE3_IMPLEMENTATION.md` §8).
16. **Financial API Phase 4** (implemented and committed; deployment configuration pending): admission control and identity, existence gate, watch-data cap, per-job internal auth,
    App Check (monitor mode, client SDKs pending), durable Brief/Earnings AI quotas, provider budgets with circuit breaker, usage summaries.
    Deployment conditions: `docs/FINANCIAL_API_PHASE4_IMPLEMENTATION.md` §8. Deferred: Phase 4E shared caching (licensing D3).
17. App Check client integration (Android Play Integrity, iOS App Attest/DeviceCheck) and later enforcement (`APP_CHECK_ENFORCE`).
18. Password reset / forgot-password flow (no backend support; the sign-in screen shows a "not available yet" notice).
19. **Ubuntu backend — host freshness check**: `deploy/local/deploy.sh smoke` after ≥ 16 h of container uptime (≈ 13:50 UTC 2026-10-14), no restart.
20. **Ubuntu backend — finish mobile integration**: signed-out journeys done (Phase 3B). Remaining:
    - the owner signs in on `emulator-5556` and the iPhone 16 Pro simulator, then test server watchlists, Portfolio, Practice trades, AI comparison;
    - physical devices;
    - iOS offline;
    - fixes for the four Phase 3B issues (approval needed; the `payoutRatio` fix needs a redeploy).
21. **Before any remote access** (not implemented): Firebase ID-token verification in MOCK (`STOCKSTEPS_MOCK_AUTH=firebase`), gate MOCK debug and
    `/internal/*` routes, HTTPS via tunnel/reverse proxy bound to `127.0.0.1` (plan: `docs/UBUNTU_BACKEND_VERIFICATION_2026-10-13.md` §6).
22. Optional MOCK fixes: unknown symbols return a generated sample company (200) instead of 404; Settings → Development copy "Using local sample
    market data" is misleading when the mock server is the Ubuntu host.
23. Housekeeping: remove the scratch worktree `mobile-fa7c563` (registered in `git worktree list`) when Phase 3 no longer needs it; old
    `stocksteps-local:*` images on the host are kept for rollback (remove explicitly, never prune globally).

## 5. Known limitations / bugs to watch

- REAL AI features need `GEMINI_API_KEY` (otherwise 503 `AI_UNAVAILABLE`); the older Earnings Details ask and Guided Research AI
  have no REAL provider wired (503). Phase 5 quotas/caches/conversations are in process memory (single instance).
- MOCK fixtures use `sampleFallback`: some missing values are filled with labelled sample numbers (e.g. TD debt/equity).
- MOCK ETF fixtures (SPY, QQQ…) have prices only (no profile file); some paths treat them as
  "unsupported" (Guided Research) while the sample profile fallback marks them ETF elsewhere.
- MOCK news feed includes general (political) news; the brief now filters to market-relevant stories,
  so a brief may show fewer than three stories.
- MOCK practice/brief state is in-memory: restarting the mock server clears it.
- Practice uses the NYSE calendar for TSX quote freshness; single-currency (CAD) cash.
- Firestore Practice document is per user, bounded (5,000 transactions/generation, 5 archives).
- Brief history persistence relies on briefs being requested (no scheduled generation yet).
- Android emulator needs `adb reverse tcp:8081 tcp:8081` again after restarts.
- REAL earnings data gaps:
  - Finnhub: no publication time, revision flag, estimate period, exact announcement time, or
    postponed/canceled flags;
  - FMP daily closes only (no OHLC, intraday or extended hours);
  - no corporate-action feed;
  - rule-based calendars (no TSX early closes);
  - TSX coverage unverified.
- Earnings reminder and delivery state is in-memory in MOCK (cleared on restart). The MOCK server's Gradle
  daemon can die during other Gradle runs, so restart `runMock` if `/health` fails.
- Disk space on the dev machine is tight. The previous session's scratch iOS derived data is broken; use
  the default DerivedData.
- Phase 1–4 screens have no device walkthrough or accessibility audit yet; there are no UI automation tests.
- Screener: default universe 150 (50 per exchange); with 25 warm-ups/h coverage is full after 6 h and needs every hourly slot to stay full
  (U / T exactly); a smaller budget or failures show partial coverage ("X of 150"). Re-warm bug fixed in `517a46b`.
- Earnings-aware statement refresh without a source `periodEnd` uses a baseline rule (worst case: 2 h polling for one company for ≤ 10 days).
- All provider caches, single flight and most budgets are per Cloud Run instance; Cloud Run instance counts are unknown (no deploy config
  in the repo). Phase 4: route limiters key on the verified uid or a trusted client IP (only once `TRUSTED_PROXY_HOPS` is verified);
  until then anonymous callers share a large per-instance pool per route group. Per-instance limits are not global: the provider budgets
  (`ProviderGuard`) are the deployment-wide backstop once `CLOUD_RUN_MAX_INSTANCES` and plan limits are configured.
- Phase 4: every provider-backed public route has a cost-weighted admission policy; unknown symbols are answered by a cached existence check.
- Flaky: `PracticeServiceTest.concurrentOrdersCannotOverspendOrBypassTheLimit` failed once in a full parallel run, passed 3/3 alone.
- Company Details market status now follows the NYSE calendar (may show pre-market/after-hours); TSX listings still use NYSE status there.
- **Ubuntu MOCK backend (LAN only)**:
  - MOCK trusts unsigned bearer tokens (`MockUserAuthenticator` reads the uid without verification; `mock-user:<uid>` also accepted): any LAN
    client can act as any uid. Acceptable only while LAN-only.
  - `/internal/metrics/usage`, `/internal/*` dispatch routes and MOCK debug routes (plan simulation, practice/portfolio/home scenarios) are open in MOCK.
  - User data (watchlists, practice, alerts, AI usage) is in container memory and is lost on every restart/redeploy; nothing goes to Firestore.
  - The MOCK clock restarts at the fixture capture (2026-10-07 21:15Z) on every container start and advances in real time; quote *timestamps*
    follow it, prices stay the captured/sample values (static), so changes and charts still describe Oct 7 data.
  - Market data: ~25 captured tickers + ETFs + 8 TSX listings, plus the 137-company catalog; any other valid symbol gets a labelled generated
    sample company.
- Parallel sessions share one Android emulator, one iPhone 16 simulator and the same `build/` folders: concurrent installs, size overrides and
  Gradle test runs interfere (seen 2026-10-13: an app reinstall mid-test and a Gradle `EOFException`).
- Android builds against the Ubuntu host need `-PstockstepsMockBackendUrl=http://<lan-ip>:8081` per build. The machine-wide `~/.gradle/gradle.properties`
  entry used briefly on 2026-10-13 was removed, so debug builds default to `127.0.0.1:8081` again.
- MOCK screener: the catalog companies have no payout ratio, so presets that filter on it leave them out "for missing data".
- iOS: the shared screener presenter keeps a catalog error from before a REAL → Mock switch until "Try again".

## 6. Where things are

| Area | Core | Server | Android (Compose) | iOS |
|---|---|---|---|---|
| Daily Brief | `brief/DailyBriefModels.kt`, `DailyBriefPresentation.kt` | `brief/BriefSessions.kt`, `DailyBriefService.kt`, `BriefAi.kt`, `DailyBriefRoutes.kt` | `presentation/brief/*`, Home/Markets cards | `DailyBriefScenes.swift`, `IosBriefClient.kt` |
| Practice | `practice/PracticeModels.kt`, `PracticeEngine.kt`, `PracticeContent.kt`, `PracticePresentation.kt` | `practice/PracticeService.kt`, `PracticeRoutes.kt` | `presentation/practice/*` | `PracticeScenes.swift`, `IosPracticeClient.kt` |
| Guided Research | `learning/*` | `learning/LearningService.kt` | `presentation/research/*`, `presentation/learn/*` | `GuidedResearchScenes.swift`, `IosLearningClient.kt` |
| Earnings (Phases 1–4) | `earnings/EarningsCalendar.kt`, `EarningsResults.kt`, `EarningsPriceReaction.kt`, `EarningsReminders.kt`, plus `EarningsModels/Calculations/Presentation.kt` | `earnings/EarningsService.kt`, `EarningsSources.kt`, `PriceReactionEngine.kt`, `EarningsReminderService.kt`, `EarningsReaction.kt` | `presentation/earnings/*` (`EarningsRoute`, `EarningsScenes`, `EarningsScreens`, `EarningsReminderUi`, `DeviceZone`) | `EarningsScenes.swift`, `EarningsReminderViews.swift`, `IosEarningsClient.kt` |
| Earnings fixtures | — | `scripts/generate_earnings_fixtures.py` → `fixtures/earnings/events.json`, `price-scenarios.json` | — | — |
| Screener/Compare | `screener/*` (Phase 2: `ComparisonInterpretation.kt`; Phase 3: `ComparisonHistory.kt`, `ComparisonHistoryPresentation.kt`; Phase 4: `ComparisonResearch.kt`, `ComparisonResearchPresentation.kt`) | `screener/*` (`ComparisonHistoryService.kt`, `ComparisonResearchService.kt`, `ResearchPdf.kt`), `service/ProviderUsage.kt` | `presentation/screener/*` (`GuidedMetricExplanation.kt`, `ComparisonHistoryUi.kt`, `ComparisonResearchUi.kt`, `PdfSaver*.kt`) | `ScreenerScenes.swift`, `ComparisonHistoryViews.swift`, `ComparisonResearchViews.swift` |
| Portfolio (+Insights) | `portfolio/*`, `portfolio/analytics/*` | `userdata/Portfolio*.kt` | `presentation/portfolio/*` | `Portfolio*.swift` |
| Entitlements | `portfolio/analytics/EntitlementsRepository.kt` | `userdata/PortfolioAnalyticsService.kt` (`EntitlementService`) | Settings simulated plan | `SettingsScreen.swift` |
| User data store | `data/userdata/*` (client cache, `UserApi`) | `userdata/UserDataStore.kt`, `FirestoreUserDataStore.kt` | — | — |
| Navigation | — | — | `presentation/AppNavigation.kt` | `AppScene.swift` |
| Comparison AI (Phase 5) | `screener/ComparisonAi.kt`, `ComparisonAiPresentation.kt` | `screener/ComparisonAiService.kt` | `presentation/screener/ComparisonAiUi.kt`, `ComparisonAiScene` | `ComparisonAiViews.swift`, `IosScreenerClient.kt` |
| Sign-in / create account | — | (Firebase auth unchanged) | `presentation/account/AuthScreen.kt`, `AuthComponents.kt`, `theme/AuthTokens.kt` | `AuthScreen.swift`, `AuthComponents.swift` |
| Provider caching & metering | — | `service/CompanyFinancialCache.kt`, `httpclient/NetworkUtils.kt` (`ProviderCalls`), `service/ProviderUsage.kt`, `repositoryImpl/Fmp*.kt` | — | — |

Tests: `core/src/commonTest/.../{brief,practice,learning,earnings,screener,…}`, `server/src/test/.../{brief,practice,learning,earnings,screener,service,userdata,…}` (provider-call benchmarks: `server/src/test/.../service/ProviderRequestBenchmarkTest.kt`, `Phase3AuditBenchmarkTest.kt`).
