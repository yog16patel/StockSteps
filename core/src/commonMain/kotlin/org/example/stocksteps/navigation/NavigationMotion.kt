package org.example.stocksteps.navigation

/** How a navigation step moves on screen (Phase 8E motion policy). */
enum class NavMotion {
    /** Instant: tab switches, a tab's landing page, sections, sign-out reset. */
    NONE,
    /** Forward to a tab screen: a short slide from the trailing edge with a fade-through. */
    PUSH,
    /** Back to the previous screen: the reverse of [PUSH]. */
    POP,
    /** A modal or global screen opens over the current one: a quick fade. */
    MODAL_IN,
    /** A modal or global screen closes: a quick fade. */
    MODAL_OUT
}

/**
 * Phase 8E: one rule for every navigation step, so screens never animate twice or in the wrong direction. Bottom tabs switch instantly
 * (no slide or full-screen fade); forward and Back are short and mirrored; modals fade. "No animation is better than a bad animation":
 * anything not listed is instant. The platform's own animation scale (Android animator duration scale) still applies on top.
 */
object NavigationMotion {
    /** Timings in milliseconds: the outgoing screen fades out first, then the incoming one fades in while both slide a short distance. */
    const val SLIDE_MS = 220
    const val FADE_OUT_MS = 70
    const val FADE_IN_MS = 150
    const val MODAL_FADE_MS = 150
    /** Slide distance in dp: minimal travel. */
    const val SLIDE_DP = 24

    /** Opening a screen of [kind]. */
    fun open(kind: ScreenKind): NavMotion = when (kind) {
        ScreenKind.TAB_ROOT -> NavMotion.NONE
        ScreenKind.TAB_SCREEN -> NavMotion.PUSH
        ScreenKind.MODAL, ScreenKind.GLOBAL -> NavMotion.MODAL_IN
    }

    /** A tab tap (also a jump to a landing page or a Portfolio section): instant, never a slide. */
    fun selectTab(): NavMotion = NavMotion.NONE

    /**
     * Back from a screen of [fromKind]. [toLandingOfAnotherTab] is Back at a non-Home tab root, which shows Home's landing (a tab switch, so it
     * is instant like a tab tap).
     */
    fun back(fromKind: ScreenKind, toLandingOfAnotherTab: Boolean = false): NavMotion = when {
        fromKind == ScreenKind.MODAL || fromKind == ScreenKind.GLOBAL -> NavMotion.MODAL_OUT
        toLandingOfAnotherTab -> NavMotion.NONE
        else -> NavMotion.POP
    }

    /** Reselecting the visible tab pops to its root: a Back step. */
    fun reselectPopToRoot(): NavMotion = NavMotion.POP

    /** Sign-out or an ended session: instant. */
    fun reset(): NavMotion = NavMotion.NONE

    /**
     * The motion to draw between two destinations. A modal on either side wins over the recorded step, so a modal that closes itself (a
     * form's own Back) still fades even when the last navigator step was something else.
     */
    fun between(recorded: NavMotion, fromModal: Boolean, toModal: Boolean): NavMotion = when {
        toModal && !fromModal -> NavMotion.MODAL_IN
        fromModal && !toModal -> NavMotion.MODAL_OUT
        else -> recorded
    }
}
