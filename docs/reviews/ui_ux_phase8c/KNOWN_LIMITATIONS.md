# Phase 8C — known limitations

1. **Signed-in iOS is BLOCKED.** No credentials may be entered on devices, and the iPhone 16 runs as a guest. The iOS My Portfolio
   reorder and the borderless Practice are verified by build only; guest views were captured.
2. **Android uses the MOCK user-data store.** The emulator session is signed in to the local MOCK server (not production Firebase). The
   Portfolio frames use the read-only sample scenarios.
3. **The 8C watchlist test data was added on MOCK.** AAPL was added to the emulator account's MOCK watchlist to capture a filled
   Watchlist. Production data wasn't touched.
4. **Trade preview / review / result frames: NOT RUN.** The order flow is unchanged, so the 7C/7D evidence stands.
   **Practice empty-state frame: NOT RUN.** The MOCK Practice account stayed on the `one-holding` scenario; the copy change is verified
   in code.
   **Capture tooling:** the scenario picker sometimes failed. Frames from failed switches were deleted and recaptured with on-screen
   verification.
5. **Transactions: not changed.** The audit found no P0/P1 in the forms (`PortfolioFormRules`), and forms are financial-flow code.
6. **Watchlist add takes three steps.** Search → Company Details → star. Existing navigation, noted for a later phase.
7. **Two "Add" entries on My Portfolio.** "+ Add" (account header) and "+ Add transaction". This is the existing D-series layout and was
   not changed.
8. **Firestore-emulator tests: NOT RUN in 8C.** They were run in 7D, and 8C changes no server or persistence code. The 8 emulator tests
   are skipped in `:server:test`.
9. **TalkBack / VoiceOver: NOT RUN.**
10. **The Phase 7E enrichment budget is still outstanding** (recorded in 8B); not changed.
