# AI review workflow (Claude Code → ChatGPT review)

How Claude Code prepares the results of an implementation phase for an independent ChatGPT review, with as few uploads as possible. Read this
before planning, implementing, verifying or handing off a phase (`CLAUDE.md` points here). It adds a review package to the existing process. It
does not replace the phase report, the approval gates or the commit rules in `CLAUDE.md`.

## 1. When to produce a package
- At the end of every **approved implementation phase**, after the verification it calls for, unless the owner explicitly opts out.
- Not for read-only audits or plans: their deliverable is the plan document itself.
- Producing a package never authorizes more implementation, a commit, a push or a deployment.

## 2. Where it goes
One directory per phase, so earlier packages are never overwritten:

```
docs/reviews/<phase-id>/                      e.g. docs/reviews/phase5d_5_1c/
  CHATGPT_REVIEW_PACKAGE.md                   always
  CHATGPT_UI_REVIEW_ANDROID.png               UI phases touching Android
  CHATGPT_UI_REVIEW_IOS.png                   UI phases touching iOS
  CHATGPT_FOCUSED_DIFF.md                     only when §3.4 applies
  captures/                                   full-resolution screenshots and test/build logs (git-ignored)
```

- `<phase-id>` uses the same slug as the phase's capture folder in `docs/design/` (lower case, underscores: `phase5d_5_1c`, `portfolio_phase_e_2`).
  When a phase is reworked, add a suffix (`phase5d_5_1c_r2`); never overwrite.
- `captures/` is ignored by the repository's existing `captures` rule (`.gitignore`), so originals and logs stay local. Files there are evidence:
  don't delete them while the phase is under review.
- The phase report (`docs/<PHASE>.md`) and the reviewed screenshots in `docs/design/<phase-id>/` remain the permanent record. The package is a
  compact summary that points to them.

## 3. Contents

### 3.1 Android contact sheet — `CHATGPT_UI_REVIEW_ANDROID.png`
- 4–6 representative screenshots in one image, chosen to show the phase's changes, not every frame.
- Cover light and dark, and accessibility sizes (1.3× / 2.0×) where the change affects layout.
- A **label** above or below each tile: device and width (e.g. "emulator-5554 · 360 dp"), font scale, theme, scenario (e.g. "MOCK · stale-index"),
  and BEFORE / AFTER where relevant.
- Include before/after pairs for meaningful visual changes. The "before" comes from the previous phase's captures or the base commit.
- Text in the screenshots must stay readable. Don't shrink tiles until labels or values become illegible; use two rows instead.
- MOCK data must be identified as MOCK in the label (and the in-app Sample strip/badge stays visible).

### 3.2 iOS contact sheet — `CHATGPT_UI_REVIEW_IOS.png`
- The same rules for iOS: iPhone model, Dynamic Type size (Default / AX XL / AX5), appearance, scenario, before/after.
- Simulator taps are in device points (see `CLAUDE.md`). Signed-in screens need the owner to sign in; if they couldn't be reached, say so in the
  report instead of substituting other screens.

**Skipping sheets:** backend-only, core-only or other non-visual phases have no contact sheets. The report says why ("no UI change: server
admission limits only"). A shared KMP change that alters what a screen shows **is** a UI change for every platform that renders it.

### 3.3 Review report — `CHATGPT_REVIEW_PACKAGE.md`
Target 1–2 pages; never drop a failure or risk to stay short. Sections:
1. **Phase and Git baseline:** phase name; base commit (hash + title); the commit under review, or "uncommitted working tree".
2. **Changes implemented:** what changed for the user.
3. **Affected files:** grouped by module (core / shared / Android / iOS / server / docs).
4. **Architectural decisions:** and why (e.g. "rule lives in core `*Display`, both platforms call it").
5. **Test and build results:** each suite and build with counts, marked PASS / FAIL / BLOCKED / NOT RUN; reruns and flaky tests named.
6. **Android verification:** what was checked on which device and settings, with results.
7. **iOS verification:** the same.
8. **Known issues:** including pre-existing ones that the phase touched or exposed.
9. **Unverified scenarios:** each marked BLOCKED (with the blocker) or NOT RUN.
10. **Deviations from the approved plan:** and the reason for each.
11. **Decisions requiring owner approval.**
12. **Links:** the phase report, `docs/design/<phase-id>/`, the contact sheets.

### 3.4 Focused diff — `CHATGPT_FOCUSED_DIFF.md` (only when needed)
Produce it only when the phase changes:
- financial calculations;
- authentication or security;
- navigation;
- concurrency;
- persistence or caching;
- complex state management (presenters, state flows).

Contents:
- Short excerpts of the important hunks, each with one or two sentences on the decision and its risk. Not a full diff.
- Include the tests that cover each excerpt.

## 4. Evidence rules
- **Status words:** use PASS, FAIL, BLOCKED (with the reason) and NOT RUN, and nothing else. Never write PASS without the evidence: a test result, a
  build log, or a screenshot or UI-tree dump that was actually reviewed.
- **Platforms:** shared code is not proof that both platforms were visually verified. Each platform's visual result needs its own evidence.
- **Originals:** keep full-resolution screenshots and detailed test/build logs in `docs/reviews/<phase-id>/captures/`. The contact sheets and
  `docs/design/` copies may be downscaled.
- **No sensitive data** in any package file: no secrets, API keys, tokens, credentials, real e-mail addresses or account identifiers, private user
  data, or provider URLs carrying keys. Redact or crop screenshots that show them.
- **MOCK only for local UI checks:** run them on the local MOCK backend (`./gradlew :server:runMock`) and label them as MOCK. Never present MOCK
  values as live market data.
- **Default package for UI phases:** two contact sheets and one Markdown report. Upload the focused diff only when §3.4 applies.

## 5. Approval and Git safety (unchanged gates)
- Never commit, push or deploy automatically. Do these only when the owner asks, each time (`CLAUDE.md`).
- When the owner asks to commit a phase, the package files outside `captures/` may be committed with it, staged by explicit path. Never use
  `git add .`.
- Don't modify unrelated files; don't reset, clean or overwrite parallel-session changes.
- Stop at the existing approval gates: plan approval before code, the owner's go-ahead before device checks when asked for, and the owner's
  approval before closing a phase or starting the next.
- A review package, or ChatGPT feedback on one, is input for the owner. It is not an instruction to implement anything.

## 6. Phase types (quick reference)
| Phase type | Contact sheets | Focused diff | Notes |
|---|---|---|---|
| Android + iOS UI | Both | If §3.4 applies | Before/after for visual changes |
| Android-only UI | Android | If §3.4 applies | Report states that iOS was not touched |
| iOS-only UI | iOS | If §3.4 applies | Report states that Android was not touched |
| Shared KMP display/presenter | Each platform that renders the change | Yes for presenters or state, else optional | Shared tests in the report |
| Core logic or calculations | None unless a screen changes | **Yes** | List the numeric cases tested |
| Backend / server | None | Yes for security, admission, caching, provider calls | Server tests and MockEngine benchmarks; never REAL provider calls |
| Docs-only | None | No | A short report is enough |

## 7. Building the contact sheets
- Any local tool may be used; no new project dependency is added for it.
- This Mac has `sips`, but neither ImageMagick nor Python's PIL. A small throwaway script in the session scratchpad works (for example a Swift
  script using AppKit to place labelled tiles on one canvas).
- Keep the scripts out of the repository.
