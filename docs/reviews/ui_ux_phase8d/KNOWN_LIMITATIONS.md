# Phase 8D — known limitations

1. **TalkBack / VoiceOver: NOT RUN.** See `ACCESSIBILITY_REVIEW.md` for the reasons. Accessibility is assessed by code and screenshots
   only.
2. **Signed-in iOS: BLOCKED.** No credentials can be entered on devices. Discover, Practice and Learn were seen as a guest. Practice with
   data at AX5 is unseen.
3. **Login, onboarding and Settings were not re-audited.** They are unchanged since earlier phases; screens that need credentials can't be
   driven.
4. **Android at 1.5× was swept with the 8C build.** No defects were found, so there is no 8D change to compare; AFTER at 1.5× is NOT RUN.
5. **The Discover scope label wording is new.** "N companies · not the whole market" is a new phrase and needs owner approval (D8D-1).
   The server's description is unchanged and one tap away.
6. **Learn content was not reviewed by a finance expert.** No inaccuracies were found in the lessons checked: P/E, market cap, EPS.
7. **Capture tooling.** A crash in the UI-tree helper (nodes without a `text` attribute) caused earlier "NOT FOUND" taps. It is fixed in
   8D; frames from failed navigations were discarded.
8. **Backlog items carried forward.** The REAL search error copy and the REAL Company Details empty heading are listed in
   `LAUNCH_READINESS_BACKLOG.md`.
