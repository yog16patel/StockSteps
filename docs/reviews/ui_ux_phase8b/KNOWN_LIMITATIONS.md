# Phase 8B — known limitations

1. **Phase 7E enrichment quota (outstanding, not changed):** ≈ 310 requests a day per instance at `SCREENER_ENRICHMENT_PER_HOUR=60` vs a
   250-a-day key (7E `KNOWN_LIMITATIONS.md` §6). Needs an owner decision on the budget; no backend work in 8B.
2. **No per-row price date in Discover:** `CompanyRecord` carries no price date; the "Latest close" basis is the server's warning line. A
   dated price per row needs a server field (out of scope).
3. **MOCK only:** REAL screens weren't captured (FMP answering 429). MOCK's universe is 160 companies with a long "(not real data)"
   description, which makes the scope line three lines at default size and long at AX5; the REAL description is shorter.
4. **BRK.B on device:** MOCK has no Berkshire listing; normalization is covered by unit tests only.
5. **Sector shortcut with several sectors:** when the filter sheet applies two or more sectors, no pill is highlighted (by design); tapping
   a pill replaces them with one sector.
6. **2.0× placeholder:** the Discover search placeholder is one line and ends with "…" at 2.0×; the field's spoken label is complete.
7. **Chart axis currency (P2):** Y-axis labels have no currency symbol (shared chart; follow-up).
8. **Legacy Discovery screens:** fixed formatter, but the screens aren't reachable in the current app, so the fix is verified by unit tests,
   not on a device.
9. **Not run:** TalkBack / VoiceOver, signed-in iOS, Android guest, iOS BEFORE Discover in dark / AX5, REAL, small phones, tablets.
