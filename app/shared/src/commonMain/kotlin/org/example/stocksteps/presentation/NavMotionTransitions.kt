package org.example.stocksteps.presentation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import org.example.stocksteps.navigation.NavMotion
import org.example.stocksteps.navigation.NavigationMotion

/**
 * Phase 8E: the NavHost's only transitions, chosen from the navigator's recorded step (core [NavigationMotion]); they replace the library
 * default (a 700 ms cross-fade on every destination, tab switches included, that showed two pages at once under an already-switched top
 * bar). Tab switches are instant. A push fades the old page out first (70 ms) while both slide a short distance, then fades the new page in,
 * so the pages never overlap for long; Back mirrors it. Modals fade over the page below. The system animator duration scale applies, so
 * "animations off" makes every step instant.
 */
internal class NavMotionTransitions(private val navigator: TabNavigator, private val slidePx: Int) {
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.motion(): NavMotion =
        NavigationMotion.between(navigator.motion, fromModal = initialState.destination.tab() == null, toModal = targetState.destination.tab() == null)

    private val slide = tween<androidx.compose.ui.unit.IntOffset>(NavigationMotion.SLIDE_MS, easing = FastOutSlowInEasing)
    private val fadeIn = fadeIn(tween(NavigationMotion.FADE_IN_MS, delayMillis = NavigationMotion.FADE_OUT_MS, easing = LinearOutSlowInEasing))
    private val fadeOut = fadeOut(tween(NavigationMotion.FADE_OUT_MS, easing = FastOutLinearInEasing))

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when (motion()) {
            NavMotion.PUSH -> fadeIn + slideInHorizontally(slide) { slidePx }
            NavMotion.POP -> fadeIn + slideInHorizontally(slide) { -slidePx }
            NavMotion.MODAL_IN -> fadeIn(tween(NavigationMotion.MODAL_FADE_MS))
            NavMotion.MODAL_OUT, NavMotion.NONE -> EnterTransition.None // the page below a closing modal is already in place
        }
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when (motion()) {
            NavMotion.PUSH -> fadeOut + slideOutHorizontally(slide) { -slidePx }
            NavMotion.POP -> fadeOut + slideOutHorizontally(slide) { slidePx }
            NavMotion.MODAL_IN -> ExitTransition.KeepUntilTransitionsFinished // the modal fades in over the page, never over a blank frame
            NavMotion.MODAL_OUT -> fadeOut(tween(NavigationMotion.MODAL_FADE_MS))
            NavMotion.NONE -> ExitTransition.None
        }
    }
}
