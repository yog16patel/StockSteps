# Phase 8E — Motion policy

**Source of truth:** core `navigation/NavigationMotion.kt` (pure, tested in `NavigationMotionTest`). Android applies it in one place,
`presentation/NavMotionTransitions.kt`, wired into the single `NavHost`. iOS stays native.

| Step | `NavMotion` | Android (Compose) | iOS |
|---|---|---|---|
| Bottom-tab tap, landing jump, Portfolio section, Back at a non-Home root (→ Home), sign-out reset | NONE | `EnterTransition.None` / `ExitTransition.None` | Native instant `TabView` |
| Open a tab screen (same tab or across tabs, pushed or reused) | PUSH | Old page: `fadeOut` 70 ms + slide 24 dp toward the leading edge (220 ms). New page: slide in 24 dp from the trailing edge (220 ms, FastOutSlowIn) + `fadeIn` 150 ms after a 70 ms delay | Native push |
| Back (button, system Back, hop end, reselect pop to root) | POP | The exact reverse of PUSH | Native pop and interactive swipe-back |
| Open a modal or global screen (Search, forms, pickers, Settings, Notifications, Auth) | MODAL_IN | New screen `fadeIn` 150 ms over the page (old page `KeepUntilTransitionsFinished`, so no blank frame) | Native sheet |
| Close a modal or global screen | MODAL_OUT | Modal `fadeOut` 150 ms over the page below (page enter `None`) | Native sheet dismissal |

**Rules:**
- **Direction comes from the navigator's recorded step,** not from the library's `isPop`. A hop's Back is a graph navigate (not a pop) but
  still plays POP.
- **A modal on either side wins** (`NavigationMotion.between`), so a form that closes itself still fades.
- **Durations:** total ≤ 220 ms (within 180–240 ms); travel 24 dp; no overshoot or bounce; the fade-through keeps the two pages from
  overlapping for long.
- **System animator duration scale is respected** (Compose scales tween durations): 0.5× halves the motion, Off makes every step
  instant.
- **No custom iOS animations** are added; native behaviour is preserved.
