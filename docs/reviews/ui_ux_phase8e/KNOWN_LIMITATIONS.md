# Phase 8E — Known limitations

1. **P2, Android — the top bar updates before the new page's first frame.** The Scaffold top bar is outside the NavHost, so for the time it
   takes the new page to compose, the next page's title sits over the old page (and the old page shifts by the bar's height).
   - It shows with animations Off as well, so it's composition latency, not motion.
   - It is longer on this heavily loaded emulator: several hundred ms back to the Markets landing in some runs.
   - Fix (backlog, larger change): render pushed screens' top bars inside each route, so they move with their page.
2. **P2, Android — predictive Back is not seekable.** `AppBackHandler` handles all Back presses, so the NavHost's built-in predictive-back
   seeking isn't used, and the manifest doesn't opt into `enableOnBackInvokedCallback`. Back plays the POP motion after release. Unchanged
   in 8E.
3. **Evidence quality.**
   - Emulator timing is noisy (host load average 6–8): one Back took 250 ms in one run and 1,800 ms in another.
   - `screenrecord` drops frames on this emulator, so the strips show distinct frames, not every vsync.
   - Physical-device validation is NOT RUN; the attached phone is the owner's personal device.
4. **P2, iOS — two one-off transient frames under heavy simulator lag,** not reproduced in clean runs:
   - the company page drawn under the status bar during a sheet dismissal plus push;
   - an inline "Markets" title after an edge swipe on the root.
5. **Not run:**
   - process recreation in 8E;
   - Watchlist → company and Practice inner tabs (no data);
   - filter sheet; dark-mode video; iOS Reduce Motion;
   - TalkBack and VoiceOver;
   - signed-in iOS (BLOCKED).
