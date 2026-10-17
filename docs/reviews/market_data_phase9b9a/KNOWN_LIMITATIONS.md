# Known limitations — Phase 9B.9A

1. No live validation yet: 0 authenticated requests from this session (no key here). The owner uses a free personal key for owner-only
   local testing; the smoke test is theirs to run. Earlier the work was described as company-associated — ToS §2 (ii) makes use on behalf
   of a corporation commercial, so testers, a beta or launch need the written agreement first.
2. 20 requests/day can't support the app beyond the owner's checks (≈ 2 cold companies a day).
3. FREE history is the latest 100 sessions only (charts beyond ~5 months empty); no OVERVIEW profiles; no adjusted series.
4. Personal access is now **loopback only** (adb reverse, the iOS simulator, local tools) or verified uids on signed-in routes; a physical
   phone on the LAN is refused by design (a LAN address can stand for many people behind NAT).
5. The daily budget is exact per instance; more than one instance would multiply it (Cloud Run personal mode is already a startup error).
6. FMP stays the default for every dataset; Alpha Vantage is opt-in and unvalidated.
