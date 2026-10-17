# Focused diff: Phase 8E (navigation motion)

## 1. Policy (core `navigation/NavigationMotion.kt`, new)
```kotlin
enum class NavMotion { NONE, PUSH, POP, MODAL_IN, MODAL_OUT }
object NavigationMotion {
    const val SLIDE_MS = 220; const val FADE_OUT_MS = 70; const val FADE_IN_MS = 150; const val MODAL_FADE_MS = 150; const val SLIDE_DP = 24
    fun open(kind: ScreenKind) = when (kind) { TAB_ROOT -> NONE; TAB_SCREEN -> PUSH; MODAL, GLOBAL -> MODAL_IN }
    fun selectTab() = NONE
    fun back(fromKind: ScreenKind, toLandingOfAnotherTab: Boolean = false) = when { modal -> MODAL_OUT; toLandingOfAnotherTab -> NONE; else -> POP }
    fun between(recorded: NavMotion, fromModal: Boolean, toModal: Boolean) = when { toModal && !fromModal -> MODAL_IN; fromModal && !toModal -> MODAL_OUT; else -> recorded }
}
```
**Decision.** One shared rule, so tabs are never animated and Back always mirrors forward.

**Tests:** `NavigationMotionTest` (4).

## 2. Navigator records the step (`TabNavigator.kt`)
```kotlin
var motion: NavMotion = NavMotion.NONE; private set
fun selectTab(tab: AppTab) { motion = NavigationMotion.selectTab(); … }
// open: MODAL/GLOBAL → open(kind); TAB_SCREEN → PUSH
// back(): modal on top → MODAL_OUT, else POP (Back at a non-Home root goes through selectTab → NONE)
// reselect pop to root → POP; showPortfolioSection → NONE; reset → NONE
```
**Decision.** Direction comes from the user's action, not from the library's `isPop`: a hop's Back is a graph navigate but must still look
like Back.

**Risk.** A screen that pops itself through `navController` keeps the last recorded value. Modal pops are covered by `between`; other
direct pops don't exist (all navigation goes through `TabNavigator`).

## 3. Central transitions (`NavMotionTransitions.kt`, new) and the NavHost
```kotlin
val enter = { when (motion()) { PUSH -> fadeIn(150, delay 70) + slideIn(+24dp, 220); POP -> … slideIn(-24dp);
                                MODAL_IN -> fadeIn(150); MODAL_OUT, NONE -> EnterTransition.None } }
val exit  = { when (motion()) { PUSH -> fadeOut(70) + slideOut(-24dp, 220); POP -> … slideOut(+24dp);
                                MODAL_IN -> ExitTransition.KeepUntilTransitionsFinished; MODAL_OUT -> fadeOut(150); NONE -> ExitTransition.None } }
NavHost(…, enterTransition = motion.enter, exitTransition = motion.exit, popEnterTransition = motion.enter, popExitTransition = motion.exit)
```
**Decision.** It replaces the library's 700 ms simultaneous cross-fade. The fade-through (old page out first) avoids double exposure;
`KeepUntilTransitionsFinished` keeps a modal from fading in over a blank frame. The tweens follow the system animator scale.

**Evidence:**
- `captures/at-strip.png` (instant tabs);
- `captures/af-strip.png` (push);
- `captures/off-strip.png` (animations Off);
- requests identical before and after.
