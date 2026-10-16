# ChatGPT review package — Phase 4 Company Search Relevance & Performance Audit

## 1. Phase and Git baseline
- Phase 4: Company Search relevance & performance audit (KAN-33 not reachable from this session; the brief was the requirement).
- Base: `6d6b68e` "Note that the Company Details UI Phase 3 approval record 17b14a7 is pushed in the handoff and project status".
- Under review: "Normalize inner whitespace in Company Search queries for Phase 4 so the provider query and search cache key match, with ranking, stale-response and cache regression tests and the relevance and performance audit" (approved by the owner 2026-10-16; local commit, not pushed).

## 2. Changes implemented
- **Fix (server):** search queries collapse runs of inner whitespace before the provider call and the cache key. `apple  inc` returned
  nothing and cost its own pair of FMP calls; it now finds Apple and shares `apple inc`'s cache entry.
- **Regression tests:** ranking precedence (APP above AAPL, etc.), the `app` → `apple` stale-response race, normalized cache sharing.
- Nothing else changed: ranking, debounce, cancellation, recents and the UI already met the brief (inventory in the report §2).

## 3. Affected files
- server: `service/StockService.kt`; test `StockSearchTest.kt`.
- core: test `search/SearchRankingTest.kt`.
- shared: test `presentation/stocksearch/StockSearchViewModelTest.kt`.
- docs: `docs/COMPANY_SEARCH_RELEVANCE_AUDIT_PHASE4.md`, this folder, `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`.
- Android / iOS source: none.

## 4. Architectural decisions
- Normalize once on the server (the single owner of the provider call and the cache), not in each client. Ranking already normalized.
- No cache redesign: the existing `CompanyFinancialCache` (6 h, 512, coalesced, failures not stored, public keys only) is adequate;
  proposals in report §7 only.
- No hard-coded tickers or names in production ranking; the APP/AAPL case is proven with provider-shaped test data.

## 5. Test and build results
All PASS — core JVM 612/0, core iOS 610/0, server 508/0 (3 skipped), shared Android 282/0, shared iOS 279/0, Android assembleDebug, iOS
xcodebuild. Details: `CHATGPT_TEST_RESULTS.md`.

## 6. Android verification (emulator-5554, MOCK :8083, 1.0×, light)
PASS: rapid typing (1 request), pause + continue (2 requests, final list correct), clearing (recents), selection → Company Details (0 search
requests), Back (0), tab switches (0 requests of any kind). Back returns to the origin screen (search is a modal by plan 5D.5.6 rule 5).

## 7. iOS verification (iPhone 16, guest, MOCK :8083, Default, light)
PASS: rapid typing (1 request), `app` then `le` (2 requests, final list `apple`), clear button → recents, selection → Company Details, Back
(0 search requests).

## 8. Known issues
- MOCK fixtures have no APP, SPY/QQQ or BRK.B listings, so those cases are unit-test evidence only.
- One failing FMP endpoint fails the whole search (existing behaviour; backlog).
- The search sheet on iOS needs ~0.35 s before typed text reaches the field (existing; text typed earlier is lost).

## 9. Unverified scenarios
- REAL provider behaviour, FMP default page size, provider call counts in REAL: NOT RUN (no approved quota).
- Android 2.0×, iOS AX5, dark, empty, error/Retry, network failure on device: NOT RUN in this phase (client code unchanged since the
  5D.5.5D §8 PASS run).
- TalkBack / VoiceOver: NOT RUN (owner).
- iOS stale-response race as a unit test: NOT RUN (no iOS test target); code review + device only.

## 10. Deviations from the brief
- No contact sheets: no UI change (AI_REVIEW_WORKFLOW §3.2).
- Security-type identification not added: owner decision D3 (no type labels).

## 11. Decisions requiring owner approval
- Approve the whitespace fix (server; takes effect on the next approved deploy).
- Optional backlog items in report §13 (REAL page-size check, empty-result TTL, client memo, partial results).

## 12. Links
- Report: `docs/COMPANY_SEARCH_RELEVANCE_AUDIT_PHASE4.md`
- Focused diff: `CHATGPT_FOCUSED_DIFF.md`; tests: `CHATGPT_TEST_RESULTS.md`

**Recommendation: APPROVE WITH FIXES.**
